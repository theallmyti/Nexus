package com.pcmonitor.app.data.repository

import com.pcmonitor.app.data.model.AuthState
import com.pcmonitor.app.data.model.LoginRequest
import com.pcmonitor.app.data.model.RegisterRequest
import com.pcmonitor.app.data.model.User
import com.pcmonitor.app.data.model.UserProfile
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

    // Settings → profile and Account & Security
    suspend fun fetchProfile(): Result<UserProfile>
    suspend fun updateName(name: String): Result<String>
    suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit>
    suspend fun signOutEverywhere(): Result<Unit>
    /** The account's data as JSON (GDPR export). */
    suspend fun exportData(): Result<String>
    suspend fun deleteAccount(): Result<Unit>
}

