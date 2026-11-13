package com.pcmonitor.app.ui.screens.auth

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
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
import com.pcmonitor.app.ui.components.TurnstileWidget
import com.pcmonitor.app.ui.theme.ArcadeYellow
import com.pcmonitor.app.ui.theme.CriticalRed
import com.pcmonitor.app.ui.theme.DeepNavy
import com.pcmonitor.app.ui.theme.PixelFontFamily
import com.pcmonitor.app.ui.theme.ReadableFontFamily
import com.pcmonitor.app.ui.theme.TextDarkGray
import com.pcmonitor.app.ui.theme.TextGray
import com.pcmonitor.app.ui.theme.TextWhite

@Composable
fun RegisterScreen(
    viewModel: AuthViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToHome: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        RetroTopBar(
            title = "Create Account",
            onBackClick = onNavigateBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Join Nexus",
                fontFamily = PixelFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                lineHeight = 25.sp,
                color = TextWhite
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Keep an eye on all your machines remotely",
                fontFamily = ReadableFontFamily,
                fontSize = 14.sp,
                color = TextGray
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Name
            RetroTextField(
                value = viewModel.registerName,
                onValueChange = { viewModel.registerName = it },
                label = "Full Name",
                leadingIcon = Icons.Default.Person,
                isError = viewModel.registerNameError != null,
                errorMessage = viewModel.registerNameError,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Email
            RetroTextField(
                value = viewModel.registerEmail,
                onValueChange = { viewModel.registerEmail = it },
                label = "Email address",
                leadingIcon = Icons.Default.Email,
                isError = viewModel.registerEmailError != null,
                errorMessage = viewModel.registerEmailError,
                keyboardType = KeyboardType.Email,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Password
            RetroTextField(
                value = viewModel.registerPassword,
                onValueChange = { viewModel.registerPassword = it },
                label = "Password (min 6 characters)",
                leadingIcon = Icons.Default.Lock,
                isPassword = true,
                isError = viewModel.registerPasswordError != null,
                errorMessage = viewModel.registerPasswordError,
                keyboardType = KeyboardType.Password,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Confirm Password
            RetroTextField(
                value = viewModel.registerConfirmPassword,
                onValueChange = { viewModel.registerConfirmPassword = it },
                label = "Confirm Password",
                leadingIcon = Icons.Default.Lock,
                isPassword = true,
                isError = viewModel.registerConfirmPasswordError != null,
                errorMessage = viewModel.registerConfirmPasswordError,
                keyboardType = KeyboardType.Password,
                modifier = Modifier.fillMaxWidth()
            )

            if (viewModel.generalError != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = viewModel.generalError ?: "",
                    fontFamily = ReadableFontFamily,
                    fontSize = 13.sp,
                    color = CriticalRed,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Cloudflare Turnstile bot check (required by auth:signUp)
            TurnstileWidget(
                resetSignal = viewModel.turnstileResetSignal,
                onToken = { viewModel.turnstileToken = it }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Register Button
            RetroButton(
                text = "Create Account",
                onClick = { viewModel.register(onSuccess = onNavigateToHome) },
                enabled = viewModel.turnstileToken != null,
                isLoading = viewModel.isLoading,
                variant = RetroButtonVariant.Secondary,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Already have an account? ",
                    fontFamily = ReadableFontFamily,
                    fontSize = 14.sp,
                    color = TextGray
                )
                Text(
                    text = "Sign In",
                    fontFamily = ReadableFontFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = ArcadeYellow,
                    modifier = Modifier.clickable { onNavigateBack() }
                )
            }
        }
    }
}

