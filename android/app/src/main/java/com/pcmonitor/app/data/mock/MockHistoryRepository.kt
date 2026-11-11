package com.pcmonitor.app.data.mock

import com.pcmonitor.app.data.model.HistoryEntry
import com.pcmonitor.app.data.model.HistorySummary
import com.pcmonitor.app.data.repository.HistoryRepository
import com.pcmonitor.app.data.repository.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class MockHistoryRepository(
    private val userPreferencesRepository: UserPreferencesRepository? = null
) : HistoryRepository {

    companion object {
        private const val CONVEX_BASE_URL = "https://tidy-newt-65.convex.cloud"
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    override suspend fun getHistoryForDevice(deviceId: String, days: Int): HistorySummary {
        val userEmail = userPreferencesRepository?.userEmail?.first()?.trim() ?: ""

        val entries = mutableListOf<HistoryEntry>()

        if (deviceId.isNotBlank() && userEmail.isNotBlank()) {
            withContext(Dispatchers.IO) {
                try {
                    val mediaType = "application/json; charset=utf-8".toMediaType()
                    val reqJson = JSONObject().apply {
                        put("path", "devices:getDeviceSummaries")
                        put("args", JSONObject().apply {
                            put("deviceId", deviceId)
                            put("userEmail", userEmail)
                            put("days", days)
                        })
                    }
                    val req = Request.Builder()
                        .url("$CONVEX_BASE_URL/api/query")
                        .post(reqJson.toString().toRequestBody(mediaType))
                        .build()
                    val resp = httpClient.newCall(req).execute()
                    if (resp.isSuccessful) {
                        val body = resp.body?.string()
                        if (!body.isNullOrBlank()) {
                            val env = JSONObject(body)
                            val list = env.optJSONArray("value")
                            if (list != null) {
                                for (i in 0 until list.length()) {
                                    val item = list.optJSONObject(i) ?: continue
                                    val ts = item.optLong("minuteTs", 0L)
                                    val cpuTemp = item.optDouble("cpuTempAvg", item.optDouble("cpuTempMax", 0.0)).toFloat()
                                    val gpuTemp = item.optDouble("gpuTempMax", 0.0).toFloat()
                                    val ramPct = item.optDouble("ramPctAvg", 0.0).toFloat()
                                    val cpuUtil = item.optDouble("cpuUtilAvg", 0.0).toFloat()

                                    entries.add(HistoryEntry(ts, cpuTemp, gpuTemp, ramPct, cpuUtil))
                                }
                            }
                        }
                    }
                    resp.close()
                } catch (_: Exception) {
                }
            }
        }

        val avgCpu = if (entries.isNotEmpty()) entries.map { it.cpuTemp }.filter { it > 0f }.let { if (it.isNotEmpty()) it.average().toFloat() else 0f } else 0f
        val maxCpu = if (entries.isNotEmpty()) entries.map { it.cpuTemp }.maxOrNull() ?: 0f else 0f
        val avgRam = if (entries.isNotEmpty()) entries.map { it.ramUsagePercent }.filter { it > 0f }.let { if (it.isNotEmpty()) it.average().toFloat() else 0f } else 0f
        val uptimeSec = if (entries.isNotEmpty()) (entries.size * 60).toLong() else 0L

        return HistorySummary(
            deviceId = deviceId,
            deviceName = "Device",
            entries = entries,
            avgCpuTemp = avgCpu,
            maxCpuTemp = maxCpu,
            avgRamUsage = avgRam,
            totalUptimeSeconds = uptimeSec
        )
    }
}
