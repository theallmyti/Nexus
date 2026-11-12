package com.pcmonitor.app.ui.screens.alerts

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pcmonitor.app.data.model.Alert
import com.pcmonitor.app.data.model.AlertSeverity
import com.pcmonitor.app.data.repository.AlertRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AlertFilter { ALL, WARNING, CRITICAL, OFFLINE }

data class AlertsUiState(
    val alerts: List<Alert> = emptyList(),
    val filteredAlerts: List<Alert> = emptyList(),
    val currentFilter: AlertFilter = AlertFilter.ALL,
    val unreadCount: Int = 0
)

class AlertsViewModel(
    private val alertRepository: AlertRepository
) : ViewModel() {

    var currentFilter by mutableStateOf(AlertFilter.ALL)
        private set

    // The filter is a flow too, so picking a chip recomputes the list (Compose state read inside
    // `combine` is not observed: the list only changed when a new alert arrived).
    private val filterFlow = MutableStateFlow(AlertFilter.ALL)

    val uiState: StateFlow<AlertsUiState> = alertRepository.alerts.combine(filterFlow) { alerts, currentFilter ->
        val filtered = when (currentFilter) {
            AlertFilter.ALL -> alerts
            AlertFilter.WARNING -> alerts.filter { it.severity == AlertSeverity.WARNING }
            AlertFilter.CRITICAL -> alerts.filter { it.severity == AlertSeverity.CRITICAL }
            AlertFilter.OFFLINE -> alerts.filter { it.severity == AlertSeverity.OFFLINE }
        }
        AlertsUiState(
            alerts = alerts,
            filteredAlerts = filtered,
            currentFilter = currentFilter,
            unreadCount = alerts.count { !it.isRead }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AlertsUiState()
    )

    fun onFilterSelected(filter: AlertFilter) {
        currentFilter = filter
        filterFlow.value = filter
    }

    fun markAsRead(alertId: String) {
        viewModelScope.launch {
            alertRepository.markAsRead(alertId)
        }
    }

    fun clearAlert(alertId: String) {
        viewModelScope.launch {
            alertRepository.clearAlert(alertId)
        }
    }

    fun clearAllAlerts() {
        viewModelScope.launch {
            alertRepository.clearAllAlerts()
        }
    }
}

