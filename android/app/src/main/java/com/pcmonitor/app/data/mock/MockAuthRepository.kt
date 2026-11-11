package com.pcmonitor.app.data.mock

import com.pcmonitor.app.data.model.AuthState
import com.pcmonitor.app.data.model.LoginRequest
import com.pcmonitor.app.data.model.RegisterRequest
import com.pcmonitor.app.data.model.User
import com.pcmonitor.app.data.repository.AuthRepository
import com.pcmonitor.app.data.repository.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class MockAuthRepository(
    private val userPreferencesRepository: UserPreferencesRepository? = null
) : AuthRepository {

    companion object {
        private const val CONVEX_BASE_URL = "https://tidy-newt-65.convex.cloud"
    }

    private val httpClient = OkHttpClient()

    private val _authState = MutableStateFlow(AuthState())
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val registeredUsers = mutableMapOf<String, Pair<String, String>>()

    override suspend fun login(request: LoginRequest): Result<User> {
        _authState.value = _authState.value.copy(isLoading = true, error = null)
        val email = request.email.lowercase().trim()

        // 1. Try Convex Cloud auth:signIn
        val mediaType = "application/json; charset=utf-8".toMediaType()
        try {
            val reqJson = JSONObject().apply {
                put("path", "auth:signIn")
                put("args", JSONObject().apply {
                    put("email", email)
                    put("password", request.password)
                })
            }
            val req = Request.Builder()
                .url("$CONVEX_BASE_URL/api/action")
                .post(reqJson.toString().toRequestBody(mediaType))
                .build()
            val resp = withContext(Dispatchers.IO) { httpClient.newCall(req).execute() }
            val bodyStr = resp.body?.string() ?: ""
            resp.close()

            if (bodyStr.isNotBlank()) {
                val envelope = JSONObject(bodyStr)
                if (envelope.optString("status") == "success") {
                    val value = envelope.optJSONObject("value")
                    val token = value?.optString("token")
                    if (!token.isNullOrBlank()) {
                        userPreferencesRepository?.saveSessionToken(token)
                        val derivedName = email.substringBefore("@").replaceFirstChar { it.uppercase() }
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
                        return Result.success(user)
                    }
                } else {
                    val err = envelope.optString("errorMessage")
                    if (err.isNotBlank() && (err.contains("Wrong email or password", ignoreCase = true) || err.contains("Too many", ignoreCase = true))) {
                        _authState.value = _authState.value.copy(isLoading = false, error = err)
                        return Result.failure(Exception(err))
                    }
                }
            }
        } catch (_: Exception) {
            // Fallback to local offline validation
        }

        delay(800L)

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

        // 1. Try Convex Cloud auth:signUp
        val mediaType = "application/json; charset=utf-8".toMediaType()
        try {
            val reqJson = JSONObject().apply {
                put("path", "auth:signUp")
                put("args", JSONObject().apply {
                    put("email", email)
                    put("password", request.password)
                })
            }
            val req = Request.Builder()
                .url("$CONVEX_BASE_URL/api/action")
                .post(reqJson.toString().toRequestBody(mediaType))
                .build()
            val resp = withContext(Dispatchers.IO) { httpClient.newCall(req).execute() }
            val bodyStr = resp.body?.string() ?: ""
            resp.close()

            if (bodyStr.isNotBlank()) {
                val envelope = JSONObject(bodyStr)
                if (envelope.optString("status") == "success") {
                    val value = envelope.optJSONObject("value")
                    val token = value?.optString("token")
                    if (!token.isNullOrBlank()) {
                        userPreferencesRepository?.saveSessionToken(token)
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
                } else {
                    val err = envelope.optString("errorMessage")
                    if (err.isNotBlank() && (err.contains("already registered", ignoreCase = true) || err.contains("at least 8 characters", ignoreCase = true))) {
                        _authState.value = _authState.value.copy(isLoading = false, error = err)
                        return Result.failure(Exception(err))
                    }
                }
            }
        } catch (_: Exception) {
            // Fallback to local offline validation
        }

        delay(800L)

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
        val token = userPreferencesRepository?.getSessionToken()
        if (!token.isNullOrBlank()) {
            withContext(Dispatchers.IO) {
                try {
                    val mediaType = "application/json; charset=utf-8".toMediaType()
                    val reqJson = JSONObject().apply {
                        put("path", "auth:signOut")
                        put("args", JSONObject().apply {
                            put("token", token)
                        })
                    }
                    val req = Request.Builder()
                        .url("$CONVEX_BASE_URL/api/mutation")
                        .post(reqJson.toString().toRequestBody(mediaType))
                        .build()
                    httpClient.newCall(req).execute().close()
                } catch (_: Exception) {}
            }
            userPreferencesRepository?.clearSessionToken()
        }
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
