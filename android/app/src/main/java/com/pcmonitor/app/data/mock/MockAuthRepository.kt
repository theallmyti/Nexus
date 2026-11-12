package com.pcmonitor.app.data.mock

import com.pcmonitor.app.data.model.AuthState
import com.pcmonitor.app.data.model.LoginRequest
import com.pcmonitor.app.data.model.RegisterRequest
import com.pcmonitor.app.data.model.User
import com.pcmonitor.app.data.repository.AuthRepository
import com.pcmonitor.app.data.repository.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
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
    private val userPreferencesRepository: UserPreferencesRepository? = null,
    private val baseUrl: String = CONVEX_BASE_URL
) : AuthRepository {

    companion object {
        const val CONVEX_BASE_URL = "https://tidy-newt-65.convex.cloud"
        private const val OFFLINE = "Can't reach Nexus. Check your internet connection and try again."
    }

    private val httpClient = OkHttpClient()

    private val _authState = MutableStateFlow(AuthState())
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    // Accounts live only on the server (auth.ts): no local or "offline" accounts. An offline
    // fallback used to accept a built-in demo login and create local-only accounts that had no
    // session token, which left the app signed in to nothing.

    override suspend fun login(request: LoginRequest): Result<User> {
        _authState.value = _authState.value.copy(isLoading = true, error = null)
        val email = request.email.lowercase().trim()
        val result = callAuthAction("auth:signIn", mapOf("email" to email, "password" to request.password))
        return finishAuth(result) { token ->
            userPreferencesRepository?.saveSessionToken(token)
            User(
                id = userIdFor(email),
                name = email.substringBefore("@").replaceFirstChar { it.uppercase() },
                email = email,
                createdAt = System.currentTimeMillis()
            )
        }
    }

    override suspend fun register(request: RegisterRequest): Result<User> {
        _authState.value = _authState.value.copy(isLoading = true, error = null)
        val email = request.email.lowercase().trim()

        val invalid = when {
            email.isBlank() || !email.contains("@") || !email.contains(".") -> "Please enter a valid email address."
            request.password.length < 8 -> "Password must be at least 8 characters long."
            request.name.isBlank() -> "Please enter your name."
            else -> null
        }
        if (invalid != null) return fail(invalid)

        val result = callAuthAction(
            "auth:signUp",
            mapOf("email" to email, "password" to request.password, "turnstileToken" to request.turnstileToken)
        )
        return finishAuth(result) { token ->
            userPreferencesRepository?.saveSessionToken(token)
            User(id = userIdFor(email), name = request.name, email = email, createdAt = System.currentTimeMillis())
        }
    }

    // Calls a Convex auth action. Success: the session token. Failure: a message to show.
    private suspend fun callAuthAction(path: String, args: Map<String, String>): Result<String> {
        if (baseUrl.isBlank()) return Result.failure(Exception(OFFLINE))
        return withContext(Dispatchers.IO) {
            try {
                val body = JSONObject().apply {
                    put("path", path)
                    put("args", JSONObject(args))
                }.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val req = Request.Builder().url("$baseUrl/api/action").post(body).build()
                val text = httpClient.newCall(req).execute().use { it.body?.string().orEmpty() }
                val env = JSONObject(text)
                val token = env.optJSONObject("value")?.optString("token").orEmpty()
                when {
                    env.optString("status") == "success" && token.isNotBlank() -> Result.success(token)
                    else -> Result.failure(Exception(serverMessage(env)))
                }
            } catch (e: Exception) {
                Result.failure(Exception(OFFLINE))
            }
        }
    }

    private fun finishAuth(result: Result<String>, onToken: (String) -> User): Result<User> =
        result.fold(
            onSuccess = { token ->
                val user = onToken(token)
                _authState.value = AuthState(isLoggedIn = true, currentUser = user, isLoading = false, error = null)
                Result.success(user)
            },
            onFailure = { fail(it.message ?: "Something went wrong. Please try again.") }
        )

    private fun fail(message: String): Result<User> {
        _authState.value = _authState.value.copy(isLoading = false, error = message)
        return Result.failure(Exception(message))
    }

    private fun userIdFor(email: String) = "user_${email.hashCode().toLong().let { if (it < 0) -it else it }}"

    // ConvexError text arrives in errorData (a string for our errors); errorMessage adds a request id.
    private fun serverMessage(env: JSONObject): String {
        val data = env.opt("errorData")
        if (data is String && data.isNotBlank()) return data
        val msg = env.optString("errorMessage")
        return msg.substringAfter("ConvexError: ", msg).substringBefore('\n').ifBlank { "Something went wrong. Please try again." }
    }

    override suspend fun logout() {
        val token = userPreferencesRepository?.getSessionToken()
        if (!token.isNullOrBlank() && baseUrl.isNotBlank()) {
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
                        .url("$baseUrl/api/mutation")
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

    // The backend has no password reset yet. This used to claim "Reset code sent" without sending
    // anything; it now says so plainly.
    override suspend fun requestPasswordReset(email: String): Result<String> {
        val message = "Password reset isn't available yet. If you can't sign in, ask the Nexus team to reset your account."
        _authState.value = _authState.value.copy(isLoading = false, error = message)
        return Result.failure(Exception(message))
    }

    override suspend fun isLoggedIn(): Boolean {
        return _authState.value.isLoggedIn
    }
}
