package com.pcmonitor.app.notification

import android.content.Context
import com.pcmonitor.app.data.model.Alert
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * When the user last looked at alerts (Alerts tab or the Home bell sheet).
 * The red dots mean "something new since you last looked", not "unread": visiting clears them
 * until a newer alert arrives, without marking every alert as read.
 */
object AlertsSeen {
    private const val PREFS = "alerts_seen"
    private const val KEY = "last_seen_ms"
    private val _lastSeen = MutableStateFlow(0L)
    val lastSeen: StateFlow<Long> = _lastSeen.asStateFlow()

    fun init(context: Context) {
        _lastSeen.value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY, 0L)
    }

    fun markSeen(context: Context) {
        val now = System.currentTimeMillis()
        _lastSeen.value = now
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(KEY, now).apply()
    }

    fun hasNew(alerts: List<Alert>, lastSeenMs: Long): Boolean = alerts.any { it.timestamp > lastSeenMs }
}
