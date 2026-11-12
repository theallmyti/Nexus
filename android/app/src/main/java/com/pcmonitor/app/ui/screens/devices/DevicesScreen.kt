package com.pcmonitor.app.ui.screens.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
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
import com.pcmonitor.app.ui.components.DeviceCard
import com.pcmonitor.app.ui.components.PixelComputer
import com.pcmonitor.app.ui.components.RetroTopBar
import com.pcmonitor.app.ui.theme.ArcadeYellow
import com.pcmonitor.app.ui.theme.ArcadeYellowDark
import com.pcmonitor.app.ui.theme.DarkNavySurface
import com.pcmonitor.app.ui.theme.DeepNavy
import com.pcmonitor.app.ui.theme.PixelBorderColor
import com.pcmonitor.app.ui.theme.PixelFontFamily
import com.pcmonitor.app.ui.theme.PixelGreen
import com.pcmonitor.app.ui.theme.ReadableFontFamily
import com.pcmonitor.app.ui.theme.TextDarkGray
import com.pcmonitor.app.ui.theme.TextGray
import com.pcmonitor.app.ui.theme.TextWhite

@Composable
fun DevicesScreen(
    viewModel: DevicesViewModel,
    onDeviceClick: (String) -> Unit,
    onAddDeviceClick: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    val isDark = MaterialTheme.colorScheme.background == DeepNavy

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddDeviceClick,
                containerColor = if (isDark) ArcadeYellow else MaterialTheme.colorScheme.primary,
                contentColor = if (isDark) DeepNavy else Color.White,
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add Device")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Add Device",
                        fontFamily = PixelFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            RetroTopBar(
                title = "Devices (${state.devices.size})"
            )

            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Spacer(modifier = Modifier.height(12.dp))

                // Search Bar
                OutlinedTextField(
                    value = viewModel.searchQuery,
                    onValueChange = { viewModel.onSearchQueryChanged(it) },
                    placeholder = {
                        Text(
                            text = "Search by name, OS or type...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = if (isDark) ArcadeYellow else MaterialTheme.colorScheme.primary
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = if (isDark) ArcadeYellow else MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Leveled, single-line filter pills with high contrast
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    DeviceFilterStatus.entries.forEach { status ->
                        val isSelected = viewModel.filterStatus == status
                        val chipBg = if (isSelected) {
                            if (isDark) ArcadeYellow else MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                        val chipBorder = if (isSelected) {
                            if (isDark) ArcadeYellowDark else MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        }
                        val chipTextColor = if (isSelected) {
                            if (isDark) DeepNavy else Color.White
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(chipBg)
                                .border(1.5.dp, chipBorder, RoundedCornerShape(6.dp))
                                .clickable { viewModel.onFilterStatusChanged(status) }
                                .padding(vertical = 10.dp, horizontal = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = when (status) {
                                    DeviceFilterStatus.ALL -> "All (${state.devices.size})"
                                    DeviceFilterStatus.ONLINE -> "Online (${state.devices.count { it.isOnline }})"
                                    DeviceFilterStatus.OFFLINE -> "Offline (${state.devices.count { !it.isOnline }})"
                                },
                                fontFamily = ReadableFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                maxLines = 1,
                                softWrap = false,
                                color = chipTextColor
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Device List or Empty State
                if (state.filteredDevices.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        ) {
                            if (state.devices.isEmpty() && state.searchQuery.isBlank()) {
                                PixelComputer(size = 72.dp)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "NO DEVICES PAIRED",
                                    fontFamily = PixelFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (isDark) ArcadeYellow else MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "1. Double-click Start-PC-Monitor on your PC\n2. Tap '+ Add Device' below to scan the code",
                                    fontFamily = ReadableFontFamily,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    lineHeight = 18.sp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Devices,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(56.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "No devices found",
                                    fontFamily = ReadableFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Try adjusting your search or filters.",
                                    fontFamily = ReadableFontFamily,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(state.filteredDevices, key = { it.id }) { device ->
                            DeviceCard(
                                device = device,
                                onClick = { onDeviceClick(device.id) },
                                useFahrenheit = state.useFahrenheit,
                                modifier = Modifier.padding(bottom = 10.dp)
                            )
                        }
                        item {
                            Spacer(modifier = Modifier.height(72.dp)) // Space for FAB
                        }
                    }
                }
            }
        }
    }
}

