package com.pcmonitor.app.data.model

import java.util.UUID

data class Device(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val os: String,
    val type: DeviceType,
    val isOnline: Boolean = true,
    val lastSeen: Long = System.currentTimeMillis(),
    val hardwareStats: HardwareStats = HardwareStats(),
    val ownerId: String = ""
)

enum class DeviceType(val label: String) {
    LAPTOP("Laptop"),
    DESKTOP("Desktop"),
    SERVER("Server"),
    RASPBERRY_PI("Raspberry Pi")
}

data class HardwareStats(
    val cpu: CpuStats = CpuStats(),
    val gpu: GpuStats? = null,
    val ram: RamStats = RamStats(),
    val battery: BatteryStats? = null,
    val fans: FanStats = FanStats(),
    val usage: UsageStats = UsageStats(),
    // Fixed drives from the agent (latest.disks, or capabilities.drives before any live data).
    val disks: List<DiskStats> = emptyList()
)

data class DiskStats(
    val name: String,          // "C:\\" on Windows, "/" or "/home" on Linux
    val totalGb: Double,
    val freeGb: Double? = null // null until the agent has sent live data
) {
    val freePercent: Float? get() = freeGb?.let { if (totalGb > 0) (it / totalGb * 100).toFloat() else null }
}

data class FanStats(
    val cpuFanRpm: Int = 0,
    val gpuFanRpm: Int = 0,
    val maxRpm: Int = 4500
) {
    val averageRpm: Int get() = if (cpuFanRpm > 0 && gpuFanRpm > 0) (cpuFanRpm + gpuFanRpm) / 2 else maxOf(cpuFanRpm, gpuFanRpm)
}

data class CpuStats(
    val coreTemperatures: List<Float> = emptyList(),
    val packageTemperature: Float = 0f,
    val averageTemperature: Float = 0f,
    val maxTemperature: Float = 0f,
    val coreUtilizations: List<Float> = emptyList(),
    val threadUtilizations: List<Float> = emptyList(),
    val totalUtilization: Float = 0f,
    val clockSpeedMhz: Float = 0f,
    val powerWatts: Float = 0f
)

data class GpuStats(
    val name: String = "",
    val temperature: Float = 0f,
    val utilization: Float = 0f,
    val vramUsedMb: Long = 0,
    val vramTotalMb: Long = 0,
    val powerWatts: Float = 0f,
    val clockSpeedMhz: Float = 0f,
    val fanSpeedPercent: Float = 0f
)

data class RamStats(
    val usedMb: Long = 0,
    val totalMb: Long = 0,
    val freeMb: Long = 0,
    val cachedMb: Long = 0,
    val swapUsedMb: Long = 0,
    val swapTotalMb: Long = 0,
    val speedMhz: Int = 0,
    val type: String = "DDR4",
    val topProcesses: List<ProcessInfo> = emptyList()
) {
    val usagePercent: Float
        get() = if (totalMb > 0) (usedMb.toFloat() / totalMb) * 100f else 0f
}

data class ProcessInfo(
    val name: String,
    val memoryMb: Long,
    val cpuPercent: Float
)

data class BatteryStats(
    val percentage: Int = 0,
    val isCharging: Boolean = false,
    val health: String = "Good",
    val healthPercent: Int = 100,
    val chargeCycles: Int = 0,
    val powerRateWatts: Float = 0f
)

data class UsageStats(
    val uptimeSeconds: Long = 0,
    val activeTimeTodaySeconds: Long = 0,
    val bootHistory: List<BootEvent> = emptyList()
) {
    val uptimeFormatted: String
        get() {
            val days = uptimeSeconds / 86400
            val hours = (uptimeSeconds % 86400) / 3600
            val minutes = (uptimeSeconds % 3600) / 60
            return "${days}d ${hours}h ${minutes}m"
        }
    val activeTimeTodayFormatted: String
        get() {
            val hours = activeTimeTodaySeconds / 3600
            val minutes = (activeTimeTodaySeconds % 3600) / 60
            return "${hours}h ${minutes}m"
        }
}

data class BootEvent(
    val type: BootEventType,
    val timestamp: Long
)

enum class BootEventType {
    BOOT, SLEEP, WAKE, SHUTDOWN
}

