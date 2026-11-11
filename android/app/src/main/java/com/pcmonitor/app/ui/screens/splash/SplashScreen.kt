package com.pcmonitor.app.ui.screens.splash

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pcmonitor.app.ui.components.PixelCat
import com.pcmonitor.app.ui.components.AppLogo
import com.pcmonitor.app.ui.components.PixelCharacter
import com.pcmonitor.app.ui.components.PixelMountainScene
import com.pcmonitor.app.ui.components.WoodenSign
import com.pcmonitor.app.ui.theme.ArcadeYellow
import com.pcmonitor.app.ui.theme.DeepNavy
import com.pcmonitor.app.ui.theme.PixelFontFamily
import com.pcmonitor.app.ui.theme.TextWhite

@Composable
fun SplashScreen(
    viewModel: SplashViewModel,
    onNavigateToOnboarding: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToHome: () -> Unit
) {
    val destination by viewModel.destination.collectAsState()

    LaunchedEffect(destination) {
        when (destination) {
            is SplashDestination.Onboarding -> onNavigateToOnboarding()
            is SplashDestination.Login -> onNavigateToLogin()
            is SplashDestination.Home -> onNavigateToHome()
            is SplashDestination.Loading -> { /* Keep waiting */ }
        }
    }

    val transition = rememberInfiniteTransition(label = "pulse")
    val alphaAnim by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        // Fullscreen Pixel Daytime Landscape (Mountains, forest, lake, meadow)
        PixelMountainScene(
            modifier = Modifier.fillMaxSize()
        )

        // Upper Area: Wooden Sign & Tagline
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(44.dp))

            // Official App Logo
            AppLogo(size = 92.dp)

            Spacer(modifier = Modifier.height(14.dp))

            // Wooden Sign with PC MONITOR
            WoodenSign(
                text = "NEXUS"
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Subtitle Tagline in dark navy pixel text
            Text(
                text = "Your devices.\nAlways in sight.",
                fontFamily = PixelFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = Color(0xFF0F1B2E),
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.weight(1f))

            // Lower Meadow: Adventurer & Companion Cat standing on the path
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 70.dp, start = 28.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Start
            ) {
                PixelCharacter(size = 64.dp)
                PixelCat(size = 40.dp, modifier = Modifier.offset(x = 10.dp, y = 2.dp))
            }

            // Pulsing loading dots
            Row(
                modifier = Modifier
                    .padding(bottom = 32.dp)
                    .alpha(alphaAnim),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(ArcadeYellow)
                    )
                }
            }
        }
    }
}
