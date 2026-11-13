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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pcmonitor.app.ui.components.AlertItem
import com.pcmonitor.app.notification.AlertsSeen
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onDeviceClick: (String) -> Unit,
    onAddDeviceClick: () -> Unit = {},
    onOpenThresholds: () -> Unit = {},
    onAlertsClick: () -> Unit,
    onProfileClick: () -> Unit,
    onAlertClick: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var showAlertSheet by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ArcadeYellow)
            }
        } else PullToRefreshBox(
            isRefreshing = viewModel.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize()
        ) {
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
                                IconButton(onClick = { showAlertSheet = true; AlertsSeen.markSeen(context) }) {
                                    Icon(
                                        imageVector = Icons.Default.Notifications,
                                        contentDescription = "Alerts",
                                        tint = ArcadeYellow
                                    )
                                }
                                if (state.hasNewAlerts) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(top = 10.dp, end = 11.dp)
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(CriticalRed)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF2A3F5F))
                                    .clickable { onProfileClick() },
                                contentAlignment = Alignment.Center
                            ) {
                                com.pcmonitor.app.ui.components.UserAvatar(size = 40.dp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Greeting (original layout)
                    Text(
                        text = "${greeting()}, ${state.userName}!",
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

                    // Fleet Overview (Total, Online, Offline, Critical)
                    FleetOverviewCard(
                        totalDevices = state.totalCount,
                        onlineDevices = state.onlineCount,
                        offlineDevices = state.offlineCount,
                        criticalDevices = state.criticalCount
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Each card adds its own bottom gap, so a hidden card leaves no hole.
                    StartHereCard(
                        hasDevices = state.devices.isNotEmpty(),
                        onAddDevice = onAddDeviceClick,
                        onOpenThresholds = onOpenThresholds
                    )
                    NeedsAttentionCard(devices = state.devices, onDeviceClick = onDeviceClick)

                    if (state.devices.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Your Devices",
                            fontFamily = ReadableFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                // Device Cards list
                if (state.devices.isEmpty()) {
                    item {
                        Text(
                            text = "No computers yet. Once you pair one, it shows up here with its live temperature and memory.",
                            fontFamily = ReadableFontFamily,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                } else {
                    items(state.devices, key = { it.id }) { device ->
                        DeviceCard(
                            device = device,
                            onClick = { onDeviceClick(device.id) },
                            useFahrenheit = state.useFahrenheit,
                            showProblemChip = false, // the Needs attention card above already names it
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }

    // Bell: a quick peek at the latest alerts without leaving Home; the Alerts tab has the full list.
    if (showAlertSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAlertSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Latest alerts",
                        fontFamily = ReadableFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (state.unreadAlertCount > 0) {
                        Text(
                            text = "Mark all read",
                            fontFamily = ReadableFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = PixelGreen,
                            modifier = Modifier.clickable { viewModel.markAllAlertsRead() }.padding(12.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                if (state.recentAlerts.isEmpty()) {
                    Text(
                        text = "All quiet. No alerts yet.",
                        fontFamily = ReadableFontFamily,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    state.recentAlerts.forEach { alert ->
                        AlertItem(
                            alert = alert,
                            onClick = { showAlertSheet = false; onAlertClick(alert.id) },
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                RetroButton(
                    text = "Open Alerts",
                    onClick = { showAlertSheet = false; onAlertsClick() },
                    variant = RetroButtonVariant.Primary,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

private fun greeting(): String = when (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..21 -> "Good evening"
    else -> "Hello"
}

