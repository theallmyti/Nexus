package com.pcmonitor.app.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pcmonitor.app.ui.components.RetroTextField
import com.pcmonitor.app.ui.components.RetroTopBar
import com.pcmonitor.app.ui.theme.ArcadeYellow
import com.pcmonitor.app.ui.theme.CriticalRed
import com.pcmonitor.app.ui.theme.PixelFontFamily
import com.pcmonitor.app.ui.theme.PixelGreen
import com.pcmonitor.app.ui.theme.ReadableFontFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class AccountDialog { ChangePassword, SignOutEverywhere, DeleteAccount }

@Composable
fun AccountSecurityScreen(
    viewModel: AccountSecurityViewModel,
    onBack: () -> Unit,
    onSignedOut: () -> Unit
) {
    var dialog by remember { mutableStateOf<AccountDialog?>(null) }
    val resolver = LocalContext.current.contentResolver
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> if (uri != null) viewModel.exportTo(uri, resolver) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        RetroTopBar(title = "Account & Security", onBackClick = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            SettingsMenuItem(
                icon = Icons.Default.Key,
                title = "Change password",
                subtitle = "Signs out your other devices",
                onClick = {
                    viewModel.resetPasswordForm()
                    dialog = AccountDialog.ChangePassword
                }
            )
            Spacer(modifier = Modifier.height(10.dp))
            SettingsMenuItem(
                icon = Icons.Default.Logout,
                title = "Sign out everywhere",
                subtitle = "Every phone and browser, including this one",
                onClick = { dialog = AccountDialog.SignOutEverywhere }
            )
            Spacer(modifier = Modifier.height(10.dp))
            SettingsMenuItem(
                icon = Icons.Default.Download,
                title = "Export my data",
                subtitle = "Your account, computers and recent history as JSON",
                onClick = {
                    val day = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
                    exportLauncher.launch("nexus-data-$day.json")
                }
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Color(0xFF8B7355).copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(16.dp))

            SettingsMenuItem(
                icon = Icons.Default.DeleteForever,
                title = "Delete account",
                subtitle = "Permanently removes your account and all data",
                onClick = {
                    viewModel.deleteConfirmText = ""
                    dialog = AccountDialog.DeleteAccount
                }
            )

            viewModel.message?.let { msg ->
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = msg,
                    fontFamily = ReadableFontFamily,
                    fontSize = 13.sp,
                    color = if (viewModel.messageIsError) CriticalRed else PixelGreen
                )
            }
        }
    }

    when (dialog) {
        AccountDialog.ChangePassword -> AccountActionDialog(
            title = "Change Password",
            confirmText = if (viewModel.busy) "Saving…" else "Change",
            confirmEnabled = !viewModel.busy,
            onConfirm = { viewModel.changePassword { dialog = null } },
            onDismiss = { dialog = null }
        ) {
            RetroTextField(
                value = viewModel.currentPassword,
                onValueChange = { viewModel.currentPassword = it },
                label = "Current password",
                leadingIcon = Icons.Default.Lock,
                isPassword = true,
                keyboardType = KeyboardType.Password,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
            RetroTextField(
                value = viewModel.newPassword,
                onValueChange = { viewModel.newPassword = it },
                label = "New password (8+ characters)",
                leadingIcon = Icons.Default.Key,
                isPassword = true,
                keyboardType = KeyboardType.Password,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
            RetroTextField(
                value = viewModel.confirmPassword,
                onValueChange = { viewModel.confirmPassword = it },
                label = "Repeat new password",
                leadingIcon = Icons.Default.Key,
                isPassword = true,
                keyboardType = KeyboardType.Password,
                modifier = Modifier.fillMaxWidth()
            )
            viewModel.passwordError?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = it, fontSize = 12.sp, color = CriticalRed)
            }
        }

        AccountDialog.SignOutEverywhere -> AccountActionDialog(
            title = "Sign Out Everywhere?",
            confirmText = if (viewModel.busy) "Signing out…" else "Sign out",
            confirmEnabled = !viewModel.busy,
            onConfirm = { viewModel.signOutEverywhere(onSignedOut) },
            onDismiss = { dialog = null }
        ) {
            Text(
                text = "Every phone and browser signed in to your account, including this one, will need to sign in again. Your computers keep reporting.",
                fontFamily = ReadableFontFamily,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        AccountDialog.DeleteAccount -> AccountActionDialog(
            title = "Delete Account?",
            confirmText = if (viewModel.busy) "Deleting…" else "Delete forever",
            confirmEnabled = !viewModel.busy && viewModel.deleteConfirmText.trim() == "DELETE",
            confirmColor = CriticalRed,
            onConfirm = { viewModel.deleteAccount(onSignedOut) },
            onDismiss = { dialog = null }
        ) {
            Text(
                text = "This permanently deletes your account, your paired computers, their history, alerts and settings. It can't be undone. Export your data first if you want a copy.",
                fontFamily = ReadableFontFamily,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
            RetroTextField(
                value = viewModel.deleteConfirmText,
                onValueChange = { viewModel.deleteConfirmText = it },
                label = "Type DELETE to confirm",
                leadingIcon = Icons.Default.DeleteForever,
                modifier = Modifier.fillMaxWidth()
            )
        }

        null -> Unit
    }
}

@Composable
private fun AccountActionDialog(
    title: String,
    confirmText: String,
    confirmEnabled: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmColor: Color = ArcadeYellow,
    content: @Composable () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title, fontFamily = PixelFontFamily, color = ArcadeYellow, fontSize = 15.sp) },
        text = { Column { content() } },
        confirmButton = {
            Text(
                text = confirmText,
                color = if (confirmEnabled) confirmColor else confirmColor.copy(alpha = 0.4f),
                fontFamily = PixelFontFamily,
                modifier = Modifier
                    .clickable(enabled = confirmEnabled, onClick = onConfirm)
                    .padding(8.dp)
            )
        },
        dismissButton = {
            Text(
                text = "Cancel",
                color = Color(0xFF94A3B8),
                fontFamily = PixelFontFamily,
                modifier = Modifier
                    .clickable(onClick = onDismiss)
                    .padding(8.dp)
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}
