package com.pcmonitor.app.data.mock

import com.pcmonitor.app.data.model.AuthState
import com.pcmonitor.app.data.model.LoginRequest
import com.pcmonitor.app.data.model.RegisterRequest
import com.pcmonitor.app.data.model.User
import com.pcmonitor.app.data.model.UserProfile
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
        // The display name is stored on the account: read it once signed in.
        val storedName = result.getOrNull()?.let { token ->
            userPreferencesRepository?.saveSessionToken(token)
            callFunction("query", "auth:profile", mapOf("token" to token)).getOrNull()
                ?.let { (it as? JSONObject)?.optString("name")?.takeIf { n -> n.isNotBlank() && n != "null" } }
        }
        return finishAuth(result) { token ->
            userPreferencesRepository?.saveSessionToken(token)
            User(
                id = userIdFor(email),
                name = storedName ?: email.substringBefore("@").replaceFirstChar { it.uppercase() },
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
            mapOf(
                "email" to email,
                "password" to request.password,
                "turnstileToken" to request.turnstileToken,
                "name" to request.name.trim()
            )
        )
        return finishAuth(result) { token ->
            userPreferencesRepository?.saveSessionToken(token)
            User(id = userIdFor(email), name = request.name, email = email, createdAt = System.currentTimeMillis())
        }
    }

    // Calls a Convex auth action. Success: the session token. Failure: a message to show.
    private suspend fun callAuthAction(path: String, args: Map<String, String>): Result<String> =
        callAction(path, args).mapCatching { value ->
            value?.optString("token").orEmpty().ifBlank { throw Exception("Something went wrong. Please try again.") }
        }

    // Calls a Convex action. Success: its value. Failure: a message to show.
    private suspend fun callAction(path: String, args: Map<String, String>): Result<JSONObject?> {
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
                if (env.optString("status") == "success") Result.success(env.optJSONObject("value"))
                else Result.failure(Exception(serverMessage(env)))
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

    // Emails a 6-digit code (auth:requestPasswordReset). The server answers the same whether or
    // not the address has an account.
    override suspend fun requestPasswordReset(email: String): Result<String> {
        val clean = email.lowercase().trim()
        return callAction("auth:requestPasswordReset", mapOf("email" to clean)).map {
            "If $clean has a Nexus account, a 6-digit code is on its way. It expires in 15 minutes."
        }
    }

    override suspend fun resetPassword(email: String, code: String, newPassword: String): Result<User> {
        _authState.value = _authState.value.copy(isLoading = true, error = null)
        val clean = email.lowercase().trim()
        val result = callAuthAction(
            "auth:resetPassword",
            mapOf("email" to clean, "code" to code.trim(), "newPassword" to newPassword)
        )
        return finishAuth(result) { token ->
            userPreferencesRepository?.saveSessionToken(token)
            User(
                id = userIdFor(clean),
                name = clean.substringBefore("@").replaceFirstChar { it.uppercase() },
                email = clean,
                createdAt = System.currentTimeMillis()
            )
        }
    }

    // Calls any Convex function ("query" | "mutation" | "action"). Success: its value
    // (JSONObject, JSONArray, String, Number or null). Failure: a message to show.
    private suspend fun callFunction(kind: String, path: String, args: Map<String, Any?>): Result<Any?> {
        if (baseUrl.isBlank()) return Result.failure(Exception(OFFLINE))
        return withContext(Dispatchers.IO) {
            try {
                val body = JSONObject().apply {
                    put("path", path)
                    put("args", JSONObject(args))
                }.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val req = Request.Builder().url("$baseUrl/api/$kind").post(body).build()
                val text = httpClient.newCall(req).execute().use { it.body?.string().orEmpty() }
                val env = JSONObject(text)
                if (env.optString("status") == "success") Result.success(env.opt("value"))
                else Result.failure(Exception(serverMessage(env)))
            } catch (e: Exception) {
                Result.failure(Exception(OFFLINE))
            }
        }
    }

    private fun sessionToken(): String? = userPreferencesRepository?.getSessionToken()?.takeIf { it.isNotBlank() }

    private suspend fun signedIn(kind: String, path: String, extra: Map<String, Any?> = emptyMap()): Result<Any?> {
        val token = sessionToken() ?: return Result.failure(Exception("You're signed out. Sign in again."))
        return callFunction(kind, path, mapOf("token" to token) + extra)
    }

    override suspend fun fetchProfile(): Result<UserProfile> =
        signedIn("query", "auth:profile").mapCatching { v ->
            val o = v as? JSONObject ?: throw Exception("Couldn't load your profile.")
            UserProfile(
                email = o.optString("email"),
                name = if (o.isNull("name")) null else o.optString("name"),
                createdAt = o.optLong("createdAt"),
                deviceCount = o.optInt("deviceCount")
            )
        }

    override suspend fun updateName(name: String): Result<String> =
        signedIn("mutation", "auth:updateProfile", mapOf("name" to name)).map { v ->
            (v as? JSONObject)?.optString("name") ?: name.trim()
        }

    override suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> =
        signedIn(
            "action", "auth:changePassword",
            mapOf("currentPassword" to currentPassword, "newPassword" to newPassword)
        ).map { }

    override suspend fun signOutEverywhere(): Result<Unit> =
        signedIn("mutation", "auth:signOutEverywhere").map { }

    override suspend fun exportData(): Result<String> =
        signedIn("query", "auth:exportData").map { v -> (v as? JSONObject)?.toString(2) ?: v.toString() }

    override suspend fun deleteAccount(): Result<Unit> =
        signedIn("mutation", "auth:deleteAccount").map { }

    override suspend fun isLoggedIn(): Boolean {
        return _authState.value.isLoggedIn
    }
}
