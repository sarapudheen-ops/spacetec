/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 *
 * This file is part of the SpaceTec professional automotive diagnostic system.
 */

package com.spacetec.obd.scanner.usb

import android.content.Context
import com.spacetec.core.common.exceptions.CommunicationException
import com.spacetec.core.common.result.Result
import com.spacetec.core.domain.models.scanner.ScannerConnectionType
import com.spacetec.obd.scanner.core.BaseScannerConnection
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
 * USB protocol adapter for OBD-II scanner communication.
 *
 * This adapter manages USB connections for OBD-II communication,
 * providing a unified interface for USB-based diagnostic adapters.
 * It handles USB Host API connections, protocol initialization,
 * and data transfer for USB OBD-II adapters like OBDLink, ELM327 USB,
 * and other USB-to-serial based devices.
 *
 * ## Features
 *
 * - USB Host API integration
 * - Automatic driver detection (FTDI, CP210x, CH340, CDC ACM)
 * - ELM327 and OBDLink protocol support
 * - Serial parameter auto-configuration
 * - Hot-plug detection support
 * - Automatic reconnection and error recovery
 *
 * @param context Android context for accessing USB services
 * @param dispatcher Coroutine dispatcher for I/O operations
 *
 * @author SpaceTec Development Team
 * @since 1.0.0
 */
class USBProtocolAdapter @Inject constructor(
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
     * Connects to a USB OBD-II adapter.
     *
     * @param address USB device address (device name, path, or vendor:product ID)
     * @param autoDetectProtocol Whether to auto-detect the protocol
     * @return Success or error result
     */
    suspend fun connect(
        address: String,
        autoDetectProtocol: Boolean = true
    ): Result<Unit> {
        return try {
            // Create USB connection
            val usbConnection = USBConnection.create(context)
            connection = usbConnection

            // Connect to the device
            val connectResult = usbConnection.connect(address)
            if (connectResult is Result.Error) {
                return connectResult
            }

            // Initialize the appropriate protocol adapter
            if (autoDetectProtocol) {
                val protocolType = detectProtocolType(usbConnection)
                when {
                    protocolType.isElm327 -> {
                        elm327Adapter = Elm327ProtocolAdapter.create(usbConnection)
                        val initResult = elm327Adapter?.initialize()
                        if (initResult is Result.Error) {
                            return initResult
                        }
                    }
                    protocolType.isOBDLink -> {
                        obdlinkAdapter = OBDLinkProtocolAdapter.create(usbConnection)
                        val initResult = obdlinkAdapter?.initialize()
                        if (initResult is Result.Error) {
                            return initResult
                        }
                    }
                    else -> {
                        // Default to basic protocol adapter
                        protocolAdapter = ScannerProtocolAdapter.create(
                            usbConnection,
                            ProtocolType.AUTO
                        )
                    }
                }
            } else {
                // Default to basic protocol adapter
                protocolAdapter = ScannerProtocolAdapter.create(
                    usbConnection,
                    ProtocolType.AUTO
                )
            }

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(CommunicationException("USB connection failed: ${e.message}", e))
        }
    }

    /**
     * Detects the protocol type of the connected device.
     */
    private suspend fun detectProtocolType(connection: com.spacetec.obd.scanner.core.ScannerConnection): ProtocolTypeDetectionResult {
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
     * Gets the USB device information.
     */
    fun getDeviceInformation(): USBDeviceInfo? {
        return if (connection is USBConnection) {
            val usbConn = connection as USBConnection
            USBDeviceInfo(
                deviceName = usbConn.getDeviceName(),
                productName = usbConn.getProductName(),
                manufacturerName = usbConn.getManufacturerName(),
                driverType = usbConn.getDriverType()
            )
        } else {
            null
        }
    }

    /**
     * Checks if the connection is active.
     */
    fun isConnected(): Boolean {
        return connection?.isConnected == true
    }

    /**
     * Disconnects from the USB device.
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
     * Gets the connection type (USB).
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
         * Creates a USB protocol adapter instance.
         *
         * @param context Android context
         * @return USB protocol adapter
         */
        fun create(context: Context): USBProtocolAdapter {
            return USBProtocolAdapter(context)
        }

        /**
         * Checks if USB Host is available on the device.
         */
        fun isUSBHostAvailable(context: Context): Boolean {
            return USBConnection.isUSBHostSupported(context)
        }

        /**
         * Gets the list of connected USB devices.
         */
        fun getConnectedDevices(context: Context): List<android.hardware.usb.UsbDevice> {
            return USBConnection.getConnectedDevices(context)
        }

        /**
         * Gets the list of connected OBD adapters.
         */
        fun getConnectedOBDAdapters(context: Context): List<android.hardware.usb.UsbDevice> {
            return USBConnection.getConnectedOBDAdapters(context)
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

/**
 * USB device information.
 */
data class USBDeviceInfo(
    val deviceName: String?,
    val productName: String?,
    val manufacturerName: String?,
    val driverType: USBDriverType?
)