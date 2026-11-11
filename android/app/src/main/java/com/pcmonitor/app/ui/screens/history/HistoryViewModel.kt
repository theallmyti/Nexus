package com.pcmonitor.app.ui.screens.history

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pcmonitor.app.data.model.Device
import com.pcmonitor.app.data.model.HistorySummary
import com.pcmonitor.app.data.repository.DeviceRepository
import com.pcmonitor.app.data.repository.HistoryRepository
import com.pcmonitor.app.data.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HistoryUiState(
    val devices: List<Device> = emptyList(),
    val selectedDeviceId: String = "",
    val summary: HistorySummary? = null,
    val selectedDays: Int = 7,
    val useFahrenheit: Boolean = false,
    val isLoading: Boolean = false
)

class HistoryViewModel(
    initialDeviceId: String,
    private val historyRepository: HistoryRepository,
    private val deviceRepository: DeviceRepository,
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    var selectedDeviceId by mutableStateOf(initialDeviceId)
        private set
    var selectedDays by mutableIntStateOf(7)
        private set

    private val _summary = MutableStateFlow<HistorySummary?>(null)

    val uiState: StateFlow<HistoryUiState> = combine(
        deviceRepository.devices,
        _summary,
        preferencesRepository.useFahrenheit
    ) { devices, summary, fahrenheit ->
        HistoryUiState(
            devices = devices,
            selectedDeviceId = selectedDeviceId,
            summary = summary,
            selectedDays = selectedDays,
            useFahrenheit = fahrenheit,
            isLoading = summary == null
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HistoryUiState(isLoading = true)
    )

    init {
        loadHistory()
    }

    fun onDeviceSelected(deviceId: String) {
        selectedDeviceId = deviceId
        loadHistory()
    }

    fun onDaysSelected(days: Int) {
        selectedDays = days
        loadHistory()
    }

    private fun loadHistory() {
        viewModelScope.launch {
            val devId = if (selectedDeviceId.isNotBlank()) selectedDeviceId else "j572h79gypvrbmm71dyaa7bbfd8hny36"
            _summary.value = historyRepository.getHistoryForDevice(devId, selectedDays)
        }
    }
}

