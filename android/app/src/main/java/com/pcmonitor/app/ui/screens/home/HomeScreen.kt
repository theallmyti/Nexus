package com.pcmonitor.app.ui.screens.home

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pcmonitor.app.ui.components.AlertItem
import com.pcmonitor.app.ui.components.AppLogo
import com.pcmonitor.app.ui.components.DeviceCard
import com.pcmonitor.app.ui.components.FleetOverviewCard
import com.pcmonitor.app.ui.components.PixelComputer
import com.pcmonitor.app.ui.components.RetroButton
import com.pcmonitor.app.ui.components.RetroButtonVariant
import com.pcmonitor.app.ui.theme.ArcadeYellow
import com.pcmonitor.app.ui.theme.CreamBackground
import com.pcmonitor.app.ui.theme.CreamBorder
import com.pcmonitor.app.ui.theme.CreamText
import com.pcmonitor.app.ui.theme.CreamTextSecondary
import com.pcmonitor.app.ui.theme.CriticalRed
import com.pcmonitor.app.ui.theme.DeepNavy
import com.pcmonitor.app.ui.theme.PixelFontFamily
import com.pcmonitor.app.ui.theme.PixelGreen
import com.pcmonitor.app.ui.theme.ReadableFontFamily
import com.pcmonitor.app.ui.theme.TextDarkGray
import com.pcmonitor.app.ui.theme.TextGray
import com.pcmonitor.app.ui.theme.TextWhite

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onDeviceClick: (String) -> Unit,
    onViewAllDevicesClick: () -> Unit,
    onAddDeviceClick: () -> Unit = {},
    onAlertsClick: () -> Unit,
    onProfileClick: () -> Unit,
    onAlertClick: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ArcadeYellow)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                // Top App Bar Item
                item {
                    Spacer(modifier = Modifier.statusBarsPadding())
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Logo + App Name
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppLogo(size = 32.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "NEXUS",
                                fontFamily = PixelFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = ArcadeYellow
                            )
                        }

                        // Notification Icon + Avatar Icon
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box {
                                IconButton(onClick = onAlertsClick) {
                                    Icon(
                                        imageVector = Icons.Default.Notifications,
                                        contentDescription = "Alerts",
                                        tint = ArcadeYellow
                                    )
                                }
                                if (state.unreadAlertCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(top = 8.dp, end = 8.dp)
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(CriticalRed)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF2A3F5F))
                                    .clickable { onProfileClick() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "Profile",
                                    tint = ArcadeYellow,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Greeting
                    Text(
                        text = "Good morning, ${state.userName}!",
                        fontFamily = ReadableFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Here's what's happening with your devices.",
                        fontFamily = ReadableFontFamily,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Fleet Overview (4 Boxes: Total, Online, Offline, Critical)
                    FleetOverviewCard(
                        totalDevices = state.totalCount,
                        onlineDevices = state.onlineCount,
                        offlineDevices = state.offlineCount,
                        criticalDevices = state.criticalCount
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Section: Your Devices
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Your Devices",
                            fontFamily = ReadableFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        Text(
                            text = "View all →",
                            fontFamily = ReadableFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = PixelGreen,
                            modifier = Modifier
                                .clickable { onViewAllDevicesClick() }
                                .padding(4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Device Cards list
                if (state.devices.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(CreamBackground)
                                .border(2.dp, CreamBorder, RoundedCornerShape(12.dp))
                                .padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                PixelComputer(size = 72.dp)
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "CONNECT YOUR PC",
                                    fontFamily = PixelFontFamily,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CreamText,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "1. Double-click Start-PC-Monitor on your PC\n2. Tap the button below to scan your QR code!",
                                    fontFamily = ReadableFontFamily,
                                    fontSize = 13.sp,
                                    color = CreamTextSecondary,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 18.sp
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                RetroButton(
                                    text = "+ Connect Computer",
                                    onClick = onAddDeviceClick,
                                    variant = RetroButtonVariant.Primary,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                } else {
                    items(state.devices, key = { it.id }) { device ->
                        DeviceCard(
                            device = device,
                            onClick = { onDeviceClick(device.id) },
                            useFahrenheit = state.useFahrenheit,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                    }
                }

                // Recent Alerts Section (if any)
                if (state.recentAlerts.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Recent Alerts",
                                fontFamily = ReadableFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = MaterialTheme.colorScheme.onBackground
                            )

                            Text(
                                text = "See all →",
                                fontFamily = ReadableFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = PixelGreen,
                                modifier = Modifier
                                    .clickable { onAlertsClick() }
                                    .padding(4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    items(state.recentAlerts, key = { it.id }) { alert ->
                        AlertItem(
                            alert = alert,
                            onClick = { onAlertClick(alert.id) },
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

