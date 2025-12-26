/**
 * Protocol.kt
 *
 * Domain interface for diagnostic protocols.
 * Defines the contract that all protocol implementations must follow.
 */

package com.spacetec.core.domain.protocol

import com.spacetec.core.domain.scanner.ScannerConnection

/**
 * Interface for diagnostic protocol implementations.
 *
 * This domain interface defines the core operations that all diagnostic protocols
 * (OBD-II, UDS, KWP2000, etc.) must support. It provides a consistent API
 * for communication with vehicle ECUs regardless of the underlying protocol.
 */
interface Protocol {

    /**
     * The type of protocol this implementation handles.
     */
    val protocolType: ProtocolType

    /**
     * Human-readable protocol name for display.
     */
    val protocolName: String

    /**
     * Protocol version or variant identifier.
     */
    val protocolVersion: String

    /**
     * Current protocol configuration.
     */
    val config: ProtocolConfig

    /**
     * Whether the protocol is initialized and ready for communication.
     */
    val isInitialized: Boolean

    /**
     * Whether a diagnostic session is currently active.
     */
    val isSessionActive: Boolean

    /**
     * Initializes the protocol with the given scanner connection.
     *
     * @param connection Active scanner connection to use
     * @param config Protocol configuration
     */
    suspend fun initialize(
        connection: ScannerConnection,
        config: ProtocolConfig = ProtocolConfig.DEFAULT
    )

    /**
     * Shuts down the protocol and releases resources.
     */
    suspend fun shutdown()

    /**
     * Resets the protocol to initial state.
     */
    suspend fun reset()

    /**
     * Sends a diagnostic message and returns the response.
     *
     * @param request The diagnostic message to send
     * @return The response message from the ECU
     */
    suspend fun sendMessage(request: DiagnosticMessage): DiagnosticMessage

    /**
     * Sends a diagnostic message with custom timeout.
     *
     * @param request The diagnostic message to send
     * @param timeoutMs Custom timeout in milliseconds
     * @return The response message from the ECU
     */
    suspend fun sendMessage(
        request: DiagnosticMessage,
        timeoutMs: Long
    ): DiagnosticMessage

    /**
     * Sends raw bytes and returns raw response.
     *
     * @param data Raw bytes to send
     * @return Raw response bytes
     */
    suspend fun sendRaw(data: ByteArray): ByteArray

    /**
     * Starts a diagnostic session with the vehicle.
     *
     * @param sessionType Type of session to start
     * @param ecuAddress Specific ECU address (null for broadcast)
     */
    suspend fun startSession(
        sessionType: SessionType,
        ecuAddress: Int? = null
    )

    /**
     * Ends the current diagnostic session.
     */
    suspend fun endSession()

    /**
     * Sends a keep-alive message to maintain the session.
     */
    suspend fun sendKeepAlive()
}

/**
 * Protocol state enumeration.
 */
sealed class ProtocolState {
    object Uninitialized : ProtocolState()
    object Ready : ProtocolState()
    data class SessionActive(val sessionType: SessionType) : ProtocolState()
    data class Error(val error: Throwable) : ProtocolState()
    object Shutdown : ProtocolState()
}

/**
 * Protocol configuration.
 */
data class ProtocolConfig(
    val responseTimeoutMs: Long = 1000L,
    val maxRetries: Int = 3,
    val retryDelayMs: Long = 100L
) {
    companion object {
        val DEFAULT = ProtocolConfig()
    }
}

/**
 * Session types for diagnostic operations.
 */
enum class SessionType(val id: Int, val description: String) {
    DEFAULT(0x01, "Default Session"),
    PROGRAMMING(0x02, "Programming Session"),
    EXTENDED(0x03, "Extended Diagnostic Session")
}

/**
 * Basic diagnostic message structure.
 */
interface DiagnosticMessage {
    val serviceId: Int
    val data: ByteArray

    fun toByteArray(): ByteArray
}