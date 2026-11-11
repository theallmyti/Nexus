package com.pcmonitor.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.pcmonitor.app.navigation.PCMonitorNavGraph
import com.pcmonitor.app.notification.NotificationHelper
import com.pcmonitor.app.ui.theme.DeepNavy
import com.pcmonitor.app.ui.theme.PCMonitorTheme
import com.pcmonitor.app.util.PairingHelper

class MainActivity : ComponentActivity() {

    private val incomingPairingCode = mutableStateOf<String?>(null)

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            // Notification permission result handled
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        NotificationHelper.createNotificationChannels(this)
        handlePairingIntent(intent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            PCMonitorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DeepNavy
                ) {
                    PCMonitorNavGraph(
                        initialPairingCode = incomingPairingCode.value,
                        onPairingCodeConsumed = { incomingPairingCode.value = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePairingIntent(intent)
    }

    private fun handlePairingIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        val rawCode = uri.getQueryParameter("code") ?: uri.pathSegments.lastOrNull()
        if (!rawCode.isNullOrBlank()) {
            val code = PairingHelper.normalizeCode(rawCode)
            if (PairingHelper.isValidCode(code)) {
                incomingPairingCode.value = code
            }
        }
    }
}

