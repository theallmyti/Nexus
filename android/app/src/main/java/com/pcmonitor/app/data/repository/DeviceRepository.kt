package com.pcmonitor.app.data.repository

import com.pcmonitor.app.data.model.Device
import com.pcmonitor.app.data.model.HardwareStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface DeviceRepository {
    val devices: StateFlow<List<Device>>
    fun getDevice(deviceId: String): Flow<Device?>
    suspend fun addDevice(device: Device)
    suspend fun removeDevice(deviceId: String)
    suspend fun updateDeviceStats(deviceId: String, stats: HardwareStats)
    suspend fun setDeviceOnline(deviceId: String, isOnline: Boolean)
    fun getOnlineDeviceCount(): Flow<Int>
    fun getOfflineDeviceCount(): Flow<Int>
    fun getCriticalDeviceCount(): Flow<Int>
    suspend fun startMockSimulation()
    suspend fun stopMockSimulation()
}

