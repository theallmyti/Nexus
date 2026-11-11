package com.pcmonitor.app

import android.app.Application
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.pcmonitor.app.data.mock.MockAlertRepository
import com.pcmonitor.app.data.mock.MockAuthRepository
import com.pcmonitor.app.data.mock.MockDeviceRepository
import com.pcmonitor.app.data.mock.MockHistoryRepository
import com.pcmonitor.app.data.repository.AlertRepository
import com.pcmonitor.app.data.repository.AuthRepository
import com.pcmonitor.app.data.repository.DeviceRepository
import com.pcmonitor.app.data.repository.HistoryRepository
import com.pcmonitor.app.data.repository.UserPreferencesRepository
import com.pcmonitor.app.notification.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PCMonitorApplication : Application() {

    lateinit var userPreferencesRepository: UserPreferencesRepository
        private set
    lateinit var authRepository: AuthRepository
        private set
    lateinit var deviceRepository: DeviceRepository
        private set
    lateinit var alertRepository: AlertRepository
        private set
    lateinit var historyRepository: HistoryRepository
        private set

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannels(this)

        userPreferencesRepository = UserPreferencesRepository(this)
        authRepository = MockAuthRepository()
        val devRepo = MockDeviceRepository(userPreferencesRepository, this)
        deviceRepository = devRepo
        alertRepository = MockAlertRepository(devRepo, userPreferencesRepository)
        historyRepository = MockHistoryRepository(userPreferencesRepository)

        // Fetch current FCM token
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                Log.i("NexusApplication", "FCM Registration Token: $token")
                CoroutineScope(Dispatchers.IO).launch {
                    userPreferencesRepository.setFcmToken(token)
                    val email = userPreferencesRepository.userEmail.first()
                    if (email.isNotBlank()) {
                        userPreferencesRepository.syncPushTokenToConvex(email, token)
                    }
                }
            } else {
                Log.w("NexusApplication", "Failed to fetch FCM registration token", task.exception)
            }
        }

        // Automatically start the realistic 1-second simulation loop
        CoroutineScope(Dispatchers.Default).launch {
            devRepo.startMockSimulation()
        }
    }
}
