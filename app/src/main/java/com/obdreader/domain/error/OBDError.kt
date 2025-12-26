package com.obdreader.domain.error

/**
 * Sealed class hierarchy for all OBD-related errors.
 * Provides structured error handling with specific error types.
 */
sealed class OBDError : Exception() {
    abstract val errorCode: String
    abstract val isRecoverable: Boolean
    abstract val suggestedAction: RecoveryAction
    
    // ========== Bluetooth Errors ==========
    
    data class BluetoothDisabledError(
        override val message: String = "Bluetooth is disabled"
    ) : OBDError() {
        override val errorCode = "E001"
        override val isRecoverable = true
        override val suggestedAction = RecoveryAction.PROMPT_ENABLE_BLUETOOTH
    }
    
    data class DeviceNotFoundError(
        val deviceName: String? = null,
        override val message: String = "OBD adapter not found${deviceName?.let { ": $it" } ?: ""}"
    ) : OBDError() {
        override val errorCode = "E003"
        override val isRecoverable = true
        override val suggestedAction = RecoveryAction.RETRY_SCAN
    }
    
    data class ConnectionTimeoutError(
        val deviceAddress: String? = null,
        override val message: String = "Connection timed out"
    ) : OBDError() {
        override val errorCode = "E004"
        override val isRecoverable = true
        override val suggestedAction = RecoveryAction.RETRY_CONNECTION
    }
    
    data class ConnectionLostError(
        override val message: String = "Connection to adapter lost"
    ) : OBDError() {
        override val errorCode = "E005"
        override val isRecoverable = true
        override val suggestedAction = RecoveryAction.AUTO_RECONNECT
    }
    
    data class ConnectionClosedError(
        override val message: String = "Connection closed"
    ) : OBDError() {
        override val errorCode = "E005a"
        override val isRecoverable = false
        override val suggestedAction = RecoveryAction.NONE
    }
    
    // ========== Protocol Errors ==========
    
    data class ProtocolError(
        val command: String,
        override val message: String
    ) : OBDError() {
        override val errorCode = "E006"
        override val isRecoverable = true
        override val suggestedAction = RecoveryAction.TRY_PROTOCOLS
    }
    
    data class NoDataError(
        val command: String,
        override val message: String = "No data received for command: $command"
    ) : OBDError() {
        override val errorCode = "E007"
        override val isRecoverable = false
        override val suggestedAction = RecoveryAction.SKIP_PID
    }
    
    data class BusBusyError(
        val command: String,
        override val message: String = "Vehicle bus is busy"
    ) : OBDError() {
        override val errorCode = "E008"
        override val isRecoverable = true
        override val suggestedAction = RecoveryAction.RETRY_WITH_DELAY
    }
    
    data class BufferFullError(
        val command: String,
        override val message: String = "Adapter buffer is full"
    ) : OBDError() {
        override val errorCode = "E009"
        override val isRecoverable = true
        override val suggestedAction = RecoveryAction.RESET_ADAPTER
    }
    
    data class CANError(
        val command: String,
        override val message: String = "CAN bus error"
    ) : OBDError() {
        override val errorCode = "E012"
        override val isRecoverable = true
        override val suggestedAction = RecoveryAction.INCREASE_TIMEOUT
    }
    
    // ========== Command Errors ==========
    
    data class TimeoutError(
        val command: String,
        override val message: String = "Command timed out: $command"
    ) : OBDError() {
        override val errorCode = "E020"
        override val isRecoverable = true
        override val suggestedAction = RecoveryAction.RETRY_COMMAND
    }
    
    data class CommandStoppedError(
        val command: String,
        override val message: String = "Command was stopped: $command"
    ) : OBDError() {
        override val errorCode = "E021"
        override val isRecoverable = true
        override val suggestedAction = RecoveryAction.RETRY_COMMAND
    }
    
    data class UnknownCommandError(
        val command: String,
        override val message: String = "Unknown command: $command"
    ) : OBDError() {
        override val errorCode = "E022"
        override val isRecoverable = false
        override val suggestedAction = RecoveryAction.NONE
    }
    
    data class AdapterError(
        val command: String,
        val response: String,
        override val message: String = "Adapter error: $response"
    ) : OBDError() {
        override val errorCode = "E023"
        override val isRecoverable = true
        override val suggestedAction = RecoveryAction.RESET_ADAPTER
    }
    
    data class BusInitError(
        val command: String,
        override val message: String = "Failed to initialize vehicle bus"
    ) : OBDError() {
        override val errorCode = "E024"
        override val isRecoverable = true
        override val suggestedAction = RecoveryAction.TRY_PROTOCOLS
    }
    
    // ========== Parse Errors ==========
    
    data class ParseError(
        val rawData: String,
        override val message: String = "Failed to parse response: $rawData"
    ) : OBDError() {
        override val errorCode = "E030"
        override val isRecoverable = false
        override val suggestedAction = RecoveryAction.NONE
    }
    
    // ========== Initialization Errors ==========
    
    data class InitializationError(
        override val message: String
    ) : OBDError() {
        override val errorCode = "E040"
        override val isRecoverable = true
        override val suggestedAction = RecoveryAction.RESET_ADAPTER
    }
    
    // ========== DTC Errors ==========
    
    data class ClearDTCError(
        val response: String,
        override val message: String = "Failed to clear DTCs: $response"
    ) : OBDError() {
        override val errorCode = "E050"
        override val isRecoverable = true
        override val suggestedAction = RecoveryAction.RETRY_COMMAND
    }
    
    // ========== Other Errors ==========
    
    data class InvalidRequestError(
        override val message: String
    ) : OBDError() {
        override val errorCode = "E070"
        override val isRecoverable = false
        override val suggestedAction = RecoveryAction.NONE
    }
    
    data class CommunicationError(
        val command: String,
        override val message: String
    ) : OBDError() {
        override val errorCode = "E080"
        override val isRecoverable = true
        override val suggestedAction = RecoveryAction.RETRY_COMMAND
    }
}
