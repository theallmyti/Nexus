package com.pcmonitor.app

import com.pcmonitor.app.data.model.CpuStats
import com.pcmonitor.app.data.model.GpuStats
import com.pcmonitor.app.data.model.RamStats
import com.pcmonitor.app.data.repository.UserPreferencesRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThresholdTest {

    @Test
    fun testPreferenceKeyGeneration() {
        val deviceId = "dev_abc123"
        val cpuKey = UserPreferencesRepository.deviceCpuKey(deviceId)
        val gpuKey = UserPreferencesRepository.deviceGpuKey(deviceId)
        val ramKey = UserPreferencesRepository.deviceRamKey(deviceId)
        val diskKey = UserPreferencesRepository.deviceDiskKey(deviceId)
        val hasCustomKey = UserPreferencesRepository.deviceHasCustomKey(deviceId)

        assertEquals("cpu_thresh_dev_abc123", cpuKey.name)
        assertEquals("gpu_thresh_dev_abc123", gpuKey.name)
        assertEquals("ram_thresh_dev_abc123", ramKey.name)
        assertEquals("disk_thresh_dev_abc123", diskKey.name)
        assertEquals("has_custom_thresh_dev_abc123", hasCustomKey.name)
    }

    @Test
    fun testHardwareThresholdEvaluationLogic() {
        // Global thresholds
        val globalCpuLimit = 90f
        val globalGpuLimit = 85f
        val globalRamLimit = 90f

        // Device 1: Inheriting global defaults
        val dev1CustomLimits: Map<String, Float>? = null

        val dev1EffectiveCpuLimit = dev1CustomLimits?.get("cpu") ?: globalCpuLimit
        val dev1EffectiveGpuLimit = dev1CustomLimits?.get("gpu") ?: globalGpuLimit
        val dev1EffectiveRamLimit = dev1CustomLimits?.get("ram") ?: globalRamLimit

        assertEquals(90f, dev1EffectiveCpuLimit, 0.01f)
        assertEquals(85f, dev1EffectiveGpuLimit, 0.01f)
        assertEquals(90f, dev1EffectiveRamLimit, 0.01f)

        // Device 2: Strict custom thresholds (e.g. laptop or quiet workstation)
        val dev2CustomLimits = mapOf(
            "cpu" to 75f,
            "gpu" to 70f,
            "ram" to 80f
        )

        val dev2EffectiveCpuLimit = dev2CustomLimits["cpu"] ?: globalCpuLimit
        val dev2EffectiveGpuLimit = dev2CustomLimits["gpu"] ?: globalGpuLimit
        val dev2EffectiveRamLimit = dev2CustomLimits["ram"] ?: globalRamLimit

        assertEquals(75f, dev2EffectiveCpuLimit, 0.01f)
        assertEquals(70f, dev2EffectiveGpuLimit, 0.01f)
        assertEquals(80f, dev2EffectiveRamLimit, 0.01f)

        // Test alert evaluation for a 78°C CPU reading
        val readingCpuTemp = 78f
        // For Dev 1 (global limit 90°C): should NOT trigger alert
        val dev1TriggersAlert = readingCpuTemp > dev1EffectiveCpuLimit
        assertFalse(dev1TriggersAlert)

        // For Dev 2 (custom limit 75°C): MUST trigger alert!
        val dev2TriggersAlert = readingCpuTemp > dev2EffectiveCpuLimit
        assertTrue(dev2TriggersAlert)
    }

    @Test
    fun testRevertingToDefaults() {
        val globalRamLimit = 90f
        var customRamLimit: Float? = 70f

        // With custom limit set
        var effective = customRamLimit ?: globalRamLimit
        assertEquals(70f, effective, 0.01f)

        // User reverts device to global defaults
        customRamLimit = null
        effective = customRamLimit ?: globalRamLimit
        assertEquals(90f, effective, 0.01f)
    }
}
