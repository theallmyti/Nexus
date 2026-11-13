package com.pcmonitor.app.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pcmonitor.app.ui.components.RetroButton
import com.pcmonitor.app.ui.components.RetroButtonVariant
import com.pcmonitor.app.ui.components.RetroTextField
import com.pcmonitor.app.ui.components.RetroTopBar
import com.pcmonitor.app.ui.theme.CriticalRed
import com.pcmonitor.app.ui.theme.DeepNavy
import com.pcmonitor.app.ui.theme.PixelGreen
import com.pcmonitor.app.ui.theme.PixelFontFamily
import com.pcmonitor.app.ui.theme.ReadableFontFamily
import com.pcmonitor.app.ui.theme.TextGray
import com.pcmonitor.app.ui.theme.TextWhite

@Composable
fun ForgotPasswordScreen(
    viewModel: AuthViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToHome: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        RetroTopBar(
            title = "Reset Password",
            onBackClick = onNavigateBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Forgot Your Password?",
                fontFamily = PixelFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                color = TextWhite
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = if (viewModel.forgotCodeSent)
                    "Enter the 6-digit code we emailed you and choose a new password. This signs you out everywhere else."
                else
                    "Enter your account's email address and we'll send you a 6-digit reset code.",
                fontFamily = ReadableFontFamily,
                fontSize = 14.sp,
                color = TextGray,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Email input
            RetroTextField(
                value = viewModel.forgotEmail,
                onValueChange = { viewModel.forgotEmail = it },
                label = "Registered email address",
                leadingIcon = Icons.Default.Email,
                isError = viewModel.forgotEmailError != null,
                errorMessage = viewModel.forgotEmailError,
                keyboardType = KeyboardType.Email,
                modifier = Modifier.fillMaxWidth()
            )

            if (viewModel.forgotCodeSent) {
                Spacer(modifier = Modifier.height(14.dp))
                RetroTextField(
                    value = viewModel.forgotCode,
                    onValueChange = { v -> viewModel.forgotCode = v.filter { it.isDigit() }.take(6) },
                    label = "6-digit code",
                    leadingIcon = Icons.Default.Pin,
                    isError = viewModel.forgotCodeError != null,
                    errorMessage = viewModel.forgotCodeError,
                    keyboardType = KeyboardType.NumberPassword,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(14.dp))
                RetroTextField(
                    value = viewModel.forgotNewPassword,
                    onValueChange = { viewModel.forgotNewPassword = it },
                    label = "New password (8+ characters)",
                    leadingIcon = Icons.Default.Lock,
                    isPassword = true,
                    isError = viewModel.forgotNewPasswordError != null,
                    errorMessage = viewModel.forgotNewPasswordError,
                    keyboardType = KeyboardType.Password,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (viewModel.generalError != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = viewModel.generalError ?: "",
                    fontFamily = ReadableFontFamily,
                    fontSize = 13.sp,
                    color = CriticalRed,
                    textAlign = TextAlign.Center
                )
            }

            if (viewModel.forgotMessage != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = viewModel.forgotMessage ?: "",
                    fontFamily = ReadableFontFamily,
                    fontSize = 13.sp,
                    color = PixelGreen,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            if (viewModel.forgotCodeSent) {
                RetroButton(
                    text = "Set New Password",
                    onClick = { viewModel.resetPassword(onSuccess = onNavigateToHome) },
                    isLoading = viewModel.isLoading,
                    variant = RetroButtonVariant.Primary,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Resend code",
                    fontFamily = ReadableFontFamily,
                    fontSize = 13.sp,
                    color = TextGray,
                    modifier = Modifier.clickable(enabled = !viewModel.isLoading) { viewModel.requestPasswordReset() }
                )
            } else {
                RetroButton(
                    text = "Send Reset Code",
                    onClick = { viewModel.requestPasswordReset() },
                    isLoading = viewModel.isLoading,
                    variant = RetroButtonVariant.Primary,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

