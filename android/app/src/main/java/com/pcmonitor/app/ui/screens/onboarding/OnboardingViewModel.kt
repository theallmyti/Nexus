package com.pcmonitor.app.ui.screens.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pcmonitor.app.data.repository.UserPreferencesRepository
import kotlinx.coroutines.launch

data class OnboardingPageData(
    val title: String,
    val description: String,
    val pageType: OnboardingPageType
)

enum class OnboardingPageType {
    MONITOR_ALL, TRACK_PERFORMANCE, STAY_ALERTED
}

class OnboardingViewModel(
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    var currentPage by mutableIntStateOf(0)
        private set

    val pages = listOf(
        OnboardingPageData(
            title = "Monitor All\nYour Devices",
            description = "Track temperatures, load, battery\nand more — from anywhere.",
            pageType = OnboardingPageType.MONITOR_ALL
        ),
        OnboardingPageData(
            title = "Real-Time\nPerformance",
            description = "Inspect CPU cores, GPU load, memory\nand thermal throttling instantly.",
            pageType = OnboardingPageType.TRACK_PERFORMANCE
        ),
        OnboardingPageData(
            title = "Smart Hardware\nAlerts",
            description = "Get notified before overheating or\ncritical failures happen.",
            pageType = OnboardingPageType.STAY_ALERTED
        )
    )

    fun onNextClick(onFinished: () -> Unit) {
        if (currentPage < pages.size - 1) {
            currentPage++
        } else {
            completeOnboarding(onFinished)
        }
    }

    fun onSkipClick(onFinished: () -> Unit) {
        completeOnboarding(onFinished)
    }

    private fun completeOnboarding(onFinished: () -> Unit) {
        viewModelScope.launch {
            preferencesRepository.setOnboardingCompleted(true)
            onFinished()
        }
    }
}

