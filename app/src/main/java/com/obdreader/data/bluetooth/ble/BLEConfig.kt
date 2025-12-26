package com.obdreader.data.bluetooth.ble

import java.util.UUID

/**
 * Configuration constants for BLE OBD communication
 */
object BLEConfig {
    // Standard OBD BLE Service UUID
    val GATT_SERVICE_UUID: UUID = UUID.fromString("0000FFF0-0000-1000-8000-00805F9B34FB")
    
    // TX Characteristic - Write commands to adapter
    val TX_CHAR_UUID: UUID = UUID.fromString("0000FFF1-0000-1000-8000-00805F9B34FB")
    
    // RX Characteristic - Receive responses from adapter
    val RX_CHAR_UUID: UUID = UUID.fromString("0000FFF2-0000-1000-8000-00805F9B34FB")
    
    // Client Characteristic Configuration Descriptor
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    
    // Alternative service UUIDs for different adapters
    val ALT_SERVICE_UUIDS = listOf(
        UUID.fromString("0000FFE0-0000-1000-8000-00805F9B34FB"),
        UUID.fromString("0000ABF0-0000-1000-8000-00805F9B34FB"),
        UUID.fromString("E7810A71-73AE-499D-8C15-FAA9AEF0C3F2")
    )
    
    // Connection parameters
    const val PREFERRED_MTU = 512
    const val MIN_MTU = 23
    const val DEFAULT_MTU = 20
    
    // Timing constants
    const val SCAN_TIMEOUT_MS = 10_000L
    const val CONNECTION_TIMEOUT_MS = 10_000L
    const val SERVICE_DISCOVERY_TIMEOUT_MS = 5_000L
    const val MTU_REQUEST_TIMEOUT_MS = 2_000L
    const val WRITE_TIMEOUT_MS = 1_000L
    const val NOTIFICATION_TIMEOUT_MS = 2_000L
    
    // Connection intervals (in units of 1.25ms)
    const val MIN_CONNECTION_INTERVAL = 12  // 15ms
    const val MAX_CONNECTION_INTERVAL = 24  // 30ms
    const val SLAVE_LATENCY = 0
    const val SUPERVISION_TIMEOUT = 200  // 2 seconds
    
    // Retry configuration
    const val MAX_CONNECT_RETRIES = 3
    const val MAX_WRITE_RETRIES = 2
    const val MAX_MTU_RETRIES = 2
    
    // Queue limits
    const val MAX_PENDING_WRITES = 6
    const val WRITE_QUEUE_CAPACITY = 20
}
