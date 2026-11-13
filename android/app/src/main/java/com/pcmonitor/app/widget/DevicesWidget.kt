package com.pcmonitor.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pcmonitor.app.MainActivity
import com.pcmonitor.app.PCMonitorApplication
import com.pcmonitor.app.R
import com.pcmonitor.app.data.model.Device
import com.pcmonitor.app.data.model.DeviceType
import com.pcmonitor.app.data.model.problem
import java.util.concurrent.TimeUnit

/**
 * Home-screen widget in the app's look: navy frame, cream device cards, pixel-font numbers,
 * CPU/RAM bars that turn red past the limit. It changes with its size: 1 card when short,
 * 2 at medium height, 3 when tall, instead of leaving empty space.
 *
 * Kept fresh two ways: while the app process is alive, PCMonitorApplication pushes every
 * device-list change here; when it isn't, a 15-minute WorkManager job refetches.
 */
class DevicesWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        render(context, devicesOf(context))
        schedule(context)
    }

    // Resizing on Android 11 and older: re-pick the layout for the new height.
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        render(context, devicesOf(context))
    }

    override fun onEnabled(context: Context) = schedule(context)

    override fun onDisabled(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    companion object {
        private const val WORK_NAME = "devices_widget_refresh"
        private const val GREEN = 0xFF15803D.toInt()
        private const val RED = 0xFFB91C1C.toInt()
        private const val GREY = 0xFF94A3B8.toInt()

        private fun devicesOf(context: Context) =
            (context.applicationContext as PCMonitorApplication).deviceRepository.devices.value

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<RefreshWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun render(context: Context, devices: List<Device>) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, DevicesWidget::class.java))
            if (ids.isEmpty()) return
            val sorted = devices.sortedWith(compareBy({ it.problem() == null }, { !it.isOnline }, { it.name.lowercase() }))

            for (id in ids) {
                val views = if (Build.VERSION.SDK_INT >= 31) {
                    // The launcher picks the biggest layout that fits the widget's current size.
                    RemoteViews(
                        mapOf(
                            SizeF(150f, 40f) to compact(context, sorted),
                            SizeF(150f, 110f) to build(context, sorted, 1),
                            SizeF(150f, 190f) to build(context, sorted, 2),
                            SizeF(150f, 280f) to build(context, sorted, 3)
                        )
                    )
                } else {
                    val h = manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 180)
                    when {
                        h < 110 -> compact(context, sorted)
                        else -> build(context, sorted, when { h >= 280 -> 3; h >= 190 -> 2; else -> 1 })
                    }
                }
                manager.updateAppWidget(id, views)
            }
        }

        /** One line for a 1-cell-high widget: the worst problem, or how many are online. */
        private fun compact(context: Context, devices: List<Device>): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_compact)
            views.setImageViewBitmap(R.id.widget_title, pixelText(context, "NEXUS", 11f, 0xFFFACC15.toInt()))
            val worst = devices.firstOrNull { it.problem() != null }
            val online = devices.count { it.isOnline }
            val (text, color) = when {
                devices.isEmpty() -> "Loading…" to GREY
                worst != null -> "${worst.name} · ${worst.problem()}" to 0xFFF87171.toInt()
                online < devices.size -> "$online of ${devices.size} online" to 0xFFFDBA74.toInt()
                else -> "All ${devices.size} online" to 0xFF86EFAC.toInt()
            }
            views.setTextViewText(R.id.widget_summary, text)
            views.setTextColor(R.id.widget_summary, color)
            views.setOnClickPendingIntent(R.id.widget_root, openApp(context))
            return views
        }

        private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        private fun build(context: Context, devices: List<Device>, cards: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_devices)
            views.removeAllViews(R.id.widget_cards)
            devices.take(cards).forEach { views.addView(R.id.widget_cards, card(context, it)) }

            views.setImageViewBitmap(R.id.widget_title, pixelText(context, "NEXUS", 11f, 0xFFFACC15.toInt()))
            views.setViewVisibility(R.id.widget_empty, if (devices.isEmpty()) View.VISIBLE else View.GONE)
            // Empty list right after a cold start usually means "still loading", not "signed out".
            val signedIn = !(context.applicationContext as PCMonitorApplication).userPreferencesRepository.getSessionToken().isNullOrBlank()
            views.setTextViewText(
                R.id.widget_empty,
                if (signedIn) "Loading your computers…" else "Open Nexus and sign in to see your computers here."
            )
            views.setTextViewText(R.id.widget_summary, summary(devices))
            val more = devices.size - cards
            val time = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date())
            views.setTextViewText(R.id.widget_updated, if (more > 0) "+$more more · updated $time" else "Updated $time")

            views.setOnClickPendingIntent(R.id.widget_root, openApp(context))
            return views
        }

        private fun card(context: Context, d: Device): RemoteViews {
            val v = RemoteViews(context.packageName, R.layout.widget_device_card)
            val problem = d.problem()
            val s = d.hardwareStats

            v.setImageViewBitmap(R.id.card_icon, pixelIcon(d.type, d.isOnline))
            v.setTextViewText(R.id.card_name, d.name)
            v.setTextViewText(R.id.card_sub, "${d.os} · ${d.type.label}")
            v.setInt(R.id.card_body, "setBackgroundResource", if (problem != null) R.drawable.widget_card_alert else R.drawable.widget_card)

            when {
                !d.isOnline -> { v.setTextViewText(R.id.card_status, "○ Offline"); v.setTextColor(R.id.card_status, GREY) }
                problem != null -> { v.setTextViewText(R.id.card_status, problem); v.setTextColor(R.id.card_status, RED) }
                else -> { v.setTextViewText(R.id.card_status, "● Online"); v.setTextColor(R.id.card_status, GREEN) }
            }

            if (!d.isOnline) {
                v.setViewVisibility(R.id.card_metrics, View.GONE)
                return v
            }
            v.setViewVisibility(R.id.card_metrics, View.VISIBLE)
            val cpuTemp = s.cpu.averageTemperature
            val ram = s.ram.usagePercent
            val ink = 0xFF3E2723.toInt()
            v.setImageViewBitmap(
                R.id.card_cpu,
                pixelText(context, if (cpuTemp > 0f) "CPU ${cpuTemp.toInt()}°C" else "CPU ${s.cpu.totalUtilization.toInt()}%", 8f, if (cpuTemp >= 85f) RED else ink)
            )
            v.setProgressBar(R.id.card_cpu_bar, 100, (if (cpuTemp > 0f) cpuTemp else s.cpu.totalUtilization).toInt().coerceIn(0, 100), false)
            v.setImageViewBitmap(R.id.card_ram, pixelText(context, "RAM ${ram.toInt()}%", 8f, if (ram >= 90f) RED else ink))
            v.setProgressBar(R.id.card_ram_bar, 100, ram.toInt().coerceIn(0, 100), false)
            if (Build.VERSION.SDK_INT >= 31) {
                v.setColorStateList(R.id.card_cpu_bar, "setProgressTintList", ColorStateList.valueOf(if (cpuTemp >= 85f) RED else 0xFF4CAF50.toInt()))
                v.setColorStateList(R.id.card_ram_bar, "setProgressTintList", ColorStateList.valueOf(if (ram >= 90f) RED else 0xFF4CAF50.toInt()))
            }
            return v
        }

        private fun summary(devices: List<Device>): String {
            if (devices.isEmpty()) return ""
            val attention = devices.count { it.problem() != null }
            val offline = devices.count { !it.isOnline }
            return when {
                attention > 0 -> "$attention ${if (attention == 1) "needs" else "need"} you"
                offline > 0 -> "$offline offline"
                else -> "All good"
            }
        }

        /** Text in the app's pixel font, as a bitmap (RemoteViews can't use bundled fonts). */
        private fun pixelText(context: Context, text: String, sizeSp: Float, color: Int): Bitmap {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = androidx.core.content.res.ResourcesCompat.getFont(context, R.font.press_start_2p)
                textSize = sizeSp * context.resources.displayMetrics.scaledDensity
                this.color = color
            }
            val fm = paint.fontMetrics
            val w = paint.measureText(text).toInt().coerceAtLeast(1) + 2
            val h = (fm.descent - fm.ascent).toInt().coerceAtLeast(1)
            return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
                Canvas(it).drawText(text, 1f, -fm.ascent, paint)
            }
        }

        /** Tiny pixel-art icon per device type, drawn on a 16×16 grid like the app's art. */
        private fun pixelIcon(type: DeviceType, online: Boolean): Bitmap {
            val n = 16
            val scale = 4
            val bmp = Bitmap.createBitmap(n * scale, n * scale, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val p = Paint().apply { isAntiAlias = false }
            fun r(color: Long, x: Int, y: Int, w: Int, h: Int) {
                p.color = (if (online) color else 0xFF9E9E9E).toInt()
                c.drawRect((x * scale).toFloat(), (y * scale).toFloat(), ((x + w) * scale).toFloat(), ((y + h) * scale).toFloat(), p)
            }
            when (type) {
                DeviceType.RASPBERRY_PI -> {
                    r(0xFF2E7D32, 2, 3, 12, 10)   // board
                    r(0xFF1B5E20, 2, 3, 12, 1)
                    r(0xFF212121, 6, 6, 4, 4)     // chip
                    r(0xFFFFC107, 3, 4, 1, 1); r(0xFFFFC107, 12, 4, 1, 1)
                    r(0xFFFFC107, 3, 11, 1, 1); r(0xFFFFC107, 12, 11, 1, 1)
                    r(0xFFB0BEC5, 11, 7, 3, 3)    // port
                }
                DeviceType.SERVER -> {
                    for (i in 0..2) {
                        r(0xFF455A64, 2, 2 + i * 4, 12, 3)
                        r(0xFF4CAF50, 3, 3 + i * 4, 1, 1)
                    }
                }
                DeviceType.LAPTOP -> {
                    r(0xFFFFC107, 3, 3, 10, 7)    // lid
                    r(0xFF0F1B2E, 4, 4, 8, 5)
                    r(0xFF4CAF50, 5, 5, 6, 3)     // screen
                    r(0xFFFFA000, 1, 10, 14, 2)   // base
                }
                DeviceType.DESKTOP -> {
                    r(0xFFFFC107, 2, 1, 12, 9)
                    r(0xFF0F1B2E, 3, 2, 10, 7)
                    r(0xFF4CAF50, 4, 3, 8, 5)
                    r(0xFFFFA000, 7, 10, 2, 2)
                    r(0xFFFFC107, 5, 12, 6, 2)
                }
            }
            return bmp
        }
    }

    /** Runs when the app may be closed: refetch from Convex, then redraw. */
    class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val app = applicationContext as PCMonitorApplication
            return try {
                app.deviceRepository.refresh()
                render(applicationContext, app.deviceRepository.devices.value)
                Result.success()
            } catch (e: Exception) {
                Result.retry()
            }
        }
    }
}
