package com.pcmonitor.app.ui.screens.onboarding

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import com.pcmonitor.app.ui.components.PixelAlertBell
import com.pcmonitor.app.ui.components.AppLogo
import com.pcmonitor.app.ui.components.PixelCharacter
import com.pcmonitor.app.ui.components.PixelComputer
import com.pcmonitor.app.ui.components.PixelHeart
import com.pcmonitor.app.ui.components.PixelMonitor
import com.pcmonitor.app.ui.components.PixelTemperature
import com.pcmonitor.app.ui.components.RetroButton
import com.pcmonitor.app.ui.components.RetroButtonVariant
import com.pcmonitor.app.ui.theme.ArcadeYellow
import com.pcmonitor.app.ui.theme.CreamBackground
import com.pcmonitor.app.ui.theme.CreamText
import com.pcmonitor.app.ui.theme.DeepNavy
import com.pcmonitor.app.ui.theme.PixelFontFamily
import com.pcmonitor.app.ui.theme.PixelGreen
import com.pcmonitor.app.ui.theme.ReadableFontFamily
import com.pcmonitor.app.ui.theme.TextDarkGray
import com.pcmonitor.app.ui.theme.TextGray
import com.pcmonitor.app.ui.theme.TextWhite

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onNavigateToLogin: () -> Unit
) {
    val page = viewModel.pages[viewModel.currentPage]
    val isLastPage = viewModel.currentPage == viewModel.pages.size - 1
    val isDark = MaterialTheme.colorScheme.background.red < 0.5f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Warm beige rounded card container with brown border
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFFDF6E3))
                    .border(2.dp, Color(0xFF8B7355), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                when (page.pageType) {
                    OnboardingPageType.MONITOR_ALL -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            PixelHeart(size = 28.dp, modifier = Modifier.padding(bottom = 12.dp))
                            AppLogo(size = 130.dp)
                        }
                    }
                    OnboardingPageType.TRACK_PERFORMANCE -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            PixelTemperature(size = 90.dp)
                            Spacer(modifier = Modifier.height(12.dp))
                            PixelMonitor(size = 80.dp)
                        }
                    }
                    OnboardingPageType.STAY_ALERTED -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            PixelAlertBell(size = 90.dp)
                            Spacer(modifier = Modifier.height(12.dp))
                            PixelHeart(size = 32.dp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            // Title
            Text(
                text = page.title,
                fontFamily = PixelFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Description
            Text(
                text = page.description,
                fontFamily = ReadableFontFamily,
                fontSize = 14.sp,
                color = if (isDark) TextGray else Color(0xFF6B5B45),
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.weight(1f))

            // Dot indicators
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                viewModel.pages.indices.forEach { index ->
                    val isActive = index == viewModel.currentPage
                    Box(
                        modifier = Modifier
                            .size(if (isActive) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .background(if (isActive) ArcadeYellow else TextDarkGray)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Next / Get Started button
            RetroButton(
                text = if (isLastPage) "Get Started" else "Next",
                onClick = { viewModel.onNextClick(onNavigateToLogin) },
                modifier = Modifier.fillMaxWidth(),
                variant = RetroButtonVariant.Secondary // Pixel green button
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Skip link
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = if (isLastPage) "" else "Skip",
                    fontFamily = ReadableFontFamily,
                    fontSize = 14.sp,
                    color = PixelGreen,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clickable { viewModel.onSkipClick(onNavigateToLogin) }
                        .padding(8.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

