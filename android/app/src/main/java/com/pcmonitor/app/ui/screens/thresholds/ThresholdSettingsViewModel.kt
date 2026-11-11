package com.pcmonitor.app.ui.screens.thresholds

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pcmonitor.app.data.mock.MockDeviceRepository
import com.pcmonitor.app.data.repository.DeviceRepository
import com.pcmonitor.app.data.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ThresholdSettingsViewModel(
    private val preferencesRepository: UserPreferencesRepository,
    private val deviceRepository: DeviceRepository? = null
) : ViewModel() {

    var cpuThreshold by mutableFloatStateOf(90f)
    var gpuThreshold by mutableFloatStateOf(85f)
    var ramThreshold by mutableFloatStateOf(90f)
    var diskThreshold by mutableFloatStateOf(10f)

    var cDriveThreshold by mutableFloatStateOf(10f)
    var dDriveThreshold by mutableFloatStateOf(10f)

    var saveFeedback by androidx.compose.runtime.mutableStateOf<String?>(null)

    init {
        viewModelScope.launch {
            cpuThreshold = preferencesRepository.cpuThreshold.first()
            gpuThreshold = preferencesRepository.gpuThreshold.first()
            ramThreshold = preferencesRepository.ramThreshold.first()
            diskThreshold = preferencesRepository.diskThreshold.first()
        }
    }

    fun saveChanges() {
        viewModelScope.launch {
            saveFeedback = "Saving thresholds..."
            preferencesRepository.saveThresholds(
                cpu = cpuThreshold,
                gpu = gpuThreshold,
                ram = ramThreshold,
                disk = diskThreshold
            )
            (deviceRepository as? MockDeviceRepository)?.resetAlertCooldowns()
            saveFeedback = "Settings saved & synced to cloud!"
        }
    }

    fun resetToDefaults() {
        cpuThreshold = 90f
        gpuThreshold = 85f
        ramThreshold = 90f
        diskThreshold = 10f
        cDriveThreshold = 10f
        dDriveThreshold = 10f
        saveChanges()
        saveFeedback = "Reset to default thresholds."
    }
}

