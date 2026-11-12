package com.pcmonitor.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pcmonitor.app.ui.theme.ArcadeYellow
import com.pcmonitor.app.ui.theme.DeepNavy
import com.pcmonitor.app.ui.theme.PixelBorderColor
import com.pcmonitor.app.ui.theme.PixelFontFamily
import com.pcmonitor.app.ui.theme.TextWhite

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RetroTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBackClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val isDark = MaterialTheme.colorScheme.background == DeepNavy
    val topBg = MaterialTheme.colorScheme.background
    val topTitleColor = if (isDark) ArcadeYellow else MaterialTheme.colorScheme.onBackground
    val topBorderColor = if (isDark) PixelBorderColor else Color(0xFFE2E8F0)

    TopAppBar(
        title = {
            Text(
                text = title,
                fontFamily = PixelFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = topTitleColor
            )
        },
        modifier = modifier
            .statusBarsPadding()
            .drawBehind {
                // Bottom border
                drawLine(
                    color = topBorderColor,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.5.dp.toPx()
                )
            },
        windowInsets = WindowInsets(0, 0, 0, 0),
        navigationIcon = {
            if (onBackClick != null) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = topTitleColor
                    )
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = topBg,
            titleContentColor = topTitleColor,
            navigationIconContentColor = topTitleColor,
            actionIconContentColor = MaterialTheme.colorScheme.onBackground
        )
    )
}
