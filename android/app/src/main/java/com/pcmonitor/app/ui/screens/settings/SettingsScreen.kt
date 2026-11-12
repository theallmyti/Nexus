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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.pcmonitor.app.ui.components.RetroButton
import com.pcmonitor.app.ui.components.RetroButtonVariant
import com.pcmonitor.app.ui.components.RetroTopBar
import com.pcmonitor.app.ui.theme.ArcadeYellow
import com.pcmonitor.app.ui.theme.CardBackground
import com.pcmonitor.app.ui.theme.CriticalRed
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
    onSignedOut: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var showAccountDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showAppPrefsDialog by remember { mutableStateOf(false) }
    var showChangeNameDialog by remember { mutableStateOf(false) }
    var newUserName by remember { mutableStateOf("") }
    val isDark = MaterialTheme.colorScheme.background == DeepNavy

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
            // Profile Card
            val profileCardBg = if (isDark) Color(0xFFFDF6E3) else Color.White
            val profileCardBorder = if (isDark) Color(0xFF8B7355) else Color(0xFFE2E8F0)
            val profileTitleColor = if (isDark) Color(0xFF3E2F1C) else Color(0xFF0F172A)
            val profileSecColor = if (isDark) Color(0xFF6B5B45) else Color(0xFF64748B)
            val avatarTileBg = if (isDark) Color(0xFFE8DFC8) else Color(0xFFF1F5F9)
            val avatarTileBorder = if (isDark) Color(0xFF8B7355) else Color(0xFFE2E8F0)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(profileCardBg)
                    .border(1.5.dp, profileCardBorder, RoundedCornerShape(10.dp))
                    .clickable { showAccountDialog = true }
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
                            .background(avatarTileBg)
                            .border(1.dp, avatarTileBorder, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        PixelCharacter(size = 36.dp)
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.userName,
                            fontFamily = ReadableFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = profileTitleColor
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = state.userEmail,
                            fontFamily = ReadableFontFamily,
                            fontSize = 12.sp,
                            color = profileSecColor
                        )
                    }

                    Text(
                        text = "›",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color(0xFF8B7355) else Color(0xFF94A3B8)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Settings Menu Items (All Beige Containers)
            SettingsMenuItem(
                icon = Icons.Default.Person,
                title = "Account",
                subtitle = "Profile, devices & security",
                onClick = { showAccountDialog = true }
            )

            Spacer(modifier = Modifier.height(10.dp))

            SettingsMenuItem(
                icon = Icons.Default.Palette,
                title = "App Preferences",
                subtitle = "Dark mode · ${if (state.useFahrenheit) "°F" else "°C"}",
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
                }
            },
            confirmButton = {
                Text(
                    text = "Done",
                    color = if (isDark) ArcadeYellow else MaterialTheme.colorScheme.primary,
                    fontFamily = PixelFontFamily,
                    modifier = Modifier
                        .clickable { showAppPrefsDialog = false }
                        .padding(8.dp)
                )
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // Account Details Dialog
    if (showAccountDialog) {
        AlertDialog(
            onDismissRequest = { showAccountDialog = false },
            title = {
                Text(
                    text = "Account Details",
                    fontFamily = PixelFontFamily,
                    color = if (isDark) ArcadeYellow else MaterialTheme.colorScheme.primary,
                    fontSize = 15.sp
                )
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(if (isDark) Color(0xFFFDF6E3) else Color(0xFFF1F5F9))
                            .border(1.5.dp, if (isDark) Color(0xFF8B7355) else Color(0xFFCBD5E1), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        PixelCharacter(size = 42.dp)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = state.userName,
                            fontFamily = ReadableFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Name",
                            tint = if (isDark) ArcadeYellow else MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable {
                                    newUserName = state.userName
                                    showChangeNameDialog = true
                                }
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = state.userEmail,
                        fontFamily = ReadableFontFamily,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Change Name Action Pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isDark) Color(0xFF2A3F5F) else Color(0xFFF1F5F9))
                            .border(1.dp, if (isDark) PixelBorderColor else Color(0xFFCBD5E1), RoundedCornerShape(6.dp))
                            .clickable {
                                newUserName = state.userName
                                showChangeNameDialog = true
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                tint = if (isDark) ArcadeYellow else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Change Name",
                                fontFamily = PixelFontFamily,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) ArcadeYellow else MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Info Container
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isDark) Color(0xFFFDF6E3) else MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, if (isDark) Color(0xFF8B7355) else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "User ID",
                                    fontSize = 12.sp,
                                    color = if (isDark) Color(0xFF6B5B45) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                val shortId = if (state.userEmail.isNotBlank()) {
                                    "UID-${state.userEmail.hashCode().let { if (it < 0) -it else it } % 10000}"
                                } else {
                                    "UID-0000"
                                }
                                Text(
                                    text = shortId,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) Color(0xFF3E2F1C) else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Active Fleet",
                                    fontSize = 12.sp,
                                    color = if (isDark) Color(0xFF6B5B45) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                val deviceCount = state.deviceCount
                                Text(
                                    text = "$deviceCount Computer${if (deviceCount != 1) "s" else ""}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PixelGreen
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Status",
                                    fontSize = 12.sp,
                                    color = if (isDark) Color(0xFF6B5B45) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Authenticated",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) Color(0xFF3E2F1C) else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Text(
                    text = "Close",
                    color = if (isDark) ArcadeYellow else MaterialTheme.colorScheme.primary,
                    fontFamily = PixelFontFamily,
                    modifier = Modifier
                        .clickable { showAccountDialog = false }
                        .padding(8.dp)
                )
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // Change Name Dialog
    if (showChangeNameDialog) {
        AlertDialog(
            onDismissRequest = { showChangeNameDialog = false },
            title = {
                Text(
                    text = "Change Display Name",
                    fontFamily = PixelFontFamily,
                    color = if (isDark) ArcadeYellow else MaterialTheme.colorScheme.primary,
                    fontSize = 15.sp
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter your preferred name for greetings and profile:",
                        fontFamily = ReadableFontFamily,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newUserName,
                        onValueChange = { newUserName = it },
                        singleLine = true,
                        placeholder = { Text("Display name") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = if (isDark) ArcadeYellow else MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                }
            },
            confirmButton = {
                Text(
                    text = "Save",
                    color = if (isDark) ArcadeYellow else MaterialTheme.colorScheme.primary,
                    fontFamily = PixelFontFamily,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable {
                            if (newUserName.isNotBlank()) {
                                viewModel.updateUserName(newUserName.trim())
                                showChangeNameDialog = false
                            }
                        }
                        .padding(8.dp)
                )
            },
            dismissButton = {
                Text(
                    text = "Cancel",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = PixelFontFamily,
                    modifier = Modifier
                        .clickable { showChangeNameDialog = false }
                        .padding(8.dp)
                )
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
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
                        text = "Nexus v1.0.0\nReal-time PC Hardware Monitoring\nMIT License 2026",
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
                    color = if (isDark) ArcadeYellow else MaterialTheme.colorScheme.primary,
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
private fun SettingsMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background == DeepNavy
    val cardBg = if (isDark) Color(0xFFFDF6E3) else Color.White
    val cardBorder = if (isDark) Color(0xFF8B7355) else Color(0xFFE2E8F0)
    val iconTileBg = if (isDark) Color(0xFFF5ECD0) else Color(0xFFF1F5F9)
    val iconTileBorder = if (isDark) Color(0xFF8B7355) else Color(0xFFE2E8F0)
    val iconTint = if (isDark) Color(0xFF8B7355) else MaterialTheme.colorScheme.primary
    val titleColor = if (isDark) Color(0xFF3E2F1C) else Color(0xFF0F172A)
    val subtitleColor = if (isDark) Color(0xFF6B5B45) else Color(0xFF64748B)
    val arrowColor = if (isDark) Color(0xFF8B7355) else Color(0xFF94A3B8)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(cardBg)
            .border(1.5.dp, cardBorder, RoundedCornerShape(8.dp))
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
                    .background(iconTileBg)
                    .border(1.dp, iconTileBorder, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconTint,
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
                    color = titleColor
                )
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        fontFamily = ReadableFontFamily,
                        fontSize = 12.sp,
                        color = subtitleColor
                    )
                }
            }

            Text(
                text = "›",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = arrowColor
            )
        }
    }
}

