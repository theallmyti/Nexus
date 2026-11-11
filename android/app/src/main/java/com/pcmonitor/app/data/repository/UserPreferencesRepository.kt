package com.pcmonitor.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "pc_monitor_prefs")

class UserPreferencesRepository(private val context: Context) {

    private val httpClient = OkHttpClient.Builder()
        .dns(object : okhttp3.Dns {
            override fun lookup(hostname: String): List<java.net.InetAddress> {
                return try {
                    okhttp3.Dns.SYSTEM.lookup(hostname)
                } catch (e: Exception) {
                    if (hostname.contains("convex.cloud")) {
                        listOf(
                            java.net.InetAddress.getByAddress(hostname, byteArrayOf(104.toByte(), 18.toByte(), 14.toByte(), 131.toByte())),
                            java.net.InetAddress.getByAddress(hostname, byteArrayOf(104.toByte(), 18.toByte(), 15.toByte(), 131.toByte()))
                        )
                    } else throw e
                }
            }
        })
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    companion object {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
        val USER_ID = stringPreferencesKey("user_id")
        val USER_NAME = stringPreferencesKey("user_name")
        val USER_EMAIL = stringPreferencesKey("user_email")
        val TEMP_UNIT_FAHRENHEIT = booleanPreferencesKey("temp_unit_fahrenheit")
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val CPU_THRESHOLD = floatPreferencesKey("cpu_threshold")
        val GPU_THRESHOLD = floatPreferencesKey("gpu_threshold")
        val RAM_THRESHOLD = floatPreferencesKey("ram_threshold")
        val DISK_THRESHOLD = floatPreferencesKey("disk_threshold")
        val FCM_TOKEN = stringPreferencesKey("fcm_token")
    }

    val fcmToken: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[FCM_TOKEN]
    }

    suspend fun setFcmToken(token: String) {
        context.dataStore.edit { it[FCM_TOKEN] = token }
    }

    val onboardingCompleted: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[ONBOARDING_COMPLETED] ?: false
    }

    val isLoggedIn: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[IS_LOGGED_IN] ?: false
    }

    val userName: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[USER_NAME] ?: ""
    }

    val userEmail: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[USER_EMAIL] ?: ""
    }

    val useFahrenheit: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[TEMP_UNIT_FAHRENHEIT] ?: false
    }

    val darkMode: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[DARK_MODE] ?: true
    }

    val cpuThreshold: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[CPU_THRESHOLD] ?: 90f
    }

    val gpuThreshold: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[GPU_THRESHOLD] ?: 85f
    }

    val ramThreshold: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[RAM_THRESHOLD] ?: 90f
    }

    val diskThreshold: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[DISK_THRESHOLD] ?: 10f
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { it[ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setLoggedIn(loggedIn: Boolean) {
        context.dataStore.edit { it[IS_LOGGED_IN] = loggedIn }
    }

    suspend fun saveUserInfo(id: String, name: String, email: String) {
        context.dataStore.edit { prefs ->
            prefs[USER_ID] = id
            prefs[USER_NAME] = name
            prefs[USER_EMAIL] = email
            prefs[IS_LOGGED_IN] = true
        }
        val token = fcmToken.first()
        if (!token.isNullOrBlank() && email.isNotBlank()) {
            syncPushTokenToConvex(email, token)
        }
    }

    suspend fun clearUserInfo() {
        context.dataStore.edit { prefs ->
            prefs.remove(USER_ID)
            prefs.remove(USER_NAME)
            prefs.remove(USER_EMAIL)
            prefs[IS_LOGGED_IN] = false
        }
    }

    suspend fun setUseFahrenheit(useFahrenheit: Boolean) {
        context.dataStore.edit { it[TEMP_UNIT_FAHRENHEIT] = useFahrenheit }
    }

    suspend fun setDarkMode(darkMode: Boolean) {
        context.dataStore.edit { it[DARK_MODE] = darkMode }
    }

    suspend fun saveThresholds(cpu: Float, gpu: Float, ram: Float, disk: Float) {
        context.dataStore.edit { prefs ->
            prefs[CPU_THRESHOLD] = cpu
            prefs[GPU_THRESHOLD] = gpu
            prefs[RAM_THRESHOLD] = ram
            prefs[DISK_THRESHOLD] = disk
        }
        val email = userEmail.first()
        if (email.isNotBlank()) {
            syncThresholdsToConvex(email, cpu, gpu, ram)
        }
    }

    suspend fun syncThresholdsToConvex(userEmail: String, cpu: Float, gpu: Float, ram: Float) {
        if (userEmail.isBlank()) return
        withContext(Dispatchers.IO) {
            try {
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val json = JSONObject().apply {
                    put("path", "devices:updateThresholds")
                    put("args", JSONObject().apply {
                        put("userEmail", userEmail.trim().lowercase())
                        put("cpuTempC", cpu.toDouble())
                        put("gpuTempC", gpu.toDouble())
                        put("ramPct", ram.toDouble())
                    })
                }
                val req = Request.Builder()
                    .url("https://tidy-newt-65.convex.cloud/api/mutation")
                    .post(json.toString().toRequestBody(mediaType))
                    .build()
                httpClient.newCall(req).execute().close()
                android.util.Log.i("UserPreferencesRepo", "Synced thresholds to Convex for $userEmail (CPU: $cpu, GPU: $gpu, RAM: $ram)")
            } catch (e: Exception) {
                android.util.Log.e("UserPreferencesRepo", "Failed to sync thresholds to Convex: ${e.message}")
            }
        }
    }

    suspend fun syncPushTokenToConvex(userEmail: String, fcmToken: String, phoneModel: String = android.os.Build.MODEL) {
        if (userEmail.isBlank() || fcmToken.isBlank()) return
        withContext(Dispatchers.IO) {
            try {
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val json = JSONObject().apply {
                    put("path", "devices:registerPushToken")
                    put("args", JSONObject().apply {
                        put("userEmail", userEmail.trim().lowercase())
                        put("fcmToken", fcmToken.trim())
                        put("phoneModel", phoneModel)
                    })
                }
                val req = Request.Builder()
                    .url("https://tidy-newt-65.convex.cloud/api/mutation")
                    .post(json.toString().toRequestBody(mediaType))
                    .build()
                httpClient.newCall(req).execute().close()
                android.util.Log.i("UserPreferencesRepo", "Synced FCM push token to Convex for $userEmail")
            } catch (e: Exception) {
                android.util.Log.e("UserPreferencesRepo", "Failed to sync FCM push token to Convex: ${e.message}")
            }
        }
    }

    suspend fun resetThresholds() {
        saveThresholds(90f, 85f, 90f, 10f)
    }
}

