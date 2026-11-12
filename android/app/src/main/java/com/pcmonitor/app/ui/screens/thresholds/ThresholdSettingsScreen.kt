package com.pcmonitor.app.ui.screens.thresholds

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pcmonitor.app.ui.components.RetroButton
import com.pcmonitor.app.ui.components.RetroButtonVariant
import com.pcmonitor.app.ui.components.RetroTopBar
import com.pcmonitor.app.ui.theme.ArcadeYellow
import com.pcmonitor.app.ui.theme.CreamBackground
import com.pcmonitor.app.ui.theme.CreamBorder
import com.pcmonitor.app.ui.theme.CreamText
import com.pcmonitor.app.ui.theme.PixelFontFamily
import com.pcmonitor.app.ui.theme.PixelGreen
import com.pcmonitor.app.ui.theme.ReadableFontFamily

@Composable
fun ThresholdSettingsScreen(
    viewModel: ThresholdSettingsViewModel,
    onBack: () -> Unit
) {
    val devices by viewModel.devices.collectAsState()
    val isDark = MaterialTheme.colorScheme.background.red < 0.5f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        RetroTopBar(
            title = "Threshold Settings",
            onBackClick = onBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Scope / Device Selector Section
            Text(
                text = "Target Scope",
                fontFamily = PixelFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = ArcadeYellow
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Horizontally scrollable chip row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // "All Devices (Global)" Chip
                ScopeChip(
                    label = "Global Defaults",
                    icon = Icons.Default.Language,
                    isSelected = viewModel.selectedDeviceId == null,
                    isDark = isDark,
                    onClick = { viewModel.selectDevice(null) }
                )

                // Per-device chips
                devices.forEach { device ->
                    ScopeChip(
                        label = device.name,
                        icon = Icons.Default.Computer,
                        isSelected = viewModel.selectedDeviceId == device.id,
                        isDark = isDark,
                        onClick = { viewModel.selectDevice(device.id) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Status Banner for Current Selection
            val selectedDev = devices.find { it.id == viewModel.selectedDeviceId }
            if (viewModel.selectedDeviceId == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isDark) Color(0xFF1E293B) else Color(0xFFEDE7D9))
                        .border(1.dp, if (isDark) Color(0xFF334155) else CreamBorder, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = ArcadeYellow,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Global Thresholds: Applied to all devices unless overridden with custom limits.",
                            fontFamily = ReadableFontFamily,
                            fontSize = 12.sp,
                            color = if (isDark) Color(0xFFE2E8F0) else Color(0xFF2C2214),
                            lineHeight = 16.sp
                        )
                    }
                }
            } else {
                val hasCustom = viewModel.isCustomForSelectedDevice
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (hasCustom) Color(0xFF0D3320) else (if (isDark) Color(0xFF2D2412) else Color(0xFFFFF8E1)))
                        .border(
                            1.dp,
                            if (hasCustom) PixelGreen else Color(0xFFFFD54F),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (hasCustom) Icons.Default.CheckCircle else Icons.Default.Info,
                            contentDescription = null,
                            tint = if (hasCustom) PixelGreen else Color(0xFFF57F17),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (hasCustom) "Custom Thresholds Active: ${selectedDev?.name ?: "Device"}" else "Inheriting Global Defaults",
                                fontFamily = PixelFontFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (hasCustom) PixelGreen else (if (isDark) ArcadeYellow else Color(0xFF5D4037))
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (hasCustom)
                                    "Alerts for this device use these custom parameters."
                                else
                                    "Modify sliders and tap 'Save Changes' to assign custom thresholds to this device.",
                                fontFamily = ReadableFontFamily,
                                fontSize = 11.sp,
                                color = if (hasCustom) Color(0xFFB4F0D0) else (if (isDark) Color(0xFFE2E8F0) else Color(0xFF5D4037)),
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section: Threshold Sliders
            Text(
                text = if (viewModel.selectedDeviceId == null) "Default Threshold Values" else "${selectedDev?.name ?: "Device"} Thresholds",
                fontFamily = PixelFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = ArcadeYellow
            )

            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(CreamBackground)
                    .border(2.dp, CreamBorder, RoundedCornerShape(10.dp))
                    .padding(16.dp)
            ) {
                Column {
                    // CPU Threshold Slider
                    ThresholdSliderItem(
                        icon = Icons.Default.Thermostat,
                        label = "CPU Temperature",
                        value = "${viewModel.cpuThreshold.toInt()}°C",
                        current = viewModel.cpuThreshold,
                        range = 50f..105f,
                        onValueChange = { viewModel.cpuThreshold = it }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // GPU Threshold Slider
                    ThresholdSliderItem(
                        icon = Icons.Default.Speed,
                        label = "GPU Temperature",
                        value = "${viewModel.gpuThreshold.toInt()}°C",
                        current = viewModel.gpuThreshold,
                        range = 50f..100f,
                        onValueChange = { viewModel.gpuThreshold = it }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // RAM Threshold Slider
                    ThresholdSliderItem(
                        icon = Icons.Default.Memory,
                        label = "RAM Usage",
                        value = "${viewModel.ramThreshold.toInt()}%",
                        current = viewModel.ramThreshold,
                        range = 50f..99f,
                        onValueChange = { viewModel.ramThreshold = it }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Disk Space Threshold Slider
                    ThresholdSliderItem(
                        icon = Icons.Default.Storage,
                        label = "Disk Space",
                        value = "< ${viewModel.diskThreshold.toInt()}% free",
                        current = viewModel.diskThreshold,
                        range = 5f..30f,
                        onValueChange = { viewModel.diskThreshold = it }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (viewModel.saveFeedback != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = viewModel.saveFeedback ?: "",
                    fontFamily = ReadableFontFamily,
                    fontSize = 12.sp,
                    color = PixelGreen,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons
            RetroButton(
                text = if (viewModel.selectedDeviceId == null) "Save Global Thresholds" else "Save Device Thresholds",
                onClick = { viewModel.saveChanges() },
                variant = RetroButtonVariant.Secondary,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            RetroButton(
                text = if (viewModel.selectedDeviceId == null)
                    "Reset to Defaults"
                else if (viewModel.isCustomForSelectedDevice)
                    "Revert to Global Defaults"
                else
                    "Reset Values",
                onClick = { viewModel.resetToDefaults() },
                variant = RetroButtonVariant.Outline,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ScopeChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val bg = if (isSelected) {
        ArcadeYellow
    } else {
        if (isDark) Color(0xFF1E293B) else Color(0xFFEDE7D9)
    }
    val contentColor = if (isSelected) {
        Color(0xFF0F172A)
    } else {
        if (isDark) Color(0xFFE2E8F0) else Color(0xFF2C2214)
    }
    val borderColor = if (isSelected) ArcadeYellow else (if (isDark) Color(0xFF334155) else CreamBorder)

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.5.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            fontFamily = PixelFontFamily,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = contentColor
        )
    }
}

@Composable
private fun ThresholdSliderItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    current: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = CreamBorder,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = label,
                    fontFamily = ReadableFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = CreamText
                )
            }
            Text(
                text = value,
                fontFamily = PixelFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = CreamText
            )
        }

        Slider(
            value = current,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = CreamBorder,
                activeTrackColor = PixelGreen,
                inactiveTrackColor = Color(0xFFE5DAC0)
            )
        )
    }
}

