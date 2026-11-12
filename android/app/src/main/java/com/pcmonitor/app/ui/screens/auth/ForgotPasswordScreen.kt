package com.pcmonitor.app.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
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
import com.pcmonitor.app.ui.theme.ReadableFontFamily
import com.pcmonitor.app.ui.theme.TextGray
import com.pcmonitor.app.ui.theme.TextWhite

@Composable
fun ForgotPasswordScreen(
    viewModel: AuthViewModel,
    onNavigateBack: () -> Unit
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Forgot Your Password?",
                fontFamily = ReadableFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = TextWhite
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Enter your registered email address. In this mock mode, the system will verify whether your account exists and generate a reset code.",
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

