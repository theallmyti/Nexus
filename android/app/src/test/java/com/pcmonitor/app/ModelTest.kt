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
}
