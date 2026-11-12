package com.pcmonitor.app.ui.screens.alerts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pcmonitor.app.data.model.Alert
import com.pcmonitor.app.data.model.AlertSeverity
import com.pcmonitor.app.data.repository.AlertRepository
import com.pcmonitor.app.ui.components.RetroButton
import com.pcmonitor.app.ui.components.RetroButtonVariant
import com.pcmonitor.app.ui.components.RetroTopBar
import com.pcmonitor.app.ui.components.formatRelativeTime
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
import com.pcmonitor.app.ui.theme.WarningOrange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun AlertDetailScreen(
    alertId: String,
    alertRepository: AlertRepository,
    onBack: () -> Unit
) {
    val alerts by alertRepository.alerts.collectAsState()
    val alert = alerts.find { it.id == alertId }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        RetroTopBar(
            title = "Alert Details",
            onBackClick = onBack,
            actions = {
                if (alert != null) {
                    IconButton(onClick = {
                        coroutineScope.launch {
                            alertRepository.clearAlert(alert.id)
                        }
                        onBack()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Dismiss Alert",
                            tint = ArcadeYellow
                        )
                    }
                }
            }
        )

        if (alert == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Alert not found", color = MaterialTheme.colorScheme.onBackground)
            }
            return
        }

        val severityColor = when (alert.severity) {
            AlertSeverity.CRITICAL -> CriticalRed
            AlertSeverity.WARNING -> WarningOrange
            AlertSeverity.OFFLINE -> Color(0xFF9E9E9E)
            AlertSeverity.INFO -> PixelGreen
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            // Beige Alert Detail Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFFDF6E3))
                    .border(2.dp, Color(0xFF8B7355), RoundedCornerShape(12.dp))
                    .padding(20.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(severityColor.copy(alpha = 0.2f))
                                .border(1.dp, severityColor, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = severityColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = alert.severity.label.uppercase(),
                                fontFamily = ReadableFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = severityColor
                            )
                            Text(
                                text = formatRelativeTime(alert.timestamp),
                                fontFamily = ReadableFontFamily,
                                fontSize = 12.sp,
                                color = Color(0xFF6B5B45)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = alert.title,
                        fontFamily = ReadableFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color(0xFF3E2F1C)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Device: ${alert.deviceName}",
                        fontFamily = PixelFontFamily,
                        fontSize = 12.sp,
                        color = Color(0xFF8B7355)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = alert.description,
                        fontFamily = ReadableFontFamily,
                        fontSize = 14.sp,
                        color = Color(0xFF6B5B45),
                        lineHeight = 22.sp
                    )

                    if (alert.value != null && alert.threshold != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFF5ECD0))
                                .border(1.dp, Color(0xFF8B7355), RoundedCornerShape(6.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Recorded: ${alert.value.toInt()}",
                                fontFamily = PixelFontFamily,
                                fontSize = 12.sp,
                                color = severityColor
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = "Threshold: ${alert.threshold.toInt()}",
                                fontFamily = PixelFontFamily,
                                fontSize = 12.sp,
                                color = Color(0xFF6B5B45)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            RetroButton(
                text = "Dismiss Alert",
                onClick = {
                    CoroutineScope(Dispatchers.Default).launch {
                        alertRepository.clearAlert(alert.id)
                    }
                    onBack()
                },
                variant = RetroButtonVariant.Danger,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

