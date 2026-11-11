package com.pcmonitor.app.ui.screens.devices

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

enum class DeviceFilterStatus { ALL, ONLINE, OFFLINE }

data class DevicesUiState(
    val devices: List<Device> = emptyList(),
    val filteredDevices: List<Device> = emptyList(),
    val searchQuery: String = "",
    val filterStatus: DeviceFilterStatus = DeviceFilterStatus.ALL,
    val useFahrenheit: Boolean = false,
    val isLoading: Boolean = false
)

class DevicesViewModel(
    private val deviceRepository: DeviceRepository,
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    var searchQuery by mutableStateOf("")
        private set
    var filterStatus by mutableStateOf(DeviceFilterStatus.ALL)
        private set

    val uiState: StateFlow<DevicesUiState> = combine(
        deviceRepository.devices,
        preferencesRepository.useFahrenheit
    ) { devices, fahrenheit ->
        filterDevices(devices, searchQuery, filterStatus, fahrenheit)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DevicesUiState(isLoading = true)
    )

    fun onSearchQueryChanged(query: String) {
        searchQuery = query
        // Re-trigger update through repository state flow emission or internal logic
    }

    fun onFilterStatusChanged(status: DeviceFilterStatus) {
        filterStatus = status
    }

    private fun filterDevices(
        devices: List<Device>,
        query: String,
        status: DeviceFilterStatus,
        fahrenheit: Boolean
    ): DevicesUiState {
        val filtered = devices.filter { device ->
            val matchesQuery = query.isBlank() ||
                device.name.contains(query, ignoreCase = true) ||
                device.os.contains(query, ignoreCase = true) ||
                device.type.label.contains(query, ignoreCase = true)

            val matchesStatus = when (status) {
                DeviceFilterStatus.ALL -> true
                DeviceFilterStatus.ONLINE -> device.isOnline
                DeviceFilterStatus.OFFLINE -> !device.isOnline
            }

            matchesQuery && matchesStatus
        }

        return DevicesUiState(
            devices = devices,
            filteredDevices = filtered,
            searchQuery = query,
            filterStatus = status,
            useFahrenheit = fahrenheit,
            isLoading = false
        )
    }
}

