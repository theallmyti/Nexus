package com.pcmonitor.app.ui.screens.history

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.pcmonitor.app.ui.components.RetroLineChart
import com.pcmonitor.app.ui.components.RetroTopBar
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
import com.pcmonitor.app.ui.theme.PixelFontFamily
import com.pcmonitor.app.ui.theme.PixelGreen
import com.pcmonitor.app.ui.theme.ReadableFontFamily
import com.pcmonitor.app.ui.theme.TextDarkGray
import com.pcmonitor.app.ui.theme.TextGray
import com.pcmonitor.app.ui.theme.TextWhite

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var deviceDropdownExpanded by remember { mutableStateOf(false) }

    val currentDevice = state.devices.find { it.id == viewModel.selectedDeviceId }
        ?: state.devices.firstOrNull()
    val isDark = MaterialTheme.colorScheme.background.red < 0.5f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        RetroTopBar(
            title = "History",
            onBackClick = onBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Time range pills: 7 Days (Green), 30 Days, Custom
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(7 to "7 Days", 30 to "30 Days", 90 to "Custom").forEach { (days, label) ->
                    val isSelected = viewModel.selectedDays == days
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isSelected) PixelGreen else (if (isDark) Color(0xFF162640) else Color(0xFFEADFC9))
                            )
                            .border(
                                1.dp,
                                if (isSelected) PixelGreen else (if (isDark) Color(0xFF2A3F5F) else CreamBorder),
                                RoundedCornerShape(6.dp)
                            )
                            .clickable { viewModel.onDaysSelected(days) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontFamily = PixelFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = if (isSelected) Color(0xFF0F172A) else (if (isDark) TextGray else Color(0xFF5D4037))
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Cream-colored History Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CreamBackground)
                    .border(2.dp, CreamBorder, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Column {
                    // Device Selector Dropdown inside cream card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(CreamSurface)
                            .border(1.dp, CreamBorderLight, RoundedCornerShape(6.dp))
                            .clickable { deviceDropdownExpanded = true }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = currentDevice?.name ?: "Select Device",
                                fontFamily = ReadableFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = CreamText
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Select",
                                tint = CreamBorder
                            )
                        }

                        DropdownMenu(
                            expanded = deviceDropdownExpanded,
                            onDismissRequest = { deviceDropdownExpanded = false }
                        ) {
                            state.devices.forEach { dev ->
                                DropdownMenuItem(
                                    text = { Text(dev.name) },
                                    onClick = {
                                        viewModel.onDeviceSelected(dev.id)
                                        deviceDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Chart Metric Header
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Thermostat,
                            contentDescription = null,
                            tint = CreamBorder,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "CPU Temperature",
                            fontFamily = ReadableFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = CreamText
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Stepped Retro Canvas Line Chart
                    val summary = state.summary
                    if (summary != null && summary.entries.isNotEmpty()) {
                        val points = summary.entries.map { it.cpuTemp }
                        val fmt = java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault())
                        val labels = if (summary.entries.size >= 2) {
                            listOf(
                                fmt.format(java.util.Date(summary.entries.first().timestamp)),
                                fmt.format(java.util.Date(summary.entries[summary.entries.size / 2].timestamp)),
                                fmt.format(java.util.Date(summary.entries.last().timestamp))
                            )
                        } else emptyList()

                        RetroLineChart(
                            dataPoints = points,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            lineColor = CreamGreenChart,
                            fillColor = CreamGreenAccent.copy(alpha = 0.3f),
                            gridColor = CreamBorderLight.copy(alpha = 0.5f),
                            labelColor = CreamTextSecondary,
                            labels = labels,
                            minValue = 0f,
                            maxValue = 100f
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(CreamSurface)
                                .border(1.dp, CreamBorderLight, RoundedCornerShape(8.dp))
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No historical summary data available yet",
                                fontFamily = ReadableFontFamily,
                                fontSize = 13.sp,
                                color = CreamTextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Summary (7 days) Box
                    Text(
                        text = "Summary (${viewModel.selectedDays} days)",
                        fontFamily = PixelFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = CreamText
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    val avgTempStr = if (summary != null && summary.avgCpuTemp > 0f) "${summary.avgCpuTemp.toInt()}°C" else "--"
                    val maxTempStr = if (summary != null && summary.maxCpuTemp > 0f) "${summary.maxCpuTemp.toInt()}°C" else "--"
                    val avgRamStr = if (summary != null && summary.avgRamUsage > 0f) "${summary.avgRamUsage.toInt()}%" else "--"
                    val uptimeStr = if (summary != null && summary.totalUptimeSeconds > 0L) summary.totalUptimeFormatted else "--"

                    SummaryRow(label = "Avg. CPU Temp", value = avgTempStr)
                    SummaryRow(label = "Max. CPU Temp", value = maxTempStr)
                    SummaryRow(label = "Avg. RAM Usage", value = avgRamStr)
                    SummaryRow(label = "Total Uptime", value = uptimeStr)
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontFamily = ReadableFontFamily,
            fontSize = 13.sp,
            color = CreamTextSecondary
        )
        Text(
            text = value,
            fontFamily = PixelFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            color = CreamText
        )
    }
}

