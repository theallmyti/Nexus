package com.pcmonitor.app.ui.screens.thresholds

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pcmonitor.app.data.mock.MockDeviceRepository
import com.pcmonitor.app.data.model.Device
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

    var cDriveThreshold by mutableFloatStateOf(10f)
    var dDriveThreshold by mutableFloatStateOf(10f)

    var saveFeedback by mutableStateOf<String?>(null)

    init {
        loadThresholdsForSelection(selectedDeviceId)
    }

    fun selectDevice(deviceId: String?) {
        selectedDeviceId = deviceId
        saveFeedback = null
        loadThresholdsForSelection(deviceId)
    }

    private fun loadThresholdsForSelection(deviceId: String?) {
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
        }
    }

    fun saveChanges() {
        val devId = selectedDeviceId
        viewModelScope.launch {
            saveFeedback = "Saving thresholds..."
            if (devId == null) {
                preferencesRepository.saveThresholds(
                    cpu = cpuThreshold,
                    gpu = gpuThreshold,
                    ram = ramThreshold,
                    disk = diskThreshold
                )
                saveFeedback = "Global thresholds saved & synced!"
            } else {
                preferencesRepository.saveDeviceThresholds(
                    deviceId = devId,
                    cpu = cpuThreshold,
                    gpu = gpuThreshold,
                    ram = ramThreshold,
                    disk = diskThreshold
                )
                isCustomForSelectedDevice = true
                saveFeedback = "Device thresholds saved & synced!"
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
                cDriveThreshold = 10f
                dDriveThreshold = 10f
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
