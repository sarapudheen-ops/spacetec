package com.spacetec.protocol.error

import com.spacetec.core.common.exceptions.ProtocolException
import com.spacetec.protocol.core.base.NegativeResponseCodes
import kotlinx.coroutines.CancellationException

/**
 * Comprehensive error handling system for automotive diagnostic protocols.
 * This class provides centralized error handling, logging, and recovery strategies.
 */
class ProtocolErrorHandler {
    
    /**
     * Represents different types of protocol errors
     */
    sealed class ProtocolError {
        abstract val message: String
        abstract val cause: Throwable?
        
        data class CommunicationError(override val message: String, override val cause: Throwable? = null) : ProtocolError()
        data class TimeoutError(override val message: String, override val cause: Throwable? = null) : ProtocolError()
        data class NegativeResponseError(
            override val message: String, 
            val nrc: Int, 
            val serviceId: Int,
            override val cause: Throwable? = null
        ) : ProtocolError()
        data class ValidationError(override val message: String, override val cause: Throwable? = null) : ProtocolError()
        data class SessionError(override val message: String, override val cause: Throwable? = null) : ProtocolError()
        data class SecurityError(override val message: String, override val cause: Throwable? = null) : ProtocolError()
        data class NotImplementedError(override val message: String, override val cause: Throwable? = null) : ProtocolError()
    }
    
    /**
     * Error recovery strategies
     */
    enum class RecoveryStrategy {
        RETRY,          // Retry the operation
        RECONNECT,      // Reconnect to the device
        RESET,          // Reset the protocol
        ABORT,          // Abort the operation
        FALLBACK        // Use a fallback method
    }
    
    /**
     * Handles an error and determines the appropriate recovery strategy
     */
    fun handleProtocolError(error: Throwable, context: String = ""): RecoveryStrategy {
        return when (error) {
            is CancellationException -> {
                // Operation was cancelled, no recovery needed
                RecoveryStrategy.ABORT
            }
            is ProtocolException -> {
                // Check if it's a negative response
                if (error.message?.contains("Negative response") == true) {
                    handleNegativeResponseError(error)
                } else {
                    // General protocol error
                    RecoveryStrategy.RESET
                }
            }
            is java.util.concurrent.TimeoutException -> {
                // Timeout error
                RecoveryStrategy.RETRY
            }
            is java.net.SocketException, 
            is java.io.IOException -> {
                // Communication error
                RecoveryStrategy.RECONNECT
            }
            else -> {
                // Unknown error
                RecoveryStrategy.ABORT
            }
        }
    }
    
    /**
     * Handles negative response errors specifically
     */
    private fun handleNegativeResponseError(exception: ProtocolException): RecoveryStrategy {
        val message = exception.message ?: ""
        
        // Try to extract NRC from the message
        val nrc = extractNRCFromMessage(message)
        
        return when (nrc) {
            NegativeResponseCodes.BUSY_REPEAT_REQUEST,
            NegativeResponseCodes.REQUEST_CORRECTLY_RECEIVED_PENDING -> {
                // Temporary condition, can retry
                RecoveryStrategy.RETRY
            }
            NegativeResponseCodes.CONDITIONS_NOT_CORRECT,
            NegativeResponseCodes.REQUEST_SEQUENCE_ERROR -> {
                // Conditions not right, may need to reset or change state
                RecoveryStrategy.RESET
            }
            NegativeResponseCodes.SECURITY_ACCESS_DENIED,
            NegativeResponseCodes.INVALID_KEY,
            NegativeResponseCodes.EXCEEDED_ATTEMPTS -> {
                // Security related, need different approach
                RecoveryStrategy.ABORT
            }
            NegativeResponseCodes.VOLTAGE_TOO_HIGH,
            NegativeResponseCodes.VOLTAGE_TOO_LOW -> {
                // Vehicle conditions not safe, abort
                RecoveryStrategy.ABORT
            }
            NegativeResponseCodes.SERVICE_NOT_SUPPORTED,
            NegativeResponseCodes.SUB_FUNCTION_NOT_SUPPORTED -> {
                // Feature not supported, try fallback
                RecoveryStrategy.FALLBACK
            }
            else -> {
                // Other NRCs - default to reset
                RecoveryStrategy.RESET
            }
        }
    }
    
    /**
     * Extracts NRC (Negative Response Code) from error message
     */
    private fun extractNRCFromMessage(message: String): Int {
        // Look for patterns like "0x7F", "NRC 0x22", etc.
        val nrcPattern = Regex("0x([0-9A-Fa-f]{2})")
        val match = nrcPattern.find(message)
        
        return if (match != null) {
            try {
                match.groupValues[1].toInt(16)
            } catch (e: NumberFormatException) {
                -1
            }
        } else {
            -1
        }
    }
    
    /**
     * Logs protocol errors with appropriate level based on severity
     */
    fun logProtocolError(error: ProtocolError, context: String = "") {
        val logMessage = buildString {
            append("Protocol Error in $context: ${error.message}")
            error.cause?.let { append(" (Caused by: ${it.message})") }
        }
        
        // In a real implementation, this would use a proper logging framework
        // For now, we'll just print to console
        System.err.println(logMessage)
    }
    
    /**
     * Creates a standardized error response based on the error type
     */
    fun createErrorResponse(error: ProtocolError): String {
        return when (error) {
            is ProtocolError.CommunicationError -> "Communication Error: ${error.message}"
            is ProtocolError.TimeoutError -> "Timeout Error: ${error.message}"
            is ProtocolError.NegativeResponseError -> "Negative Response (0x${error.nrc.toString(16)}): ${error.message}"
            is ProtocolError.ValidationError -> "Validation Error: ${error.message}"
            is ProtocolError.SessionError -> "Session Error: ${error.message}"
            is ProtocolError.SecurityError -> "Security Error: ${error.message}"
            is ProtocolError.NotImplementedError -> "Not Implemented: ${error.message}"
        }
    }
    
    /**
     * Determines if an error is recoverable
     */
    fun isRecoverable(error: ProtocolError): Boolean {
        return when (error) {
            is ProtocolError.CommunicationError -> true
            is ProtocolError.TimeoutError -> true
            is ProtocolError.NegativeResponseError -> {
                // Some NRCs are recoverable, others are not
                when (error.nrc) {
                    NegativeResponseCodes.BUSY_REPEAT_REQUEST,
                    NegativeResponseCodes.REQUEST_CORRECTLY_RECEIVED_PENDING -> true
                    else -> false
                }
            }
            is ProtocolError.ValidationError -> false
            is ProtocolError.SessionError -> true
            is ProtocolError.SecurityError -> false
            is ProtocolError.NotImplementedError -> false
        }
    }
    
    /**
     * Formats error for display to user
     */
    fun formatUserError(error: ProtocolError): String {
        return when (error) {
            is ProtocolError.CommunicationError -> "Communication with vehicle failed. Please check connection."
            is ProtocolError.TimeoutError -> "Operation timed out. Please try again."
            is ProtocolError.NegativeResponseError -> {
                when (error.nrc) {
                    NegativeResponseCodes.SERVICE_NOT_SUPPORTED -> "Service not supported by this vehicle/ECU."
                    NegativeResponseCodes.CONDITIONS_NOT_CORRECT -> "Vehicle conditions not correct for this operation."
                    NegativeResponseCodes.SECURITY_ACCESS_DENIED -> "Security access denied. Operation requires higher authorization."
                    NegativeResponseCodes.VOLTAGE_TOO_LOW -> "Battery voltage too low. Charging system may be faulty."
                    NegativeResponseCodes.VOLTAGE_TOO_HIGH -> "Battery voltage too high. Check charging system."
                    else -> "ECU returned error: ${NegativeResponseCodes.getDescription(error.nrc)}"
                }
            }
            is ProtocolError.ValidationError -> "Invalid data format. Please check input."
            is ProtocolError.SessionError -> "Session error occurred. Please reconnect."
            is ProtocolError.SecurityError -> "Security error. Operation not permitted."
            is ProtocolError.NotImplementedError -> "Feature not implemented for this vehicle/protocol."
        }
    }
    
    /**
     * Provides troubleshooting steps for common errors
     */
    fun getTroubleshootingSteps(error: ProtocolError): List<String> {
        return when (error) {
            is ProtocolError.CommunicationError -> listOf(
                "Check Bluetooth/WiFi connection",
                "Verify OBD-II adapter is properly connected",
                "Ensure vehicle ignition is ON (not START)",
                "Try reconnecting the adapter"
            )
            is ProtocolError.TimeoutError -> listOf(
                "Check if vehicle ECU is responding",
                "Verify proper connection to OBD-II port",
                "Ensure vehicle is awake and not in sleep mode",
                "Try reducing the baud rate if applicable"
            )
            is ProtocolError.NegativeResponseError -> {
                when (error.nrc) {
                    NegativeResponseCodes.SERVICE_NOT_SUPPORTED -> listOf(
                        "This service is not supported by the vehicle/ECU",
                        "Try a different diagnostic service",
                        "Check vehicle year/make/model compatibility"
                    )
                    NegativeResponseCodes.CONDITIONS_NOT_CORRECT -> listOf(
                        "Ensure engine is off for programming operations",
                        "Check transmission is in PARK or NEUTRAL",
                        "Verify brake is applied if required",
                        "Wait for vehicle to reach proper conditions"
                    )
                    NegativeResponseCodes.SECURITY_ACCESS_DENIED -> listOf(
                        "Operation requires security access",
                        "Perform security access sequence first",
                        "Check if special authorization is required"
                    )
                    else -> listOf(
                        "ECU returned error: ${NegativeResponseCodes.getDescription(error.nrc)}",
                        "Check vehicle service manual for this error code",
                        "Try basic operations first to verify connection"
                    )
                }
            }
            else -> emptyList()
        }
    }
}

/**
 * Extension function to convert exceptions to protocol errors
 */
fun Throwable.toProtocolError(): ProtocolErrorHandler.ProtocolError {
    return when (this) {
        is ProtocolException -> {
            if (this.message?.contains("Negative response") == true) {
                // Try to extract NRC from message
                val nrc = this.message?.let { extractNRCFromMessage(it) } ?: -1
                val serviceId = this.message?.let { extractServiceIdFromMessage(it) } ?: -1
                ProtocolErrorHandler.ProtocolError.NegativeResponseError(
                    this.message ?: "Negative response",
                    nrc,
                    serviceId,
                    this
                )
            } else {
                ProtocolErrorHandler.ProtocolError.ValidationError(
                    this.message ?: "Protocol validation error",
                    this
                )
            }
        }
        is java.util.concurrent.TimeoutException -> {
            ProtocolErrorHandler.ProtocolError.TimeoutError(
                this.message ?: "Operation timed out",
                this
            )
        }
        is java.net.SocketException, 
        is java.io.IOException -> {
            ProtocolErrorHandler.ProtocolError.CommunicationError(
                this.message ?: "Communication error",
                this
            )
        }
        else -> {
            ProtocolErrorHandler.ProtocolError.ValidationError(
                this.message ?: "Unknown error",
                this
            )
        }
    }
}

/**
 * Helper function to extract service ID from message
 */
private fun extractServiceIdFromMessage(message: String): Int {
    // Look for service ID patterns in the message
    val servicePattern = Regex("service (0x[0-9A-Fa-f]{2})|service ([0-9]+)")
    val match = servicePattern.find(message)
    
    return if (match != null) {
        val serviceStr = match.groupValues[1].ifEmpty { match.groupValues[2] }
        try {
            if (serviceStr.startsWith("0x")) {
                serviceStr.substring(2).toInt(16)
            } else {
                serviceStr.toInt()
            }
        } catch (e: NumberFormatException) {
            -1
        }
    } else {
        -1
    }
}