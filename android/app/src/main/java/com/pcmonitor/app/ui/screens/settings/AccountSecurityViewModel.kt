package com.pcmonitor.app.ui.screens.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pcmonitor.app.data.repository.AuthRepository
import com.pcmonitor.app.data.repository.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Settings → Account → Account & Security: change password, sign out everywhere, export my data,
// delete account (backend: auth.ts).
class AccountSecurityViewModel(
    private val authRepository: AuthRepository,
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    var busy by mutableStateOf(false)
        private set
    /** Result line under the list (green when ok). */
    var message by mutableStateOf<String?>(null)
        private set
    var messageIsError by mutableStateOf(false)
        private set

    // Change password dialog
    var currentPassword by mutableStateOf("")
    var newPassword by mutableStateOf("")
    var confirmPassword by mutableStateOf("")
    var passwordError by mutableStateOf<String?>(null)
        private set

    // Delete account dialog: the user types DELETE to confirm.
    var deleteConfirmText by mutableStateOf("")

    fun resetPasswordForm() {
        currentPassword = ""
        newPassword = ""
        confirmPassword = ""
        passwordError = null
    }

    fun changePassword(onDone: () -> Unit) {
        passwordError = when {
            currentPassword.isEmpty() -> "Enter your current password."
            newPassword.length < 8 -> "Use at least 8 characters for the new password."
            newPassword != confirmPassword -> "The new passwords don't match."
            newPassword == currentPassword -> "Pick a password different from the current one."
            else -> null
        }
        if (passwordError != null) return
        busy = true
        viewModelScope.launch {
            authRepository.changePassword(currentPassword, newPassword)
                .onSuccess {
                    resetPasswordForm()
                    show("Password changed. Your other devices were signed out.", error = false)
                    onDone()
                }
                .onFailure { passwordError = it.message }
            busy = false
        }
    }

    fun signOutEverywhere(onSignedOut: () -> Unit) {
        runThenSignOut(onSignedOut) { authRepository.signOutEverywhere() }
    }

    fun deleteAccount(onSignedOut: () -> Unit) {
        if (deleteConfirmText.trim() != "DELETE") return
        runThenSignOut(onSignedOut) { authRepository.deleteAccount() }
    }

    // Runs an account-ending call, then clears this phone's session and leaves for the login screen.
    private fun runThenSignOut(onSignedOut: () -> Unit, call: suspend () -> Result<Unit>) {
        busy = true
        viewModelScope.launch {
            call()
                .onSuccess {
                    preferencesRepository.clearUserInfo()
                    onSignedOut()
                }
                .onFailure { show(it.message ?: "Something went wrong.", error = true) }
            busy = false
        }
    }

    /** Writes the account's data (JSON) to the file the user picked. */
    fun exportTo(uri: Uri, resolver: ContentResolver) {
        busy = true
        viewModelScope.launch {
            authRepository.exportData()
                .onSuccess { json ->
                    val ok = withContext(Dispatchers.IO) {
                        runCatching {
                            resolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) } != null
                        }.getOrDefault(false)
                    }
                    if (ok) show("Your data was saved.", error = false)
                    else show("Couldn't write the file. Try another location.", error = true)
                }
                .onFailure { show(it.message ?: "Couldn't export your data.", error = true) }
            busy = false
        }
    }

    private fun show(text: String, error: Boolean) {
        message = text
        messageIsError = error
    }
}
