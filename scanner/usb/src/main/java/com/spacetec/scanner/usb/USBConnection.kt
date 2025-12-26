/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 *
 * This file is part of the SpaceTec professional automotive diagnostic system.
 */

package com.spacetec.obd.scanner.usb

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import com.spacetec.core.common.exceptions.CommunicationException
import com.spacetec.core.common.exceptions.ConnectionException
import com.spacetec.core.domain.models.scanner.ScannerConnectionType
import com.spacetec.obd.scanner.core.BaseScannerConnection
import com.spacetec.obd.scanner.core.ConnectionConfig
import com.spacetec.obd.scanner.core.ConnectionInfo
import com.spacetec.obd.scanner.core.ConnectionState
import com.spacetec.obd.scanner.usb.drivers.USBDriverFactory
import com.spacetec.obd.scanner.usb.serial.USBSerialPort
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

/**
 * USB serial connection implementation for OBD-II scanners.
 *
 * Provides robust USB Host API integration for connecting to USB-based
 * diagnostic adapters like OBDLink EX and other USB-to-serial devices.
 *
 * ## Features
 *
 * - Automatic driver detection (FTDI, CP210x, CH340, CDC ACM)
 * - Serial parameter auto-configuration
 * - Hot-plug detection support
 * - Background data reading with coroutines
 * - Thread-safe operations with mutex protection
 * - Comprehensive error handling and recovery
 *
 * ## Usage Example
 *
 * ```kotlin
 * val connection = USBConnection(context)
 *
 * // Connect to device by device name or path
 * val result = connection.connect("/dev/bus/usb/001/002")
 * if (result is Result.Success) {
 *     // Send command
 *     val response = connection.sendAndReceive("ATZ")
 *     println("Response: ${response.getOrNull()}")
 * }
 *
 * // Disconnect
 * connection.disconnect()
 * ```
 *
 * @param context Android context for accessing USB services
 * @param usbConfig USB-specific configuration
 * @param dispatcher Coroutine dispatcher for I/O operations
 *
 * @author SpaceTec Development Team
 * @since 1.0.0
 */
class USBConnection @Inject constructor(
    private val context: Context,
    private var usbConfig: USBConnectionConfig = USBConnectionConfig.DEFAULT,
    dispatcher: CoroutineDispatcher = Dispatchers.IO
) : BaseScannerConnection(dispatcher) {

    override val connectionType: ScannerConnectionType = ScannerConnectionType.USB

    private val usbManager: UsbManager by lazy {
        context.getSystemService(Context.USB_SERVICE) as UsbManager
    }

    private var usbDevice: UsbDevice? = null
    private var usbConnection: UsbDeviceConnection? = null
    private var serialPort: USBSerialPort? = null

    private val readBuffer = ByteArray(USBConstants.READ_BUFFER_SIZE)
    private val streamLock = Mutex()

    private var backgroundReaderJob: Job? = null
    private val isReading = AtomicBoolean(false)
    private val connectionAttempt = AtomicInteger(0)

    // ═══════════════════════════════════════════════════════════════════════
    // CONNECTION IMPLEMENTATION
    // ═══════════════════════════════════════════════════════════════════════

    override suspend fun doConnect(
        address: String,
        config: ConnectionConfig
    ): ConnectionInfo = withContext(dispatcher) {

        // Find USB device by address (device name or path)
        val device = findDevice(address)
            ?: throw ConnectionException("USB device not found: $address")

        // Check permission
        if (!usbManager.hasPermission(device)) {
            throw ConnectionException("USB permission not granted for device: ${device.deviceName}")
        }

        // Open device connection
        val connection = usbManager.openDevice(device)
            ?: throw ConnectionException("Failed to open USB device: ${device.deviceName}")

        usbDevice = device
        usbConnection = connection

        // Attempt connection with retries
        var lastException: Exception? = null
        var attempt = 0

        while (attempt < USBConstants.MAX_CONNECTION_RETRIES) {
            attempt++
            connectionAttempt.set(attempt)

            try {
                // Create and open serial port
                val port = USBDriverFactory.createDriver(device)
                
                // Determine serial config
                val serialConfig = if (usbConfig.autoDetectParameters) {
                    detectSerialConfig(device)
                } else {
                    usbConfig.serialConfig
                }

                port.open(connection, serialConfig)
                serialPort = port

                // Reset device if configured
                if (usbConfig.resetOnConnect) {
                    delay(USBConstants.USB_RESET_DELAY)
                    port.purgeBuffers(input = true, output = true)
                }

                // Start background reader
                startBackgroundReaderInternal()

                // Build connection info
                return@withContext ConnectionInfo(
                    connectedAt = System.currentTimeMillis(),
                    localAddress = null,
                    remoteAddress = device.deviceName,
                    mtu = USBConstants.DEFAULT_MTU,
                    signalStrength = null,
                    connectionType = ScannerConnectionType.USB
                )

            } catch (e: CancellationException) {
                cleanupConnection()
                throw e
            } catch (e: Exception) {
                lastException = e
                cleanupSerialPort()

                if (attempt < USBConstants.MAX_CONNECTION_RETRIES) {
                    delay(USBConstants.CONNECTION_RETRY_DELAY * attempt)
                }
            }
        }

        cleanupConnection()
        throw ConnectionException(
            "Failed to connect after $attempt attempts: ${lastException?.message}",
            lastException
        )
    }

    /**
     * Finds a USB device by address.
     *
     * @param address Device name, path, or identifier
     * @return USB device or null if not found
     */
    private fun findDevice(address: String): UsbDevice? {
        val deviceList = usbManager.deviceList

        // Try exact match first
        deviceList[address]?.let { return it }

        // Try matching by device name
        for ((_, device) in deviceList) {
            if (device.deviceName == address ||
                device.deviceName.endsWith(address) ||
                "${device.vendorId}:${device.productId}" == address) {
                return device
            }
        }

        // Try matching by vendor:product ID
        val parts = address.split(":")
        if (parts.size == 2) {
            val vendorId = parts[0].toIntOrNull(16) ?: parts[0].toIntOrNull()
            val productId = parts[1].toIntOrNull(16) ?: parts[1].toIntOrNull()
            
            if (vendorId != null && productId != null) {
                for ((_, device) in deviceList) {
                    if (device.vendorId == vendorId && device.productId == productId) {
                        return device
                    }
                }
            }
        }

        return null
    }

    /**
     * Detects optimal serial configuration for a device.
     */
    private fun detectSerialConfig(device: UsbDevice): USBSerialConfig {
        val baudRate = USBDriverFactory.getRecommendedBaudRate(device)
        
        return USBSerialConfig(
            baudRate = baudRate,
            dataBits = USBConstants.DEFAULT_DATA_BITS,
            stopBits = USBStopBits.ONE,
            parity = USBParity.NONE,
            dtr = true,
            rts = true
        )
    }

    /**
     * Cleans up the serial port.
     */
    private fun cleanupSerialPort() {
        try {
            serialPort?.close()
        } catch (_: Exception) {}
        serialPort = null
    }

    /**
     * Cleans up the USB connection.
     */
    private fun cleanupConnection() {
        cleanupSerialPort()
        
        try {
            usbConnection?.close()
        } catch (_: Exception) {}
        
        usbConnection = null
        usbDevice = null
    }

    // ═══════════════════════════════════════════════════════════════════════
    // DISCONNECTION
    // ═══════════════════════════════════════════════════════════════════════

    override suspend fun doDisconnect(graceful: Boolean) = withContext(dispatcher) {
        // Stop background reader
        stopBackgroundReader()

        // Close serial port and connection
        streamLock.withLock {
            if (graceful) {
                try {
                    // Flush output before closing
                    delay(50)
                } catch (_: Exception) {}
            }

            cleanupConnection()
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // DATA TRANSFER
    // ═══════════════════════════════════════════════════════════════════════

    override suspend fun doWrite(data: ByteArray): Int = streamLock.withLock {
        val port = serialPort
            ?: throw CommunicationException("Serial port not available")

        try {
            return port.write(data, config.writeTimeout.toInt())
        } catch (e: IOException) {
            handleIOException(e)
            throw CommunicationException("Write failed: ${e.message}", e)
        }
    }

    override suspend fun doRead(buffer: ByteArray, timeout: Long): Int {
        val port = serialPort
            ?: throw CommunicationException("Serial port not available")

        val startTime = System.currentTimeMillis()

        while (System.currentTimeMillis() - startTime < timeout) {
            try {
                val available = port.available()
                if (available > 0) {
                    return port.read(buffer, timeout.toInt())
                }
                
                // Try a blocking read with short timeout
                val bytesRead = port.read(buffer, USBConstants.BACKGROUND_READ_INTERVAL.toInt())
                if (bytesRead > 0) {
                    return bytesRead
                }
                
                delay(USBConstants.BACKGROUND_READ_INTERVAL)
            } catch (e: IOException) {
                handleIOException(e)
                throw CommunicationException("Read failed: ${e.message}", e)
            }
        }

        return 0 // Timeout, no data
    }

    override suspend fun doAvailable(): Int {
        return try {
            serialPort?.available() ?: 0
        } catch (e: IOException) {
            0
        }
    }

    override suspend fun doClearBuffers() {
        val port = serialPort ?: return

        try {
            port.purgeBuffers(input = true, output = true)
        } catch (e: IOException) {
            // Ignore errors during clear
        }

        // Clear response buffer
        responseLock.withLock {
            responseBuffer.clear()
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // BACKGROUND READER
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Starts the background reader coroutine.
     */
    private fun startBackgroundReaderInternal() {
        if (isReading.getAndSet(true)) {
            return // Already reading
        }

        backgroundReaderJob = scope.launch {
            try {
                val buffer = ByteArray(USBConstants.READ_BUFFER_SIZE)

                while (isActive && isConnected) {
                    try {
                        val port = serialPort
                        if (port == null) {
                            delay(100)
                            continue
                        }

                        val bytesRead = port.read(buffer, 100)
                        if (bytesRead > 0) {
                            val data = buffer.copyOf(bytesRead)
                            processIncomingData(data)
                        } else if (bytesRead < 0) {
                            // Device disconnected
                            handleDeviceDisconnected()
                            break
                        } else {
                            delay(USBConstants.BACKGROUND_READ_INTERVAL)
                        }
                    } catch (e: IOException) {
                        if (isActive && isConnected) {
                            handleIOException(e)
                        }
                        break
                    }
                }
            } finally {
                isReading.set(false)
            }
        }
    }

    /**
     * Stops the background reader.
     */
    private fun stopBackgroundReader() {
        isReading.set(false)
        backgroundReaderJob?.cancel()
        backgroundReaderJob = null
    }

    override fun startBackgroundReader() {
        if (isConnected && !isReading.get()) {
            startBackgroundReaderInternal()
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ERROR HANDLING
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Handles IOException during communication.
     */
    private suspend fun handleIOException(e: IOException) {
        stats.recordError()

        // Check if device is still connected
        if (!isDeviceConnected()) {
            _connectionState.value = ConnectionState.Error(
                ConnectionException("USB device disconnected: ${e.message}", e),
                isRecoverable = false
            )
        } else {
            _connectionState.value = ConnectionState.Error(
                CommunicationException("Communication error: ${e.message}", e),
                isRecoverable = true
            )

            // Attempt recovery if configured
            if (config.autoReconnect) {
                handleCommunicationError(e)
            }
        }
    }

    /**
     * Handles device disconnection event.
     */
    private suspend fun handleDeviceDisconnected() {
        if (!isConnected) return

        _connectionState.value = ConnectionState.Error(
            ConnectionException("USB device disconnected"),
            isRecoverable = false
        )
    }

    /**
     * Checks if the USB device is still connected.
     */
    private fun isDeviceConnected(): Boolean {
        val device = usbDevice ?: return false
        return usbManager.deviceList.containsKey(device.deviceName)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // UTILITIES
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Gets the connected device.
     */
    fun getDevice(): UsbDevice? = usbDevice

    /**
     * Gets the device name.
     */
    fun getDeviceName(): String? = usbDevice?.deviceName

    /**
     * Gets the device product name.
     */
    fun getProductName(): String? = usbDevice?.productName

    /**
     * Gets the device manufacturer name.
     */
    fun getManufacturerName(): String? = usbDevice?.manufacturerName

    /**
     * Gets the driver type being used.
     */
    fun getDriverType(): USBDriverType? {
        return usbDevice?.let { USBDriverFactory.detectDriverType(it) }
    }

    /**
     * Updates the USB configuration.
     */
    fun updateConfig(config: USBConnectionConfig) {
        this.usbConfig = config
    }

    /**
     * Gets the current connection attempt number.
     */
    fun getConnectionAttempt(): Int = connectionAttempt.get()

    /**
     * Resets the USB device.
     *
     * @return true if reset was successful
     */
    suspend fun resetDevice(): Boolean = withContext(dispatcher) {
        try {
            serialPort?.purgeBuffers(input = true, output = true)
            delay(USBConstants.USB_RESET_DELAY)
            true
        } catch (e: Exception) {
            false
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // FACTORY
    // ═══════════════════════════════════════════════════════════════════════

    companion object {

        /**
         * Creates a USBConnection with default configuration.
         */
        fun create(context: Context): USBConnection {
            return USBConnection(context)
        }

        /**
         * Creates a USBConnection with custom configuration.
         */
        fun create(
            context: Context,
            config: USBConnectionConfig
        ): USBConnection {
            return USBConnection(context, usbConfig = config)
        }

        /**
         * Checks if USB Host is supported on the device.
         */
        fun isUSBHostSupported(context: Context): Boolean {
            return context.packageManager.hasSystemFeature(
                android.content.pm.PackageManager.FEATURE_USB_HOST
            )
        }

        /**
         * Gets the list of connected USB devices.
         */
        fun getConnectedDevices(context: Context): List<UsbDevice> {
            val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
            return usbManager.deviceList.values.toList()
        }

        /**
         * Gets the list of connected OBD adapters.
         */
        fun getConnectedOBDAdapters(context: Context): List<UsbDevice> {
            return getConnectedDevices(context).filter { device ->
                USBDriverFactory.isOBDAdapter(device)
            }
        }
    }
}
