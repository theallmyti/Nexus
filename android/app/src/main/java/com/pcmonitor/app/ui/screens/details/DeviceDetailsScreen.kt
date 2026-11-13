package com.pcmonitor.app.ui.screens.details

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.pcmonitor.app.data.model.CpuStats
import com.pcmonitor.app.data.model.Device
import com.pcmonitor.app.data.model.GpuStats
import com.pcmonitor.app.data.model.RamStats
import com.pcmonitor.app.data.model.formatGb
import com.pcmonitor.app.notification.AlertSnooze
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.heightIn
import com.pcmonitor.app.ui.components.PixelComputer
import com.pcmonitor.app.ui.components.RetroProgressBar
import com.pcmonitor.app.ui.components.RetroSparkline
import com.pcmonitor.app.ui.theme.ArcadeYellow
import com.pcmonitor.app.ui.theme.CreamBackground
import com.pcmonitor.app.ui.theme.CreamBorder
import com.pcmonitor.app.ui.theme.CreamBorderLight
import com.pcmonitor.app.ui.theme.CreamGreenAccent
import com.pcmonitor.app.ui.theme.CreamGreenChart
import com.pcmonitor.app.ui.theme.CreamSurface
import com.pcmonitor.app.ui.theme.CreamText
import com.pcmonitor.app.ui.theme.CreamTextSecondary
import com.pcmonitor.app.ui.theme.DeepNavy
import com.pcmonitor.app.ui.theme.OfflineRed
import com.pcmonitor.app.ui.theme.OnlineGreen
import com.pcmonitor.app.ui.theme.PixelFontFamily
import com.pcmonitor.app.ui.theme.PixelGreen
import com.pcmonitor.app.ui.theme.ReadableFontFamily
import com.pcmonitor.app.ui.theme.TextDarkGray
import com.pcmonitor.app.ui.theme.TextWhite

@Composable
fun DeviceDetailsScreen(
    viewModel: DeviceDetailsViewModel,
    onBack: () -> Unit,
    onNavigateToHistory: (String) -> Unit,
    onNavigateToThresholds: (String) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val device = state.device
    var showUnpairDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ArcadeYellow)
            }
            return
        }

        if (device == null || state.notFound) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Device not found",
                    fontFamily = ReadableFontFamily,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Go Back",
                    fontFamily = PixelFontFamily,
                    color = ArcadeYellow,
                    modifier = Modifier.clickable { onBack() }
                )
            }
            return
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            // Top Bar: Back button, Title, and Action shortcuts
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = ArcadeYellow
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = device.name,
                        fontFamily = PixelFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = ArcadeYellow,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onNavigateToThresholds(device.id) }) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Alert Thresholds",
                            tint = ArcadeYellow
                        )
                    }
                    IconButton(onClick = { onNavigateToHistory(device.id) }) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "View History",
                            tint = ArcadeYellow
                        )
                    }
                    SnoozeButton(deviceId = device.id)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // THE RETRO WARM CREAM CONTAINER (STRICT REQUIREMENT)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CreamBackground)
                    .border(2.dp, CreamBorder, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    // Header Area of Cream Card
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Pixel art computer / laptop
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(CreamSurface)
                                .border(1.5.dp, CreamBorderLight, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            PixelComputer(size = 48.dp)
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${device.os} · ${device.type.label}",
                                fontFamily = ReadableFontFamily,
                                fontSize = 12.sp,
                                color = CreamTextSecondary
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            // Online / Offline Status Badge
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (device.isOnline) Color(0xFFDFF0D8) else Color(0xFFF2DEDE))
                                    .border(
                                        1.dp,
                                        if (device.isOnline) OnlineGreen else OfflineRed,
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (device.isOnline) OnlineGreen else OfflineRed)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (device.isOnline) "Online" else "Offline",
                                    fontFamily = ReadableFontFamily,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (device.isOnline) Color(0xFF2E6930) else Color(0xFFA94442)
                                )
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            // One line of context: uptime while online, last-seen while offline.
                            Text(
                                text = if (device.isOnline) {
                                    if (device.hardwareStats.usage.uptimeSeconds > 0) "Up ${device.hardwareStats.usage.uptimeFormatted}" else ""
                                } else formatLastSeen(device.lastSeen, false),
                                fontFamily = ReadableFontFamily,
                                fontSize = 12.sp,
                                color = CreamTextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Green Pill Navigation Tabs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DetailTab.entries.filter { tab ->
                            when (tab) {
                                DetailTab.GPU -> device.hardwareStats.gpu != null || device.hasGpu == true
                                DetailTab.BATTERY -> device.hardwareStats.battery != null || device.hasBattery == true
                                else -> true
                            }
                        }.forEach { tab ->
                            val isSelected = viewModel.selectedTab == tab
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) CreamGreenAccent else CreamSurface)
                                    .border(
                                        1.5.dp,
                                        if (isSelected) CreamGreenChart else CreamBorderLight,
                                        RoundedCornerShape(6.dp)
                                    )
                                    .clickable { viewModel.onTabSelected(tab) }
                                    .heightIn(min = 44.dp)
                                    .padding(horizontal = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = tab.label,
                                    fontFamily = PixelFontFamily,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) TextWhite else CreamText
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Offline status banner if PC is shut down / disconnected
                    if (!device.isOnline) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFFEE2E2))
                                .border(1.5.dp, Color(0xFFFCA5A5), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(OfflineRed)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "PC IS SHUT DOWN / OFFLINE",
                                        fontFamily = PixelFontFamily,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = OfflineRed
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Live telemetry paused. Reconnects automatically when PC powers on.",
                                        fontFamily = ReadableFontFamily,
                                        fontSize = 12.sp,
                                        color = Color(0xFF7F1D1D)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    // Per-core min/max/averages: tracked for as long as this device is open.
                    val coreStats = remember(device.id) { CoreTempStats() }
                    val liveCpu = device.hardwareStats.cpu
                    LaunchedEffect(liveCpu.coreTemperatures) {
                        if (liveCpu.coreTemperatures.isNotEmpty()) {
                            val (cores, _) = groupCores(liveCpu.coreTemperatures, liveCpu.threadUtilizations)
                            coreStats.update(cores.map { it.temp })
                        }
                    }

                    // Dynamic tab contents
                    when (viewModel.selectedTab) {
                        DetailTab.OVERVIEW -> {
                            OverviewSection(
                                device = device,
                                useFahrenheit = state.useFahrenheit,
                                onUnpairClick = { showUnpairDialog = true }
                            ,
                                viewModel = viewModel
                            )
                        }
                        DetailTab.CPU -> {
                            CpuSection(
                                cpu = device.hardwareStats.cpu,
                                isOnline = device.isOnline,
                                useFahrenheit = state.useFahrenheit,
                                coreStats = coreStats
                            )
                        }
                        DetailTab.GPU -> {
                            GpuSection(
                                gpu = device.hardwareStats.gpu,
                                isOnline = device.isOnline,
                                useFahrenheit = state.useFahrenheit
                            )
                        }
                        DetailTab.RAM -> {
                            RamSection(
                                ram = device.hardwareStats.ram,
                                isOnline = device.isOnline
                            )
                        }
                        DetailTab.BATTERY -> {
                            BatterySection(device = device)
                        }
                    }
                }
            }
        }

        if (showUnpairDialog) {
            AlertDialog(
                onDismissRequest = { showUnpairDialog = false },
                title = {
                    Text(
                        text = if (device.shared) "REMOVE FROM YOUR ACCOUNT?" else "UNPAIR DEVICE?",
                        fontFamily = PixelFontFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = CreamText
                    )
                },
                text = {
                    Text(
                        text = if (device.shared) {
                            "\"${device.name}\" was shared with you. Removing it only takes it off your account; the owner keeps it."
                        } else {
                            "Are you sure you want to unpair \"${device.name}\"? This will remove it from your account and stop live monitoring."
                        },
                        fontFamily = ReadableFontFamily,
                        fontSize = 14.sp,
                        color = CreamTextSecondary
                    )
                },
                confirmButton = {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(OfflineRed)
                            .clickable {
                                showUnpairDialog = false
                                viewModel.unpairDevice { onBack() }
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = if (device.shared) "REMOVE" else "UNPAIR",
                            fontFamily = PixelFontFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextWhite
                        )
                    }
                },
                dismissButton = {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(CreamSurface)
                            .border(1.dp, CreamBorderLight, RoundedCornerShape(6.dp))
                            .clickable { showUnpairDialog = false }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "CANCEL",
                            fontFamily = PixelFontFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = CreamText
                        )
                    }
                },
                containerColor = CreamBackground,
                shape = RoundedCornerShape(12.dp)
            )
        }
    }
}

@Composable
private fun OverviewSection(
    device: Device,
    useFahrenheit: Boolean,
    onUnpairClick: () -> Unit,
    viewModel: DeviceDetailsViewModel
) {
    val stats = device.hardwareStats
    val isOnline = device.isOnline

    var cpuHistory by remember(device.id) { mutableStateOf<List<Float>>(emptyList()) }
    var gpuHistory by remember(device.id) { mutableStateOf<List<Float>>(emptyList()) }

    // 1. CPU Temperature Card (Cream card with brown border & green sparkline)
    val cpuTemp = stats.cpu.averageTemperature
    val cpuStr = if (isOnline && cpuTemp > 0f) {
        if (useFahrenheit) "${(cpuTemp * 9f / 5f + 32).toInt()}°F" else "${cpuTemp.toInt()}°C"
    } else "--"

    LaunchedEffect(cpuTemp, isOnline) {
        if (isOnline && cpuTemp > 0f) {
            cpuHistory = if (cpuHistory.isEmpty()) listOf(cpuTemp) else (cpuHistory + cpuTemp).takeLast(20)
        }
    }

    val cpuSparklineData = if (cpuHistory.isNotEmpty()) cpuHistory else if (isOnline && cpuTemp > 0f) listOf(cpuTemp) else emptyList()
    CreamMetricCard(
        icon = Icons.Default.Thermostat,
        title = "CPU Temperature",
        mainValue = cpuStr,
        sparklineData = cpuSparklineData
    )

    Spacer(modifier = Modifier.height(12.dp))

    // 2. GPU Temperature Card (if GPU available or when offline)
    if (stats.gpu != null || !isOnline) {
        val gpuTemp = stats.gpu?.temperature ?: 0f
        val gpuStr = if (isOnline && gpuTemp > 0f) {
            if (useFahrenheit) "${(gpuTemp * 9f / 5f + 32).toInt()}°F" else "${gpuTemp.toInt()}°C"
        } else "--"

        LaunchedEffect(gpuTemp, isOnline) {
            if (isOnline && gpuTemp > 0f) {
                gpuHistory = if (gpuHistory.isEmpty()) listOf(gpuTemp) else (gpuHistory + gpuTemp).takeLast(20)
            }
        }

        val gpuSparklineData = if (gpuHistory.isNotEmpty()) gpuHistory else if (isOnline && gpuTemp > 0f) listOf(gpuTemp) else emptyList()
        CreamMetricCard(
            icon = Icons.Default.Speed,
            title = "GPU Temperature",
            mainValue = gpuStr,
            sparklineData = gpuSparklineData
        )
        Spacer(modifier = Modifier.height(12.dp))
    }

    // 3. RAM Usage Card
    val ramPercent = if (isOnline) stats.ram.usagePercent.toInt() else 0
    val ramMainValue = if (isOnline && ramPercent > 0) "$ramPercent%" else "--"
    val ramSubValue = if (isOnline && stats.ram.totalMb > 0) "${formatGb(stats.ram.usedMb)} / ${formatGb(stats.ram.totalMb)}" else "Device offline"
    val ramProgress = if (isOnline) stats.ram.usagePercent / 100f else 0f
    CreamBarMetricCard(
        icon = Icons.Default.Memory,
        title = "RAM Usage",
        mainValue = ramMainValue,
        subValue = ramSubValue,
        progress = ramProgress
    )

    Spacer(modifier = Modifier.height(12.dp))

    // 4. Battery Card (if present)
    if (stats.battery != null) {
        val bPct = if (isOnline) "${stats.battery.percentage}%" else "--"
        val bSub = if (isOnline) {
            if (stats.battery.isCharging) "Charging (${stats.battery.powerRateWatts}W)" else "Discharging"
        } else "Device offline"
        val bProg = if (isOnline) stats.battery.percentage / 100f else 0f
        CreamBarMetricCard(
            icon = Icons.Default.BatteryFull,
            title = "Battery",
            mainValue = bPct,
            subValue = bSub,
            progress = bProg,
            higherIsBetter = true
        )
        Spacer(modifier = Modifier.height(12.dp))
    }

    Spacer(modifier = Modifier.height(8.dp))

    if (!device.shared) {
        PeopleWithAccessCard(viewModel)
        Spacer(modifier = Modifier.height(12.dp))
    }

    // Danger Zone: Unpair Device button (for a shared computer it only leaves)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFFDE8E8))
            .border(1.5.dp, OfflineRed.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .clickable { onUnpairClick() }
            .padding(vertical = 12.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                tint = OfflineRed,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (device.shared) "REMOVE FROM MY ACCOUNT" else "UNPAIR THIS DEVICE",
                fontFamily = PixelFontFamily,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = OfflineRed
            )
        }
    }
}

@Composable
private fun CreamMetricCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    mainValue: String,
    sparklineData: List<Float>
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(CreamSurface)
            .border(1.5.dp, CreamBorderLight, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE8DFC8))
                    .border(1.dp, CreamBorderLight, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = CreamBorder,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontFamily = ReadableFontFamily,
                    fontSize = 12.sp,
                    color = CreamTextSecondary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = mainValue,
                    fontFamily = PixelFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = CreamText
                )
            }

            // Green Sparkline Graph
            RetroSparkline(
                dataPoints = sparklineData,
                lineColor = CreamGreenChart,
                modifier = Modifier
                    .width(80.dp)
                    .height(32.dp)
            )
        }
    }
}

@Composable
private fun CreamBarMetricCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    mainValue: String,
    subValue: String,
    progress: Float,
    higherIsBetter: Boolean = false
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(CreamSurface)
            .border(1.5.dp, CreamBorderLight, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE8DFC8))
                        .border(1.dp, CreamBorderLight, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = CreamBorder,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontFamily = ReadableFontFamily,
                        fontSize = 12.sp,
                        color = CreamTextSecondary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = mainValue,
                        fontFamily = PixelFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = CreamText
                    )
                }

                Text(
                    text = subValue,
                    fontFamily = ReadableFontFamily,
                    fontSize = 11.sp,
                    color = CreamTextSecondary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Progress bar
            RetroProgressBar(
                progress = progress,
                color = CreamGreenChart,
                trackColor = Color(0xFFE5DAC0),
                modifier = Modifier.fillMaxWidth(),
                height = 10.dp,
                higherIsBetter = higherIsBetter
            )
        }
    }
}

@Composable
private fun CpuSection(cpu: CpuStats, isOnline: Boolean, useFahrenheit: Boolean, coreStats: CoreTempStats) {
    var showThreads by remember { mutableStateOf(false) }
    Column {
        Text(
            text = "CPU",
            fontFamily = PixelFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = CreamText
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val pkgTempStr = if (isOnline && cpu.packageTemperature > 0f) {
                if (useFahrenheit) "${(cpu.packageTemperature * 9f / 5f + 32).toInt()}°F" else "${cpu.packageTemperature.toInt()}°C"
            } else "--"
            val loadStr = if (isOnline) "${cpu.totalUtilization.toInt()}%" else "--"
            val clockStr = if (isOnline && cpu.clockSpeedMhz > 0f) {
                String.format(java.util.Locale.US, "%.1f GHz", cpu.clockSpeedMhz / 1000f)
            } else "--"
            CreamStatPill(label = "Temperature", value = pkgTempStr, modifier = Modifier.weight(1f))
            CreamStatPill(label = "Load", value = loadStr, modifier = Modifier.weight(1f))
            CreamStatPill(label = "Clock", value = clockStr, modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(14.dp))

        val threads = cpu.threadUtilizations
        if (!isOnline || threads.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(CreamSurface)
                    .border(1.dp, CreamBorderLight, RoundedCornerShape(8.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (!isOnline) "Offline. Load per processor shows when it's back." else "Waiting for per-processor load…",
                    fontFamily = ReadableFontFamily,
                    fontSize = 13.sp,
                    color = CreamTextSecondary
                )
            }
        } else {
            // Per-core temperatures line up with the load rows when the chip reports them.
            val temps = cpu.coreTemperatures.takeIf { it.size == threads.size }
            if (temps != null) {
                val (cores, smt) = groupCores(temps, threads)
                Text(
                    text = "Temperature per core",
                    fontFamily = PixelFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    lineHeight = 16.sp,
                    color = CreamTextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                CoreTempTable(cores = cores, smt = smt, stats = coreStats, useFahrenheit = useFahrenheit)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (showThreads) "Hide threads" else "Show ${threads.size} threads",
                    fontFamily = ReadableFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = PixelGreen,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { showThreads = !showThreads }
                        .padding(vertical = 8.dp)
                )
                if (!showThreads) return@Column
            }
            Text(
                text = if (temps != null) "Load & temp per CPU (${threads.size})"
                else "Load per CPU (${threads.size})",
                fontFamily = PixelFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                lineHeight = 16.sp,
                color = CreamTextSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (temps == null) {
                Text(
                    text = "This chip reports one temperature for the whole CPU, shown above.",
                    fontFamily = ReadableFontFamily,
                    fontSize = 11.sp,
                    color = CreamTextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
            threads.forEachIndexed { index, util ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "CPU $index",
                        fontFamily = PixelFontFamily,
                        fontSize = 10.sp,
                        color = CreamText,
                        maxLines = 1,
                        modifier = Modifier.width(64.dp)
                    )
                    if (temps != null) {
                        val t = temps[index]
                        Text(
                            text = if (useFahrenheit) "${(t * 9f / 5f + 32).toInt()}°F" else "${t.toInt()}°C",
                            fontFamily = PixelFontFamily,
                            fontSize = 10.sp,
                            color = if (t >= 85f) Color(0xFFB91C1C) else CreamGreenChart,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.width(52.dp)
                        )
                    }
                    RetroProgressBar(
                        progress = (util / 100f).coerceIn(0f, 1f),
                        color = CreamGreenAccent,
                        trackColor = Color(0xFFE5DAC0),
                        modifier = Modifier.weight(1f),
                        height = 8.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${util.toInt()}%",
                        fontFamily = PixelFontFamily,
                        fontSize = 10.sp,
                        color = CreamTextSecondary,
                        maxLines = 1,
                        softWrap = false,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        modifier = Modifier.width(40.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun GpuSection(gpu: GpuStats?, isOnline: Boolean, useFahrenheit: Boolean) {
    if (gpu == null) {
        Text(
            text = if (isOnline) "No dedicated GPU detected on this machine." else "No dedicated GPU detected or PC is offline.",
            fontFamily = ReadableFontFamily,
            fontSize = 13.sp,
            color = CreamTextSecondary,
            modifier = Modifier.padding(vertical = 16.dp)
        )
        return
    }

    Column {
        Text(
            text = gpu.name.ifBlank { "Dedicated GPU" },
            fontFamily = PixelFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = CreamText
        )
        Spacer(modifier = Modifier.height(12.dp))

        val tempStr = if (isOnline && gpu.temperature > 0f) {
            if (useFahrenheit) "${(gpu.temperature * 9f / 5f + 32).toInt()}°F" else "${gpu.temperature.toInt()}°C"
        } else "--"
        val utilStr = if (isOnline) "${gpu.utilization.toInt()}%" else "--"
        val fanStr = if (isOnline && gpu.fanSpeedPercent > 0f) "${gpu.fanSpeedPercent.toInt()}%" else "--"

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CreamStatPill(
                label = "Temperature",
                value = tempStr,
                modifier = Modifier.weight(1f)
            )
            CreamStatPill(
                label = "Utilization",
                value = utilStr,
                modifier = Modifier.weight(1f)
            )
            CreamStatPill(
                label = "Fan Speed",
                value = fanStr,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        val clockStr = if (isOnline && gpu.clockSpeedMhz > 0f) "${gpu.clockSpeedMhz.toInt()} MHz" else "--"
        val powerStr = if (isOnline && gpu.powerWatts > 0f) "${gpu.powerWatts.toInt()} W" else "--"
        val vramStr = if (isOnline && gpu.vramTotalMb > 0) formatGb(gpu.vramUsedMb) else "--"

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CreamStatPill(
                label = "Clock Speed",
                value = clockStr,
                modifier = Modifier.weight(1f)
            )
            CreamStatPill(
                label = "Power Draw",
                value = powerStr,
                modifier = Modifier.weight(1f)
            )
            CreamStatPill(
                label = "VRAM Used",
                value = vramStr,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = if (isOnline) "VRAM Usage (${formatGb(gpu.vramUsedMb)} / ${formatGb(gpu.vramTotalMb)})" else "VRAM Usage (Offline)",
            fontFamily = ReadableFontFamily,
            fontSize = 12.sp,
            color = CreamTextSecondary
        )
        Spacer(modifier = Modifier.height(6.dp))
        val vramProgress = if (isOnline && gpu.vramTotalMb > 0) gpu.vramUsedMb.toFloat() / gpu.vramTotalMb else 0f
        RetroProgressBar(
            progress = vramProgress,
            color = CreamGreenChart,
            trackColor = Color(0xFFE5DAC0),
            modifier = Modifier.fillMaxWidth(),
            height = 10.dp
        )
    }
}

@Composable
private fun RamSection(ram: RamStats, isOnline: Boolean) {
    Column {
        Text(
            text = if (isOnline && ram.speedMhz > 0 && ram.type.isNotBlank()) "Memory Metrics (${ram.type} @ ${ram.speedMhz} MHz)" else if (isOnline && ram.type.isNotBlank()) "Memory Metrics (${ram.type})" else "Memory Metrics",
            fontFamily = PixelFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = CreamText
        )
        Spacer(modifier = Modifier.height(10.dp))

        val usedStr = if (isOnline && ram.usedMb > 0) formatGb(ram.usedMb) else "--"
        val freeStr = if (isOnline && ram.freeMb > 0) formatGb(ram.freeMb) else "--"
        val cachedStr = if (isOnline && ram.cachedMb > 0) formatGb(ram.cachedMb) else "--"

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CreamStatPill(label = "Used", value = usedStr, modifier = Modifier.weight(1f))
            CreamStatPill(label = "Free", value = freeStr, modifier = Modifier.weight(1f))
            CreamStatPill(label = "Cached", value = cachedStr, modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Top 5 by memory",
            fontFamily = PixelFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            lineHeight = 16.sp,
            color = CreamTextSecondary
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (!isOnline || ram.topProcesses.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(CreamSurface)
                    .border(1.dp, CreamBorderLight, RoundedCornerShape(8.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (!isOnline) "PC is offline · No active processes" else "No process data available",
                    fontFamily = ReadableFontFamily,
                    fontSize = 13.sp,
                    color = CreamTextSecondary
                )
            }
        } else {
            ram.topProcesses.forEach { proc ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = proc.name,
                        fontFamily = ReadableFontFamily,
                        fontSize = 13.sp,
                        color = CreamText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )

                    Row {
                        Text(
                            text = if (proc.memoryMb >= 1024) formatGb(proc.memoryMb) else "${proc.memoryMb} MB",
                            fontFamily = PixelFontFamily,
                            fontSize = 11.sp,
                            color = CreamGreenChart
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = String.format(java.util.Locale.US, "%.1f%% CPU", proc.cpuPercent),
                            fontFamily = PixelFontFamily,
                            fontSize = 11.sp,
                            color = CreamTextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BatterySection(device: Device) {
    val b = device.hardwareStats.battery
    val isOnline = device.isOnline

    if (b == null && isOnline) {
        Text(
            text = "No battery detected (Desktop or Server machine).",
            fontFamily = ReadableFontFamily,
            fontSize = 13.sp,
            color = CreamTextSecondary,
            modifier = Modifier.padding(vertical = 16.dp)
        )
        return
    }

    Column {
        Text(
            text = "Battery Health & Status",
            fontFamily = PixelFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = CreamText
        )
        Spacer(modifier = Modifier.height(10.dp))

        val pctStr = if (isOnline && b != null) "${b.percentage}%" else "--"
        val healthStr = if (isOnline && b != null) "${b.healthPercent}%" else "--"
        val cyclesStr = if (isOnline && b != null && b.chargeCycles > 0) "${b.chargeCycles}" else "--"
        val stateStr = if (isOnline && b != null) (if (b.isCharging) "Charging" else "Discharging") else "Offline"
        val rateStr = if (isOnline && b != null && b.powerRateWatts != 0f) String.format(java.util.Locale.US, "%.1f W", kotlin.math.abs(b.powerRateWatts)) else "--"

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CreamStatPill(label = "Percentage", value = pctStr, modifier = Modifier.weight(1f))
            CreamStatPill(label = "Health", value = healthStr, modifier = Modifier.weight(1f))
            CreamStatPill(label = "Cycles", value = cyclesStr, modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CreamStatPill(
                label = "State",
                value = stateStr,
                modifier = Modifier.weight(1f)
            )
            CreamStatPill(
                label = "Power Rate",
                value = rateStr,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private fun formatLastSeen(lastSeenMs: Long, isOnline: Boolean): String {
    if (isOnline) return "Status: Active Now"
    if (lastSeenMs <= 0L) return "Last seen: Unknown"
    val diffSec = (System.currentTimeMillis() - lastSeenMs) / 1000
    return when {
        diffSec < 60 -> "Last seen: Just now"
        diffSec < 3600 -> "Last seen: ${diffSec / 60}m ago"
        diffSec < 86400 -> "Last seen: ${diffSec / 3600}h ago"
        else -> "Last seen: ${diffSec / 86400}d ago"
    }
}

@Composable
private fun CreamStatPill(label: String, value: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(CreamSurface)
            .border(1.dp, CreamBorderLight, RoundedCornerShape(6.dp))
            .padding(vertical = 8.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            var valueSize by remember(value) { mutableStateOf(13f) }
            Text(
                text = value,
                fontFamily = PixelFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = valueSize.sp,
                color = CreamText,
                maxLines = 1,
                softWrap = false,
                onTextLayout = { if (it.didOverflowWidth && valueSize > 7f) valueSize -= 1f }
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontFamily = ReadableFontFamily,
                fontSize = 10.sp,
                color = CreamTextSecondary,
                maxLines = 1
            )
        }
    }
}

/** Bell in the top bar: silence this device's notifications for a while (alerts still get recorded). */
@Composable
private fun SnoozeButton(deviceId: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var open by remember { mutableStateOf(false) }
    var until by remember(deviceId) { mutableStateOf(AlertSnooze.snoozedUntil(context, deviceId)) }
    val snoozed = until > System.currentTimeMillis()

    Box {
        IconButton(onClick = { open = true }) {
            Icon(
                imageVector = if (snoozed) Icons.Default.NotificationsOff else Icons.Default.NotificationsActive,
                contentDescription = if (snoozed) "Alerts snoozed" else "Snooze alerts",
                tint = if (snoozed) CreamTextSecondary else ArcadeYellow
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (snoozed) {
                val time = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(until))
                DropdownMenuItem(
                    text = { Text("Snoozed until $time · Turn back on") },
                    onClick = { AlertSnooze.unsnooze(context, deviceId); until = 0L; open = false }
                )
            }
            listOf(
                "Snooze 1 hour" to { System.currentTimeMillis() + 3_600_000L },
                "Snooze 8 hours" to { System.currentTimeMillis() + 8 * 3_600_000L },
                "Snooze until 8 AM" to { AlertSnooze.nextMorning() }
            ).forEach { (label, untilFn) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        val t = untilFn()
                        AlertSnooze.snooze(context, deviceId, t); until = t; open = false
                    }
                )
            }
        }
    }
}

/** Owner's view of who else can see this computer, with Remove. Hidden when nobody else can. */
@Composable
private fun PeopleWithAccessCard(viewModel: DeviceDetailsViewModel) {
    LaunchedEffect(Unit) { viewModel.loadShares() }
    val shares = viewModel.shares
    if (shares.isEmpty() && viewModel.sharesError == null) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(CreamSurface)
            .border(1.5.dp, CreamBorderLight, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Text(
            text = "People with access",
            fontFamily = PixelFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            lineHeight = 16.sp,
            color = CreamText
        )
        Text(
            text = "They can see this computer's readings and get its alerts. Only you can rename or unpair it.",
            fontFamily = ReadableFontFamily,
            fontSize = 11.sp,
            color = CreamTextSecondary,
            modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
        )
        viewModel.sharesError?.let {
            Text(it, fontFamily = ReadableFontFamily, fontSize = 11.sp, color = OfflineRed)
        }
        shares.forEach { share ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = share.name ?: share.email ?: "Someone",
                        fontFamily = ReadableFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = CreamText
                    )
                    if (share.name != null && share.email != null) {
                        Text(share.email, fontFamily = ReadableFontFamily, fontSize = 11.sp, color = CreamTextSecondary)
                    }
                }
                Text(
                    text = "Remove",
                    fontFamily = ReadableFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = OfflineRed,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { viewModel.removeShare(share.userId) }
                        .padding(horizontal = 10.dp, vertical = 10.dp)
                )
            }
        }
    }
}
