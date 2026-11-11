package com.pcmonitor.app.ui.screens.pairing

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pcmonitor.app.ui.components.PixelCharacter
import com.pcmonitor.app.ui.components.PixelComputer
import com.pcmonitor.app.ui.components.RetroTopBar
import com.pcmonitor.app.ui.theme.ArcadeYellow
import com.pcmonitor.app.ui.theme.CardBackground
import com.pcmonitor.app.ui.theme.DeepNavy
import com.pcmonitor.app.ui.theme.PixelBorderColor
import com.pcmonitor.app.ui.theme.PixelFontFamily
import com.pcmonitor.app.ui.theme.PixelGreen
import com.pcmonitor.app.ui.theme.ReadableFontFamily
import com.pcmonitor.app.ui.theme.TextDarkGray
import com.pcmonitor.app.ui.theme.TextGray
import com.pcmonitor.app.ui.theme.TextWhite

@Composable
fun AddDeviceScreen(
    onBack: () -> Unit,
    onScanQrClick: () -> Unit,
    onEnterCodeClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepNavy)
    ) {
        RetroTopBar(
            title = "Add New Device",
            onBackClick = onBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // Cream-colored card with pixel art illustration
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF9F6EE))
                    .border(2.dp, Color(0xFF8B7355), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.Center
                ) {
                    PixelComputer(size = 96.dp)
                    PixelCharacter(size = 54.dp, modifier = Modifier.padding(start = 12.dp))
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Title
            Text(
                text = "Pair a new device\nto your account",
                fontFamily = PixelFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = TextWhite,
                textAlign = TextAlign.Center,
                lineHeight = 26.sp
            )

            Spacer(modifier = Modifier.height(36.dp))

            // Option 1: Scan QR Code
            PairingOptionCard(
                icon = Icons.Default.QrCodeScanner,
                title = "Scan QR Code",
                description = "Scan the QR code displayed on your PC screen.",
                onClick = onScanQrClick
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Option 2: Enter Pairing Code
            PairingOptionCard(
                icon = Icons.Default.Pin,
                title = "Enter Pairing Code",
                description = "Type the 6-character code from your PC screen.",
                onClick = onEnterCodeClick
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Easy 3-step setup guide for non-technical users
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF162640))
                    .border(1.dp, Color(0xFF2A3F5F), RoundedCornerShape(10.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Text(
                        text = "HOW TO GET THE CODE:",
                        fontFamily = PixelFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = ArcadeYellow
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "1. On your Windows PC, double-click Start-PC-Monitor.bat\n2. The QR code and 6-digit code will appear\n3. Tap 'Scan QR Code' above and point your phone at the screen!",
                        fontFamily = ReadableFontFamily,
                        fontSize = 13.sp,
                        color = TextWhite,
                        lineHeight = 19.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun PairingOptionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFFDF6E3))
            .border(1.5.dp, Color(0xFF8B7355), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0F1B2E))
                    .border(1.5.dp, PixelGreen, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = PixelGreen,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column {
                Text(
                    text = title,
                    fontFamily = ReadableFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color(0xFF0F172A)
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = description,
                    fontFamily = ReadableFontFamily,
                    fontSize = 12.sp,
                    color = Color(0xFF64748B)
                )
            }
        }
    }
}

