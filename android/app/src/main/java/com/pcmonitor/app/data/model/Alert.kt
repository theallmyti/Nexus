package com.pcmonitor.app.data.model

import java.util.UUID

data class Alert(
    val id: String = UUID.randomUUID().toString(),
    val deviceId: String,
    val deviceName: String,
    val title: String,
    val description: String,
    val severity: AlertSeverity,
    val category: AlertCategory,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    val value: Float? = null,
    val threshold: Float? = null
)

enum class AlertSeverity(val label: String) {
    WARNING("Warning"),
    CRITICAL("Critical"),
    OFFLINE("Offline"),
    INFO("Info")
}

enum class AlertCategory(val label: String) {
    CPU_TEMP("CPU Temperature"),
    GPU_TEMP("GPU Temperature"),
    RAM_USAGE("RAM Usage"),
    DISK_SPACE("Disk Space"),
    DEVICE_OFFLINE("Device Offline"),
    DEVICE_ONLINE("Device Online")
}

data class ThresholdConfig(
    val cpuTempCelsius: Float = 90f,
    val gpuTempCelsius: Float = 85f,
    val ramUsagePercent: Float = 90f,
    val diskFreePercent: Float = 10f,
    val perDriveThresholds: Map<String, Float> = emptyMap()
) {
    companion object {
        val DEFAULT = ThresholdConfig()
    }
}

data class HistoryEntry(
    val timestamp: Long,
    val cpuTemp: Float,
    val gpuTemp: Float? = null,
    val ramUsagePercent: Float,
    val cpuUtilization: Float
)

data class HistorySummary(
    val deviceId: String,
    val deviceName: String,
    val entries: List<HistoryEntry> = emptyList(),
    val avgCpuTemp: Float = 0f,
    val maxCpuTemp: Float = 0f,
    val avgRamUsage: Float = 0f,
    val totalUptimeSeconds: Long = 0
) {
    val totalUptimeFormatted: String
        get() {
            val hours = totalUptimeSeconds / 3600
            val minutes = (totalUptimeSeconds % 3600) / 60
            return "${hours}h ${minutes}m"
        }
}

