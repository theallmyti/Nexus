package com.pcmonitor.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pcmonitor.app.ui.theme.ArcadeYellow
import com.pcmonitor.app.ui.theme.ArcadeYellowDark
import com.pcmonitor.app.ui.theme.CriticalRed
import com.pcmonitor.app.ui.theme.CyanAccent
import com.pcmonitor.app.ui.theme.DeepNavy
import com.pcmonitor.app.ui.theme.OrangeAccent
import com.pcmonitor.app.ui.theme.PixelFontFamily
import com.pcmonitor.app.ui.theme.PixelGreen
import com.pcmonitor.app.ui.theme.PixelGreenBright
import com.pcmonitor.app.ui.theme.PixelGreenDark
import com.pcmonitor.app.ui.theme.TextWhite

// ─────────────────────────────────────────────────────────
// 1. Pixel Computer (Blue monitor + green screen + stand)
// ─────────────────────────────────────────────────────────
@Composable
fun PixelComputer(modifier: Modifier = Modifier, size: Dp = 48.dp) {
    Canvas(modifier = modifier.size(size)) {
        val px = size.toPx()
        val unit = px / 16f

        // Outer Monitor frame (Arcade Yellow / Gold)
        drawRect(
            color = ArcadeYellow,
            topLeft = Offset(unit * 2, unit * 1),
            size = Size(unit * 12, unit * 9)
        )
        // Inner Bezel (Deep Navy / Dark Blue)
        drawRect(
            color = DeepNavy,
            topLeft = Offset(unit * 3, unit * 2),
            size = Size(unit * 10, unit * 7)
        )
        // CRT Screen (Pixel Green)
        drawRect(
            color = PixelGreen,
            topLeft = Offset(unit * 4, unit * 3),
            size = Size(unit * 8, unit * 5)
        )
        // Screen detail (bright pixel cross / circuit)
        drawRect(
            color = PixelGreenBright,
            topLeft = Offset(unit * 5, unit * 4),
            size = Size(unit * 3, unit * 1)
        )
        drawRect(
            color = PixelGreenBright,
            topLeft = Offset(unit * 6, unit * 5),
            size = Size(unit * 4, unit * 1)
        )
        // Monitor Stand Neck
        drawRect(
            color = ArcadeYellowDark,
            topLeft = Offset(unit * 7, unit * 10),
            size = Size(unit * 2, unit * 2)
        )
        // Monitor Base
        drawRect(
            color = ArcadeYellow,
            topLeft = Offset(unit * 5, unit * 12),
            size = Size(unit * 6, unit * 2)
        )
    }
}

// ─────────────────────────────────────────────────────────
// 2. Pixel Character (Adventurer with yellow hat & backpack)
// ─────────────────────────────────────────────────────────
@Composable
fun PixelCharacter(modifier: Modifier = Modifier, size: Dp = 60.dp) {
    Canvas(modifier = modifier.size(size)) {
        val px = size.toPx()
        val u = px / 16f

        // Straw Hat Crown (Yellow)
        drawRect(color = Color(0xFFF9A825), topLeft = Offset(u * 5, u * 1), size = Size(u * 6, u * 3))
        // Hat Brim
        drawRect(color = Color(0xFFFBC02D), topLeft = Offset(u * 3, u * 4), size = Size(u * 10, u * 1.5f))
        // Hat Ribbon (Red)
        drawRect(color = Color(0xFFC62828), topLeft = Offset(u * 5, u * 3.5f), size = Size(u * 6, u * 0.8f))

        // Face (Peach skin)
        drawRect(color = Color(0xFFFFCC80), topLeft = Offset(u * 5, u * 5.5f), size = Size(u * 6, u * 3.5f))
        // Eye (facing right)
        drawRect(color = Color(0xFF212121), topLeft = Offset(u * 8, u * 6.5f), size = Size(u * 1.5f, u * 1.5f))

        // Blue Coat / Tunic
        drawRect(color = Color(0xFF1E88E5), topLeft = Offset(u * 4, u * 9), size = Size(u * 7, u * 4))

        // Brown Backpack (on left side since facing right)
        drawRect(color = Color(0xFF6D4C41), topLeft = Offset(u * 2, u * 8.5f), size = Size(u * 2.5f, u * 4.5f))
        drawRect(color = Color(0xFF8D6E63), topLeft = Offset(u * 2.5f, u * 9.5f), size = Size(u * 1.5f, u * 2.5f))

        // Legs / Pants (Brown / Khaki)
        drawRect(color = Color(0xFF4E342E), topLeft = Offset(u * 5, u * 13), size = Size(u * 2.5f, u * 3))
        drawRect(color = Color(0xFF4E342E), topLeft = Offset(u * 8, u * 13), size = Size(u * 2.5f, u * 3))

        // Shoes
        drawRect(color = Color(0xFF212121), topLeft = Offset(u * 5, u * 15.5f), size = Size(u * 3f, u * 0.8f))
        drawRect(color = Color(0xFF212121), topLeft = Offset(u * 8, u * 15.5f), size = Size(u * 3.5f, u * 0.8f))
    }
}

// ─────────────────────────────────────────────────────────
// 3. Pixel Cat (Orange companion sitting)
// ─────────────────────────────────────────────────────────
@Composable
fun PixelCat(modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Canvas(modifier = modifier.size(size)) {
        val px = size.toPx()
        val u = px / 12f

        val orange = Color(0xFFFB8C00)
        val darkOrange = Color(0xFFE65100)
        val white = Color(0xFFFFFFFF)

        // Ears
        drawRect(color = darkOrange, topLeft = Offset(u * 4, u * 2), size = Size(u * 1.5f, u * 2))
        drawRect(color = darkOrange, topLeft = Offset(u * 7.5f, u * 2), size = Size(u * 1.5f, u * 2))

        // Head
        drawRect(color = orange, topLeft = Offset(u * 3.5f, u * 3.5f), size = Size(u * 6, u * 4))

        // Eyes (looking right)
        drawRect(color = Color(0xFF212121), topLeft = Offset(u * 7, u * 4.5f), size = Size(u * 1.2f, u * 1.2f))

        // Body
        drawRect(color = orange, topLeft = Offset(u * 3, u * 7), size = Size(u * 6, u * 4))
        // White chest
        drawRect(color = white, topLeft = Offset(u * 6.5f, u * 7.5f), size = Size(u * 2, u * 3))

        // Tail curled up
        drawRect(color = darkOrange, topLeft = Offset(u * 1.5f, u * 6.5f), size = Size(u * 1.5f, u * 4))
        drawRect(color = darkOrange, topLeft = Offset(u * 2f, u * 5.5f), size = Size(u * 1.5f, u * 1.5f))
    }
}

// ─────────────────────────────────────────────────────────
// 4. Pixel Mountain Scene (Full Daytime Landscape from Reference)
// ─────────────────────────────────────────────────────────
@Composable
fun PixelMountainScene(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // 1. Sky Gradient (Daytime Cyan/Blue)
        drawRect(
            color = Color(0xFF81D4FA),
            topLeft = Offset(0f, 0f),
            size = Size(w, h * 0.45f)
        )

        // Pixel Clouds
        val cloudColor = Color(0xFFFFFFFF)
        // Cloud 1
        drawRect(cloudColor, Offset(w * 0.1f, h * 0.08f), Size(w * 0.25f, h * 0.03f))
        drawRect(cloudColor, Offset(w * 0.14f, h * 0.06f), Size(w * 0.17f, h * 0.04f))
        // Cloud 2
        drawRect(cloudColor, Offset(w * 0.65f, h * 0.05f), Size(w * 0.28f, h * 0.035f))
        drawRect(cloudColor, Offset(w * 0.70f, h * 0.035f), Size(w * 0.18f, h * 0.04f))

        // 2. Far Majestic Mountains (Blue with snowy peaks)
        val mountainPath = Path().apply {
            moveTo(0f, h * 0.45f)
            lineTo(w * 0.15f, h * 0.28f)
            lineTo(w * 0.35f, h * 0.42f)
            lineTo(w * 0.55f, h * 0.22f) // highest peak
            lineTo(w * 0.75f, h * 0.38f)
            lineTo(w * 0.90f, h * 0.26f)
            lineTo(w, h * 0.40f)
            lineTo(w, h * 0.55f)
            lineTo(0f, h * 0.55f)
            close()
        }
        drawPath(mountainPath, Color(0xFF1E88E5))

        // Snowcaps on peaks
        drawRect(Color(0xFFFFFFFF), Offset(w * 0.52f, h * 0.22f), Size(w * 0.06f, h * 0.035f))
        drawRect(Color(0xFFE3F2FD), Offset(w * 0.13f, h * 0.28f), Size(w * 0.04f, h * 0.025f))
        drawRect(Color(0xFFE3F2FD), Offset(w * 0.88f, h * 0.26f), Size(w * 0.04f, h * 0.025f))

        // 3. Pine Forest Midground
        drawRect(
            color = Color(0xFF2E7D32),
            topLeft = Offset(0f, h * 0.42f),
            size = Size(w, h * 0.16f)
        )
        // Pixel tree spires
        for (i in 0..20) {
            val tx = i * (w / 20f)
            drawRect(
                color = Color(0xFF1B5E20),
                topLeft = Offset(tx, h * 0.40f),
                size = Size(w / 35f, h * 0.04f)
            )
        }

        // 4. Blue Lake with Reflection
        drawRect(
            color = Color(0xFF0288D1),
            topLeft = Offset(0f, h * 0.52f),
            size = Size(w, h * 0.15f)
        )
        drawRect(
            color = Color(0xFF29B6F6),
            topLeft = Offset(w * 0.1f, h * 0.54f),
            size = Size(w * 0.8f, h * 0.04f)
        )

        // Cabin on Lake Shore
        drawRect(Color(0xFF5D4037), Offset(w * 0.72f, h * 0.50f), Size(w * 0.18f, h * 0.05f))
        drawRect(Color(0xFF8D6E63), Offset(w * 0.70f, h * 0.47f), Size(w * 0.22f, h * 0.035f)) // Roof
        drawRect(Color(0xFFFFF176), Offset(w * 0.78f, h * 0.51f), Size(w * 0.04f, h * 0.025f)) // Light window

        // 5. Lush Meadow Foreground (Grass + Dirt Path)
        drawRect(
            color = Color(0xFF4CAF50),
            topLeft = Offset(0f, h * 0.65f),
            size = Size(w, h * 0.35f)
        )
        // Grass texture layers
        drawRect(
            color = Color(0xFF388E3C),
            topLeft = Offset(0f, h * 0.65f),
            size = Size(w, h * 0.03f)
        )

        // Dirt Path leading towards the right/lake
        val dirtColor = Color(0xFF8D6E63)
        drawRect(dirtColor, Offset(0f, h * 0.80f), Size(w * 0.55f, h * 0.20f))
        drawRect(Color(0xFFA1887F), Offset(0f, h * 0.82f), Size(w * 0.50f, h * 0.14f))

        // Colorful Pixel Flowers on Grass (Red, Yellow, White dots)
        val flowerColors = listOf(Color(0xFFE53935), Color(0xFFFFEB3B), Color(0xFFFFFFFF), Color(0xFFAB47BC))
        for (i in 0..12) {
            val fx = w * 0.05f + (i * w * 0.075f)
            val fy = h * 0.72f + ((i % 4) * h * 0.05f)
            drawRect(
                color = flowerColors[i % flowerColors.size],
                topLeft = Offset(fx, fy),
                size = Size(4.dp.toPx(), 4.dp.toPx())
            )
        }
    }
}

// ─────────────────────────────────────────────────────────
// 5. Wooden Sign (Large hanging board matching reference)
// ─────────────────────────────────────────────────────────
@Composable
fun WoodenSign(modifier: Modifier = Modifier, text: String = "PC MONITOR") {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFD4A359))
            .border(3.dp, Color(0xFF6B4423), RoundedCornerShape(8.dp))
            .padding(horizontal = 24.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Monitor icon at top of sign
            PixelComputer(size = 36.dp)

            Spacer(modifier = Modifier.height(6.dp))

            // Carved Text
            Text(
                text = text,
                fontFamily = PixelFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = Color(0xFF3E2723),
                letterSpacing = 0.5.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

// Additional pixel shapes
@Composable
fun PixelHeart(modifier: Modifier = Modifier, size: Dp = 20.dp, color: Color = CriticalRed) {
    Canvas(modifier = modifier.size(size)) {
        val px = size.toPx()
        val u = px / 10f
        drawRect(color, Offset(u * 1, u * 2), Size(u * 3, u * 3))
        drawRect(color, Offset(u * 6, u * 2), Size(u * 3, u * 3))
        drawRect(color, Offset(u * 2, u * 1), Size(u * 2, u * 1))
        drawRect(color, Offset(u * 6, u * 1), Size(u * 2, u * 1))
        drawRect(color, Offset(u * 1, u * 4), Size(u * 8, u * 2))
        drawRect(color, Offset(u * 2, u * 6), Size(u * 6, u * 2))
        drawRect(color, Offset(u * 4, u * 8), Size(u * 2, u * 2))
    }
}

@Composable
fun PixelMonitor(modifier: Modifier = Modifier, size: Dp = 80.dp) {
    PixelComputer(modifier = modifier, size = size)
}

@Composable
fun PixelAlertBell(modifier: Modifier = Modifier, size: Dp = 80.dp) {
    Canvas(modifier = modifier.size(size)) {
        val px = size.toPx()
        val u = px / 16f
        drawRect(ArcadeYellow, Offset(u * 7, u * 2), Size(u * 2, u * 3))
        drawRect(ArcadeYellow, Offset(u * 5, u * 5), Size(u * 6, u * 6))
        drawRect(ArcadeYellow, Offset(u * 3, u * 11), Size(u * 10, u * 2))
        drawRect(Color(0xFFE65100), Offset(u * 7, u * 13), Size(u * 2, u * 2))
    }
}

@Composable
fun PixelTemperature(modifier: Modifier = Modifier, size: Dp = 80.dp) {
    Canvas(modifier = modifier.size(size)) {
        val px = size.toPx()
        val u = px / 16f
        drawRect(Color(0xFF90A4AE), Offset(u * 7, u * 2), Size(u * 2, u * 10))
        drawRect(CriticalRed, Offset(u * 5.5f, u * 11), Size(u * 5, u * 4))
        drawRect(CriticalRed, Offset(u * 7, u * 6), Size(u * 2, u * 6))
    }
}
