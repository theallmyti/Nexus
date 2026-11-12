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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.pcmonitor.app.navigation.PCMonitorNavGraph
import com.pcmonitor.app.notification.NotificationHelper
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

        val app = application as PCMonitorApplication
        setContent {
            val isDarkMode by app.userPreferencesRepository.darkMode.collectAsState(initial = true)
            PCMonitorTheme(darkTheme = isDarkMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PCMonitorNavGraph(
                        initialPairingCode = incomingPairingCode.value,
                        onPairingCodeConsumed = { incomingPairingCode.value = null }
                    )
                }
            }
        }
    }

    // Live polling (1 s) only while the app is on screen: in the background it drained the
    // battery and kept the agent streaming, using up Convex quota for nobody.
    override fun onStart() {
        super.onStart()
        val app = application as PCMonitorApplication
        lifecycleScope.launch { app.deviceRepository.startMockSimulation() }
    }

    override fun onStop() {
        val app = application as PCMonitorApplication
        lifecycleScope.launch { app.deviceRepository.stopMockSimulation() }
        super.onStop()
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

