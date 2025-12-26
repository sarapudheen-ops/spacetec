package com.spacetec.protocol.core

import com.spacetec.core.domain.protocol.DiagnosticMessage
import com.spacetec.core.domain.protocol.ProtocolState
import com.spacetec.core.domain.protocol.ProtocolType
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

// Extension properties for ProtocolType
val ProtocolType.isCAN: Boolean 
    get() = this in listOf(ProtocolType.ISO_15765_4_CAN_11BIT_500K, ProtocolType.ISO_15765_4_CAN_11BIT_250K, 
                         ProtocolType.ISO_15765_4_CAN_29BIT_500K, ProtocolType.ISO_15765_4_CAN_29BIT_250K,
                         ProtocolType.UDS_ON_CAN_11BIT_500K, ProtocolType.UDS_ON_CAN_29BIT_500K)

val ProtocolType.isKLine: Boolean 
    get() = this in listOf(ProtocolType.ISO_14230_4_KWP_FAST, ProtocolType.ISO_9141_2)

val ProtocolType.isJ1850: Boolean 
    get() = this in listOf(ProtocolType.SAE_J1850_PWM, ProtocolType.SAE_J1850_VPW)

// ============================================================================
// SECTION 1: PROTOCOL STATE SEALED CLASS
// ============================================================================

// ProtocolState is defined in Protocol.kt

// ============================================================================
// SECTION 2: SESSION TYPE ENUM
// ============================================================================

/**
 * Diagnostic session types as defined by UDS (ISO 14229) and KWP2000 (ISO 14230).
 *
 * Each session type provides different access levels to ECU functions:
 * - Default: Basic read operations
 * - Extended: Enhanced diagnostics, actuator tests
 * - Programming: ECU reprogramming, calibration
 * - Safety: Safety-critical systems (restricted)
 *
 * @property id Session type identifier (sub-function value)
 * @property description Human-readable description
 * @property requiresSecurity Whether security access is typically required
 * @property keepAliveRequired Whether tester present is required to maintain session
 */
enum class SessionType(
    val id: Int,
    val description: String,
    val requiresSecurity: Boolean = false,
    val keepAliveRequired: Boolean = true
) {
    /**
     * Default diagnostic session (0x01).
     * Basic read access, limited write capability.
     */
    DEFAULT(
        id = 0x01,
        description = "Default Session",
        requiresSecurity = false,
        keepAliveRequired = false
    ),
    
    /**
     * Programming session (0x02).
     * Required for ECU reprogramming and calibration.
     */
    PROGRAMMING(
        id = 0x02,
        description = "Programming Session",
        requiresSecurity = true,
        keepAliveRequired = true
    ),
    
    /**
     * Extended diagnostic session (0x03).
     * Enhanced diagnostics, actuator tests, I/O control.
     */
    EXTENDED(
        id = 0x03,
        description = "Extended Diagnostic Session",
        requiresSecurity = false,
        keepAliveRequired = true
    ),
    
    /**
     * Safety system diagnostic session (0x04).
     * Access to safety-critical systems (airbags, ABS, etc.).
     */
    SAFETY(
        id = 0x04,
        description = "Safety System Diagnostic Session",
        requiresSecurity = true,
        keepAliveRequired = true
    ),
    
    /**
     * EOL (End of Line) programming session (0x40).
     * Factory programming session.
     */
    EOL_PROGRAMMING(
        id = 0x40,
        description = "EOL Programming Session",
        requiresSecurity = true,
        keepAliveRequired = true
    ),
    
    /**
     * Manufacturer specific session (0x41-0x5F range, using 0x41).
     * OEM-specific diagnostic functions.
     */
    MANUFACTURER_SPECIFIC(
        id = 0x41,
        description = "Manufacturer Specific Session",
        requiresSecurity = true,
        keepAliveRequired = true
    ),
    
    /**
     * Development session (0x4F).
     * Development and calibration tools only.
     */
    DEVELOPMENT(
        id = 0x4F,
        description = "Development Session",
        requiresSecurity = true,
        keepAliveRequired = true
    );
    
    companion object {
        private val idMap = entries.associateBy { it.id }
        
        /**
         * Gets session type from its ID.
         *
         * @param id Session type identifier
         * @return SessionType or null if not found
         */
        fun fromId(id: Int): SessionType? = idMap[id]
        
        /**
         * Gets session type from its ID, throwing if not found.
         *
         * @param id Session type identifier
         * @return SessionType
         * @throws IllegalArgumentException if ID is not valid
         */
        fun fromIdOrThrow(id: Int): SessionType = 
            fromId(id) ?: throw IllegalArgumentException("Unknown session type ID: 0x${id.toString(16)}")
        
        /**
         * Checks if an ID represents a valid session type.
         */
        fun isValidId(id: Int): Boolean = idMap.containsKey(id)
        
        /**
         * Gets all session types that require security access.
         */
        fun getSecurityRequiredSessions(): List<SessionType> = 
            entries.filter { it.requiresSecurity }
    }
    
    /**
     * Creates the sub-function byte for DiagnosticSessionControl request.
     *
     * @param suppressResponse Whether to suppress positive response
     * @return Sub-function byte value
     */
    fun toSubFunction(suppressResponse: Boolean = false): Int =
        if (suppressResponse) id or 0x80 else id
}

// ============================================================================
// SECTION 3: PROTOCOL EVENT SEALED CLASS
// ============================================================================

/**
 * Events emitted by the protocol manager for logging, monitoring, and debugging.
 *
 * Events provide a comprehensive audit trail of all protocol operations,
 * useful for diagnostics, debugging, and user feedback.
 *
 * Usage:
 * ```kotlin
 * protocolManager.events.collect { event ->
 *     when (event) {
 *         is ProtocolEvent.StateChanged -> updateUI(event.newState)
 *         is ProtocolEvent.ErrorOccurred -> showError(event.error)
 *         // ...
 *     }
 * }
 * ```
 */
sealed class ProtocolEvent {
    /** Timestamp when the event occurred */
    abstract val timestamp: Long
    
    /**
     * Protocol manager state changed.
     *
     * @property oldState Previous state
     * @property newState New state
     * @property timestamp Event timestamp
     */
    data class StateChanged(
        val oldState: ProtocolState,
        val newState: ProtocolState,
        override val timestamp: Long = System.currentTimeMillis()
    ) : ProtocolEvent()
    
    /**
     * Diagnostic message was sent.
     *
     * @property message The sent message
     * @property targetAddress Target ECU address
     * @property timestamp Event timestamp
     */
    data class MessageSent(
        val message: DiagnosticMessage,
        val targetAddress: Int? = null,
        override val timestamp: Long = System.currentTimeMillis()
    ) : ProtocolEvent()
    
    /**
     * Diagnostic message was received.
     *
     * @property message The received message
     * @property sourceAddress Source ECU address
     * @property responseTimeMs Response time in milliseconds
     * @property timestamp Event timestamp
     */
    data class MessageReceived(
        val message: DiagnosticMessage,
        val sourceAddress: Int? = null,
        val responseTimeMs: Long = 0,
        override val timestamp: Long = System.currentTimeMillis()
    ) : ProtocolEvent()
    
    /**
     * An error occurred during protocol operation.
     *
     * @property error The error details
     * @property operation Operation that was being performed
     * @property timestamp Event timestamp
     */
    data class ErrorOccurred(
        val error: ProtocolError,
        val operation: String? = null,
        override val timestamp: Long = System.currentTimeMillis()
    ) : ProtocolEvent()
    
    /**
     * Diagnostic session started.
     *
     * @property sessionType Type of session started
     * @property ecuAddress Target ECU address
     * @property timestamp Event timestamp
     */
    data class SessionStarted(
        val sessionType: SessionType,
        val ecuAddress: Int? = null,
        override val timestamp: Long = System.currentTimeMillis()
    ) : ProtocolEvent()
    
    /**
     * Diagnostic session ended.
     *
     * @property reason Reason for session end
     * @property sessionType Session type that ended
     * @property duration Session duration in milliseconds
     * @property timestamp Event timestamp
     */
    data class SessionEnded(
        val reason: String,
        val sessionType: SessionType? = null,
        val duration: Long = 0,
        override val timestamp: Long = System.currentTimeMillis()
    ) : ProtocolEvent()
    
    /**
     * Vehicle protocol was detected.
     *
     * @property protocol Detected protocol type
     * @property detectionTimeMs Time taken to detect in milliseconds
     * @property timestamp Event timestamp
     */
    data class ProtocolDetected(
        val protocol: ProtocolType,
        val detectionTimeMs: Long = 0,
        override val timestamp: Long = System.currentTimeMillis()
    ) : ProtocolEvent()
    
    /**
     * ECU was discovered during scan.
     *
     * @property address ECU address
     * @property name ECU name (if available)
     * @property type ECU type (if identified)
     * @property timestamp Event timestamp
     */
    data class ECUDiscovered(
        val address: Int,
        val name: String? = null,
        val type: ECUType? = null,
        override val timestamp: Long = System.currentTimeMillis()
    ) : ProtocolEvent()
    
    /**
     * Keep-alive (Tester Present) was sent.
     *
     * @property success Whether the keep-alive was acknowledged
     * @property timestamp Event timestamp
     */
    data class KeepAliveSent(
        val success: Boolean = true,
        override val timestamp: Long = System.currentTimeMillis()
    ) : ProtocolEvent()
    
    /**
     * A timeout occurred during operation.
     *
     * @property operation Operation that timed out
     * @property timeoutMs Timeout duration in milliseconds
     * @property timestamp Event timestamp
     */
    data class TimeoutOccurred(
        val operation: String,
        val timeoutMs: Long,
        override val timestamp: Long = System.currentTimeMillis()
    ) : ProtocolEvent()
    
    /**
     * Security access was attempted.
     *
     * @property level Security level attempted
     * @property success Whether access was granted
     * @property timestamp Event timestamp
     */
    data class SecurityAccessAttempted(
        val level: Int,
        val success: Boolean,
        override val timestamp: Long = System.currentTimeMillis()
    ) : ProtocolEvent()
    
    /**
     * Negative response code received.
     *
     * @property serviceId Service that was rejected
     * @property nrc Negative response code
     * @property description NRC description
     * @property timestamp Event timestamp
     */
    data class NegativeResponseReceived(
        val serviceId: Int,
        val nrc: Int,
        val description: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : ProtocolEvent()
    
    /**
     * Protocol configuration was updated.
     *
     * @property config New configuration
     * @property timestamp Event timestamp
     */
    data class ConfigurationUpdated(
        val config: ProtocolConfig,
        override val timestamp: Long = System.currentTimeMillis()
    ) : ProtocolEvent()
    
    /**
     * Data transfer progress update.
     *
     * @property operation Transfer operation (upload/download)
     * @property bytesTransferred Bytes transferred so far
     * @property totalBytes Total bytes to transfer
     * @property progress Progress from 0.0 to 1.0
     * @property timestamp Event timestamp
     */
    data class TransferProgress(
        val operation: String,
        val bytesTransferred: Long,
        val totalBytes: Long,
        val progress: Float,
        override val timestamp: Long = System.currentTimeMillis()
    ) : ProtocolEvent()
}

// ============================================================================
// SECTION 4: PROTOCOL ERROR DATA CLASS
// ============================================================================

/**
 * Represents a protocol error with detailed information.
 *
 * Protocol errors can originate from various sources:
 * - Communication failures (timeout, bus errors)
 * - Protocol violations (invalid messages, checksum errors)
 * - ECU rejections (negative response codes)
 * - Application errors (invalid parameters, state errors)
 *
 * @property code Error code (unique identifier)
 * @property message Human-readable error message
 * @property cause Underlying exception (if any)
 * @property recoverable Whether automatic recovery is possible
 * @property nrc Negative response code (if from ECU rejection)
 * @property serviceId Service ID that caused the error (if applicable)
 * @property details Additional error details
 */


// ============================================================================
// SECTION 5: DETECTION PROGRESS SEALED CLASS
// ============================================================================

/**
 * Represents the progress of protocol auto-detection.
 *
 * Detection progress provides real-time updates during the auto-detection
 * process, allowing UI feedback and cancellation support.
 *
 * Usage:
 * ```kotlin
 * protocolManager.detectProtocolWithProgress().collect { progress ->
 *     when (progress) {
 *         is DetectionProgress.Testing -> updateProgress(progress.protocol, progress.progress)
 *         is DetectionProgress.Detected -> onProtocolDetected(progress.protocol)
 *         is DetectionProgress.Failed -> showError(progress.error)
 *         // ...
 *     }
 * }
 * ```
 */

// ============================================================================
// SECTION 6: PROTOCOL CONFIG DATA CLASS
// ============================================================================

/**
 * Configuration for the protocol manager.
 *
 * ProtocolConfig defines all operational parameters for protocol communication
 * including timeouts, retry behavior, keep-alive settings, and protocol-specific options.
 *
 * Example:
 * ```kotlin
 * val config = ProtocolConfig.Builder()
 *     .autoDetect(true)
 *     .timeout(2000L)
 *     .retries(3)
 *     .keepAlive(enabled = true, intervalMs = 2000L)
 *     .build()
 * ```
 *
 * @property autoDetect Whether to auto-detect protocol if not specified
 * @property preferredProtocol Preferred protocol to try first during detection
 * @property targetECUAddress Target ECU address for communication (null for broadcast)
 * @property responseTimeoutMs Default response timeout in milliseconds
 * @property extendedTimeoutMs Extended timeout for slow operations
 * @property maxRetries Maximum number of retries on failure
 * @property retryDelayMs Delay between retries in milliseconds
 * @property enableKeepAlive Whether to enable automatic tester present
 * @property keepAliveIntervalMs Interval for keep-alive messages
 * @property enablePadding Whether to pad messages to standard length
 * @property paddingByte Byte value used for padding
 * @property useExtendedAddressing Whether to use extended addressing (ISO-TP)
 * @property extendedAddress Extended address byte
 * @property canBaudRate CAN bus baud rate
 * @property enableCanFD Whether to enable CAN FD support
 * @property separationTimeMs Separation time for ISO-TP consecutive frames
 * @property blockSize Block size for ISO-TP flow control
 * @property enableLogging Whether to enable detailed protocol logging
 */
data class ProtocolConfig(
    val autoDetect: Boolean = true,
    val preferredProtocol: ProtocolType? = null,
    val targetECUAddress: Int? = null,
    val responseTimeoutMs: Long = DEFAULT_RESPONSE_TIMEOUT_MS,
    val extendedTimeoutMs: Long = DEFAULT_EXTENDED_TIMEOUT_MS,
    val maxRetries: Int = DEFAULT_MAX_RETRIES,
    val retryDelayMs: Long = DEFAULT_RETRY_DELAY_MS,
    val enableKeepAlive: Boolean = true,
    val keepAliveIntervalMs: Long = DEFAULT_KEEP_ALIVE_INTERVAL_MS,
    val enablePadding: Boolean = true,
    val paddingByte: Byte = DEFAULT_PADDING_BYTE,
    val useExtendedAddressing: Boolean = false,
    val extendedAddress: Byte = 0x00,
    val canBaudRate: Int = DEFAULT_CAN_BAUD_RATE,
    val enableCanFD: Boolean = false,
    val separationTimeMs: Int = DEFAULT_SEPARATION_TIME_MS,
    val blockSize: Int = DEFAULT_BLOCK_SIZE,
    val enableLogging: Boolean = true
) {
    init {
        require(responseTimeoutMs > 0) { "Response timeout must be positive" }
        require(extendedTimeoutMs >= responseTimeoutMs) { "Extended timeout must be >= response timeout" }
        require(maxRetries >= 0) { "Max retries must be non-negative" }
        require(retryDelayMs >= 0) { "Retry delay must be non-negative" }
        require(keepAliveIntervalMs > 0) { "Keep-alive interval must be positive" }
        require(canBaudRate > 0) { "CAN baud rate must be positive" }
        require(separationTimeMs >= 0) { "Separation time must be non-negative" }
        require(blockSize >= 0) { "Block size must be non-negative" }
    }
    
    /**
     * Creates a modified copy with the target ECU address set.
     */
    fun withTargetECU(address: Int): ProtocolConfig = copy(targetECUAddress = address)
    
    /**
     * Creates a modified copy with extended timeout.
     */
    fun withExtendedTimeout(timeoutMs: Long): ProtocolConfig = 
        copy(extendedTimeoutMs = timeoutMs)
    
    /**
     * Creates a modified copy with keep-alive settings.
     */
    fun withKeepAlive(enabled: Boolean, intervalMs: Long = keepAliveIntervalMs): ProtocolConfig =
        copy(enableKeepAlive = enabled, keepAliveIntervalMs = intervalMs)
    
    companion object {
        // Default values
        const val DEFAULT_RESPONSE_TIMEOUT_MS = 1000L
        const val DEFAULT_EXTENDED_TIMEOUT_MS = 5000L
        const val DEFAULT_MAX_RETRIES = 3
        const val DEFAULT_RETRY_DELAY_MS = 100L
        const val DEFAULT_KEEP_ALIVE_INTERVAL_MS = 2000L
        const val DEFAULT_PADDING_BYTE: Byte = 0xCC.toByte()
        const val DEFAULT_CAN_BAUD_RATE = 500_000
        const val DEFAULT_SEPARATION_TIME_MS = 0
        const val DEFAULT_BLOCK_SIZE = 0
        
        /**
         * Default configuration.
         */
        val DEFAULT = ProtocolConfig()
        
        /**
         * Configuration optimized for OBD-II operations.
         */
        fun forOBD(): ProtocolConfig = ProtocolConfig(
            autoDetect = true,
            responseTimeoutMs = 1000L,
            maxRetries = 3,
            enableKeepAlive = false, // OBD doesn't require keep-alive
            enablePadding = true
        )
        
        /**
         * Configuration optimized for UDS operations.
         */
        fun forUDS(): ProtocolConfig = ProtocolConfig(
            autoDetect = false,
            responseTimeoutMs = 1000L,
            extendedTimeoutMs = 10000L,
            maxRetries = 3,
            enableKeepAlive = true,
            keepAliveIntervalMs = 2000L,
            enablePadding = true
        )
        
        /**
         * Configuration optimized for KWP2000 operations.
         */
        fun forKWP2000(): ProtocolConfig = ProtocolConfig(
            autoDetect = false,
            responseTimeoutMs = 2000L,
            extendedTimeoutMs = 10000L,
            maxRetries = 3,
            enableKeepAlive = true,
            keepAliveIntervalMs = 2500L,
            enablePadding = false
        )
        
        /**
         * Configuration for ECU programming operations.
         */
        fun forProgramming(): ProtocolConfig = ProtocolConfig(
            autoDetect = false,
            responseTimeoutMs = 5000L,
            extendedTimeoutMs = 30000L,
            maxRetries = 1, // Programming should not retry blindly
            enableKeepAlive = true,
            keepAliveIntervalMs = 2000L,
            enablePadding = true
        )
        
        /**
         * Fast configuration with minimal timeouts (for testing).
         */
        fun forFastOperations(): ProtocolConfig = ProtocolConfig(
            autoDetect = false,
            responseTimeoutMs = 500L,
            extendedTimeoutMs = 2000L,
            maxRetries = 1,
            retryDelayMs = 50L,
            enableKeepAlive = false
        )
    }
    
    /**
     * Builder for creating ProtocolConfig instances.
     */
    class Builder {
        private var autoDetect: Boolean = true
        private var preferredProtocol: ProtocolType? = null
        private var targetECUAddress: Int? = null
        private var responseTimeoutMs: Long = DEFAULT_RESPONSE_TIMEOUT_MS
        private var extendedTimeoutMs: Long = DEFAULT_EXTENDED_TIMEOUT_MS
        private var maxRetries: Int = DEFAULT_MAX_RETRIES
        private var retryDelayMs: Long = DEFAULT_RETRY_DELAY_MS
        private var enableKeepAlive: Boolean = true
        private var keepAliveIntervalMs: Long = DEFAULT_KEEP_ALIVE_INTERVAL_MS
        private var enablePadding: Boolean = true
        private var paddingByte: Byte = DEFAULT_PADDING_BYTE
        private var useExtendedAddressing: Boolean = false
        private var extendedAddress: Byte = 0x00
        private var canBaudRate: Int = DEFAULT_CAN_BAUD_RATE
        private var enableCanFD: Boolean = false
        private var separationTimeMs: Int = DEFAULT_SEPARATION_TIME_MS
        private var blockSize: Int = DEFAULT_BLOCK_SIZE
        private var enableLogging: Boolean = true
        
        fun autoDetect(enabled: Boolean) = apply { autoDetect = enabled }
        fun preferredProtocol(protocol: ProtocolType?) = apply { preferredProtocol = protocol }
        fun targetECU(address: Int?) = apply { targetECUAddress = address }
        fun timeout(ms: Long) = apply { responseTimeoutMs = ms }
        fun extendedTimeout(ms: Long) = apply { extendedTimeoutMs = ms }
        fun retries(count: Int) = apply { maxRetries = count }
        fun retryDelay(ms: Long) = apply { retryDelayMs = ms }
        fun keepAlive(enabled: Boolean, intervalMs: Long = DEFAULT_KEEP_ALIVE_INTERVAL_MS) = apply {
            enableKeepAlive = enabled
            keepAliveIntervalMs = intervalMs
        }
        fun padding(enabled: Boolean, paddingByte: Byte = DEFAULT_PADDING_BYTE) = apply {
            enablePadding = enabled
            this.paddingByte = paddingByte
        }
        fun extendedAddressing(enabled: Boolean, address: Byte = 0x00) = apply {
            useExtendedAddressing = enabled
            extendedAddress = address
        }
        fun canBaudRate(rate: Int) = apply { canBaudRate = rate }
        fun canFD(enabled: Boolean) = apply { enableCanFD = enabled }
        fun isotpTiming(separationTimeMs: Int, blockSize: Int) = apply {
            this.separationTimeMs = separationTimeMs
            this.blockSize = blockSize
        }
        fun logging(enabled: Boolean) = apply { enableLogging = enabled }
        
        fun build(): ProtocolConfig = ProtocolConfig(
            autoDetect = autoDetect,
            preferredProtocol = preferredProtocol,
            targetECUAddress = targetECUAddress,
            responseTimeoutMs = responseTimeoutMs,
            extendedTimeoutMs = extendedTimeoutMs,
            maxRetries = maxRetries,
            retryDelayMs = retryDelayMs,
            enableKeepAlive = enableKeepAlive,
            keepAliveIntervalMs = keepAliveIntervalMs,
            enablePadding = enablePadding,
            paddingByte = paddingByte,
            useExtendedAddressing = useExtendedAddressing,
            extendedAddress = extendedAddress,
            canBaudRate = canBaudRate,
            enableCanFD = enableCanFD,
            separationTimeMs = separationTimeMs,
            blockSize = blockSize,
            enableLogging = enableLogging
        )
    }
}

// ============================================================================
// SECTION 7: PROTOCOL CAPABILITIES DATA CLASS
// ============================================================================

/**
 * Describes the capabilities and features of a protocol.
 *
 * ProtocolCapabilities provides information about what operations are
 * supported by a specific protocol, helping the application determine
 * available functionality.
 *
 * @property protocol Protocol type
 * @property supportsExtendedAddressing Whether extended addressing is supported
 * @property supportsMultiFrame Whether multi-frame messages are supported
 * @property maxDataLength Maximum single-frame data length
 * @property maxMultiFrameLength Maximum multi-frame message length
 * @property supportedServices Set of supported UDS service IDs
 * @property supportedBaudRates List of supported baud rates
 * @property supportsKeepAlive Whether tester present is supported
 * @property supportsSecurityAccess Whether security access is supported
 * @property canReadDTCs Whether DTC reading is supported
 * @property canClearDTCs Whether DTC clearing is supported
 * @property canReadLiveData Whether live data reading is supported
 * @property canPerformActuatorTests Whether actuator tests are supported
 * @property canProgramECU Whether ECU programming is supported
 * @property supportsCanFD Whether CAN FD is supported
 * @property requiresInitialization Whether protocol requires initialization sequence
 */
data class ProtocolCapabilities(
    val protocol: ProtocolType,
    val supportsExtendedAddressing: Boolean,
    val supportsMultiFrame: Boolean,
    val maxDataLength: Int,
    val maxMultiFrameLength: Int = 4095,
    val supportedServices: Set<Int>,
    val supportedBaudRates: List<Int>,
    val supportsKeepAlive: Boolean,
    val supportsSecurityAccess: Boolean,
    val canReadDTCs: Boolean,
    val canClearDTCs: Boolean,
    val canReadLiveData: Boolean,
    val canPerformActuatorTests: Boolean,
    val canProgramECU: Boolean,
    val supportsCanFD: Boolean = false,
    val requiresInitialization: Boolean = false
) {
    /**
     * Checks if a specific UDS service is supported.
     */
    fun supportsService(serviceId: Int): Boolean = serviceId in supportedServices
    
    /**
     * Checks if a baud rate is supported.
     */
    fun supportsBaudRate(baudRate: Int): Boolean = baudRate in supportedBaudRates
    
    /**
     * Checks if the data length can be handled.
     */
    fun canHandleDataLength(length: Int): Boolean = 
        length <= maxDataLength || (supportsMultiFrame && length <= maxMultiFrameLength)
    
    companion object {
        /**
         * Creates capabilities for OBD-II protocol.
         */
        fun forOBD(protocol: ProtocolType): ProtocolCapabilities = ProtocolCapabilities(
            protocol = protocol,
            supportsExtendedAddressing = false,
            supportsMultiFrame = protocol.isCAN,
            maxDataLength = if (protocol.isCAN) 7 else 5,
            supportedServices = setOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A),
            supportedBaudRates = when {
                protocol.isCAN -> listOf(250_000, 500_000)
                protocol.isKLine -> listOf(10400)
                protocol.isJ1850 -> listOf(10400, 41600)
                else -> emptyList()
            },
            supportsKeepAlive = false,
            supportsSecurityAccess = false,
            canReadDTCs = true,
            canClearDTCs = true,
            canReadLiveData = true,
            canPerformActuatorTests = false,
            canProgramECU = false,
            requiresInitialization = protocol.isKLine
        )
        
        /**
         * Creates capabilities for UDS protocol.
         */
        fun forUDS(): ProtocolCapabilities = ProtocolCapabilities(
            protocol = ProtocolType.ISO_15765_4_CAN_11BIT_500K,
            supportsExtendedAddressing = true,
            supportsMultiFrame = true,
            maxDataLength = 7,
            maxMultiFrameLength = 4095,
            supportedServices = setOf(
                0x10, 0x11, 0x14, 0x19, 0x22, 0x23, 0x24, 0x27,
                0x28, 0x2A, 0x2C, 0x2E, 0x2F, 0x31, 0x34, 0x35,
                0x36, 0x37, 0x38, 0x3D, 0x3E, 0x85, 0x86, 0x87
            ),
            supportedBaudRates = listOf(250_000, 500_000),
            supportsKeepAlive = true,
            supportsSecurityAccess = true,
            canReadDTCs = true,
            canClearDTCs = true,
            canReadLiveData = true,
            canPerformActuatorTests = true,
            canProgramECU = true
        )
        
        /**
         * Creates capabilities for KWP2000 protocol.
         */
        fun forKWP2000(): ProtocolCapabilities = ProtocolCapabilities(
            protocol = ProtocolType.ISO_14230_4_KWP_FAST,
            supportsExtendedAddressing = false,
            supportsMultiFrame = true,
            maxDataLength = 255,
            supportedServices = setOf(
                0x10, 0x11, 0x14, 0x17, 0x18, 0x1A, 0x21, 0x22,
                0x23, 0x27, 0x2C, 0x2E, 0x2F, 0x30, 0x31, 0x34,
                0x35, 0x36, 0x37, 0x38, 0x3B, 0x3E
            ),
            supportedBaudRates = listOf(10400),
            supportsKeepAlive = true,
            supportsSecurityAccess = true,
            canReadDTCs = true,
            canClearDTCs = true,
            canReadLiveData = true,
            canPerformActuatorTests = true,
            canProgramECU = true,
            requiresInitialization = true
        )
    }
}

// ============================================================================
// SECTION 8: SUPPORTING DATA CLASSES
// ============================================================================

/**
 * ECU information discovered during scan.
 *
 * @property address ECU address on the network
 * @property name ECU name or description
 * @property type ECU type classification
 * @property manufacturer ECU manufacturer
 * @property hardwareVersion Hardware version string
 * @property softwareVersion Software version string
 * @property partNumber ECU part number
 * @property serialNumber ECU serial number
 * @property supportedServices Set of supported UDS service IDs
 * @property additionalInfo Additional manufacturer-specific information
 */
data class ECUInfo(
    val address: Int,
    val name: String? = null,
    val type: ECUType? = null,
    val manufacturer: String? = null,
    val hardwareVersion: String? = null,
    val softwareVersion: String? = null,
    val partNumber: String? = null,
    val serialNumber: String? = null,
    val supportedServices: Set<Int> = emptySet(),
    val additionalInfo: Map<String, String> = emptyMap()
) {
    /**
     * Formatted address string (hexadecimal).
     */
    val addressHex: String
        get() = "0x${address.toString(16).uppercase().padStart(4, '0')}"
    
    /**
     * Display name (name if available, otherwise address).
     */
    val displayName: String
        get() = name ?: type?.displayName ?: addressHex
    
    /**
     * Checks if this ECU supports a specific service.
     */
    fun supportsService(serviceId: Int): Boolean = serviceId in supportedServices
    
    override fun toString(): String = "ECU($displayName at $addressHex)"
}

/**
 * ECU type classification.
 */
enum class ECUType(val displayName: String, val category: String) {
    ENGINE("Engine Control Module", "Powertrain"),
    TRANSMISSION("Transmission Control Module", "Powertrain"),
    ABS("Anti-lock Braking System", "Chassis"),
    AIRBAG("Airbag Control Module", "Safety"),
    INSTRUMENT_CLUSTER("Instrument Cluster", "Body"),
    BODY_CONTROL("Body Control Module", "Body"),
    CLIMATE_CONTROL("Climate Control", "Body"),
    POWER_STEERING("Power Steering", "Chassis"),
    GATEWAY("Central Gateway", "Network"),
    INFOTAINMENT("Infotainment System", "Comfort"),
    TELEMATICS("Telematics Control Unit", "Connectivity"),
    BATTERY_MANAGEMENT("Battery Management System", "Electric"),
    MOTOR_CONTROLLER("Motor Controller", "Electric"),
    CHARGING_SYSTEM("Charging System", "Electric"),
    ADAS("Advanced Driver Assistance", "Safety"),
    UNKNOWN("Unknown ECU", "Other");
    
    companion object {
        /**
         * Attempts to identify ECU type from its address.
         */
        fun fromAddress(address: Int): ECUType = when (address) {
            0x7E0, 0x7E8 -> ENGINE
            0x7E1, 0x7E9 -> TRANSMISSION
            0x760, 0x768 -> ABS
            0x772, 0x77A -> AIRBAG
            0x720, 0x728 -> INSTRUMENT_CLUSTER
            else -> UNKNOWN
        }
    }
}

/**
 * Detailed ECU identification data.
 *
 * Contains comprehensive identification information typically
 * obtained through UDS service 0x22 (ReadDataByIdentifier).
 */
data class ECUIdentification(
    val vin: String? = null,
    val ecuName: String? = null,
    val ecuManufacturer: String? = null,
    val ecuHardwareNumber: String? = null,
    val ecuHardwareVersion: String? = null,
    val ecuSoftwareNumber: String? = null,
    val ecuSoftwareVersion: String? = null,
    val bootSoftwareIdentification: String? = null,
    val applicationSoftwareIdentification: String? = null,
    val applicationDataIdentification: String? = null,
    val ecuManufacturingDate: String? = null,
    val ecuSerialNumber: String? = null,
    val systemName: String? = null,
    val repairShopCode: String? = null,
    val programmingDate: String? = null,
    val calibrationIdentifiers: List<String> = emptyList(),
    val additionalIdentifiers: Map<Int, ByteArray> = emptyMap()
) {
    /**
     * Checks if the ECU has been programmed (has programming date).
     */
    val hasBeenProgrammed: Boolean
        get() = programmingDate != null
    
    /**
     * Gets the primary software version (application or ECU software).
     */
    val primarySoftwareVersion: String?
        get() = applicationSoftwareIdentification ?: ecuSoftwareVersion
    
    companion object {
        /**
         * Standard UDS DIDs for ECU identification.
         */
        object DIDs {
            const val VIN = 0xF190
            const val ECU_MANUFACTURING_DATE = 0xF18B
            const val ECU_SERIAL_NUMBER = 0xF18C
            const val SYSTEM_NAME = 0xF197
            const val REPAIR_SHOP_CODE = 0xF198
            const val PROGRAMMING_DATE = 0xF199
            const val CALIBRATION_ID = 0xF19A
            const val ECU_HARDWARE_NUMBER = 0xF191
            const val ECU_HARDWARE_VERSION = 0xF193
            const val ECU_SOFTWARE_NUMBER = 0xF194
            const val ECU_SOFTWARE_VERSION = 0xF195
            const val BOOT_SOFTWARE_ID = 0xF183
            const val APPLICATION_SOFTWARE_ID = 0xF181
            const val APPLICATION_DATA_ID = 0xF182
        }
    }
}

/**
 * Routine execution result.
 *
 * @property routineId Routine identifier
 * @property status Routine status
 * @property resultData Result data from routine
 * @property statusInfo Additional status information
 */
data class RoutineResult(
    val routineId: Int,
    val status: RoutineStatus,
    val resultData: ByteArray = byteArrayOf(),
    val statusInfo: String? = null
) {
    /**
     * Routine execution status.
     */
    enum class RoutineStatus {
        /** Routine completed successfully */
        COMPLETED_SUCCESS,
        /** Routine completed with results data */
        COMPLETED_WITH_RESULTS,
        /** Routine is still in progress */
        IN_PROGRESS,
        /** Routine was stopped */
        STOPPED,
        /** Routine failed */
        FAILED,
        /** Routine status unknown */
        UNKNOWN
    }
    
    /**
     * Checks if the routine completed successfully.
     */
    val isSuccess: Boolean
        get() = status == RoutineStatus.COMPLETED_SUCCESS || 
                status == RoutineStatus.COMPLETED_WITH_RESULTS
    
    /**
     * Checks if the routine is still running.
     */
    val isRunning: Boolean
        get() = status == RoutineStatus.IN_PROGRESS
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as RoutineResult
        return routineId == other.routineId && 
               status == other.status && 
               resultData.contentEquals(other.resultData)
    }
    
    override fun hashCode(): Int {
        var result = routineId
        result = 31 * result + status.hashCode()
        result = 31 * result + resultData.contentHashCode()
        return result
    }
}

/**
 * I/O control parameter for InputOutputControlByIdentifier (0x2F).
 *
 * @property value Control parameter value
 */
enum class IOControlParameter(val value: Int) {
    /** Return control to ECU */
    RETURN_CONTROL_TO_ECU(0x00),
    /** Reset to default value */
    RESET_TO_DEFAULT(0x01),
    /** Freeze current state */
    FREEZE_CURRENT_STATE(0x02),
    /** Short term adjustment */
    SHORT_TERM_ADJUSTMENT(0x03);
    
    companion object {
        fun fromValue(value: Int): IOControlParameter? =
            entries.find { it.value == value }
    }
}

/**
 * I/O control operation result.
 *
 * @property identifier I/O identifier
 * @property success Whether the operation succeeded
 * @property controlState Current control state after operation
 * @property statusRecord Status record from ECU
 */
data class IOControlResult(
    val identifier: Int,
    val success: Boolean,
    val controlState: ByteArray = byteArrayOf(),
    val statusRecord: ByteArray = byteArrayOf()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as IOControlResult
        return identifier == other.identifier && 
               success == other.success && 
               controlState.contentEquals(other.controlState)
    }
    
    override fun hashCode(): Int {
        var result = identifier
        result = 31 * result + success.hashCode()
        result = 31 * result + controlState.contentHashCode()
        return result
    }
}

/**
 * ECU reset types for ECUReset service (0x11).
 *
 * @property value Reset type value
 * @property description Human-readable description
 * @property requiresReconnect Whether reconnection is required after reset
 */
enum class ECUResetType(
    val value: Int,
    val description: String,
    val requiresReconnect: Boolean = true
) {
    /** Hard reset - equivalent to power cycle */
    HARD_RESET(0x01, "Hard Reset", true),
    /** Key off/on reset - simulates ignition cycle */
    KEY_OFF_ON_RESET(0x02, "Key Off/On Reset", true),
    /** Soft reset - software restart */
    SOFT_RESET(0x03, "Soft Reset", false),
    /** Enable rapid power shutdown */
    ENABLE_RAPID_POWER_SHUTDOWN(0x04, "Enable Rapid Power Shutdown", false),
    /** Disable rapid power shutdown */
    DISABLE_RAPID_POWER_SHUTDOWN(0x05, "Disable Rapid Power Shutdown", false);
    
    companion object {
        fun fromValue(value: Int): ECUResetType? =
            entries.find { it.value == value }
    }
}

/**
 * DTC (Diagnostic Trouble Code) type categories.
 */
enum class DTCType(val description: String) {
    /** Stored/confirmed DTCs */
    STORED("Stored DTCs"),
    /** Pending DTCs (not yet confirmed) */
    PENDING("Pending DTCs"),
    /** Permanent DTCs (cannot be cleared by scan tool) */
    PERMANENT("Permanent DTCs"),
    /** All DTC types */
    ALL("All DTCs");
    
    /**
     * Converts to OBD-II service number.
     */
    fun toOBDService(): Int = when (this) {
        STORED -> 0x03
        PENDING -> 0x07
        PERMANENT -> 0x0A
        ALL -> 0x03
    }
    
    /**
     * Converts to UDS sub-function for ReadDTCInformation (0x19).
     */
    fun toUDSSubFunction(): Int = when (this) {
        STORED -> 0x02 // reportDTCByStatusMask
        PENDING -> 0x02
        PERMANENT -> 0x15 // reportDTCWithPermanentStatus
        ALL -> 0x02
    }
}

/**
 * Live data value from vehicle.
 *
 * @property pid Parameter ID
 * @property rawValue Raw bytes received from ECU
 * @property value Decoded numeric value
 * @property unit Value unit (e.g., "km/h", "°C")
 * @property name Human-readable parameter name
 * @property min Minimum expected value
 * @property max Maximum expected value
 * @property timestamp Timestamp when value was read
 */
data class LiveDataValue(
    val pid: Int,
    val rawValue: ByteArray,
    val value: Double,
    val unit: String,
    val name: String = "",
    val min: Double? = null,
    val max: Double? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Formatted value string with unit.
     */
    val formattedValue: String
        get() = "${"%.2f".format(value)} $unit"
    
    /**
     * Checks if value is within expected range.
     */
    val isInRange: Boolean
        get() = (min == null || value >= min) && (max == null || value <= max)
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as LiveDataValue
        return pid == other.pid && rawValue.contentEquals(other.rawValue)
    }
    
    override fun hashCode(): Int {
        var result = pid
        result = 31 * result + rawValue.contentHashCode()
        return result
    }
}


/**
 * Security algorithm interface for custom security access implementations.
 */
interface SecurityAlgorithm {
    /**
     * Calculates the key from the seed.
     *
     * @param seed Seed bytes from ECU
     * @param securityLevel Security access level
     * @return Calculated key bytes
     */
    suspend fun calculateKey(seed: ByteArray, securityLevel: Int): ByteArray
    
    /**
     * Algorithm name for logging.
     */
    val name: String
}