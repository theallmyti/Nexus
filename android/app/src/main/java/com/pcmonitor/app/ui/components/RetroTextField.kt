package com.pcmonitor.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pcmonitor.app.ui.theme.ArcadeYellow
import com.pcmonitor.app.ui.theme.CriticalRed
import com.pcmonitor.app.ui.theme.DarkNavySurface
import com.pcmonitor.app.ui.theme.PixelBorderColor
import com.pcmonitor.app.ui.theme.ReadableFontFamily
import com.pcmonitor.app.ui.theme.RetroButtonShape
import com.pcmonitor.app.ui.theme.TextGray
import com.pcmonitor.app.ui.theme.TextWhite

@Composable
fun RetroTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    isError: Boolean = false,
    errorMessage: String? = null,
    leadingIcon: ImageVector? = null,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    var passwordVisible by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = {
                Text(
                    text = label,
                    fontFamily = ReadableFontFamily,
                    fontSize = 14.sp,
                    color = TextGray
                )
            },
            modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(
                color = TextWhite,
                fontFamily = ReadableFontFamily,
                fontSize = 16.sp
            ),
            isError = isError,
            singleLine = true,
            shape = RetroButtonShape,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = DarkNavySurface,
                unfocusedContainerColor = DarkNavySurface,
                errorContainerColor = DarkNavySurface,
                focusedBorderColor = ArcadeYellow,
                unfocusedBorderColor = PixelBorderColor,
                errorBorderColor = CriticalRed,
                cursorColor = ArcadeYellow,
                errorCursorColor = CriticalRed,
                focusedLabelColor = ArcadeYellow,
                unfocusedLabelColor = TextGray,
                errorLabelColor = CriticalRed,
                focusedLeadingIconColor = ArcadeYellow,
                unfocusedLeadingIconColor = TextGray,
                errorLeadingIconColor = CriticalRed,
                focusedTrailingIconColor = TextGray,
                unfocusedTrailingIconColor = TextGray,
                errorTrailingIconColor = CriticalRed
            ),
            leadingIcon = if (leadingIcon != null) {
                { Icon(imageVector = leadingIcon, contentDescription = null) }
            } else null,
            trailingIcon = if (isPassword) {
                {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Filled.Visibility
                            else Icons.Filled.VisibilityOff,
                            contentDescription = if (passwordVisible) "Hide password"
                            else "Show password"
                        )
                    }
                }
            } else null,
            visualTransformation = if (isPassword && !passwordVisible) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = if (isPassword) KeyboardType.Password else keyboardType
            )
        )

        if (isError && !errorMessage.isNullOrEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = errorMessage,
                color = CriticalRed,
                fontSize = 12.sp,
                fontFamily = ReadableFontFamily,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}
