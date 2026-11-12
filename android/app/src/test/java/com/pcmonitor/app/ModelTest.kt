package com.pcmonitor.app

import com.pcmonitor.app.data.model.CpuStats
import com.pcmonitor.app.data.model.FanStats
import com.pcmonitor.app.data.model.RamStats
import com.pcmonitor.app.data.model.UsageStats
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelTest {

    @Test
    fun testCpuStatsDefaults() {
        val cpu = CpuStats(packageTemperature = 65f, totalUtilization = 42f)
        assertEquals(65f, cpu.packageTemperature, 0.01f)
        assertEquals(42f, cpu.totalUtilization, 0.01f)
    }

    @Test
    fun testFanStatsAveraging() {
        // Dual fans active
        val dual = FanStats(cpuFanRpm = 2200, gpuFanRpm = 2000, maxRpm = 4500)
        assertEquals(2100, dual.averageRpm)

        // Single fan (e.g. desktop / iGPU)
        val single = FanStats(cpuFanRpm = 1450, gpuFanRpm = 0, maxRpm = 2000)
        assertEquals(1450, single.averageRpm)

        // Passive cooling / 0 RPM
        val silent = FanStats(cpuFanRpm = 0, gpuFanRpm = 0, maxRpm = 4500)
        assertEquals(0, silent.averageRpm)
    }

    @Test
    fun testRamPercentageCalculation() {
        val ram = RamStats(
            usedMb = 8192L,
            totalMb = 16384L,
            freeMb = 8192L
        )
        assertEquals(50f, ram.usagePercent, 0.01f)
    }

    @Test
    fun testUsageStatsFormatting() {
        val usage = UsageStats(uptimeSeconds = 3665L, activeTimeTodaySeconds = 7200L)
        assertEquals("0d 1h 1m", usage.uptimeFormatted)
        assertEquals("2h 0m", usage.activeTimeTodayFormatted)
    }

    @Test
    fun testGpuStatsRetentionOnPartialSnapshot() {
        val initialGpu = com.pcmonitor.app.data.model.GpuStats(
            name = "NVIDIA GeForce RTX 3060",
            temperature = 48f,
            utilization = 12f,
            vramUsedMb = 1024L,
            vramTotalMb = 6144L,
            powerWatts = 25f,
            clockSpeedMhz = 1400f
        )
        val initialStats = com.pcmonitor.app.data.model.HardwareStats(
            cpu = CpuStats(totalUtilization = 15f),
            gpu = initialGpu
        )

        // Incoming partial snapshot without GPU (e.g. agent idle tick)
        val incomingPartial = com.pcmonitor.app.data.model.HardwareStats(
            cpu = CpuStats(totalUtilization = 20f),
            gpu = null
        )

        // Merged stats must retain previous valid GPU
        val mergedStats = incomingPartial.copy(
            gpu = incomingPartial.gpu ?: initialStats.gpu
        )

        assertEquals("NVIDIA GeForce RTX 3060", mergedStats.gpu?.name)
        assertEquals(48f, mergedStats.gpu?.temperature ?: 0f, 0.01f)
        assertEquals(20f, mergedStats.cpu.totalUtilization, 0.01f)
    }

    @Test
    fun testVramAndRamFormattingPrecision() {
        val vramSubGbMb = 512L
        val formattedSubGb = if (vramSubGbMb < 1024) "${vramSubGbMb} MB" else String.format(java.util.Locale.US, "%.1f GB", vramSubGbMb / 1024.0)
        assertEquals("512 MB", formattedSubGb)

        val vramMultiGbMb = 1536L
        val formattedMultiGb = if (vramMultiGbMb < 1024) "${vramMultiGbMb} MB" else String.format(java.util.Locale.US, "%.1f GB", vramMultiGbMb / 1024.0)
        assertEquals("1.5 GB", formattedMultiGb)

        val ramUsedMb = 3584L
        val formattedRamGb = String.format(java.util.Locale.US, "%.1f GB", ramUsedMb / 1024.0)
        assertEquals("3.5 GB", formattedRamGb)
    }

    @Test
    fun testGpuDisplayFallbackLogic() {
        // Case 1: Active GPU with temperature
        val gpuWithTemp = com.pcmonitor.app.data.model.GpuStats(
            temperature = 52f,
            utilization = 15f
        )
        val display1 = if (gpuWithTemp.temperature > 0f) "${gpuWithTemp.temperature.toInt()}°C"
            else if (gpuWithTemp.utilization > 0f) "${gpuWithTemp.utilization.toInt()}%"
            else "Idle"
        assertEquals("52°C", display1)

        // Case 2: GPU with 0°C temperature sensor (or sensor unexposed), but active utilization
        val gpuNoTempActive = com.pcmonitor.app.data.model.GpuStats(
            temperature = 0f,
            utilization = 45f
        )
        val display2 = if (gpuNoTempActive.temperature > 0f) "${gpuNoTempActive.temperature.toInt()}°C"
            else if (gpuNoTempActive.utilization > 0f) "${gpuNoTempActive.utilization.toInt()}%"
            else "Idle"
        assertEquals("45%", display2)

        // Case 3: GPU present, 0°C and 0% utilization (fully idle)
        val gpuIdle = com.pcmonitor.app.data.model.GpuStats(
            temperature = 0f,
            utilization = 0f
        )
        val display3 = if (gpuIdle.temperature > 0f) "${gpuIdle.temperature.toInt()}°C"
            else if (gpuIdle.utilization > 0f) "${gpuIdle.utilization.toInt()}%"
            else "Idle"
        assertEquals("Idle", display3)
    }
}
