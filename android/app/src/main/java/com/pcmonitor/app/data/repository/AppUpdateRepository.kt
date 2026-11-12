package com.pcmonitor.app.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.pcmonitor.app.data.model.AppUpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class AppUpdateRepository(
    private val context: Context,
    private val client: OkHttpClient = defaultClient()
) {
    companion object {
        private const val CONVEX_BASE_URL = "https://tidy-newt-65.convex.cloud"
        private val dnsCache = ConcurrentHashMap<String, List<InetAddress>>()

        private fun defaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .dns(object : okhttp3.Dns {
                    override fun lookup(hostname: String): List<InetAddress> {
                        dnsCache[hostname]?.let { return it }
                        return try {
                            val resolved = okhttp3.Dns.SYSTEM.lookup(hostname)
                            dnsCache[hostname] = resolved
                            resolved
                        } catch (e: Exception) {
                            if (hostname.contains("convex.cloud")) {
                                val fallbacks = listOf(
                                    InetAddress.getByAddress(hostname, byteArrayOf(104.toByte(), 18.toByte(), 14.toByte(), 131.toByte())),
                                    InetAddress.getByAddress(hostname, byteArrayOf(104.toByte(), 18.toByte(), 15.toByte(), 131.toByte()))
                                )
                                dnsCache[hostname] = fallbacks
                                fallbacks
                            } else throw e
                        }
                    }
                })
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build()
        }
    }

    suspend fun checkForUpdate(currentVersionCode: Int): Result<AppUpdateInfo> = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("path", "versions:checkAppUpdate")
                put("args", JSONObject().apply {
                    put("currentVersionCode", currentVersionCode)
                })
            }
            val body = json.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$CONVEX_BASE_URL/api/query")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
                }
                val respStr = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
                val root = JSONObject(respStr)
                if (root.optString("status") != "success") {
                    return@withContext Result.failure(Exception(root.optString("errorMessage", "Convex query error")))
                }
                val value = root.optJSONObject("value") ?: return@withContext Result.failure(Exception("Missing value in response"))

                val info = AppUpdateInfo(
                    updateAvailable = value.optBoolean("updateAvailable", false),
                    latestVersionCode = value.optInt("latestVersionCode", 0),
                    latestVersionName = value.optString("latestVersionName", ""),
                    downloadUrl = value.optString("downloadUrl", ""),
                    releaseNotes = value.optString("releaseNotes", ""),
                    isMandatory = value.optBoolean("isMandatory", false)
                )
                Result.success(info)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadApk(downloadUrl: String, onProgress: (Float) -> Unit): Result<File> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(downloadUrl)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Download failed: HTTP ${response.code}"))
                }
                val body = response.body ?: return@withContext Result.failure(Exception("Empty download body"))
                val contentLength = body.contentLength()

                val updateDir = File(context.cacheDir, "updates")
                if (!updateDir.exists()) updateDir.mkdirs()
                val targetFile = File(updateDir, "nexus-update.apk")
                if (targetFile.exists()) targetFile.delete()

                body.byteStream().use { input ->
                    FileOutputStream(targetFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalRead = 0L

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            if (contentLength > 0) {
                                val progress = (totalRead.toFloat() / contentLength).coerceIn(0f, 1f)
                                onProgress(progress)
                            }
                        }
                        output.flush()
                    }
                }

                onProgress(1.0f)
                Result.success(targetFile)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun installApk(apkFile: File) {
        val authority = "${context.packageName}.fileprovider"
        val apkUri: Uri = FileProvider.getUriForFile(context, authority, apkFile)

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
