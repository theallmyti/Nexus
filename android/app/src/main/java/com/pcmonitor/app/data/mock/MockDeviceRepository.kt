package com.pcmonitor.app.data.mock

import android.content.Context
import android.util.Log
import com.pcmonitor.app.data.model.BatteryStats
import com.pcmonitor.app.data.model.BootEvent
import com.pcmonitor.app.data.model.BootEventType
import com.pcmonitor.app.data.model.CpuStats
import com.pcmonitor.app.data.model.Device
import com.pcmonitor.app.data.model.DiskStats
import com.pcmonitor.app.data.model.DeviceType
import com.pcmonitor.app.data.model.FanStats
import com.pcmonitor.app.data.model.GpuStats
import com.pcmonitor.app.data.model.HardwareStats
import com.pcmonitor.app.data.model.ProcessInfo
import com.pcmonitor.app.data.model.RamStats
import com.pcmonitor.app.data.model.UsageStats
import com.pcmonitor.app.data.repository.DeviceRepository
import com.pcmonitor.app.data.repository.UserPreferencesRepository
import com.pcmonitor.app.notification.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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

class MockDeviceRepository(
    private val userPreferencesRepository: UserPreferencesRepository? = null,
    private val context: Context? = null
) : DeviceRepository {

    companion object {
        private const val TAG = "DeviceRepository"
        private const val CONVEX_BASE_URL = "https://tidy-newt-65.convex.cloud"
    }

    private var activeDeviceId: String = ""
    private var activeUserEmail: String = ""

    // Initial state is strictly empty to guarantee multi-tenant tenant isolation
    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    override val devices: StateFlow<List<Device>> = _devices.asStateFlow()

    private val repositoryScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var simulationJob: Job? = null
    private var lastViewerPingMs: Long = 0L
    private var lastDevicesFetchMs: Long = 0L

    private var lastRamAlertMs: Long = 0L
    private var lastCpuAlertMs: Long = 0L
    private var lastGpuAlertMs: Long = 0L
    private val ALERT_COOLDOWN_MS = 15 * 60 * 1000L // 15-minute cooldown

    fun resetAlertCooldowns() {
        lastRamAlertMs = 0L
        lastCpuAlertMs = 0L
        lastGpuAlertMs = 0L
        Log.i(TAG, "Alert cooldowns reset.")
    }

    private val fallbackDns = object : okhttp3.Dns {
        override fun lookup(hostname: String): List<java.net.InetAddress> {
            return try {
                okhttp3.Dns.SYSTEM.lookup(hostname)
            } catch (e: Exception) {
                if (hostname.contains("convex.cloud")) {
                    Log.i(TAG, "Using fallback IP for $hostname")
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
        // Observe user session changes to enforce strict per-account isolation
        userPreferencesRepository?.let { prefs ->
            repositoryScope.launch {
                combine(prefs.isLoggedIn, prefs.userEmail) { loggedIn, email ->
                    Pair(loggedIn, email)
                }.collect { (loggedIn, email) ->
                    val cleanEmail = email.trim().lowercase()
                    if (!loggedIn || cleanEmail.isBlank()) {
                        Log.i(TAG, "User logged out or empty session. Clearing all devices.")
                        activeUserEmail = ""
                        activeDeviceId = ""
                        _devices.value = emptyList()
                    } else {
                        if (activeUserEmail != cleanEmail) {
                            Log.i(TAG, "User session switched to $cleanEmail. Clearing previous devices and fetching fresh list.")
                            activeUserEmail = cleanEmail
                            _devices.value = emptyList()
                            activeDeviceId = ""
                            fetchDevicesFromConvex()
                        }
                        // Also sync FCM push token to Convex for this active session
                        val token = prefs.fcmToken.first()
                        if (!token.isNullOrBlank()) {
                            prefs.syncPushTokenToConvex(cleanEmail, token)
                        }
                    }
                }
            }
        }
    }

    private suspend fun fetchDevicesFromConvex() {
        if (activeUserEmail.isBlank()) return
        val mediaType = "application/json; charset=utf-8".toMediaType()
        try {
            var list: JSONArray? = null
            val token = userPreferencesRepository?.getSessionToken()

            if (!token.isNullOrBlank()) {
                val overviewJson = JSONObject().apply {
                    put("path", "dashboard:overview")
                    put("args", JSONObject().apply {
                        put("token", token)
                    })
                }
                val oReq = Request.Builder()
                    .url("$CONVEX_BASE_URL/api/query")
                    .post(overviewJson.toString().toRequestBody(mediaType))
                    .build()
                val oResp = withContext(Dispatchers.IO) { httpClient.newCall(oReq).execute() }
                val body = oResp.body?.string()
                oResp.close()
                if (!body.isNullOrBlank()) {
                    val env = JSONObject(body)
                    if (isSignedOutError(env)) {
                        // The session expired (30 days unused) or was signed out elsewhere: clear it
                        // so the app returns to the login screen instead of showing empty lists.
                        Log.i(TAG, "Session is no longer valid; signing out locally.")
                        userPreferencesRepository?.clearUserInfo()
                        return
                    }
                    list = env.optJSONObject("value")?.optJSONArray("devices")
                }
            }

            if (list == null && !token.isNullOrBlank()) {
                val devicesReqJson = JSONObject().apply {
                    put("path", "devices:getMyDevices")
                    put("args", JSONObject().apply {
                        put("token", token)
                    })
                }
                val dReq = Request.Builder()
                    .url("$CONVEX_BASE_URL/api/query")
                    .post(devicesReqJson.toString().toRequestBody(mediaType))
                    .build()
                val dResp = withContext(Dispatchers.IO) { httpClient.newCall(dReq).execute() }
                if (dResp.isSuccessful) {
                    val body = dResp.body?.string()
                    if (!body.isNullOrBlank()) {
                        val env = JSONObject(body)
                        list = env.optJSONArray("value")
                    }
                }
                dResp.close()
            }

            if (list != null) {
                val newDevices = mutableListOf<Device>()
                for (i in 0 until list.length()) {
                    val item = list.getJSONObject(i)
                    val id = item.optString("id", item.optString("_id"))
                    val name = item.optString("name", item.optString("hostname", "PC"))

                    var rawOs = item.optString("os", "")
                    if (rawOs.isBlank() || rawOs.equals("null", ignoreCase = true)) {
                        val caps = item.optJSONObject("capabilities")
                        rawOs = when {
                            caps?.optBoolean("isPi", false) == true -> "Raspberry Pi"
                            caps?.has("windowsMode") == true -> "Windows 11"
                            else -> "Linux"
                        }
                    }
                    val os = when {
                        rawOs.contains("win", ignoreCase = true) -> "Windows 11"
                        rawOs.contains("pi", ignoreCase = true) -> "Raspberry Pi"
                        rawOs.contains("linux", ignoreCase = true) -> "Linux"
                        else -> rawOs.replaceFirstChar { it.uppercase() }
                    }

                    // Pi from the OS; laptop vs desktop from whether the agent found a battery.
                    val caps = item.optJSONObject("capabilities")
                    val kindStr = item.optString("kind", "").lowercase()
                    val devType = when {
                        os == "Raspberry Pi" || caps?.optBoolean("isPi", false) == true -> DeviceType.RASPBERRY_PI
                        kindStr.contains("desktop") -> DeviceType.DESKTOP
                        kindStr.contains("laptop") -> DeviceType.LAPTOP
                        caps?.has("hasBattery") == true && !caps.optBoolean("hasBattery") -> DeviceType.DESKTOP
                        else -> DeviceType.LAPTOP
                    }
                    val lastSeen = item.optLong("lastSeenAt", item.optLong("lastSeen", 0L))

                    val latestObj = item.optJSONObject("latest")
                    val latestTs = latestObj?.optLong("ts", 0L) ?: 0L
                    val now = System.currentTimeMillis()
                    val isOnline = item.optBoolean("online", false) ||
                        (latestTs > 0L && (now - latestTs) < 45000L) ||
                        (lastSeen > 0L && (now - lastSeen) < 180000L)

                    val existing = _devices.value.find { it.id == id }
                    val parsedStats = if (latestObj != null && (latestObj.has("cpu") || latestObj.has("ram"))) {
                        parseConvexSnapshot(latestObj)
                    } else null

                    val baseStats = if (parsedStats != null) {
                        if (existing != null) {
                            parsedStats.copy(
                                gpu = parsedStats.gpu ?: existing.hardwareStats.gpu,
                                battery = parsedStats.battery ?: existing.hardwareStats.battery,
                                usage = if (parsedStats.usage.uptimeSeconds > 0) parsedStats.usage else existing.hardwareStats.usage
                            )
                        } else {
                            parsedStats
                        }
                    } else if (existing != null && existing.hardwareStats.cpu.totalUtilization > 0f) {
                        existing.hardwareStats
                    } else {
                        createEmptyHardwareStats()
                    }
                    // Drives are known from registration even before any live data.
                    val stats = if (baseStats.disks.isEmpty()) {
                        baseStats.copy(disks = parseDisks(caps?.optJSONArray("drives")))
                    } else baseStats

                    val device = Device(
                        id = id,
                        name = name,
                        os = os,
                        type = devType,
                        isOnline = isOnline,
                        lastSeen = lastSeen,
                        hardwareStats = stats,
                        ownerId = activeUserEmail,
                        hasGpu = if (caps?.has("hasNvidiaGpu") == true) caps.optBoolean("hasNvidiaGpu") else null,
                        hasBattery = if (caps?.has("hasBattery") == true) caps.optBoolean("hasBattery") else null,
                        shared = item.optBoolean("shared", false)
                    )
                    newDevices.add(device.copy(hardwareStats = device.gate(stats)))
                }
                _devices.value = newDevices
                if (newDevices.isNotEmpty()) {
                    if (activeDeviceId.isBlank() || newDevices.none { it.id == activeDeviceId }) {
                        activeDeviceId = newDevices[0].id
                    }
                } else {
                    activeDeviceId = ""
                }
                Log.i(TAG, "Fetched ${newDevices.size} devices for user $activeUserEmail. Active device: $activeDeviceId")
            }
        } catch (e: Exception) {
            Log.d(TAG, "Device discovery query failed: ${e.message}")
        }
    }

    // Convex answers a rejected session with status "error" and the ConvexError text "signed out".
    private fun isSignedOutError(env: JSONObject): Boolean =
        env.optString("status") == "error" &&
            (env.optString("errorData").contains("signed out") || env.optString("errorMessage").contains("signed out"))

    private fun createEmptyHardwareStats(): HardwareStats {
        return HardwareStats(
            cpu = CpuStats(
                coreTemperatures = emptyList(),
                packageTemperature = 0f,
                averageTemperature = 0f,
                maxTemperature = 0f,
                coreUtilizations = emptyList(),
                threadUtilizations = emptyList(),
                totalUtilization = 0f,
                clockSpeedMhz = 0f,
                powerWatts = 0f
            ),
            gpu = null,
            ram = RamStats(
                usedMb = 0,
                totalMb = 0,
                freeMb = 0,
                cachedMb = 0,
                swapUsedMb = 0,
                swapTotalMb = 0,
                speedMhz = 0,
                type = "",
                topProcesses = emptyList()
            ),
            battery = null,
            fans = FanStats(
                cpuFanRpm = 0,
                gpuFanRpm = 0,
                maxRpm = 0
            ),
            usage = UsageStats(
                uptimeSeconds = 0,
                activeTimeTodaySeconds = 0,
                bootHistory = emptyList()
            )
        )
    }

    override fun getDevice(deviceId: String): Flow<Device?> {
        setActiveDevice(deviceId)
        return _devices.map { list -> list.find { it.id == deviceId } }
    }

    override fun setActiveDevice(deviceId: String) {
        if (deviceId.isBlank()) return
        if (activeDeviceId != deviceId) {
            // Tell the agent right away that someone is watching this device; otherwise live data
            // only started at the next 15 s keepalive.
            lastViewerPingMs = 0L
        }
        activeDeviceId = deviceId
    }

    override suspend fun addDevice(device: Device) {
        _devices.value = _devices.value.filter { it.id != device.id } + device
        activeDeviceId = device.id
    }

    override suspend fun removeDevice(deviceId: String) {
        _devices.value = _devices.value.filter { it.id != deviceId }
        if (activeDeviceId == deviceId) {
            activeDeviceId = _devices.value.firstOrNull()?.id ?: ""
        }
        val token = userPreferencesRepository?.getSessionToken()
        if (!token.isNullOrBlank()) {
            withContext(Dispatchers.IO) {
                try {
                    val mediaType = "application/json; charset=utf-8".toMediaType()
                    val json = JSONObject().apply {
                        put("path", "devices:deleteDevice")
                        put("args", JSONObject().apply {
                            put("token", token)
                            put("deviceId", deviceId)
                        })
                    }
                    val req = Request.Builder()
                        .url("$CONVEX_BASE_URL/api/mutation")
                        .post(json.toString().toRequestBody(mediaType))
                        .build()
                    val resp = httpClient.newCall(req).execute()
                    resp.close()
                    Log.i(TAG, "Unpaired device $deviceId on Convex")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to unpair device on Convex: ${e.message}")
                }
            }
        }
    }

    override suspend fun updateDeviceStats(deviceId: String, stats: HardwareStats) {
        _devices.value = _devices.value.map { device ->
            if (device.id == deviceId) device.copy(hardwareStats = stats, lastSeen = System.currentTimeMillis())
            else device
        }
    }

    private suspend fun convexCall(kind: String, path: String, args: JSONObject): Result<Any?> =
        withContext(Dispatchers.IO) {
            runCatching {
                val token = userPreferencesRepository?.getSessionToken()
                    ?: throw Exception("You're signed out. Sign in again.")
                val body = JSONObject().apply {
                    put("path", path)
                    put("args", args.put("token", token))
                }
                val req = Request.Builder()
                    .url("$CONVEX_BASE_URL/api/$kind")
                    .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    val env = JSONObject(resp.body?.string() ?: "{}")
                    if (env.optString("status") != "success") {
                        throw Exception("Couldn't reach the server. Try again.")
                    }
                    env.opt("value")
                }
            }
        }

    override suspend fun getShares(deviceId: String): Result<List<com.pcmonitor.app.data.model.DeviceShare>> =
        convexCall("query", "devices:getDeviceShares", JSONObject().put("deviceId", deviceId)).map { v ->
            val arr = v as? JSONArray ?: JSONArray()
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { o ->
                    com.pcmonitor.app.data.model.DeviceShare(
                        userId = o.optString("userId"),
                        name = if (o.isNull("name")) null else o.optString("name"),
                        email = if (o.isNull("email")) null else o.optString("email")
                    )
                }
            }
        }

    override suspend fun removeShare(deviceId: String, userId: String): Result<Unit> =
        convexCall("mutation", "devices:removeDeviceShare", JSONObject().put("deviceId", deviceId).put("userId", userId)).map { }

    override suspend fun refresh() {
        // After a cold start (widget worker) the session collector may not have set the user yet.
        repeat(20) {
            if (activeUserEmail.isNotBlank()) return@repeat
            delay(250L)
        }
        lastDevicesFetchMs = System.currentTimeMillis()
        withContext(Dispatchers.IO) { fetchDevicesFromConvex() }
    }

    override suspend fun setDeviceOnline(deviceId: String, isOnline: Boolean) {
        _devices.value = _devices.value.map { device ->
            if (device.id == deviceId) device.copy(isOnline = isOnline, lastSeen = System.currentTimeMillis())
            else device
        }
    }

    override fun getOnlineDeviceCount(): Flow<Int> {
        return _devices.map { list -> list.count { it.isOnline } }
    }

    override fun getOfflineDeviceCount(): Flow<Int> {
        return _devices.map { list -> list.count { !it.isOnline } }
    }

    override fun getCriticalDeviceCount(): Flow<Int> {
        return _devices.map { list ->
            list.count { device ->
                device.isOnline && (
                    device.hardwareStats.cpu.averageTemperature >= 85f ||
                    (device.hardwareStats.gpu?.temperature ?: 0f) >= 80f ||
                    device.hardwareStats.ram.usagePercent >= 90f
                )
            }
        }
    }

    override suspend fun startMockSimulation() {
        if (simulationJob?.isActive == true) return
        simulationJob = repositoryScope.launch {
            val mediaType = "application/json; charset=utf-8".toMediaType()

            while (isActive) {
                delay(1000L) // 1 second real-time refresh

                if (activeUserEmail.isBlank()) continue

                val now = System.currentTimeMillis()

                // Periodically discover/refresh devices from Convex (every 5s if empty, or every 15s)
                val interval = if (_devices.value.isEmpty()) 5000L else 15000L
                if (now - lastDevicesFetchMs > interval) {
                    lastDevicesFetchMs = now
                    fetchDevicesFromConvex()
                }

                if (activeDeviceId.isBlank()) continue
                // Snapshot the id: the user can open another device while the query below is in
                // flight, and its answer must land on the device it was asked for.
                val queriedId = activeDeviceId

                // Renew live viewing lease every 15s per contract (ANDROID_INTEGRATION_GUIDE Section 5)
                if (now - lastViewerPingMs > 15000L) {
                    lastViewerPingMs = now
                    try {
                        val token = userPreferencesRepository?.getSessionToken()
                        if (!token.isNullOrBlank()) {
                            val pingJson = JSONObject().apply {
                                put("path", "devices:setViewer")
                                put("args", JSONObject().apply {
                                    put("token", token)
                                    put("deviceId", queriedId)
                                })
                            }
                            val pingReq = Request.Builder()
                                .url("$CONVEX_BASE_URL/api/mutation")
                                .post(pingJson.toString().toRequestBody(mediaType))
                                .build()
                            withContext(Dispatchers.IO) {
                                httpClient.newCall(pingReq).execute().close()
                            }
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "Viewer keepalive failed: ${e.message}")
                    }
                }

                var liveStats: HardwareStats? = null
                var snapshotTs = 0L
                var isDeviceOnline: Boolean? = null

                // Query Convex Cloud directly over cellular / internet (no local bridge)
                try {
                    val token = userPreferencesRepository?.getSessionToken()
                    if (!token.isNullOrBlank()) {
                        val queryJson = JSONObject().apply {
                            put("path", "devices:getDeviceLatest")
                            put("args", JSONObject().apply {
                                put("token", token)
                                put("deviceId", queriedId)
                            })
                        }
                        val req = Request.Builder()
                            .url("$CONVEX_BASE_URL/api/query")
                            .post(queryJson.toString().toRequestBody(mediaType))
                            .build()
                        val resp = withContext(Dispatchers.IO) {
                            httpClient.newCall(req).execute()
                        }
                        if (resp.isSuccessful) {
                            val body = resp.body?.string()
                            if (!body.isNullOrBlank()) {
                                val envelope = JSONObject(body)
                                val value = envelope.optJSONObject("value")
                                if (value != null) {
                                    snapshotTs = value.optLong("ts", 0L)
                                    val onlineFlag = value.optBoolean("isOnline", false)
                                    val ageMs = now - snapshotTs
                                    if (onlineFlag || (snapshotTs > 0L && ageMs < 45000L)) {
                                        isDeviceOnline = true
                                    } else {
                                        isDeviceOnline = false
                                    }
                                    if (value.has("cpu") || value.has("ram")) {
                                        liveStats = parseConvexSnapshot(value)
                                    }
                                }
                            }
                        }
                        resp.close()
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Convex query error: ${e.message}")
                }

                // Update device in state:
                _devices.value = _devices.value.map { device ->
                    if (device.id == queriedId) {
                        val online = when {
                            isDeviceOnline == true -> true
                            isDeviceOnline == false -> false
                            device.lastSeen > 0L && (now - device.lastSeen < 180000L) -> device.isOnline
                            else -> false
                        }
                        val effectiveLastSeen = if (snapshotTs > 0L) snapshotTs else device.lastSeen
                        val stats = if (liveStats != null) {
                            liveStats.copy(
                                gpu = liveStats.gpu ?: device.hardwareStats.gpu,
                                battery = liveStats.battery ?: device.hardwareStats.battery,
                                disks = liveStats.disks.ifEmpty { device.hardwareStats.disks },
                                usage = if (liveStats.usage.uptimeSeconds > 0) liveStats.usage else device.hardwareStats.usage
                            )
                        } else if (online) {
                            device.hardwareStats
                        } else {
                            createEmptyHardwareStats()
                        }
                        device.copy(
                            hardwareStats = device.gate(stats),
                            isOnline = online,
                            lastSeen = effectiveLastSeen
                        )
                    } else {
                        device
                    }
                }

                // Evaluate hardware thresholds in real-time and fire heads-up notifications
                if (liveStats != null && context != null && userPreferencesRepository != null) {
                    try {
                        val cpuLimit = userPreferencesRepository.getDeviceCpuThreshold(queriedId).first()
                        val gpuLimit = userPreferencesRepository.getDeviceGpuThreshold(queriedId).first()
                        val ramLimit = userPreferencesRepository.getDeviceRamThreshold(queriedId).first()

                        val currentDevice = _devices.value.find { it.id == queriedId }
                        val devName = currentDevice?.name ?: "Primary PC"

                        // 1. RAM Usage Evaluation
                        val ramPct = liveStats.ram.usagePercent
                        if (ramPct > ramLimit && (now - lastRamAlertMs > ALERT_COOLDOWN_MS)) {
                            lastRamAlertMs = now
                            NotificationHelper.showHardwareAlert(
                                context = context,
                                deviceId = queriedId,
                                title = "$devName · RAM High",
                                message = "RAM usage is ${ramPct.toInt()}% (exceeds ${ramLimit.toInt()}% threshold)"
                            )
                            Log.w(TAG, "Hardware Alert triggered: RAM $ramPct% > $ramLimit%")
                        }

                        // 2. CPU Temperature Evaluation
                        val cpuTemp = liveStats.cpu.packageTemperature
                        if (cpuTemp > cpuLimit && (now - lastCpuAlertMs > ALERT_COOLDOWN_MS)) {
                            lastCpuAlertMs = now
                            NotificationHelper.showHardwareAlert(
                                context = context,
                                deviceId = queriedId,
                                title = "$devName · CPU High",
                                message = "CPU package temperature is ${cpuTemp.toInt()}°C (exceeds ${cpuLimit.toInt()}°C threshold)"
                            )
                            Log.w(TAG, "Hardware Alert triggered: CPU $cpuTemp°C > $cpuLimit°C")
                        }

                        // 3. GPU Temperature Evaluation
                        val gpuTemp = liveStats.gpu?.temperature ?: 0f
                        if (liveStats.gpu != null && gpuTemp > gpuLimit && (now - lastGpuAlertMs > ALERT_COOLDOWN_MS)) {
                            lastGpuAlertMs = now
                            NotificationHelper.showHardwareAlert(
                                context = context,
                                deviceId = queriedId,
                                title = "$devName · GPU High",
                                message = "GPU temperature is ${gpuTemp.toInt()}°C (exceeds ${gpuLimit.toInt()}°C threshold)"
                            )
                            Log.w(TAG, "Hardware Alert triggered: GPU $gpuTemp°C > $gpuLimit°C")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error evaluating hardware thresholds: ${e.message}")
                    }
                }
            }
        }
    }

    private fun parseConvexSnapshot(value: JSONObject): HardwareStats {
        val cpuObj = value.optJSONObject("cpu")
        val gpuObj = value.optJSONObject("gpu")
        val ramObj = value.optJSONObject("ram")
        val batteryObj = value.optJSONObject("battery")

        // 1. CPU Telemetry
        val util = cpuObj?.optDouble("util", 0.0)?.toFloat() ?: 0f
        val perCoreArr = cpuObj?.optJSONArray("perCoreUtil")
        val threadList = mutableListOf<Float>()
        if (perCoreArr != null) {
            for (i in 0 until perCoreArr.length()) {
                threadList.add(perCoreArr.optDouble(i, util.toDouble()).toFloat())
            }
        }
        val cpuTemp = cpuObj?.optDouble(
            "packageTemp",
            cpuObj.optDouble("cpuTemp", value.optDouble("cpuTemp", 0.0))
        )?.toFloat() ?: 0f

        val clocksArr = cpuObj?.optJSONArray("clocks")
        val clockSpeed = if (clocksArr != null && clocksArr.length() > 0) {
            val sum = (0 until clocksArr.length()).sumOf { clocksArr.optDouble(it, 0.0) }
            (sum / clocksArr.length()).toFloat()
        } else {
            cpuObj?.optDouble("clockSpeedMhz", cpuObj.optDouble("clock", 0.0))?.toFloat() ?: 0f
        }

        val powerWatts = cpuObj?.optDouble("powerW", 0.0)?.toFloat() ?: 0f

        val coreUtils = if (threadList.isNotEmpty()) {
            threadList
        } else {
            emptyList()
        }

        // Per-logical-processor temperatures, only from chips with per-core sensors (agent 0.2.3+).
        val coreTempsArr = cpuObj?.optJSONArray("coreTemps")
        val coreTemps = if (coreTempsArr != null) {
            (0 until coreTempsArr.length()).map { coreTempsArr.optDouble(it, 0.0).toFloat() }
        } else emptyList()

        val cpu = CpuStats(
            coreTemperatures = coreTemps,
            packageTemperature = cpuTemp,
            averageTemperature = cpuTemp,
            maxTemperature = cpuTemp,
            coreUtilizations = coreUtils,
            threadUtilizations = threadList,
            totalUtilization = util,
            clockSpeedMhz = clockSpeed,
            powerWatts = powerWatts
        )

        // 2. GPU Telemetry
        val gpu = if (gpuObj != null) {
            val gpuName = gpuObj.optString("name").ifBlank { "GPU" }
            GpuStats(
                name = gpuName,
                temperature = gpuObj.optDouble("temp", 0.0).toFloat(),
                utilization = gpuObj.optDouble("util", 0.0).toFloat(),
                vramUsedMb = gpuObj.optDouble("vramUsed", 0.0).toLong(),
                vramTotalMb = gpuObj.optDouble("vramTotal", 0.0).toLong(),
                powerWatts = gpuObj.optDouble("powerW", 0.0).toFloat(),
                clockSpeedMhz = gpuObj.optDouble("clock", 0.0).toFloat(),
                fanSpeedPercent = gpuObj.optDouble("fanPct", 0.0).toFloat()
            )
        } else null

        // 3. RAM & Top Processes Telemetry
        val ram = if (ramObj != null) {
            val detail = ramObj.optJSONObject("detail")
            val totalGb = detail?.optDouble("total", 0.0) ?: ramObj.optDouble("totalGb", 0.0)
            val usedGb = detail?.optDouble("used", 0.0) ?: ramObj.optDouble("usedGb", 0.0)
            val freeGb = detail?.optDouble("free", 0.0) ?: ramObj.optDouble("freeGb", 0.0)
            val swapTotalGb = detail?.optDouble("swapTotal", 0.0) ?: 0.0
            val swapUsedGb = detail?.optDouble("swapUsed", 0.0) ?: 0.0

            val topProcsArr = ramObj.optJSONArray("topProcs")
            val topProcsList = mutableListOf<ProcessInfo>()
            if (topProcsArr != null) {
                for (i in 0 until topProcsArr.length()) {
                    val p = topProcsArr.optJSONObject(i)
                    if (p != null) {
                        topProcsList.add(
                            ProcessInfo(
                                name = p.optString("name", "Process"),
                                memoryMb = p.optDouble("mb", 0.0).toLong(),
                                cpuPercent = p.optDouble("cpuPct", 0.0).toFloat()
                            )
                        )
                    }
                }
            }

            RamStats(
                usedMb = (usedGb * 1024.0).toLong(),
                totalMb = (totalGb * 1024.0).toLong(),
                freeMb = (freeGb * 1024.0).toLong(),
                cachedMb = (detail?.optDouble("cached", 0.0)?.times(1024.0))?.toLong() ?: 0L,
                swapUsedMb = (swapUsedGb * 1024.0).toLong(),
                swapTotalMb = (swapTotalGb * 1024.0).toLong(),
                speedMhz = ramObj.optInt("speedMhz", 0),
                type = ramObj.optString("type", ""),
                topProcesses = topProcsList
            )
        } else RamStats()

        // 4. Battery Telemetry
        val battery = if (batteryObj != null) {
            val rawPct = batteryObj.optDouble("pct", 0.0)
            val rawHealth = batteryObj.optDouble("healthPct", 100.0)
            BatteryStats(
                percentage = kotlin.math.round(rawPct).toInt().coerceIn(0, 100),
                isCharging = batteryObj.optBoolean("charging", false),
                health = batteryObj.optString("health", "Normal"),
                healthPercent = kotlin.math.round(rawHealth).toInt().coerceIn(0, 100),
                chargeCycles = batteryObj.optInt("cycles", 0),
                powerRateWatts = batteryObj.optDouble("rateW", 0.0).toFloat()
            )
        } else null

        // 5. Fan Telemetry removed per user request
        val fans = FanStats(
            cpuFanRpm = 0,
            gpuFanRpm = 0,
            maxRpm = 0
        )

        // 6. System Uptime & Usage
        val uptimeSec = value.optLong("uptimeSec", 0L)
        val usage = UsageStats(
            uptimeSeconds = uptimeSec,
            activeTimeTodaySeconds = 0L,
            bootHistory = if (uptimeSec > 0L) {
                listOf(BootEvent(BootEventType.BOOT, System.currentTimeMillis() - uptimeSec * 1000L))
            } else emptyList()
        )

        return HardwareStats(
            cpu = cpu,
            gpu = gpu,
            ram = ram,
            battery = battery,
            fans = fans,
            usage = usage,
            disks = parseDisks(value.optJSONArray("disks"))
        )
    }

    // [{name, totalGB, freeGB?}] from latest.disks or capabilities.drives.
    private fun parseDisks(arr: JSONArray?): List<DiskStats> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val d = arr.optJSONObject(i) ?: return@mapNotNull null
            val name = d.optString("name")
            if (name.isBlank()) return@mapNotNull null
            DiskStats(
                name = name,
                totalGb = d.optDouble("totalGB", 0.0),
                freeGb = if (d.has("freeGB")) d.optDouble("freeGB") else null
            )
        }
    }

    override suspend fun stopMockSimulation() {
        simulationJob?.cancel()
        simulationJob = null
    }
}
