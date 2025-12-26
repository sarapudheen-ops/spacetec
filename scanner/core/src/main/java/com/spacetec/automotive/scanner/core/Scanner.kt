// scanner/core/src/main/kotlin/com/spacetec/automotive/scanner/core/Scanner.kt
package com.spacetec.obd.scanner.core

import com.spacetec.obd.core.common.result.AppResult
import com.spacetec.transport.contract.Protocol
import com.spacetec.transport.contract.ProtocolConfig
import com.spacetec.transport.contract.ProtocolType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Base interface for all OBD-II scanner devices.
 * 
 * A Scanner represents a physical or virtual device that connects
 * to a vehicle's OBD-II port and enables diagnostic communication.
 * Common examples include ELM327-based Bluetooth/WiFi adapters,
 * USB interfaces, and professional J2534 devices.
 */
interface Scanner {
    
    /**
     * Unique identifier for this scanner instance.
     */
    val id: String
    
    /**
     * Human-readable name of the scanner.
     */
    val name: String
    
    /**
     * Type of scanner connection.
     */
    val type: ScannerType
    
    /**
     * Current connection state.
     */
    val connectionState: StateFlow<ScannerConnectionState>
    
    /**
     * Whether the scanner is currently connected and ready.
     */
    val isConnected: Boolean
    
    /**
     * Scanner capabilities and features.
     */
    val capabilities: ScannerCapabilities
    
    /**
     * Information about the connected scanner device.
     */
    val deviceInfo: StateFlow<ScannerDeviceInfo?>
    
    /**
     * Connects to the scanner device.
     * 
     * @param config Connection configuration
     * @return Success or failure result
     */
    suspend fun connect(config: ScannerConnectionConfig = ScannerConnectionConfig()): AppResult<Unit>
    
    /**
     * Disconnects from the scanner device.
     */
    suspend fun disconnect()
    
    /**
     * Initializes communication with the vehicle.
     * 
     * @param protocolConfig Protocol configuration
     * @return The active protocol or error
     */
    suspend fun initializeVehicle(
        protocolConfig: ProtocolConfig = ProtocolConfig()
    ): AppResult<Protocol>
    
    /**
     * Sends raw data to the scanner.
     * 
     * @param data Raw bytes to send
     * @return Success or failure
     */
    suspend fun sendRaw(data: ByteArray): AppResult<Unit>
    
    /**
     * Receives raw data from the scanner.
     * 
     * @param timeout Timeout in milliseconds
     * @return Received bytes or error
     */
    suspend fun receiveRaw(timeout: Long = DEFAULT_TIMEOUT): AppResult<ByteArray>
    
    /**
     * Sends a command string and receives response.
     * 
     * @param command Command string (e.g., "ATZ", "0100")
     * @param timeout Response timeout
     * @return Response string or error
     */
    suspend fun sendCommand(
        command: String,
        timeout: Long = DEFAULT_TIMEOUT
    ): AppResult<String>
    
    /**
     * Observes incoming data as a flow.
     */
    fun observeData(): Flow<ByteArray>
    
    /**
     * Resets the scanner to default state.
     */
    suspend fun reset(): AppResult<Unit>
    
    /**
     * Gets the current protocol in use.
     */
    fun getCurrentProtocol(): Protocol?
    
    /**
     * Checks if a specific protocol is supported.
     */
    fun supportsProtocol(protocol: ProtocolType): Boolean
    
    companion object {
        const val DEFAULT_TIMEOUT = 5000L
        const val FAST_TIMEOUT = 1000L
        const val SLOW_TIMEOUT = 30000L
    }
}

/**
 * Types of scanner connections.
 */
enum class ScannerType(
    val displayName: String,
    val description: String,
    val iconName: String
) {
    BLUETOOTH("Bluetooth", "Bluetooth OBD-II adapter", "bluetooth"),
    BLUETOOTH_LE("Bluetooth LE", "Bluetooth Low Energy adapter", "bluetooth"),
    WIFI("WiFi", "WiFi OBD-II adapter", "wifi"),
    USB("USB", "USB OBD-II interface", "usb"),
    J2534("J2534", "Professional J2534 PassThru device", "developer_board"),
    SIMULATOR("Simulator", "Virtual scanner for testing", "bug_report");
    
    val isBluetooth: Boolean
        get() = this == BLUETOOTH || this == BLUETOOTH_LE
    
    val isWireless: Boolean
        get() = this == BLUETOOTH || this == BLUETOOTH_LE || this == WIFI
}

/**
 * Scanner connection states.
 */
sealed class ScannerConnectionState {
    
    /** Not connected to any scanner */
    object Disconnected : ScannerConnectionState()
    
    /** Scanning for available devices */
    object Scanning : ScannerConnectionState()
    
    /** Connecting to a specific device */
    data class Connecting(val deviceName: String) : ScannerConnectionState()
    
    /** Connected to scanner, initializing */
    data class Initializing(val deviceName: String) : ScannerConnectionState()
    
    /** Fully connected and ready */
    data class Connected(
        val deviceName: String,
        val protocol: ProtocolType? = null
    ) : ScannerConnectionState()
    
    /** Connection error occurred */
    data class Error(
        val message: String,
        val cause: Throwable? = null,
        val isRecoverable: Boolean = true
    ) : ScannerConnectionState()
    
    /** Reconnecting after connection loss */
    data class Reconnecting(
        val deviceName: String,
        val attempt: Int,
        val maxAttempts: Int
    ) : ScannerConnectionState()
    
    val isConnected: Boolean
        get() = this is Connected
    
    val isConnecting: Boolean
        get() = this is Connecting || this is Initializing || this is Reconnecting
    
    val isError: Boolean
        get() = this is Error
    
    val displayStatus: String
        get() = when (this) {
            is Disconnected -> "Disconnected"
            is Scanning -> "Scanning..."
            is Connecting -> "Connecting to $deviceName..."
            is Initializing -> "Initializing $deviceName..."
            is Connected -> "Connected to $deviceName"
            is Error -> "Error: $message"
            is Reconnecting -> "Reconnecting ($attempt/$maxAttempts)..."
        }
}

/**
 * Scanner connection configuration.
 */
data class ScannerConnectionConfig(
    val autoReconnect: Boolean = true,
    val reconnectAttempts: Int = 3,
    val reconnectDelayMs: Long = 2000,
    val connectionTimeoutMs: Long = 30000,
    val keepAliveIntervalMs: Long = 5000,
    val autoProtocolDetection: Boolean = true,
    val preferredProtocol: ProtocolType = ProtocolType.AUTO
)

/**
 * Scanner device information.
 */
data class ScannerDeviceInfo(
    val deviceName: String,
    val deviceAddress: String,
    val firmwareVersion: String = "",
    val hardwareVersion: String = "",
    val serialNumber: String = "",
    val chipType: String = "",
    val voltage: Float = 0f,
    val supportedProtocols: List<ProtocolType> = emptyList(),
    val manufacturer: String = "",
    val model: String = ""
) {
    val displayName: String
        get() = if (model.isNotEmpty()) "$manufacturer $model" else deviceName
    
    val isElm327Compatible: Boolean
        get() = chipType.contains("ELM327", ignoreCase = true) ||
                firmwareVersion.contains("ELM", ignoreCase = true)
    
    val voltageDisplay: String
        get() = if (voltage > 0) "%.1fV".format(voltage) else "N/A"
}

/**
 * Scanner capabilities.
 */
data class ScannerCapabilities(
    val supportsObd2: Boolean = true,
    val supportsUds: Boolean = false,
    val supportsCanBus: Boolean = true,
    val supportsKLine: Boolean = false,
    val supportsJ1850: Boolean = false,
    val supportsBidirectional: Boolean = false,
    val supportsSecurityAccess: Boolean = false,
    val maxBaudRate: Int = 500000,
    val supportedProtocols: List<ProtocolType> = emptyList(),
    val canReadVoltage: Boolean = true,
    val canSendRawCan: Boolean = false,
    val hasAccelerometer: Boolean = false,
    val hasGps: Boolean = false,
    val supportedVehicleBrands: List<String> = emptyList()
) {
    companion object {
        val ELM327_BASIC = ScannerCapabilities(
            supportsObd2 = true,
            supportsUds = false,
            supportsCanBus = true,
            supportsKLine = true,
            supportsJ1850 = true,
            supportsBidirectional = false,
            maxBaudRate = 500000,
            supportedProtocols = listOf(
                ProtocolType.ISO_15765_4_CAN_11BIT_500K,
                ProtocolType.ISO_15765_4_CAN_29BIT_500K,
                ProtocolType.ISO_14230_4_KWP_FAST,
                ProtocolType.ISO_9141_2,
                ProtocolType.SAE_J1850_PWM,
                ProtocolType.SAE_J1850_VPW
            )
        )
        
        val ELM327_ADVANCED = ELM327_BASIC.copy(
            supportsUds = true,
            supportsBidirectional = true,
            canSendRawCan = true
        )
        
        val J2534_FULL = ScannerCapabilities(
            supportsObd2 = true,
            supportsUds = true,
            supportsCanBus = true,
            supportsKLine = true,
            supportsJ1850 = true,
            supportsBidirectional = true,
            supportsSecurityAccess = true,
            maxBaudRate = 1000000,
            canSendRawCan = true,
            supportedProtocols = ProtocolType.entries.filter { 
                it != ProtocolType.AUTO 
            }
        )
    }
}