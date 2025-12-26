package com.obdreader.domain.error

/**
 * Classifies errors by severity and type.
 */
class ErrorClassifier {
    
    enum class ErrorSeverity {
        LOW,      // Non-critical, operation can continue
        MEDIUM,   // Important but recoverable
        HIGH,     // Critical, requires user action
        CRITICAL  // System failure, requires restart
    }
    
    enum class ErrorType {
        CONNECTIVITY,
        PROTOCOL,
        PARSING,
        HARDWARE,
        CONFIGURATION,
        UNKNOWN
    }
    
    /**
     * Classify error severity.
     */
    fun classifySeverity(error: OBDError): ErrorSeverity {
        return when (error) {
            is OBDError.NoDataError -> ErrorSeverity.LOW
            is OBDError.ParseError -> ErrorSeverity.LOW
            is OBDError.TimeoutError -> ErrorSeverity.MEDIUM
            is OBDError.BusBusyError -> ErrorSeverity.MEDIUM
            is OBDError.ConnectionLostError -> ErrorSeverity.HIGH
            is OBDError.ProtocolError -> ErrorSeverity.HIGH
            is OBDError.BluetoothDisabledError -> ErrorSeverity.CRITICAL
            is OBDError.InitializationError -> ErrorSeverity.CRITICAL
            else -> ErrorSeverity.MEDIUM
        }
    }
    
    /**
     * Classify error type.
     */
    fun classifyType(error: OBDError): ErrorType {
        return when (error) {
            is OBDError.BluetoothDisabledError,
            is OBDError.DeviceNotFoundError,
            is OBDError.ConnectionTimeoutError,
            is OBDError.ConnectionLostError -> ErrorType.CONNECTIVITY
            
            is OBDError.ProtocolError,
            is OBDError.BusInitError,
            is OBDError.CANError -> ErrorType.PROTOCOL
            
            is OBDError.ParseError -> ErrorType.PARSING
            
            is OBDError.BufferFullError,
            is OBDError.AdapterError -> ErrorType.HARDWARE
            
            is OBDError.InvalidRequestError -> ErrorType.CONFIGURATION
            
            else -> ErrorType.UNKNOWN
        }
    }
}
