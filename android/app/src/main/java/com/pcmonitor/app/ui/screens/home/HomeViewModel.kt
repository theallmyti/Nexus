package com.pcmonitor.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pcmonitor.app.data.model.Alert
import com.pcmonitor.app.data.model.Device
import com.pcmonitor.app.data.repository.AlertRepository
import com.pcmonitor.app.data.repository.DeviceRepository
import com.pcmonitor.app.data.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val userName: String = "User",
    val devices: List<Device> = emptyList(),
    val totalCount: Int = 0,
    val onlineCount: Int = 0,
    val offlineCount: Int = 0,
    val criticalCount: Int = 0,
    val recentAlerts: List<Alert> = emptyList(),
    val unreadAlertCount: Int = 0,
    val useFahrenheit: Boolean = false,
    val isLoading: Boolean = false
)

class HomeViewModel(
    private val deviceRepository: DeviceRepository,
    private val alertRepository: AlertRepository,
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        deviceRepository.devices,
        alertRepository.alerts,
        preferencesRepository.userName,
        preferencesRepository.useFahrenheit
    ) { devices, alerts, name, fahrenheit ->
        val total = devices.size
        val online = devices.count { it.isOnline }
        val offline = devices.count { !it.isOnline }
        val critical = devices.count { dev ->
            dev.isOnline && (
                dev.hardwareStats.cpu.averageTemperature >= 85f ||
                (dev.hardwareStats.gpu?.temperature ?: 0f) >= 80f ||
                dev.hardwareStats.ram.usagePercent >= 90f
            )
        }
        val unreadCount = alerts.count { !it.isRead }

        HomeUiState(
            userName = if (name.isNotBlank()) name else "User",
            devices = devices,
            totalCount = total,
            onlineCount = online,
            offlineCount = offline,
            criticalCount = critical,
            recentAlerts = alerts.take(3),
            unreadAlertCount = unreadCount,
            useFahrenheit = fahrenheit,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState(isLoading = true)
    )
}

