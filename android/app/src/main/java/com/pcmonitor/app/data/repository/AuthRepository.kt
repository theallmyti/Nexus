package com.pcmonitor.app.data.repository

import com.pcmonitor.app.data.model.AuthState
import com.pcmonitor.app.data.model.LoginRequest
import com.pcmonitor.app.data.model.RegisterRequest
import com.pcmonitor.app.data.model.User
import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val authState: StateFlow<AuthState>
    suspend fun login(request: LoginRequest): Result<User>
    suspend fun register(request: RegisterRequest): Result<User>
    suspend fun logout()
    suspend fun requestPasswordReset(email: String): Result<String>
    /** Sets a new password with the emailed 6-digit code and signs in. */
    suspend fun resetPassword(email: String, code: String, newPassword: String): Result<User>
    suspend fun isLoggedIn(): Boolean
}

