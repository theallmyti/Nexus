package com.pcmonitor.app.ui.screens.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pcmonitor.app.data.model.UserProfile
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

    // --- Profile picture ---
    var avatarBusy by mutableStateOf(false)
        private set
    var avatarError by mutableStateOf<String?>(null)
        private set

    // Load the profile once at start so a picture set on another phone shows up here too.
    init {
        viewModelScope.launch { authRepository.fetchProfile() }
    }

    fun setPixelAvatar(id: String?) = avatarAction { authRepository.setPixelAvatar(id) }

    fun uploadAvatar(jpeg: ByteArray) = avatarAction { authRepository.uploadAvatarPhoto(jpeg) }

    private fun avatarAction(call: suspend () -> Result<*>) {
        if (avatarBusy) return
        avatarBusy = true
        avatarError = null
        viewModelScope.launch {
            call().onFailure { avatarError = it.message }
            avatarBusy = false
        }
    }

    // --- Profile dialog (name card) ---
    var profile by mutableStateOf<UserProfile?>(null)
        private set
    var profileName by mutableStateOf("")
    var profileError by mutableStateOf<String?>(null)
        private set
    var profileSaving by mutableStateOf(false)
        private set

    /** Loads the account's profile for the dialog (name, member since, device count). */
    fun openProfile() {
        profileError = null
        profileName = uiState.value.userName
        viewModelScope.launch {
            authRepository.fetchProfile()
                .onSuccess { p ->
                    profile = p
                    profileName = p.name ?: uiState.value.userName
                }
                .onFailure { profileError = it.message }
        }
    }

    fun saveProfileName(onSaved: () -> Unit) {
        val name = profileName.trim()
        if (name.isEmpty() || name.length > 40) {
            profileError = "Enter a name (up to 40 characters)."
            return
        }
        profileSaving = true
        profileError = null
        viewModelScope.launch {
            authRepository.updateName(name)
                .onSuccess { saved ->
                    preferencesRepository.setUserName(saved)
                    onSaved()
                }
                .onFailure { profileError = it.message }
            profileSaving = false
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

