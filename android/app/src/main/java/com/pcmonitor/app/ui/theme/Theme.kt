package com.pcmonitor.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import android.app.Activity
import androidx.core.view.WindowCompat

private val PCMonitorDarkColorScheme = darkColorScheme(
    primary = ArcadeYellow,
    onPrimary = DeepNavy,
    primaryContainer = ArcadeYellowDark,
    onPrimaryContainer = TextWhite,

    secondary = PixelGreen,
    onSecondary = DeepNavy,
    secondaryContainer = PixelGreenDark,
    onSecondaryContainer = TextWhite,

    tertiary = CyanAccent,
    onTertiary = DeepNavy,
    tertiaryContainer = CyanAccentLight,
    onTertiaryContainer = TextWhite,

    background = DeepNavy,
    onBackground = TextWhite,

    surface = DarkNavySurface,
    onSurface = TextWhite,
    surfaceVariant = DarkNavyLight,
    onSurfaceVariant = TextGray,

    error = CriticalRed,
    onError = TextWhite,
    errorContainer = CriticalRed.copy(alpha = 0.3f),
    onErrorContainer = CriticalRed,

    outline = PixelBorderColor,
    outlineVariant = PixelBorderLight,

    inverseSurface = CreamBackground,
    inverseOnSurface = CreamText,
    inversePrimary = ArcadeYellowDark,

    surfaceTint = Color.Transparent,
    scrim = Color.Black.copy(alpha = 0.6f)
)

private val PCMonitorLightColorScheme = lightColorScheme(
    primary = ArcadeYellowDark,
    onPrimary = Color.White,
    primaryContainer = ArcadeYellow,
    onPrimaryContainer = Color(0xFF2C2214),

    secondary = PixelGreenDark,
    onSecondary = Color.White,
    secondaryContainer = PixelGreenLight,
    onSecondaryContainer = Color(0xFF1B3D1D),

    tertiary = CyanAccent,
    onTertiary = Color.White,
    tertiaryContainer = CyanAccentLight,
    onTertiaryContainer = Color(0xFF00363D),

    background = Color(0xFFF7F3E9), // Clean retro cream background
    onBackground = Color(0xFF2C2214), // Dark retro brown text

    surface = Color(0xFFFFFFFF), // Crisp card surface
    onSurface = Color(0xFF2C2214),
    surfaceVariant = Color(0xFFEDE4D0),
    onSurfaceVariant = Color(0xFF6B5B45),

    error = CriticalRed,
    onError = Color.White,
    errorContainer = Color(0xFFFFCDD2),
    onErrorContainer = Color(0xFFB71C1C),

    outline = Color(0xFF8B7355),
    outlineVariant = Color(0xFFD4C7B0),

    inverseSurface = DeepNavy,
    inverseOnSurface = TextWhite,
    inversePrimary = ArcadeYellow,

    surfaceTint = Color.Transparent,
    scrim = Color.Black.copy(alpha = 0.3f)
)

@Composable
fun PCMonitorTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) PCMonitorDarkColorScheme else PCMonitorLightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = PCMonitorTypography,
        shapes = PCMonitorShapes,
        content = content
    )
}

