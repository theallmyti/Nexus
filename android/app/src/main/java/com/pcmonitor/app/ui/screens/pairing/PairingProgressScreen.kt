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
import androidx.compose.material3.MaterialTheme
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
    // True when the computer already had an owner and was shared with this account.
    var pairedShared by remember { mutableStateOf(false) }
    var pairedDeviceOs by remember { mutableStateOf("Linux") }
    // Set once the code is looked up: the user must see which computer is asking and tap PAIR
    // before it is linked (docs/Security.md §2). A code can arrive from a link on any website.
    var awaitingConfirm by remember { mutableStateOf(false) }
    var confirmRequested by remember { mutableStateOf(false) }
    val cleanCode = remember(code) { PairingHelper.normalizeCode(code) }

    // 1. Look the code up. Nothing is linked yet.
    LaunchedEffect(cleanCode) {
        delay(400L)
        currentStep = 2
        progress = 0.4f
        val token = userPreferencesRepository.getSessionToken()
        if (token.isNullOrBlank()) {
            errorMessage = "Please log in before pairing a device."
            return@LaunchedEffect
        }
        val env = convexMutation("dashboard:checkPairingCode", token, cleanCode)
        when {
            env == null -> errorMessage = "Network error. Check your connection and try again."
            env.optString("status") != "success" ->
                errorMessage = friendlyPairingError(env.optString("errorMessage", ""))
            env.isNull("value") ->
                errorMessage = "Pairing code not found. Make sure the agent is running and the code hasn't expired (10-min limit)."
            else -> {
                val v = env.getJSONObject("value")
                when (v.optString("state")) {
                    "pending" -> {
                        pairedDeviceName = v.optString("name", "Monitored PC")
                        pairedDeviceOs = v.optString("os", "")
                        currentStep = 3
                        progress = 0.6f
                        awaitingConfirm = true
                    }
                    "confirmed" -> errorMessage = "This code was already used. Run 'nexus pair' on the PC for a new one."
                    else -> errorMessage = "Pairing code expired. Run 'nexus pair' on the PC for a new code."
                }
            }
        }
    }

    // 2. The user tapped PAIR: link the computer to this account.
    LaunchedEffect(confirmRequested) {
        if (!confirmRequested) return@LaunchedEffect
        awaitingConfirm = false
        progress = 0.8f
        val token = userPreferencesRepository.getSessionToken()
        if (token.isNullOrBlank()) {
            errorMessage = "Please log in before pairing a device."
            return@LaunchedEffect
        }
        val env = convexMutation("dashboard:confirmPairing", token, cleanCode)
        val v = env?.optJSONObject("value")
        when {
            env == null -> errorMessage = "Network error. Check your connection and try again."
            env.optString("status") != "success" ->
                errorMessage = friendlyPairingError(env.optString("errorMessage", ""))
            v == null ->
                errorMessage = "Pairing code not found. Make sure the agent is running and the code hasn't expired (10-min limit)."
            else -> {
                pairedDeviceName = v.optString("name", pairedDeviceName)
                pairedShared = v.optBoolean("shared", false)
                v.optString("os", "").takeIf { it.isNotBlank() }?.let { pairedDeviceOs = it }
                val detectedOs = displayOs(pairedDeviceOs)
                deviceRepository.addDevice(
                    Device(
                        id = v.optString("deviceId"),
                        name = pairedDeviceName,
                        os = detectedOs,
                        type = when (detectedOs) {
                            "Raspberry Pi" -> DeviceType.RASPBERRY_PI
                            "Linux" -> DeviceType.DESKTOP
                            else -> DeviceType.LAPTOP
                        },
                        isOnline = true,
                        lastSeen = System.currentTimeMillis()
                    )
                )
                currentStep = 4
                progress = 1.0f
                isDone = true
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
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
                title = "Reading code (${cleanCode.take(8)})",
                isCompleted = currentStep > 1,
                isCurrent = currentStep == 1
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Step 2
            PairingStepRow(
                stepNumber = 2,
                title = "Looking up computer",
                isCompleted = currentStep > 2,
                isCurrent = currentStep == 2
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Step 3
            PairingStepRow(
                stepNumber = 3,
                title = if (confirmRequested) "Linking to your account" else "Waiting for your OK",
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

            if (awaitingConfirm && errorMessage == null) {
                ConfirmComputerCard(name = pairedDeviceName, os = displayOs(pairedDeviceOs))
            } else Box(
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
                    isDone && pairedShared -> "Shared with you: you can now watch $pairedDeviceName."
                    isDone -> "Device successfully linked!"
                    errorMessage != null -> errorMessage ?: "Pairing failed"
                    awaitingConfirm -> "Only pair a computer you own, or one whose owner gave you its code."
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
            } else if (awaitingConfirm && errorMessage == null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RetroButton(
                        text = "Pair",
                        onClick = { confirmRequested = true },
                        variant = RetroButtonVariant.Secondary,
                        modifier = Modifier.weight(1f)
                    )
                    RetroButton(
                        text = "Cancel",
                        onClick = onBack,
                        variant = RetroButtonVariant.Outline,
                        modifier = Modifier.weight(1f)
                    )
                }
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
            fontFamily = PixelFontFamily,
            fontWeight = if (isCurrent || isCompleted || isError) FontWeight.Bold else FontWeight.Normal,
            fontSize = 10.sp,
            lineHeight = 16.sp,
            color = when {
                isError -> CriticalRed
                isCompleted || isCurrent -> MaterialTheme.colorScheme.onBackground
                else -> TextDarkGray
            }
        )
    }
}

// Shows which computer is asking before it is linked (docs/Security.md §2).
@Composable
private fun ConfirmComputerCard(name: String, os: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFFDF6E3))
            .border(2.dp, ArcadeYellow, RoundedCornerShape(12.dp))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "PAIR THIS COMPUTER?",
                fontFamily = PixelFontFamily,
                fontSize = 12.sp,
                color = DeepNavy,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            AppLogo(size = 48.dp)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = name,
                fontFamily = PixelFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                lineHeight = 19.sp,
                color = DeepNavy,
                textAlign = TextAlign.Center
            )
            Text(
                text = os,
                fontFamily = ReadableFontFamily,
                fontSize = 14.sp,
                color = TextDarkGray,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun displayOs(raw: String): String = when {
    raw.contains("win", ignoreCase = true) -> "Windows 11"
    raw.contains("pi", ignoreCase = true) -> "Raspberry Pi"
    raw.contains("linux", ignoreCase = true) -> "Linux"
    raw.isNotBlank() -> raw.replaceFirstChar { it.uppercase() }
    else -> "Linux"
}

private fun friendlyPairingError(errMsg: String): String = when {
    errMsg.contains("expired", ignoreCase = true) || errMsg.contains("not found", ignoreCase = true) ->
        "Pairing code expired or not found. Run 'nexus pair' on PC for a new code."
    errMsg.contains("already paired", ignoreCase = true) -> "This device is already paired to another account."
    errMsg.contains("Too many", ignoreCase = true) -> "Too many attempts. Wait 10 minutes and try again."
    errMsg.contains("signed out", ignoreCase = true) -> "Your session expired. Please log in again."
    errMsg.isNotBlank() -> errMsg
    else -> "Pairing failed. Please check the code and try again."
}

// POST a Convex mutation taking { token, code }. Returns the response envelope, or null on a
// network failure.
private suspend fun convexMutation(path: String, token: String, code: String): JSONObject? =
    withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("path", path)
                put("args", JSONObject().apply {
                    put("token", token)
                    put("code", code)
                })
            }.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val req = Request.Builder()
                .url("https://tidy-newt-65.convex.cloud/api/mutation")
                .post(body)
                .build()
            pairingHttpClient.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (text.isBlank()) null else JSONObject(text)
            }
        } catch (e: Exception) {
            null
        }
    }

private val pairingHttpClient = OkHttpClient()
