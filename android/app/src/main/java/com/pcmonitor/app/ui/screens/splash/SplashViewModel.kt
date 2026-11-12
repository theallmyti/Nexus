package com.pcmonitor.app.ui.screens.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pcmonitor.app.data.repository.UserPreferencesRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed class SplashDestination {
    data object Loading : SplashDestination()
    data object Onboarding : SplashDestination()
    data object Login : SplashDestination()
    data object Home : SplashDestination()
}

class SplashViewModel(
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _destination = MutableStateFlow<SplashDestination>(SplashDestination.Loading)
    val destination: StateFlow<SplashDestination> = _destination.asStateFlow()

    init {
        determineDestination()
    }

    private fun determineDestination() {
        viewModelScope.launch {
            // Keep splash visible for 2.2 seconds to show the retro artwork
            delay(2200L)
            val onboardingCompleted = preferencesRepository.onboardingCompleted.first()
            val isLoggedIn = preferencesRepository.isLoggedIn.first()
            val sessionToken = preferencesRepository.getSessionToken()

            if (isLoggedIn && sessionToken.isNullOrBlank()) {
                preferencesRepository.setLoggedIn(false)
            }

            _destination.value = when {
                !onboardingCompleted -> SplashDestination.Onboarding
                !isLoggedIn || sessionToken.isNullOrBlank() -> SplashDestination.Login
                else -> SplashDestination.Home
            }
        }
    }
}

