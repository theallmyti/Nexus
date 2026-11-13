package com.pcmonitor.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pcmonitor.app.ui.components.AppLogo
import com.pcmonitor.app.ui.components.PixelCharacter
import com.pcmonitor.app.ui.components.UserAvatar
import com.pcmonitor.app.ui.components.AvatarPickerSheet
import com.pcmonitor.app.ui.components.RetroButton
import com.pcmonitor.app.ui.components.RetroButtonVariant
import com.pcmonitor.app.ui.components.RetroTopBar
import com.pcmonitor.app.ui.theme.ArcadeYellow
import com.pcmonitor.app.ui.theme.CardBackground
import com.pcmonitor.app.ui.theme.CriticalRed
import com.pcmonitor.app.ui.components.RetroTextField
import androidx.compose.ui.text.style.TextAlign
import com.pcmonitor.app.ui.theme.DeepNavy
import com.pcmonitor.app.ui.theme.PixelBorderColor
import com.pcmonitor.app.ui.theme.PixelFontFamily
import com.pcmonitor.app.ui.theme.PixelGreen
import com.pcmonitor.app.ui.theme.ReadableFontFamily
import com.pcmonitor.app.ui.theme.TextDarkGray
import com.pcmonitor.app.ui.theme.TextGray
import com.pcmonitor.app.ui.theme.TextWhite

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateToThresholds: () -> Unit,
    onNavigateToAccount: () -> Unit,
    onSignedOut: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var showProfileDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showAppPrefsDialog by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    var quiet by remember { mutableStateOf(com.pcmonitor.app.notification.AlertSnooze.quietHours(context)) }
    fun setQuiet(on: Boolean, start: Int, end: Int) {
        com.pcmonitor.app.notification.AlertSnooze.setQuietHours(context, on, start, end)
        quiet = Triple(on, start, end)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        RetroTopBar(
            title = "Settings"
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Profile Card (Beige Container matching reference image)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFFDF6E3))
                    .border(1.5.dp, Color(0xFF8B7355), RoundedCornerShape(10.dp))
                    .clickable {
                        viewModel.openProfile()
                        showProfileDialog = true
                    }
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE8DFC8))
                            .border(1.dp, Color(0xFF8B7355), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        UserAvatar(size = 46.dp)
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.userName,
                            fontFamily = ReadableFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color(0xFF3E2F1C)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = state.userEmail,
                            fontFamily = ReadableFontFamily,
                            fontSize = 12.sp,
                            color = Color(0xFF6B5B45)
                        )
                    }

                    Text(
                        text = "›",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF8B7355)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Settings Menu Items (All Beige Containers)
            SettingsMenuItem(
                icon = Icons.Default.Person,
                title = "Account",
                subtitle = "Password, sessions, your data",
                onClick = onNavigateToAccount
            )

            Spacer(modifier = Modifier.height(10.dp))

            SettingsMenuItem(
                icon = Icons.Default.Palette,
                title = "App Preferences",
                subtitle = "Dark mode · ${if (state.useFahrenheit) "°F" else "°C"}" +
                    if (quiet.first) " · Quiet ${hourLabel(quiet.second)}–${hourLabel(quiet.third)}" else "",
                onClick = { showAppPrefsDialog = true }
            )

            Spacer(modifier = Modifier.height(10.dp))

            SettingsMenuItem(
                icon = Icons.Default.Tune,
                title = "Threshold Settings",
                onClick = onNavigateToThresholds
            )

            Spacer(modifier = Modifier.height(10.dp))

            SettingsMenuItem(
                icon = Icons.Default.Info,
                title = "About Nexus",
                onClick = { showAboutDialog = true }
            )

            Spacer(modifier = Modifier.weight(1f))

            // Coral / Pink Sign Out Button matching reference
            RetroButton(
                text = "Sign Out",
                onClick = { viewModel.signOut(onSignedOut) },
                variant = RetroButtonVariant.Danger,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // App Preferences Dialog
    if (showAppPrefsDialog) {
        AlertDialog(
            onDismissRequest = { showAppPrefsDialog = false },
            title = {
                Text(
                    text = "App Preferences",
                    fontFamily = PixelFontFamily,
                    color = ArcadeYellow,
                    fontSize = 16.sp
                )
            },
            text = {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Use Fahrenheit (°F)", color = MaterialTheme.colorScheme.onSurface)
                        Switch(
                            checked = state.useFahrenheit,
                            onCheckedChange = { viewModel.toggleTemperatureUnit() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = ArcadeYellow,
                                checkedTrackColor = PixelGreen
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Dark Mode", color = MaterialTheme.colorScheme.onSurface)
                        Switch(
                            checked = state.isDarkMode,
                            onCheckedChange = { viewModel.toggleDarkMode() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = ArcadeYellow,
                                checkedTrackColor = PixelGreen
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Quiet hours: no alert notifications in this window (alerts still show in the app).
                    val (qOn, qStart, qEnd) = quiet
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Quiet hours", color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                text = "Silence alert notifications overnight",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = qOn,
                            onCheckedChange = { setQuiet(it, qStart, qEnd) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = ArcadeYellow,
                                checkedTrackColor = PixelGreen
                            )
                        )
                    }
                    if (qOn) {
                        Spacer(modifier = Modifier.height(8.dp))
                        HourStepper("From", qStart) { setQuiet(true, it, qEnd) }
                        HourStepper("Until", qEnd) { setQuiet(true, qStart, it) }
                    }
                }
            },
            confirmButton = {
                Text(
                    text = "Done",
                    color = ArcadeYellow,
                    fontFamily = PixelFontFamily,
                    modifier = Modifier
                        .clickable { showAppPrefsDialog = false }
                        .padding(8.dp)
                )
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // Profile dialog (name card): who you are; the name is saved to the account.
    if (showProfileDialog) {
        ProfileDialog(viewModel = viewModel, onDismiss = { showProfileDialog = false })
    }

    // About Dialog
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppLogo(size = 32.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "About",
                        fontFamily = PixelFontFamily,
                        color = ArcadeYellow,
                        fontSize = 15.sp
                    )
                }
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AppLogo(size = 72.dp)
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Nexus ${appVersion(context)}\nHardware monitoring for your computers\nMIT License",
                        fontFamily = ReadableFontFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 22.sp,
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            },
            confirmButton = {
                Text(
                    text = "OK",
                    color = ArcadeYellow,
                    fontFamily = PixelFontFamily,
                    modifier = Modifier
                        .clickable { showAboutDialog = false }
                        .padding(8.dp)
                )
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

@Composable
internal fun SettingsMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    // Beige container matching user specification
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFFDF6E3))
            .border(1.5.dp, Color(0xFF8B7355), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFFF5ECD0))
                    .border(1.dp, Color(0xFF8B7355), RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = Color(0xFF8B7355),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontFamily = ReadableFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color(0xFF3E2F1C)
                )
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        fontFamily = ReadableFontFamily,
                        fontSize = 12.sp,
                        color = Color(0xFF6B5B45)
                    )
                }
            }

            Text(
                text = "›",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF8B7355)
            )
        }
    }
}

@Composable
private fun ProfileRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, fontSize = 12.sp, color = Color(0xFF6B5B45))
        Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3E2F1C))
    }
}

private fun memberSince(createdAt: Long): String =
    java.text.SimpleDateFormat("MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(createdAt))

/** Shared by the Settings profile card and the Home avatar. Call viewModel.openProfile() before showing. */
@Composable
fun ProfileDialog(viewModel: SettingsViewModel, onDismiss: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    var showAvatarPicker by remember { mutableStateOf(false) }
    if (showAvatarPicker) {
        val current by com.pcmonitor.app.data.AvatarStore.avatar.collectAsState()
        AvatarPickerSheet(
            current = current,
            busy = viewModel.avatarBusy,
            error = viewModel.avatarError,
            onPhoto = { viewModel.uploadAvatar(it) },
            onPixel = { viewModel.setPixelAvatar(it) },
            onDismiss = { showAvatarPicker = false }
        )
    }

    val profile = viewModel.profile
    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = {
            Text(
                text = "Your Profile",
                fontFamily = PixelFontFamily,
                color = ArcadeYellow,
                fontSize = 15.sp
            )
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Tap the picture to change it (photo or pixel character).
                UserAvatar(
                    size = 72.dp,
                    modifier = Modifier
                        .border(2.dp, Color(0xFF8B7355), CircleShape)
                        .clickable { showAvatarPicker = true }
                )
                Text(
                    text = "Change picture",
                    fontFamily = ReadableFontFamily,
                    fontSize = 13.sp,
                    color = ArcadeYellow,
                    modifier = Modifier.clickable { showAvatarPicker = true }.padding(8.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                RetroTextField(
                    value = viewModel.profileName,
                    onValueChange = { viewModel.profileName = it.take(40) },
                    label = "Display name",
                    leadingIcon = Icons.Default.Person,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFDF6E3))
                        .border(1.dp, Color(0xFF8B7355), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        ProfileRow("Email", state.userEmail)
                        Spacer(modifier = Modifier.height(6.dp))
                        ProfileRow(
                            "Member since",
                            profile?.let { memberSince(it.createdAt) } ?: "…"
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        val count = profile?.deviceCount ?: state.deviceCount
                        ProfileRow("Computers paired", "$count")
                    }
                }

                if (viewModel.profileError != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = viewModel.profileError ?: "",
                        fontSize = 12.sp,
                        color = CriticalRed,
                        textAlign = TextAlign.Center
                    )
                }
            }
        },
        confirmButton = {
            Text(
                text = if (viewModel.profileSaving) "Saving…" else "Save",
                color = ArcadeYellow,
                fontFamily = PixelFontFamily,
                modifier = Modifier
                    .clickable(enabled = !viewModel.profileSaving) {
                        viewModel.saveProfileName { onDismiss() }
                    }
                    .padding(8.dp)
            )
        },
        dismissButton = {
            Text(
                text = "Cancel",
                color = Color(0xFF94A3B8),
                fontFamily = PixelFontFamily,
                modifier = Modifier
                    .clickable { onDismiss() }
                    .padding(8.dp)
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

private fun hourLabel(h: Int): String = when {
    h == 0 -> "12 AM"
    h < 12 -> "$h AM"
    h == 12 -> "12 PM"
    else -> "${h - 12} PM"
}

@Composable
private fun HourStepper(label: String, hour: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "−",
                color = ArcadeYellow,
                fontSize = 20.sp,
                modifier = Modifier.clickable { onChange((hour + 23) % 24) }.padding(horizontal = 12.dp, vertical = 4.dp)
            )
            Text(
                text = hourLabel(hour),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(56.dp)
            )
            Text(
                text = "+",
                color = ArcadeYellow,
                fontSize = 20.sp,
                modifier = Modifier.clickable { onChange((hour + 1) % 24) }.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

/** The installed build's real version, not a hard-coded one. */
private fun appVersion(context: android.content.Context): String =
    runCatching { "v" + context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrDefault("")
