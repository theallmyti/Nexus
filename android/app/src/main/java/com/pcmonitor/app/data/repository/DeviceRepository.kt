package com.pcmonitor.app.data.repository

import com.pcmonitor.app.data.model.Device
import com.pcmonitor.app.data.model.HardwareStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface DeviceRepository {
    val devices: StateFlow<List<Device>>
    fun getDevice(deviceId: String): Flow<Device?>
    /** The device whose screen is open: it gets live (1 s) data; the others refresh every 15 s. */
    fun setActiveDevice(deviceId: String)
    suspend fun addDevice(device: Device)
    suspend fun removeDevice(deviceId: String)
    suspend fun updateDeviceStats(deviceId: String, stats: HardwareStats)
    suspend fun setDeviceOnline(deviceId: String, isOnline: Boolean)
    fun getOnlineDeviceCount(): Flow<Int>
    fun getOfflineDeviceCount(): Flow<Int>
    fun getCriticalDeviceCount(): Flow<Int>
    /** Re-fetch the device list now (pull-to-refresh, home-screen widget). */
    suspend fun refresh()
    /** Owner only: accounts the computer is shared with. */
    suspend fun getShares(deviceId: String): Result<List<com.pcmonitor.app.data.model.DeviceShare>>
    /** Owner only: remove one account's access. */
    suspend fun removeShare(deviceId: String, userId: String): Result<Unit>
    suspend fun startMockSimulation()
    suspend fun stopMockSimulation()
}

