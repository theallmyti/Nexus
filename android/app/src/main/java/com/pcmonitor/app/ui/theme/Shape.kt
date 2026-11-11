package com.pcmonitor.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val PCMonitorShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp)
)

// Pixel-perfect shape variants
val PixelShape = RoundedCornerShape(2.dp)
val RetroCardShape = RoundedCornerShape(8.dp)
val RetroButtonShape = RoundedCornerShape(6.dp)
val RetroChipShape = RoundedCornerShape(4.dp)

