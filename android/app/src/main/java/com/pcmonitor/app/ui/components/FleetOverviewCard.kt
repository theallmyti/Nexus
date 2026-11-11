package com.pcmonitor.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pcmonitor.app.ui.theme.PixelFontFamily
import com.pcmonitor.app.ui.theme.ReadableFontFamily
import com.pcmonitor.app.ui.theme.TextWhite

@Composable
fun FleetOverviewCard(
    totalDevices: Int,
    onlineDevices: Int,
    offlineDevices: Int,
    criticalDevices: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Total Box (Green as shown in reference)
        FleetStatBox(
            count = totalDevices,
            label = "Total",
            backgroundColor = Color(0xFF388E3C),
            modifier = Modifier.weight(1f)
        )

        // Online Box (Green)
        FleetStatBox(
            count = onlineDevices,
            label = "Online",
            backgroundColor = Color(0xFF4CAF50),
            modifier = Modifier.weight(1f)
        )

        // Offline Box (Orange)
        FleetStatBox(
            count = offlineDevices,
            label = "Offline",
            backgroundColor = Color(0xFFFB8C00),
            modifier = Modifier.weight(1f)
        )

        // Critical Box (Red)
        FleetStatBox(
            count = criticalDevices,
            label = "Critical",
            backgroundColor = Color(0xFFE53935),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun FleetStatBox(
    count: Int,
    label: String,
    backgroundColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .border(1.5.dp, Color.Black.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
            .padding(vertical = 10.dp, horizontal = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "$count",
                fontFamily = PixelFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = TextWhite
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = label,
                fontFamily = ReadableFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                color = TextWhite.copy(alpha = 0.95f)
            )
        }
    }
}
