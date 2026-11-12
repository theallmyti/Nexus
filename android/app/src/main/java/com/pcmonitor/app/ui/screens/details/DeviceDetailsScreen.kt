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
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                        color = ArcadeYellow
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
                    IconButton(onClick = { showUnpairDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Unpair Device",
                            tint = OfflineRed
                        )
                    }
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
                                text = device.name,
                                fontFamily = ReadableFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = CreamText
                            )

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
                                    fontFamily = PixelFontFamily,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (device.isOnline) Color(0xFF2E6930) else Color(0xFFA94442)
                                )
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = formatLastSeen(device.lastSeen, device.isOnline),
                                fontFamily = ReadableFontFamily,
                                fontSize = 11.sp,
                                color = CreamTextSecondary
                            )

                            Text(
                                text = if (device.isOnline && device.hardwareStats.usage.uptimeSeconds > 0) {
                                    "Uptime: ${device.hardwareStats.usage.uptimeFormatted}"
                                } else {
                                    "Uptime: Offline"
                                },
                                fontFamily = ReadableFontFamily,
                                fontSize = 11.sp,
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
                        DetailTab.entries.forEach { tab ->
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
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = tab.label,
                                    fontFamily = PixelFontFamily,
                                    fontSize = 9.sp,
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

                    // Dynamic tab contents
                    when (viewModel.selectedTab) {
                        DetailTab.OVERVIEW -> {
                            OverviewSection(
                                device = device,
                                useFahrenheit = state.useFahrenheit,
                                onUnpairClick = { showUnpairDialog = true }
                            )
                        }
                        DetailTab.CPU -> {
                            CpuSection(
                                cpu = device.hardwareStats.cpu,
                                isOnline = device.isOnline,
                                useFahrenheit = state.useFahrenheit
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
                        text = "UNPAIR DEVICE?",
                        fontFamily = PixelFontFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = CreamText
                    )
                },
                text = {
                    Text(
                        text = "Are you sure you want to unpair \"${device.name}\"? This will remove it from your account and stop live monitoring.",
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
                            text = "UNPAIR",
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
    onUnpairClick: () -> Unit
) {
    val stats = device.hardwareStats
    val isOnline = device.isOnline

    var cpuHistory by remember(device.id) { mutableStateOf<List<Float>>(emptyList()) }
    var gpuHistory by remember(device.id) { mutableStateOf<List<Float>>(emptyList()) }

    // 1. CPU Temperature Card (Cream card with brown border & green sparkline)
    val cpuTemp = stats.cpu.averageTemperature
    val cpuStr = if (isOnline && cpuTemp > 0f) {
        if (useFahrenheit) "${(cpuTemp * 9f / 5f + 32).toInt()}°F (avg)" else "${cpuTemp.toInt()}°C (avg)"
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
            if (useFahrenheit) "${(gpuTemp * 9f / 5f + 32).toInt()}°F (avg)" else "${gpuTemp.toInt()}°C (avg)"
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
    val ramSubValue = if (isOnline && stats.ram.totalMb > 0) "${stats.ram.usedMb / 1024} GB / ${stats.ram.totalMb / 1024} GB" else "Device offline"
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
            title = "Battery (Laptop)",
            mainValue = bPct,
            subValue = bSub,
            progress = bProg
        )
        Spacer(modifier = Modifier.height(12.dp))
    }

    Spacer(modifier = Modifier.height(8.dp))

    // Danger Zone: Unpair Device button
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
                text = "UNPAIR THIS DEVICE",
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
    progress: Float
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
                height = 10.dp
            )
        }
    }
}

@Composable
private fun CpuSection(cpu: CpuStats, isOnline: Boolean, useFahrenheit: Boolean) {
    var showThreads by remember { mutableStateOf(false) }

    Column {
        Text(
            text = "CPU Metrics & Core Utilization",
            fontFamily = PixelFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = CreamText
        )
        Spacer(modifier = Modifier.height(4.dp))
        val coreCount = cpu.coreUtilizations.size
        val threadCount = cpu.threadUtilizations.size
        val archSubtitle = if (coreCount > 0 && threadCount > 0) {
            "Multi-Core Architecture · $coreCount Cores / $threadCount Threads"
        } else if (coreCount > 0) {
            "Multi-Core Architecture · $coreCount Cores"
        } else {
            "Multi-Core Architecture"
        }
        Text(
            text = archSubtitle,
            fontFamily = ReadableFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            color = CreamTextSecondary
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Grid of Key Stats
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val pkgTempStr = if (isOnline && cpu.packageTemperature > 0f) {
                if (useFahrenheit) "${(cpu.packageTemperature * 9f / 5f + 32).toInt()}°F" else "${cpu.packageTemperature.toInt()}°C"
            } else "--"

            val clockStr = if (isOnline && cpu.clockSpeedMhz > 0f) {
                "${(cpu.clockSpeedMhz / 1000f).let { "%.1f".format(it) }} GHz"
            } else "--"

            val powerStr = if (isOnline && cpu.powerWatts > 0f) {
                "${cpu.powerWatts.toInt()} W"
            } else "--"

            CreamStatPill(
                label = "Package Temp",
                value = pkgTempStr,
                modifier = Modifier.weight(1f)
            )
            CreamStatPill(
                label = "Clock Speed",
                value = clockStr,
                modifier = Modifier.weight(1f)
            )
            CreamStatPill(
                label = "Power",
                value = powerStr,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (!isOnline) {
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
                    text = "PC is offline · No live core metrics available",
                    fontFamily = ReadableFontFamily,
                    fontSize = 13.sp,
                    color = CreamTextSecondary
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (showThreads) "Logical Threads ($threadCount Threads)" else "Physical Cores ($coreCount Cores)",
                    fontFamily = ReadableFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = CreamTextSecondary
                )

                Text(
                    text = if (showThreads) "Show $coreCount Cores" else "Show $threadCount Threads",
                    fontFamily = ReadableFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = PixelGreen,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { showThreads = !showThreads }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (!showThreads) {
                // Render Physical Cores
                cpu.coreUtilizations.forEachIndexed { index, util ->
                    val label = "Core $index"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label,
                            fontFamily = PixelFontFamily,
                            fontSize = 10.sp,
                            color = CreamText,
                            maxLines = 1,
                            modifier = Modifier.width(92.dp)
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        Text(
                            text = "${cpu.packageTemperature.toInt()}°C",
                            fontFamily = PixelFontFamily,
                            fontSize = 10.sp,
                            color = CreamGreenChart,
                            modifier = Modifier.width(44.dp)
                        )

                        Spacer(modifier = Modifier.width(6.dp))

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
                            modifier = Modifier.width(36.dp)
                        )
                    }
                }
            } else {
                // Render 20 Logical Threads
                val threads = if (cpu.threadUtilizations.isNotEmpty()) cpu.threadUtilizations else cpu.coreUtilizations
                threads.forEachIndexed { index, util ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Thread $index",
                            fontFamily = PixelFontFamily,
                            fontSize = 10.sp,
                            color = CreamText,
                            maxLines = 1,
                            modifier = Modifier.width(92.dp)
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        Text(
                            text = "${cpu.packageTemperature.toInt()}°C",
                            fontFamily = PixelFontFamily,
                            fontSize = 10.sp,
                            color = CreamGreenChart,
                            modifier = Modifier.width(44.dp)
                        )

                        Spacer(modifier = Modifier.width(6.dp))

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
                            modifier = Modifier.width(36.dp)
                        )
                    }
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
        val fanStr = if (isOnline) "${gpu.fanSpeedPercent.toInt()}%" else "--"

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
        val vramStr = if (isOnline && gpu.vramUsedMb > 0) "${gpu.vramUsedMb / 1024} GB" else "--"

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
            text = if (isOnline) "VRAM Usage (${gpu.vramUsedMb} MB / ${gpu.vramTotalMb} MB)" else "VRAM Usage (Offline)",
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

        val usedStr = if (isOnline && ram.usedMb > 0) "${ram.usedMb / 1024} GB" else "--"
        val freeStr = if (isOnline && ram.freeMb > 0) "${ram.freeMb / 1024} GB" else "--"
        val cachedStr = if (isOnline && ram.cachedMb > 0) "${ram.cachedMb / 1024} GB" else "--"

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CreamStatPill(label = "Used", value = usedStr, modifier = Modifier.weight(1f))
            CreamStatPill(label = "Free", value = freeStr, modifier = Modifier.weight(1f))
            CreamStatPill(label = "Cached", value = cachedStr, modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Top 5 Memory-Consuming Processes",
            fontFamily = ReadableFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
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
                        color = CreamText
                    )

                    Row {
                        Text(
                            text = "${proc.memoryMb} MB",
                            fontFamily = PixelFontFamily,
                            fontSize = 11.sp,
                            color = CreamGreenChart
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "${proc.cpuPercent}% CPU",
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
        val cyclesStr = if (isOnline && b != null) "${b.chargeCycles}" else "--"
        val stateStr = if (isOnline && b != null) (if (b.isCharging) "Charging" else "Discharging") else "Offline"
        val rateStr = if (isOnline && b != null) "${b.powerRateWatts} W" else "--"

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
            Text(
                text = value,
                fontFamily = PixelFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = CreamText
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontFamily = ReadableFontFamily,
                fontSize = 10.sp,
                color = CreamTextSecondary
            )
        }
    }
}

