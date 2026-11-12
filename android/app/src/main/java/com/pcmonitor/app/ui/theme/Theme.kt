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
    primary = Color(0xFFD97706), // Rich amber (high contrast on white)
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFEF3C7),
    onPrimaryContainer = Color(0xFF78350F),

    secondary = PixelGreenDark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCFCE7),
    onSecondaryContainer = Color(0xFF14532D),

    tertiary = Color(0xFF0284C7),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE0F2FE),
    onTertiaryContainer = Color(0xFF0369A1),

    background = Color(0xFFF8FAFC), // Modern crisp clean canvas (Slate-50)
    onBackground = Color(0xFF0F172A), // Dark slate-900 (crisp high contrast)

    surface = Color(0xFFFFFFFF), // Crisp pure white cards & sheets
    onSurface = Color(0xFF0F172A), // Slate-900
    surfaceVariant = Color(0xFFF1F5F9), // Slate-100 for secondary containers / inputs
    onSurfaceVariant = Color(0xFF475569), // Slate-600

    error = CriticalRed,
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF991B1B),

    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),

    inverseSurface = DeepNavy,
    inverseOnSurface = TextWhite,
    inversePrimary = ArcadeYellow,

    surfaceTint = Color.Transparent,
    scrim = Color.Black.copy(alpha = 0.4f)
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

