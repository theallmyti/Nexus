package com.pcmonitor.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pcmonitor.app.ui.theme.ArcadeYellow
import com.pcmonitor.app.ui.theme.ArcadeYellowDark
import com.pcmonitor.app.ui.theme.CriticalRed
import com.pcmonitor.app.ui.theme.DeepNavy
import com.pcmonitor.app.ui.theme.PixelFontFamily
import com.pcmonitor.app.ui.theme.PixelGreen
import com.pcmonitor.app.ui.theme.PixelGreenDark
import com.pcmonitor.app.ui.theme.RetroButtonShape
import com.pcmonitor.app.ui.theme.TextWhite

enum class RetroButtonVariant { Primary, Secondary, Danger, Outline }

@Composable
fun RetroButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    variant: RetroButtonVariant = RetroButtonVariant.Primary
) {
    val bgColor: Color
    val contentColor: Color
    val shadowColor: Color

    when (variant) {
        RetroButtonVariant.Primary -> {
            bgColor = ArcadeYellow
            contentColor = DeepNavy
            shadowColor = ArcadeYellowDark
        }
        RetroButtonVariant.Secondary -> {
            bgColor = PixelGreen
            contentColor = TextWhite
            shadowColor = PixelGreenDark
        }
        RetroButtonVariant.Danger -> {
            bgColor = CriticalRed
            contentColor = TextWhite
            shadowColor = Color(0xFFCC3333)
        }
        RetroButtonVariant.Outline -> {
            bgColor = Color.Transparent
            contentColor = ArcadeYellow
            shadowColor = Color.Transparent
        }
    }

    if (variant == RetroButtonVariant.Outline) {
        OutlinedButton(
            onClick = { if (!isLoading) onClick() },
            modifier = modifier.defaultMinSize(minHeight = 48.dp),
            enabled = enabled,
            shape = RetroButtonShape,
            border = BorderStroke(2.dp, if (enabled) ArcadeYellow else ArcadeYellow.copy(alpha = 0.4f)),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color.Transparent,
                contentColor = contentColor,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = contentColor.copy(alpha = 0.4f)
            ),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
        ) {
            ButtonContent(
                text = text,
                isLoading = isLoading,
                contentColor = if (enabled) contentColor else contentColor.copy(alpha = 0.4f),
                progressColor = ArcadeYellow
            )
        }
    } else {
        val actualShadowColor = if (enabled) shadowColor else shadowColor.copy(alpha = 0.4f)
        Button(
            onClick = { if (!isLoading) onClick() },
            modifier = modifier
                .defaultMinSize(minHeight = 48.dp)
                .drawBehind {
                    if (shadowColor != Color.Transparent) {
                        drawRoundRect(
                            color = actualShadowColor,
                            topLeft = Offset(0f, 2.dp.toPx()),
                            size = Size(size.width, size.height),
                            cornerRadius = CornerRadius(6.dp.toPx())
                        )
                    }
                },
            enabled = enabled,
            shape = RetroButtonShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = bgColor,
                contentColor = contentColor,
                disabledContainerColor = bgColor.copy(alpha = 0.4f),
                disabledContentColor = contentColor.copy(alpha = 0.5f)
            ),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp,
                disabledElevation = 0.dp
            )
        ) {
            ButtonContent(
                text = text,
                isLoading = isLoading,
                contentColor = if (enabled) contentColor else contentColor.copy(alpha = 0.5f),
                progressColor = contentColor
            )
        }
    }
}

@Composable
private fun ButtonContent(
    text: String,
    isLoading: Boolean,
    contentColor: Color,
    progressColor: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = progressColor,
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            fontFamily = PixelFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            color = contentColor
        )
    }
}
