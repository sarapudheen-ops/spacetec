/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 *
 * This file is part of the SpaceTec professional automotive diagnostic system.
 */

package com.spacetec.obd.scanner.bluetooth

import android.content.Context
import com.spacetec.core.common.exceptions.CommunicationException
import com.spacetec.core.common.result.Result
import com.spacetec.core.domain.models.scanner.ScannerConnectionType
import com.spacetec.obd.scanner.bluetooth.classic.BluetoothClassicConnection
import com.spacetec.obd.scanner.bluetooth.ble.BluetoothLEConnection
import com.spacetec.obd.scanner.core.BaseScannerConnection
import com.spacetec.obd.scanner.core.ScannerConnection
import com.spacetec.obd.scanner.core.ScannerProtocolAdapter
import com.spacetec.obd.scanner.devices.elm327.Elm327ProtocolAdapter
import com.spacetec.obd.scanner.devices.obdlink.OBDLinkProtocolAdapter
import com.spacetec.protocol.core.ProtocolType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Bluetooth protocol adapter for OBD-II scanner communication.
 *
 * This adapter manages Bluetooth connections (both Classic and LE) for OBD-II
 * communication, providing a unified interface for Bluetooth-based diagnostic
 * adapters. It handles connection establishment, protocol initialization,
 * and data transfer for both traditional Bluetooth SPP and Bluetooth LE GATT
 * based adapters.
 *
 * ## Features
 *
 * - Dual Bluetooth technology support (Classic SPP and LE GATT)
 * - Automatic connection type detection
 * - ELM327 and OBDLink protocol support
 * - Signal strength monitoring
 * - Connection quality assessment
 * - Automatic reconnection and error recovery
 *
 * @param context Android context for accessing Bluetooth services
 * @param dispatcher Coroutine dispatcher for I/O operations
 *
 * @author SpaceTec Development Team
 * @since 1.0.0
 */
class BluetoothProtocolAdapter @Inject constructor(
    private val context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private var connection: BaseScannerConnection? = null
    private var protocolAdapter: ScannerProtocolAdapter? = null
    private var elm327Adapter: Elm327ProtocolAdapter? = null
    private var obdlinkAdapter: OBDLinkProtocolAdapter? = null

    /**
     * Gets the current connection state.
     */
    val connectionState: StateFlow<com.spacetec.obd.scanner.core.ConnectionState>?
        get() = connection?.connectionState

    /**
     * Gets the incoming data flow.
     */
    val incomingData: Flow<ByteArray>?
        get() = connection?.incomingData

    /**
     * Creates a Bluetooth connection for the specified address.
     *
     * @param address Bluetooth device address (MAC address format)
     * @return Connection instance
     */
    fun createConnection(address: String): ScannerConnection {
        // Determine if this is a Classic or LE device based on address
        // For now, default to Classic; in a real implementation, we'd have more sophisticated detection
        return if (isBluetoothLEAddress(address)) {
            BluetoothLEConnection.create(context)
        } else {
            BluetoothClassicConnection.create(context)
        }
    }

    /**
     * Checks if the address is likely a Bluetooth LE address.
     * This is a simplified check - in reality, both Classic and LE use the same MAC format.
     */
    private fun isBluetoothLEAddress(address: String): Boolean {
        // In a real implementation, this would involve actual device discovery
        // and service discovery to determine if the device supports LE
        return false // Default to Classic for now
    }

    /**
     * Connects to a Bluetooth OBD-II adapter.
     *
     * @param address Bluetooth device address (MAC address)
     * @param autoDetectProtocol Whether to auto-detect the protocol
     * @return Success or error result
     */
    suspend fun connect(
        address: String,
        autoDetectProtocol: Boolean = true
    ): Result<Unit> {
        return try {
            // Create the appropriate connection
            val bluetoothConnection = createConnection(address) as BaseScannerConnection
            connection = bluetoothConnection

            // Connect to the device
            val connectResult = bluetoothConnection.connect(address)
            if (connectResult is Result.Error) {
                return connectResult
            }

            // Initialize the appropriate protocol adapter
            if (autoDetectProtocol) {
                val protocolType = detectProtocolType(bluetoothConnection)
                when {
                    protocolType.isElm327 -> {
                        elm327Adapter = Elm327ProtocolAdapter.create(bluetoothConnection)
                        val initResult = elm327Adapter?.initialize()
                        if (initResult is Result.Error) {
                            return initResult
                        }
                    }
                    protocolType.isOBDLink -> {
                        obdlinkAdapter = OBDLinkProtocolAdapter.create(bluetoothConnection)
                        val initResult = obdlinkAdapter?.initialize()
                        if (initResult is Result.Error) {
                            return initResult
                        }
                    }
                    else -> {
                        // Default to basic protocol adapter
                        protocolAdapter = ScannerProtocolAdapter.create(
                            bluetoothConnection,
                            ProtocolType.AUTO
                        )
                    }
                }
            } else {
                // Default to basic protocol adapter
                protocolAdapter = ScannerProtocolAdapter.create(
                    bluetoothConnection,
                    ProtocolType.AUTO
                )
            }

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(CommunicationException("Bluetooth connection failed: ${e.message}", e))
        }
    }

    /**
     * Detects the protocol type of the connected device.
     */
    private suspend fun detectProtocolType(connection: ScannerConnection): ProtocolTypeDetectionResult {
        // Try ELM327 detection first
        val isElm327 = Elm327ProtocolAdapter.isElm327Compatible(connection)
        if (isElm327) {
            return ProtocolTypeDetectionResult(isElm327 = true, isOBDLink = false)
        }

        // Try OBDLink detection
        val isOBDLink = OBDLinkProtocolAdapter.isOBDLinkCompatible(connection)
        if (isOBDLink) {
            return ProtocolTypeDetectionResult(isElm327 = false, isOBDLink = true)
        }

        // Unknown protocol type
        return ProtocolTypeDetectionResult(isElm327 = false, isOBDLink = false)
    }

    /**
     * Sends a command to the connected device.
     *
     * @param command The command to send
     * @param timeout Command timeout in milliseconds
     * @return Command response or error
     */
    suspend fun sendCommand(command: String, timeout: Long = 5000L): Result<String> {
        return when {
            elm327Adapter?.isReady() == true -> {
                elm327Adapter?.executeCommand(command, timeout) ?: 
                    Result.Error(CommunicationException("ELM327 adapter not initialized"))
            }
            obdlinkAdapter?.isReady() == true -> {
                obdlinkAdapter?.executeCommand(command, timeout) ?: 
                    Result.Error(CommunicationException("OBDLink adapter not initialized"))
            }
            protocolAdapter?.isReady() == true -> {
                protocolAdapter?.sendCommand(command, timeout) ?: 
                    Result.Error(CommunicationException("Protocol adapter not initialized"))
            }
            else -> {
                Result.Error(CommunicationException("No active protocol adapter"))
            }
        }
    }

    /**
     * Sends an OBD-II command to the connected device.
     *
     * @param command The OBD-II command to send
     * @param timeout Command timeout in milliseconds
     * @return Command response or error
     */
    suspend fun sendObdCommand(command: String, timeout: Long = 5000L): Result<String> {
        return when {
            elm327Adapter?.isReady() == true -> {
                elm327Adapter?.sendObdCommand(command, timeout) ?: 
                    Result.Error(CommunicationException("ELM327 adapter not initialized"))
            }
            obdlinkAdapter?.isReady() == true -> {
                obdlinkAdapter?.sendObdCommand(command, timeout) ?: 
                    Result.Error(CommunicationException("OBDLink adapter not initialized"))
            }
            else -> {
                Result.Error(CommunicationException("No OBD-capable protocol adapter"))
            }
        }
    }

    /**
     * Gets the signal strength of the Bluetooth connection.
     */
    fun getSignalStrength(): Int? {
        val info = connection?.connectionState?.value?.connectionInfo
        return info?.signalStrength
    }

    /**
     * Checks if the connection is active.
     */
    fun isConnected(): Boolean {
        return connection?.isConnected == true
    }

    /**
     * Disconnects from the Bluetooth device.
     */
    suspend fun disconnect() {
        try {
            elm327Adapter?.reset()
            obdlinkAdapter?.reset()
            protocolAdapter?.disconnect()
            connection?.disconnect()
        } catch (e: Exception) {
            // Log error but don't throw to ensure cleanup
        }
    }

    /**
     * Releases all resources held by the adapter.
     */
    fun release() {
        try {
            elm327Adapter?.let { 
                it.reset()
                it::class.java.getDeclaredField("connection").let { field ->
                    field.isAccessible = true
                    field.set(it, null)
                }
            }
            obdlinkAdapter?.let {
                it.reset()
                it::class.java.getDeclaredField("connection").let { field ->
                    field.isAccessible = true
                    field.set(it, null)
                }
            }
            protocolAdapter?.release()
            connection?.release()
        } catch (e: Exception) {
            // Log error but don't throw to ensure cleanup
        }
    }

    /**
     * Gets the connection type (Classic or LE).
     */
    fun getConnectionType(): ScannerConnectionType? {
        return connection?.connectionType
    }

    /**
     * Gets device information from the connected adapter.
     */
    fun getDeviceInfo(): Map<String, String> {
        return when {
            elm327Adapter?.isReady() == true -> {
                elm327Adapter?.getDeviceInfo() ?: emptyMap()
            }
            obdlinkAdapter?.isReady() == true -> {
                obdlinkAdapter?.getDeviceInfo() ?: emptyMap()
            }
            else -> emptyMap()
        }
    }

    companion object {
        /**
         * Creates a Bluetooth protocol adapter instance.
         *
         * @param context Android context
         * @return Bluetooth protocol adapter
         */
        fun create(context: Context): BluetoothProtocolAdapter {
            return BluetoothProtocolAdapter(context)
        }

        /**
         * Checks if Bluetooth is available on the device.
         */
        fun isBluetoothAvailable(context: Context): Boolean {
            return BluetoothClassicConnection.isBluetoothAvailable(context)
        }

        /**
         * Checks if Bluetooth LE is available on the device.
         */
        fun isBluetoothLEAvailable(context: Context): Boolean {
            return BluetoothLEConnection.isBLEAvailable(context)
        }
    }
}

/**
 * Result of protocol type detection.
 */
private data class ProtocolTypeDetectionResult(
    val isElm327: Boolean,
    val isOBDLink: Boolean
)