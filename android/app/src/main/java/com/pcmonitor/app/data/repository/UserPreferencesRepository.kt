package com.pcmonitor.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "pc_monitor_prefs")

class UserPreferencesRepository(private val context: Context) {

    private val dnsCache = java.util.concurrent.ConcurrentHashMap<String, List<java.net.InetAddress>>()
    private val thresholdCache = java.util.concurrent.ConcurrentHashMap<String, Float>()
    private val prefScope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())

    private val httpClient = OkHttpClient.Builder()
        .dns(object : okhttp3.Dns {
            override fun lookup(hostname: String): List<java.net.InetAddress> {
                dnsCache[hostname]?.let { return it }
                return try {
                    val resolved = okhttp3.Dns.SYSTEM.lookup(hostname)
                    dnsCache[hostname] = resolved
                    resolved
                } catch (e: Exception) {
                    if (hostname.contains("convex.cloud")) {
                        val fallbacks = listOf(
                            java.net.InetAddress.getByAddress(hostname, byteArrayOf(104.toByte(), 18.toByte(), 14.toByte(), 131.toByte())),
                            java.net.InetAddress.getByAddress(hostname, byteArrayOf(104.toByte(), 18.toByte(), 15.toByte(), 131.toByte()))
                        )
                        dnsCache[hostname] = fallbacks
                        fallbacks
                    } else throw e
                }
            }
        })
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()

    init {
        // Asynchronously keep in-memory threshold cache hot so 1s polling has 0ms disk overhead
        prefScope.launch {
            context.dataStore.data.collect { prefs ->
                prefs[CPU_THRESHOLD]?.let { thresholdCache["cpu_global"] = it }
                prefs[GPU_THRESHOLD]?.let { thresholdCache["gpu_global"] = it }
                prefs[RAM_THRESHOLD]?.let { thresholdCache["ram_global"] = it }
                prefs[DISK_THRESHOLD]?.let { thresholdCache["disk_global"] = it }
                prefs.asMap().forEach { (key, value) ->
                    if (value is Float) {
                        val name = key.name
                        when {
                            name.startsWith("cpu_thresh_") -> thresholdCache["cpu_${name.removePrefix("cpu_thresh_")}"] = value
                            name.startsWith("gpu_thresh_") -> thresholdCache["gpu_${name.removePrefix("gpu_thresh_")}"] = value
                            name.startsWith("ram_thresh_") -> thresholdCache["ram_${name.removePrefix("ram_thresh_")}"] = value
                            name.startsWith("disk_thresh_") -> thresholdCache["disk_${name.removePrefix("disk_thresh_")}"] = value
                        }
                    }
                }
            }
        }
    }

    private val encryptedPrefs by lazy {
        try {
            val keyGenParameterSpec = androidx.security.crypto.MasterKeys.AES256_GCM_SPEC
            val masterKeyAlias = androidx.security.crypto.MasterKeys.getOrCreate(keyGenParameterSpec)
            androidx.security.crypto.EncryptedSharedPreferences.create(
                "nexus_encrypted_session",
                masterKeyAlias,
                context,
                androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            context.getSharedPreferences("nexus_session_fallback", Context.MODE_PRIVATE)
        }
    }

    private val _sessionToken = MutableStateFlow<String?>(encryptedPrefs.getString("auth_token", null))
    val sessionToken: Flow<String?> = _sessionToken.asStateFlow()

    fun getSessionToken(): String? {
        return encryptedPrefs.getString("auth_token", null)
    }

    fun saveSessionToken(token: String) {
        encryptedPrefs.edit().putString("auth_token", token).apply()
        _sessionToken.value = token
    }

    fun clearSessionToken() {
        encryptedPrefs.edit().remove("auth_token").apply()
        _sessionToken.value = null
    }

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

        fun deviceCpuKey(deviceId: String) = floatPreferencesKey("cpu_thresh_$deviceId")
        fun deviceGpuKey(deviceId: String) = floatPreferencesKey("gpu_thresh_$deviceId")
        fun deviceRamKey(deviceId: String) = floatPreferencesKey("ram_thresh_$deviceId")
        fun deviceDiskKey(deviceId: String) = floatPreferencesKey("disk_thresh_$deviceId")
        fun deviceHasCustomKey(deviceId: String) = booleanPreferencesKey("has_custom_thresh_$deviceId")
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
        val loggedIn = prefs[IS_LOGGED_IN] ?: false
        val token = getSessionToken()
        loggedIn && !token.isNullOrBlank()
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
        clearSessionToken()
        context.dataStore.edit { prefs ->
            prefs.remove(USER_ID)
            prefs.remove(USER_NAME)
            prefs.remove(USER_EMAIL)
            prefs[IS_LOGGED_IN] = false
        }
    }

    suspend fun setUserName(name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotBlank()) {
            context.dataStore.edit { prefs ->
                prefs[USER_NAME] = trimmed
            }
        }
    }

    suspend fun setUseFahrenheit(useFahrenheit: Boolean) {
        context.dataStore.edit { it[TEMP_UNIT_FAHRENHEIT] = useFahrenheit }
    }

    suspend fun setDarkMode(darkMode: Boolean) {
        context.dataStore.edit { it[DARK_MODE] = darkMode }
    }

    suspend fun saveThresholds(cpu: Float, gpu: Float, ram: Float, disk: Float) {
        thresholdCache["cpu_global"] = cpu
        thresholdCache["gpu_global"] = gpu
        thresholdCache["ram_global"] = ram
        thresholdCache["disk_global"] = disk
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

    // Instant in-memory non-blocking threshold lookups for 1-second polling loops
    fun getCachedDeviceCpuThreshold(deviceId: String): Float {
        return thresholdCache["cpu_$deviceId"] ?: thresholdCache["cpu_global"] ?: 90f
    }

    fun getCachedDeviceGpuThreshold(deviceId: String): Float {
        return thresholdCache["gpu_$deviceId"] ?: thresholdCache["gpu_global"] ?: 85f
    }

    fun getCachedDeviceRamThreshold(deviceId: String): Float {
        return thresholdCache["ram_$deviceId"] ?: thresholdCache["ram_global"] ?: 90f
    }

    fun getCachedDeviceDiskThreshold(deviceId: String): Float {
        return thresholdCache["disk_$deviceId"] ?: thresholdCache["disk_global"] ?: 10f
    }

    fun getDeviceCpuThreshold(deviceId: String): Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[deviceCpuKey(deviceId)] ?: prefs[CPU_THRESHOLD] ?: 90f
    }

    fun getDeviceGpuThreshold(deviceId: String): Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[deviceGpuKey(deviceId)] ?: prefs[GPU_THRESHOLD] ?: 85f
    }

    fun getDeviceRamThreshold(deviceId: String): Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[deviceRamKey(deviceId)] ?: prefs[RAM_THRESHOLD] ?: 90f
    }

    fun getDeviceDiskThreshold(deviceId: String): Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[deviceDiskKey(deviceId)] ?: prefs[DISK_THRESHOLD] ?: 10f
    }

    fun hasCustomThresholds(deviceId: String): Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[deviceHasCustomKey(deviceId)] ?: false
    }

    suspend fun saveDeviceThresholds(deviceId: String, cpu: Float, gpu: Float, ram: Float, disk: Float) {
        thresholdCache["cpu_$deviceId"] = cpu
        thresholdCache["gpu_$deviceId"] = gpu
        thresholdCache["ram_$deviceId"] = ram
        thresholdCache["disk_$deviceId"] = disk
        context.dataStore.edit { prefs ->
            prefs[deviceCpuKey(deviceId)] = cpu
            prefs[deviceGpuKey(deviceId)] = gpu
            prefs[deviceRamKey(deviceId)] = ram
            prefs[deviceDiskKey(deviceId)] = disk
            prefs[deviceHasCustomKey(deviceId)] = true
        }
        val email = userEmail.first()
        if (email.isNotBlank()) {
            syncThresholdsToConvex(email, cpu, gpu, ram, deviceId = deviceId)
        }
    }

    suspend fun resetDeviceThresholds(deviceId: String) {
        thresholdCache.remove("cpu_$deviceId")
        thresholdCache.remove("gpu_$deviceId")
        thresholdCache.remove("ram_$deviceId")
        thresholdCache.remove("disk_$deviceId")
        context.dataStore.edit { prefs ->
            prefs.remove(deviceCpuKey(deviceId))
            prefs.remove(deviceGpuKey(deviceId))
            prefs.remove(deviceRamKey(deviceId))
            prefs.remove(deviceDiskKey(deviceId))
            prefs.remove(deviceHasCustomKey(deviceId))
        }
        resetDeviceThresholdsInConvex(deviceId)
    }

    suspend fun syncThresholdsToConvex(userEmail: String, cpu: Float, gpu: Float, ram: Float, deviceId: String? = null) {
        val token = getSessionToken()
        if (token.isNullOrBlank()) return
        withContext(Dispatchers.IO) {
            try {
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val args = JSONObject().apply {
                    put("token", token)
                    put("cpuTempC", cpu.toDouble())
                    put("gpuTempC", gpu.toDouble())
                    put("ramPct", ram.toDouble())
                    if (!deviceId.isNullOrBlank()) {
                        put("deviceId", deviceId)
                    }
                }
                val json = JSONObject().apply {
                    put("path", "devices:updateThresholds")
                    put("args", args)
                }
                val req = Request.Builder()
                    .url("https://tidy-newt-65.convex.cloud/api/mutation")
                    .post(json.toString().toRequestBody(mediaType))
                    .build()
                httpClient.newCall(req).execute().close()
                android.util.Log.i("UserPreferencesRepo", "Synced thresholds to Convex (device: $deviceId, CPU: $cpu, GPU: $gpu, RAM: $ram)")
            } catch (e: Exception) {
                android.util.Log.e("UserPreferencesRepo", "Failed to sync thresholds to Convex: ${e.message}")
            }
        }
    }

    suspend fun resetDeviceThresholdsInConvex(deviceId: String) {
        val token = getSessionToken()
        if (token.isNullOrBlank() || deviceId.isBlank()) return
        withContext(Dispatchers.IO) {
            try {
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val json = JSONObject().apply {
                    put("path", "devices:resetDeviceThresholds")
                    put("args", JSONObject().apply {
                        put("token", token)
                        put("deviceId", deviceId)
                    })
                }
                val req = Request.Builder()
                    .url("https://tidy-newt-65.convex.cloud/api/mutation")
                    .post(json.toString().toRequestBody(mediaType))
                    .build()
                httpClient.newCall(req).execute().close()
                android.util.Log.i("UserPreferencesRepo", "Reset thresholds in Convex for device: $deviceId")
            } catch (e: Exception) {
                android.util.Log.e("UserPreferencesRepo", "Failed to reset thresholds in Convex: ${e.message}")
            }
        }
    }

    suspend fun syncPushTokenToConvex(userEmail: String, fcmToken: String, phoneModel: String = android.os.Build.MODEL) {
        val token = getSessionToken()
        if (token.isNullOrBlank() || fcmToken.isBlank()) return
        withContext(Dispatchers.IO) {
            try {
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val json = JSONObject().apply {
                    put("path", "devices:registerPushToken")
                    put("args", JSONObject().apply {
                        put("token", token)
                        put("fcmToken", fcmToken.trim())
                        put("phoneModel", phoneModel)
                    })
                }
                val req = Request.Builder()
                    .url("https://tidy-newt-65.convex.cloud/api/mutation")
                    .post(json.toString().toRequestBody(mediaType))
                    .build()
                httpClient.newCall(req).execute().close()
                android.util.Log.i("UserPreferencesRepo", "Synced FCM push token to Convex for user")
            } catch (e: Exception) {
                android.util.Log.e("UserPreferencesRepo", "Failed to sync FCM push token to Convex: ${e.message}")
            }
        }
    }

    suspend fun resetThresholds() {
        saveThresholds(90f, 85f, 90f, 10f)
    }
}

