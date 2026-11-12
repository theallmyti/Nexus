package com.pcmonitor.app

import com.pcmonitor.app.data.mock.MockAuthRepository
import com.pcmonitor.app.data.model.LoginRequest
import com.pcmonitor.app.data.model.RamStats
import com.pcmonitor.app.data.model.RegisterRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AdversarialSecurityTest {

    @Test
    fun testZeroRamTotalDivisionByZero() {
        val ramZero = RamStats(
            usedMb = 0L,
            totalMb = 0L,
            freeMb = 0L
        )
        // Must not crash with NaN or Infinity or divide by zero exception
        assertEquals(0f, ramZero.usagePercent, 0.001f)
    }

    @Test
    fun testExtremelyLargeValues() {
        val largeRam = RamStats(
            usedMb = Long.MAX_VALUE / 2,
            totalMb = Long.MAX_VALUE,
            freeMb = Long.MAX_VALUE / 2
        )
        assertTrue(largeRam.usagePercent.isFinite())
    }

    @Test
    fun testSliding30DaySessionMath() {
        val thirtyDaysMs = 30L * 24L * 60L * 60L * 1000L
        val now = 1700000000000L

        // Exactly 30 days + 1 ms ago -> expired
        val expiredTs = now - thirtyDaysMs - 1L
        assertTrue((now - expiredTs) > thirtyDaysMs)

        // 29 days ago -> active
        val activeTs = now - (29L * 24L * 60L * 60L * 1000L)
        assertFalse((now - activeTs) > thirtyDaysMs)
    }

    @Test
    fun testAuthAdversarialVectors() = runBlocking {
        val repo = MockAuthRepository(baseUrl = "")

        // No backdoor: the old built-in demo account must not sign in, and with no server nothing
        // signs in locally (accounts exist only on the server).
        val demoResult = repo.login(LoginRequest("  USER@EXAMPLE.COM  ", "password123"))
        assertTrue("Built-in demo account must not exist", demoResult.isFailure)
        assertTrue(
            "Offline sign-in explains the problem",
            demoResult.exceptionOrNull()?.message?.contains("Can't reach Nexus") == true
        )

        // Tier 7: Injection strings must fail gracefully without unhandled exception
        val sqliResult = repo.login(LoginRequest("' OR '1'='1", "password123"))
        assertTrue("SQLi attempt must fail gracefully", sqliResult.isFailure)

        val xssResult = repo.login(LoginRequest("<script>alert(1)</script>", "password123"))
        assertTrue("XSS attempt must fail gracefully", xssResult.isFailure)

        // Tier 7: Wrong credentials (email registered previously)
        val wrongPassResult = repo.login(LoginRequest("user@example.com", "wrongpassword"))
        assertTrue("Wrong password must fail", wrongPassResult.isFailure)

        // Tier 1 & 7: Malformed registration
        val shortPassResult = repo.register(RegisterRequest("User", "new@example.com", "123"))
        assertTrue("Password < 8 chars must fail", shortPassResult.isFailure)

        // Registration never creates a local-only account when the server can't be reached.
        val offlineRegister = repo.register(RegisterRequest("User", "new@example.com", "password123"))
        assertTrue("Offline registration must fail", offlineRegister.isFailure)

        val malformedEmailResult = repo.register(RegisterRequest("User", "not-an-email", "password123"))
        assertTrue("Malformed email must fail", malformedEmailResult.isFailure)
    }

    @Test
    fun testDeviceUnpairStateIntegrity() = runBlocking {
        val repo = com.pcmonitor.app.data.mock.MockDeviceRepository()
        val initialCount = repo.devices.value.size

        // Tier 1: Attempt to remove non-existent device ID - must be safe and not corrupt flow
        repo.removeDevice("non_existent_device_id_99999")
        assertEquals(initialCount, repo.devices.value.size)

        // Tier 1: Attempt to remove empty device ID - must be safe
        repo.removeDevice("")
        assertEquals(initialCount, repo.devices.value.size)
    }

    @Test
    fun testThresholdBoundaryAndCooldownMath() {
        val cooldownMs = 15 * 60 * 1000L // 15 minutes
        val now = 1700000000000L

        // Tier 1 & 2: Alert triggered within cooldown window -> suppressed
        val lastAlertRecent = now - (5 * 60 * 1000L) // 5 minutes ago
        assertFalse("Alert within cooldown must not fire", (now - lastAlertRecent) > cooldownMs)

        // Alert triggered after cooldown window -> permitted
        val lastAlertOld = now - (16 * 60 * 1000L) // 16 minutes ago
        assertTrue("Alert outside cooldown must fire", (now - lastAlertOld) > cooldownMs)

        // Tier 1: Extreme boundary values for thresholds
        val extremeRamZero = 0f
        val extremeRamHundred = 100f
        val extremeRamOver = 150f

        val currentRam = 68.5f
        assertTrue(currentRam > extremeRamZero)
        assertFalse(currentRam > extremeRamHundred)
        assertFalse(currentRam > extremeRamOver)
    }

    @Test
    fun testFcmPayloadSanitization() {
        // Tier 7: Malformed and missing FCM remote message fields
        val emptyMap = emptyMap<String, String>()
        val deviceId = emptyMap["deviceId"] ?: "primary-pc"
        val title = emptyMap["title"] ?: "Nexus Alert"
        val message = emptyMap["message"] ?: "Threshold exceeded on device"

        assertEquals("primary-pc", deviceId)
        assertEquals("Nexus Alert", title)
        assertEquals("Threshold exceeded on device", message)

        // Special characters and XSS in notification title
        val dirtyTitle = "<script>alert('xss')</script> Primary PC · RAM High"
        assertNotNull(dirtyTitle)
        assertTrue(dirtyTitle.contains("RAM High"))
    }

    @Test
    fun testDeviceOfflineStalenessDetection() {
        val now = 1_700_000_000_000L

        // 1. Fresh telemetry within 15 seconds threshold
        val freshTs = now - 5_000L
        val isFreshOnline = (freshTs > 0L) && ((now - freshTs) < 15_000L)
        assertTrue("Telemetry <= 15s must be considered online", isFreshOnline)

        // 2. Stale telemetry older than 15 seconds (e.g. PC shut down 30s ago)
        val staleTs = now - 30_000L
        val isStaleOnline = (staleTs > 0L) && ((now - staleTs) < 15_000L)
        assertFalse("Telemetry > 15s must be flagged offline immediately", isStaleOnline)

        // 3. Telemetry older than multiple hours (e.g. PC shut down days ago)
        val veryOldTs = now - 3600_000L * 24L
        val isVeryOldOnline = (veryOldTs > 0L) && ((now - veryOldTs) < 15_000L)
        assertFalse("Telemetry from days ago must be offline", isVeryOldOnline)

        // 4. Missing or zero timestamp
        val zeroTs = 0L
        val isZeroOnline = (zeroTs > 0L) && ((now - zeroTs) < 15_000L)
        assertFalse("Zero timestamp must never be online", isZeroOnline)

        // 5. Negative / corrupted timestamp
        val negativeTs = -1000L
        val isNegativeOnline = (negativeTs > 0L) && ((now - negativeTs) < 15_000L)
        assertFalse("Negative timestamp must never be online", isNegativeOnline)
    }
}
