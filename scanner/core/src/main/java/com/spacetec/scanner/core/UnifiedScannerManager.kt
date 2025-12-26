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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/**
 * Unified scanner manager that coordinates all scanner communication layers.
 *
 * This manager provides a single interface for managing Bluetooth, WiFi, USB,
 * and J2534 OBD-II scanner connections. It handles automatic connection type
 * detection, protocol initialization, and provides a consistent API for
 * communication across all connection types.
 *
 * ## Features
 *
 * - Multi-connection type support (Bluetooth, WiFi, USB, J2534)
 * - Automatic connection type detection
 * - Protocol auto-detection and initialization
 * - Unified command interface
 * - Connection state management
 * - Error handling and recovery
 * - Resource management and cleanup
 *
 * @param context Android context for accessing system services
 * @param connectionFactory Factory for creating scanner connections
 * @param dispatcher Coroutine dispatcher for I/O operations
 *
 * @author SpaceTec Development Team
 * @since 1.0.0
 */
class UnifiedScannerManager @Inject constructor(
    private val context: Context,
    private val connectionFactory: ScannerConnectionFactory,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private var activeBluetoothAdapter: BluetoothProtocolAdapter? = null
    private var activeWiFiAdapter: WiFiProtocolAdapter? = null
    private var activeUSBAdapter: USBProtocolAdapter? = null
    private var activeJ2534Adapter: J2534ProtocolAdapter? = null
    
    private var currentConnectionType: ScannerConnectionType? = null

    /**
     * Gets the combined connection state from all active adapters.
     */
    val connectionState: StateFlow<ConnectionState> = combine(
        getBluetoothStateFlow(),
        getWiFiStateFlow(),
        getUSBStateFlow(),
        getJ2534StateFlow()
    ) { states ->
        // Return the state of the currently active connection
        when (currentConnectionType) {
            ScannerConnectionType.BLUETOOTH_CLASSIC,
            ScannerConnectionType.BLUETOOTH_LE -> states[0]
            ScannerConnectionType.WIFI -> states[1]
            ScannerConnectionType.USB -> states[2]
            ScannerConnectionType.J2534 -> states[3]
            else -> ConnectionState.Disconnected
        }
    }.asStateFlow()

    /**
     * Gets the incoming data flow from the active connection.
     */
    val incomingData: Flow<ByteArray>?
        get() = when (currentConnectionType) {
            ScannerConnectionType.BLUETOOTH_CLASSIC,
            ScannerConnectionType.BLUETOOTH_LE -> activeBluetoothAdapter?.incomingData
            ScannerConnectionType.WIFI -> activeWiFiAdapter?.incomingData
            ScannerConnectionType.USB -> activeUSBAdapter?.incomingData
            ScannerConnectionType.J2534 -> null // J2534 uses different data flow
            else -> null
        }

    /**
     * Connects to an OBD-II scanner using the specified connection type.
     *
     * @param address Device address (format depends on connection type)
     * @param connectionType Type of connection to use
     * @param autoDetectProtocol Whether to auto-detect the protocol
     * @return Success or error result
     */
    suspend fun connect(
        address: String,
        connectionType: ScannerConnectionType,
        autoDetectProtocol: Boolean = true
    ): Result<Unit> {
        return when (connectionType) {
            ScannerConnectionType.BLUETOOTH_CLASSIC,
            ScannerConnectionType.BLUETOOTH_LE -> {
                connectBluetooth(address, autoDetectProtocol)
            }
            ScannerConnectionType.WIFI -> {
                connectWiFi(address, autoDetectProtocol)
            }
            ScannerConnectionType.USB -> {
                connectUSB(address, autoDetectProtocol)
            }
            ScannerConnectionType.J2534 -> {
                connectJ2534(address)
            }
            else -> {
                Result.Error(CommunicationException("Unsupported connection type: $connectionType"))
            }
        }
    }

    /**
     * Connects to a Bluetooth OBD-II scanner.
     */
    private suspend fun connectBluetooth(address: String, autoDetectProtocol: Boolean): Result<Unit> {
        try {
            val adapter = BluetoothProtocolAdapter.create(context)
            val result = adapter.connect(address, autoDetectProtocol)
            
            if (result is Result.Success) {
                // Disconnect any existing adapters
                disconnectCurrent()
                
                activeBluetoothAdapter = adapter
                currentConnectionType = ScannerConnectionType.BLUETOOTH_CLASSIC
            }
            
            return result
        } catch (e: Exception) {
            return Result.Error(CommunicationException("Bluetooth connection failed: ${e.message}", e))
        }
    }

    /**
     * Connects to a WiFi OBD-II scanner.
     */
    private suspend fun connectWiFi(address: String, autoDetectProtocol: Boolean): Result<Unit> {
        try {
            val adapter = WiFiProtocolAdapter.create(context)
            val result = adapter.connect(address, autoDetectProtocol)
            
            if (result is Result.Success) {
                // Disconnect any existing adapters
                disconnectCurrent()
                
                activeWiFiAdapter = adapter
                currentConnectionType = ScannerConnectionType.WIFI
            }
            
            return result
        } catch (e: Exception) {
            return Result.Error(CommunicationException("WiFi connection failed: ${e.message}", e))
        }
    }

    /**
     * Connects to a USB OBD-II scanner.
     */
    private suspend fun connectUSB(address: String, autoDetectProtocol: Boolean): Result<Unit> {
        try {
            val adapter = USBProtocolAdapter.create(context)
            val result = adapter.connect(address, autoDetectProtocol)
            
            if (result is Result.Success) {
                // Disconnect any existing adapters
                disconnectCurrent()
                
                activeUSBAdapter = adapter
                currentConnectionType = ScannerConnectionType.USB
            }
            
            return result
        } catch (e: Exception) {
            return Result.Error(CommunicationException("USB connection failed: ${e.message}", e))
        }
    }

    /**
     * Connects to a J2534 OBD-II scanner.
     */
    private suspend fun connectJ2534(address: String): Result<Unit> {
        try {
            val adapter = J2534ProtocolAdapter.create()
            val result = adapter.connect(address)
            
            if (result is Result.Success) {
                // Disconnect any existing adapters
                disconnectCurrent()
                
                activeJ2534Adapter = adapter
                currentConnectionType = ScannerConnectionType.J2534
            }
            
            return result
        } catch (e: Exception) {
            return Result.Error(CommunicationException("J2534 connection failed: ${e.message}", e))
        }
    }

    /**
     * Automatically detects and connects to the best available scanner.
     *
     * @param autoDetectProtocol Whether to auto-detect the protocol
     * @return Success or error result
     */
    suspend fun connectAuto(autoDetectProtocol: Boolean = true): Result<Unit> {
        // Try each connection type in order of preference
        val supportedTypes = connectionFactory.getSupportedConnectionTypes()
        
        for (type in supportedTypes) {
            when (type) {
                ScannerConnectionType.BLUETOOTH_CLASSIC,
                ScannerConnectionType.BLUETOOTH_LE -> {
                    // Try to discover and connect to Bluetooth devices
                    // For now, return if we find a connection
                    val result = connectBluetooth("", autoDetectProtocol) // Empty address for auto-discovery
                    if (result is Result.Success) return result
                }
                ScannerConnectionType.WIFI -> {
                    // Try to discover and connect to WiFi devices
                    val result = connectWiFi("", autoDetectProtocol) // Empty address for auto-discovery
                    if (result is Result.Success) return result
                }
                ScannerConnectionType.USB -> {
                    // Try to discover and connect to USB devices
                    val usbDevices = USBProtocolAdapter.getConnectedOBDAdapters(context)
                    if (usbDevices.isNotEmpty()) {
                        val deviceAddress = "${usbDevices.first().vendorId}:${usbDevices.first().productId}"
                        val result = connectUSB(deviceAddress, autoDetectProtocol)
                        if (result is Result.Success) return result
                    }
                }
                ScannerConnectionType.J2534 -> {
                    val devices = J2534ProtocolAdapter.getAvailableDevices()
                    if (devices.isNotEmpty()) {
                        val result = connectJ2534(devices.first())
                        if (result is Result.Success) return result
                    }
                }
                else -> continue
            }
        }
        
        return Result.Error(CommunicationException("No compatible scanner found"))
    }

    /**
     * Sends a command to the connected scanner.
     *
     * @param command The command to send
     * @param timeout Command timeout in milliseconds
     * @return Command response or error
     */
    suspend fun sendCommand(command: String, timeout: Long = 5000L): Result<String> {
        return when (currentConnectionType) {
            ScannerConnectionType.BLUETOOTH_CLASSIC,
            ScannerConnectionType.BLUETOOTH_LE -> {
                activeBluetoothAdapter?.sendCommand(command, timeout) ?: 
                    Result.Error(CommunicationException("No active Bluetooth connection"))
            }
            ScannerConnectionType.WIFI -> {
                activeWiFiAdapter?.sendCommand(command, timeout) ?: 
                    Result.Error(CommunicationException("No active WiFi connection"))
            }
            ScannerConnectionType.USB -> {
                activeUSBAdapter?.sendCommand(command, timeout) ?: 
                    Result.Error(CommunicationException("No active USB connection"))
            }
            ScannerConnectionType.J2534 -> {
                activeJ2534Adapter?.sendCommand(command, timeout) ?: 
                    Result.Error(CommunicationException("No active J2534 connection"))
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
        return when (currentConnectionType) {
            ScannerConnectionType.BLUETOOTH_CLASSIC,
            ScannerConnectionType.BLUETOOTH_LE -> {
                activeBluetoothAdapter?.sendObdCommand(command, timeout) ?: 
                    Result.Error(CommunicationException("No active Bluetooth connection"))
            }
            ScannerConnectionType.WIFI -> {
                activeWiFiAdapter?.sendObdCommand(command, timeout) ?: 
                    Result.Error(CommunicationException("No active WiFi connection"))
            }
            ScannerConnectionType.USB -> {
                activeUSBAdapter?.sendObdCommand(command, timeout) ?: 
                    Result.Error(CommunicationException("No active USB connection"))
            }
            ScannerConnectionType.J2534 -> {
                // J2534 may require different handling
                activeJ2534Adapter?.sendCommand(command, timeout) ?: 
                    Result.Error(CommunicationException("No active J2534 connection"))
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
        return when (currentConnectionType) {
            ScannerConnectionType.BLUETOOTH_CLASSIC,
            ScannerConnectionType.BLUETOOTH_LE -> activeBluetoothAdapter?.isConnected() == true
            ScannerConnectionType.WIFI -> activeWiFiAdapter?.isConnected() == true
            ScannerConnectionType.USB -> activeUSBAdapter?.isConnected() == true
            ScannerConnectionType.J2534 -> activeJ2534Adapter?.isConnected() == true
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
        return when (currentConnectionType) {
            ScannerConnectionType.BLUETOOTH_CLASSIC,
            ScannerConnectionType.BLUETOOTH_LE -> activeBluetoothAdapter?.getDeviceInfo() ?: emptyMap()
            ScannerConnectionType.WIFI -> activeWiFiAdapter?.getDeviceInfo() ?: emptyMap()
            ScannerConnectionType.USB -> activeUSBAdapter?.getDeviceInfo() ?: emptyMap()
            ScannerConnectionType.J2534 -> emptyMap() // J2534 devices may have different info
            else -> emptyMap()
        }
    }

    /**
     * Disconnects the current connection.
     */
    suspend fun disconnect() {
        disconnectCurrent()
    }

    /**
     * Disconnects the currently active connection.
     */
    private suspend fun disconnectCurrent() {
        when (currentConnectionType) {
            ScannerConnectionType.BLUETOOTH_CLASSIC,
            ScannerConnectionType.BLUETOOTH_LE -> {
                activeBluetoothAdapter?.disconnect()
                activeBluetoothAdapter = null
            }
            ScannerConnectionType.WIFI -> {
                activeWiFiAdapter?.disconnect()
                activeWiFiAdapter = null
            }
            ScannerConnectionType.USB -> {
                activeUSBAdapter?.disconnect()
                activeUSBAdapter = null
            }
            ScannerConnectionType.J2534 -> {
                activeJ2534Adapter?.disconnect()
                activeJ2534Adapter = null
            }
        }
        currentConnectionType = null
    }

    /**
     * Releases all resources held by the manager.
     */
    fun release() {
        try {
            activeBluetoothAdapter?.release()
            activeWiFiAdapter?.release()
            activeUSBAdapter?.release()
            activeJ2534Adapter?.release()
            
            activeBluetoothAdapter = null
            activeWiFiAdapter = null
            activeUSBAdapter = null
            activeJ2534Adapter = null
            
            currentConnectionType = null
        } catch (e: Exception) {
            // Log error but don't throw to ensure cleanup
        }
    }

    /**
     * Gets Bluetooth state flow for combining.
     */
    private fun getBluetoothStateFlow(): StateFlow<ConnectionState> {
        return activeBluetoothAdapter?.connectionState ?: MutableStateFlow(ConnectionState.Disconnected).asStateFlow()
    }

    /**
     * Gets WiFi state flow for combining.
     */
    private fun getWiFiStateFlow(): StateFlow<ConnectionState> {
        return activeWiFiAdapter?.connectionState ?: MutableStateFlow(ConnectionState.Disconnected).asStateFlow()
    }

    /**
     * Gets USB state flow for combining.
     */
    private fun getUSBStateFlow(): StateFlow<ConnectionState> {
        return activeUSBAdapter?.connectionState ?: MutableStateFlow(ConnectionState.Disconnected).asStateFlow()
    }

    /**
     * Gets J2534 state flow for combining.
     */
    private fun getJ2534StateFlow(): StateFlow<ConnectionState> {
        // J2534 doesn't have a standard state flow
        return MutableStateFlow(ConnectionState.Disconnected).asStateFlow()
    }

    companion object {
        /**
         * Creates a unified scanner manager instance.
         *
         * @param context Android context
         * @param connectionFactory Scanner connection factory
         * @return Unified scanner manager
         */
        fun create(
            context: Context,
            connectionFactory: ScannerConnectionFactory
        ): UnifiedScannerManager {
            return UnifiedScannerManager(context, connectionFactory)
        }
    }
}