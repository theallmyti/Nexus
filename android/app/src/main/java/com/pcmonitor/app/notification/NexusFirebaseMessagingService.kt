package com.pcmonitor.app.notification

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.pcmonitor.app.data.repository.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class NexusFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.i(TAG, "New FCM Token registered: $token")
        CoroutineScope(Dispatchers.IO).launch {
            val repo = UserPreferencesRepository(applicationContext)
            repo.setFcmToken(token)
            val email = repo.userEmail.first()
            if (email.isNotBlank()) {
                repo.syncPushTokenToConvex(email, token)
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.i(TAG, "FCM message received from: ${remoteMessage.from}")

        // 1. Extract payload from data or notification block
        val data = remoteMessage.data
        val deviceId = data["deviceId"] ?: "primary-pc"
        val title = data["title"] ?: remoteMessage.notification?.title ?: "Nexus Alert"
        val message = data["message"] ?: remoteMessage.notification?.body ?: "Threshold exceeded on device"

        // Handle canary ping
        val isCanary = data["type"] == "canary"
        if (isCanary) {
            Log.i(TAG, "Received canary ping from server")
            return
        }

        // Show minimalist hardware alert
        NotificationHelper.showHardwareAlert(
            context = applicationContext,
            deviceId = deviceId,
            title = title,
            message = message
        )
    }

    companion object {
        private const val TAG = "NexusFCM"
    }
}
