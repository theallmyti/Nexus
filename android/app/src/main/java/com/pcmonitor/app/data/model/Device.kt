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
    val ownerId: String = "",
    // From the agent's capabilities at registration. null = unknown (older agent), so trust live data.
    val hasGpu: Boolean? = null,
    val hasBattery: Boolean? = null
) {
    /** Drops GPU/battery data for hardware the agent said this device doesn't have. */
    fun gate(stats: HardwareStats): HardwareStats = stats.copy(
        gpu = if (hasGpu == false) null else stats.gpu,
        battery = if (hasBattery == false) null else stats.battery
    )
}

/** RAM/VRAM sizes with one decimal, so a 15.7 GB machine isn't shown as "15 GB". */
fun formatGb(mb: Long): String {
    val gb = mb / 1024.0
    return if (gb >= 100) "${gb.toInt()} GB" else String.format(java.util.Locale.US, "%.1f GB", gb)
}

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
    val type: String = "",
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
    val health: String = "",
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


/**
 * The worst live problem on this device, as a short label for its card ("CPU 91°C"), or null.
 * Same limits as Home's "Critical" counter so the two never disagree.
 */
fun Device.problem(): String? {
    if (!isOnline) return null
    val s = hardwareStats
    val cpu = s.cpu.averageTemperature
    val gpu = s.gpu?.temperature ?: 0f
    val ram = s.ram.usagePercent
    val battery = s.battery
    return when {
        cpu >= 85f -> "CPU ${cpu.toInt()}°C"
        gpu >= 80f -> "GPU ${gpu.toInt()}°C"
        ram >= 90f -> "RAM ${ram.toInt()}%"
        battery != null && !battery.isCharging && battery.percentage <= 15 -> "Battery ${battery.percentage}%"
        else -> null
    }
}

/** "just now" / "12s ago" / "5m ago" for a card's freshness line. */
fun ageLabel(sinceMs: Long, now: Long = System.currentTimeMillis()): String {
    if (sinceMs <= 0L) return "never"
    val sec = ((now - sinceMs) / 1000).coerceAtLeast(0)
    return when {
        sec < 5 -> "just now"
        sec < 60 -> "${sec}s ago"
        sec < 3600 -> "${sec / 60}m ago"
        sec < 86400 -> "${sec / 3600}h ago"
        else -> "${sec / 86400}d ago"
    }
}
