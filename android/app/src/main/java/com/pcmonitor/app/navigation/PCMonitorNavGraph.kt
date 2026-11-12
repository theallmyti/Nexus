package com.pcmonitor.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pcmonitor.app.PCMonitorApplication
import com.pcmonitor.app.ui.components.BottomNavTab
import com.pcmonitor.app.ui.components.RetroBottomNavBar
import com.pcmonitor.app.ui.screens.alerts.AlertDetailScreen
import com.pcmonitor.app.ui.screens.alerts.AlertsScreen
import com.pcmonitor.app.ui.screens.alerts.AlertsViewModel
import com.pcmonitor.app.ui.screens.auth.AuthViewModel
import com.pcmonitor.app.ui.screens.auth.ForgotPasswordScreen
import com.pcmonitor.app.ui.screens.auth.LoginScreen
import com.pcmonitor.app.ui.screens.auth.RegisterScreen
import com.pcmonitor.app.ui.screens.details.DeviceDetailsScreen
import com.pcmonitor.app.ui.screens.details.DeviceDetailsViewModel
import com.pcmonitor.app.ui.screens.devices.DevicesScreen
import com.pcmonitor.app.ui.screens.devices.DevicesViewModel
import com.pcmonitor.app.ui.screens.history.HistoryScreen
import com.pcmonitor.app.ui.screens.history.HistoryViewModel
import com.pcmonitor.app.ui.screens.home.HomeScreen
import com.pcmonitor.app.ui.screens.home.HomeViewModel
import com.pcmonitor.app.ui.screens.onboarding.OnboardingScreen
import com.pcmonitor.app.ui.screens.onboarding.OnboardingViewModel
import com.pcmonitor.app.ui.screens.pairing.AddDeviceScreen
import com.pcmonitor.app.ui.screens.pairing.ManualPairingScreen
import com.pcmonitor.app.ui.screens.pairing.PairingProgressScreen
import com.pcmonitor.app.ui.screens.pairing.QRScanScreen
import com.pcmonitor.app.ui.screens.settings.SettingsScreen
import com.pcmonitor.app.ui.screens.settings.SettingsViewModel
import com.pcmonitor.app.ui.screens.splash.SplashScreen
import com.pcmonitor.app.ui.screens.splash.SplashViewModel
import com.pcmonitor.app.ui.screens.thresholds.ThresholdSettingsScreen
import com.pcmonitor.app.ui.screens.thresholds.ThresholdSettingsViewModel
import com.pcmonitor.app.ui.theme.DeepNavy

@Composable
fun PCMonitorNavGraph(
    initialPairingCode: String? = null,
    onPairingCodeConsumed: () -> Unit = {}
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val app = context.applicationContext as PCMonitorApplication

    LaunchedEffect(initialPairingCode) {
        if (!initialPairingCode.isNullOrBlank()) {
            navController.navigate(Screen.PairingProgress.createRoute(initialPairingCode))
            onPairingCodeConsumed()
        }
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route
    ) {
        // Splash Screen
        composable(Screen.Splash.route) {
            val splashViewModel = viewModel {
                SplashViewModel(app.userPreferencesRepository)
            }
            SplashScreen(
                viewModel = splashViewModel,
                onNavigateToOnboarding = {
                    navController.navigate(Screen.Onboarding.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToHome = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        // Onboarding Screen
        composable(Screen.Onboarding.route) {
            val onboardingViewModel = viewModel {
                OnboardingViewModel(app.userPreferencesRepository)
            }
            OnboardingScreen(
                viewModel = onboardingViewModel,
                onNavigateToLogin = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        // Auth: Login Screen
        composable(Screen.Login.route) {
            val authViewModel = viewModel {
                AuthViewModel(app.authRepository, app.userPreferencesRepository)
            }
            LoginScreen(
                viewModel = authViewModel,
                onNavigateToRegister = { navController.navigate(Screen.Register.route) },
                onNavigateToForgotPassword = { navController.navigate(Screen.ForgotPassword.route) },
                onNavigateToHome = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        // Auth: Register Screen
        composable(Screen.Register.route) {
            val authViewModel = viewModel {
                AuthViewModel(app.authRepository, app.userPreferencesRepository)
            }
            RegisterScreen(
                viewModel = authViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToHome = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        // Auth: Forgot Password Screen
        composable(Screen.ForgotPassword.route) {
            val authViewModel = viewModel {
                AuthViewModel(app.authRepository, app.userPreferencesRepository)
            }
            ForgotPasswordScreen(
                viewModel = authViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToHome = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        // Main Shell with Bottom Navigation Tabs
        composable(Screen.Main.route) {
            MainContainerScreen(
                app = app,
                rootNavController = navController
            )
        }

        // Device Details Screen
        composable(
            route = Screen.DeviceDetails.route,
            arguments = listOf(navArgument("deviceId") { type = NavType.StringType })
        ) { backStackEntry ->
            val deviceId = backStackEntry.arguments?.getString("deviceId") ?: return@composable
            val detailsViewModel = viewModel {
                DeviceDetailsViewModel(deviceId, app.deviceRepository, app.userPreferencesRepository)
            }
            DeviceDetailsScreen(
                viewModel = detailsViewModel,
                onBack = { navController.popBackStack() },
                onNavigateToHistory = { devId ->
                    navController.navigate(Screen.History.createRoute(devId))
                },
                onNavigateToThresholds = { devId ->
                    navController.navigate(Screen.ThresholdSettings.createRoute(devId))
                }
            )
        }

        // Add Device Screen
        composable(Screen.AddDevice.route) {
            AddDeviceScreen(
                onBack = { navController.popBackStack() },
                onScanQrClick = { navController.navigate(Screen.QRScan.route) },
                onEnterCodeClick = { navController.navigate(Screen.ManualPairing.route) }
            )
        }

        // QR Scan Screen
        composable(Screen.QRScan.route) {
            QRScanScreen(
                onBack = { navController.popBackStack() },
                onManualCodeClick = {
                    navController.navigate(Screen.ManualPairing.route) {
                        popUpTo(Screen.QRScan.route) { inclusive = true }
                    }
                },
                onQrDetected = { code ->
                    navController.navigate(Screen.PairingProgress.createRoute(code)) {
                        popUpTo(Screen.AddDevice.route) { inclusive = true }
                    }
                }
            )
        }

        // Manual Pairing Screen
        composable(Screen.ManualPairing.route) {
            ManualPairingScreen(
                onBack = { navController.popBackStack() },
                onCodeSubmitted = { code ->
                    navController.navigate(Screen.PairingProgress.createRoute(code)) {
                        popUpTo(Screen.AddDevice.route) { inclusive = true }
                    }
                }
            )
        }

        // Pairing Progress Screen
        composable(
            route = Screen.PairingProgress.route,
            arguments = listOf(navArgument("code") { type = NavType.StringType })
        ) { backStackEntry ->
            val code = backStackEntry.arguments?.getString("code") ?: ""
            PairingProgressScreen(
                code = code,
                deviceRepository = app.deviceRepository,
                userPreferencesRepository = app.userPreferencesRepository,
                onBack = { navController.popBackStack() },
                onPairingComplete = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Main.route) { inclusive = true }
                    }
                }
            )
        }

        // Alert Detail Screen
        composable(
            route = Screen.AlertDetail.route,
            arguments = listOf(navArgument("alertId") { type = NavType.StringType })
        ) { backStackEntry ->
            val alertId = backStackEntry.arguments?.getString("alertId") ?: return@composable
            AlertDetailScreen(
                alertId = alertId,
                alertRepository = app.alertRepository,
                onBack = { navController.popBackStack() }
            )
        }

        // Threshold Settings Screen
        composable(
            route = Screen.ThresholdSettings.route,
            arguments = listOf(
                navArgument("deviceId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val deviceId = backStackEntry.arguments?.getString("deviceId")
            val thresholdsViewModel = viewModel {
                ThresholdSettingsViewModel(
                    preferencesRepository = app.userPreferencesRepository,
                    deviceRepository = app.deviceRepository,
                    initialDeviceId = deviceId
                )
            }
            ThresholdSettingsScreen(
                viewModel = thresholdsViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        // History Screen
        composable(
            route = Screen.History.route,
            arguments = listOf(navArgument("deviceId") { type = NavType.StringType })
        ) { backStackEntry ->
            val deviceId = backStackEntry.arguments?.getString("deviceId") ?: ""
            val historyViewModel = viewModel {
                HistoryViewModel(
                    initialDeviceId = deviceId,
                    historyRepository = app.historyRepository,
                    deviceRepository = app.deviceRepository,
                    preferencesRepository = app.userPreferencesRepository
                )
            }
            HistoryScreen(
                viewModel = historyViewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

@Composable
private fun MainContainerScreen(
    app: PCMonitorApplication,
    rootNavController: NavController
) {
    var selectedTab by remember { mutableStateOf(BottomNavTab.Home) }

    // Shared / persistent view models across the main tabs
    val homeViewModel = viewModel {
        HomeViewModel(app.deviceRepository, app.alertRepository, app.userPreferencesRepository)
    }
    val devicesViewModel = viewModel {
        DevicesViewModel(app.deviceRepository, app.userPreferencesRepository)
    }
    val alertsViewModel = viewModel {
        AlertsViewModel(app.alertRepository)
    }
    val settingsViewModel = viewModel {
        SettingsViewModel(app.authRepository, app.userPreferencesRepository, app.deviceRepository)
    }

    val unreadAlertsCount by app.alertRepository.alerts.collectAsState()
    val unreadCount = unreadAlertsCount.count { !it.isRead }

    // Session expired or rejected by the server (the device repository clears it): back to login.
    val loggedIn by app.userPreferencesRepository.isLoggedIn.collectAsState(initial = true)
    LaunchedEffect(loggedIn) {
        if (!loggedIn && rootNavController.currentDestination?.route != Screen.Login.route) {
            rootNavController.navigate(Screen.Login.route) {
                popUpTo(Screen.Main.route) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    Scaffold(
        bottomBar = {
            RetroBottomNavBar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                alertBadgeCount = unreadCount
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (selectedTab) {
                BottomNavTab.Home -> {
                    HomeScreen(
                        viewModel = homeViewModel,
                        onDeviceClick = { deviceId ->
                            rootNavController.navigate(Screen.DeviceDetails.createRoute(deviceId))
                        },
                        onViewAllDevicesClick = {
                            selectedTab = BottomNavTab.Devices
                        },
                        onAddDeviceClick = {
                            rootNavController.navigate(Screen.AddDevice.route)
                        },
                        onAlertsClick = {
                            selectedTab = BottomNavTab.Alerts
                        },
                        onProfileClick = {
                            selectedTab = BottomNavTab.Settings
                        },
                        onAlertClick = { alertId ->
                            rootNavController.navigate(Screen.AlertDetail.createRoute(alertId))
                        }
                    )
                }
                BottomNavTab.Devices -> {
                    DevicesScreen(
                        viewModel = devicesViewModel,
                        onDeviceClick = { deviceId ->
                            rootNavController.navigate(Screen.DeviceDetails.createRoute(deviceId))
                        },
                        onAddDeviceClick = {
                            rootNavController.navigate(Screen.AddDevice.route)
                        }
                    )
                }
                BottomNavTab.Alerts -> {
                    AlertsScreen(
                        viewModel = alertsViewModel,
                        onAlertClick = { alertId ->
                            rootNavController.navigate(Screen.AlertDetail.createRoute(alertId))
                        }
                    )
                }
                BottomNavTab.Settings -> {
                    SettingsScreen(
                        viewModel = settingsViewModel,
                        onNavigateToThresholds = {
                            rootNavController.navigate(Screen.ThresholdSettings.createRoute())
                        },
                        onSignedOut = {
                            rootNavController.navigate(Screen.Login.route) {
                                popUpTo(Screen.Main.route) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    )
                }
            }
        }
    }
}

