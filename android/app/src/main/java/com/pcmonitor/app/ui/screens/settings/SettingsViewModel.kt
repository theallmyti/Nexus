package com.pcmonitor.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pcmonitor.app.data.repository.AuthRepository
import com.pcmonitor.app.data.repository.DeviceRepository
import com.pcmonitor.app.data.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val userName: String = "User",
    val userEmail: String = "",
    val isDarkMode: Boolean = true,
    val useFahrenheit: Boolean = false,
    val deviceCount: Int = 0
)

class SettingsViewModel(
    private val authRepository: AuthRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val deviceRepository: DeviceRepository
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        combine(
            preferencesRepository.userName,
            preferencesRepository.userEmail,
            preferencesRepository.darkMode,
            preferencesRepository.useFahrenheit
        ) { name, email, dark, fahrenheit ->
            Triple(Pair(name, email), dark, fahrenheit)
        },
        deviceRepository.devices
    ) { (nameEmail, dark, fahrenheit), devices ->
        val (name, email) = nameEmail
        SettingsUiState(
            userName = if (name.isNotBlank()) name else (if (email.isNotBlank()) email.substringBefore("@") else "User"),
            userEmail = email,
            isDarkMode = dark,
            useFahrenheit = fahrenheit,
            deviceCount = devices.size
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    fun updateUserName(name: String) {
        viewModelScope.launch {
            preferencesRepository.setUserName(name)
        }
    }

    fun toggleTemperatureUnit() {
        viewModelScope.launch {
            val current = uiState.value.useFahrenheit
            preferencesRepository.setUseFahrenheit(!current)
        }
    }

    fun toggleDarkMode() {
        viewModelScope.launch {
            val current = uiState.value.isDarkMode
            preferencesRepository.setDarkMode(!current)
        }
    }

    fun signOut(onSignedOut: () -> Unit) {
        viewModelScope.launch {
            authRepository.logout()
            preferencesRepository.clearUserInfo()
            onSignedOut()
        }
    }
}

