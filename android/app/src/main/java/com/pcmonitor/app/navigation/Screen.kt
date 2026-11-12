package com.pcmonitor.app.navigation

sealed class Screen(val route: String) {
    data object Splash : Screen("splash")
    data object Onboarding : Screen("onboarding")
    data object Login : Screen("login")
    data object Register : Screen("register")
    data object ForgotPassword : Screen("forgot_password")
    data object Main : Screen("main")
    data object Home : Screen("home")
    data object Devices : Screen("devices")
    data object DeviceDetails : Screen("device_details/{deviceId}") {
        fun createRoute(deviceId: String) = "device_details/$deviceId"
    }
    data object AddDevice : Screen("add_device")
    data object QRScan : Screen("qr_scan")
    data object ManualPairing : Screen("manual_pairing")
    data object PairingProgress : Screen("pairing_progress/{code}") {
        fun createRoute(code: String) = "pairing_progress/$code"
    }
    data object Alerts : Screen("alerts")
    data object AlertDetail : Screen("alert_detail/{alertId}") {
        fun createRoute(alertId: String) = "alert_detail/$alertId"
    }
    data object ThresholdSettings : Screen("threshold_settings?deviceId={deviceId}") {
        fun createRoute(deviceId: String? = null) = if (deviceId != null) "threshold_settings?deviceId=$deviceId" else "threshold_settings"
    }
    data object History : Screen("history/{deviceId}") {
        fun createRoute(deviceId: String) = "history/$deviceId"
    }
    data object Settings : Screen("settings")
    data object Profile : Screen("profile")
}

