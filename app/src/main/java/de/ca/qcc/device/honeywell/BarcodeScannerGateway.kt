package de.ca.qcc.device.honeywell

import kotlinx.coroutines.flow.Flow

interface BarcodeScannerGateway {
    fun observeScans(): Flow<String>
    suspend fun triggerScan()
    suspend fun stopScan()
}
