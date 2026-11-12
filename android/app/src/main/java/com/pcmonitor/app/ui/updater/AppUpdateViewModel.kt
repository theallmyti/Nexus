package com.pcmonitor.app.ui.updater

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pcmonitor.app.BuildConfig
import com.pcmonitor.app.data.model.AppUpdateInfo
import com.pcmonitor.app.data.repository.AppUpdateRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed interface AppUpdateUiState {
    data object Idle : AppUpdateUiState
    data object Checking : AppUpdateUiState
    data class UpdateAvailable(val info: AppUpdateInfo, val isManualCheck: Boolean) : AppUpdateUiState
    data class Downloading(val progress: Float, val info: AppUpdateInfo) : AppUpdateUiState
    data class ReadyToInstall(val apkFile: File, val info: AppUpdateInfo) : AppUpdateUiState
    data class UpToDate(val versionName: String) : AppUpdateUiState
    data class Error(val message: String, val isManualCheck: Boolean) : AppUpdateUiState
}

class AppUpdateViewModel(
    private val repository: AppUpdateRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<AppUpdateUiState>(AppUpdateUiState.Idle)
    val uiState: StateFlow<AppUpdateUiState> = _uiState.asStateFlow()

    fun checkForUpdate(isManualCheck: Boolean = false) {
        viewModelScope.launch {
            if (isManualCheck) {
                _uiState.value = AppUpdateUiState.Checking
            }
            val currentCode = BuildConfig.VERSION_CODE
            val result = repository.checkForUpdate(currentCode)
            result.onSuccess { info ->
                if (info.updateAvailable) {
                    _uiState.value = AppUpdateUiState.UpdateAvailable(info, isManualCheck)
                } else if (isManualCheck) {
                    _uiState.value = AppUpdateUiState.UpToDate(BuildConfig.VERSION_NAME)
                } else {
                    _uiState.value = AppUpdateUiState.Idle
                }
            }.onFailure { err ->
                if (isManualCheck) {
                    _uiState.value = AppUpdateUiState.Error(err.message ?: "Failed to check for updates", isManualCheck = true)
                } else {
                    _uiState.value = AppUpdateUiState.Idle
                }
            }
        }
    }

    fun startDownload(info: AppUpdateInfo) {
        viewModelScope.launch {
            _uiState.value = AppUpdateUiState.Downloading(0f, info)
            val result = repository.downloadApk(info.downloadUrl) { progress ->
                _uiState.value = AppUpdateUiState.Downloading(progress, info)
            }
            result.onSuccess { file ->
                _uiState.value = AppUpdateUiState.ReadyToInstall(file, info)
                repository.installApk(file)
            }.onFailure { err ->
                _uiState.value = AppUpdateUiState.Error(err.message ?: "Download failed", isManualCheck = true)
            }
        }
    }

    fun installDownloadedApk(file: File) {
        repository.installApk(file)
    }

    fun dismiss() {
        _uiState.value = AppUpdateUiState.Idle
    }
}
