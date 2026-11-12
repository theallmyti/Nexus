package com.pcmonitor.app.ui.screens.pairing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pcmonitor.app.data.model.Device
import com.pcmonitor.app.data.model.DeviceType
import com.pcmonitor.app.data.repository.DeviceRepository
import com.pcmonitor.app.data.repository.UserPreferencesRepository
import com.pcmonitor.app.ui.components.AppLogo
import com.pcmonitor.app.ui.components.RetroButton
import com.pcmonitor.app.ui.components.RetroButtonVariant
import com.pcmonitor.app.ui.components.RetroProgressBar
import com.pcmonitor.app.ui.components.RetroTopBar
import com.pcmonitor.app.ui.theme.ArcadeYellow
import com.pcmonitor.app.ui.theme.CriticalRed
import com.pcmonitor.app.ui.theme.CyanAccent
import com.pcmonitor.app.ui.theme.DeepNavy
import com.pcmonitor.app.ui.theme.PixelFontFamily
import com.pcmonitor.app.ui.theme.PixelGreen
import com.pcmonitor.app.ui.theme.ReadableFontFamily
import com.pcmonitor.app.ui.theme.TextDarkGray
import com.pcmonitor.app.ui.theme.TextGray
import com.pcmonitor.app.ui.theme.TextWhite
import com.pcmonitor.app.util.PairingHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

@Composable
fun PairingProgressScreen(
    code: String,
    deviceRepository: DeviceRepository,
    userPreferencesRepository: UserPreferencesRepository,
    onBack: () -> Unit,
    onPairingComplete: () -> Unit
) {
    var currentStep by remember { mutableIntStateOf(1) }
    var progress by remember { mutableFloatStateOf(0.2f) }
    var isDone by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var pairedDeviceName by remember { mutableStateOf("PC") }
    var pairedDeviceOs by remember { mutableStateOf("Linux") }

    LaunchedEffect(Unit) {
        val cleanCode = PairingHelper.normalizeCode(code)

        delay(600L)
        currentStep = 2
        progress = 0.5f

        delay(600L)
        currentStep = 3
        progress = 0.75f

        // Confirm pairing code on Convex backend using session token or email
        var pairingSuccess = false
        var deviceId = ""
        var hostname = "Monitored PC"
        var err: String? = null

        withContext(Dispatchers.IO) {
            try {
                val token = userPreferencesRepository.getSessionToken()
                val email = userPreferencesRepository.userEmail.first().trim()

                val client = OkHttpClient()
                val mediaType = "application/json; charset=utf-8".toMediaType()

                // Step 1: If session token exists, check and confirm via dashboard API contract
                if (!token.isNullOrBlank()) {
                    // checkPairingCode
                    val checkJson = JSONObject().apply {
                        put("path", "dashboard:checkPairingCode")
                        put("args", JSONObject().apply {
                            put("token", token)
                            put("code", cleanCode)
                        })
                    }
                    val checkReq = Request.Builder()
                        .url("https://tidy-newt-65.convex.cloud/api/mutation")
                        .post(checkJson.toString().toRequestBody(mediaType))
                        .build()
                    val checkResp = client.newCall(checkReq).execute()
                    val checkBody = checkResp.body?.string() ?: ""
                    checkResp.close()

                    if (checkBody.isNotBlank()) {
                        val checkEnv = JSONObject(checkBody)
                        if (checkEnv.optString("status") == "success") {
                            val checkVal = checkEnv.optJSONObject("value")
                            if (checkVal != null) {
                                hostname = checkVal.optString("name", "Monitored PC")
                                val osStr = checkVal.optString("os", "")
                                if (osStr.isNotBlank()) pairedDeviceOs = osStr
                            }
                        }
                    }

                    // confirmPairing
                    val confirmJson = JSONObject().apply {
                        put("path", "dashboard:confirmPairing")
                        put("args", JSONObject().apply {
                            put("token", token)
                            put("code", cleanCode)
                        })
                    }
                    val confirmReq = Request.Builder()
                        .url("https://tidy-newt-65.convex.cloud/api/mutation")
                        .post(confirmJson.toString().toRequestBody(mediaType))
                        .build()
                    val confirmResp = client.newCall(confirmReq).execute()
                    val confirmBody = confirmResp.body?.string() ?: ""
                    confirmResp.close()

                    if (confirmBody.isNotBlank()) {
                        val env = JSONObject(confirmBody)
                        if (env.optString("status") == "success") {
                            val v = env.optJSONObject("value")
                            if (v != null) {
                                pairingSuccess = true
                                deviceId = v.optString("deviceId", "dev_${System.currentTimeMillis()}")
                                hostname = v.optString("name", hostname)
                                val osStr = v.optString("os", "")
                                if (osStr.isNotBlank()) pairedDeviceOs = osStr
                            } else {
                                // confirmPairing returned null: code not found in database
                                err = "Pairing code not found. Make sure the agent is running and the code hasn't expired (10-min limit)."
                            }
                        } else {
                            val errMsg = env.optString("errorMessage", "")
                            err = when {
                                errMsg.contains("expired", ignoreCase = true) ||
                                errMsg.contains("not found", ignoreCase = true) ->
                                    "Pairing code expired or not found. Run 'nexus pair' on PC for a new code."
                                errMsg.contains("already paired", ignoreCase = true) ->
                                    "This device is already paired to another account."
                                errMsg.contains("Too many", ignoreCase = true) ->
                                    "Too many attempts. Wait 10 minutes and try again."
                                errMsg.isNotBlank() -> errMsg
                                else -> "Pairing failed. Please check the code and try again."
                            }
                        }
                    }
                }

                // Step 2: Fallback to devices:confirmPairing with token if dashboard:confirmPairing returned error
                if (!pairingSuccess && !token.isNullOrBlank()) {
                    val fallbackJson = JSONObject().apply {
                        put("path", "devices:confirmPairing")
                        put("args", JSONObject().apply {
                            put("token", token)
                            put("code", cleanCode)
                        })
                    }
                    val fallbackReq = Request.Builder()
                        .url("https://tidy-newt-65.convex.cloud/api/mutation")
                        .post(fallbackJson.toString().toRequestBody(mediaType))
                        .build()
                    val fallbackResp = client.newCall(fallbackReq).execute()
                    val fallbackBody = fallbackResp.body?.string() ?: ""
                    fallbackResp.close()

                    if (fallbackBody.isNotBlank()) {
                        val env = JSONObject(fallbackBody)
                        if (env.optString("status") == "success") {
                            val v = env.optJSONObject("value")
                            if (v != null) {
                                pairingSuccess = true
                                err = null
                                deviceId = v.optString("deviceId", "dev_${System.currentTimeMillis()}")
                                hostname = v.optString("name", hostname)
                                val osStr = v.optString("os", "")
                                if (osStr.isNotBlank()) pairedDeviceOs = osStr
                            }
                        } else {
                            val errMsg = env.optString("errorMessage", "")
                            if (errMsg.isNotBlank() && err == null) {
                                err = if (errMsg.contains("expired", ignoreCase = true)) {
                                    "Pairing code expired. Run 'nexus pair' on PC for a new code."
                                } else {
                                    errMsg
                                }
                            }
                        }
                    }
                } else if (!pairingSuccess && err == null && email.isBlank()) {
                    err = "Please log in before pairing a device."
                }
            } catch (e: Exception) {
                err = "Network error: ${e.message}"
            }
        }

        if (pairingSuccess) {
            pairedDeviceName = hostname
            val detectedOs = when {
                pairedDeviceOs.contains("win", ignoreCase = true) -> "Windows 11"
                pairedDeviceOs.contains("pi", ignoreCase = true) -> "Raspberry Pi"
                pairedDeviceOs.contains("linux", ignoreCase = true) -> "Linux"
                pairedDeviceOs.isNotBlank() -> pairedDeviceOs.replaceFirstChar { it.uppercase() }
                else -> "Linux"
            }
            val newDev = Device(
                id = deviceId,
                name = hostname,
                os = detectedOs,
                type = if (detectedOs == "Linux") DeviceType.DESKTOP else DeviceType.LAPTOP,
                isOnline = true,
                lastSeen = System.currentTimeMillis()
            )
            deviceRepository.addDevice(newDev)
            currentStep = 4
            progress = 1.0f
            isDone = true
        } else {
            errorMessage = err ?: "Pairing failed. Please check the code."
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepNavy)
    ) {
        RetroTopBar(
            title = "Pairing Device",
            onBackClick = onBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Step 1
            PairingStepRow(
                stepNumber = 1,
                title = "Reading QR code (${code.take(8)})",
                isCompleted = currentStep > 1,
                isCurrent = currentStep == 1
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Step 2
            PairingStepRow(
                stepNumber = 2,
                title = "Connecting to agent via secure channel",
                isCompleted = currentStep > 2,
                isCurrent = currentStep == 2
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Step 3
            PairingStepRow(
                stepNumber = 3,
                title = "Verifying device ownership on Convex",
                isCompleted = currentStep > 3 && isDone,
                isCurrent = currentStep == 3 && errorMessage == null,
                isError = errorMessage != null
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Step 4
            PairingStepRow(
                stepNumber = 4,
                title = if (isDone) "Linked: $pairedDeviceName" else "Finalizing setup...",
                isCompleted = isDone,
                isCurrent = currentStep == 4 && !isDone
            )

            Spacer(modifier = Modifier.weight(1f))

            // Graphic box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFFDF6E3))
                    .border(2.dp, Color(0xFF8B7355), RoundedCornerShape(12.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AppLogo(size = 80.dp)
                    Spacer(modifier = Modifier.height(12.dp))
                    RetroProgressBar(
                        progress = if (errorMessage != null) 0.5f else progress,
                        color = if (errorMessage != null) CriticalRed else PixelGreen,
                        modifier = Modifier.fillMaxWidth(0.85f),
                        height = 12.dp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = when {
                    isDone -> "Device successfully linked!"
                    errorMessage != null -> errorMessage ?: "Pairing failed"
                    else -> "This might take a few seconds..."
                },
                fontFamily = PixelFontFamily,
                fontSize = 11.sp,
                color = when {
                    isDone -> PixelGreen
                    errorMessage != null -> CriticalRed
                    else -> TextDarkGray
                },
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (isDone) {
                RetroButton(
                    text = "Done! View Devices",
                    onClick = onPairingComplete,
                    variant = RetroButtonVariant.Secondary,
                    modifier = Modifier.fillMaxWidth()
                )
            } else if (errorMessage != null) {
                RetroButton(
                    text = "Try Again",
                    onClick = onBack,
                    variant = RetroButtonVariant.Primary,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PairingStepRow(
    stepNumber: Int,
    title: String,
    isCompleted: Boolean,
    isCurrent: Boolean,
    isError: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isError -> CriticalRed
                        isCompleted -> PixelGreen
                        isCurrent -> CyanAccent
                        else -> Color(0xFF2A3F5F)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            when {
                isError -> {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = TextWhite,
                        modifier = Modifier.size(18.dp)
                    )
                }
                isCompleted -> {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = TextWhite,
                        modifier = Modifier.size(18.dp)
                    )
                }
                else -> {
                    Text(
                        text = "$stepNumber",
                        fontFamily = PixelFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = if (isCurrent) DeepNavy else TextDarkGray
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Text(
            text = title,
            fontFamily = ReadableFontFamily,
            fontWeight = if (isCurrent || isCompleted || isError) FontWeight.Bold else FontWeight.Normal,
            fontSize = 14.sp,
            color = when {
                isError -> CriticalRed
                isCompleted || isCurrent -> TextWhite
                else -> TextDarkGray
            }
        )
    }
}
