package com.pcmonitor.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import android.app.Activity
import androidx.core.view.WindowCompat

private val PCMonitorColorScheme = darkColorScheme(
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

@Composable
fun PCMonitorTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = PCMonitorColorScheme,
        typography = PCMonitorTypography,
        shapes = PCMonitorShapes,
        content = content
    )
}

