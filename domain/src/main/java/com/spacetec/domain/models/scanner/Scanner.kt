/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 *
 * This file is part of the SpaceTec professional automotive diagnostic system.
 */

package com.spacetec.domain.models.scanner

import java.util.Locale

/**
 * Domain model representing an OBD-II scanner/adapter device.
 *
 * This is a pure domain model with no framework dependencies, designed to be used
 * across all layers of the application. It encapsulates all information about a
 * diagnostic scanner including its connection type, capabilities, and current state.
 *
 * ## Supported Scanner Types
 *
 * The model supports various scanner types including:
 * - **ELM327**: Standard and clone variants
 * - **STN1110/STN2120**: OBDLink chipsets with enhanced features
 * - **Professional Scanners**: OBDLink MX+, EX, LX, Carista, BlueDriver
 * - **Generic OBD Adapters**: Various Bluetooth, WiFi, and USB adapters
 *
 * ## Usage Example
 *
 * ```kotlin
 * val scanner = Scanner(
 *     id = "AA:BB:CC:DD:EE:FF",
 *     name = "OBDLink MX+",
 *     connectionType = ScannerConnectionType.BLUETOOTH_CLASSIC,
 *     deviceType = ScannerDeviceType.OBDLINK_MX,
 *     address = "AA:BB:CC:DD:EE:FF"
 * )
 *
 * if (scanner.canConnect) {
 *     // Initiate connection
 * }
 * ```
 *
 * @property id Unique identifier for the scanner (typically MAC address or device ID)
 * @property name User-friendly display name for the scanner
 * @property connectionType The physical connection type (Bluetooth, WiFi, USB)
 * @property deviceType The scanner chipset/device type
 * @property address Physical address (MAC address, IP:port, or USB path)
 * @property state Current connection state of the scanner
 * @property capabilities Scanner capabilities (null until connected and queried)
 * @property firmwareVersion Firmware version string (null until queried)
 * @property lastConnected Timestamp of last successful connection (null if never connected)
 * @property signalStrength Signal strength in dBm for Bluetooth devices (null for wired)
 * @property batteryLevel Battery percentage for scanners with batteries (null if no battery)
 * @property isPaired Whether the scanner is paired with the device
 * @property isTrusted Whether the user has marked this scanner as trusted
 * @property metadata Additional key-value metadata for the scanner
 *
 * @author SpaceTec Development Team
 * @since 1.0.0
 */
data class Scanner(
    val id: String,
    val name: String,
    val connectionType: ScannerConnectionType,
    val deviceType: ScannerDeviceType,
    val address: String,
    val state: ScannerState = ScannerState.DISCONNECTED,
    val capabilities: ScannerCapabilities? = null,
    val firmwareVersion: String? = null,
    val lastConnected: Long? = null,
    val signalStrength: Int? = null,
    val batteryLevel: Int? = null,
    val isPaired: Boolean = false,
    val isTrusted: Boolean = false,
    val metadata: Map<String, String> = emptyMap()
) {
    
    // ═══════════════════════════════════════════════════════════════════════
    // COMPUTED PROPERTIES
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Indicates whether the scanner is currently connected and ready for communication.
     *
     * A scanner is considered connected when it's in either [ScannerState.CONNECTED]
     * or [ScannerState.COMMUNICATING] state.
     */
    val isConnected: Boolean
        get() = state == ScannerState.CONNECTED || state == ScannerState.COMMUNICATING
    
    /**
     * Indicates whether the scanner is currently in the process of connecting.
     *
     * This includes [ScannerState.CONNECTING], [ScannerState.INITIALIZING],
     * and [ScannerState.DETECTING_PROTOCOL] states.
     */
    val isConnecting: Boolean
        get() = state == ScannerState.CONNECTING ||
                state == ScannerState.INITIALIZING ||
                state == ScannerState.DETECTING_PROTOCOL
    
    /**
     * Indicates whether a connection attempt can be made.
     *
     * Connection can be attempted when the scanner is [ScannerState.DISCONNECTED]
     * or [ScannerState.ERROR].
     */
    val canConnect: Boolean
        get() = state == ScannerState.DISCONNECTED || state == ScannerState.ERROR
    
    /**
     * Indicates whether the scanner is in an error state.
     */
    val hasError: Boolean
        get() = state == ScannerState.ERROR
    
    /**
     * Indicates whether this scanner is a Bluetooth device.
     */
    val isBluetooth: Boolean
        get() = connectionType == ScannerConnectionType.BLUETOOTH_CLASSIC ||
                connectionType == ScannerConnectionType.BLUETOOTH_LE
    
    /**
     * Indicates whether this scanner is a wireless device (Bluetooth or WiFi).
     */
    val isWireless: Boolean
        get() = connectionType != ScannerConnectionType.USB
    
    /**
     * Indicates whether this is an ELM327 clone with potentially limited features.
     */
    val isClone: Boolean
        get() = deviceType == ScannerDeviceType.ELM327_CLONE
    
    /**
     * Indicates whether this scanner supports advanced STN commands.
     */
    val supportsSTNCommands: Boolean
        get() = deviceType in listOf(
            ScannerDeviceType.STN1110,
            ScannerDeviceType.STN2120,
            ScannerDeviceType.OBDLINK_MX,
            ScannerDeviceType.OBDLINK_EX,
            ScannerDeviceType.OBDLINK_LX
        )
    
    /**
     * Indicates whether the scanner has been connected before.
     */
    val hasConnectedBefore: Boolean
        get() = lastConnected != null
    
    /**
     * Returns a formatted display address based on connection type.
     *
     * For Bluetooth devices, formats as XX:XX:XX:XX:XX:XX.
     * For WiFi devices, shows IP:port.
     * For USB devices, prefixes with "USB:".
     */
    val displayAddress: String
        get() = when (connectionType) {
            ScannerConnectionType.BLUETOOTH_CLASSIC,
            ScannerConnectionType.BLUETOOTH_LE -> formatMacAddress(address)
            ScannerConnectionType.WIFI -> address
            ScannerConnectionType.USB -> "USB: $address"
        }
    
    /**
     * Returns a human-readable name for the device type.
     */
    val deviceTypeDisplayName: String
        get() = when (deviceType) {
            ScannerDeviceType.ELM327 -> "ELM327"
            ScannerDeviceType.ELM327_CLONE -> "ELM327 Clone"
            ScannerDeviceType.STN1110 -> "STN1110"
            ScannerDeviceType.STN2120 -> "STN2120"
            ScannerDeviceType.OBDLINK_MX -> "OBDLink MX+"
            ScannerDeviceType.OBDLINK_EX -> "OBDLink EX"
            ScannerDeviceType.OBDLINK_LX -> "OBDLink LX"
            ScannerDeviceType.VGATE_ICAR -> "vGate iCar"
            ScannerDeviceType.VEEPEAK -> "Veepeak"
            ScannerDeviceType.CARISTA -> "Carista"
            ScannerDeviceType.BLUEDRIVER -> "BlueDriver"
            ScannerDeviceType.GENERIC -> "Generic OBD Adapter"
        }
    
    /**
     * Returns a human-readable name for the connection type.
     */
    val connectionTypeDisplayName: String
        get() = when (connectionType) {
            ScannerConnectionType.BLUETOOTH_CLASSIC -> "Bluetooth"
            ScannerConnectionType.BLUETOOTH_LE -> "Bluetooth LE"
            ScannerConnectionType.WIFI -> "WiFi"
            ScannerConnectionType.USB -> "USB"
        }
    
    /**
     * Returns a human-readable description of the current state.
     */
    val stateDescription: String
        get() = when (state) {
            ScannerState.UNKNOWN -> "Unknown"
            ScannerState.DISCONNECTED -> "Disconnected"
            ScannerState.DISCOVERING -> "Discovering..."
            ScannerState.CONNECTING -> "Connecting..."
            ScannerState.INITIALIZING -> "Initializing..."
            ScannerState.DETECTING_PROTOCOL -> "Detecting Protocol..."
            ScannerState.CONNECTED -> "Connected"
            ScannerState.COMMUNICATING -> "Communicating"
            ScannerState.ERROR -> "Error"
            ScannerState.RECONNECTING -> "Reconnecting..."
        }
    
    /**
     * Returns signal strength as a percentage (0-100).
     *
     * Converts RSSI value to percentage where:
     * - -50 dBm or better = 100%
     * - -100 dBm or worse = 0%
     *
     * @return Signal strength as percentage, or null if not a Bluetooth device
     */
    val signalStrengthPercent: Int?
        get() = signalStrength?.let { rssi ->
            when {
                rssi >= -50 -> 100
                rssi <= -100 -> 0
                else -> (2 * (rssi + 100)).coerceIn(0, 100)
            }
        }
    
    /**
     * Returns a signal strength description.
     */
    val signalStrengthDescription: String?
        get() = signalStrengthPercent?.let { percent ->
            when {
                percent >= 80 -> "Excellent"
                percent >= 60 -> "Good"
                percent >= 40 -> "Fair"
                percent >= 20 -> "Weak"
                else -> "Very Weak"
            }
        }
    
    /**
     * Returns full display name including device type.
     */
    val fullDisplayName: String
        get() = buildString {
            append(name)
            if (name != deviceTypeDisplayName && deviceType != ScannerDeviceType.GENERIC) {
                append(" ($deviceTypeDisplayName)")
            }
        }
    
    // ═══════════════════════════════════════════════════════════════════════
    // VALIDATION
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Validates that the scanner has required fields populated.
     *
     * @return true if the scanner data is valid
     */
    fun isValid(): Boolean {
        return id.isNotBlank() &&
               name.isNotBlank() &&
               address.isNotBlank() &&
               isValidAddress()
    }
    
    /**
     * Validates the address format based on connection type.
     *
     * @return true if the address format is valid
     */
    fun isValidAddress(): Boolean {
        return when (connectionType) {
            ScannerConnectionType.BLUETOOTH_CLASSIC,
            ScannerConnectionType.BLUETOOTH_LE -> isValidMacAddress(address)
            ScannerConnectionType.WIFI -> isValidIpAddress(address)
            ScannerConnectionType.USB -> address.isNotBlank()
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // COMPANION OBJECT
    // ═══════════════════════════════════════════════════════════════════════
    
    companion object {
        
        /**
         * Common Bluetooth name prefixes for OBD adapters.
         */
        val OBD_NAME_PREFIXES = listOf(
            "OBD", "ELM", "VLink", "OBDII", "Vgate", "iCar",
            "Veepeak", "Carista", "BlueDriver", "LELink", "Konnwei",
            "Scan", "Diagnos", "Car", "Auto", "Vehicle"
        )
        
        /**
         * Standard SPP UUID for Bluetooth OBD adapters.
         */
        const val SPP_UUID = "00001101-0000-1000-8000-00805F9B34FB"
        
        /**
         * Default WiFi port for OBD adapters.
         */
        const val DEFAULT_WIFI_PORT = 35000
        
        /**
         * Default connection timeout in milliseconds.
         */
        const val DEFAULT_CONNECTION_TIMEOUT_MS = 10_000L
        
        /**
         * Creates a Scanner from a discovered Bluetooth device.
         *
         * @param name Device name
         * @param address MAC address
         * @param rssi Signal strength
         * @param isBLE Whether this is a BLE device
         * @return Scanner instance
         */
        fun fromBluetoothDevice(
            name: String?,
            address: String,
            rssi: Int? = null,
            isBLE: Boolean = false
        ): Scanner {
            val displayName = name ?: "Unknown Device"
            val deviceType = detectDeviceType(displayName)
            
            return Scanner(
                id = address,
                name = displayName,
                connectionType = if (isBLE) {
                    ScannerConnectionType.BLUETOOTH_LE
                } else {
                    ScannerConnectionType.BLUETOOTH_CLASSIC
                },
                deviceType = deviceType,
                address = address,
                signalStrength = rssi
            )
        }
        
        /**
         * Creates a Scanner for a WiFi device.
         *
         * @param name Device name
         * @param ipAddress IP address
         * @param port Port number
         * @return Scanner instance
         */
        fun fromWiFiDevice(
            name: String,
            ipAddress: String,
            port: Int = DEFAULT_WIFI_PORT
        ): Scanner {
            val address = "$ipAddress:$port"
            val deviceType = detectDeviceType(name)
            
            return Scanner(
                id = address,
                name = name,
                connectionType = ScannerConnectionType.WIFI,
                deviceType = deviceType,
                address = address
            )
        }
        
        /**
         * Creates a Scanner for a USB device.
         *
         * @param name Device name
         * @param devicePath USB device path
         * @param vendorId USB vendor ID
         * @param productId USB product ID
         * @return Scanner instance
         */
        fun fromUSBDevice(
            name: String,
            devicePath: String,
            vendorId: Int,
            productId: Int
        ): Scanner {
            val id = "$vendorId:$productId:$devicePath"
            val deviceType = detectDeviceTypeFromUSB(vendorId, productId)
            
            return Scanner(
                id = id,
                name = name,
                connectionType = ScannerConnectionType.USB,
                deviceType = deviceType,
                address = devicePath,
                metadata = mapOf(
                    "vendorId" to vendorId.toString(),
                    "productId" to productId.toString()
                )
            )
        }
        
        /**
         * Detects device type from device name.
         *
         * @param name Device name
         * @return Detected device type
         */
        fun detectDeviceType(name: String): ScannerDeviceType {
            val upperName = name.uppercase(Locale.ROOT)
            
            return when {
                upperName.contains("OBDLINK MX") || 
                upperName.contains("MX+") -> ScannerDeviceType.OBDLINK_MX
                
                upperName.contains("OBDLINK EX") -> ScannerDeviceType.OBDLINK_EX
                
                upperName.contains("OBDLINK LX") || 
                upperName.contains("OBDLINK SX") -> ScannerDeviceType.OBDLINK_LX
                
                upperName.contains("STN1110") -> ScannerDeviceType.STN1110
                
                upperName.contains("STN2120") -> ScannerDeviceType.STN2120
                
                upperName.contains("VGATE") || 
                upperName.contains("ICAR") -> ScannerDeviceType.VGATE_ICAR
                
                upperName.contains("VEEPEAK") -> ScannerDeviceType.VEEPEAK
                
                upperName.contains("CARISTA") -> ScannerDeviceType.CARISTA
                
                upperName.contains("BLUEDRIVER") -> ScannerDeviceType.BLUEDRIVER
                
                upperName.contains("ELM327") || 
                upperName.contains("ELM 327") -> ScannerDeviceType.ELM327
                
                upperName.contains("OBD") ||
                upperName.contains("VLINK") ||
                upperName.contains("SCAN") -> ScannerDeviceType.ELM327_CLONE
                
                else -> ScannerDeviceType.GENERIC
            }
        }
        
        /**
         * Detects device type from USB vendor/product IDs.
         *
         * @param vendorId USB vendor ID
         * @param productId USB product ID
         * @return Detected device type
         */
        fun detectDeviceTypeFromUSB(vendorId: Int, productId: Int): ScannerDeviceType {
            // Known USB vendor/product IDs for OBD devices
            return when {
                // FTDI chips (common in quality adapters)
                vendorId == 0x0403 -> ScannerDeviceType.ELM327
                
                // OBDLink (STN chipsets)
                vendorId == 0x0403 && productId == 0x6015 -> ScannerDeviceType.OBDLINK_EX
                
                // CH340 (common in clones)
                vendorId == 0x1A86 -> ScannerDeviceType.ELM327_CLONE
                
                // CP2102 (common in clones)
                vendorId == 0x10C4 -> ScannerDeviceType.ELM327_CLONE
                
                // PL2303 (common in clones)
                vendorId == 0x067B -> ScannerDeviceType.ELM327_CLONE
                
                else -> ScannerDeviceType.GENERIC
            }
        }
        
        /**
         * Checks if a device name looks like an OBD adapter.
         *
         * @param name Device name to check
         * @return true if the name suggests an OBD adapter
         */
        fun looksLikeOBDAdapter(name: String?): Boolean {
            if (name.isNullOrBlank()) return false
            
            val upperName = name.uppercase(Locale.ROOT)
            return OBD_NAME_PREFIXES.any { prefix ->
                upperName.contains(prefix.uppercase(Locale.ROOT))
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// ENUMS
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Types of physical connections for OBD scanners.
 *
 * Different connection types have different characteristics:
 * - **BLUETOOTH_CLASSIC**: Most common, uses SPP/RFCOMM profile
 * - **BLUETOOTH_LE**: Lower power, used by newer adapters
 * - **WIFI**: TCP/IP connection, typically faster
 * - **USB**: Wired connection, most reliable
 *
 * @property displayName Human-readable name for the connection type
 */
enum class ScannerConnectionType(val displayName: String) {
    /**
     * Bluetooth Classic using SPP (Serial Port Profile) / RFCOMM.
     * Most common connection type for OBD adapters.
     */
    BLUETOOTH_CLASSIC("Bluetooth"),
    
    /**
     * Bluetooth Low Energy using GATT.
     * Lower power consumption, used by newer adapters.
     */
    BLUETOOTH_LE("Bluetooth LE"),
    
    /**
     * WiFi using TCP/IP socket connection.
     * Typically offers higher throughput.
     */
    WIFI("WiFi"),
    
    /**
     * USB wired connection using serial communication.
     * Most reliable with lowest latency.
     */
    USB("USB");
    
    /**
     * Indicates whether this connection type is wireless.
     */
    val isWireless: Boolean
        get() = this != USB
    
    /**
     * Indicates whether this is a Bluetooth connection type.
     */
    val isBluetooth: Boolean
        get() = this == BLUETOOTH_CLASSIC || this == BLUETOOTH_LE
}

/**
 * Types of OBD scanner devices/chipsets.
 *
 * Different chipsets have different capabilities and command sets:
 * - **ELM327**: Standard ELM chipset with AT commands
 * - **STN11xx/STN21xx**: OBDLink chipsets with extended ST commands
 * - **Clones**: ELM327 clones with potentially limited functionality
 *
 * @property displayName Human-readable name for the device type
 * @property supportsExtendedCommands Whether the device supports STN extended commands
 * @property reliabilityRating Reliability rating (1-5, 5 being most reliable)
 */
enum class ScannerDeviceType(
    val displayName: String,
    val supportsExtendedCommands: Boolean,
    val reliabilityRating: Int
) {
    /**
     * Genuine ELM327 chipset.
     * Standard AT command set, reliable.
     */
    ELM327("ELM327", false, 4),
    
    /**
     * ELM327 clone/counterfeit.
     * May have limited functionality or bugs.
     */
    ELM327_CLONE("ELM327 Clone", false, 2),
    
    /**
     * STN1110 chipset (OBDLink original).
     * Supports ST extended commands, reliable.
     */
    STN1110("STN1110", true, 5),
    
    /**
     * STN2120 chipset (OBDLink next-gen).
     * Full ST command support, CAN FD capable.
     */
    STN2120("STN2120", true, 5),
    
    /**
     * OBDLink MX+ professional adapter.
     * Bluetooth, STN chipset, very reliable.
     */
    OBDLINK_MX("OBDLink MX+", true, 5),
    
    /**
     * OBDLink EX USB adapter.
     * USB connection, STN chipset, professional grade.
     */
    OBDLINK_EX("OBDLink EX", true, 5),
    
    /**
     * OBDLink LX Bluetooth adapter.
     * Bluetooth, STN chipset, consumer grade.
     */
    OBDLINK_LX("OBDLink LX", true, 4),
    
    /**
     * vGate iCar series adapter.
     * Bluetooth, enhanced ELM, consumer grade.
     */
    VGATE_ICAR("vGate iCar", false, 3),
    
    /**
     * Veepeak adapter series.
     * Bluetooth/BLE, ELM-based, consumer grade.
     */
    VEEPEAK("Veepeak", false, 3),
    
    /**
     * Carista adapter.
     * Bluetooth, enhanced features, consumer grade.
     */
    CARISTA("Carista", false, 4),
    
    /**
     * BlueDriver adapter.
     * Bluetooth, enhanced features, consumer grade.
     */
    BLUEDRIVER("BlueDriver", false, 4),
    
    /**
     * Unknown/Generic adapter.
     * Unknown chipset, treat with caution.
     */
    GENERIC("Generic", false, 1);
    
    /**
     * Indicates whether this is a professional-grade device.
     */
    val isProfessional: Boolean
        get() = reliabilityRating >= 4 && supportsExtendedCommands
    
    /**
     * Indicates whether this device is likely to be a clone.
     */
    val isLikelyClone: Boolean
        get() = this == ELM327_CLONE || this == GENERIC
}

/**
 * Scanner connection state.
 *
 * Represents the current state in the scanner connection lifecycle.
 * States follow a typical state machine pattern from DISCONNECTED through
 * various connecting states to CONNECTED.
 *
 * ## State Machine
 *
 * ```
 *                    ┌─────────────────┐
 *                    │   DISCONNECTED  │◀──────────────────────┐
 *                    └────────┬────────┘                       │
 *                             │ connect()                      │
 *                             ▼                                │
 *                    ┌─────────────────┐                       │
 *                    │   CONNECTING    │───────────────────────┤
 *                    └────────┬────────┘    timeout/error      │
 *                             │ socket connected               │
 *                             ▼                                │
 *                    ┌─────────────────┐                       │
 *                    │  INITIALIZING   │───────────────────────┤
 *                    └────────┬────────┘    init failed        │
 *                             │ ATZ, ATE0, etc.                │
 *                             ▼                                │
 *                    ┌─────────────────┐                       │
 *                    │DETECTING_PROTOCOL│──────────────────────┤
 *                    └────────┬────────┘    no protocol        │
 *                             │ ATSP0, 0100                    │
 *                             ▼                                │
 *                    ┌─────────────────┐                       │
 *                    │   CONNECTED     │◀──────────┐           │
 *                    └────────┬────────┘           │           │
 *                             │                    │           │
 *          ┌──────────────────┼──────────────────┐ │           │
 *          │                  │                  │ │           │
 *          ▼                  ▼                  ▼ │           │
 * ┌────────────────┐ ┌────────────────┐ ┌────────────────┐    │
 * │ COMMUNICATING  │ │  RECONNECTING  │ │    ERROR       │────┘
 * └────────────────┘ └────────────────┘ └────────────────┘
 * ```
 *
 * @property displayName Human-readable name for the state
 * @property isTerminal Whether this is a terminal state (no automatic transitions)
 */
enum class ScannerState(val displayName: String, val isTerminal: Boolean) {
    /**
     * State is unknown.
     * Initial state before any discovery.
     */
    UNKNOWN("Unknown", false),
    
    /**
     * Scanner is not connected.
     * Ready for connection attempt.
     */
    DISCONNECTED("Disconnected", true),
    
    /**
     * Discovering/scanning for devices.
     * Actively searching for scanners.
     */
    DISCOVERING("Discovering", false),
    
    /**
     * Establishing physical connection.
     * Socket/port connection in progress.
     */
    CONNECTING("Connecting", false),
    
    /**
     * Connection established, initializing adapter.
     * Sending AT commands to configure adapter.
     */
    INITIALIZING("Initializing", false),
    
    /**
     * Detecting vehicle protocol.
     * Auto-detecting CAN/K-Line/etc.
     */
    DETECTING_PROTOCOL("Detecting Protocol", false),
    
    /**
     * Fully connected and ready.
     * Can send OBD commands.
     */
    CONNECTED("Connected", true),
    
    /**
     * Actively communicating with vehicle.
     * OBD commands in flight.
     */
    COMMUNICATING("Communicating", false),
    
    /**
     * Error occurred.
     * Requires disconnect or recovery.
     */
    ERROR("Error", true),
    
    /**
     * Attempting to reconnect.
     * After connection loss.
     */
    RECONNECTING("Reconnecting", false);
    
    /**
     * Indicates whether the scanner is in a connected state.
     */
    val isConnected: Boolean
        get() = this == CONNECTED || this == COMMUNICATING
    
    /**
     * Indicates whether a connection is in progress.
     */
    val isConnecting: Boolean
        get() = this == CONNECTING || this == INITIALIZING ||
                this == DETECTING_PROTOCOL || this == RECONNECTING
    
    /**
     * Indicates whether a new connection can be attempted.
     */
    val canConnect: Boolean
        get() = this == DISCONNECTED || this == ERROR || this == UNKNOWN
}

// ═══════════════════════════════════════════════════════════════════════════
// DATA CLASSES
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Scanner capabilities detected during initialization.
 *
 * Represents the features and protocols supported by the scanner hardware.
 * This information is typically gathered by querying the adapter's
 * firmware and capabilities.
 *
 * @property supportedProtocols Set of vehicle protocols the scanner supports
 * @property maxBaudRate Maximum baud rate supported by the adapter
 * @property supportsCanFD Whether the scanner supports CAN FD (Flexible Data-Rate)
 * @property supportsJ1939 Whether the scanner supports SAE J1939 (heavy-duty vehicles)
 * @property supportsSecurityAccess Whether the scanner supports UDS security access
 * @property supportsFlashing Whether the scanner supports ECU programming/flashing
 * @property hasBattery Whether the scanner has an internal battery
 * @property hasLED Whether the scanner has status LEDs
 * @property firmwareVersion Firmware version string
 */
data class ScannerCapabilities(
    val supportedProtocols: Set<ProtocolType>,
    val maxBaudRate: Int,
    val supportsCanFD: Boolean = false,
    val supportsJ1939: Boolean = false,
    val supportsSecurityAccess: Boolean = false,
    val supportsFlashing: Boolean = false,
    val hasBattery: Boolean = false,
    val hasLED: Boolean = true,
    val firmwareVersion: String? = null
) {
    
    /**
     * Indicates whether the scanner supports CAN protocols.
     */
    val supportsCAN: Boolean
        get() = supportedProtocols.any { it.isCAN }
    
    /**
     * Indicates whether the scanner supports K-Line protocols.
     */
    val supportsKLine: Boolean
        get() = supportedProtocols.any { it.isKLine }
    
    /**
     * Indicates whether the scanner supports J1850 protocols.
     */
    val supportsJ1850: Boolean
        get() = supportedProtocols.any { it.isJ1850 }
    
    /**
     * Indicates whether the scanner supports all standard OBD-II protocols.
     */
    val supportsAllOBD2Protocols: Boolean
        get() = supportsCAN && supportsKLine && supportsJ1850
    
    /**
     * Indicates whether this is a professional-grade scanner.
     */
    val isProfessional: Boolean
        get() = supportsSecurityAccess || supportsFlashing
    
    companion object {
        /**
         * Default capabilities for unknown scanners.
         */
        val UNKNOWN = ScannerCapabilities(
            supportedProtocols = emptySet(),
            maxBaudRate = 38400
        )
        
        /**
         * Standard ELM327 capabilities.
         */
        val ELM327_STANDARD = ScannerCapabilities(
            supportedProtocols = setOf(
                ProtocolType.AUTO,
                ProtocolType.SAE_J1850_PWM,
                ProtocolType.SAE_J1850_VPW,
                ProtocolType.ISO_9141_2,
                ProtocolType.ISO_14230_4_KWP_5BAUD,
                ProtocolType.ISO_14230_4_KWP_FAST,
                ProtocolType.ISO_15765_4_CAN_11BIT_500K,
                ProtocolType.ISO_15765_4_CAN_29BIT_500K,
                ProtocolType.ISO_15765_4_CAN_11BIT_250K,
                ProtocolType.ISO_15765_4_CAN_29BIT_250K
            ),
            maxBaudRate = 500000
        )
        
        /**
         * OBDLink/STN capabilities.
         */
        val OBDLINK_STN = ScannerCapabilities(
            supportedProtocols = setOf(
                ProtocolType.AUTO,
                ProtocolType.SAE_J1850_PWM,
                ProtocolType.SAE_J1850_VPW,
                ProtocolType.ISO_9141_2,
                ProtocolType.ISO_14230_4_KWP_5BAUD,
                ProtocolType.ISO_14230_4_KWP_FAST,
                ProtocolType.ISO_15765_4_CAN_11BIT_500K,
                ProtocolType.ISO_15765_4_CAN_29BIT_500K,
                ProtocolType.ISO_15765_4_CAN_11BIT_250K,
                ProtocolType.ISO_15765_4_CAN_29BIT_250K,
                ProtocolType.SAE_J1939_CAN
            ),
            maxBaudRate = 500000,
            supportsJ1939 = true,
            supportsSecurityAccess = true,
            supportsFlashing = true
        )
    }
}

/**
 * Scanner information retrieved from the device.
 *
 * Contains detailed information about the scanner obtained through
 * AT commands during initialization.
 *
 * @property deviceType Detected device type
 * @property firmwareVersion Firmware version string
 * @property voltage Current vehicle voltage (if supported)
 * @property protocolDescription Description of the detected protocol
 * @property capabilities Full capabilities of the scanner
 */
data class ScannerInfo(
    val deviceType: ScannerDeviceType,
    val firmwareVersion: String,
    val voltage: Float? = null,
    val protocolDescription: String? = null,
    val capabilities: ScannerCapabilities
) {
    
    /**
     * Indicates whether the vehicle appears to have ignition on.
     * Based on voltage reading (typically > 12.5V indicates engine running).
     */
    val isIgnitionOn: Boolean
        get() = voltage?.let { it > 12.0f } ?: false
    
    /**
     * Indicates whether the engine appears to be running.
     * Based on voltage reading (typically > 13.5V indicates charging/running).
     */
    val isEngineRunning: Boolean
        get() = voltage?.let { it > 13.5f } ?: false
    
    /**
     * Returns battery status description based on voltage.
     */
    val batteryStatus: String
        get() = when {
            voltage == null -> "Unknown"
            voltage < 11.5f -> "Low"
            voltage < 12.0f -> "Weak"
            voltage < 12.6f -> "Normal (Engine Off)"
            voltage < 13.5f -> "Normal (Ignition On)"
            voltage < 14.7f -> "Charging"
            else -> "Overcharging"
        }
    
    companion object {
        /**
         * Creates ScannerInfo from version string and voltage reading.
         *
         * @param versionResponse Response from ATI command
         * @param voltageResponse Response from ATRV command
         * @return Parsed ScannerInfo
         */
        fun fromResponses(
            versionResponse: String,
            voltageResponse: String? = null
        ): ScannerInfo {
            val (deviceType, version) = parseVersionResponse(versionResponse)
            val voltage = voltageResponse?.let { parseVoltage(it) }
            
            val capabilities = when (deviceType) {
                ScannerDeviceType.STN1110,
                ScannerDeviceType.STN2120,
                ScannerDeviceType.OBDLINK_MX,
                ScannerDeviceType.OBDLINK_EX,
                ScannerDeviceType.OBDLINK_LX -> ScannerCapabilities.OBDLINK_STN
                ScannerDeviceType.ELM327 -> ScannerCapabilities.ELM327_STANDARD
                else -> ScannerCapabilities.UNKNOWN
            }
            
            return ScannerInfo(
                deviceType = deviceType,
                firmwareVersion = version,
                voltage = voltage,
                protocolDescription = null,
                capabilities = capabilities.copy(firmwareVersion = version)
            )
        }
        
        private fun parseVersionResponse(response: String): Pair<ScannerDeviceType, String> {
            val upperResponse = response.uppercase(Locale.ROOT)
            
            // Try to extract version pattern
            val elmPattern = Regex("ELM(\\d+)\\s*[Vv]?([\\d.]+)")
            val stnPattern = Regex("STN(\\d+)\\s*[Vv]?([\\d.]+)")
            
            elmPattern.find(upperResponse)?.let { match ->
                return ScannerDeviceType.ELM327 to "ELM${match.groupValues[1]} v${match.groupValues[2]}"
            }
            
            stnPattern.find(upperResponse)?.let { match ->
                val chipNumber = match.groupValues[1]
                val version = match.groupValues[2]
                val type = when {
                    chipNumber.startsWith("11") -> ScannerDeviceType.STN1110
                    chipNumber.startsWith("21") -> ScannerDeviceType.STN2120
                    else -> ScannerDeviceType.STN1110
                }
                return type to "STN$chipNumber v$version"
            }
            
            // Fallback
            return ScannerDeviceType.GENERIC to response.trim()
        }
        
        private fun parseVoltage(response: String): Float? {
            val voltagePattern = Regex("(\\d+\\.?\\d*)\\s*[Vv]?")
            return voltagePattern.find(response)?.groupValues?.get(1)?.toFloatOrNull()
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// PROTOCOL TYPE ENUM
// ═══════════════════════════════════════════════════════════════════════════

/**
 * OBD-II and vehicle communication protocol types.
 *
 * Represents the various protocols supported by OBD-II vehicles and
 * diagnostic tools. Each protocol has specific characteristics
 * regarding speed, addressing, and physical layer.
 *
 * ## Protocol Categories
 *
 * - **CAN (Controller Area Network)**: Modern protocol used in vehicles since ~2008
 * - **K-Line (ISO 9141/14230)**: Serial protocol used in European and Asian vehicles
 * - **J1850**: Protocol used in older American vehicles
 *
 * @property code ELM327 protocol code (0-12)
 * @property description Human-readable protocol description
 */
enum class ProtocolType(val code: Int, val description: String) {
    /**
     * Automatic protocol detection.
     * Scanner will try all protocols.
     */
    AUTO(0, "Automatic"),
    
    /**
     * SAE J1850 PWM (Pulse Width Modulation).
     * Used in older Ford vehicles, 41.6 kbaud.
     */
    SAE_J1850_PWM(1, "SAE J1850 PWM (41.6 kbaud)"),
    
    /**
     * SAE J1850 VPW (Variable Pulse Width).
     * Used in older GM vehicles, 10.4 kbaud.
     */
    SAE_J1850_VPW(2, "SAE J1850 VPW (10.4 kbaud)"),
    
    /**
     * ISO 9141-2 with 5-baud initialization.
     * Used in older European and Asian vehicles.
     */
    ISO_9141_2(3, "ISO 9141-2 (5 baud init)"),
    
    /**
     * ISO 14230-4 KWP2000 with 5-baud initialization.
     * Keyword Protocol used in European vehicles.
     */
    ISO_14230_4_KWP_5BAUD(4, "ISO 14230-4 KWP (5 baud init)"),
    
    /**
     * ISO 14230-4 KWP2000 with fast initialization.
     * Faster version of KWP2000.
     */
    ISO_14230_4_KWP_FAST(5, "ISO 14230-4 KWP (fast init)"),
    
    /**
     * ISO 15765-4 CAN with 11-bit ID at 500 kbaud.
     * Most common modern protocol.
     */
    ISO_15765_4_CAN_11BIT_500K(6, "ISO 15765-4 CAN (11 bit ID, 500 kbaud)"),
    
    /**
     * ISO 15765-4 CAN with 29-bit ID at 500 kbaud.
     * Extended addressing CAN.
     */
    ISO_15765_4_CAN_29BIT_500K(7, "ISO 15765-4 CAN (29 bit ID, 500 kbaud)"),
    
    /**
     * ISO 15765-4 CAN with 11-bit ID at 250 kbaud.
     * Lower speed CAN variant.
     */
    ISO_15765_4_CAN_11BIT_250K(8, "ISO 15765-4 CAN (11 bit ID, 250 kbaud)"),
    
    /**
     * ISO 15765-4 CAN with 29-bit ID at 250 kbaud.
     * Lower speed extended addressing CAN.
     */
    ISO_15765_4_CAN_29BIT_250K(9, "ISO 15765-4 CAN (29 bit ID, 250 kbaud)"),
    
    /**
     * SAE J1939 CAN for heavy-duty vehicles.
     * 29-bit ID at 250 kbaud.
     */
    SAE_J1939_CAN(10, "SAE J1939 CAN (29 bit ID, 250 kbaud)"),
    
    /**
     * User-defined CAN protocol 1.
     * 11-bit ID with user-specified baud rate.
     */
    USER_CAN_1(11, "User1 CAN (11 bit ID, user baud)"),
    
    /**
     * User-defined CAN protocol 2.
     * 11-bit ID with user-specified baud rate.
     */
    USER_CAN_2(12, "User2 CAN (11 bit ID, user baud)"),
    
    /**
     * Unknown or unsupported protocol.
     */
    UNKNOWN(-1, "Unknown");
    
    /**
     * Indicates whether this is a CAN-based protocol.
     */
    val isCAN: Boolean
        get() = this in listOf(
            ISO_15765_4_CAN_11BIT_500K,
            ISO_15765_4_CAN_29BIT_500K,
            ISO_15765_4_CAN_11BIT_250K,
            ISO_15765_4_CAN_29BIT_250K,
            SAE_J1939_CAN,
            USER_CAN_1,
            USER_CAN_2
        )
    
    /**
     * Indicates whether this is a K-Line protocol.
     */
    val isKLine: Boolean
        get() = this in listOf(
            ISO_9141_2,
            ISO_14230_4_KWP_5BAUD,
            ISO_14230_4_KWP_FAST
        )
    
    /**
     * Indicates whether this is a J1850 protocol.
     */
    val isJ1850: Boolean
        get() = this in listOf(
            SAE_J1850_PWM,
            SAE_J1850_VPW
        )
    
    /**
     * Indicates whether this protocol uses 29-bit CAN IDs.
     */
    val isExtendedCAN: Boolean
        get() = this in listOf(
            ISO_15765_4_CAN_29BIT_500K,
            ISO_15765_4_CAN_29BIT_250K,
            SAE_J1939_CAN
        )
    
    /**
     * Returns the baud rate for this protocol.
     */
    val baudRate: Int
        get() = when (this) {
            SAE_J1850_PWM -> 41600
            SAE_J1850_VPW -> 10400
            ISO_9141_2, ISO_14230_4_KWP_5BAUD, ISO_14230_4_KWP_FAST -> 10400
            ISO_15765_4_CAN_11BIT_500K, ISO_15765_4_CAN_29BIT_500K -> 500000
            ISO_15765_4_CAN_11BIT_250K, ISO_15765_4_CAN_29BIT_250K, SAE_J1939_CAN -> 250000
            else -> 0
        }
    
    /**
     * Returns the ELM327 AT command to select this protocol.
     */
    val atCommand: String
        get() = if (code >= 0) "ATSP${code.toString(16).uppercase()}" else "ATSP0"
    
    /**
     * Short display name for the protocol.
     */
    val shortName: String
        get() = when (this) {
            AUTO -> "Auto"
            SAE_J1850_PWM -> "J1850 PWM"
            SAE_J1850_VPW -> "J1850 VPW"
            ISO_9141_2 -> "ISO 9141"
            ISO_14230_4_KWP_5BAUD -> "KWP Slow"
            ISO_14230_4_KWP_FAST -> "KWP Fast"
            ISO_15765_4_CAN_11BIT_500K -> "CAN 11/500"
            ISO_15765_4_CAN_29BIT_500K -> "CAN 29/500"
            ISO_15765_4_CAN_11BIT_250K -> "CAN 11/250"
            ISO_15765_4_CAN_29BIT_250K -> "CAN 29/250"
            SAE_J1939_CAN -> "J1939"
            USER_CAN_1 -> "User CAN 1"
            USER_CAN_2 -> "User CAN 2"
            UNKNOWN -> "Unknown"
        }
    
    companion object {
        /**
         * Gets protocol type from ELM327 protocol code.
         *
         * @param code Protocol code (0-12)
         * @return Matching ProtocolType or UNKNOWN
         */
        fun fromCode(code: Int): ProtocolType {
            return values().find { it.code == code } ?: UNKNOWN
        }
        
        /**
         * Gets protocol type from protocol description string.
         *
         * @param description Protocol description (from ATDP response)
         * @return Matching ProtocolType or UNKNOWN
         */
        fun fromDescription(description: String): ProtocolType {
            val upper = description.uppercase(Locale.ROOT)
            
            return when {
                upper.contains("AUTO") -> AUTO
                upper.contains("J1850") && upper.contains("PWM") -> SAE_J1850_PWM
                upper.contains("J1850") && upper.contains("VPW") -> SAE_J1850_VPW
                upper.contains("9141") -> ISO_9141_2
                upper.contains("14230") && upper.contains("SLOW") -> ISO_14230_4_KWP_5BAUD
                upper.contains("14230") && upper.contains("FAST") -> ISO_14230_4_KWP_FAST
                upper.contains("14230") -> ISO_14230_4_KWP_FAST  // Default to fast
                upper.contains("15765") || upper.contains("CAN") -> {
                    when {
                        upper.contains("29") && upper.contains("500") -> ISO_15765_4_CAN_29BIT_500K
                        upper.contains("29") && upper.contains("250") -> ISO_15765_4_CAN_29BIT_250K
                        upper.contains("11") && upper.contains("250") -> ISO_15765_4_CAN_11BIT_250K
                        upper.contains("29") -> ISO_15765_4_CAN_29BIT_500K
                        else -> ISO_15765_4_CAN_11BIT_500K  // Most common
                    }
                }
                upper.contains("J1939") -> SAE_J1939_CAN
                else -> UNKNOWN
            }
        }
        
        /**
         * All standard OBD-II protocols.
         */
        val OBD2_PROTOCOLS = listOf(
            ISO_15765_4_CAN_11BIT_500K,
            ISO_15765_4_CAN_29BIT_500K,
            ISO_15765_4_CAN_11BIT_250K,
            ISO_15765_4_CAN_29BIT_250K,
            ISO_14230_4_KWP_FAST,
            ISO_14230_4_KWP_5BAUD,
            ISO_9141_2,
            SAE_J1850_VPW,
            SAE_J1850_PWM
        )
        
        /**
         * CAN protocols only.
         */
        val CAN_PROTOCOLS = listOf(
            ISO_15765_4_CAN_11BIT_500K,
            ISO_15765_4_CAN_29BIT_500K,
            ISO_15765_4_CAN_11BIT_250K,
            ISO_15765_4_CAN_29BIT_250K
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// EXTENSION FUNCTIONS
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Creates a copy of the scanner with updated state.
 *
 * @param newState New scanner state
 * @return Scanner with updated state
 */
fun Scanner.withState(newState: ScannerState): Scanner {
    return copy(state = newState)
}

/**
 * Creates a copy of the scanner with updated capabilities.
 *
 * @param capabilities New capabilities
 * @return Scanner with updated capabilities
 */
fun Scanner.withCapabilities(capabilities: ScannerCapabilities): Scanner {
    return copy(capabilities = capabilities)
}

/**
 * Creates a copy of the scanner with updated firmware version.
 *
 * @param version Firmware version string
 * @return Scanner with updated firmware version
 */
fun Scanner.withFirmwareVersion(version: String): Scanner {
    return copy(firmwareVersion = version)
}

/**
 * Creates a copy of the scanner marked as connected.
 *
 * @return Scanner with connected state and updated timestamp
 */
fun Scanner.asConnected(): Scanner {
    return copy(
        state = ScannerState.CONNECTED,
        lastConnected = System.currentTimeMillis()
    )
}

/**
 * Creates a copy of the scanner marked as disconnected.
 *
 * @return Scanner with disconnected state
 */
fun Scanner.asDisconnected(): Scanner {
    return copy(state = ScannerState.DISCONNECTED)
}

/**
 * Creates a copy of the scanner with error state.
 *
 * @return Scanner with error state
 */
fun Scanner.asError(): Scanner {
    return copy(state = ScannerState.ERROR)
}

/**
 * Creates a copy of the scanner marked as paired.
 *
 * @return Scanner marked as paired
 */
fun Scanner.asPaired(): Scanner {
    return copy(isPaired = true)
}

/**
 * Creates a copy of the scanner marked as trusted.
 *
 * @return Scanner marked as trusted
 */
fun Scanner.asTrusted(): Scanner {
    return copy(isTrusted = true)
}

/**
 * Updates scanner with info from device response.
 *
 * @param info Scanner info from device
 * @return Updated scanner
 */
fun Scanner.withInfo(info: ScannerInfo): Scanner {
    return copy(
        deviceType = info.deviceType,
        firmwareVersion = info.firmwareVersion,
        capabilities = info.capabilities
    )
}

/**
 * Adds metadata to the scanner.
 *
 * @param key Metadata key
 * @param value Metadata value
 * @return Scanner with added metadata
 */
fun Scanner.withMetadata(key: String, value: String): Scanner {
    return copy(metadata = metadata + (key to value))
}

/**
 * Adds multiple metadata entries to the scanner.
 *
 * @param entries Map of metadata entries
 * @return Scanner with added metadata
 */
fun Scanner.withMetadata(entries: Map<String, String>): Scanner {
    return copy(metadata = metadata + entries)
}

// ═══════════════════════════════════════════════════════════════════════════
// UTILITY FUNCTIONS
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Formats a MAC address with colons.
 *
 * @param address MAC address (with or without delimiters)
 * @return Formatted MAC address (XX:XX:XX:XX:XX:XX)
 */
private fun formatMacAddress(address: String): String {
    // Remove existing delimiters
    val clean = address.replace(Regex("[:-]"), "").uppercase(Locale.ROOT)
    
    // Add colons every 2 characters
    return if (clean.length == 12) {
        clean.chunked(2).joinToString(":")
    } else {
        address.uppercase(Locale.ROOT)
    }
}

/**
 * Validates a MAC address format.
 *
 * @param address Address to validate
 * @return true if valid MAC address format
 */
private fun isValidMacAddress(address: String): Boolean {
    // Allow formats: XX:XX:XX:XX:XX:XX, XX-XX-XX-XX-XX-XX, XXXXXXXXXXXX
    val pattern = Regex(
        "^([0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}$|^[0-9A-Fa-f]{12}$"
    )
    return pattern.matches(address)
}

/**
 * Validates an IP address format (with optional port).
 *
 * @param address Address to validate (IP or IP:port)
 * @return true if valid IP address format
 */
private fun isValidIpAddress(address: String): Boolean {
    val parts = address.split(":")
    val ip = parts[0]
    val port = parts.getOrNull(1)?.toIntOrNull()
    
    // Validate IP
    val ipPattern = Regex(
        "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
    )
    if (!ipPattern.matches(ip)) return false
    
    // Validate port if present
    if (port != null && (port < 1 || port > 65535)) return false
    
    return true
}

// ═══════════════════════════════════════════════════════════════════════════
// TYPE ALIASES
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Type alias for scanner ID.
 */
typealias ScannerId = String

/**
 * Type alias for MAC address.
 */
typealias MacAddress = String

/**
 * Type alias for IP address with optional port.
 */
typealias IpAddress = String
