/**
 * File: BaseProtocol.kt
 * 
 * Abstract base class for all diagnostic protocol implementations in the
 * SpaceTec automotive diagnostic application. This class provides the
 * foundation for protocol lifecycle management, message handling, error
 * recovery, and session management across all supported protocols.
 * 
 * Structure:
 * 1. Package declaration and imports
 * 2. BaseProtocol abstract class with full implementation
 * 3. ProtocolState sealed class hierarchy
 * 4. ProtocolCapabilities data class
 * 5. ProtocolConfig data class with builders
 * 6. ProtocolFeature enum
 * 7. SessionType enum
 * 8. Supporting classes and interfaces
 * 
 * @author SpaceTec Development Team
 * @since 1.0.0
 */
package com.spacetec.protocol.core.base

import com.spacetec.core.common.NRCConstants
import com.spacetec.core.domain.protocol.Protocol
import com.spacetec.core.domain.protocol.ProtocolConfig
import com.spacetec.core.domain.protocol.ProtocolState
import com.spacetec.core.domain.protocol.ProtocolType
import com.spacetec.core.domain.protocol.SessionType
import com.spacetec.core.domain.scanner.ScannerConnection
import com.spacetec.protocol.core.message.DiagnosticMessage
import com.spacetec.protocol.core.message.MessageType
import com.spacetec.protocol.core.message.ResponseMessage
import com.spacetec.protocol.core.timing.TimingManager
import com.spacetec.core.logging.SpaceTecLogger
import com.spacetec.core.common.exceptions.CommunicationException
import com.spacetec.core.common.exceptions.TimeoutException
import com.spacetec.core.common.exceptions.ProtocolException
import com.spacetec.core.common.constants.OBDConstants
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
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
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Abstract base class for all diagnostic protocol implementations.
 * 
 * This class provides the common foundation for implementing vehicle
 * diagnostic protocols including OBD-II, UDS, KWP2000, CAN, K-Line,
 * and J1850 variants. It handles:
 * 
 * - **Lifecycle Management**: Initialize, shutdown, and reset operations
 * - **Message Handling**: Send/receive with timeout and retry logic
 * - **Session Management**: Diagnostic session start/end and keep-alive
 * - **Error Recovery**: Negative response handling and retry strategies
 * - **State Management**: Thread-safe protocol state tracking
 * - **Logging**: Comprehensive operation logging for debugging
 * 
 * ## Implementation Guidelines
 * 
 * Subclasses must implement:
 * - [protocolType]: The specific protocol type
 * - [capabilities]: Protocol capabilities and features
 * - [protocolName]: Human-readable protocol name
 * - [protocolVersion]: Protocol version/variant
 * - [initialize]: Protocol-specific initialization
 * - [sendMessage]: Message transmission logic
 * - [validateResponse]: Response validation logic
 * - [handleNegativeResponse]: Error handling logic
 * - [buildRequest]: Request message construction
 * - [parseResponse]: Response parsing logic
 * 
 * ## Thread Safety
 * 
 * All public methods are thread-safe through the use of:
 * - [Mutex] for state synchronization
 * - [StateFlow] for reactive state updates
 * - Atomic operations for counters
 * 
 * ## Usage Example
 * 
 * ```kotlin
 * class OBDProtocol : BaseProtocol() {
 *     override val protocolType = ProtocolType.ISO_15765_4_CAN_11BIT_500K
 *     
 *     override suspend fun initialize(connection: ScannerConnection, config: ProtocolConfig) {
 *         // OBD-specific initialization
 *     }
 *     
 *     override suspend fun sendMessage(request: DiagnosticMessage): DiagnosticMessage {
 *         // OBD message handling
 *     }
 * }
 * ```
 * 
 * @see ProtocolState
 * @see ProtocolCapabilities
 * @see ProtocolConfig
 * @see DiagnosticMessage
 */
abstract class BaseProtocol : Protocol {

    // ==================== Abstract Properties ====================

    /**
     * The specific protocol type this implementation handles.
     * 
     * This identifies the exact protocol variant, including baud rate
     * and addressing mode for CAN-based protocols.
     * 
     * @see ProtocolType
     */
    override abstract val protocolType: ProtocolType

    /**
     * Protocol capabilities and supported features.
     * 
     * Defines what operations this protocol supports, including
     * maximum data length, supported services, and special features.
     * 
     * @see ProtocolCapabilities
     */
    abstract val capabilities: ProtocolCapabilities

    /**
     * Human-readable protocol name for display purposes.
     * 
     * Example: "ISO 15765-4 CAN (11-bit, 500 kbaud)"
     */
    override abstract val protocolName: String

    /**
     * Protocol version or variant identifier.
     * 
     * Example: "1.0", "2014", "Fast Init"
     */
    override abstract val protocolVersion: String

    // ==================== Protected Properties ====================

    /**
     * Logger instance for protocol operations.
     */
    protected open val logger = SpaceTecLogger("BaseProtocol")

    /**
     * Timing manager for protocol-specific timing requirements.
     */
    protected open val timingManager: TimingManager = TimingManager()

    /**
     * Coroutine scope for protocol operations.
     */
    protected val protocolScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Mutex for synchronizing state changes.
     */
    protected val stateMutex: Mutex = Mutex()

    /**
     * Mutex for synchronizing message operations.
     */
    protected val messageMutex: Mutex = Mutex()

    /**
     * Active scanner connection.
     */
    protected var connection: ScannerConnection? = null

    /**
     * Current protocol configuration.
     */
    protected var _config: ProtocolConfig = ProtocolConfig.DEFAULT

    /**
     * Keep-alive job for session maintenance.
     */
    protected var keepAliveJob: Job? = null

    /**
     * Message sequence counter for tracking.
     */
    protected val messageSequence: AtomicLong = AtomicLong(0)

    /**
     * Flag indicating if protocol is being shutdown.
     */
    protected val isShuttingDown: AtomicBoolean = AtomicBoolean(false)

    // ==================== State Properties ====================

    /**
     * Mutable backing field for protocol state.
     */
    protected val _state = MutableStateFlow<ProtocolState>(ProtocolState.Uninitialized)

    /**
     * Current protocol state as an observable stream.
     * 
     * Observers can collect this flow to react to state changes.
     * States progress through: Uninitialized → Initializing → Ready → SessionActive
     * 
     * @see ProtocolState
     */
    val state: StateFlow<ProtocolState>
        get() = _state.asStateFlow()

    /**
     * Whether the protocol is initialized and ready for communication.
     * 
     * @return true if state is [ProtocolState.Ready] or [ProtocolState.SessionActive]
     */
    override val isInitialized: Boolean
        get() = _state.value is ProtocolState.Ready || _state.value is ProtocolState.SessionActive

    /**
     * Whether a diagnostic session is currently active.
     * 
     * @return true if state is [ProtocolState.SessionActive]
     */
    override val isSessionActive: Boolean
        get() = _state.value is ProtocolState.SessionActive

    /**
     * Current protocol configuration.
     * 
     * @see ProtocolConfig
     */
    override val config: ProtocolConfig
        get() = _config

    // ==================== Event Flows ====================

    /**
     * Mutable backing field for message events.
     */
    private val _messageEvents = MutableSharedFlow<MessageEvent>(extraBufferCapacity = 64)

    /**
     * Flow of message events for monitoring/debugging.
     */
    val messageEvents: Flow<MessageEvent>
        get() = _messageEvents.asSharedFlow()

    /**
     * Mutable backing field for error events.
     */
    private val _errorEvents = MutableSharedFlow<ProtocolError>(extraBufferCapacity = 16)

    /**
     * Flow of protocol errors.
     */
    val errorEvents: Flow<ProtocolError>
        get() = _errorEvents.asSharedFlow()

    // ==================== Lifecycle Methods ====================

    /**
     * Initializes the protocol with the given scanner connection.
     * 
     * This method prepares the protocol for communication by:
     * 1. Storing the connection reference
     * 2. Applying the configuration
     * 3. Setting up protocol-specific parameters
     * 4. Transitioning to Ready state
     * 
     * Subclasses must call `super.initialize()` and then perform
     * protocol-specific initialization.
     * 
     * @param connection Active scanner connection to use
     * @param config Protocol configuration (defaults to [ProtocolConfig.DEFAULT])
     * 
     * @throws ProtocolException If initialization fails
     * @throws CommunicationException If connection is invalid
     * 
     * @see shutdown
     * @see reset
     */
    abstract override suspend fun initialize(
        connection: ScannerConnection,
        config: ProtocolConfig
    )

    /**
     * Base initialization logic called by subclasses.
     * 
     * @param connection Scanner connection
     * @param config Protocol configuration
     */
    protected suspend fun baseInitialize(
        connection: ScannerConnection,
        config: ProtocolConfig
    ) {
        stateMutex.withLock {
            // logger.i(TAG, "Initializing protocol: $protocolName")
            
            if (_state.value != ProtocolState.Uninitialized && 
                _state.value != ProtocolState.Shutdown) {
                // logger.w(TAG, "Protocol already initialized, resetting first")
                baseReset()
            }
            
            // Domain ProtocolState doesn't model an Initializing state.
            
            this.connection = connection
            this._config = config
            this.isShuttingDown.set(false)
            this.messageSequence.set(0)
            
            // Validate connection
            if (!connection.isConnected) {
                _state.value = ProtocolState.Error(ProtocolError.ConnectionError("Scanner not connected"))
                throw CommunicationException("Scanner connection is not active")
            }
            
            // logger.d(TAG, "Base initialization complete for $protocolName")
        }
    }

    /**
     * Completes initialization and transitions to Ready state.
     * 
     * Call this at the end of subclass initialize() implementation.
     */
    protected suspend fun completeInitialization() {
        stateMutex.withLock {
            _state.value = ProtocolState.Ready
            logger.info("Protocol $protocolName initialized and ready")
        }
    }

    /**
     * Shuts down the protocol and releases all resources.
     * 
     * This method:
     * 1. Ends any active session
     * 2. Cancels keep-alive operations
     * 3. Cleans up resources
     * 4. Transitions to Shutdown state
     * 
     * After shutdown, [initialize] must be called again before use.
     * 
     * @see initialize
     * @see reset
     */
    override suspend fun shutdown() {
        stateMutex.withLock {
            if (_state.value == ProtocolState.Shutdown) {
                logger.debug("Protocol already shutdown")
                return
            }
            
            // logger.i(TAG, "Shutting down protocol: $protocolName")
            isShuttingDown.set(true)
            // Domain ProtocolState doesn't model ShuttingDown; proceed directly.
            
            try {
                // Cancel keep-alive
                keepAliveJob?.cancel()
                keepAliveJob = null
                
                // End active session if any
                if (isSessionActive) {
                    try {
                        endSessionInternal()
                    } catch (e: Exception) {
                        // logger.w(TAG, "Error ending session during shutdown: ${e.message}")
                    }
                }
                
                // Perform protocol-specific cleanup
                performShutdownCleanup()
                
                // Clear connection reference
                connection = null
                
                _state.value = ProtocolState.Shutdown
                logger.info("Protocol $protocolName shutdown complete")
                
            } catch (e: Exception) {
                // logger.e(TAG, "Error during shutdown", e)
                _state.value = ProtocolState.Shutdown
            }
        }
    }

    /**
     * Protocol-specific shutdown cleanup.
     * 
     * Override to perform additional cleanup operations.
     */
    protected open suspend fun performShutdownCleanup() {
        // Default: no additional cleanup
    }

    /**
     * Resets the protocol to initial state without full shutdown.
     * 
     * This method:
     * 1. Ends any active session
     * 2. Clears message buffers
     * 3. Resets counters and state
     * 4. Remains connected and ready
     * 
     * Use this to recover from errors or prepare for a new diagnostic session.
     * 
     * @throws ProtocolException If reset fails
     * 
     * @see initialize
     * @see shutdown
     */
    override suspend fun reset() {
        stateMutex.withLock {
            // logger.i(TAG, "Resetting protocol: $protocolName")
            baseReset()
        }
    }

    /**
     * Internal reset logic.
     */
    protected open suspend fun baseReset() {
        // Cancel keep-alive
        keepAliveJob?.cancel()
        keepAliveJob = null
        
        // End session if active
        if (_state.value is ProtocolState.SessionActive) {
            try {
                endSessionInternal()
            } catch (e: Exception) {
                // logger.w(TAG, "Error ending session during reset: ${e.message}")
            }
        }
        
        // Reset counters
        messageSequence.set(0)
        
        // Clear timing manager
        timingManager.reset()
        
        // Return to ready state if we have a connection
        if (connection?.isConnected == true) {
            _state.value = ProtocolState.Ready
        } else {
            _state.value = ProtocolState.Uninitialized
        }
        
        // logger.d(TAG, "Protocol reset complete")
    }

    // ==================== Communication Methods ====================

    /**
     * Sends a diagnostic message and waits for a response.
     * 
     * This method handles:
     * - Message validation
     * - Timeout management (using [config] timeout)
     * - Response validation
     * - Negative response handling
     * 
     * @param request The diagnostic message to send
     * @return The response message from the ECU
     * 
     * @throws TimeoutException If no response received within timeout
     * @throws ProtocolException If protocol error occurs
     * @throws CommunicationException If communication fails
     * 
     * @see sendMessage(DiagnosticMessage, Long)
     * @see sendRaw
     */
    abstract override suspend fun sendMessage(request: DiagnosticMessage): DiagnosticMessage

    /**
     * Sends a diagnostic message with a custom timeout.
     * 
     * Use this for operations that require longer timeouts, such as
     * ECU programming or extended diagnostic services.
     * 
     * @param request The diagnostic message to send
     * @param timeoutMs Custom timeout in milliseconds
     * @return The response message from the ECU
     * 
     * @throws TimeoutException If no response received within timeout
     * @throws ProtocolException If protocol error occurs
     * @throws CommunicationException If communication fails
     * 
     * @see sendMessage(DiagnosticMessage)
     */
    abstract override suspend fun sendMessage(
        request: DiagnosticMessage,
        timeoutMs: Long
    ): DiagnosticMessage

    /**
     * Base implementation for sending messages with retry logic.
     * 
     * @param request Request message
     * @param timeoutMs Timeout in milliseconds
     * @param transmit Actual transmission function
     * @return Response message
     */
    protected suspend fun baseSendMessage(
        request: DiagnosticMessage,
        timeoutMs: Long,
        transmit: suspend (ByteArray) -> ByteArray
    ): DiagnosticMessage = messageMutex.withLock {
        validateState()
        
        val sequence = messageSequence.incrementAndGet()
        val startTime = System.currentTimeMillis()
        
        // logger.d(TAG, "Sending message #$sequence: ${request.toHexString()}")
        emitMessageEvent(MessageEvent.Sending(sequence, request))
        
        try {
            val response = executeWithRetry(
                operation = "sendMessage",
                maxRetries = _config.maxRetries,
                retryDelayMs = _config.retryDelayMs
            ) {
                withTimeout(timeoutMs) {
                    val requestBytes = request.toByteArray()
                    val responseBytes = transmit(requestBytes)
                    parseResponse(responseBytes, request.serviceId)
                }
            }
            
            val elapsed = System.currentTimeMillis() - startTime
            // logger.d(TAG, "Message #$sequence response received in ${elapsed}ms: ${response.toHexString()}")
            emitMessageEvent(MessageEvent.Received(sequence, response, elapsed))
            
            // Validate response
            if (!validateResponse(request, response)) {
                throw ProtocolException("Invalid response for request ${request.serviceId}")
            }
            
            // Handle negative response
            if (response.isNegativeResponse) {
                return@withLock handleNegativeResponse(
                    request.serviceId,
                    response.negativeResponseCode,
                    request
                )
            }
            
            response
            
        } catch (e: TimeoutException) {
            val elapsed = System.currentTimeMillis() - startTime
            logger.warn("Message #$sequence timeout after ${elapsed}ms")
            emitMessageEvent(MessageEvent.Timeout(sequence, elapsed))
            emitError(ProtocolError.Timeout("Message timeout after ${elapsed}ms"))
            throw e
        } catch (e: CancellationException) {
            logger.debug("Message #$sequence cancelled")
            emitMessageEvent(MessageEvent.Cancelled(sequence))
            throw e
        } catch (e: Exception) {
            logger.error("Message #$sequence error: ${e.message}", e)
            emitMessageEvent(MessageEvent.Error(sequence, e))
            emitError(ProtocolError.CommunicationError(e.message ?: "Unknown error"))
            throw e
        }
    }

    /**
     * Sends raw bytes and receives raw response.
     * 
     * Use this for low-level operations that don't require message parsing.
     * 
     * @param data Raw bytes to send
     * @return Raw response bytes
     * 
     * @throws TimeoutException If no response received
     * @throws CommunicationException If communication fails
     * 
     * @see sendRaw(ByteArray, Long)
     */
    override suspend fun sendRaw(data: ByteArray): ByteArray {
        return sendRaw(data, _config.responseTimeoutMs)
    }

    /**
     * Sends raw bytes with a custom timeout.
     * 
     * @param data Raw bytes to send
     * @param timeoutMs Custom timeout in milliseconds
     * @return Raw response bytes
     * 
     * @throws TimeoutException If no response received
     * @throws CommunicationException If communication fails
     */
    override suspend fun sendRaw(data: ByteArray, timeoutMs: Long): ByteArray = messageMutex.withLock {
        validateState()
        
        val conn = connection ?: throw CommunicationException("No active connection")
        
        logger.debug("Sending raw: ${data.toHexString()}")
        
        try {
            withTimeout(timeoutMs) {
                conn.write(data)
                val response = conn.read(timeoutMs)
                logger.debug("Raw response: ${response.toHexString()}")
                response
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            throw TimeoutException("Raw send timeout after ${timeoutMs}ms")
        }
    }

    // ==================== Session Methods ====================

    /**
     * Starts a diagnostic session with the vehicle.
     * 
     * For protocols that support session types (UDS, KWP2000), this
     * transitions the ECU to the requested session mode. For OBD-II,
     * this is typically a no-op as OBD uses a default session.
     * 
     * After starting a session, keep-alive messages are automatically
     * sent if configured.
     * 
     * @param sessionType Type of session to start (defaults to [SessionType.DEFAULT])
     * @param ecuAddress Specific ECU address (null for broadcast)
     * 
     * @throws ProtocolException If session start fails
     * @throws TimeoutException If ECU doesn't respond
     * 
     * @see endSession
     * @see sendKeepAlive
     */
    override suspend fun startSession(
        sessionType: SessionType,
        ecuAddress: Int?
    ) {
        stateMutex.withLock {
            validateState()
            
            logger.info("Starting session: $sessionType" + 
                (ecuAddress?.let { " to ECU 0x${it.toString(16)}" } ?: ""))
            
            try {
                // Perform protocol-specific session start
                performSessionStart(sessionType, ecuAddress)
                
                // Update state
                _state.value = ProtocolState.SessionActive(sessionType)
                
                // Start keep-alive if enabled
                if (capabilities.supportsKeepAlive) {
                    startKeepAlive()
                }
                
                logger.info("Session started successfully")
                
            } catch (e: Exception) {
                logger.error("Failed to start session", e)
                emitError(ProtocolError.SessionError("Failed to start session: ${e.message}"))
                throw ProtocolException("Failed to start $sessionType session: ${e.message}")
            }
        }
    }

    /**
     * Protocol-specific session start implementation.
     * 
     * Override to implement protocol-specific session initialization.
     * 
     * @param sessionType Session type to start
     * @param ecuAddress Target ECU address
     */
    protected open suspend fun performSessionStart(
        sessionType: SessionType,
        ecuAddress: Int?
    ) {
        // Default implementation for protocols without session concept
        logger.debug("Default session start (no-op)")
    }

    /**
     * Ends the current diagnostic session.
     * 
     * This returns the ECU to the default session and stops
     * keep-alive messages.
     * 
     * @throws ProtocolException If session end fails
     * 
     * @see startSession
     */
    override suspend fun endSession() {
        stateMutex.withLock {
            if (_state.value !is ProtocolState.SessionActive) {
                logger.debug("No active session to end")
                return
            }
            
            endSessionInternal()
        }
    }

    /**
     * Internal session end without lock.
     */
    protected open suspend fun endSessionInternal() {
        logger.info("Ending session")
        
        try {
            // Stop keep-alive
            keepAliveJob?.cancel()
            keepAliveJob = null
            
            // Perform protocol-specific session end
            performSessionEnd()
            
            // Update state
            _state.value = ProtocolState.Ready
            
            logger.info("Session ended successfully")
            
        } catch (e: Exception) {
            logger.error("Error ending session", e)
            // Still transition to Ready state
            _state.value = ProtocolState.Ready
        }
    }

    /**
     * Protocol-specific session end implementation.
     * 
     * Override to implement protocol-specific session teardown.
     */
    protected open suspend fun performSessionEnd() {
        // Default implementation for protocols without session concept
        logger.debug("Default session end (no-op)")
    }

    /**
     * Sends a keep-alive (tester present) message to maintain the session.
     * 
     * This is automatically called at regular intervals when a session
     * is active. Can also be called manually if needed.
     * 
     * @throws ProtocolException If keep-alive fails
     * 
     * @see startSession
     */
    override suspend fun sendKeepAlive() {
        if (!isSessionActive) return
        
        try {
            performKeepAlive()
            logger.debug("Keep-alive sent")
        } catch (e: Exception) {
            logger.warn("Keep-alive failed: ${e.message}")
            emitError(ProtocolError.SessionError("Keep-alive failed"))
        }
    }

    /**
     * Protocol-specific keep-alive implementation.
     * 
     * Override to implement protocol-specific tester present logic.
     */
    protected open suspend fun performKeepAlive() {
        // Default: UDS Tester Present (0x3E)
        val testerPresent = byteArrayOf(0x3E, 0x00)
        sendRaw(testerPresent, _config.responseTimeoutMs)
    }

    /**
     * Starts the keep-alive coroutine.
     */
    private fun startKeepAlive() {
        keepAliveJob?.cancel()
        keepAliveJob = protocolScope.launch {
            while (isActive && isSessionActive && !isShuttingDown.get()) {
                delay(1000L)
                if (isSessionActive) {
                    try {
                        sendKeepAlive()
                    } catch (e: Exception) {
                        if (!isShuttingDown.get()) {
                            logger.warn("Keep-alive error: ${e.message}")
                        }
                    }
                }
            }
        }
    }

    // ==================== Protected Abstract Methods ====================

    /**
     * Validates a response message against the original request.
     * 
     * Implementations should verify:
     * - Service ID matches (request + 0x40 for positive)
     * - Data length is valid
     * - Any protocol-specific validations
     * 
     * @param request The original request message
     * @param response The received response message
     * @return true if response is valid for the request
     */
    protected abstract fun validateResponse(
        request: DiagnosticMessage,
        response: DiagnosticMessage
    ): Boolean

    /**
     * Handles a negative response from the ECU.
     * 
     * Negative responses (NRC) indicate the ECU rejected the request.
     * Implementations should:
     * - Log the negative response
     * - Determine if retry is appropriate
     * - Either retry, return error response, or throw exception
     * 
     * @param serviceId The service that was requested
     * @param nrc The Negative Response Code
     * @param request The original request message
     * @return Alternative response or throws exception
     * 
     * @throws ProtocolException If error cannot be recovered
     */
    protected abstract suspend fun handleNegativeResponse(
        serviceId: Int,
        nrc: Int,
        request: DiagnosticMessage
    ): DiagnosticMessage

    /**
     * Builds a request message for the given service.
     * 
     * Implementations should construct a properly formatted message
     * for the protocol, including any headers, addressing, and padding.
     * 
     * @param serviceId The OBD/UDS service ID
     * @param data Additional data bytes (optional)
     * @return Constructed diagnostic message
     */
    protected abstract fun buildRequest(
        serviceId: Int,
        data: ByteArray = byteArrayOf()
    ): DiagnosticMessage

    /**
     * Parses a raw response into a diagnostic message.
     * 
     * Implementations should:
     * - Extract the service ID and data
     * - Identify negative responses
     * - Handle multi-frame responses if applicable
     * 
     * @param response Raw response bytes
     * @param expectedService The service ID that was requested
     * @return Parsed diagnostic message
     * 
     * @throws ProtocolException If response cannot be parsed
     */
    protected abstract fun parseResponse(
        response: ByteArray,
        expectedService: Int
    ): DiagnosticMessage

    // ==================== Common Protected Implementations ====================

    /**
     * Executes an operation with retry logic.
     * 
     * Retries the operation on failure, with configurable delay between attempts.
     * Does not retry on [CancellationException] or if shutting down.
     * 
     * @param operation Name of the operation for logging
     * @param maxRetries Maximum number of retry attempts
     * @param retryDelayMs Delay between retries in milliseconds
     * @param block The operation to execute
     * @return Result of the operation
     * 
     * @throws Exception The last exception if all retries fail
     */
    protected suspend fun <T> executeWithRetry(
        operation: String,
        maxRetries: Int = _config.maxRetries,
        retryDelayMs: Long = _config.retryDelayMs,
        block: suspend () -> T
    ): T {
        var lastException: Exception? = null
        
        repeat(maxRetries + 1) { attempt ->
            if (isShuttingDown.get()) {
                throw CancellationException("Protocol shutting down")
            }
            
            try {
                return block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastException = e
                
                if (attempt < maxRetries) {
                    logger.debug("$operation attempt ${attempt + 1} failed, retrying: ${e.message}")
                    delay(retryDelayMs)
                } else {
                    logger.warn("$operation failed after ${attempt + 1} attempts: ${e.message}")
                }
            }
        }
        
        throw lastException ?: ProtocolException("$operation failed with unknown error")
    }

    /**
     * Logs a protocol operation for debugging.
     * 
     * @param operation Name of the operation
     * @param request Request bytes (optional)
     * @param response Response bytes (optional)
     * @param error Error that occurred (optional)
     */
    protected fun logOperation(
        operation: String,
        request: ByteArray? = null,
        response: ByteArray? = null,
        error: Throwable? = null
    ) {
        val message = buildString {
            append("[$protocolName] $operation")
            request?.let { append(" TX: ${it.toHexString()}") }
            response?.let { append(" RX: ${it.toHexString()}") }
            error?.let { append(" ERROR: ${it.message}") }
        }
        
        if (error != null) {
            logger.error(message, error)
        } else {
            logger.debug(message)
        }
    }

    /**
     * Calculates checksum for message data.
     * 
     * Default implementation uses simple sum modulo 256.
     * Override for protocol-specific checksum algorithms.
     * 
     * @param data Data bytes to checksum
     * @return Calculated checksum byte
     */
    protected open fun calculateChecksum(data: ByteArray): Byte {
        var sum = 0
        for (byte in data) {
            sum += byte.toInt() and 0xFF
        }
        return (sum and 0xFF).toByte()
    }

    /**
     * Validates checksum of received data.
     * 
     * Default implementation assumes last byte is checksum.
     * Override for protocol-specific validation.
     * 
     * @param data Data bytes including checksum
     * @return true if checksum is valid
     */
    protected open fun validateChecksum(data: ByteArray): Boolean {
        if (data.isEmpty()) return false
        
        val receivedChecksum = data.last()
        val calculatedChecksum = calculateChecksum(data.dropLast(1).toByteArray())
        
        return receivedChecksum == calculatedChecksum
    }

    /**
     * Validates that the protocol is in a valid state for operations.
     * 
     * @throws ProtocolException If protocol is not ready
     */
    protected fun validateState() {
        when (val currentState = _state.value) {
            is ProtocolState.Uninitialized -> 
                throw ProtocolException("Protocol not initialized")
            is ProtocolState.Shutdown -> 
                throw ProtocolException("Protocol is shutdown")
            is ProtocolState.Error -> 
                throw ProtocolException("Protocol in error state: ${currentState.error.message}")
            is ProtocolState.Ready, is ProtocolState.SessionActive -> {
                // Valid states for operations
            }
        }
    }

    /**
     * Emits a message event.
     */
    protected fun emitMessageEvent(event: MessageEvent) {
        protocolScope.launch {
            _messageEvents.emit(event)
        }
    }

    /**
     * Emits an error event.
     */
    protected fun emitError(error: ProtocolError) {
        protocolScope.launch {
            _errorEvents.emit(error)
        }
    }

    /**
     * Pads data to the required length.
     * 
     * @param data Data to pad
     * @param length Target length
     * @return Padded data
     */
    protected fun padData(data: ByteArray, length: Int): ByteArray {
        if (data.size >= length) return data
        
        return ByteArray(length) { index ->
            if (index < data.size) data[index]
            else 0x00
        }
    }

    companion object {
        private const val TAG = "BaseProtocol"
    }
}

// ==================== Protocol State ====================

/**
 * Represents the current state of a protocol instance.
 * 
 * States progress through the following lifecycle:
 * ```
 * Uninitialized → Initializing → Ready ⇄ SessionActive
 *                                  ↓
 *                              ShuttingDown → Shutdown
 *                                  ↓
 *                                Error
 * ```
 */
sealed class ProtocolState {
    

}

// ==================== Protocol Capabilities ====================

/**
 * Describes the capabilities and features of a protocol implementation.
 * 
 * This information is used to determine what operations are available
 * and how to configure communication parameters.
 * 
 * @property protocolType The protocol type
 * @property maxDataLength Maximum data bytes per message
 * @property supportsMultiFrame Whether multi-frame messages are supported
 * @property supportsExtendedAddressing Whether extended addressing is available
 * @property supportedServices Set of supported OBD/UDS service IDs
 * @property supportedBaudRates List of supported baud rates
 * @property supportsKeepAlive Whether keep-alive (tester present) is supported
 * @property supportsSecurityAccess Whether security access is supported
 * @property supportedSessionTypes Set of supported session types
 * @property features Set of supported protocol features
 */
data class ProtocolCapabilities(
    val protocolType: ProtocolType,
    val maxDataLength: Int,
    val supportsMultiFrame: Boolean,
    val supportsExtendedAddressing: Boolean,
    val supportedServices: Set<Int>,
    val supportedBaudRates: List<Int>,
    val supportsKeepAlive: Boolean,
    val supportsSecurityAccess: Boolean,
    val supportedSessionTypes: Set<SessionType>,
    val features: Set<ProtocolFeature>
) {
    /**
     * Checks if a service is supported.
     */
    fun supportsService(serviceId: Int): Boolean = serviceId in supportedServices
    
    /**
     * Checks if a feature is supported.
     */
    fun supportsFeature(feature: ProtocolFeature): Boolean = feature in features
    
    /**
     * Checks if a session type is supported.
     */
    fun supportsSessionType(sessionType: SessionType): Boolean = sessionType in supportedSessionTypes
    
    companion object {
        /**
         * Standard OBD-II capabilities.
         */
        fun forOBD(protocolType: ProtocolType): ProtocolCapabilities = ProtocolCapabilities(
            protocolType = protocolType,
            maxDataLength = 7,
            supportsMultiFrame = true,
            supportsExtendedAddressing = false,
            supportedServices = setOf(
                OBDConstants.SERVICE_CURRENT_DATA,
                OBDConstants.SERVICE_FREEZE_FRAME_DATA,
                OBDConstants.SERVICE_READ_DTC,
                OBDConstants.SERVICE_CLEAR_DTC,
                OBDConstants.SERVICE_OXYGEN_SENSORS,
                OBDConstants.SERVICE_CONTROL_ONBOARD,
                OBDConstants.SERVICE_PENDING_DTC,
                OBDConstants.SERVICE_VEHICLE_INFO,
                OBDConstants.SERVICE_PERMANENT_DTC
            ),
            supportedBaudRates = listOf(500000, 250000),
            supportsKeepAlive = false,
            supportsSecurityAccess = false,
            supportedSessionTypes = setOf(SessionType.DEFAULT),
            features = setOf(
                ProtocolFeature.READ_DTCS,
                ProtocolFeature.CLEAR_DTCS,
                ProtocolFeature.READ_FREEZE_FRAME,
                ProtocolFeature.READ_LIVE_DATA,
                ProtocolFeature.READ_VIN
            )
        )
        
        /**
         * Standard UDS capabilities.
         */
        fun forUDS(protocolType: ProtocolType): ProtocolCapabilities = ProtocolCapabilities(
            protocolType = protocolType,
            maxDataLength = 4095,
            supportsMultiFrame = true,
            supportsExtendedAddressing = true,
            supportedServices = setOf(
                0x10, 0x11, 0x14, 0x19, 0x22, 0x23, 0x24, 0x27,
                0x28, 0x29, 0x2A, 0x2C, 0x2E, 0x2F, 0x31, 0x34,
                0x35, 0x36, 0x37, 0x38, 0x3D, 0x3E, 0x83, 0x84,
                0x85, 0x86, 0x87
            ),
            supportedBaudRates = listOf(500000, 250000),
            supportsKeepAlive = true,
            supportsSecurityAccess = true,
            supportedSessionTypes = setOf(
                SessionType.DEFAULT,
                SessionType.PROGRAMMING,
                SessionType.EXTENDED
            ),
            features = setOf(
                ProtocolFeature.READ_DTCS,
                ProtocolFeature.CLEAR_DTCS,
                ProtocolFeature.READ_FREEZE_FRAME,
                ProtocolFeature.READ_LIVE_DATA,
                ProtocolFeature.READ_VIN,
                ProtocolFeature.ECU_IDENTIFICATION,
                ProtocolFeature.SECURITY_ACCESS,
                ProtocolFeature.ROUTINE_CONTROL,
                ProtocolFeature.IO_CONTROL,
                ProtocolFeature.PROGRAMMING,
                ProtocolFeature.CODING
            )
        )
    }
}

// ==================== Protocol Features ====================

/**
 * Enumeration of protocol features.
 * 
 * Features represent specific diagnostic operations that a protocol
 * may or may not support.
 */
enum class ProtocolFeature {
    /** Read Diagnostic Trouble Codes */
    READ_DTCS,
    
    /** Clear Diagnostic Trouble Codes */
    CLEAR_DTCS,
    
    /** Read Freeze Frame Data */
    READ_FREEZE_FRAME,
    
    /** Read Live Data / PIDs */
    READ_LIVE_DATA,
    
    /** Read Vehicle Identification Number */
    READ_VIN,
    
    /** Read ECU Identification */
    ECU_IDENTIFICATION,
    
    /** Security Access for Protected Operations */
    SECURITY_ACCESS,
    
    /** Routine Control */
    ROUTINE_CONTROL,
    
    /** Input/Output Control */
    IO_CONTROL,
    
    /** ECU Programming/Flashing */
    PROGRAMMING,
    
    /** ECU Coding/Configuration */
    CODING
}

// ==================== Protocol Configuration ====================

/**
 * Configuration options for protocol behavior.
 * 
 * @property responseTimeoutMs Default response timeout in milliseconds
 * @property extendedTimeoutMs Extended timeout for long operations
 * @property maxRetries Maximum number of retry attempts
 * @property retryDelayMs Delay between retries in milliseconds
 * @property enableKeepAlive Whether to enable automatic keep-alive
 * @property keepAliveIntervalMs Interval between keep-alive messages
 * @property enablePadding Whether to pad messages to fixed length
 * @property paddingByte Byte value used for padding
 * @property targetECUAddress Specific ECU address to target
 * @property useExtendedAddressing Whether to use extended addressing
 * @property extendedAddress Extended address byte
 */
data class BaseProtocolConfig(
    val responseTimeoutMs: Long = DEFAULT_RESPONSE_TIMEOUT,
    val extendedTimeoutMs: Long = DEFAULT_EXTENDED_TIMEOUT,
    val maxRetries: Int = DEFAULT_MAX_RETRIES,
    val retryDelayMs: Long = DEFAULT_RETRY_DELAY,
    val enableKeepAlive: Boolean = true,
    val keepAliveIntervalMs: Long = DEFAULT_KEEP_ALIVE_INTERVAL,
    val enablePadding: Boolean = true,
    val paddingByte: Byte = DEFAULT_PADDING_BYTE,
    val targetECUAddress: Int? = null,
    val useExtendedAddressing: Boolean = false,
    val extendedAddress: Byte = 0x00,
    val flowControlDelay: Long = 0L,
    val separationTimeMs: Long = 0L,
    val blockSize: Int = 0
) {
    /**
     * Builder for creating configurations.
     */
    class Builder {
        private var responseTimeoutMs: Long = DEFAULT_RESPONSE_TIMEOUT
        private var extendedTimeoutMs: Long = DEFAULT_EXTENDED_TIMEOUT
        private var maxRetries: Int = DEFAULT_MAX_RETRIES
        private var retryDelayMs: Long = DEFAULT_RETRY_DELAY
        private var enableKeepAlive: Boolean = true
        private var keepAliveIntervalMs: Long = DEFAULT_KEEP_ALIVE_INTERVAL
        private var enablePadding: Boolean = true
        private var paddingByte: Byte = DEFAULT_PADDING_BYTE
        private var targetECUAddress: Int? = null
        private var useExtendedAddressing: Boolean = false
        private var extendedAddress: Byte = 0x00
        private var flowControlDelay: Long = 0L
        private var separationTimeMs: Long = 0L
        private var blockSize: Int = 0
        
        fun responseTimeout(ms: Long) = apply { responseTimeoutMs = ms }
        fun extendedTimeout(ms: Long) = apply { extendedTimeoutMs = ms }
        fun maxRetries(retries: Int) = apply { maxRetries = retries }
        fun retryDelay(ms: Long) = apply { retryDelayMs = ms }
        fun keepAlive(enabled: Boolean) = apply { enableKeepAlive = enabled }
        fun keepAliveInterval(ms: Long) = apply { keepAliveIntervalMs = ms }
        fun padding(enabled: Boolean) = apply { enablePadding = enabled }
        fun paddingByte(byte: Byte) = apply { paddingByte = byte }
        fun targetECU(address: Int) = apply { targetECUAddress = address }
        fun extendedAddressing(enabled: Boolean) = apply { useExtendedAddressing = enabled }
        fun extendedAddress(address: Byte) = apply { extendedAddress = address }
        fun flowControlDelay(ms: Long) = apply { flowControlDelay = ms }
        fun separationTime(ms: Long) = apply { separationTimeMs = ms }
        fun blockSize(size: Int) = apply { blockSize = size }
        
        fun build() = BaseProtocolConfig(
            responseTimeoutMs = responseTimeoutMs,
            extendedTimeoutMs = extendedTimeoutMs,
            maxRetries = maxRetries,
            retryDelayMs = retryDelayMs,
            enableKeepAlive = enableKeepAlive,
            keepAliveIntervalMs = keepAliveIntervalMs,
            enablePadding = enablePadding,
            paddingByte = paddingByte,
            targetECUAddress = targetECUAddress,
            useExtendedAddressing = useExtendedAddressing,
            extendedAddress = extendedAddress,
            flowControlDelay = flowControlDelay,
            separationTimeMs = separationTimeMs,
            blockSize = blockSize
        )
    }
    
    companion object {
        const val DEFAULT_RESPONSE_TIMEOUT = 1000L
        const val DEFAULT_EXTENDED_TIMEOUT = 5000L
        const val DEFAULT_MAX_RETRIES = 3
        const val DEFAULT_RETRY_DELAY = 100L
        const val DEFAULT_KEEP_ALIVE_INTERVAL = 2000L
        const val DEFAULT_PADDING_BYTE = 0xCC.toByte()
        
        /** Default configuration for general use. */
        val DEFAULT = BaseProtocolConfig()
        
        /** Configuration optimized for OBD-II operations. */
        fun forOBD(): BaseProtocolConfig = BaseProtocolConfig(
            responseTimeoutMs = 200L,
            maxRetries = 3,
            enableKeepAlive = false,
            enablePadding = false
        )
        
        /** Configuration optimized for UDS operations. */
        fun forUDS(): BaseProtocolConfig = BaseProtocolConfig(
            responseTimeoutMs = 1000L,
            extendedTimeoutMs = 5000L,
            maxRetries = 3,
            enableKeepAlive = true,
            keepAliveIntervalMs = 2000L,
            enablePadding = true
        )
        
        /** Configuration for ECU programming operations. */
        fun forProgramming(): BaseProtocolConfig = BaseProtocolConfig(
            responseTimeoutMs = 5000L,
            extendedTimeoutMs = 30000L,
            maxRetries = 1,
            enableKeepAlive = true,
            keepAliveIntervalMs = 1000L,
            enablePadding = true
        )
        
        /** Creates a builder for custom configuration. */
        fun builder(): Builder = Builder()
    }
}

// ==================== Protocol Errors ====================

/**
 * Represents errors that can occur during protocol operations.
 */
sealed class ProtocolError(
    open val message: String,
    open val code: Int = 0,
    open val recoverable: Boolean = true,
    open val nrc: Int? = null,
    open val serviceId: Int? = null,
    open val details: Map<String, Any> = emptyMap(),
    open val cause: Throwable? = null
) {
    /** Error category based on error code ranges. */
    val category: ErrorCategory
        get() = ErrorCategory.fromCode(code)
    
    /** NRC description if this is an NRC error. */
    val nrcDescription: String?
        get() = nrc?.let { NRCConstants.getDescription(it) }
    
    /** Checks if this error was caused by a timeout. */
    val isTimeout: Boolean
        get() = code in TIMEOUT_ERROR_RANGE || cause is TimeoutException
    
    /** Checks if this error was caused by an NRC. */
    val isNegativeResponse: Boolean
        get() = nrc != null
    
    /** Checks if this error indicates security access is required. */
    val requiresSecurityAccess: Boolean
        get() = nrc == NRCConstants.SECURITY_ACCESS_DENIED ||
                nrc == NRCConstants.EXCEEDED_NUMBER_OF_ATTEMPTS
    
    /** Checks if this error indicates conditions are not correct. */
    val conditionsNotCorrect: Boolean
        get() = nrc == NRCConstants.CONDITIONS_NOT_CORRECT
    
    /** Formats the error for logging. */
    fun toLogString(): String = buildString {
        append("ProtocolError[")
        append("code=$code")
        append(", category=$category")
        nrc?.let { append(", nrc=0x${it.toString(16).uppercase()}") }
        serviceId?.let { append(", service=0x${it.toString(16).uppercase()}") }
        append(", recoverable=$recoverable")
        append("]: $message")
        nrcDescription?.let { append(" ($it)") }
        cause?.let { append(" caused by: ${it.javaClass.simpleName}: ${it.message}") }
    }
    
    override fun toString(): String = "ProtocolError(code=$code, message='$message')"
    
    /** Timeout waiting for response */
    data class Timeout(
        override val message: String,
        override val recoverable: Boolean = true,
        override val nrc: Int? = null,
        override val serviceId: Int? = null,
        override val details: Map<String, Any> = emptyMap(),
        override val cause: Throwable? = null
    ) : ProtocolError(message, ERROR_TIMEOUT, recoverable, nrc, serviceId, details, cause)
    
    /** Communication failure */
    data class CommunicationError(
        override val message: String,
        override val recoverable: Boolean = true,
        override val nrc: Int? = null,
        override val serviceId: Int? = null,
        override val details: Map<String, Any> = emptyMap(),
        override val cause: Throwable? = null
    ) : ProtocolError(message, ERROR_COMMUNICATION, recoverable, nrc, serviceId, details, cause)
    
    /** Connection error */
    data class ConnectionError(
        override val message: String,
        override val recoverable: Boolean = true,
        override val nrc: Int? = null,
        override val serviceId: Int? = null,
        override val details: Map<String, Any> = emptyMap(),
        override val cause: Throwable? = null
    ) : ProtocolError(message, ERROR_CONNECTION, recoverable, nrc, serviceId, details, cause)
    
    /** Protocol violation or invalid data */
    data class ProtocolViolation(
        override val message: String,
        override val recoverable: Boolean = true,
        override val nrc: Int? = null,
        override val serviceId: Int? = null,
        override val details: Map<String, Any> = emptyMap(),
        override val cause: Throwable? = null
    ) : ProtocolError(message, ERROR_PROTOCOL, recoverable, nrc, serviceId, details, cause)
    
    /** Session management error */
    data class SessionError(
        override val message: String,
        override val recoverable: Boolean = true,
        override val nrc: Int? = null,
        override val serviceId: Int? = null,
        override val details: Map<String, Any> = emptyMap(),
        override val cause: Throwable? = null
    ) : ProtocolError(message, ERROR_SESSION, recoverable, nrc, serviceId, details, cause)
    
    /** Security access error */
    data class SecurityError(
        override val message: String,
        val nrcValue: Int = 0,
        override val recoverable: Boolean = false,
        override val nrc: Int? = null,
        override val serviceId: Int? = null,
        override val details: Map<String, Any> = emptyMap(),
        override val cause: Throwable? = null
    ) : ProtocolError(message, ERROR_SECURITY, recoverable, nrcValue.takeIf { it != 0 }, serviceId, details, cause)
    
    /** ECU returned negative response */
    data class NegativeResponse(
        override val message: String,
        val serviceIdValue: Int,
        val nrcValue: Int,
        override val recoverable: Boolean = true,
        override val details: Map<String, Any> = emptyMap(),
        override val cause: Throwable? = null
    ) : ProtocolError(message, ERROR_NEGATIVE_RESPONSE, recoverable, nrcValue, serviceIdValue, details, cause)
    
    /** Configuration error */
    data class ConfigurationError(
        override val message: String,
        override val recoverable: Boolean = false,
        override val nrc: Int? = null,
        override val serviceId: Int? = null,
        override val details: Map<String, Any> = emptyMap(),
        override val cause: Throwable? = null
    ) : ProtocolError(message, ERROR_CONFIGURATION, recoverable, nrc, serviceId, details, cause)
    
    /** Error category enumeration. */
    enum class ErrorCategory(val range: IntRange, val description: String) {
        COMMUNICATION(1000..1999, "Communication Error"),
        PROTOCOL(2000..2999, "Protocol Error"),
        NRC(3000..3999, "Negative Response"),
        TIMEOUT(4000..4999, "Timeout Error"),
        SECURITY(5000..5999, "Security Error"),
        STATE(6000..6999, "State Error"),
        CONFIGURATION(7000..7999, "Configuration Error"),
        INTERNAL(8000..8999, "Internal Error"),
        UNKNOWN(9000..9999, "Unknown Error");
        
        companion object {
            fun fromCode(code: Int): ErrorCategory =
                entries.find { code in it.range } ?: UNKNOWN
        }
    }
    
    companion object {
        // Error code ranges
        private val TIMEOUT_ERROR_RANGE = 4000..4999
        
        // Common error codes
        const val CODE_COMMUNICATION_FAILED = 1001
        const val CODE_CONNECTION_LOST = 1002
        const val CODE_BUS_ERROR = 1003
        const val CODE_PROTOCOL_NOT_SUPPORTED = 2001
        const val CODE_INVALID_MESSAGE = 2002
        const val CODE_CHECKSUM_ERROR = 2003
        const val CODE_FRAME_ERROR = 2004
        const val CODE_NRC_RECEIVED = 3001
        const val CODE_TIMEOUT = 4001
        const val CODE_RESPONSE_TIMEOUT = 4002
        const val CODE_KEEP_ALIVE_TIMEOUT = 4003
        const val CODE_SECURITY_ACCESS_DENIED = 5001
        const val CODE_SECURITY_LOCKED = 5002
        const val CODE_INVALID_STATE = 6001
        const val CODE_SESSION_NOT_ACTIVE = 6002
        const val CODE_NOT_INITIALIZED = 6003
        const val CODE_INVALID_CONFIG = 7001
        const val CODE_INTERNAL_ERROR = 8001
        
        /**
         * Creates a ProtocolError from an exception.
         *
         * @param e The exception
         * @param operation Optional operation description
         * @return ProtocolError instance
         */
        fun fromException(e: Exception, operation: String? = null): ProtocolError {
            val (code, message) = when (e) {
                is TimeoutException -> CODE_TIMEOUT to "Operation timed out"
                is CommunicationException -> CODE_COMMUNICATION_FAILED to "Communication failed"
                is ProtocolException -> CODE_PROTOCOL_NOT_SUPPORTED to e.message
                else -> CODE_INTERNAL_ERROR to "Internal error occurred"
            }

            val fullMessage = operation?.let { "$it: ${message ?: e.message}" } ?: (message ?: e.message ?: "Unknown error")
            val details = mapOf("operation" to (operation ?: "unknown"))

            return when (e) {
                is TimeoutException -> Timeout(fullMessage, recoverable = true, details = details, cause = e)
                is CommunicationException -> CommunicationError(fullMessage, recoverable = true, details = details, cause = e)
                is ProtocolException -> ProtocolViolation(fullMessage, recoverable = false, details = details, cause = e)
                else -> ProtocolViolation(fullMessage, recoverable = true, details = details, cause = e)
            }
        }
        
        /**
         * Creates a ProtocolError from a Negative Response Code.
         *
         * @param nrc Negative response code
         * @param serviceId Service ID that was rejected
         * @return ProtocolError instance
         */
        fun fromNRC(nrc: Int, serviceId: Int): ProtocolError {
            val description = NRCConstants.getDescription(nrc)
            val recoverable = when (nrc) {
                NRCConstants.SERVICE_NOT_SUPPORTED,
                NRCConstants.SUB_FUNCTION_NOT_SUPPORTED,
                NRCConstants.SERVICE_NOT_SUPPORTED_IN_ACTIVE_SESSION -> false
                else -> true
            }

            return NegativeResponse(
                message = "Service 0x${serviceId.toString(16).uppercase()} rejected: $description",
                serviceIdValue = serviceId,
                nrcValue = nrc,
                recoverable = recoverable,
                details = mapOf(
                    "nrc" to nrc,
                    "serviceId" to serviceId,
                    "nrcName" to NRCConstants.getName(nrc)
                )
            )
        }
        
        /**
         * Creates a timeout error.
         *
         * @param operation Operation that timed out
         * @param timeoutMs Timeout duration
         * @return ProtocolError instance
         */
        fun timeout(operation: String, timeoutMs: Long): ProtocolError = Timeout(
            message = "$operation timed out after ${timeoutMs}ms",
            recoverable = true,
            details = mapOf("operation" to operation, "timeout_ms" to timeoutMs)
        )
        
        /**
         * Creates a state error.
         *
         * @param message Error message
         * @param currentState Current state
         * @return ProtocolError instance
         */
        fun stateError(message: String, currentState: ProtocolState): ProtocolError = SessionError(
            message = message,
            recoverable = false,
            details = mapOf("currentState" to currentState.toString())
        )
        
        /**
         * Creates a communication error.
         *
         * @param message Error message
         * @param cause Underlying cause
         * @return ProtocolError instance
         */
        fun communicationError(message: String, cause: Throwable? = null): ProtocolError = CommunicationError(
            message = message,
            cause = cause,
            recoverable = true
        )

        // Internal error type codes (used by the nested ProtocolError data classes)
        const val ERROR_TIMEOUT = 1
        const val ERROR_COMMUNICATION = 2
        const val ERROR_CONNECTION = 3
        const val ERROR_PROTOCOL = 4
        const val ERROR_SESSION = 5
        const val ERROR_SECURITY = 6
        const val ERROR_NEGATIVE_RESPONSE = 7
        const val ERROR_CONFIGURATION = 8
    }
}

// ==================== Message Events ====================

/**
 * Events related to message transmission and reception.
 */
sealed class MessageEvent {
    abstract val sequence: Long
    
    /** Message is being sent */
    data class Sending(
        override val sequence: Long,
        val message: DiagnosticMessage
    ) : MessageEvent()
    
    /** Response received */
    data class Received(
        override val sequence: Long,
        val response: DiagnosticMessage,
        val elapsedMs: Long
    ) : MessageEvent()
    
    /** Message timed out */
    data class Timeout(
        override val sequence: Long,
        val elapsedMs: Long
    ) : MessageEvent()
    
    /** Message was cancelled */
    data class Cancelled(
        override val sequence: Long
    ) : MessageEvent()
    
    /** Message error */
    data class Error(
        override val sequence: Long,
        val error: Throwable
    ) : MessageEvent()
}

// ==================== Negative Response Codes ====================

/**
 * Standard UDS Negative Response Codes (NRC) as per ISO 14229.
 */
object NegativeResponseCodes {
    const val GENERAL_REJECT = 0x10
    const val SERVICE_NOT_SUPPORTED = 0x11
    const val SUB_FUNCTION_NOT_SUPPORTED = 0x12
    const val INCORRECT_MESSAGE_LENGTH = 0x13
    const val RESPONSE_TOO_LONG = 0x14
    const val BUSY_REPEAT_REQUEST = 0x21
    const val CONDITIONS_NOT_CORRECT = 0x22
    const val REQUEST_SEQUENCE_ERROR = 0x24
    const val NO_RESPONSE_FROM_SUBNET = 0x25
    const val FAILURE_PREVENTS_EXECUTION = 0x26
    const val REQUEST_OUT_OF_RANGE = 0x31
    const val SECURITY_ACCESS_DENIED = 0x33
    const val INVALID_KEY = 0x35
    const val EXCEEDED_ATTEMPTS = 0x36
    const val REQUIRED_TIME_DELAY = 0x37
    const val UPLOAD_DOWNLOAD_NOT_ACCEPTED = 0x70
    const val TRANSFER_DATA_SUSPENDED = 0x71
    const val GENERAL_PROGRAMMING_FAILURE = 0x72
    const val WRONG_BLOCK_SEQUENCE = 0x73
    const val REQUEST_CORRECTLY_RECEIVED_PENDING = 0x78
    const val SUB_FUNCTION_NOT_SUPPORTED_ACTIVE_SESSION = 0x7E
    const val SERVICE_NOT_SUPPORTED_ACTIVE_SESSION = 0x7F
    const val RPM_TOO_HIGH = 0x81
    const val RPM_TOO_LOW = 0x82
    const val ENGINE_RUNNING = 0x83
    const val ENGINE_NOT_RUNNING = 0x84
    const val ENGINE_RUN_TIME_TOO_LOW = 0x85
    const val TEMPERATURE_TOO_HIGH = 0x86
    const val TEMPERATURE_TOO_LOW = 0x87
    const val VEHICLE_SPEED_TOO_HIGH = 0x88
    const val VEHICLE_SPEED_TOO_LOW = 0x89
    const val THROTTLE_TOO_HIGH = 0x8A
    const val THROTTLE_TOO_LOW = 0x8B
    const val TRANSMISSION_NOT_NEUTRAL = 0x8C
    const val TRANSMISSION_NOT_GEAR = 0x8D
    const val BRAKE_NOT_APPLIED = 0x8F
    const val SHIFTER_NOT_PARK = 0x90
    const val TORQUE_CONVERTER_CLUTCH_LOCKED = 0x91
    const val VOLTAGE_TOO_HIGH = 0x92
    const val VOLTAGE_TOO_LOW = 0x93
    
    /**
     * Gets human-readable description for NRC.
     */
    fun getDescription(nrc: Int): String = when (nrc) {
        GENERAL_REJECT -> "General reject"
        SERVICE_NOT_SUPPORTED -> "Service not supported"
        SUB_FUNCTION_NOT_SUPPORTED -> "Sub-function not supported"
        INCORRECT_MESSAGE_LENGTH -> "Incorrect message length or invalid format"
        RESPONSE_TOO_LONG -> "Response too long"
        BUSY_REPEAT_REQUEST -> "Busy - repeat request"
        CONDITIONS_NOT_CORRECT -> "Conditions not correct"
        REQUEST_SEQUENCE_ERROR -> "Request sequence error"
        NO_RESPONSE_FROM_SUBNET -> "No response from subnet component"
        FAILURE_PREVENTS_EXECUTION -> "Failure prevents execution of requested action"
        REQUEST_OUT_OF_RANGE -> "Request out of range"
        SECURITY_ACCESS_DENIED -> "Security access denied"
        INVALID_KEY -> "Invalid key"
        EXCEEDED_ATTEMPTS -> "Exceeded number of attempts"
        REQUIRED_TIME_DELAY -> "Required time delay not expired"
        UPLOAD_DOWNLOAD_NOT_ACCEPTED -> "Upload/download not accepted"
        TRANSFER_DATA_SUSPENDED -> "Transfer data suspended"
        GENERAL_PROGRAMMING_FAILURE -> "General programming failure"
        WRONG_BLOCK_SEQUENCE -> "Wrong block sequence counter"
        REQUEST_CORRECTLY_RECEIVED_PENDING -> "Request correctly received - response pending"
        SUB_FUNCTION_NOT_SUPPORTED_ACTIVE_SESSION -> "Sub-function not supported in active session"
        SERVICE_NOT_SUPPORTED_ACTIVE_SESSION -> "Service not supported in active session"
        RPM_TOO_HIGH -> "RPM too high"
        RPM_TOO_LOW -> "RPM too low"
        ENGINE_RUNNING -> "Engine is running"
        ENGINE_NOT_RUNNING -> "Engine is not running"
        ENGINE_RUN_TIME_TOO_LOW -> "Engine run time too low"
        TEMPERATURE_TOO_HIGH -> "Temperature too high"
        TEMPERATURE_TOO_LOW -> "Temperature too low"
        VEHICLE_SPEED_TOO_HIGH -> "Vehicle speed too high"
        VEHICLE_SPEED_TOO_LOW -> "Vehicle speed too low"
        THROTTLE_TOO_HIGH -> "Throttle/pedal too high"
        THROTTLE_TOO_LOW -> "Throttle/pedal too low"
        TRANSMISSION_NOT_NEUTRAL -> "Transmission not in neutral"
        TRANSMISSION_NOT_GEAR -> "Transmission not in gear"
        BRAKE_NOT_APPLIED -> "Brake switch(es) not closed"
        SHIFTER_NOT_PARK -> "Shifter lever not in park"
        TORQUE_CONVERTER_CLUTCH_LOCKED -> "Torque converter clutch locked"
        VOLTAGE_TOO_HIGH -> "Voltage too high"
        VOLTAGE_TOO_LOW -> "Voltage too low"
        else -> "Unknown NRC (0x${nrc.toString(16).uppercase()})"
    }
    
    /**
     * Whether this NRC indicates a temporary condition that may resolve.
     */
    fun isTemporary(nrc: Int): Boolean = nrc in listOf(
        BUSY_REPEAT_REQUEST,
        CONDITIONS_NOT_CORRECT,
        REQUEST_CORRECTLY_RECEIVED_PENDING,
        REQUIRED_TIME_DELAY
    )
    
    /**
     * Whether retry is appropriate for this NRC.
     */
    fun shouldRetry(nrc: Int): Boolean = nrc in listOf(
        BUSY_REPEAT_REQUEST,
        REQUEST_CORRECTLY_RECEIVED_PENDING
    )
}

// ==================== Extension Functions ====================

/**
 * Converts a ByteArray to a hex string for logging.
 */
fun ByteArray.toHexString(): String =
    joinToString(" ") { String.format("%02X", it) }

/**
 * Converts a hex string to ByteArray.
 */
fun String.hexToByteArray(): ByteArray {
    val cleanHex = replace(" ", "").replace("0x", "")
    return ByteArray(cleanHex.length / 2) { i ->
        cleanHex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
    }
}