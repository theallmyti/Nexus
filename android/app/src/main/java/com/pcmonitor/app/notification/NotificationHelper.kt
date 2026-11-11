package com.pcmonitor.app.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.pcmonitor.app.MainActivity
import com.pcmonitor.app.R

object NotificationHelper {

    const val CHANNEL_ALERTS = "pc_monitor_hardware_alerts"
    const val CHANNEL_CANARY = "pc_monitor_canary"

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // 1. High Priority Hardware Alerts Channel
            val alertChannel = NotificationChannel(
                CHANNEL_ALERTS,
                "Hardware Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Hardware threshold alerts"
                enableLights(true)
                lightColor = Color.CYAN
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 200, 100, 200)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(alertChannel)

            // 2. Canary Channel
            val canaryChannel = NotificationChannel(
                CHANNEL_CANARY,
                "Canary & Connectivity",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Daily connectivity tests"
                enableLights(false)
                enableVibration(false)
            }
            notificationManager.createNotificationChannel(canaryChannel)
        }
    }

    fun canPostNotifications(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    /**
     * Minimalistic alert notification:
     * Compact, single-line message, no bulky action buttons, direct tap to inspect.
     */
    fun showHardwareAlert(
        context: Context,
        deviceId: String,
        title: String,
        message: String,
        notificationId: Int = (deviceId.hashCode() xor title.hashCode())
    ): Boolean {
        if (!canPostNotifications(context)) {
            return false
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("deviceId", deviceId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            deviceId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification_alert)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(notificationId, builder.build())
            return true
        } catch (_: SecurityException) {
            return false
        }
    }

    fun showCanaryNotification(context: Context): Boolean {
        if (!canPostNotifications(context)) return false

        val builder = NotificationCompat.Builder(context, CHANNEL_CANARY)
            .setSmallIcon(R.drawable.ic_notification_alert)
            .setContentTitle("Nexus · Canary")
            .setContentText("Connection healthy")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)

        try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(9999, builder.build())
            return true
        } catch (_: SecurityException) {
            return false
        }
    }
}
