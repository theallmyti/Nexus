package com.pcmonitor.app.ui.screens.details

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pcmonitor.app.data.model.Device
import com.pcmonitor.app.data.repository.DeviceRepository
import com.pcmonitor.app.data.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class DetailTab(val label: String) {
    OVERVIEW("Overview"),
    CPU("CPU"),
    GPU("GPU"),
    RAM("RAM"),
    BATTERY("Battery")
}

data class DeviceDetailsUiState(
    val device: Device? = null,
    val selectedTab: DetailTab = DetailTab.OVERVIEW,
    val useFahrenheit: Boolean = false,
    val isLoading: Boolean = false,
    val notFound: Boolean = false
)

class DeviceDetailsViewModel(
    private val deviceId: String,
    private val deviceRepository: DeviceRepository,
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    var selectedTab by mutableStateOf(DetailTab.OVERVIEW)
        private set

    val uiState: StateFlow<DeviceDetailsUiState> = combine(
        deviceRepository.devices,
        preferencesRepository.useFahrenheit
    ) { devices, fahrenheit ->
        val dev = devices.find { it.id == deviceId }
        DeviceDetailsUiState(
            device = dev,
            selectedTab = selectedTab,
            useFahrenheit = fahrenheit,
            isLoading = false,
            notFound = dev == null
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DeviceDetailsUiState(isLoading = true)
    )

    init {
        // Without this the live loop kept polling the first device in the list, so any other
        // device's page showed only the 15 s snapshot.
        deviceRepository.setActiveDevice(deviceId)
    }

    fun onTabSelected(tab: DetailTab) {
        selectedTab = tab
    }

    fun unpairDevice(onComplete: () -> Unit) {
        viewModelScope.launch {
            deviceRepository.removeDevice(deviceId)
            onComplete()
        }
    }
}

