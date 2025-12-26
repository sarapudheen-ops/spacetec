/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 *
 * This file is part of the SpaceTec professional automotive diagnostic system.
 */

package com.spacetec.obd.scanner.bluetooth.classic

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.spacetec.core.common.exceptions.CommunicationException
import com.spacetec.core.common.exceptions.ConnectionException
import com.spacetec.core.common.result.Result
import com.spacetec.core.domain.models.scanner.ScannerConnectionType
import com.spacetec.obd.scanner.core.BaseScannerConnection
import com.spacetec.obd.scanner.core.ConnectionConfig
import com.spacetec.obd.scanner.core.ConnectionInfo
import com.spacetec.obd.scanner.core.ConnectionState
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
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.lang.reflect.Method
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

/**
 * Bluetooth Classic SPP (Serial Port Profile) connection implementation.
 *
 * Provides robust Bluetooth socket management for connecting to ELM327 and
 * compatible OBD-II adapters using the Serial Port Profile (RFCOMM).
 *
 * ## Features
 *
 * - Secure and insecure socket connections
 * - Multiple fallback socket creation strategies for compatibility
 * - Background data reading with coroutines
 * - Automatic reconnection on connection loss
 * - Thread-safe operations with mutex protection
 * - Comprehensive error handling and recovery
 *
 * ## Socket Creation Strategy
 *
 * The connection attempts to create a socket in the following order:
 * 1. Standard secure socket with SPP UUID
 * 2. Insecure socket with SPP UUID (if secure fails)
 * 3. Reflection-based RFCOMM socket (fallback for problematic devices)
 *
 * ## Usage Example
 *
 * ```kotlin
 * val connection = BluetoothClassicConnection(context)
 *
 * // Connect to device
 * val result = connection.connect("AA:BB:CC:DD:EE:FF")
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
 * @param context Android context for accessing Bluetooth services
 * @param bluetoothAdapter Bluetooth adapter instance (optional, auto-obtained if null)
 * @param config Bluetooth-specific configuration
 * @param dispatcher Coroutine dispatcher for I/O operations
 *
 * @author SpaceTec Development Team
 * @since 1.0.0
 */
class BluetoothClassicConnection @Inject constructor(
    private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter? = null,
    private var classicConfig: BluetoothClassicConfig = BluetoothClassicConfig.DEFAULT,
    dispatcher: CoroutineDispatcher = Dispatchers.IO
) : BaseScannerConnection(dispatcher) {

    override val connectionType: ScannerConnectionType = ScannerConnectionType.BLUETOOTH_CLASSIC

    private val adapter: BluetoothAdapter? by lazy {
        bluetoothAdapter ?: getBluetoothAdapter()
    }

    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var device: BluetoothDevice? = null

    private val readBuffer = ByteArray(BluetoothClassicConstants.READ_BUFFER_SIZE)
    private val streamLock = Mutex()

    private var backgroundReaderJob: Job? = null
    private val isReading = AtomicBoolean(false)
    private val connectionAttempt = AtomicInteger(0)


    // ═══════════════════════════════════════════════════════════════════════
    // CONNECTION IMPLEMENTATION
    // ═══════════════════════════════════════════════════════════════════════

    @SuppressLint("MissingPermission")
    override suspend fun doConnect(
        address: String,
        config: ConnectionConfig
    ): ConnectionInfo = withContext(dispatcher) {

        // Check Bluetooth availability
        val btAdapter = adapter
            ?: throw ConnectionException("Bluetooth not available on this device")

        if (!btAdapter.isEnabled) {
            throw ConnectionException("Bluetooth is not enabled. Please enable Bluetooth and try again.")
        }

        // Validate MAC address format
        if (!BluetoothAdapter.checkBluetoothAddress(address)) {
            throw ConnectionException("Invalid Bluetooth address format: $address")
        }

        // Check permissions
        if (!hasBluetoothPermissions()) {
            throw ConnectionException("Bluetooth permissions not granted. Please grant Bluetooth permissions.")
        }

        // Cancel discovery if needed (improves connection speed significantly)
        if (classicConfig.cancelDiscoveryBeforeConnect && btAdapter.isDiscovering) {
            btAdapter.cancelDiscovery()
            delay(100) // Give discovery time to stop
        }

        // Get remote device
        device = btAdapter.getRemoteDevice(address)
        val btDevice = device ?: throw ConnectionException("Device not found: $address")

        // Attempt connection with retries
        var lastException: Exception? = null
        var attempt = 0

        while (attempt < BluetoothClassicConstants.MAX_CONNECTION_RETRIES) {
            attempt++
            connectionAttempt.set(attempt)

            try {
                // Create and connect socket
                socket = createSocket(btDevice, attempt)

                socket?.let { sock ->
                    withTimeout(classicConfig.baseConfig.connectionTimeout) {
                        sock.connect()
                    }

                    // Get streams
                    inputStream = sock.inputStream
                    outputStream = sock.outputStream
                } ?: run {
                    throw IOException("Failed to create socket")
                }

                // Start background reader
                startBackgroundReaderInternal()

                // Build connection info
                return@withContext ConnectionInfo(
                    connectedAt = System.currentTimeMillis(),
                    localAddress = btAdapter.address,
                    remoteAddress = address,
                    mtu = BluetoothClassicConstants.DEFAULT_MTU,
                    signalStrength = null, // Not available after connection
                    connectionType = ScannerConnectionType.BLUETOOTH_CLASSIC
                )

            } catch (e: CancellationException) {
                cleanupSocket()
                throw e
            } catch (e: Exception) {
                lastException = e
                cleanupSocket()

                if (attempt < BluetoothClassicConstants.MAX_CONNECTION_RETRIES) {
                    delay(BluetoothClassicConstants.CONNECTION_RETRY_DELAY * attempt)
                }
            }
        }

        throw ConnectionException(
            "Failed to connect after $attempt attempts: ${lastException?.message}",
            lastException
        )
    }

    /**
     * Creates a Bluetooth socket using various strategies for maximum compatibility.
     *
     * @param device Remote Bluetooth device
     * @param attempt Current connection attempt number
     * @return BluetoothSocket ready for connection
     */
    @SuppressLint("MissingPermission")
    private fun createSocket(device: BluetoothDevice, attempt: Int): BluetoothSocket {
        return when {
            attempt == 1 && classicConfig.secure -> {
                // First attempt: secure socket with standard UUID
                try {
                    device.createRfcommSocketToServiceRecord(classicConfig.uuid)
                } catch (e: IOException) {
                    // Fall through to insecure
                    device.createInsecureRfcommSocketToServiceRecord(classicConfig.uuid)
                }
            }

            attempt == 2 || !classicConfig.secure -> {
                // Second attempt or insecure requested: insecure socket
                try {
                    device.createInsecureRfcommSocketToServiceRecord(classicConfig.uuid)
                } catch (e: IOException) {
                    // Try fallback
                    createFallbackSocket(device, classicConfig.rfcommChannel)
                }
            }

            classicConfig.useFallback -> {
                // Third+ attempt: use reflection fallback
                createFallbackSocket(device, classicConfig.rfcommChannel)
            }

            else -> {
                device.createRfcommSocketToServiceRecord(classicConfig.uuid)
            }
        }
    }

    /**
     * Creates a socket using reflection (fallback method for problematic devices).
     *
     * This method uses reflection to access the hidden createRfcommSocket method,
     * which works around compatibility issues with some devices and adapters.
     *
     * @param device Remote Bluetooth device
     * @param channel RFCOMM channel number
     * @return BluetoothSocket
     */
    @Suppress("UNCHECKED_CAST")
    private fun createFallbackSocket(device: BluetoothDevice, channel: Int): BluetoothSocket {
        try {
            // Try createRfcommSocket (hidden API)
            val createMethod: Method = device.javaClass.getMethod(
                "createRfcommSocket",
                Int::class.javaPrimitiveType
            )
            return createMethod.invoke(device, channel) as BluetoothSocket
        } catch (e: Exception) {
            // Try createInsecureRfcommSocket (hidden API)
            try {
                val createInsecureMethod: Method = device.javaClass.getMethod(
                    "createInsecureRfcommSocket",
                    Int::class.javaPrimitiveType
                )
                return createInsecureMethod.invoke(device, channel) as BluetoothSocket
            } catch (e2: Exception) {
                throw ConnectionException(
                    "Failed to create fallback socket: ${e.message}",
                    e
                )
            }
        }
    }

    /**
     * Cleans up socket and streams.
     */
    private fun cleanupSocket() {
        try {
            inputStream?.close()
        } catch (_: Exception) {}

        try {
            outputStream?.close()
        } catch (_: Exception) {}

        try {
            socket?.close()
        } catch (_: Exception) {}

        inputStream = null
        outputStream = null
        socket = null
    }

    // ═══════════════════════════════════════════════════════════════════════
    // DISCONNECTION
    // ═══════════════════════════════════════════════════════════════════════

    override suspend fun doDisconnect(graceful: Boolean) = withContext(dispatcher) {
        // Stop background reader
        stopBackgroundReader()

        // Close streams and socket
        streamLock.withLock {
            if (graceful) {
                try {
                    // Flush output before closing
                    outputStream?.flush()
                    delay(50)
                } catch (_: Exception) {}
            }

            cleanupSocket()
        }

        device = null
    }


    // ═══════════════════════════════════════════════════════════════════════
    // DATA TRANSFER
    // ═══════════════════════════════════════════════════════════════════════

    override suspend fun doWrite(data: ByteArray): Int = streamLock.withLock {
        val stream = outputStream
            ?: throw CommunicationException("Output stream not available")

        try {
            stream.write(data)
            if (config.flushAfterWrite) {
                stream.flush()
            }
            return data.size
        } catch (e: IOException) {
            handleIOException(e)
            throw CommunicationException("Write failed: ${e.message}", e)
        }
    }

    override suspend fun doRead(buffer: ByteArray, timeout: Long): Int {
        val stream = inputStream
            ?: throw CommunicationException("Input stream not available")

        val startTime = System.currentTimeMillis()

        while (System.currentTimeMillis() - startTime < timeout) {
            try {
                val available = stream.available()
                if (available > 0) {
                    val toRead = minOf(available, buffer.size)
                    return stream.read(buffer, 0, toRead)
                }
                delay(BluetoothClassicConstants.BACKGROUND_READ_INTERVAL)
            } catch (e: IOException) {
                handleIOException(e)
                throw CommunicationException("Read failed: ${e.message}", e)
            }
        }

        return 0 // Timeout, no data
    }

    override suspend fun doAvailable(): Int {
        return try {
            inputStream?.available() ?: 0
        } catch (e: IOException) {
            0
        }
    }

    override suspend fun doClearBuffers() {
        val stream = inputStream ?: return

        try {
            // Drain input buffer
            while (stream.available() > 0) {
                val toSkip = stream.available().toLong()
                stream.skip(toSkip)
            }
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
                val buffer = ByteArray(BluetoothClassicConstants.READ_BUFFER_SIZE)

                while (isActive && isConnected) {
                    try {
                        val stream = inputStream
                        if (stream == null) {
                            delay(100)
                            continue
                        }

                        val available = stream.available()
                        if (available > 0) {
                            val bytesRead = stream.read(buffer, 0, minOf(available, buffer.size))
                            if (bytesRead > 0) {
                                val data = buffer.copyOf(bytesRead)
                                processIncomingData(data)
                            } else if (bytesRead == -1) {
                                // Stream closed
                                handleStreamClosed()
                                break
                            }
                        } else {
                            delay(BluetoothClassicConstants.BACKGROUND_READ_INTERVAL)
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

        // Check if connection is lost
        if (!isSocketConnected()) {
            _connectionState.value = ConnectionState.Error(
                ConnectionException("Connection lost: ${e.message}", e),
                isRecoverable = true
            )

            // Attempt reconnection if configured
            if (config.autoReconnect) {
                handleCommunicationError(e)
            }
        }
    }

    /**
     * Handles stream closed event.
     */
    private suspend fun handleStreamClosed() {
        if (!isConnected) return

        _connectionState.value = ConnectionState.Error(
            ConnectionException("Connection closed by remote device"),
            isRecoverable = true
        )

        if (config.autoReconnect) {
            scope.launch {
                reconnect()
            }
        }
    }

    /**
     * Checks if the socket is still connected.
     */
    private fun isSocketConnected(): Boolean {
        return try {
            socket?.isConnected == true
        } catch (e: Exception) {
            false
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // UTILITIES
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Gets the Bluetooth adapter from system service.
     */
    private fun getBluetoothAdapter(): BluetoothAdapter? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            bluetoothManager?.adapter
        } else {
            @Suppress("DEPRECATION")
            BluetoothAdapter.getDefaultAdapter()
        }
    }

    /**
     * Checks if required Bluetooth permissions are granted.
     */
    private fun hasBluetoothPermissions(): Boolean {
        return BluetoothClassicConstants.BLUETOOTH_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Gets the connected device name.
     */
    @SuppressLint("MissingPermission")
    fun getDeviceName(): String? {
        return try {
            device?.name
        } catch (e: SecurityException) {
            null
        }
    }

    /**
     * Gets the connected device address.
     */
    fun getDeviceAddress(): String? {
        return device?.address
    }

    /**
     * Updates the Bluetooth configuration.
     */
    fun updateConfig(config: BluetoothClassicConfig) {
        this.classicConfig = config
    }

    /**
     * Gets the current connection attempt number.
     */
    fun getConnectionAttempt(): Int = connectionAttempt.get()

    // ═══════════════════════════════════════════════════════════════════════
    // FACTORY
    // ═══════════════════════════════════════════════════════════════════════

    companion object {

        /**
         * Creates a BluetoothClassicConnection with default configuration.
         */
        fun create(context: Context): BluetoothClassicConnection {
            return BluetoothClassicConnection(context)
        }

        /**
         * Creates a BluetoothClassicConnection with custom configuration.
         */
        fun create(
            context: Context,
            config: BluetoothClassicConfig
        ): BluetoothClassicConnection {
            return BluetoothClassicConnection(context, classicConfig = config)
        }

        /**
         * Checks if Bluetooth is available on the device.
         */
        fun isBluetoothAvailable(context: Context): Boolean {
            val bluetoothManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            } else {
                null
            }
            return bluetoothManager?.adapter != null ||
                   @Suppress("DEPRECATION") BluetoothAdapter.getDefaultAdapter() != null
        }

        /**
         * Checks if Bluetooth is enabled.
         */
        @SuppressLint("MissingPermission")
        fun isBluetoothEnabled(context: Context): Boolean {
            val adapter = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            } else {
                @Suppress("DEPRECATION")
                BluetoothAdapter.getDefaultAdapter()
            }
            return adapter?.isEnabled == true
        }
    }
}