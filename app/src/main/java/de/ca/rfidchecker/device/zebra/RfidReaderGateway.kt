package de.ca.rfidchecker.device.zebra

import de.ca.rfidchecker.domain.model.ReaderStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

interface RfidReaderGateway {
    fun observeStatus(): Flow<ReaderStatus>
    fun observeTagScans(): Flow<String>
    fun observeTriggerEvents(): Flow<Unit> = emptyFlow() // Default empty implementation
    suspend fun discoverReaders(): List<String>
    suspend fun connect(readerId: String): Result<Unit>
    suspend fun disconnect()
    suspend fun triggerInventory()
    suspend fun setPower(level: Int): Result<Unit>
}