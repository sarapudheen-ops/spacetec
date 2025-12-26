package com.spacetec.obd.core.common.result

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/**
 * Base sealed class for all errors in the SpaceTec application.
 * 
 * This provides a structured way to handle different types of errors
 * throughout the application with specific error codes and messages.
 */
@Serializable
sealed class SpaceTecError : Parcelable {
    
    /**
     * Human-readable error message.
     */
    abstract val message: String
    
    /**
     * Error code for logging and debugging.
     */
    abstract val code: String
    
    /**
     * Optional underlying cause of the error.
     */
    @kotlinx.serialization.Transient
    open val cause: Throwable? = null
    
    // ========================================================================
    // CONNECTION ERRORS
    // ========================================================================
    
    /**
     * Errors related to scanner/device connection.
     */
    @Serializable
    sealed class ConnectionError : SpaceTecError() {
        
        @Parcelize
        @Serializable
        data class DeviceNotFound(
            override val message: String = "Scanner device not found",
            val deviceId: String? = null
        ) : ConnectionError() {
            override val code: String = "CONN_001"
        }
        
        @Parcelize
        @Serializable
        data class ConnectionFailed(
            override val message: String = "Failed to connect to scanner",
            val reason: String? = null
        ) : ConnectionError() {
            override val code: String = "CONN_002"
        }
        
        @Parcelize
        @Serializable
        data class ConnectionLost(
            override val message: String = "Connection to scanner lost",
            val wasUnexpected: Boolean = true
        ) : ConnectionError() {
            override val code: String = "CONN_003"
        }
        
        @Parcelize
        @Serializable
        data class Timeout(
            override val message: String = "Connection timed out",
            val timeoutMs: Long = 0
        ) : ConnectionError() {
            override val code: String = "CONN_004"
        }
        
        @Parcelize
        @Serializable
        data class PermissionDenied(
            override val message: String = "Permission denied for device access",
            val permission: String? = null
        ) : ConnectionError() {
            override val code: String = "CONN_005"
        }
        
        @Parcelize
        @Serializable
        data class BluetoothDisabled(
            override val message: String = "Bluetooth is disabled"
        ) : ConnectionError() {
            override val code: String = "CONN_006"
        }
        
        @Parcelize
        @Serializable
        data class WifiDisabled(
            override val message: String = "WiFi is disabled"
        ) : ConnectionError() {
            override val code: String = "CONN_007"
        }
        
        @Parcelize
        @Serializable
        data class UsbNotSupported(
            override val message: String = "USB OTG not supported on this device"
        ) : ConnectionError() {
            override val code: String = "CONN_008"
        }
    }
    
    // ========================================================================
    // PROTOCOL ERRORS
    // ========================================================================
    
    /**
     * Errors related to OBD-II/UDS protocol communication.
     */
    @Serializable
    sealed class ProtocolError : SpaceTecError() {
        
        @Parcelize
        @Serializable
        data class ProtocolNotSupported(
            override val message: String = "Protocol not supported by vehicle",
            val protocol: String? = null
        ) : ProtocolError() {
            override val code: String = "PROTO_001"
        }
        
        @Parcelize
        @Serializable
        data class InvalidResponse(
            override val message: String = "Invalid response from ECU",
            val rawData: String? = null
        ) : ProtocolError() {
            override val code: String = "PROTO_002"
        }
        
        @Parcelize
        @Serializable
        data class NoResponse(
            override val message: String = "No response from ECU",
            val ecuAddress: String? = null
        ) : ProtocolError() {
            override val code: String = "PROTO_003"
        }
        
        @Parcelize
        @Serializable
        data class NegativeResponse(
            override val message: String = "Negative response from ECU",
            val nrc: Int = 0,
            val nrcDescription: String? = null
        ) : ProtocolError() {
            override val code: String = "PROTO_004"
        }
        
        @Parcelize
        @Serializable
        data class SecurityAccessDenied(
            override val message: String = "Security access denied",
            val securityLevel: Int = 0
        ) : ProtocolError() {
            override val code: String = "PROTO_005"
        }
        
        @Parcelize
        @Serializable
        data class ServiceNotSupported(
            override val message: String = "Service not supported by ECU",
            val serviceId: Int = 0
        ) : ProtocolError() {
            override val code: String = "PROTO_006"
        }
        
        @Parcelize
        @Serializable
        data class SubFunctionNotSupported(
            override val message: String = "Sub-function not supported",
            val serviceId: Int = 0,
            val subFunction: Int = 0
        ) : ProtocolError() {
            override val code: String = "PROTO_007"
        }
        
        @Parcelize
        @Serializable
        data class BusError(
            override val message: String = "CAN bus error detected",
            val errorType: String? = null
        ) : ProtocolError() {
            override val code: String = "PROTO_008"
        }
    }
    
    // ========================================================================
    // DTC ERRORS
    // ========================================================================
    
    /**
     * Errors related to DTC operations.
     */
    @Serializable
    sealed class DtcError : SpaceTecError() {
        
        @Parcelize
        @Serializable
        data class ReadFailed(
            override val message: String = "Failed to read DTCs",
            val ecuAddress: String? = null
        ) : DtcError() {
            override val code: String = "DTC_001"
        }
        
        @Parcelize
        @Serializable
        data class ClearFailed(
            override val message: String = "Failed to clear DTCs",
            val reason: String? = null
        ) : DtcError() {
            override val code: String = "DTC_002"
        }
        
        @Parcelize
        @Serializable
        data class ClearNotAllowed(
            override val message: String = "DTC clearing not allowed",
            val reason: String? = null
        ) : DtcError() {
            override val code: String = "DTC_003"
        }
        
        @Parcelize
        @Serializable
        data class InvalidDtcFormat(
            override val message: String = "Invalid DTC format",
            val rawDtc: String? = null
        ) : DtcError() {
            override val code: String = "DTC_004"
        }
        
        @Parcelize
        @Serializable
        data class FreezeFrameNotAvailable(
            override val message: String = "Freeze frame data not available",
            val dtcCode: String? = null
        ) : DtcError() {
            override val code: String = "DTC_005"
        }
        
        @Parcelize
        @Serializable
        data class DtcNotFound(
            override val message: String = "DTC not found in database",
            val dtcCode: String? = null
        ) : DtcError() {
            override val code: String = "DTC_006"
        }
    }
    
    // ========================================================================
    // DATABASE ERRORS
    // ========================================================================
    
    /**
     * Errors related to local database operations.
     */
    @Serializable
    sealed class DatabaseError : SpaceTecError() {
        
        @Parcelize
        @Serializable
        data class ReadError(
            override val message: String = "Failed to read from database"
        ) : DatabaseError() {
            override val code: String = "DB_001"
        }
        
        @Parcelize
        @Serializable
        data class WriteError(
            override val message: String = "Failed to write to database"
        ) : DatabaseError() {
            override val code: String = "DB_002"
        }
        
        @Parcelize
        @Serializable
        data class MigrationError(
            override val message: String = "Database migration failed",
            val fromVersion: Int = 0,
            val toVersion: Int = 0
        ) : DatabaseError() {
            override val code: String = "DB_003"
        }
        
        @Parcelize
        @Serializable
        data class CorruptedDatabase(
            override val message: String = "Database is corrupted"
        ) : DatabaseError() {
            override val code: String = "DB_004"
        }
    }
    
    // ========================================================================
    // NETWORK ERRORS
    // ========================================================================
    
    /**
     * Errors related to network/API operations.
     */
    @Serializable
    sealed class NetworkError : SpaceTecError() {
        
        @Parcelize
        @Serializable
        data class NoInternet(
            override val message: String = "No internet connection"
        ) : NetworkError() {
            override val code: String = "NET_001"
        }
        
        @Parcelize
        @Serializable
        data class ServerError(
            override val message: String = "Server error",
            val httpCode: Int = 0
        ) : NetworkError() {
            override val code: String = "NET_002"
        }
        
        @Parcelize
        @Serializable
        data class ApiError(
            override val message: String = "API error",
            val apiCode: String? = null
        ) : NetworkError() {
            override val code: String = "NET_003"
        }
        
        @Parcelize
        @Serializable
        data class Timeout(
            override val message: String = "Network request timed out"
        ) : NetworkError() {
            override val code: String = "NET_004"
        }
    }
    
    // ========================================================================
    // VEHICLE ERRORS
    // ========================================================================
    
    /**
     * Errors related to vehicle operations.
     */
    @Serializable
    sealed class VehicleError : SpaceTecError() {
        
        @Parcelize
        @Serializable
        data class VehicleNotSupported(
            override val message: String = "Vehicle not supported",
            val make: String? = null,
            val model: String? = null,
            val year: Int? = null
        ) : VehicleError() {
            override val code: String = "VEH_001"
        }
        
        @Parcelize
        @Serializable
        data class InvalidVin(
            override val message: String = "Invalid VIN",
            val vin: String? = null
        ) : VehicleError() {
            override val code: String = "VEH_002"
        }
        
        @Parcelize
        @Serializable
        data class EcuNotFound(
            override val message: String = "ECU not found",
            val ecuName: String? = null
        ) : VehicleError() {
            override val code: String = "VEH_003"
        }
        
        @Parcelize
        @Serializable
        data class IgnitionOff(
            override val message: String = "Vehicle ignition is off"
        ) : VehicleError() {
            override val code: String = "VEH_004"
        }
        
        @Parcelize
        @Serializable
        data class EngineRunning(
            override val message: String = "Engine must be off for this operation"
        ) : VehicleError() {
            override val code: String = "VEH_005"
        }
    }
    
    // ========================================================================
    // VALIDATION ERRORS
    // ========================================================================
    
    /**
     * Errors related to input validation.
     */
    @Serializable
    sealed class ValidationError : SpaceTecError() {
        
        @Parcelize
        @Serializable
        data class InvalidInput(
            override val message: String = "Invalid input",
            val field: String? = null,
            val reason: String? = null
        ) : ValidationError() {
            override val code: String = "VAL_001"
        }
        
        @Parcelize
        @Serializable
        data class MissingRequired(
            override val message: String = "Required field is missing",
            val field: String? = null
        ) : ValidationError() {
            override val code: String = "VAL_002"
        }
        
        @Parcelize
        @Serializable
        data class OutOfRange(
            override val message: String = "Value out of range",
            val field: String? = null,
            val min: String? = null,
            val max: String? = null
        ) : ValidationError() {
            override val code: String = "VAL_003"
        }
    }
    
    // ========================================================================
    // GENERIC ERRORS
    // ========================================================================
    
    /**
     * Generic/unknown errors.
     */
    @Parcelize
    @Serializable
    data class Unknown(
        override val message: String = "An unknown error occurred",
        val originalError: String? = null
    ) : SpaceTecError() {
        override val code: String = "UNKNOWN"
    }
    
    companion object {
        /**
         * Creates an appropriate SpaceTecError from a Throwable.
         */
        fun fromThrowable(throwable: Throwable): SpaceTecError {
            return when (throwable) {
                is java.net.UnknownHostException -> NetworkError.NoInternet()
                is java.net.SocketTimeoutException -> NetworkError.Timeout()
                is java.io.IOException -> ConnectionError.ConnectionLost()
                is SecurityException -> ConnectionError.PermissionDenied(
                    permission = throwable.message
                )
                else -> Unknown(
                    message = throwable.message ?: "Unknown error",
                    originalError = throwable.toString()
                )
            }
        }
    }
}

/**
 * Extension to convert SpaceTecError to a user-friendly message.
 */
fun SpaceTecError.toUserMessage(): String = when (this) {
    is SpaceTecError.ConnectionError.BluetoothDisabled -> 
        "Please enable Bluetooth to connect to the scanner"
    is SpaceTecError.ConnectionError.WifiDisabled -> 
        "Please enable WiFi to connect to the scanner"
    is SpaceTecError.ConnectionError.PermissionDenied -> 
        "Permission required to access the scanner. Please grant the necessary permissions."
    is SpaceTecError.VehicleError.IgnitionOff -> 
        "Please turn on the vehicle ignition"
    else -> message
}