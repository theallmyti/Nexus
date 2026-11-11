package com.pcmonitor.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pcmonitor.app.ui.theme.CriticalRed
import com.pcmonitor.app.ui.theme.DarkNavyLight
import com.pcmonitor.app.ui.theme.OrangeAccent
import com.pcmonitor.app.ui.theme.PixelFontFamily
import com.pcmonitor.app.ui.theme.PixelGreen
import com.pcmonitor.app.ui.theme.TextGray
import com.pcmonitor.app.ui.theme.TextWhite

@Composable
fun RetroProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = PixelGreen,
    trackColor: Color = DarkNavyLight,
    label: String? = null,
    showPercentage: Boolean = false,
    height: Dp = 12.dp
) {
    val clampedProgress = progress.coerceIn(0f, 1f)
    val percentage = (clampedProgress * 100).toInt()

    // Color changes based on percentage
    val barColor = when {
        percentage >= 90 -> CriticalRed
        percentage >= 70 -> OrangeAccent
        else -> color
    }

    Column(modifier = modifier) {
        if (label != null || showPercentage) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (label != null) {
                    Text(
                        text = label,
                        color = TextGray,
                        fontSize = 11.sp,
                        fontFamily = PixelFontFamily,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (showPercentage) {
                    Text(
                        text = "$percentage%",
                        color = TextWhite,
                        fontSize = 11.sp,
                        fontFamily = PixelFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
        ) {
            val cornerRadius = CornerRadius(2.dp.toPx())
            val barHeight = size.height

            // Draw track
            drawRoundRect(
                color = trackColor,
                topLeft = Offset.Zero,
                size = Size(size.width, barHeight),
                cornerRadius = cornerRadius
            )

            // Draw filled portion as pixel blocks
            val filledWidth = size.width * clampedProgress
            if (filledWidth > 0f) {
                val blockWidth = 4.dp.toPx()
                val gapWidth = 1.dp.toPx()
                val totalBlockWidth = blockWidth + gapWidth
                val numBlocks = (filledWidth / totalBlockWidth).toInt()
                val blockInset = 1.dp.toPx()
                val blockHeight = barHeight - (blockInset * 2)

                // Draw the filled rounded rect background first
                drawRoundRect(
                    color = barColor.copy(alpha = 0.3f),
                    topLeft = Offset.Zero,
                    size = Size(filledWidth, barHeight),
                    cornerRadius = cornerRadius
                )

                // Draw individual pixel blocks on top
                for (i in 0 until numBlocks) {
                    val blockX = blockInset + (i * totalBlockWidth)
                    if (blockX + blockWidth <= filledWidth) {
                        drawRect(
                            color = barColor,
                            topLeft = Offset(blockX, blockInset),
                            size = Size(blockWidth, blockHeight)
                        )
                    }
                }

                // Draw any remaining partial block
                val remainingStart = blockInset + (numBlocks * totalBlockWidth)
                val remainingWidth = filledWidth - remainingStart
                if (remainingWidth > gapWidth) {
                    drawRect(
                        color = barColor,
                        topLeft = Offset(remainingStart, blockInset),
                        size = Size((remainingWidth - gapWidth).coerceAtLeast(0f), blockHeight)
                    )
                }
            }
        }
    }
}
