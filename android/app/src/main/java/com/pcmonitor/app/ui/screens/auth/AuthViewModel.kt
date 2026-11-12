package com.pcmonitor.app.ui.screens.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pcmonitor.app.data.model.LoginRequest
import com.pcmonitor.app.data.model.RegisterRequest
import com.pcmonitor.app.data.repository.AuthRepository
import com.pcmonitor.app.data.repository.UserPreferencesRepository
import kotlinx.coroutines.launch

class AuthViewModel(
    private val authRepository: AuthRepository,
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    // Login Form State
    var loginEmail by mutableStateOf("")
    var loginPassword by mutableStateOf("")
    var loginEmailError by mutableStateOf<String?>(null)
    var loginPasswordError by mutableStateOf<String?>(null)

    // Register Form State
    var registerName by mutableStateOf("")
    var registerEmail by mutableStateOf("")
    var registerPassword by mutableStateOf("")
    var registerConfirmPassword by mutableStateOf("")
    var registerNameError by mutableStateOf<String?>(null)
    var registerEmailError by mutableStateOf<String?>(null)
    var registerPasswordError by mutableStateOf<String?>(null)
    var registerConfirmPasswordError by mutableStateOf<String?>(null)
    // Cloudflare Turnstile: the current single-use token, and a counter that resets the widget
    // after every attempt so the next one gets a fresh token.
    var turnstileToken by mutableStateOf<String?>(null)
    var turnstileResetSignal by mutableStateOf(0)

    // Forgot Password Form State
    var forgotEmail by mutableStateOf("")
    var forgotEmailError by mutableStateOf<String?>(null)
    var forgotMessage by mutableStateOf<String?>(null)

    // Common State
    var isLoading by mutableStateOf(false)
    var generalError by mutableStateOf<String?>(null)

    fun login(onSuccess: () -> Unit) {
        if (!validateLogin()) return

        isLoading = true
        generalError = null

        viewModelScope.launch {
            val result = authRepository.login(
                LoginRequest(email = loginEmail.trim(), password = loginPassword)
            )
            isLoading = false
            result.fold(
                onSuccess = { user ->
                    preferencesRepository.saveUserInfo(user.id, user.name, user.email)
                    onSuccess()
                },
                onFailure = { error ->
                    generalError = error.message ?: "Authentication failed."
                }
            )
        }
    }

    fun register(onSuccess: () -> Unit) {
        if (!validateRegister()) return
        val token = turnstileToken
        if (token.isNullOrBlank()) {
            generalError = "Please complete the bot check below."
            return
        }

        isLoading = true
        generalError = null

        viewModelScope.launch {
            val result = authRepository.register(
                RegisterRequest(
                    name = registerName.trim(),
                    email = registerEmail.trim(),
                    password = registerPassword,
                    turnstileToken = token
                )
            )
            isLoading = false
            // The token was spent on this attempt (or is unusable): get a fresh one.
            turnstileToken = null
            turnstileResetSignal++
            result.fold(
                onSuccess = { user ->
                    preferencesRepository.saveUserInfo(user.id, user.name, user.email)
                    onSuccess()
                },
                onFailure = { error ->
                    generalError = error.message ?: "Registration failed."
                }
            )
        }
    }

    fun requestPasswordReset() {
        if (!validateForgot()) return

        isLoading = true
        generalError = null
        forgotMessage = null

        viewModelScope.launch {
            val result = authRepository.requestPasswordReset(forgotEmail.trim())
            isLoading = false
            result.fold(
                onSuccess = { msg ->
                    forgotMessage = msg
                },
                onFailure = { error ->
                    generalError = error.message ?: "Could not process password reset."
                }
            )
        }
    }

    private fun validateLogin(): Boolean {
        var valid = true
        if (loginEmail.isBlank() || !loginEmail.contains("@") || !loginEmail.contains(".")) {
            loginEmailError = "Please enter a valid email address."
            valid = false
        } else {
            loginEmailError = null
        }

        if (loginPassword.length < 8) {
            loginPasswordError = "Password must be at least 8 characters."
            valid = false
        } else {
            loginPasswordError = null
        }
        return valid
    }

    private fun validateRegister(): Boolean {
        var valid = true
        if (registerName.isBlank()) {
            registerNameError = "Please enter your name."
            valid = false
        } else {
            registerNameError = null
        }

        if (registerEmail.isBlank() || !registerEmail.contains("@") || !registerEmail.contains(".")) {
            registerEmailError = "Please enter a valid email address."
            valid = false
        } else {
            registerEmailError = null
        }

        if (registerPassword.length < 8) {
            registerPasswordError = "Password must be at least 8 characters."
            valid = false
        } else {
            registerPasswordError = null
        }

        if (registerConfirmPassword != registerPassword) {
            registerConfirmPasswordError = "Passwords do not match."
            valid = false
        } else {
            registerConfirmPasswordError = null
        }
        return valid
    }

    private fun validateForgot(): Boolean {
        return if (forgotEmail.isBlank() || !forgotEmail.contains("@") || !forgotEmail.contains(".")) {
            forgotEmailError = "Please enter a valid email address."
            false
        } else {
            forgotEmailError = null
            true
        }
    }
}

