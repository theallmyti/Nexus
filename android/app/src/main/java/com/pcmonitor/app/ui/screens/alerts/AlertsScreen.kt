package com.pcmonitor.app.ui.screens.alerts

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.pcmonitor.app.ui.components.AlertItem
import com.pcmonitor.app.ui.components.RetroTopBar
import com.pcmonitor.app.ui.theme.ArcadeYellow
import com.pcmonitor.app.ui.theme.CardBackground
import com.pcmonitor.app.ui.theme.CriticalRed
import com.pcmonitor.app.ui.theme.DeepNavy
import com.pcmonitor.app.ui.theme.OfflineRed
import com.pcmonitor.app.ui.theme.PixelBorderColor
import com.pcmonitor.app.ui.theme.PixelFontFamily
import com.pcmonitor.app.ui.theme.PixelGreen
import com.pcmonitor.app.ui.theme.ReadableFontFamily
import com.pcmonitor.app.ui.theme.TextDarkGray
import com.pcmonitor.app.ui.theme.TextGray
import com.pcmonitor.app.ui.theme.TextWhite
import com.pcmonitor.app.ui.theme.WarningOrange

@Composable
fun AlertsScreen(
    viewModel: AlertsViewModel,
    onAlertClick: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepNavy)
    ) {
        RetroTopBar(
            title = "Alerts",
            actions = {
                if (state.alerts.isNotEmpty()) {
                    IconButton(onClick = { viewModel.clearAllAlerts() }) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear All",
                            tint = ArcadeYellow
                        )
                    }
                }
            }
        )

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Spacer(modifier = Modifier.height(12.dp))

            // Filter Pills matching reference image: All (Green), Warning (Orange outline), Critical (Red outline), Offline (Gray outline)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AlertFilter.entries.forEach { filter ->
                    val isSelected = viewModel.currentFilter == filter
                    val filterColor = when (filter) {
                        AlertFilter.ALL -> PixelGreen
                        AlertFilter.WARNING -> WarningOrange
                        AlertFilter.CRITICAL -> CriticalRed
                        AlertFilter.OFFLINE -> Color(0xFF9E9E9E)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) filterColor else Color(0xFF162640))
                            .border(
                                1.5.dp,
                                if (isSelected) filterColor else filterColor.copy(alpha = 0.5f),
                                RoundedCornerShape(6.dp)
                            )
                            .clickable { viewModel.onFilterSelected(filter) }
                            .padding(vertical = 8.dp, horizontal = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when (filter) {
                                AlertFilter.ALL -> "All"
                                AlertFilter.WARNING -> "Warning"
                                AlertFilter.CRITICAL -> "Critical"
                                AlertFilter.OFFLINE -> "Offline"
                            },
                            fontFamily = ReadableFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            maxLines = 1,
                            softWrap = false,
                            color = if (isSelected) DeepNavy else filterColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Alerts List or Empty
            if (state.filteredAlerts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.NotificationsOff,
                            contentDescription = null,
                            tint = TextDarkGray,
                            modifier = Modifier.size(52.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No alerts in this category",
                            fontFamily = ReadableFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = TextWhite
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "All systems functioning normally.",
                            fontFamily = ReadableFontFamily,
                            fontSize = 13.sp,
                            color = TextDarkGray
                        )
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(state.filteredAlerts, key = { it.id }) { alert ->
                        AlertItem(
                            alert = alert,
                            onClick = {
                                viewModel.markAsRead(alert.id)
                                onAlertClick(alert.id)
                            },
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

