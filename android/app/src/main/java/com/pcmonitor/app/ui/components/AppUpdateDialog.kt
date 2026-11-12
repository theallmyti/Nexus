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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import com.pcmonitor.app.ui.theme.ArcadeYellow
import com.pcmonitor.app.ui.theme.DeepNavy
import com.pcmonitor.app.ui.theme.PixelFontFamily
import com.pcmonitor.app.ui.theme.PixelGreen
import com.pcmonitor.app.ui.theme.ReadableFontFamily
import com.pcmonitor.app.ui.updater.AppUpdateUiState

@Composable
fun AppUpdateDialog(
    uiState: AppUpdateUiState,
    onUpdateClick: (com.pcmonitor.app.data.model.AppUpdateInfo) -> Unit,
    onInstallClick: (java.io.File) -> Unit,
    onDismiss: () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background == DeepNavy
    val primaryColor = if (isDark) ArcadeYellow else MaterialTheme.colorScheme.primary

    when (uiState) {
        is AppUpdateUiState.Idle -> {
            // Nothing to show
        }

        is AppUpdateUiState.Checking -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = {
                    Text(
                        text = "Checking For Updates",
                        fontFamily = PixelFontFamily,
                        color = primaryColor,
                        fontSize = 14.sp
                    )
                },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        CircularProgressIndicator(
                            color = primaryColor,
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 3.dp
                        )
                        Text(
                            text = "Connecting to server...",
                            fontFamily = ReadableFontFamily,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                confirmButton = {},
                dismissButton = {
                    Text(
                        text = "Cancel",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = PixelFontFamily,
                        modifier = Modifier
                            .clickable(onClick = onDismiss)
                            .padding(8.dp)
                    )
                },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp)
            )
        }

        is AppUpdateUiState.UpToDate -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = {
                    Text(
                        text = "Up To Date",
                        fontFamily = PixelFontFamily,
                        color = primaryColor,
                        fontSize = 15.sp
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "You are using the latest version of Nexus (v${uiState.versionName}).",
                            fontFamily = ReadableFontFamily,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 20.sp
                        )
                    }
                },
                confirmButton = {
                    Text(
                        text = "OK",
                        color = primaryColor,
                        fontFamily = PixelFontFamily,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable(onClick = onDismiss)
                            .padding(8.dp)
                    )
                },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp)
            )
        }

        is AppUpdateUiState.UpdateAvailable -> {
            val info = uiState.info
            AlertDialog(
                onDismissRequest = {
                    if (!info.isMandatory) onDismiss()
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = null,
                            tint = primaryColor,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Update Available",
                            fontFamily = PixelFontFamily,
                            color = primaryColor,
                            fontSize = 15.sp
                        )
                    }
                },
                text = {
                    Column {
                        Text(
                            text = "A new version of Nexus is ready to install!",
                            fontFamily = ReadableFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "New Version: v${info.latestVersionName} (Build ${info.latestVersionCode})",
                            fontFamily = PixelFontFamily,
                            fontSize = 11.sp,
                            color = PixelGreen
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Release Notes Box
                        if (info.releaseNotes.isNotBlank()) {
                            Text(
                                text = "What's New:",
                                fontFamily = ReadableFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                    .padding(10.dp)
                            ) {
                                Text(
                                    text = info.releaseNotes,
                                    fontFamily = ReadableFontFamily,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(primaryColor)
                            .clickable { onUpdateClick(info) }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "UPDATE NOW",
                            fontFamily = PixelFontFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) DeepNavy else Color.White
                        )
                    }
                },
                dismissButton = {
                    if (!info.isMandatory) {
                        Text(
                            text = "Later",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = PixelFontFamily,
                            modifier = Modifier
                                .clickable(onClick = onDismiss)
                                .padding(8.dp)
                        )
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp)
            )
        }

        is AppUpdateUiState.Downloading -> {
            val progress = uiState.progress
            val pct = (progress * 100).toInt()
            AlertDialog(
                onDismissRequest = {},
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            tint = primaryColor,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Downloading Update",
                            fontFamily = PixelFontFamily,
                            color = primaryColor,
                            fontSize = 15.sp
                        )
                    }
                },
                text = {
                    Column {
                        Text(
                            text = "Downloading Nexus v${uiState.info.latestVersionName}...",
                            fontFamily = ReadableFontFamily,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        RetroProgressBar(
                            progress = progress,
                            color = PixelGreen,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                            height = 12.dp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = if (progress > 0f) "$pct% complete" else "Connecting...",
                            fontFamily = PixelFontFamily,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {},
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp)
            )
        }

        is AppUpdateUiState.ReadyToInstall -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = {
                    Text(
                        text = "Ready to Install",
                        fontFamily = PixelFontFamily,
                        color = primaryColor,
                        fontSize = 15.sp
                    )
                },
                text = {
                    Text(
                        text = "Download complete! Tap Install to update Nexus to v${uiState.info.latestVersionName}.",
                        fontFamily = ReadableFontFamily,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 20.sp
                    )
                },
                confirmButton = {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(PixelGreen)
                            .clickable { onInstallClick(uiState.apkFile) }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "INSTALL",
                            fontFamily = PixelFontFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                },
                dismissButton = {
                    Text(
                        text = "Close",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = PixelFontFamily,
                        modifier = Modifier
                            .clickable(onClick = onDismiss)
                            .padding(8.dp)
                    )
                },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp)
            )
        }

        is AppUpdateUiState.Error -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = {
                    Text(
                        text = "Update Check",
                        fontFamily = PixelFontFamily,
                        color = primaryColor,
                        fontSize = 15.sp
                    )
                },
                text = {
                    Text(
                        text = uiState.message,
                        fontFamily = ReadableFontFamily,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                confirmButton = {
                    Text(
                        text = "OK",
                        color = primaryColor,
                        fontFamily = PixelFontFamily,
                        modifier = Modifier
                            .clickable(onClick = onDismiss)
                            .padding(8.dp)
                    )
                },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp)
            )
        }
    }
}
