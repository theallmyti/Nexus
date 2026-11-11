package com.pcmonitor.app.data.mock

import com.pcmonitor.app.data.model.AuthState
import com.pcmonitor.app.data.model.LoginRequest
import com.pcmonitor.app.data.model.RegisterRequest
import com.pcmonitor.app.data.model.User
import com.pcmonitor.app.data.repository.AuthRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MockAuthRepository : AuthRepository {

    private val _authState = MutableStateFlow(AuthState())
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val registeredUsers = mutableMapOf<String, Pair<String, String>>()

    override suspend fun login(request: LoginRequest): Result<User> {
        _authState.value = _authState.value.copy(isLoading = true, error = null)
        delay(1500L)

        val email = request.email.lowercase().trim()
        val credentials = registeredUsers[email]
        return if (credentials != null) {
            if (credentials.first == request.password) {
                val user = User(
                    id = "user_${email.hashCode().toLong().let { if (it < 0) -it else it }}",
                    name = credentials.second,
                    email = email,
                    createdAt = System.currentTimeMillis()
                )
                _authState.value = AuthState(
                    isLoggedIn = true,
                    currentUser = user,
                    isLoading = false,
                    error = null
                )
                Result.success(user)
            } else {
                val errorMsg = "Incorrect password. Please try again."
                _authState.value = _authState.value.copy(isLoading = false, error = errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } else if (email.isNotBlank() && email.contains("@") && email.contains(".") && request.password.length >= 6) {
            val derivedName = email.substringBefore("@").replaceFirstChar { it.uppercase() }
            registeredUsers[email] = Pair(request.password, derivedName)
            val user = User(
                id = "user_${email.hashCode().toLong().let { if (it < 0) -it else it }}",
                name = derivedName,
                email = email,
                createdAt = System.currentTimeMillis()
            )
            _authState.value = AuthState(
                isLoggedIn = true,
                currentUser = user,
                isLoading = false,
                error = null
            )
            Result.success(user)
        } else {
            val errorMsg = "Please enter a valid email address and password (at least 6 characters)."
            _authState.value = _authState.value.copy(isLoading = false, error = errorMsg)
            Result.failure(Exception(errorMsg))
        }
    }

    override suspend fun register(request: RegisterRequest): Result<User> {
        _authState.value = _authState.value.copy(isLoading = true, error = null)
        delay(1500L)

        val email = request.email.lowercase().trim()

        if (email.isBlank() || !email.contains("@") || !email.contains(".")) {
            val errorMsg = "Please enter a valid email address."
            _authState.value = _authState.value.copy(isLoading = false, error = errorMsg)
            return Result.failure(Exception(errorMsg))
        }

        if (request.password.length < 6) {
            val errorMsg = "Password must be at least 6 characters long."
            _authState.value = _authState.value.copy(isLoading = false, error = errorMsg)
            return Result.failure(Exception(errorMsg))
        }

        if (request.name.isBlank()) {
            val errorMsg = "Please enter your name."
            _authState.value = _authState.value.copy(isLoading = false, error = errorMsg)
            return Result.failure(Exception(errorMsg))
        }

        if (registeredUsers.containsKey(email)) {
            val errorMsg = "An account with this email already exists."
            _authState.value = _authState.value.copy(isLoading = false, error = errorMsg)
            return Result.failure(Exception(errorMsg))
        }

        registeredUsers[email] = Pair(request.password, request.name)

        val user = User(
            id = "user_${email.hashCode().toLong().let { if (it < 0) -it else it }}",
            name = request.name,
            email = email,
            createdAt = System.currentTimeMillis()
        )
        _authState.value = AuthState(
            isLoggedIn = true,
            currentUser = user,
            isLoading = false,
            error = null
        )
        return Result.success(user)
    }

    override suspend fun logout() {
        _authState.value = AuthState(
            isLoggedIn = false,
            currentUser = null,
            isLoading = false,
            error = null
        )
    }

    override suspend fun requestPasswordReset(email: String): Result<String> {
        _authState.value = _authState.value.copy(isLoading = true, error = null)
        delay(1000L)

        val trimmedEmail = email.lowercase().trim()
        _authState.value = _authState.value.copy(isLoading = false)

        return if (trimmedEmail.contains("@") && trimmedEmail.contains(".")) {
            Result.success("Reset code sent to $trimmedEmail. Check your inbox.")
        } else {
            val errorMsg = "Please enter a valid email address."
            _authState.value = _authState.value.copy(error = errorMsg)
            Result.failure(Exception(errorMsg))
        }
    }

    override suspend fun isLoggedIn(): Boolean {
        return _authState.value.isLoggedIn
    }
}
