package de.ca.rfidchecker.device.zebra

import android.content.Context
import com.zebra.rfid.api3.DYNAMIC_POWER_OPTIMIZATION
import com.zebra.rfid.api3.ENUM_TRANSPORT
import com.zebra.rfid.api3.ENUM_TRIGGER_MODE
import com.zebra.rfid.api3.HANDHELD_TRIGGER_EVENT_TYPE
import com.zebra.rfid.api3.InvalidUsageException
import com.zebra.rfid.api3.OperationFailureException
import com.zebra.rfid.api3.RFIDReader
import com.zebra.rfid.api3.ReaderDevice
import com.zebra.rfid.api3.Readers
import com.zebra.rfid.api3.RfidEventsListener
import com.zebra.rfid.api3.RfidReadEvents
import com.zebra.rfid.api3.RfidStatusEvents
import com.zebra.rfid.api3.SESSION
import com.zebra.rfid.api3.INVENTORY_STATE
import com.zebra.rfid.api3.SL_FLAG
import com.zebra.rfid.api3.START_TRIGGER_TYPE
import com.zebra.rfid.api3.STATUS_EVENT_TYPE
import com.zebra.rfid.api3.STOP_TRIGGER_TYPE
import com.zebra.rfid.api3.TriggerInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import de.ca.rfidchecker.domain.model.ReaderStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

class ZebraRfd8500Gateway @Inject constructor(
    @ApplicationContext private val context: Context
) : RfidReaderGateway {

    private val statusFlow = MutableStateFlow(ReaderStatus())

    // Emits exactly one EPC per inventory session (hardware trigger or button)
    private val tagFlow = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    // NEW: Emit trigger press events for unified control
    private val triggerFlow = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val readers: Readers by lazy { Readers(context, ENUM_TRANSPORT.BLUETOOTH) }
    private var availableReaders: List<ReaderDevice> = emptyList()
    private var reader: RFIDReader? = null
    private var connectedReaderName: String? = null
    private var eventHandler: EventHandler? = null

    // Index into reader's transmit power level table (0 = hardware minimum)
    private var currentPowerIndex: Int = 0
    private var maxPowerIndex: Int = 200

    // Prevents emitting more than one tag per inventory session; thread-safe
    private val inventoryActive = AtomicBoolean(false)

    override fun observeStatus(): Flow<ReaderStatus> = statusFlow.asStateFlow()
    override fun observeTagScans(): Flow<String> = tagFlow.asSharedFlow()

    // NEW: Method to observe trigger presses for unified control
    override fun observeTriggerEvents(): Flow<Unit> = triggerFlow.asSharedFlow()

    // ── Event handler ────────────────────────────────────────────────────────

    private inner class EventHandler : RfidEventsListener {

        // Called on the SDK thread when a tag is decoded
        override fun eventReadNotify(e: RfidReadEvents) {
            // Only handle the first tag; compareAndSet ensures exactly-once delivery
            if (!inventoryActive.compareAndSet(true, false)) return

            // Stop immediately — we only want exactly 1 tag
            runCatching { reader?.Actions?.Inventory?.stop() }

            val tags = reader?.Actions?.getReadTags(100) ?: return
            val epc = tags.firstOrNull { !it.tagID.isNullOrBlank() }?.tagID ?: return
            tagFlow.tryEmit(epc)
        }

        // Called on the SDK thread for trigger press/release and disconnect
        override fun eventStatusNotify(e: RfidStatusEvents) {
            when (e.StatusEventData.getStatusEventType()) {
                STATUS_EVENT_TYPE.HANDHELD_TRIGGER_EVENT -> {
                    when (e.StatusEventData.HandheldTriggerEventData.getHandheldEvent()) {
                        HANDHELD_TRIGGER_EVENT_TYPE.HANDHELD_TRIGGER_PRESSED -> {
                            startInventoryInternal()
                            triggerFlow.tryEmit(Unit) // Emit trigger press event
                        }
                        HANDHELD_TRIGGER_EVENT_TYPE.HANDHELD_TRIGGER_RELEASED -> {
                            // If trigger released before a tag was read, stop inventory
                            if (inventoryActive.compareAndSet(true, false)) {
                                runCatching { reader?.Actions?.Inventory?.stop() }
                            }
                        }
                        else -> Unit
                    }
                }
                STATUS_EVENT_TYPE.DISCONNECTION_EVENT -> {
                    inventoryActive.set(false)
                    runCatching { reader?.Events?.removeEventsListener(eventHandler) }
                    reader = null
                    connectedReaderName = null
                    updateReaderStatus("Nicht verbunden")
                }
                else -> Unit
            }
        }
    }

    // ── Inventory control ────────────────────────────────────────────────────

    private fun startInventoryInternal() {
        if (!inventoryActive.compareAndSet(false, true)) return
        runCatching { reader?.Actions?.Inventory?.perform() }
            .onFailure { inventoryActive.set(false) }
    }

    // Called by the UI button (non-blocking — result arrives via observeTagScans())
    override suspend fun triggerInventory() {
        val currentReader = reader
            ?: throw IllegalStateException("Reader nicht verbunden")
        if (!currentReader.isConnected)
            throw IllegalStateException("Reader nicht verbunden")
        startInventoryInternal()
    }

    // ── Connection ───────────────────────────────────────────────────────────

    override suspend fun discoverReaders(): List<String> = withContext(Dispatchers.IO) {
        try {
            val list = readers.GetAvailableRFIDReaderList() ?: emptyList()
            availableReaders = list
            val names = list.mapNotNull { runCatching { it.name }.getOrNull() }
            updateReaderStatus(if (names.isEmpty()) "Kein Zebra-Reader gefunden" else "Reader gefunden")
            names
        } catch (e: SecurityException) {
            updateReaderStatus("Bluetooth-Berechtigung fehlt: ${e.message}")
            emptyList()
        } catch (t: Throwable) {
            updateReaderStatus("Reader-Suche fehlgeschlagen: ${t.javaClass.simpleName}: ${t.message}")
            emptyList()
        }
    }

    override suspend fun connect(readerId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (availableReaders.isEmpty())
                availableReaders = readers.GetAvailableRFIDReaderList() ?: emptyList()

            val target = availableReaders.firstOrNull {
                runCatching { it.name == readerId }.getOrDefault(false)
            } ?: throw IllegalStateException("Reader '$readerId' nicht gefunden")

            val selectedReader = target.rfidReader
                ?: throw IllegalStateException("RFIDReader-Objekt nicht verfügbar")

            if (!selectedReader.isConnected) {
                try {
                    selectedReader.connect()
                } catch (e: OperationFailureException) {
                    throw IllegalStateException(
                        "Zebra Connect fehlgeschlagen: result=${e.results}, vendor=${e.vendorMessage}"
                    )
                }
            }

            reader = selectedReader
            connectedReaderName = readerId

            runCatching { selectedReader.PostConnectReaderUpdate() }

            configureReader(selectedReader)
            updateReaderStatus("Verbunden")
        }.onFailure { e ->
            reader = null
            connectedReaderName = null
            updateReaderStatus("Verbindung fehlgeschlagen: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    override suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            try {
                reader?.let {
                    runCatching { it.Actions.Inventory.stop() }
                    eventHandler?.let { h -> runCatching { it.Events.removeEventsListener(h) } }
                    if (it.isConnected) runCatching { it.disconnect() }
                }
            } finally {
                inventoryActive.set(false)
                reader = null
                connectedReaderName = null
                updateReaderStatus("Nicht verbunden")
            }
        }
    }

    // ── Configuration ────────────────────────────────────────────────────────

    private fun configureReader(rfidReader: RFIDReader) {
        try {
            // Query actual power table so indices stay within valid range
            try {
                val levels = rfidReader.ReaderCapabilities.getTransmitPowerLevelValues()
                if (levels != null && levels.isNotEmpty()) {
                    maxPowerIndex = levels.size - 1
                    currentPowerIndex = 0
                }
            } catch (_: Exception) {}

            // Register event listener
            if (eventHandler == null) eventHandler = EventHandler()
            runCatching { rfidReader.Events.addEventsListener(eventHandler) }

            // Enable handheld trigger events
            runCatching { rfidReader.Events.setHandheldEvent(true) }
            // Enable tag read events (data delivered via eventReadNotify)
            runCatching { rfidReader.Events.setTagReadEvent(true) }
            runCatching { rfidReader.Events.setAttachTagDataWithReadEvent(false) }

            // Route physical trigger to RFID mode (not barcode scanner beam)
            runCatching { rfidReader.Config.setTriggerMode(ENUM_TRIGGER_MODE.RFID_MODE, true) }

            // Trigger config: start and stop immediately on command
            runCatching {
                val triggerInfo = TriggerInfo()
                triggerInfo.StartTrigger.setTriggerType(START_TRIGGER_TYPE.START_TRIGGER_TYPE_IMMEDIATE)
                triggerInfo.StopTrigger.setTriggerType(STOP_TRIGGER_TYPE.STOP_TRIGGER_TYPE_IMMEDIATE)
                rfidReader.Config.setStartTrigger(triggerInfo.StartTrigger)
                rfidReader.Config.setStopTrigger(triggerInfo.StopTrigger)
            }

            // DPO off for RFD8500
            if ((connectedReaderName ?: "").contains("RFD8500", ignoreCase = true)) {
                runCatching { rfidReader.Config.setDPOState(DYNAMIC_POWER_OPTIMIZATION.DISABLE) }
            }

            rfidReader.Events.setInventoryStartEvent(false)
            rfidReader.Events.setInventoryStopEvent(false)

            // Antenna power
            applyAntennaConfig(rfidReader, currentPowerIndex)

            // Singulation: single session, read all tags in state A
            val singulation = rfidReader.Config.Antennas.getSingulationControl(1)
            singulation.setSession(SESSION.SESSION_S0)
            singulation.Action.setInventoryState(INVENTORY_STATE.INVENTORY_STATE_A)
            singulation.Action.setSLFlag(SL_FLAG.SL_ALL)
            rfidReader.Config.Antennas.setSingulationControl(1, singulation)

            runCatching { rfidReader.Config.setAccessOperationWaitTimeout(1000) }
        } catch (e: InvalidUsageException) {
            updateReaderStatus("Konfigurationsfehler: ${e.message ?: e.javaClass.simpleName}")
        } catch (e: OperationFailureException) {
            updateReaderStatus("Konfigurationsfehler: result=${e.results}, vendor=${e.vendorMessage}")
        } catch (t: Throwable) {
            updateReaderStatus("Konfigurationsfehler: ${t.message ?: t.javaClass.simpleName}")
        }
    }

    // ── Power ────────────────────────────────────────────────────────────────

    override suspend fun setPower(level: Int): Result<Unit> = withContext(Dispatchers.IO) {
        val currentReader = reader
            ?: return@withContext Result.failure(IllegalStateException("Reader nicht verbunden"))

        runCatching {
            val mappedIndex = mapUiPowerToZebraIndex(level)
            currentPowerIndex = mappedIndex
            applyAntennaConfig(currentReader, mappedIndex)
            updateReaderStatus("Verbunden")
        }.onFailure { e ->
            updateReaderStatus("Power-Fehler: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun applyAntennaConfig(rfidReader: RFIDReader, powerIndex: Int) {
        val config = rfidReader.Config.Antennas.getAntennaRfConfig(1)
        config.setTransmitPowerIndex(powerIndex)
        config.setrfModeTableIndex(0)
        config.setTari(0)
        rfidReader.Config.Antennas.setAntennaRfConfig(1, config)
    }

    private fun mapUiPowerToZebraIndex(level: Int): Int {
        val clamped = level.coerceIn(1, 30)
        return (clamped - 1) * maxPowerIndex / 29
    }

    // ── Status ───────────────────────────────────────────────────────────────

    private fun updateReaderStatus(message: String) {
        val name = runCatching {
            connectedReaderName ?: reader?.hostName ?: "RFD8500"
        }.getOrDefault(connectedReaderName ?: "RFD8500")

        statusFlow.value = ReaderStatus(
            isConnected = reader?.isConnected == true,
            readerName = name,
            batteryPercent = null,
            message = message
        )
    }
}
