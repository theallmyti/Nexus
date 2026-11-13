package com.pcmonitor.app.data.mock

import android.util.Log
import com.pcmonitor.app.data.model.Alert
import com.pcmonitor.app.data.model.AlertCategory
import com.pcmonitor.app.data.model.AlertSeverity
import com.pcmonitor.app.data.repository.AlertRepository
import com.pcmonitor.app.data.repository.DeviceRepository
import com.pcmonitor.app.data.repository.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class MockAlertRepository(
    private val deviceRepository: DeviceRepository,
    private val userPreferencesRepository: UserPreferencesRepository? = null
) : AlertRepository {

    companion object {
        private const val TAG = "AlertRepository"
        private const val CONVEX_BASE_URL = "https://tidy-newt-65.convex.cloud"
    }

    private var activeUserEmail: String = ""
    // The server has no per-alert delete: alerts dismissed on this phone are hidden locally so the
    // 15 s sync does not bring them back. Read state is kept across syncs the same way.
    private val dismissedIds = mutableSetOf<String>()
    private val readIds = mutableSetOf<String>()

    // Zero fake alerts: starts completely empty
    private val _alerts = MutableStateFlow<List<Alert>>(emptyList())
    override val alerts: StateFlow<List<Alert>> = _alerts.asStateFlow()

    private val repositoryScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val fallbackDns = object : okhttp3.Dns {
        override fun lookup(hostname: String): List<java.net.InetAddress> {
            return try {
                okhttp3.Dns.SYSTEM.lookup(hostname)
            } catch (e: Exception) {
                if (hostname.contains("convex.cloud")) {
                    listOf(
                        java.net.InetAddress.getByAddress(hostname, byteArrayOf(104.toByte(), 18.toByte(), 14.toByte(), 131.toByte())),
                        java.net.InetAddress.getByAddress(hostname, byteArrayOf(104.toByte(), 18.toByte(), 15.toByte(), 131.toByte()))
                    )
                } else {
                    throw e
                }
            }
        }
    }

    private val httpClient = OkHttpClient.Builder()
        .dns(fallbackDns)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    init {
        relabelOnDeviceChanges()
        // Observe user session changes
        userPreferencesRepository?.let { prefs ->
            repositoryScope.launch {
                combine(prefs.isLoggedIn, prefs.userEmail) { loggedIn, email ->
                    Pair(loggedIn, email)
                }.collect { (loggedIn, email) ->
                    if (!loggedIn || email.isBlank()) {
                        activeUserEmail = ""
                        dismissedIds.clear()
                        readIds.clear()
                        _alerts.value = emptyList()
                    } else {
                        activeUserEmail = email
                        syncAlertsWithConvex()
                    }
                }
            }
        }

        // Periodically poll Convex Cloud for genuine alerts
        repositoryScope.launch {
            while (isActive) {
                delay(15000L) // Poll every 15 seconds
                syncAlertsWithConvex()
            }
        }
    }

    private suspend fun syncAlertsWithConvex() {
        val token = userPreferencesRepository?.getSessionToken()
        if (token.isNullOrBlank()) return
        val mediaType = "application/json; charset=utf-8".toMediaType()
        try {
            val queryJson = JSONObject().apply {
                put("path", "devices:getMyAlerts")
                put("args", JSONObject().apply {
                    put("token", token)
                })
            }
            val req = Request.Builder()
                .url("$CONVEX_BASE_URL/api/query")
                .post(queryJson.toString().toRequestBody(mediaType))
                .build()
            val resp = withContext(Dispatchers.IO) { httpClient.newCall(req).execute() }
            if (resp.isSuccessful) {
                val body = resp.body?.string()
                if (!body.isNullOrBlank()) {
                    val env = JSONObject(body)
                    val arr = env.optJSONArray("value")
                    if (arr != null) {
                        val fetched = parseConvexAlerts(arr)
                            .filter { it.id !in dismissedIds }
                            .map { if (it.id in readIds) it.copy(isRead = true) else it }
                        if (fetched != _alerts.value) {
                            _alerts.value = fetched
                        }
                    }
                }
            }
            resp.close()
        } catch (e: Exception) {
            Log.d(TAG, "Error fetching alerts: ${e.message}")
        }
    }

    /**
     * The server sends only the device id. Alerts can arrive before the device list does, so
     * an unknown id is "Removed device" only once the list has loaded; until then the name is
     * blank and [relabelOnDeviceChanges] fills it in.
     */
    private fun nameFor(deviceId: String): String {
        val devices = deviceRepository.devices.value
        return devices.find { it.id == deviceId }?.name ?: if (devices.isEmpty()) "" else "Removed device"
    }

    private fun relabelOnDeviceChanges() {
        repositoryScope.launch {
            deviceRepository.devices.collect { devices ->
                if (devices.isEmpty()) return@collect
                val current = _alerts.value
                val relabeled = current.map { a ->
                    val name = devices.find { it.id == a.deviceId }?.name ?: "Removed device"
                    if (a.deviceName != name) a.copy(deviceName = name) else a
                }
                if (relabeled != current) _alerts.value = relabeled
            }
        }
    }

    private fun parseConvexAlerts(arr: JSONArray): List<Alert> {
        val list = mutableListOf<Alert>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val id = obj.optString("_id")
            val type = obj.optString("type")
            val value = obj.optDouble("value", 0.0).toFloat()
            val threshold = obj.optDouble("threshold", 0.0).toFloat()
            val createdAt = obj.optLong("createdAt", System.currentTimeMillis())
            val deviceId = obj.optString("deviceId")
            // The server sends only the device id: name it from the user's device list.
            val devName = obj.optString("deviceName").ifBlank { nameFor(deviceId) }

            val (severity, category, title, description) = when (type) {
                "cpuTemp" -> Quadruple(
                    AlertSeverity.CRITICAL,
                    AlertCategory.CPU_TEMP,
                    "CPU Temperature Critical",
                    "CPU package temperature reached ${value.toInt()}°C (threshold: ${threshold.toInt()}°C)."
                )
                "gpuTemp" -> Quadruple(
                    AlertSeverity.WARNING,
                    AlertCategory.GPU_TEMP,
                    "GPU Temperature High",
                    "GPU temperature reached ${value.toInt()}°C (threshold: ${threshold.toInt()}°C)."
                )
                "ram" -> Quadruple(
                    AlertSeverity.WARNING,
                    AlertCategory.RAM_USAGE,
                    "RAM Usage High",
                    "System RAM utilization reached ${value.toInt()}% (threshold: ${threshold.toInt()}%)."
                )
                "disk" -> Quadruple(
                    AlertSeverity.WARNING,
                    AlertCategory.DISK_SPACE,
                    "Disk Space Low",
                    "Drive ${obj.optString("drive").ifBlank { "?" }} is down to ${value.toInt()}% free (threshold: ${threshold.toInt()}%)."
                )
                "offline" -> Quadruple(
                    AlertSeverity.OFFLINE,
                    AlertCategory.DEVICE_OFFLINE,
                    "Device Offline",
                    "Monitored device is unreachable."
                )
                else -> Quadruple(
                    AlertSeverity.INFO,
                    AlertCategory.DEVICE_ONLINE,
                    "System Notification",
                    "Telemetry event triggered."
                )
            }

            list.add(
                Alert(
                    id = id,
                    deviceId = deviceId,
                    deviceName = devName,
                    title = title,
                    description = description,
                    severity = severity,
                    category = category,
                    timestamp = createdAt,
                    isRead = false,
                    value = value,
                    threshold = threshold
                )
            )
        }
        return list
    }

    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    override suspend fun addAlert(alert: Alert) {
        _alerts.value = listOf(alert) + _alerts.value
    }

    override suspend fun markAsRead(alertId: String) {
        readIds += alertId
        _alerts.value = _alerts.value.map { alert ->
            if (alert.id == alertId) alert.copy(isRead = true) else alert
        }
    }

    override suspend fun clearAlert(alertId: String) {
        dismissedIds += alertId
        _alerts.value = _alerts.value.filter { it.id != alertId }
    }

    override suspend fun clearAllAlerts() {
        _alerts.value = emptyList()
        val token = userPreferencesRepository?.getSessionToken() ?: return
        repositoryScope.launch {
            try {
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val mutationJson = JSONObject().apply {
                    put("path", "devices:clearAlerts")
                    put("args", JSONObject().apply {
                        put("token", token)
                    })
                }
                val req = Request.Builder()
                    .url("$CONVEX_BASE_URL/api/mutation")
                    .post(mutationJson.toString().toRequestBody(mediaType))
                    .build()
                withContext(Dispatchers.IO) { httpClient.newCall(req).execute().close() }
            } catch (e: Exception) {
                Log.d(TAG, "Error clearing cloud alerts: ${e.message}")
            }
        }
    }

    override fun getAlertsByDevice(deviceId: String): List<Alert> {
        return _alerts.value.filter { it.deviceId == deviceId }
    }

    override fun getAlertsBySeverity(severity: AlertSeverity): List<Alert> {
        return _alerts.value.filter { it.severity == severity }
    }

    override fun getUnreadCount(): Int {
        return _alerts.value.count { !it.isRead }
    }

    override suspend fun checkThresholds(deviceId: String) {
        // Real-time threshold evaluation is handled by backend mutation agent:pushLatest
    }
}
