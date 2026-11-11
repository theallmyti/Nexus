package com.pcmonitor.app.ui.screens.thresholds

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.pcmonitor.app.ui.theme.CardBackground
import com.pcmonitor.app.ui.theme.CreamBackground
import com.pcmonitor.app.ui.theme.CreamBorder
import com.pcmonitor.app.ui.theme.CreamText
import com.pcmonitor.app.ui.theme.CreamTextSecondary
import com.pcmonitor.app.ui.theme.DeepNavy
import com.pcmonitor.app.ui.theme.PixelBorderColor
import com.pcmonitor.app.ui.theme.PixelFontFamily
import com.pcmonitor.app.ui.theme.PixelGreen
import com.pcmonitor.app.ui.theme.ReadableFontFamily
import com.pcmonitor.app.ui.theme.TextDarkGray
import com.pcmonitor.app.ui.theme.TextGray
import com.pcmonitor.app.ui.theme.TextWhite

@Composable
fun ThresholdSettingsScreen(
    viewModel: ThresholdSettingsViewModel,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepNavy)
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
            // Section: Default Thresholds (Cream-toned container matching reference)
            Text(
                text = "Default Thresholds",
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

            // Section: Per Drive Settings
            Text(
                text = "Per Drive Settings",
                fontFamily = PixelFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = ArcadeYellow
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Drive C:
            DriveSettingCard(driveLetter = "C:", value = "${viewModel.cDriveThreshold.toInt()}% free")

            Spacer(modifier = Modifier.height(8.dp))

            // Drive D:
            DriveSettingCard(driveLetter = "D:", value = "${viewModel.dDriveThreshold.toInt()}% free")

            Spacer(modifier = Modifier.height(16.dp))

            // Info note card (Arcade yellow tinted card matching reference)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFFFF8E1))
                    .border(1.dp, Color(0xFFFFD54F), RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFFF57F17),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "You can set custom thresholds for each device in the device settings.",
                        fontFamily = ReadableFontFamily,
                        fontSize = 11.sp,
                        color = Color(0xFF5D4037),
                        lineHeight = 16.sp
                    )
                }
            }

            if (viewModel.saveFeedback != null) {
                Spacer(modifier = Modifier.height(12.dp))
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
                text = "Save Changes",
                onClick = { viewModel.saveChanges() },
                variant = RetroButtonVariant.Secondary,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            RetroButton(
                text = "Reset to Defaults",
                onClick = { viewModel.resetToDefaults() },
                variant = RetroButtonVariant.Outline,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
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

@Composable
private fun DriveSettingCard(
    driveLetter: String,
    value: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(CreamBackground)
            .border(1.dp, CreamBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFFE8DFC8)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = driveLetter,
                        fontFamily = PixelFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = CreamText
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = value,
                    fontFamily = PixelFontFamily,
                    fontSize = 12.sp,
                    color = CreamText
                )
            }
            Text(
                text = "›",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = CreamBorder
            )
        }
    }
}

