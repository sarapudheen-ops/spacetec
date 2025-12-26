/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 *
 * This file is part of the SpaceTec professional automotive diagnostic system.
 */

package com.spacetec.obd.scanner.wifi

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
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
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

/**
 * WiFi TCP socket connection implementation for OBD-II scanners.
 *
 * Provides robust TCP/IP socket communication for connecting to WiFi-enabled
 * diagnostic scanners. Supports dynamic timeout adjustment based on network
 * conditions and automatic reconnection on connection loss.
 *
 * ## Features
 *
 * - TCP socket-based communication with configurable timeouts
 * - Hostname resolution and direct IP connection support
 * - Dynamic timeout adjustment based on response times
 * - Network condition monitoring and quality assessment
 * - Automatic reconnection with exponential backoff
 * - Background data reading with coroutines
 * - Disconnection detection within 5 seconds (per requirement 2.3)
 *
 * ## Usage Example
 *
 * ```kotlin
 * val connection = WiFiConnection(context)
 *
 * // Connect to device
 * val result = connection.connect("192.168.0.10:35000")
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
 * @param context Android context for accessing network services
 * @param wifiConfig WiFi-specific configuration
 * @param dispatcher Coroutine dispatcher for I/O operations
 *
 * @author SpaceTec Development Team
 * @since 1.0.0
 */
class WiFiConnection @Inject constructor(
    private val context: Context,
    private var wifiConfig: WiFiConfig = WiFiConfig.DEFAULT,
    dispatcher: CoroutineDispatcher = Dispatchers.IO
) : BaseScannerConnection(dispatcher) {

    override val connectionType: ScannerConnectionType = ScannerConnectionType.WIFI

    private var socket: Socket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    private val readBuffer = ByteArray(WiFiConstants.READ_BUFFER_SIZE)
    private val streamLock = Mutex()

    private var backgroundReaderJob: Job? = null
    private var disconnectionMonitorJob: Job? = null
    private val isReading = AtomicBoolean(false)
    private val connectionAttempt = AtomicInteger(0)

    // Network condition monitoring
    private val networkConditionMonitor = NetworkConditionMonitor()
    private var currentNetworkCondition = NetworkCondition()

    // Dynamic timeout tracking
    private val currentDynamicTimeout = AtomicLong(wifiConfig.baseConfig.readTimeout)


    // ═══════════════════════════════════════════════════════════════════════
    // CONNECTION IMPLEMENTATION
    // ═══════════════════════════════════════════════════════════════════════

    override suspend fun doConnect(
        address: String,
        config: ConnectionConfig
    ): ConnectionInfo = withContext(dispatcher) {

        // Check WiFi availability
        if (!isWiFiAvailable()) {
            throw ConnectionException("WiFi is not available or not connected to a network")
        }

        // Parse address (IP:port or hostname:port)
        val (host, port) = parseAddress(address)

        // Validate port
        if (port !in 1..65535) {
            throw ConnectionException("Invalid port number: $port")
        }

        // Resolve hostname if needed
        val inetAddress = try {
            InetAddress.getByName(host)
        } catch (e: UnknownHostException) {
            throw ConnectionException("Host not found: $host", e)
        }

        // Attempt connection with retries
        var lastException: Exception? = null
        var attempt = 0

        while (attempt < WiFiConstants.MAX_CONNECTION_RETRIES) {
            attempt++
            connectionAttempt.set(attempt)

            try {
                // Create socket
                socket = Socket().apply {
                    // Configure socket options
                    soTimeout = config.readTimeout.toInt()
                    tcpNoDelay = wifiConfig.enableNoDelay
                    keepAlive = wifiConfig.enableKeepAlive
                    
                    if (wifiConfig.socketLingerTimeout >= 0) {
                        setSoLinger(true, wifiConfig.socketLingerTimeout)
                    }
                    
                    receiveBufferSize = config.bufferSize
                    sendBufferSize = config.bufferSize
                }

                // Connect with timeout
                socket?.let { sock ->
                    withTimeout(config.connectionTimeout) {
                        sock.connect(
                            InetSocketAddress(inetAddress, port),
                            config.connectionTimeout.toInt()
                        )
                    }

                    // Get streams
                    inputStream = sock.getInputStream()
                    outputStream = sock.getOutputStream()
                } ?: run {
                    throw IOException("Failed to create socket")
                }

                // Reset network condition monitor
                networkConditionMonitor.reset()
                currentDynamicTimeout.set(config.readTimeout)

                // Start background reader
                startBackgroundReaderInternal()

                // Start disconnection monitor
                startDisconnectionMonitor()

                // Build connection info
                return@withContext ConnectionInfo(
                    connectedAt = System.currentTimeMillis(),
                    localAddress = socket?.localAddress?.hostAddress,
                    remoteAddress = "$host:$port",
                    mtu = WiFiConstants.DEFAULT_MTU,
                    signalStrength = null,
                    connectionType = ScannerConnectionType.WIFI
                )

            } catch (e: CancellationException) {
                cleanupSocket()
                throw e
            } catch (e: Exception) {
                lastException = e
                cleanupSocket()

                if (attempt < WiFiConstants.MAX_CONNECTION_RETRIES) {
                    delay(WiFiConstants.CONNECTION_RETRY_DELAY * attempt)
                }
            }
        }

        throw ConnectionException(
            "Failed to connect after $attempt attempts: ${lastException?.message}",
            lastException
        )
    }

    /**
     * Parses an address string into host and port components.
     *
     * @param address Address in format "host:port" or "host" (uses default port)
     * @return Pair of (host, port)
     */
    private fun parseAddress(address: String): Pair<String, Int> {
        val parts = address.split(":")
        return when (parts.size) {
            1 -> Pair(parts[0], wifiConfig.port)
            2 -> {
                val port = parts[1].toIntOrNull()
                    ?: throw ConnectionException("Invalid port in address: $address")
                Pair(parts[0], port)
            }
            else -> throw ConnectionException("Invalid address format: $address")
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
        // Stop background jobs
        stopBackgroundReader()
        stopDisconnectionMonitor()

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

        val effectiveTimeout = if (wifiConfig.enableDynamicTimeout) {
            currentDynamicTimeout.get().coerceIn(
                wifiConfig.minDynamicTimeout,
                wifiConfig.maxDynamicTimeout
            )
        } else {
            timeout
        }

        val startTime = System.currentTimeMillis()

        try {
            // Set socket timeout for this read
            socket?.soTimeout = effectiveTimeout.toInt()

            val available = stream.available()
            if (available > 0) {
                val toRead = minOf(available, buffer.size)
                val bytesRead = stream.read(buffer, 0, toRead)
                
                // Record response time for network condition monitoring
                val responseTime = System.currentTimeMillis() - startTime
                networkConditionMonitor.recordResponseTime(responseTime)
                updateDynamicTimeout()
                
                return bytesRead
            }

            // Wait for data with polling
            val deadline = startTime + effectiveTimeout
            while (System.currentTimeMillis() < deadline) {
                val nowAvailable = stream.available()
                if (nowAvailable > 0) {
                    val toRead = minOf(nowAvailable, buffer.size)
                    val bytesRead = stream.read(buffer, 0, toRead)
                    
                    val responseTime = System.currentTimeMillis() - startTime
                    networkConditionMonitor.recordResponseTime(responseTime)
                    updateDynamicTimeout()
                    
                    return bytesRead
                }
                delay(WiFiConstants.BACKGROUND_READ_INTERVAL)
            }

            return 0 // Timeout, no data

        } catch (e: SocketTimeoutException) {
            networkConditionMonitor.recordTimeout()
            updateDynamicTimeout()
            return 0
        } catch (e: IOException) {
            handleIOException(e)
            throw CommunicationException("Read failed: ${e.message}", e)
        }
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
                val buffer = ByteArray(WiFiConstants.READ_BUFFER_SIZE)

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
                            delay(WiFiConstants.BACKGROUND_READ_INTERVAL)
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
    // DISCONNECTION MONITOR
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Starts the disconnection monitor to detect connection loss within 5 seconds.
     * Per requirement 2.3: detect disconnection within 5 seconds.
     */
    private fun startDisconnectionMonitor() {
        disconnectionMonitorJob?.cancel()
        disconnectionMonitorJob = scope.launch {
            while (isActive && isConnected) {
                delay(1000) // Check every second

                if (!isSocketConnected()) {
                    // Connection lost - detected within 5 seconds
                    handleConnectionLost()
                    break
                }
            }
        }
    }

    /**
     * Stops the disconnection monitor.
     */
    private fun stopDisconnectionMonitor() {
        disconnectionMonitorJob?.cancel()
        disconnectionMonitorJob = null
    }

    /**
     * Handles detected connection loss.
     */
    private suspend fun handleConnectionLost() {
        if (!isConnected) return

        _connectionState.value = ConnectionState.Error(
            ConnectionException("WiFi connection lost"),
            isRecoverable = true
        )

        // Attempt immediate reconnection per requirement 2.3
        if (config.autoReconnect) {
            scope.launch {
                reconnect()
            }
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
        networkConditionMonitor.recordError()

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
            val s = socket
            s != null && s.isConnected && !s.isClosed && !s.isInputShutdown && !s.isOutputShutdown
        } catch (e: Exception) {
            false
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // DYNAMIC TIMEOUT ADJUSTMENT
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Updates the dynamic timeout based on network conditions.
     * Per requirement 2.5: adjust timeout values dynamically based on response times.
     */
    private fun updateDynamicTimeout() {
        if (!wifiConfig.enableDynamicTimeout) return

        val condition = networkConditionMonitor.getCondition()
        currentNetworkCondition = condition

        val newTimeout = when (condition.quality) {
            NetworkQuality.EXCELLENT -> (config.readTimeout * 0.8).toLong()
            NetworkQuality.GOOD -> config.readTimeout
            NetworkQuality.FAIR -> (config.readTimeout * 1.2).toLong()
            NetworkQuality.POOR -> (config.readTimeout * 1.5).toLong()
            NetworkQuality.VERY_POOR -> (config.readTimeout * 2.0).toLong()
            NetworkQuality.UNKNOWN -> config.readTimeout
        }

        currentDynamicTimeout.set(
            newTimeout.coerceIn(wifiConfig.minDynamicTimeout, wifiConfig.maxDynamicTimeout)
        )
    }

    // ═══════════════════════════════════════════════════════════════════════
    // NETWORK UTILITIES
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Checks if WiFi is available and connected.
     */
    private fun isWiFiAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            networkInfo?.isConnected == true && networkInfo.type == ConnectivityManager.TYPE_WIFI
        }
    }

    /**
     * Gets the current network condition.
     */
    fun getNetworkCondition(): NetworkCondition = currentNetworkCondition

    /**
     * Gets the current dynamic timeout value.
     */
    fun getCurrentTimeout(): Long = currentDynamicTimeout.get()

    /**
     * Updates the WiFi configuration.
     */
    fun updateConfig(config: WiFiConfig) {
        this.wifiConfig = config
    }

    /**
     * Gets the current connection attempt number.
     */
    fun getConnectionAttempt(): Int = connectionAttempt.get()

    /**
     * Gets the connected host address.
     */
    fun getHostAddress(): String? {
        return socket?.inetAddress?.hostAddress
    }

    /**
     * Gets the connected port.
     */
    fun getPort(): Int? {
        return socket?.port
    }

    // ═══════════════════════════════════════════════════════════════════════
    // FACTORY
    // ═══════════════════════════════════════════════════════════════════════

    companion object {

        /**
         * Creates a WiFiConnection with default configuration.
         */
        fun create(context: Context): WiFiConnection {
            return WiFiConnection(context)
        }

        /**
         * Creates a WiFiConnection with custom configuration.
         */
        fun create(
            context: Context,
            config: WiFiConfig
        ): WiFiConnection {
            return WiFiConnection(context, wifiConfig = config)
        }

        /**
         * Checks if WiFi is available on the device.
         */
        fun isWiFiAvailable(context: Context): Boolean {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false

            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork ?: return false
                val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            } else {
                @Suppress("DEPRECATION")
                val networkInfo = connectivityManager.activeNetworkInfo
                networkInfo?.isConnected == true && networkInfo.type == ConnectivityManager.TYPE_WIFI
            }
        }

        /**
         * Validates an IP address format.
         */
        fun isValidIpAddress(address: String): Boolean {
            val ipPart = address.split(":").firstOrNull() ?: address
            val parts = ipPart.split(".")
            if (parts.size != 4) return false
            return parts.all { part ->
                val num = part.toIntOrNull()
                num != null && num in 0..255
            }
        }

        /**
         * Validates a port number.
         */
        fun isValidPort(port: Int): Boolean {
            return port in 1..65535
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// NETWORK CONDITION MONITOR
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Monitors network conditions and calculates quality metrics.
 */
class NetworkConditionMonitor {
    
    private val responseTimes = mutableListOf<Long>()
    private var timeoutCount = 0
    private var errorCount = 0
    private var totalRequests = 0
    private val lock = Any()

    /**
     * Records a successful response time.
     */
    fun recordResponseTime(timeMs: Long) {
        synchronized(lock) {
            totalRequests++
            responseTimes.add(timeMs)
            
            // Keep only recent history
            while (responseTimes.size > WiFiConstants.RESPONSE_TIME_HISTORY_SIZE) {
                responseTimes.removeAt(0)
            }
        }
    }

    /**
     * Records a timeout.
     */
    fun recordTimeout() {
        synchronized(lock) {
            totalRequests++
            timeoutCount++
        }
    }

    /**
     * Records an error.
     */
    fun recordError() {
        synchronized(lock) {
            totalRequests++
            errorCount++
        }
    }

    /**
     * Resets all statistics.
     */
    fun reset() {
        synchronized(lock) {
            responseTimes.clear()
            timeoutCount = 0
            errorCount = 0
            totalRequests = 0
        }
    }

    /**
     * Gets the current network condition assessment.
     */
    fun getCondition(): NetworkCondition {
        synchronized(lock) {
            if (responseTimes.isEmpty()) {
                return NetworkCondition(quality = NetworkQuality.UNKNOWN)
            }

            val avgResponseTime = responseTimes.average().toLong()
            val minResponseTime = responseTimes.minOrNull() ?: 0
            val maxResponseTime = responseTimes.maxOrNull() ?: 0
            val jitter = maxResponseTime - minResponseTime

            val packetLossRate = if (totalRequests > 0) {
                (timeoutCount + errorCount).toFloat() / totalRequests
            } else 0f

            val quality = NetworkQuality.fromResponseTime(avgResponseTime)

            // Calculate recommended timeout based on conditions
            val recommendedTimeout = when (quality) {
                NetworkQuality.EXCELLENT -> (avgResponseTime * 2).coerceAtLeast(1000)
                NetworkQuality.GOOD -> (avgResponseTime * 2.5).toLong().coerceAtLeast(2000)
                NetworkQuality.FAIR -> (avgResponseTime * 3).coerceAtLeast(3000)
                NetworkQuality.POOR -> (avgResponseTime * 4).coerceAtLeast(5000)
                NetworkQuality.VERY_POOR -> (avgResponseTime * 5).coerceAtLeast(10000)
                NetworkQuality.UNKNOWN -> WiFiConstants.SOCKET_READ_TIMEOUT
            }

            return NetworkCondition(
                quality = quality,
                averageResponseTime = avgResponseTime,
                minResponseTime = minResponseTime,
                maxResponseTime = maxResponseTime,
                packetLossRate = packetLossRate,
                jitter = jitter,
                recommendedTimeout = recommendedTimeout,
                lastUpdated = System.currentTimeMillis()
            )
        }
    }
}