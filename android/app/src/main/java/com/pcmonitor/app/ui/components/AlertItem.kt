package com.pcmonitor.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pcmonitor.app.data.model.Alert
import com.pcmonitor.app.data.model.AlertCategory
import com.pcmonitor.app.data.model.AlertSeverity
import com.pcmonitor.app.ui.theme.CriticalRed
import com.pcmonitor.app.ui.theme.DeepNavy
import com.pcmonitor.app.ui.theme.OnlineGreen
import com.pcmonitor.app.ui.theme.PixelFontFamily
import com.pcmonitor.app.ui.theme.ReadableFontFamily
import com.pcmonitor.app.ui.theme.WarningOrange
import com.pcmonitor.app.ui.theme.WhiteCardBg
import com.pcmonitor.app.ui.theme.WhiteCardBorder
import com.pcmonitor.app.ui.theme.WhiteCardText
import com.pcmonitor.app.ui.theme.WhiteCardTextSecondary

fun formatRelativeTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        else -> "${days}d ago"
    }
}

@Composable
fun AlertItem(
    alert: Alert,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background == DeepNavy
    val cardBg = if (isDark) WhiteCardBg else Color.White
    val cardBorder = if (isDark) WhiteCardBorder else Color(0xFFE2E8F0)
    val titleColor = if (isDark) WhiteCardText else Color(0xFF0F172A)
    val detailColor = if (isDark) WhiteCardTextSecondary else Color(0xFF64748B)
    val timeColor = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8)

    val severityColor = when (alert.severity) {
        AlertSeverity.CRITICAL -> CriticalRed
        AlertSeverity.WARNING -> WarningOrange
        AlertSeverity.OFFLINE -> Color(0xFF94A3B8)
        AlertSeverity.INFO -> OnlineGreen
    }

    val icon = when (alert.category) {
        AlertCategory.CPU_TEMP, AlertCategory.GPU_TEMP -> Icons.Default.Thermostat
        AlertCategory.RAM_USAGE -> Icons.Default.Memory
        AlertCategory.DISK_SPACE -> Icons.Default.Storage
        AlertCategory.DEVICE_OFFLINE -> Icons.Default.PowerOff
        AlertCategory.DEVICE_ONLINE -> Icons.Default.CheckCircle
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(cardBg)
            .border(1.5.dp, cardBorder, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon with colored circle background
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(severityColor.copy(alpha = 0.15f))
                    .border(1.dp, severityColor.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = alert.category.label,
                    tint = severityColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Alert details
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = alert.title,
                        fontFamily = ReadableFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = titleColor
                    )
                    if (!alert.isRead) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(CriticalRed)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                val detailText = if (alert.value != null) {
                    "${alert.deviceName} · ${alert.value.toInt()}${if (alert.category == AlertCategory.RAM_USAGE) "%" else "°C"}"
                } else {
                    alert.deviceName
                }
                Text(
                    text = detailText,
                    fontFamily = ReadableFontFamily,
                    fontSize = 11.sp,
                    color = detailColor
                )
            }

            // Relative timestamp
            Text(
                text = formatRelativeTime(alert.timestamp),
                fontFamily = ReadableFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                color = timeColor
            )
        }
    }
}
