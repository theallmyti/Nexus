package com.pcmonitor.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pcmonitor.app.data.model.Device
import com.pcmonitor.app.data.model.DeviceType
import com.pcmonitor.app.data.model.ageLabel
import com.pcmonitor.app.data.model.problem
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import com.pcmonitor.app.ui.theme.PixelFontFamily
import com.pcmonitor.app.ui.theme.ReadableFontFamily
import com.pcmonitor.app.ui.theme.WhiteCardBg
import com.pcmonitor.app.ui.theme.WhiteCardBorder
import com.pcmonitor.app.ui.theme.WhiteCardText
import com.pcmonitor.app.ui.theme.WhiteCardTextSecondary

@Composable
fun DeviceCard(
    device: Device,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    useFahrenheit: Boolean = false,
    showProblemChip: Boolean = true
) {
    val stats = device.hardwareStats
    val isOnline = device.isOnline
    val problem = device.problem()
    // Re-render every 5 s so "Updated 12s ago" keeps counting without new data.
    val now by produceState(System.currentTimeMillis()) {
        while (true) { delay(5_000L); value = System.currentTimeMillis() }
    }

    // Matches the crisp white card with subtle border from reference screen 4
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(WhiteCardBg)
            .border(
                if (problem != null) 2.5.dp else 1.5.dp,
                if (problem != null) Color(0xFFDC2626) else WhiteCardBorder,
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Column {
            // Header Row: Device Type Icon, Name, Subtitle, and Status Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Beige Device Type Icon Tile
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFF5ECD0))
                        .border(1.dp, Color(0xFF8B7355).copy(alpha = 0.6f), RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val icon = when (device.type) {
                        DeviceType.LAPTOP -> Icons.Default.Laptop
                        DeviceType.DESKTOP -> Icons.Default.Computer
                        DeviceType.SERVER -> Icons.Default.Dns
                        DeviceType.RASPBERRY_PI -> Icons.Default.Memory
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = device.type.label,
                        tint = if (isOnline) Color(0xFF2563EB) else Color(0xFF94A3B8),
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Name and Subtitle
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = device.name,
                        fontFamily = PixelFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        lineHeight = 17.sp,
                        color = WhiteCardText
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${device.os} · ${device.type.label}",
                        fontFamily = ReadableFontFamily,
                        fontSize = 12.sp,
                        color = WhiteCardTextSecondary
                    )
                    Text(
                        text = if (isOnline) "Updated ${ageLabel(device.lastSeen, now)}" else "Last seen ${ageLabel(device.lastSeen, now)}",
                        fontFamily = ReadableFontFamily,
                        fontSize = 10.sp,
                        color = WhiteCardTextSecondary.copy(alpha = 0.8f)
                    )
                }

                // Online/Offline Status Pill (Light green / Light red from reference)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isOnline) Color(0xFFDCFCE7) else Color(0xFFFEE2E2))
                        .border(
                            1.dp,
                            if (isOnline) Color(0xFF86EFAC) else Color(0xFFFCA5A5),
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isOnline) Color(0xFF16A34A) else Color(0xFFDC2626))
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (isOnline) "Online" else "Offline",
                        fontFamily = ReadableFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isOnline) Color(0xFF15803D) else Color(0xFFB91C1C)
                    )
                }
            }

            if (problem != null && showProblemChip) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFFFEE2E2))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFB91C1C),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = problem,
                        fontFamily = ReadableFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color(0xFFB91C1C)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Metrics Row: CPU, GPU, RAM, Battery
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Columns share the width evenly; GPU / Battery are left out on devices without them (e.g. a Pi).
                val showGpu = device.hasGpu ?: (stats.gpu != null)
                val showBattery = device.hasBattery ?: (stats.battery != null)
                val cell = Modifier.weight(1f)
                // CPU
                val cpuTemp = stats.cpu.averageTemperature
                val cpuDisplay = if (isOnline) {
                    if (cpuTemp > 0f) {
                        if (useFahrenheit) "${(cpuTemp * 9f / 5f + 32).toInt()}°F" else "${cpuTemp.toInt()}°C"
                    } else if (stats.cpu.totalUtilization > 0f) {
                        "${stats.cpu.totalUtilization.toInt()}%"
                    } else "--"
                } else "--"
                StatMiniBox(value = cpuDisplay, label = "CPU", modifier = cell)

                // GPU
                val gpuDisplay = if (isOnline && stats.gpu != null) {
                    val gpuTemp = stats.gpu.temperature
                    if (useFahrenheit) "${(gpuTemp * 9f / 5f + 32).toInt()}°F" else "${gpuTemp.toInt()}°C"
                } else "--"
                if (showGpu) StatMiniBox(value = gpuDisplay, label = "GPU", modifier = cell)

                // RAM
                val ramDisplay = if (isOnline) "${stats.ram.usagePercent.toInt()}%" else "--"
                StatMiniBox(value = ramDisplay, label = "RAM", modifier = cell)

                // Battery
                val batteryDisplay = if (isOnline && stats.battery != null) "${stats.battery.percentage}%" else "--"
                if (showBattery) StatMiniBox(value = batteryDisplay, label = "Battery", modifier = cell)
            }
        }
    }
}

@Composable
private fun StatMiniBox(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontFamily = PixelFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            color = WhiteCardText
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            fontFamily = ReadableFontFamily,
            fontSize = 11.sp,
            color = WhiteCardTextSecondary
        )
    }
}
