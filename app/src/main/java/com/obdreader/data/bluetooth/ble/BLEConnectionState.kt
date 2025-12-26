package com.obdreader.data.bluetooth.ble

/**
 * Represents the various states of a BLE connection
 */
sealed class BLEConnectionState {
    object Disconnected : BLEConnectionState()
    object Scanning : BLEConnectionState()
    object Connecting : BLEConnectionState()
    object DiscoveringServices : BLEConnectionState()
    object NegotiatingMTU : BLEConnectionState()
    object EnablingNotifications : BLEConnectionState()
    object Ready : BLEConnectionState()
    data class Error(val exception: BLEException) : BLEConnectionState()
    
    fun isConnected(): Boolean = this is Ready
    fun canSendCommands(): Boolean = this is Ready
    
    override fun toString(): String = when (this) {
        is Disconnected -> "Disconnected"
        is Scanning -> "Scanning"
        is Connecting -> "Connecting"
        is DiscoveringServices -> "Discovering Services"
        is NegotiatingMTU -> "Negotiating MTU"
        is EnablingNotifications -> "Enabling Notifications"
        is Ready -> "Ready"
        is Error -> "Error: ${exception.message}"
    }
}

/**
 * BLE-specific exceptions
 */
sealed class BLEException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class ScanFailedException(val errorCode: Int) : 
        BLEException("BLE scan failed with error code: $errorCode")
    
    class ConnectionFailedException(val status: Int) : 
        BLEException("Connection failed with status: $status")
    
    class ServiceDiscoveryFailedException(val status: Int) : 
        BLEException("Service discovery failed with status: $status")
    
    class ServiceNotFoundException(val serviceUuid: String) : 
        BLEException("Required service not found: $serviceUuid")
    
    class CharacteristicNotFoundException(val charUuid: String) : 
        BLEException("Required characteristic not found: $charUuid")
    
    class MTUNegotiationFailedException(val requestedMtu: Int, val status: Int) : 
        BLEException("MTU negotiation failed. Requested: $requestedMtu, Status: $status")
    
    class NotificationEnableFailedException(val charUuid: String, val status: Int) : 
        BLEException("Failed to enable notifications for $charUuid, Status: $status")
    
    class WriteFailedException(val status: Int) : 
        BLEException("Write operation failed with status: $status")
    
    class ReadFailedException(val status: Int) : 
        BLEException("Read operation failed with status: $status")
    
    class TimeoutException(operation: String) : 
        BLEException("Operation timed out: $operation")
    
    class DisconnectedException : 
        BLEException("Device disconnected unexpectedly")
    
    class BluetoothDisabledException : 
        BLEException("Bluetooth is disabled")
    
    class PermissionDeniedException(val permission: String) : 
        BLEException("Permission denied: $permission")
}
