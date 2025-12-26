package com.obdreader.domain.error

/**
 * Handles OBD errors and provides recovery strategies.
 */
class ErrorHandler {
    
    /**
     * Handle an OBD error and return appropriate recovery action.
     */
    fun handleError(error: OBDError): RecoveryAction {
        return error.suggestedAction
    }
    
    /**
     * Check if error is recoverable.
     */
    fun isRecoverable(error: OBDError): Boolean {
        return error.isRecoverable
    }
    
    /**
     * Get user-friendly error message.
     */
    fun getUserMessage(error: OBDError): String {
        return when (error) {
            is OBDError.BluetoothDisabledError -> "Please enable Bluetooth to connect to the OBD adapter."
            is OBDError.DeviceNotFoundError -> "OBD adapter not found. Make sure it's powered on and in range."
            is OBDError.ConnectionTimeoutError -> "Connection timed out. Check adapter and try again."
            is OBDError.ProtocolError -> "Unable to communicate with vehicle. Check connection."
            is OBDError.NoDataError -> "No data available for this parameter."
            is OBDError.TimeoutError -> "Command timed out. Vehicle may not support this feature."
            else -> error.message ?: "Unknown error occurred"
        }
    }
}
