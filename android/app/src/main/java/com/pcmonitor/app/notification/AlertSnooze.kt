package com.pcmonitor.app.notification

import android.content.Context
import java.util.Calendar

/**
 * Per-device snooze and global quiet hours for hardware notifications.
 * Plain SharedPreferences (not DataStore) because NotificationHelper must check it synchronously,
 * including from the FCM service where there is no coroutine scope.
 * Alerts are still recorded in the Alerts tab; only the phone notification is silenced.
 */
object AlertSnooze {
    private const val PREFS = "alert_snooze"
    private const val KEY_QUIET_ON = "quiet_on"
    private const val KEY_QUIET_START = "quiet_start" // hour 0-23
    private const val KEY_QUIET_END = "quiet_end"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun snoozedUntil(context: Context, deviceId: String): Long =
        prefs(context).getLong("until_$deviceId", 0L)

    fun snooze(context: Context, deviceId: String, untilMs: Long) {
        prefs(context).edit().putLong("until_$deviceId", untilMs).apply()
    }

    fun unsnooze(context: Context, deviceId: String) {
        prefs(context).edit().remove("until_$deviceId").apply()
    }

    /** Epoch ms of the next 08:00, for "until tomorrow morning". */
    fun nextMorning(now: Long = System.currentTimeMillis()): Long =
        Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 8); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    fun quietHours(context: Context): Triple<Boolean, Int, Int> {
        val p = prefs(context)
        return Triple(p.getBoolean(KEY_QUIET_ON, false), p.getInt(KEY_QUIET_START, 23), p.getInt(KEY_QUIET_END, 7))
    }

    fun setQuietHours(context: Context, enabled: Boolean, startHour: Int, endHour: Int) {
        prefs(context).edit()
            .putBoolean(KEY_QUIET_ON, enabled)
            .putInt(KEY_QUIET_START, startHour.coerceIn(0, 23))
            .putInt(KEY_QUIET_END, endHour.coerceIn(0, 23))
            .apply()
    }

    fun isMuted(context: Context, deviceId: String, now: Long = System.currentTimeMillis()): Boolean {
        if (snoozedUntil(context, deviceId) > now) return true
        val (on, start, end) = quietHours(context)
        if (!on || start == end) return false
        val hour = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
        // A window like 23 -> 7 wraps past midnight.
        return if (start < end) hour in start until end else hour >= start || hour < end
    }
}
