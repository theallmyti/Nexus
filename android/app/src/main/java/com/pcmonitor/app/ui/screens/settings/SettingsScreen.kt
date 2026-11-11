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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepNavy)
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
                            .background(Color(0xFFE8DFC8))
                            .border(1.dp, Color(0xFF8B7355), CircleShape),
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
                        Text(text = "Use Fahrenheit (°F)", color = TextWhite)
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
                        Text(text = "Dark Mode", color = TextWhite)
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
                    color = ArcadeYellow,
                    fontFamily = PixelFontFamily,
                    modifier = Modifier
                        .clickable { showAppPrefsDialog = false }
                        .padding(8.dp)
                )
            },
            containerColor = DeepNavy
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
                    color = ArcadeYellow,
                    fontSize = 15.sp
                )
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFDF6E3))
                            .border(1.5.dp, Color(0xFF8B7355), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        PixelCharacter(size = 42.dp)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = state.userName,
                        fontFamily = ReadableFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = TextWhite
                    )
                    Text(
                        text = state.userEmail,
                        fontFamily = ReadableFontFamily,
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Beige Info Container
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFFDF6E3))
                            .border(1.dp, Color(0xFF8B7355), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "User ID", fontSize = 12.sp, color = Color(0xFF6B5B45))
                                Text(text = "UID-8842-RETRO", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3E2F1C))
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "Active Fleet", fontSize = 12.sp, color = Color(0xFF6B5B45))
                                Text(text = "4 Computers", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PixelGreen)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "Status", fontSize = 12.sp, color = Color(0xFF6B5B45))
                                Text(text = "Authenticated", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3E2F1C))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Text(
                    text = "Close",
                    color = ArcadeYellow,
                    fontFamily = PixelFontFamily,
                    modifier = Modifier
                        .clickable { showAccountDialog = false }
                        .padding(8.dp)
                )
            },
            containerColor = DeepNavy
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
                        color = Color(0xFFCBD5E1),
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
            containerColor = DeepNavy
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

