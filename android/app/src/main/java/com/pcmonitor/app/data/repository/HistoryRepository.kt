package com.pcmonitor.app.data.repository

import com.pcmonitor.app.data.model.HistorySummary

interface HistoryRepository {
    suspend fun getHistoryForDevice(deviceId: String, days: Int = 7): HistorySummary
}

