package com.pcmonitor.app

import com.pcmonitor.app.data.model.AppUpdateInfo
import com.pcmonitor.app.ui.updater.AppUpdateUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AppUpdateTest {

    @Test
    fun testAppUpdateInfoModel() {
        val info = AppUpdateInfo(
            updateAvailable = true,
            latestVersionCode = 2,
            latestVersionName = "1.0.1",
            downloadUrl = "https://example.com/nexus.apk",
            releaseNotes = "- Fixed light mode\n- Added auto-update",
            isMandatory = false
        )

        assertTrue(info.updateAvailable)
        assertEquals(2, info.latestVersionCode)
        assertEquals("1.0.1", info.latestVersionName)
        assertEquals("https://example.com/nexus.apk", info.downloadUrl)
        assertTrue(info.releaseNotes.contains("Fixed light mode"))
        assertFalse(info.isMandatory)
    }

    @Test
    fun testAppUpdateUiStateTransitions() {
        val idle = AppUpdateUiState.Idle
        assertEquals(AppUpdateUiState.Idle, idle)

        val checking = AppUpdateUiState.Checking
        assertEquals(AppUpdateUiState.Checking, checking)

        val info = AppUpdateInfo(
            updateAvailable = true,
            latestVersionCode = 2,
            latestVersionName = "1.0.1",
            downloadUrl = "https://example.com/nexus.apk",
            releaseNotes = "Notes"
        )
        val updateAvailable = AppUpdateUiState.UpdateAvailable(info, isManualCheck = true)
        assertTrue(updateAvailable.isManualCheck)
        assertEquals("1.0.1", updateAvailable.info.latestVersionName)

        val downloading = AppUpdateUiState.Downloading(0.45f, info)
        assertEquals(0.45f, downloading.progress, 0.001f)

        val dummyFile = File("fake.apk")
        val ready = AppUpdateUiState.ReadyToInstall(dummyFile, info)
        assertEquals(dummyFile, ready.apkFile)

        val upToDate = AppUpdateUiState.UpToDate("1.0.0")
        assertEquals("1.0.0", upToDate.versionName)

        val error = AppUpdateUiState.Error("Network failure", isManualCheck = true)
        assertEquals("Network failure", error.message)
        assertTrue(error.isManualCheck)
    }

    @Test
    fun testUpdateAvailabilityComparison() {
        val currentVersionCode = 1
        val remoteVersionCode = 2

        val updateNeeded = remoteVersionCode > currentVersionCode
        assertTrue("Higher remote version code must trigger update", updateNeeded)

        val sameVersionCode = 1
        val sameUpdateNeeded = sameVersionCode > currentVersionCode
        assertFalse("Same version code must not trigger update", sameUpdateNeeded)
    }
}
