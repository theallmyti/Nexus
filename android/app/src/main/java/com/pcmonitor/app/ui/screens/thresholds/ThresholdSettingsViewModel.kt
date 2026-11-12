package com.pcmonitor.app.ui.screens.thresholds

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pcmonitor.app.data.mock.MockDeviceRepository
import com.pcmonitor.app.data.model.Device
import com.pcmonitor.app.data.model.DiskStats
import com.pcmonitor.app.data.repository.DeviceRepository
import com.pcmonitor.app.data.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ThresholdSettingsViewModel(
    private val preferencesRepository: UserPreferencesRepository,
    private val deviceRepository: DeviceRepository? = null,
    val initialDeviceId: String? = null
) : ViewModel() {

    val devices: StateFlow<List<Device>> = (deviceRepository?.devices ?: flowOf(emptyList()))
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    var selectedDeviceId by mutableStateOf<String?>(initialDeviceId)
    var isCustomForSelectedDevice by mutableStateOf(false)

    var cpuThreshold by mutableFloatStateOf(90f)
    var gpuThreshold by mutableFloatStateOf(85f)
    var ramThreshold by mutableFloatStateOf(90f)
    var diskThreshold by mutableFloatStateOf(10f)


    var saveFeedback by mutableStateOf<String?>(null)

    // Per Drive Settings (one device): drives with their own limit, as saved on the server, and
    // the edits not saved yet. A drive missing from the map uses the device's disk default.
    val driveLimits = mutableStateMapOf<String, Float>()
    private val savedDriveLimits = mutableMapOf<String, Float>()
    var expandedDrive by mutableStateOf<String?>(null)

    /** The selected device's drives (empty for the account-wide scope). */
    fun drivesForSelection(): List<DiskStats> =
        devices.value.find { it.id == selectedDeviceId }?.hardwareStats?.disks.orEmpty()

    fun setDriveLimit(drive: String, pct: Float) {
        driveLimits[drive] = pct
    }

    fun useDefaultForDrive(drive: String) {
        driveLimits.remove(drive)
    }

    init {
        loadThresholdsForSelection(selectedDeviceId)
    }

    fun selectDevice(deviceId: String?) {
        selectedDeviceId = deviceId
        saveFeedback = null
        expandedDrive = null
        loadThresholdsForSelection(deviceId)
    }

    private fun loadThresholdsForSelection(deviceId: String?) {
        driveLimits.clear()
        savedDriveLimits.clear()
        viewModelScope.launch {
            if (deviceId == null) {
                cpuThreshold = preferencesRepository.cpuThreshold.first()
                gpuThreshold = preferencesRepository.gpuThreshold.first()
                ramThreshold = preferencesRepository.ramThreshold.first()
                diskThreshold = preferencesRepository.diskThreshold.first()
                isCustomForSelectedDevice = false
            } else {
                val hasCustom = preferencesRepository.hasCustomThresholds(deviceId).first()
                isCustomForSelectedDevice = hasCustom
                cpuThreshold = preferencesRepository.getDeviceCpuThreshold(deviceId).first()
                gpuThreshold = preferencesRepository.getDeviceGpuThreshold(deviceId).first()
                ramThreshold = preferencesRepository.getDeviceRamThreshold(deviceId).first()
                diskThreshold = preferencesRepository.getDeviceDiskThreshold(deviceId).first()
            }
            // The server copy is what alerts use (and survives a reinstall or a second phone):
            // show it over the values stored on this phone.
            preferencesRepository.fetchThresholdsFromConvex(deviceId)?.let { server ->
                if (selectedDeviceId != deviceId) return@let // the user switched device meanwhile
                server.cpu?.let { cpuThreshold = it }
                server.gpu?.let { gpuThreshold = it }
                server.ram?.let { ramThreshold = it }
                server.disk?.let { diskThreshold = it }
                if (deviceId != null) isCustomForSelectedDevice = server.isCustom
            }
            if (deviceId != null) {
                preferencesRepository.fetchDiskThresholds(deviceId)?.let { limits ->
                    if (selectedDeviceId != deviceId) return@let
                    savedDriveLimits.putAll(limits)
                    driveLimits.putAll(limits)
                }
            }
        }
    }

    fun saveChanges() {
        val devId = selectedDeviceId
        viewModelScope.launch {
            saveFeedback = "Saving thresholds..."
            val synced = if (devId == null) {
                preferencesRepository.saveThresholds(
                    cpu = cpuThreshold,
                    gpu = gpuThreshold,
                    ram = ramThreshold,
                    disk = diskThreshold
                )
            } else {
                preferencesRepository.saveDeviceThresholds(
                    deviceId = devId,
                    cpu = cpuThreshold,
                    gpu = gpuThreshold,
                    ram = ramThreshold,
                    disk = diskThreshold
                ).also { isCustomForSelectedDevice = true }
            }
            // Per Drive Settings: send only the drives whose limit changed (set or back to default).
            val drivesSynced = if (devId == null) true else {
                val changed = (driveLimits.keys + savedDriveLimits.keys).filter { driveLimits[it] != savedDriveLimits[it] }
                changed.map { drive ->
                    val ok = preferencesRepository.saveDiskThreshold(devId, drive, driveLimits[drive])
                    if (ok) {
                        driveLimits[drive]?.let { savedDriveLimits[drive] = it } ?: savedDriveLimits.remove(drive)
                    }
                    ok
                }.all { it }
            }
            saveFeedback = when {
                !synced || !drivesSynced -> "Saved on this phone, but couldn't reach Nexus. Alerts still use the old values; try again when online."
                devId == null -> "Global thresholds saved & synced!"
                else -> "Device thresholds saved & synced!"
            }
            (deviceRepository as? MockDeviceRepository)?.resetAlertCooldowns()
        }
    }

    fun resetToDefaults() {
        val devId = selectedDeviceId
        viewModelScope.launch {
            if (devId == null) {
                cpuThreshold = 90f
                gpuThreshold = 85f
                ramThreshold = 90f
                diskThreshold = 10f
                preferencesRepository.resetThresholds()
                saveFeedback = "Reset to default global thresholds."
            } else {
                preferencesRepository.resetDeviceThresholds(devId)
                isCustomForSelectedDevice = false
                // Reload values from global
                cpuThreshold = preferencesRepository.cpuThreshold.first()
                gpuThreshold = preferencesRepository.gpuThreshold.first()
                ramThreshold = preferencesRepository.ramThreshold.first()
                diskThreshold = preferencesRepository.diskThreshold.first()
                saveFeedback = "Reverted device to global defaults."
            }
            (deviceRepository as? MockDeviceRepository)?.resetAlertCooldowns()
        }
    }
}
