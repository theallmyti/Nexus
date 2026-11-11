package com.pcmonitor.app.data.repository

import com.pcmonitor.app.data.model.Alert
import com.pcmonitor.app.data.model.AlertCategory
import com.pcmonitor.app.data.model.AlertSeverity
import kotlinx.coroutines.flow.StateFlow

interface AlertRepository {
    val alerts: StateFlow<List<Alert>>
    suspend fun addAlert(alert: Alert)
    suspend fun markAsRead(alertId: String)
    suspend fun clearAlert(alertId: String)
    suspend fun clearAllAlerts()
    fun getAlertsByDevice(deviceId: String): List<Alert>
    fun getAlertsBySeverity(severity: AlertSeverity): List<Alert>
    fun getUnreadCount(): Int
    suspend fun checkThresholds(deviceId: String)
}

