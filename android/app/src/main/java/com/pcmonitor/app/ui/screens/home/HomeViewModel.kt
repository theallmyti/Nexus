package com.pcmonitor.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pcmonitor.app.data.model.Alert
import com.pcmonitor.app.data.model.Device
import com.pcmonitor.app.data.model.problem
import com.pcmonitor.app.data.repository.AlertRepository
import com.pcmonitor.app.data.repository.DeviceRepository
import com.pcmonitor.app.data.repository.UserPreferencesRepository
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
    val hasNewAlerts: Boolean = false,
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
        preferencesRepository.useFahrenheit,
        com.pcmonitor.app.notification.AlertsSeen.lastSeen
    ) { devices, alerts, name, fahrenheit, lastSeen ->
        val total = devices.size
        val online = devices.count { it.isOnline }
        val offline = devices.count { !it.isOnline }
        val critical = devices.count { it.problem() != null }
        // Problems first, then online, then offline; name keeps the order stable between refreshes.
        val sorted = devices.sortedWith(
            compareBy<Device>({ it.problem() == null }, { !it.isOnline }, { it.name.lowercase() })
        )
        val unreadCount = alerts.count { !it.isRead }
        val hasNew = com.pcmonitor.app.notification.AlertsSeen.hasNew(alerts, lastSeen)

        HomeUiState(
            userName = if (name.isNotBlank()) name else "User",
            devices = sorted,
            totalCount = total,
            onlineCount = online,
            offlineCount = offline,
            criticalCount = critical,
            recentAlerts = alerts.take(5),
            unreadAlertCount = unreadCount,
            hasNewAlerts = hasNew,
            useFahrenheit = fahrenheit,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState(isLoading = true)
    )

    var isRefreshing by mutableStateOf(false)
        private set

    fun refresh() {
        if (isRefreshing) return
        viewModelScope.launch {
            isRefreshing = true
            try { deviceRepository.refresh() } finally { isRefreshing = false }
        }
    }

    fun markAllAlertsRead() {
        viewModelScope.launch {
            alertRepository.alerts.value.filter { !it.isRead }.forEach { alertRepository.markAsRead(it.id) }
        }
    }
}

