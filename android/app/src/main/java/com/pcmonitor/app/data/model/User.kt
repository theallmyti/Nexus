package com.pcmonitor.app.data.model

data class User(
    val id: String,
    val name: String,
    val email: String,
    val createdAt: Long = System.currentTimeMillis()
)

/** Settings → profile (auth:profile). */
data class UserProfile(
    val email: String,
    val name: String?,
    val createdAt: Long,
    val deviceCount: Int
)

data class AuthState(
    val isLoggedIn: Boolean = false,
    val currentUser: User? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class RegisterRequest(
    val name: String,
    val email: String,
    val password: String,
    // Cloudflare Turnstile token from the sign-up widget; single use, verified by auth:signUp.
    val turnstileToken: String = ""
)

