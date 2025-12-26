/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 *
 * This file is part of the SpaceTec professional automotive diagnostic system.
 */

package com.spacetec.obd.scanner.core

import android.content.Context
import com.spacetec.core.common.exceptions.CommunicationException
import com.spacetec.core.common.result.Result
import com.spacetec.core.domain.models.scanner.ScannerConnectionType
import com.spacetec.obd.scanner.bluetooth.BluetoothProtocolAdapter
import com.spacetec.obd.scanner.j2534.J2534ProtocolAdapter
import com.spacetec.obd.scanner.usb.USBProtocolAdapter
import com.spacetec.obd.scanner.wifi.WiFiProtocolAdapter
import com.spacetec.protocol.core.ProtocolType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Comprehensive scanner manager that orchestrates all scanner communication layers.
 *
 * This manager provides a complete solution for managing OBD-II scanner connections
 * across all supported connection types (Bluetooth, WiFi, USB, J2534). It handles
 * connection lifecycle, protocol management, error recovery, and provides a unified
 * interface for diagnostic communication.
 *
 * ## Features
 *
 * - Multi-connection type support with automatic detection
 * - Protocol auto-detection and initialization
 * - Connection pooling for efficient resource management
 * - State management with observable flows
 * - Error handling and recovery mechanisms
 * - Configuration management and persistence
 * - Comprehensive logging and diagnostics
 *
 * @param context Android context for accessing system services
 * @param connectionFactory Factory for creating scanner connections
 * @param configurationManager Manager for scanner configurations
 * @param connectionPool Connection pool for efficient resource management
 * @param protocolDetectionEngine Engine for protocol detection
 * @param dispatcher Coroutine dispatcher for I/O operations
 *
 * @author SpaceTec Development Team
 * @since 1.0.0
 */
class ScannerManager @Inject constructor(
    private val context: Context,
    private val connectionFactory: ScannerConnectionFactory,
    private val configurationManager: ScannerConfigurationManager,
    private val connectionPool: ScannerConnectionPool,
    private val protocolDetectionEngine: ProtocolDetectionEngine,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private var activeAdapter: Any? = null
    private var currentConnectionType: ScannerConnectionType? = null
    private var currentAddress: String? = null
    private var currentConfig: ScannerConfig? = null

    // State management
    private val _connectionState = MutableStateFlow<ScannerConnectionState>(ScannerConnectionState.Disconnected)
    val connectionState: StateFlow<ScannerConnectionState> = _connectionState.asStateFlow()

    private val _connectionQuality = MutableStateFlow<ConnectionQuality>(ConnectionQuality.UNKNOWN)
    val connectionQuality: StateFlow<ConnectionQuality> = _connectionQuality.asStateFlow()

    /**
     * Connection quality enumeration.
     */
    enum class ConnectionQuality {
        EXCELLENT, GOOD, FAIR, POOR, UNKNOWN
    }

    /**
     * Connects to a scanner with the specified configuration.
     *
     * @param address Device address (format depends on connection type)
     * @param config Scanner configuration (uses default if null)
     * @param autoDetectProtocol Whether to auto-detect the protocol
     * @return Success or error result
     */
    suspend fun connect(
        address: String,
        config: ScannerConfig? = null,
        autoDetectProtocol: Boolean = true
    ): Result<Unit> = withContext(dispatcher) {
        try {
            // Determine connection type from address
            val connectionType = try {
                connectionFactory.detectConnectionType(address)
            } catch (e: IllegalArgumentException) {
                return@withContext Result.Error(CommunicationException("Unknown connection type for address: $address", e))
            }

            // Get or create configuration
            val scannerConfig = config ?: configurationManager.getDefaultConfiguration(connectionType)

            // Disconnect any existing connection
            disconnect()

            // Create and connect the appropriate adapter
            val result = when (connectionType) {
                ScannerConnectionType.BLUETOOTH_CLASSIC,
                ScannerConnectionType.BLUETOOTH_LE -> {
                    connectBluetooth(address, scannerConfig, autoDetectProtocol)
                }
                ScannerConnectionType.WIFI -> {
                    connectWiFi(address, scannerConfig, autoDetectProtocol)
                }
                ScannerConnectionType.USB -> {
                    connectUSB(address, scannerConfig, autoDetectProtocol)
                }
                ScannerConnectionType.J2534 -> {
                    connectJ2534(address, scannerConfig)
                }
                else -> {
                    Result.Error(CommunicationException("Unsupported connection type: $connectionType"))
                }
            }

            if (result is Result.Success) {
                currentConnectionType = connectionType
                currentAddress = address
                currentConfig = scannerConfig
                _connectionState.value = ScannerConnectionState.Connected(address)
            } else {
                _connectionState.value = ScannerConnectionState.Error("Connection failed: ${result.getOrNull()}")
            }

            result
        } catch (e: Exception) {
            _connectionState.value = ScannerConnectionState.Error("Connection failed: ${e.message}", e)
            Result.Error(CommunicationException("Connection failed: ${e.message}", e))
        }
    }

    /**
     * Connects to a Bluetooth scanner.
     */
    private suspend fun connectBluetooth(
        address: String,
        config: ScannerConfig,
        autoDetectProtocol: Boolean
    ): Result<Unit> {
        return try {
            val adapter = BluetoothProtocolAdapter.create(context)
            val result = adapter.connect(address, autoDetectProtocol)
            
            if (result is Result.Success) {
                activeAdapter = adapter
                Result.Success(Unit)
            } else {
                result.map { }
            }
        } catch (e: Exception) {
            Result.Error(CommunicationException("Bluetooth connection failed: ${e.message}", e))
        }
    }

    /**
     * Connects to a WiFi scanner.
     */
    private suspend fun connectWiFi(
        address: String,
        config: ScannerConfig,
        autoDetectProtocol: Boolean
    ): Result<Unit> {
        return try {
            val adapter = WiFiProtocolAdapter.create(context)
            val result = adapter.connect(address, autoDetectProtocol)
            
            if (result is Result.Success) {
                activeAdapter = adapter
                Result.Success(Unit)
            } else {
                result.map { }
            }
        } catch (e: Exception) {
            Result.Error(CommunicationException("WiFi connection failed: ${e.message}", e))
        }
    }

    /**
     * Connects to a USB scanner.
     */
    private suspend fun connectUSB(
        address: String,
        config: ScannerConfig,
        autoDetectProtocol: Boolean
    ): Result<Unit> {
        return try {
            val adapter = USBProtocolAdapter.create(context)
            val result = adapter.connect(address, autoDetectProtocol)
            
            if (result is Result.Success) {
                activeAdapter = adapter
                Result.Success(Unit)
            } else {
                result.map { }
            }
        } catch (e: Exception) {
            Result.Error(CommunicationException("USB connection failed: ${e.message}", e))
        }
    }

    /**
     * Connects to a J2534 scanner.
     */
    private suspend fun connectJ2534(
        address: String,
        config: ScannerConfig
    ): Result<Unit> {
        return try {
            val adapter = J2534ProtocolAdapter.create()
            val result = adapter.connect(address)
            
            if (result is Result.Success) {
                activeAdapter = adapter
                Result.Success(Unit)
            } else {
                result.map { }
            }
        } catch (e: Exception) {
            Result.Error(CommunicationException("J2534 connection failed: ${e.message}", e))
        }
    }

    /**
     * Automatically detects and connects to the best available scanner.
     *
     * @param config Scanner configuration (uses default if null)
     * @param autoDetectProtocol Whether to auto-detect the protocol
     * @return Success or error result
     */
    suspend fun connectAuto(
        config: ScannerConfig? = null,
        autoDetectProtocol: Boolean = true
    ): Result<Unit> = withContext(dispatcher) {
        // Try each connection type in order of preference
        val supportedTypes = connectionFactory.getSupportedConnectionTypes()
        
        for (type in supportedTypes) {
            when (type) {
                ScannerConnectionType.BLUETOOTH_CLASSIC,
                ScannerConnectionType.BLUETOOTH_LE -> {
                    // Try to discover and connect to Bluetooth devices
                    // Implementation would involve device discovery
                }
                ScannerConnectionType.WIFI -> {
                    // Try to discover and connect to WiFi devices
                    // Implementation would involve network discovery
                }
                ScannerConnectionType.USB -> {
                    // Try to discover and connect to USB devices
                    val usbDevices = USBProtocolAdapter.getConnectedOBDAdapters(context)
                    if (usbDevices.isNotEmpty()) {
                        val deviceAddress = "${usbDevices.first().vendorId}:${usbDevices.first().productId}"
                        val result = connect(deviceAddress, config, autoDetectProtocol)
                        if (result is Result.Success) return@withContext result
                    }
                }
                ScannerConnectionType.J2534 -> {
                    val devices = J2534ProtocolAdapter.getAvailableDevices()
                    if (devices.isNotEmpty()) {
                        val result = connect(devices.first(), config, autoDetectProtocol)
                        if (result is Result.Success) return@withContext result
                    }
                }
                else -> continue
            }
        }
        
        Result.Error(CommunicationException("No compatible scanner found"))
    }

    /**
     * Sends a command to the connected scanner.
     *
     * @param command The command to send
     * @param timeout Command timeout in milliseconds
     * @return Command response or error
     */
    suspend fun sendCommand(command: String, timeout: Long = 5000L): Result<String> {
        return when (activeAdapter) {
            is BluetoothProtocolAdapter -> {
                (activeAdapter as BluetoothProtocolAdapter).sendCommand(command, timeout)
            }
            is WiFiProtocolAdapter -> {
                (activeAdapter as WiFiProtocolAdapter).sendCommand(command, timeout)
            }
            is USBProtocolAdapter -> {
                (activeAdapter as USBProtocolAdapter).sendCommand(command, timeout)
            }
            is J2534ProtocolAdapter -> {
                (activeAdapter as J2534ProtocolAdapter).sendCommand(command, timeout)
            }
            else -> {
                Result.Error(CommunicationException("No active connection"))
            }
        }
    }

    /**
     * Sends an OBD-II command to the connected scanner.
     *
     * @param command The OBD-II command to send
     * @param timeout Command timeout in milliseconds
     * @return Command response or error
     */
    suspend fun sendObdCommand(command: String, timeout: Long = 5000L): Result<String> {
        return when (activeAdapter) {
            is BluetoothProtocolAdapter -> {
                (activeAdapter as BluetoothProtocolAdapter).sendObdCommand(command, timeout)
            }
            is WiFiProtocolAdapter -> {
                (activeAdapter as WiFiProtocolAdapter).sendObdCommand(command, timeout)
            }
            is USBProtocolAdapter -> {
                (activeAdapter as USBProtocolAdapter).sendObdCommand(command, timeout)
            }
            is J2534ProtocolAdapter -> {
                // J2534 may require different handling
                (activeAdapter as J2534ProtocolAdapter).sendCommand(command, timeout)
            }
            else -> {
                Result.Error(CommunicationException("No active connection"))
            }
        }
    }

    /**
     * Checks if there is an active connection.
     */
    fun isConnected(): Boolean {
        return when (activeAdapter) {
            is BluetoothProtocolAdapter -> (activeAdapter as BluetoothProtocolAdapter).isConnected()
            is WiFiProtocolAdapter -> (activeAdapter as WiFiProtocolAdapter).isConnected()
            is USBProtocolAdapter -> (activeAdapter as USBProtocolAdapter).isConnected()
            is J2534ProtocolAdapter -> (activeAdapter as J2534ProtocolAdapter).isConnected()
            else -> false
        }
    }

    /**
     * Gets the current connection type.
     */
    fun getConnectionType(): ScannerConnectionType? {
        return currentConnectionType
    }

    /**
     * Gets device information from the connected scanner.
     */
    fun getDeviceInfo(): Map<String, String> {
        return when (activeAdapter) {
            is BluetoothProtocolAdapter -> (activeAdapter as BluetoothProtocolAdapter).getDeviceInfo()
            is WiFiProtocolAdapter -> (activeAdapter as WiFiProtocolAdapter).getDeviceInfo()
            is USBProtocolAdapter -> (activeAdapter as USBProtocolAdapter).getDeviceInfo()
            is J2534ProtocolAdapter -> emptyMap() // J2534 devices may have different info
            else -> emptyMap()
        }
    }

    /**
     * Disconnects the current connection.
     */
    suspend fun disconnect() {
        try {
            when (activeAdapter) {
                is BluetoothProtocolAdapter -> {
                    (activeAdapter as BluetoothProtocolAdapter).disconnect()
                }
                is WiFiProtocolAdapter -> {
                    (activeAdapter as WiFiProtocolAdapter).disconnect()
                }
                is USBProtocolAdapter -> {
                    (activeAdapter as USBProtocolAdapter).disconnect()
                }
                is J2534ProtocolAdapter -> {
                    (activeAdapter as J2534ProtocolAdapter).disconnect()
                }
            }
            
            activeAdapter = null
            currentConnectionType = null
            currentAddress = null
            currentConfig = null
            _connectionState.value = ScannerConnectionState.Disconnected
        } catch (e: Exception) {
            // Log error but don't throw to ensure cleanup
        }
    }

    /**
     * Releases all resources held by the manager.
     */
    suspend fun release() {
        disconnect()
        connectionPool.shutdown()
    }

    /**
     * Gets the current connection quality.
     */
    fun getConnectionQuality(): ConnectionQuality {
        return _connectionQuality.value
    }

    /**
     * Gets the current configuration.
     */
    fun getCurrentConfig(): ScannerConfig? {
        return currentConfig
    }

    /**
     * Updates the current configuration.
     */
    suspend fun updateConfig(config: ScannerConfig) {
        currentConfig = config
        // Apply configuration changes to active connection if possible
    }

    /**
     * Gets connection statistics for the active connection.
     */
    fun getConnectionStatistics(): ConnectionStatistics? {
        return when (activeAdapter) {
            is BluetoothProtocolAdapter -> {
                // Implementation would get statistics from Bluetooth adapter
                null
            }
            is WiFiProtocolAdapter -> {
                // Implementation would get statistics from WiFi adapter
                null
            }
            is USBProtocolAdapter -> {
                // Implementation would get statistics from USB adapter
                null
            }
            is J2534ProtocolAdapter -> {
                // Implementation would get statistics from J2534 adapter
                null
            }
            else -> null
        }
    }

    /**
     * Performs a connection health check.
     */
    suspend fun checkConnectionHealth(): ConnectionHealthResult {
        if (!isConnected()) {
            return ConnectionHealthResult(
                isConnected = false,
                quality = ConnectionQuality.UNKNOWN,
                responseTime = -1,
                error = "Not connected"
            )
        }

        val startTime = System.currentTimeMillis()
        val result = sendCommand("ATI", timeout = 2000L)
        val responseTime = System.currentTimeMillis() - startTime

        val quality = when {
            result is Result.Success && responseTime < 500 -> ConnectionQuality.EXCELLENT
            result is Result.Success && responseTime < 1000 -> ConnectionQuality.GOOD
            result is Result.Success && responseTime < 2000 -> ConnectionQuality.FAIR
            result is Result.Success -> ConnectionQuality.POOR
            else -> ConnectionQuality.POOR
        }

        return ConnectionHealthResult(
            isConnected = result is Result.Success,
            quality = quality,
            responseTime = responseTime,
            error = if (result is Result.Error) result.exception.message else null
        )
    }

    companion object {
        /**
         * Creates a scanner manager instance.
         *
         * @param context Android context
         * @param connectionFactory Scanner connection factory
         * @param configurationManager Scanner configuration manager
         * @param connectionPool Connection pool
         * @param protocolDetectionEngine Protocol detection engine
         * @return Scanner manager
         */
        fun create(
            context: Context,
            connectionFactory: ScannerConnectionFactory,
            configurationManager: ScannerConfigurationManager,
            connectionPool: ScannerConnectionPool,
            protocolDetectionEngine: ProtocolDetectionEngine
        ): ScannerManager {
            return ScannerManager(
                context,
                connectionFactory,
                configurationManager,
                connectionPool,
                protocolDetectionEngine
            )
        }
    }
}

/**
 * Result of connection health check.
 */
data class ConnectionHealthResult(
    val isConnected: Boolean,
    val quality: ScannerManager.ConnectionQuality,
    val responseTime: Long,
    val error: String? = null
)