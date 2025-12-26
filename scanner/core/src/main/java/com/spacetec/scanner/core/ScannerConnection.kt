/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 *
 * This file is part of the SpaceTec professional automotive diagnostic system.
 */

package com.spacetec.obd.scanner.core

import com.spacetec.core.common.exceptions.CommunicationException
import com.spacetec.core.common.exceptions.ConnectionException
import com.spacetec.core.common.exceptions.TimeoutException
import com.spacetec.core.common.result.Result
import com.spacetec.core.domain.models.scanner.ScannerConnectionType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.io.Closeable
import java.nio.charset.Charset
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

// ═══════════════════════════════════════════════════════════════════════════
// CONNECTION STATE
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Represents the current state of a scanner connection.
 *
 * This sealed class provides a type-safe representation of all possible
 * connection states, including metadata about the connection when connected
 * or error information when in an error state.
 *
 * ## State Transitions
 *
 * ```
 * Disconnected ──► Connecting ──► Connected
 *       ▲              │              │
 *       │              ▼              ▼
 *       └────────── Error ◄───── Reconnecting
 * ```
 *
 * @author SpaceTec Development Team
 * @since 1.0.0
 */
sealed class ConnectionState {
    
    /**
     * Connection is not established.
     * This is the initial state and the state after disconnection.
     */
    object Disconnected : ConnectionState() {
        override fun toString(): String = "Disconnected"
    }
    
    /**
     * Connection is being established.
     * Socket/port connection is in progress.
     */
    object Connecting : ConnectionState() {
        override fun toString(): String = "Connecting"
    }
    
    /**
     * Connection is established and ready for communication.
     *
     * @property info Details about the established connection
     */
    data class Connected(val info: ConnectionInfo) : ConnectionState() {
        override fun toString(): String = "Connected(${info.remoteAddress})"
    }
    
    /**
     * An error occurred during connection or communication.
     *
     * @property exception The exception that caused the error
     * @property isRecoverable Whether the connection can potentially be recovered
     * @property attemptCount Number of connection attempts made
     */
    data class Error(
        val exception: Throwable,
        val isRecoverable: Boolean = true,
        val attemptCount: Int = 1
    ) : ConnectionState() {
        override fun toString(): String = "Error(${exception.message}, recoverable=$isRecoverable)"
    }
    
    /**
     * Connection was lost and reconnection is in progress.
     *
     * @property attempt Current reconnection attempt number
     * @property maxAttempts Maximum number of reconnection attempts
     */
    data class Reconnecting(
        val attempt: Int = 1,
        val maxAttempts: Int = 3
    ) : ConnectionState() {
        override fun toString(): String = "Reconnecting($attempt/$maxAttempts)"
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // CONVENIENCE PROPERTIES
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Returns true if currently connected and ready for communication.
     */
    val isConnected: Boolean
        get() = this is Connected
    
    /**
     * Returns true if a connection attempt is in progress.
     */
    val isConnecting: Boolean
        get() = this is Connecting || this is Reconnecting
    
    /**
     * Returns true if in an error state.
     */
    val isError: Boolean
        get() = this is Error
    
    /**
     * Returns true if disconnected (not connected, not connecting, not error).
     */
    val isDisconnected: Boolean
        get() = this is Disconnected
    
    /**
     * Returns true if a connection attempt can be made.
     */
    val canConnect: Boolean
        get() = this is Disconnected || (this is Error && (this as Error).isRecoverable)
    
    /**
     * Returns the connection info if connected, null otherwise.
     */
    val connectionInfo: ConnectionInfo?
        get() = (this as? Connected)?.info
    
    /**
     * Returns the error if in error state, null otherwise.
     */
    val error: Throwable?
        get() = (this as? Error)?.exception
}

/**
 * Information about an established connection.
 *
 * @property connectedAt Timestamp when connection was established
 * @property localAddress Local address/endpoint (if applicable)
 * @property remoteAddress Remote address/endpoint
 * @property mtu Maximum Transmission Unit size
 * @property signalStrength Signal strength in dBm (Bluetooth only)
 * @property connectionType Type of connection
 */
data class ConnectionInfo(
    val connectedAt: Long = System.currentTimeMillis(),
    val localAddress: String? = null,
    val remoteAddress: String,
    val mtu: Int = DEFAULT_MTU,
    val signalStrength: Int? = null,
    val connectionType: ScannerConnectionType? = null
) {
    
    /**
     * Time elapsed since connection was established.
     */
    val connectionDuration: Long
        get() = System.currentTimeMillis() - connectedAt
    
    /**
     * Connection duration formatted as string.
     */
    val connectionDurationFormatted: String
        get() {
            val seconds = connectionDuration / 1000
            val minutes = seconds / 60
            val hours = minutes / 60
            return when {
                hours > 0 -> "${hours}h ${minutes % 60}m"
                minutes > 0 -> "${minutes}m ${seconds % 60}s"
                else -> "${seconds}s"
            }
        }
    
    companion object {
        const val DEFAULT_MTU = 512
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// CONNECTION CONFIGURATION
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Configuration options for scanner connections.
 *
 * Provides fine-grained control over connection behavior including timeouts,
 * reconnection logic, and buffer sizes.
 *
 * @property connectionTimeout Maximum time to wait for connection establishment
 * @property readTimeout Maximum time to wait for read operations
 * @property writeTimeout Maximum time to wait for write operations
 * @property autoReconnect Whether to automatically attempt reconnection on disconnect
 * @property maxReconnectAttempts Maximum number of reconnection attempts
 * @property reconnectDelay Base delay between reconnection attempts (exponential backoff)
 * @property maxReconnectDelay Maximum delay between reconnection attempts
 * @property bufferSize Size of read/write buffers
 * @property keepAliveInterval Interval for keep-alive messages (0 = disabled)
 * @property flushAfterWrite Whether to flush after each write operation
 */
data class ConnectionConfig(
    val connectionTimeout: Long = DEFAULT_CONNECTION_TIMEOUT,
    val readTimeout: Long = DEFAULT_READ_TIMEOUT,
    val writeTimeout: Long = DEFAULT_WRITE_TIMEOUT,
    val autoReconnect: Boolean = true,
    val maxReconnectAttempts: Int = DEFAULT_MAX_RECONNECT_ATTEMPTS,
    val reconnectDelay: Long = DEFAULT_RECONNECT_DELAY,
    val maxReconnectDelay: Long = DEFAULT_MAX_RECONNECT_DELAY,
    val bufferSize: Int = DEFAULT_BUFFER_SIZE,
    val keepAliveInterval: Long = 0L,
    val flushAfterWrite: Boolean = true
) {
    
    /**
     * Calculates the delay for a specific reconnection attempt using exponential backoff.
     *
     * @param attempt Attempt number (1-based)
     * @return Delay in milliseconds
     */
    fun getReconnectDelay(attempt: Int): Long {
        val delay = reconnectDelay * (1 shl (attempt - 1).coerceIn(0, 5))
        return delay.coerceAtMost(maxReconnectDelay)
    }
    
    /**
     * Creates a copy with extended timeouts (useful for slow operations).
     *
     * @param factor Multiplication factor for timeouts
     * @return New config with extended timeouts
     */
    fun withExtendedTimeouts(factor: Int = 2): ConnectionConfig {
        return copy(
            connectionTimeout = connectionTimeout * factor,
            readTimeout = readTimeout * factor,
            writeTimeout = writeTimeout * factor
        )
    }
    
    /**
     * Creates a copy optimized for fast operations.
     */
    fun forFastOperations(): ConnectionConfig {
        return copy(
            readTimeout = 1000L,
            writeTimeout = 1000L
        )
    }
    
    /**
     * Creates a copy optimized for slow/long operations.
     */
    fun forSlowOperations(): ConnectionConfig {
        return copy(
            readTimeout = 30_000L,
            writeTimeout = 10_000L
        )
    }
    
    companion object {
        const val DEFAULT_CONNECTION_TIMEOUT = 10_000L
        const val DEFAULT_READ_TIMEOUT = 5_000L
        const val DEFAULT_WRITE_TIMEOUT = 5_000L
        const val DEFAULT_MAX_RECONNECT_ATTEMPTS = 3
        const val DEFAULT_RECONNECT_DELAY = 1_000L
        const val DEFAULT_MAX_RECONNECT_DELAY = 10_000L
        const val DEFAULT_BUFFER_SIZE = 4096
        
        /**
         * Default configuration.
         */
        val DEFAULT = ConnectionConfig()
        
        /**
         * Configuration for Bluetooth connections.
         */
        val BLUETOOTH = ConnectionConfig(
            connectionTimeout = 15_000L,
            readTimeout = 5_000L,
            autoReconnect = true,
            maxReconnectAttempts = 3
        )
        
        /**
         * Configuration for WiFi connections.
         */
        val WIFI = ConnectionConfig(
            connectionTimeout = 5_000L,
            readTimeout = 3_000L,
            autoReconnect = true,
            maxReconnectAttempts = 5
        )
        
        /**
         * Configuration for USB connections.
         */
        val USB = ConnectionConfig(
            connectionTimeout = 3_000L,
            readTimeout = 2_000L,
            autoReconnect = false,
            bufferSize = 8192
        )
        
        /**
         * Configuration for protocol initialization.
         */
        val INITIALIZATION = ConnectionConfig(
            connectionTimeout = 15_000L,
            readTimeout = 10_000L,
            autoReconnect = false
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// CONNECTION STATISTICS
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Statistics about connection performance and usage.
 *
 * @property bytesSent Total bytes sent over the connection
 * @property bytesReceived Total bytes received over the connection
 * @property commandsSent Number of commands sent
 * @property responsesReceived Number of responses received
 * @property errors Number of errors encountered
 * @property averageResponseTime Average response time in milliseconds
 * @property minResponseTime Minimum response time in milliseconds
 * @property maxResponseTime Maximum response time in milliseconds
 * @property lastActivityTime Timestamp of last activity
 * @property connectionUptime Total connection uptime in milliseconds
 */
data class ConnectionStatistics(
    val bytesSent: Long = 0,
    val bytesReceived: Long = 0,
    val commandsSent: Int = 0,
    val responsesReceived: Int = 0,
    val errors: Int = 0,
    val averageResponseTime: Long = 0,
    val minResponseTime: Long = Long.MAX_VALUE,
    val maxResponseTime: Long = 0,
    val lastActivityTime: Long = System.currentTimeMillis(),
    val connectionUptime: Long = 0
) {
    
    /**
     * Total bytes transferred (sent + received).
     */
    val totalBytes: Long
        get() = bytesSent + bytesReceived
    
    /**
     * Error rate as a percentage.
     */
    val errorRate: Float
        get() = if (commandsSent > 0) (errors.toFloat() / commandsSent) * 100 else 0f
    
    /**
     * Success rate as a percentage.
     */
    val successRate: Float
        get() = 100f - errorRate
    
    /**
     * Throughput in bytes per second (based on uptime).
     */
    val throughputBps: Float
        get() = if (connectionUptime > 0) (totalBytes.toFloat() / connectionUptime) * 1000 else 0f
    
    /**
     * Time since last activity in milliseconds.
     */
    val idleTime: Long
        get() = System.currentTimeMillis() - lastActivityTime
    
    /**
     * Returns true if connection appears idle (no activity for 30 seconds).
     */
    val isIdle: Boolean
        get() = idleTime > 30_000
    
    /**
     * Formats bytes sent/received as human-readable string.
     */
    fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1_000_000 -> "%.2f MB".format(bytes / 1_000_000.0)
            bytes >= 1_000 -> "%.2f KB".format(bytes / 1_000.0)
            else -> "$bytes B"
        }
    }
    
    /**
     * Returns a summary string of the statistics.
     */
    fun toSummary(): String {
        return buildString {
            appendLine("Connection Statistics:")
            appendLine("  Sent: ${formatBytes(bytesSent)} ($commandsSent commands)")
            appendLine("  Received: ${formatBytes(bytesReceived)} ($responsesReceived responses)")
            appendLine("  Errors: $errors (${String.format("%.1f", errorRate)}%)")
            appendLine("  Avg Response: ${averageResponseTime}ms")
            if (minResponseTime != Long.MAX_VALUE) {
                appendLine("  Min/Max Response: ${minResponseTime}ms / ${maxResponseTime}ms")
            }
        }
    }
}

/**
 * Mutable implementation of connection statistics for tracking.
 *
 * Thread-safe implementation using atomic operations.
 */
class MutableConnectionStatistics {
    
    private val _bytesSent = AtomicLong(0)
    private val _bytesReceived = AtomicLong(0)
    private val _commandsSent = AtomicLong(0)
    private val _responsesReceived = AtomicLong(0)
    private val _errors = AtomicLong(0)
    private val _totalResponseTime = AtomicLong(0)
    private val _responseCount = AtomicLong(0)
    private val _minResponseTime = AtomicLong(Long.MAX_VALUE)
    private val _maxResponseTime = AtomicLong(0)
    private val _lastActivityTime = AtomicLong(System.currentTimeMillis())
    private val _connectionStartTime = AtomicLong(System.currentTimeMillis())
    
    /**
     * Records bytes sent.
     */
    fun recordSent(bytes: Int) {
        _bytesSent.addAndGet(bytes.toLong())
        _commandsSent.incrementAndGet()
        _lastActivityTime.set(System.currentTimeMillis())
    }
    
    /**
     * Records bytes received with response time.
     */
    fun recordReceived(bytes: Int, responseTime: Long = 0) {
        _bytesReceived.addAndGet(bytes.toLong())
        _responsesReceived.incrementAndGet()
        _lastActivityTime.set(System.currentTimeMillis())
        
        if (responseTime > 0) {
            _totalResponseTime.addAndGet(responseTime)
            _responseCount.incrementAndGet()
            
            // Update min
            var currentMin: Long
            do {
                currentMin = _minResponseTime.get()
            } while (responseTime < currentMin && !_minResponseTime.compareAndSet(currentMin, responseTime))
            
            // Update max
            var currentMax: Long
            do {
                currentMax = _maxResponseTime.get()
            } while (responseTime > currentMax && !_maxResponseTime.compareAndSet(currentMax, responseTime))
        }
    }
    
    /**
     * Records an error.
     */
    fun recordError() {
        _errors.incrementAndGet()
        _lastActivityTime.set(System.currentTimeMillis())
    }
    
    /**
     * Resets all statistics.
     */
    fun reset() {
        _bytesSent.set(0)
        _bytesReceived.set(0)
        _commandsSent.set(0)
        _responsesReceived.set(0)
        _errors.set(0)
        _totalResponseTime.set(0)
        _responseCount.set(0)
        _minResponseTime.set(Long.MAX_VALUE)
        _maxResponseTime.set(0)
        _lastActivityTime.set(System.currentTimeMillis())
        _connectionStartTime.set(System.currentTimeMillis())
    }
    
    /**
     * Marks the connection start time.
     */
    fun markConnectionStart() {
        _connectionStartTime.set(System.currentTimeMillis())
        reset()
    }
    
    /**
     * Converts to immutable ConnectionStatistics.
     */
    fun toImmutable(): ConnectionStatistics {
        val responseCount = _responseCount.get()
        val avgResponseTime = if (responseCount > 0) {
            _totalResponseTime.get() / responseCount
        } else 0L
        
        return ConnectionStatistics(
            bytesSent = _bytesSent.get(),
            bytesReceived = _bytesReceived.get(),
            commandsSent = _commandsSent.get().toInt(),
            responsesReceived = _responsesReceived.get().toInt(),
            errors = _errors.get().toInt(),
            averageResponseTime = avgResponseTime,
            minResponseTime = _minResponseTime.get(),
            maxResponseTime = _maxResponseTime.get(),
            lastActivityTime = _lastActivityTime.get(),
            connectionUptime = System.currentTimeMillis() - _connectionStartTime.get()
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// SCANNER CONNECTION INTERFACE
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Abstract interface for scanner connections.
 *
 * Defines the contract for all scanner connection types (Bluetooth, WiFi, USB).
 * Implementations handle physical layer communication and provide a consistent
 * API for higher-level protocol handlers.
 *
 * ## Thread Safety
 *
 * All implementations must be thread-safe. The interface uses Kotlin coroutines
 * for asynchronous operations and provides mutex-protected access where needed.
 *
 * ## Lifecycle
 *
 * 1. Create connection instance
 * 2. Call [connect] to establish connection
 * 3. Use [write]/[read]/[sendAndReceive] for communication
 * 4. Call [disconnect] when done
 * 5. Call [release] to free all resources
 *
 * ## Usage Example
 *
 * ```kotlin
 * val connection: ScannerConnection = BluetoothConnection(context, adapter)
 *
 * // Connect
 * val result = connection.connect("AA:BB:CC:DD:EE:FF")
 * if (result is Result.Success) {
 *     // Send command
 *     val response = connection.sendAndReceive("ATZ")
 *     println("Response: ${response.getOrNull()}")
 *
 *     // Disconnect
 *     connection.disconnect()
 * }
 *
 * // Release resources
 * connection.release()
 * ```
 *
 * @author SpaceTec Development Team
 * @since 1.0.0
 */
interface ScannerConnection : Closeable {
    
    // ═══════════════════════════════════════════════════════════════════════
    // PROPERTIES
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Unique identifier for this connection instance.
     */
    val connectionId: String
    
    /**
     * The type of physical connection.
     */
    val connectionType: ScannerConnectionType
    
    /**
     * Current connection state as a [StateFlow].
     * Observers can collect this flow to react to state changes.
     */
    val connectionState: StateFlow<ConnectionState>
    
    /**
     * Stream of incoming data packets.
     * Emits raw bytes received from the scanner.
     */
    val incomingData: SharedFlow<ByteArray>
    
    /**
     * Returns true if currently connected and ready for communication.
     */
    val isConnected: Boolean
    
    /**
     * Returns the current connection configuration.
     */
    val config: ConnectionConfig
    
    // ═══════════════════════════════════════════════════════════════════════
    // CONNECTION MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Establishes a connection to the scanner.
     *
     * This method will:
     * 1. Validate the address format
     * 2. Create the underlying socket/port
     * 3. Establish the physical connection
     * 4. Verify the connection is usable
     *
     * @param address Device address (MAC address, IP:port, or USB path)
     * @param config Connection configuration options
     * @return [Result.Success] with [ConnectionInfo] if successful,
     *         [Result.Error] with the cause of failure otherwise
     * @throws CancellationException if the operation is cancelled
     */
    suspend fun connect(
        address: String,
        config: ConnectionConfig = ConnectionConfig.DEFAULT
    ): Result<ConnectionInfo>
    
    /**
     * Disconnects from the scanner.
     *
     * @param graceful If true, performs a graceful shutdown allowing
     *                 pending operations to complete. If false, forces
     *                 immediate disconnection.
     */
    suspend fun disconnect(graceful: Boolean = true)
    
    /**
     * Attempts to reconnect to the previously connected address.
     *
     * @return [Result.Success] with [ConnectionInfo] if successful,
     *         [Result.Error] if reconnection failed or no previous connection
     */
    suspend fun reconnect(): Result<ConnectionInfo>
    
    // ═══════════════════════════════════════════════════════════════════════
    // DATA TRANSFER
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Sends raw bytes to the scanner.
     *
     * @param data Bytes to send
     * @return [Result.Success] with number of bytes written,
     *         [Result.Error] if write failed
     */
    suspend fun write(data: ByteArray): Result<Int>
    
    /**
     * Sends a command string to the scanner.
     *
     * The command will be encoded as ASCII and a carriage return (\r)
     * will be appended automatically.
     *
     * @param command Command string to send (without terminator)
     * @return [Result.Success] if command was sent,
     *         [Result.Error] if send failed
     */
    suspend fun sendCommand(command: String): Result<Unit>
    
    /**
     * Reads available data with timeout.
     *
     * @param timeout Maximum time to wait for data in milliseconds
     * @return [Result.Success] with received bytes,
     *         [Result.Error] if read failed or timed out
     */
    suspend fun read(timeout: Long = 5000L): Result<ByteArray>
    
    /**
     * Reads data until a terminator is encountered or timeout.
     *
     * This method accumulates data until the specified terminator
     * sequence is found in the response.
     *
     * @param terminator String to look for indicating end of response
     * @param timeout Maximum time to wait in milliseconds
     * @return [Result.Success] with response string (excluding terminator),
     *         [Result.Error] if read failed or timed out
     */
    suspend fun readUntil(
        terminator: String = ELM_PROMPT,
        timeout: Long = 5000L
    ): Result<String>
    
    /**
     * Sends a command and waits for the complete response.
     *
     * This is a convenience method that combines [sendCommand] and
     * [readUntil]. It clears any pending data before sending.
     *
     * @param command Command to send
     * @param timeout Maximum time to wait for response
     * @param terminator Response terminator to look for
     * @return [Result.Success] with response string,
     *         [Result.Error] if operation failed
     */
    suspend fun sendAndReceive(
        command: String,
        timeout: Long = 5000L,
        terminator: String = ELM_PROMPT
    ): Result<String>
    
    /**
     * Sends raw bytes and waits for response bytes.
     *
     * @param data Bytes to send
     * @param timeout Maximum time to wait for response
     * @param expectedLength Expected response length (0 = read until timeout)
     * @return [Result.Success] with response bytes,
     *         [Result.Error] if operation failed
     */
    suspend fun sendAndReceiveBytes(
        data: ByteArray,
        timeout: Long = 5000L,
        expectedLength: Int = 0
    ): Result<ByteArray>
    
    // ═══════════════════════════════════════════════════════════════════════
    // BUFFER MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Clears any pending data in input/output buffers.
     */
    suspend fun clearBuffers()
    
    /**
     * Checks if there is data available to read.
     *
     * @return Number of bytes available, or 0 if none
     */
    suspend fun available(): Int
    
    // ═══════════════════════════════════════════════════════════════════════
    // STATISTICS AND DIAGNOSTICS
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Gets current connection statistics.
     *
     * @return Immutable snapshot of connection statistics
     */
    fun getStatistics(): ConnectionStatistics
    
    /**
     * Resets connection statistics.
     */
    fun resetStatistics()
    
    // ═══════════════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Releases all resources held by this connection.
     *
     * After calling this method, the connection instance should not be used.
     * This method is idempotent and can be called multiple times safely.
     */
    fun release()
    
    /**
     * Closes the connection (alias for [release]).
     * Implements [Closeable] interface.
     */
    override fun close() {
        release()
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // COMPANION
    // ═══════════════════════════════════════════════════════════════════════
    
    companion object {
        /**
         * Default ELM327 prompt character.
         */
        const val ELM_PROMPT = ">"
        
        /**
         * Carriage return character used as command terminator.
         */
        const val COMMAND_TERMINATOR = "\r"
        
        /**
         * Default charset for text communication.
         */
        val DEFAULT_CHARSET: Charset = Charsets.US_ASCII
        
        /**
         * Maximum reasonable response size.
         */
        const val MAX_RESPONSE_SIZE = 16384
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// BASE SCANNER CONNECTION
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Abstract base implementation of [ScannerConnection].
 *
 * Provides common functionality for all connection types including:
 * - State management
 * - Statistics tracking
 * - Buffer management
 * - Response accumulation
 * - Timeout handling
 *
 * Subclasses must implement the actual I/O operations:
 * - [doConnect] - Establish physical connection
 * - [doDisconnect] - Close physical connection
 * - [doWrite] - Write bytes to device
 * - [doRead] - Read bytes from device
 * - [doAvailable] - Check bytes available
 *
 * @param dispatcher Coroutine dispatcher for I/O operations
 *
 * @author SpaceTec Development Team
 * @since 1.0.0
 */
abstract class BaseScannerConnection(
    protected val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : ScannerConnection {
    
    // ═══════════════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════════════
    
    override val connectionId: String = UUID.randomUUID().toString()
    
    protected val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    protected val _incomingData = MutableSharedFlow<ByteArray>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val incomingData: SharedFlow<ByteArray> = _incomingData.asSharedFlow()
    
    override val isConnected: Boolean
        get() = connectionState.value.isConnected
    
    private var _config = ConnectionConfig.DEFAULT
    override val config: ConnectionConfig
        get() = _config
    
    // ═══════════════════════════════════════════════════════════════════════
    // INTERNAL STATE
    // ═══════════════════════════════════════════════════════════════════════
    
    protected var lastConnectedAddress: String? = null
    protected val stats = MutableConnectionStatistics()
    protected val connectionMutex = Mutex()
    protected val writeMutex = Mutex()
    protected val readMutex = Mutex()
    
    protected val responseBuffer = StringBuilder()
    protected val responseLock = Mutex()
    
    protected var readJob: Job? = null
    protected var keepAliveJob: Job? = null
    protected val scope = CoroutineScope(dispatcher + SupervisorJob())
    
    private val released = AtomicBoolean(false)
    
    // ═══════════════════════════════════════════════════════════════════════
    // ABSTRACT METHODS
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Establishes the physical connection.
     *
     * @param address Device address
     * @param config Connection configuration
     * @return ConnectionInfo on success
     * @throws Exception on failure
     */
    protected abstract suspend fun doConnect(
        address: String,
        config: ConnectionConfig
    ): ConnectionInfo
    
    /**
     * Closes the physical connection.
     *
     * @param graceful Whether to perform graceful shutdown
     */
    protected abstract suspend fun doDisconnect(graceful: Boolean)
    
    /**
     * Writes bytes to the device.
     *
     * @param data Bytes to write
     * @return Number of bytes written
     * @throws Exception on failure
     */
    protected abstract suspend fun doWrite(data: ByteArray): Int
    
    /**
     * Reads bytes from the device.
     *
     * @param buffer Buffer to read into
     * @param timeout Read timeout in milliseconds
     * @return Number of bytes read, or -1 if stream closed
     * @throws Exception on failure
     */
    protected abstract suspend fun doRead(buffer: ByteArray, timeout: Long): Int
    
    /**
     * Returns the number of bytes available to read.
     *
     * @return Number of bytes available
     */
    protected abstract suspend fun doAvailable(): Int
    
    /**
     * Clears underlying I/O buffers.
     */
    protected abstract suspend fun doClearBuffers()
    
    // ═══════════════════════════════════════════════════════════════════════
    // CONNECTION MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════════
    
    override suspend fun connect(
        address: String,
        config: ConnectionConfig
    ): Result<ConnectionInfo> = connectionMutex.withLock {
        if (released.get()) {
            return@withLock Result.Error(ConnectionException("Connection has been released"))
        }
        
        if (isConnected) {
            return@withLock Result.Success(connectionState.value.connectionInfo!!)
        }
        
        _config = config
        _connectionState.value = ConnectionState.Connecting
        
        return@withLock withContext(dispatcher) {
            try {
                val info = withTimeout(config.connectionTimeout) {
                    doConnect(address, config)
                }
                
                lastConnectedAddress = address
                stats.markConnectionStart()
                
                // Start background reader if needed
                startBackgroundReader()
                
                // Start keep-alive if configured
                if (config.keepAliveInterval > 0) {
                    startKeepAlive(config.keepAliveInterval)
                }
                
                _connectionState.value = ConnectionState.Connected(info)
                Result.Success(info)
                
            } catch (e: CancellationException) {
                _connectionState.value = ConnectionState.Disconnected
                throw e
            } catch (e: Exception) {
                val error = when (e) {
                    is java.io.IOException -> ConnectionException("Connection failed: ${e.message}", e)
                    is kotlinx.coroutines.TimeoutCancellationException -> TimeoutException("Connection timed out")
                    else -> ConnectionException("Unexpected error: ${e.message}", e)
                }
                _connectionState.value = ConnectionState.Error(error, isRecoverable = true)
                Result.Error(error)
            }
        }
    }
    
    override suspend fun disconnect(graceful: Boolean) = connectionMutex.withLock {
        if (!isConnected && connectionState.value !is ConnectionState.Connecting) {
            return@withLock
        }
        
        withContext(dispatcher) {
            try {
                // Stop background jobs
                stopBackgroundJobs()
                
                // Clear buffers
                responseLock.withLock {
                    responseBuffer.clear()
                }
                
                // Disconnect
                if (graceful) {
                    try {
                        // Give pending operations time to complete
                        delay(100)
                    } catch (_: CancellationException) {
                        // Ignore
                    }
                }
                
                doDisconnect(graceful)
                
            } catch (e: Exception) {
                // Log but don't throw
            } finally {
                _connectionState.value = ConnectionState.Disconnected
            }
        }
    }
    
    override suspend fun reconnect(): Result<ConnectionInfo> {
        val address = lastConnectedAddress
            ?: return Result.Error(ConnectionException("No previous connection to reconnect"))
        
        // Disconnect first if needed
        if (isConnected) {
            disconnect(graceful = false)
        }
        
        var attempt = 0
        var lastError: Throwable? = null
        
        while (attempt < config.maxReconnectAttempts) {
            attempt++
            _connectionState.value = ConnectionState.Reconnecting(attempt, config.maxReconnectAttempts)
            
            // Apply exponential backoff delay
            if (attempt > 1) {
                val delay = config.getReconnectDelay(attempt)
                delay(delay)
            }
            
            val result = connect(address, config)
            if (result is Result.Success) {
                return result
            }
            
            lastError = (result as Result.Error).exception
            
            // Check if error is recoverable
            val isRecoverable = categorizeError(lastError)
            if (!isRecoverable) {
                // Stop retrying for non-recoverable errors
                _connectionState.value = ConnectionState.Error(
                    lastError,
                    isRecoverable = false,
                    attemptCount = attempt
                )
                return Result.Error(lastError)
            }
        }
        
        _connectionState.value = ConnectionState.Error(
            lastError ?: ConnectionException("Reconnection failed"),
            isRecoverable = false,
            attemptCount = attempt
        )
        
        return Result.Error(lastError ?: ConnectionException("Reconnection failed after $attempt attempts"))
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // DATA TRANSFER
    // ═══════════════════════════════════════════════════════════════════════
    
    override suspend fun write(data: ByteArray): Result<Int> = writeMutex.withLock {
        if (!isConnected) {
            return@withLock Result.Error(ConnectionException("Not connected"))
        }
        
        return@withLock withContext(dispatcher) {
            try {
                val written = withTimeout(config.writeTimeout) {
                    doWrite(data)
                }
                stats.recordSent(written)
                
                // Check performance periodically
                if (stats.toImmutable().commandsSent % 10 == 0) {
                    checkPerformanceAndAlert()
                }
                
                Result.Success(written)
                
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                stats.recordError()
                val error = CommunicationException("Write failed: ${e.message}", e)
                handleCommunicationError(error)
                Result.Error(error)
            }
        }
    }
    
    override suspend fun sendCommand(command: String): Result<Unit> {
        val data = "$command${ScannerConnection.COMMAND_TERMINATOR}".toByteArray(ScannerConnection.DEFAULT_CHARSET)
        return write(data).map { }
    }
    
    override suspend fun read(timeout: Long): Result<ByteArray> = readMutex.withLock {
        if (!isConnected) {
            return@withLock Result.Error(ConnectionException("Not connected"))
        }
        
        return@withLock withContext(dispatcher) {
            try {
                val buffer = ByteArray(config.bufferSize)
                val startTime = System.currentTimeMillis()
                
                val bytesRead = withTimeout(timeout) {
                    doRead(buffer, timeout)
                }
                
                if (bytesRead <= 0) {
                    return@withContext Result.Error(CommunicationException("No data received"))
                }
                
                val data = buffer.copyOf(bytesRead)
                val responseTime = System.currentTimeMillis() - startTime
                stats.recordReceived(bytesRead, responseTime)
                
                // Check for performance issues
                if (responseTime > 5000) { // Alert if response takes more than 5 seconds
                    handlePerformanceDegradation("Slow read operation: ${responseTime}ms")
                }
                
                Result.Success(data)
                
            } catch (e: CancellationException) {
                throw e
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                stats.recordError()
                val error = TimeoutException("Read timed out after ${timeout}ms")
                handleCommunicationError(error)
                Result.Error(error)
            } catch (e: Exception) {
                stats.recordError()
                val error = CommunicationException("Read failed: ${e.message}", e)
                handleCommunicationError(error)
                Result.Error(error)
            }
        }
    }
    
    override suspend fun readUntil(
        terminator: String,
        timeout: Long
    ): Result<String> {
        if (!isConnected) {
            return Result.Error(ConnectionException("Not connected"))
        }
        
        return withContext(dispatcher) {
            try {
                val startTime = System.currentTimeMillis()
                val deadline = startTime + timeout
                val buffer = ByteArray(config.bufferSize)
                val accumulated = StringBuilder()
                
                while (System.currentTimeMillis() < deadline) {
                    val remaining = deadline - System.currentTimeMillis()
                    if (remaining <= 0) break
                    
                    // Check response buffer first
                    responseLock.withLock {
                        if (responseBuffer.isNotEmpty()) {
                            accumulated.append(responseBuffer)
                            responseBuffer.clear()
                        }
                    }
                    
                    // Check for terminator
                    val content = accumulated.toString()
                    if (content.contains(terminator)) {
                        val result = content.substringBefore(terminator).trim()
                        
                        // Save anything after terminator for next read
                        val afterTerminator = content.substringAfter(terminator)
                        if (afterTerminator.isNotEmpty()) {
                            responseLock.withLock {
                                responseBuffer.insert(0, afterTerminator)
                            }
                        }
                        
                        val responseTime = System.currentTimeMillis() - startTime
                        stats.recordReceived(result.length, responseTime)
                        
                        return@withContext Result.Success(result)
                    }
                    
                    // Read more data
                    val available = doAvailable()
                    if (available > 0) {
                        val bytesToRead = minOf(available, buffer.size)
                        val bytesRead = doRead(buffer, minOf(remaining, 100))
                        if (bytesRead > 0) {
                            val text = String(buffer, 0, bytesRead, ScannerConnection.DEFAULT_CHARSET)
                            accumulated.append(text)
                        }
                    } else {
                        delay(10)
                    }
                }
                
                // Timeout - return what we have or error
                val finalContent = accumulated.toString().trim()
                if (finalContent.isNotEmpty()) {
                    // Return partial response
                    stats.recordReceived(finalContent.length, timeout)
                    Result.Success(finalContent)
                } else {
                    Result.Error(TimeoutException("Read timed out waiting for '$terminator'"))
                }
                
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                stats.recordError()
                Result.Error(CommunicationException("Read failed: ${e.message}", e))
            }
        }
    }
    
    override suspend fun sendAndReceive(
        command: String,
        timeout: Long,
        terminator: String
    ): Result<String> {
        if (!isConnected) {
            return Result.Error(ConnectionException("Not connected"))
        }
        
        return withContext(dispatcher) {
            try {
                // Clear buffers
                clearBuffers()
                
                // Send command
                val sendResult = sendCommand(command)
                if (sendResult is Result.Error) {
                    return@withContext sendResult
                }
                
                // Read response
                readUntil(terminator, timeout)
                
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                stats.recordError()
                Result.Error(CommunicationException("Send/receive failed: ${e.message}", e))
            }
        }
    }
    
    override suspend fun sendAndReceiveBytes(
        data: ByteArray,
        timeout: Long,
        expectedLength: Int
    ): Result<ByteArray> {
        if (!isConnected) {
            return Result.Error(ConnectionException("Not connected"))
        }
        
        return withContext(dispatcher) {
            try {
                // Clear buffers
                clearBuffers()
                
                // Send data
                val writeResult = write(data)
                if (writeResult is Result.Error) {
                    return@withContext writeResult
                }
                
                // Read response
                if (expectedLength > 0) {
                    readExactBytes(expectedLength, timeout)
                } else {
                    read(timeout)
                }
                
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                stats.recordError()
                Result.Error(CommunicationException("Send/receive failed: ${e.message}", e))
            }
        }
    }
    
    /**
     * Reads exactly the specified number of bytes.
     */
    private suspend fun readExactBytes(length: Int, timeout: Long): Result<ByteArray> {
        val result = ByteArray(length)
        var totalRead = 0
        val startTime = System.currentTimeMillis()
        val buffer = ByteArray(config.bufferSize)
        
        while (totalRead < length) {
            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed >= timeout) {
                return Result.Error(TimeoutException("Read timed out after ${elapsed}ms"))
            }
            
            val remaining = length - totalRead
            val bytesRead = doRead(buffer, timeout - elapsed)
            
            if (bytesRead <= 0) {
                delay(10)
                continue
            }
            
            val toCopy = minOf(bytesRead, remaining)
            System.arraycopy(buffer, 0, result, totalRead, toCopy)
            totalRead += toCopy
        }
        
        val responseTime = System.currentTimeMillis() - startTime
        stats.recordReceived(length, responseTime)
        
        return Result.Success(result)
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // BUFFER MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════════
    
    override suspend fun clearBuffers() {
        responseLock.withLock {
            responseBuffer.clear()
        }
        
        withContext(dispatcher) {
            try {
                doClearBuffers()
            } catch (e: Exception) {
                // Ignore errors during clear
            }
        }
    }
    
    override suspend fun available(): Int {
        if (!isConnected) return 0
        
        return withContext(dispatcher) {
            try {
                doAvailable()
            } catch (e: Exception) {
                0
            }
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // STATISTICS
    // ═══════════════════════════════════════════════════════════════════════
    
    override fun getStatistics(): ConnectionStatistics = stats.toImmutable()
    
    override fun resetStatistics() {
        stats.reset()
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // BACKGROUND TASKS
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Starts the background reader coroutine.
     * Override this if the implementation needs continuous reading.
     */
    protected open fun startBackgroundReader() {
        // Default implementation does nothing
        // Subclasses can override to start continuous reading
    }
    
    /**
     * Starts the keep-alive coroutine.
     */
    protected open fun startKeepAlive(intervalMs: Long) {
        keepAliveJob?.cancel()
        keepAliveJob = scope.launch {
            while (isActive && isConnected) {
                delay(intervalMs)
                if (isConnected) {
                    // Send keep-alive (empty byte)
                    try {
                        doWrite(byteArrayOf())
                    } catch (e: Exception) {
                        // Connection might be lost
                        handleCommunicationError(e)
                    }
                }
            }
        }
    }
    
    /**
     * Stops all background jobs.
     */
    protected fun stopBackgroundJobs() {
        readJob?.cancel()
        readJob = null
        keepAliveJob?.cancel()
        keepAliveJob = null
    }
    
    /**
     * Handles a communication error.
     * May trigger reconnection if configured.
     */
    protected open suspend fun handleCommunicationError(error: Throwable) {
        if (!isConnected) return
        
        // Categorize the error
        val isRecoverable = categorizeError(error)
        
        if (config.autoReconnect && isRecoverable) {
            // Attempt reconnection in background
            scope.launch {
                val reconnectResult = reconnect()
                if (reconnectResult is Result.Error) {
                    // If reconnection fails, update state with final error
                    _connectionState.value = ConnectionState.Error(
                        reconnectResult.exception,
                        isRecoverable = false,
                        attemptCount = config.maxReconnectAttempts
                    )
                }
            }
        } else {
            _connectionState.value = ConnectionState.Error(error, isRecoverable = isRecoverable)
        }
    }
    
    /**
     * Categorizes an error as recoverable or non-recoverable.
     * 
     * Recoverable errors include:
     * - Temporary network issues
     * - Timeout exceptions
     * - Communication interruptions
     * 
     * Non-recoverable errors include:
     * - Invalid addresses
     * - Authentication failures
     * - Hardware not found
     */
    protected open fun categorizeError(error: Throwable): Boolean {
        return when (error) {
            is TimeoutException -> true
            is CommunicationException -> {
                // Check if it's a temporary communication issue
                val message = error.message?.lowercase() ?: ""
                when {
                    message.contains("timeout") -> true
                    message.contains("connection lost") -> true
                    message.contains("network unreachable") -> true
                    message.contains("connection reset") -> true
                    message.contains("broken pipe") -> true
                    message.contains("invalid address") -> false
                    message.contains("not found") -> false
                    message.contains("permission denied") -> false
                    message.contains("authentication") -> false
                    else -> true // Default to recoverable for unknown communication errors
                }
            }
            is ConnectionException -> {
                val message = error.message?.lowercase() ?: ""
                when {
                    message.contains("invalid address") -> false
                    message.contains("not found") -> false
                    message.contains("permission denied") -> false
                    message.contains("authentication") -> false
                    else -> true // Default to recoverable for connection errors
                }
            }
            else -> false // Unknown errors are non-recoverable by default
        }
    }
    
    /**
     * Checks connection performance and triggers alerts if degraded.
     */
    protected open fun checkPerformanceAndAlert() {
        val currentStats = stats.toImmutable()
        
        // Check error rate threshold
        if (currentStats.commandsSent > 10 && currentStats.errorRate > 20.0f) {
            // High error rate detected
            scope.launch {
                handlePerformanceDegradation(
                    "High error rate detected: ${String.format("%.1f", currentStats.errorRate)}%"
                )
            }
        }
        
        // Check response time degradation
        if (currentStats.responsesReceived > 5 && currentStats.averageResponseTime > 10000) {
            // Slow response times detected
            scope.launch {
                handlePerformanceDegradation(
                    "Slow response times detected: ${currentStats.averageResponseTime}ms average"
                )
            }
        }
        
        // Check if connection appears idle
        if (currentStats.isIdle && isConnected) {
            scope.launch {
                handlePerformanceDegradation(
                    "Connection appears idle: ${currentStats.idleTime}ms since last activity"
                )
            }
        }
    }
    
    /**
     * Handles performance degradation by logging and potentially taking corrective action.
     */
    protected open suspend fun handlePerformanceDegradation(message: String) {
        // Log the performance issue (in a real implementation, this would use a proper logger)
        println("Performance Alert: $message")
        
        // Could trigger additional actions like:
        // - Clearing buffers
        // - Adjusting timeouts
        // - Suggesting connection optimization
        // - Notifying observers
    }
    
    /**
     * Processes incoming data from background reader.
     */
    protected suspend fun processIncomingData(data: ByteArray) {
        if (data.isEmpty()) return
        
        // Emit to incoming data flow
        _incomingData.emit(data)
        
        // Append to response buffer
        val text = String(data, ScannerConnection.DEFAULT_CHARSET)
        responseLock.withLock {
            responseBuffer.append(text)
            
            // Prevent buffer from growing too large
            if (responseBuffer.length > ScannerConnection.MAX_RESPONSE_SIZE) {
                val excess = responseBuffer.length - ScannerConnection.MAX_RESPONSE_SIZE
                responseBuffer.delete(0, excess)
            }
        }
        
        // Check performance periodically
        if (stats.toImmutable().commandsSent % 10 == 0) {
            checkPerformanceAndAlert()
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════════
    
    override fun release() {
        if (released.getAndSet(true)) {
            return  // Already released
        }
        
        // Cancel all coroutines
        scope.cancel()
        
        // Disconnect synchronously (best effort)
        try {
            kotlinx.coroutines.runBlocking {
                disconnect(graceful = false)
            }
        } catch (e: Exception) {
            // Ignore errors during release
        }
        
        _connectionState.value = ConnectionState.Disconnected
    }
    
    /**
     * Ensures resources are released when garbage collected.
     */
    protected fun finalize() {
        if (!released.get()) {
            release()
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// RESULT EXTENSIONS
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Maps a successful result to a new type.
 */
inline fun <T, R> Result<T>.map(transform: (T) -> R): Result<R> {
    return when (this) {
        is Result.Success -> Result.Success(transform(data))
        is Result.Error -> this
        is Result.Loading -> Result.Loading
    }
}

/**
 * Flat maps a successful result to a new result.
 */
inline fun <T, R> Result<T>.flatMap(transform: (T) -> Result<R>): Result<R> {
    return when (this) {
        is Result.Success -> transform(data)
        is Result.Error -> this
        is Result.Loading -> Result.Loading
    }
}

/**
 * Returns the success value or throws the error.
 */
fun <T> Result<T>.getOrThrow(): T {
    return when (this) {
        is Result.Success -> data
        is Result.Error -> throw exception
        is Result.Loading -> throw IllegalStateException("Result is still loading")
    }
}

/**
 * Returns the success value or null.
 */
fun <T> Result<T>.getOrNull(): T? {
    return (this as? Result.Success)?.data
}

/**
 * Returns the success value or the default.
 */
fun <T> Result<T>.getOrDefault(default: T): T {
    return getOrNull() ?: default
}

/**
 * Returns the success value or computes a default.
 */
inline fun <T> Result<T>.getOrElse(default: () -> T): T {
    return getOrNull() ?: default()
}

/**
 * Executes the block if successful.
 */
inline fun <T> Result<T>.onSuccess(block: (T) -> Unit): Result<T> {
    if (this is Result.Success) {
        block(data)
    }
    return this
}

/**
 * Executes the block if error.
 */
inline fun <T> Result<T>.onError(block: (Throwable) -> Unit): Result<T> {
    if (this is Result.Error) {
        block(exception)
    }
    return this
}

// ═══════════════════════════════════════════════════════════════════════════
// CONNECTION EVENT LISTENER
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Listener interface for connection events.
 *
 * Provides callbacks for connection state changes and data reception.
 */
interface ConnectionEventListener {
    
    /**
     * Called when connection state changes.
     */
    fun onStateChanged(state: ConnectionState)
    
    /**
     * Called when data is received.
     */
    fun onDataReceived(data: ByteArray)
    
    /**
     * Called when an error occurs.
     */
    fun onError(error: Throwable)
}

/**
 * Adds a connection event listener to this connection.
 *
 * @param listener The listener to add
 * @return A job that can be cancelled to remove the listener
 */
fun ScannerConnection.addListener(
    scope: CoroutineScope,
    listener: ConnectionEventListener
): Job {
    val stateJob = scope.launch {
        connectionState.collect { state ->
            listener.onStateChanged(state)
            if (state is ConnectionState.Error) {
                listener.onError(state.exception)
            }
        }
    }
    
    val dataJob = scope.launch {
        incomingData.collect { data ->
            listener.onDataReceived(data)
        }
    }
    
    return scope.launch {
        stateJob.join()
        dataJob.join()
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// CONNECTION FACTORY
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Factory interface for creating scanner connections.
 *
 * Allows dependency injection of connection implementations.
 */
interface ScannerConnectionFactory {
    
    /**
     * Creates a connection for the specified type.
     *
     * @param type Connection type
     * @return New connection instance
     */
    fun createConnection(type: ScannerConnectionType): ScannerConnection
    
    /**
     * Creates a connection appropriate for the given address.
     *
     * @param address Device address
     * @return New connection instance
     */
    fun createConnectionForAddress(address: String): ScannerConnection
}

// ═══════════════════════════════════════════════════════════════════════════
// UTILITY EXTENSIONS
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Executes a block with the connection, ensuring proper cleanup.
 */
suspend inline fun <T> ScannerConnection.use(block: (ScannerConnection) -> T): T {
    try {
        return block(this)
    } finally {
        disconnect()
    }
}

/**
 * Executes a block with connection, connecting first if needed.
 */
suspend inline fun <T> ScannerConnection.withConnection(
    address: String,
    config: ConnectionConfig = ConnectionConfig.DEFAULT,
    block: (ScannerConnection) -> T
): Result<T> {
    val connectResult = connect(address, config)
    if (connectResult is Result.Error) {
        return connectResult
    }
    
    return try {
        Result.Success(block(this))
    } catch (e: Exception) {
        Result.Error(e)
    } finally {
        disconnect()
    }
}

/**
 * Retries an operation with exponential backoff.
 */
suspend inline fun <T> ScannerConnection.retry(
    times: Int = 3,
    initialDelay: Long = 100,
    maxDelay: Long = 1000,
    crossinline block: suspend () -> Result<T>
): Result<T> {
    var currentDelay = initialDelay
    var lastError: Throwable? = null
    
    repeat(times) { attempt ->
        when (val result = block()) {
            is Result.Success -> return result
            is Result.Error -> {
                lastError = result.exception
                if (attempt < times - 1) {
                    delay(currentDelay)
                    currentDelay = (currentDelay * 2).coerceAtMost(maxDelay)
                }
            }
            is Result.Loading -> { /* Continue */ }
        }
    }
    
    return Result.Error(lastError ?: CommunicationException("Retry failed after $times attempts"))
}

/**
 * Waits for the connection to reach a specific state.
 */
suspend fun ScannerConnection.awaitState(
    targetState: ConnectionState,
    timeout: Long = 10_000
): Boolean {
    return withTimeoutOrNull(timeout) {
        connectionState.collect { state ->
            if (state::class == targetState::class) {
                return@collect
            }
        }
        true
    } ?: false
}

/**
 * Waits for the connection to become connected.
 */
suspend fun ScannerConnection.awaitConnected(timeout: Long = 10_000): Boolean {
    return withTimeoutOrNull(timeout) {
        connectionState.collect { state ->
            if (state.isConnected) {
                return@collect
            }
            if (state is ConnectionState.Error && !state.isRecoverable) {
                return@withTimeoutOrNull false
            }
        }
        true
    } ?: false
}
