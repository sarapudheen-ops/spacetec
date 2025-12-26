package com.spacetec.scanner.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

enum class ScannerType { BLUETOOTH, WIFI, USB, J2534 }

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Scanning : ConnectionState()
    object Connecting : ConnectionState()
    data class Connected(val device: ScannerDevice) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

data class ScannerDevice(
    val id: String,
    val name: String,
    val type: ScannerType,
    val address: String,
    val signalStrength: Int = 0
)

interface Scanner {
    val type: ScannerType
    val state: Flow<ConnectionState>
    val devices: Flow<List<ScannerDevice>>
    suspend fun startScan()
    suspend fun stopScan()
    suspend fun connect(device: ScannerDevice): Boolean
    suspend fun disconnect()
    suspend fun send(data: ByteArray): ByteArray?
}

abstract class BaseScanner(override val type: ScannerType) : Scanner {
    protected val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    protected val _devices = MutableStateFlow<List<ScannerDevice>>(emptyList())
    override val state: Flow<ConnectionState> = _state
    override val devices: Flow<List<ScannerDevice>> = _devices
}

class ScannerManager {
    private val scanners = mutableMapOf<ScannerType, Scanner>()
    private var activeScanner: Scanner? = null
    
    fun register(scanner: Scanner) { scanners[scanner.type] = scanner }
    fun getScanner(type: ScannerType) = scanners[type]
    
    suspend fun connect(type: ScannerType, device: ScannerDevice): Boolean {
        val scanner = scanners[type] ?: return false
        if (scanner.connect(device)) {
            activeScanner = scanner
            return true
        }
        return false
    }
    
    suspend fun send(data: ByteArray) = activeScanner?.send(data)
    suspend fun disconnect() { activeScanner?.disconnect(); activeScanner = null }
}
