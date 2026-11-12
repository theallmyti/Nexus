package com.pcmonitor.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pcmonitor.app.ui.theme.ArcadeYellow
import com.pcmonitor.app.ui.theme.PixelBorderColor
import com.pcmonitor.app.ui.theme.PixelFontFamily
import com.pcmonitor.app.ui.theme.PixelGreen
import com.pcmonitor.app.ui.theme.TextDarkGray
import com.pcmonitor.app.ui.theme.TextGray

@Composable
fun RetroLineChart(
    dataPoints: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color = PixelGreen,
    fillColor: Color = PixelGreen.copy(alpha = 0.25f),
    gridColor: Color = PixelBorderColor,
    labelColor: Color = TextGray,
    labels: List<String> = emptyList(),
    minValue: Float = 0f,
    maxValue: Float = 100f,
    showGrid: Boolean = true,
    showDots: Boolean = true
) {
    if (dataPoints.isEmpty()) {
        Box(modifier = modifier)
        return
    }

    Column(modifier = modifier) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height

                // Draw horizontal grid lines (4 lines)
                if (showGrid) {
                    val gridSteps = 4
                    for (i in 0..gridSteps) {
                        val y = height * (i.toFloat() / gridSteps)
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(width, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                }

                val range = if (maxValue - minValue > 0f) maxValue - minValue else 1f
                val stepX = if (dataPoints.size > 1) width / (dataPoints.size - 1) else width

                // Build stepped / pixelated line path
                val linePath = Path()
                val fillPath = Path()

                var prevX = 0f
                var prevY = height - ((dataPoints[0].coerceIn(minValue, maxValue) - minValue) / range) * height

                linePath.moveTo(prevX, prevY)
                fillPath.moveTo(prevX, height)
                fillPath.lineTo(prevX, prevY)

                for (i in 1 until dataPoints.size) {
                    val currX = i * stepX
                    val currY = height - ((dataPoints[i].coerceIn(minValue, maxValue) - minValue) / range) * height

                    // Stepped connection: horizontal then vertical for retro pixel look
                    val midX = prevX + (currX - prevX) / 2f
                    linePath.lineTo(midX, prevY)
                    linePath.lineTo(midX, currY)
                    linePath.lineTo(currX, currY)

                    fillPath.lineTo(midX, prevY)
                    fillPath.lineTo(midX, currY)
                    fillPath.lineTo(currX, currY)

                    prevX = currX
                    prevY = currY
                }

                fillPath.lineTo(width, height)
                fillPath.close()

                // Draw filled area
                drawPath(
                    path = fillPath,
                    color = fillColor
                )

                // Draw chunky retro outline
                drawPath(
                    path = linePath,
                    color = lineColor,
                    style = Stroke(width = 2.5.dp.toPx())
                )

                // Draw square pixel markers at data points
                if (showDots) {
                    val dotSize = 4.dp.toPx()
                    for (i in dataPoints.indices) {
                        val x = i * stepX
                        val y = height - ((dataPoints[i].coerceIn(minValue, maxValue) - minValue) / range) * height
                        drawRect(
                            color = lineColor,
                            topLeft = Offset(x - dotSize / 2f, y - dotSize / 2f),
                            size = Size(dotSize, dotSize)
                        )
                    }
                }
            }
        }

        // X-axis labels
        if (labels.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
            ) {
                labels.forEach { label ->
                    Text(
                        text = label,
                        color = labelColor,
                        fontFamily = PixelFontFamily,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
fun RetroSparkline(
    dataPoints: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color = PixelGreen,
    strokeWidth: Dp = 2.dp
) {
    if (dataPoints.isEmpty()) return

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        if (dataPoints.size == 1) {
            val y = height / 2f
            drawLine(
                color = lineColor,
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = strokeWidth.toPx()
            )
            return@Canvas
        }

        val min = dataPoints.minOrNull() ?: 0f
        val max = dataPoints.maxOrNull() ?: 100f
        val isFlat = (max - min) <= 0.01f

        if (isFlat) {
            val y = height / 2f
            drawLine(
                color = lineColor,
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = strokeWidth.toPx()
            )
            return@Canvas
        }

        val range = max - min
        val stepX = width / (dataPoints.size - 1)
        val padY = strokeWidth.toPx() / 2f
        val usableHeight = (height - strokeWidth.toPx()).coerceAtLeast(1f)

        val path = Path()
        val startY = padY + (1f - (dataPoints[0] - min) / range) * usableHeight
        path.moveTo(0f, startY)

        var prevX = 0f
        var prevY = startY

        for (i in 1 until dataPoints.size) {
            val currX = i * stepX
            val currY = padY + (1f - (dataPoints[i] - min) / range) * usableHeight
            val midX = prevX + (currX - prevX) / 2f

            path.lineTo(midX, prevY)
            path.lineTo(midX, currY)
            path.lineTo(currX, currY)

            prevX = currX
            prevY = currY
        }

        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = strokeWidth.toPx())
        )
    }
}

