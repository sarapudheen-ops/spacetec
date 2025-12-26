/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 *
 * This file is part of the SpaceTec professional automotive diagnostic system.
 */

package com.spacetec.obd.scanner.bluetooth.ble

import android.Manifest
import android.os.Build
import com.spacetec.obd.scanner.core.ConnectionConfig
import java.util.UUID

/**
 * Bluetooth Low Energy constants.
 */
object BluetoothLEConstants {

    /**
     * Standard OBD BLE service UUID (used by most BLE OBD adapters).
     */
    val OBD_SERVICE_UUID: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")

    /**
     * Alternative OBD BLE service UUID.
     */
    val OBD_SERVICE_UUID_ALT: UUID = UUID.fromString("e7810a71-73ae-499d-8c15-faa9aef0c3f2")

    /**
     * Standard write characteristic UUID.
     */
    val WRITE_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb")

    /**
     * Alternative write characteristic UUID.
     */
    val WRITE_CHARACTERISTIC_UUID_ALT: UUID = UUID.fromString("bef8d6c9-9c21-4c9e-b632-bd58c1009f9f")

    /**
     * Standard notify characteristic UUID.
     */
    val NOTIFY_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")

    /**
     * Alternative notify characteristic UUID.
     */
    val NOTIFY_CHARACTERISTIC_UUID_ALT: UUID = UUID.fromString("bef8d6c9-9c21-4c9e-b632-bd58c1009f9e")

    /**
     * Client Characteristic Configuration Descriptor UUID.
     */
    val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    /**
     * Default MTU for BLE.
     */
    const val DEFAULT_MTU = 23

    /**
     * Maximum MTU for BLE.
     */
    const val MAX_MTU = 517

    /**
     * Preferred MTU for OBD communication.
     */
    const val PREFERRED_MTU = 247

    /**
     * ATT header size (3 bytes).
     */
    const val ATT_HEADER_SIZE = 3

    /**
     * Maximum connection retry attempts.
     */
    const val MAX_CONNECTION_RETRIES = 3

    /**
     * Delay between connection retries in milliseconds.
     */
    const val CONNECTION_RETRY_DELAY = 1000L

    /**
     * Service discovery timeout in milliseconds.
     */
    const val SERVICE_DISCOVERY_TIMEOUT = 10_000L

    /**
     * MTU negotiation timeout in milliseconds.
     */
    const val MTU_NEGOTIATION_TIMEOUT = 5_000L

    /**
     * Delay between chunk writes in milliseconds.
     */
    const val CHUNK_WRITE_DELAY = 20L

    /**
     * Bluetooth permissions required for BLE on different API levels.
     */
    val BLUETOOTH_PERMISSIONS: List<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
        } else {
            listOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
}

/**
 * Bluetooth Low Energy connection configuration.
 *
 * @property baseConfig Base connection configuration
 * @property serviceUUIDs List of service UUIDs to search for
 * @property writeCharacteristicUUIDs List of write characteristic UUIDs to search for
 * @property notifyCharacteristicUUIDs List of notify characteristic UUIDs to search for
 * @property requestMtu MTU to request during connection
 * @property serviceDiscoveryTimeout Timeout for service discovery
 * @property enableAutoReconnect Whether to enable automatic reconnection
 */
data class BluetoothLEConfig(
    val baseConfig: ConnectionConfig = ConnectionConfig.BLUETOOTH.copy(
        connectionTimeout = 15_000L,
        readTimeout = 5_000L
    ),
    val serviceUUIDs: List<UUID> = listOf(
        BluetoothLEConstants.OBD_SERVICE_UUID,
        BluetoothLEConstants.OBD_SERVICE_UUID_ALT
    ),
    val writeCharacteristicUUIDs: List<UUID> = listOf(
        BluetoothLEConstants.WRITE_CHARACTERISTIC_UUID,
        BluetoothLEConstants.WRITE_CHARACTERISTIC_UUID_ALT
    ),
    val notifyCharacteristicUUIDs: List<UUID> = listOf(
        BluetoothLEConstants.NOTIFY_CHARACTERISTIC_UUID,
        BluetoothLEConstants.NOTIFY_CHARACTERISTIC_UUID_ALT
    ),
    val requestMtu: Int = BluetoothLEConstants.PREFERRED_MTU,
    val serviceDiscoveryTimeout: Long = BluetoothLEConstants.SERVICE_DISCOVERY_TIMEOUT,
    val enableAutoReconnect: Boolean = true
) {
    companion object {
        /**
         * Default configuration.
         */
        val DEFAULT = BluetoothLEConfig()

        /**
         * Configuration for Veepeak BLE adapters.
         */
        val VEEPEAK = BluetoothLEConfig(
            serviceUUIDs = listOf(BluetoothLEConstants.OBD_SERVICE_UUID),
            requestMtu = BluetoothLEConstants.PREFERRED_MTU
        )

        /**
         * Configuration with minimal MTU (for compatibility).
         */
        val MINIMAL_MTU = BluetoothLEConfig(
            requestMtu = BluetoothLEConstants.DEFAULT_MTU
        )

        /**
         * Configuration with maximum MTU.
         */
        val MAX_MTU = BluetoothLEConfig(
            requestMtu = BluetoothLEConstants.MAX_MTU
        )
    }
}

/**
 * BLE connection error types.
 */
enum class BluetoothLEError {
    /**
     * BLE is not available on the device.
     */
    BLE_NOT_AVAILABLE,

    /**
     * Bluetooth is disabled.
     */
    BLUETOOTH_DISABLED,

    /**
     * Required permissions not granted.
     */
    PERMISSION_DENIED,

    /**
     * Invalid MAC address format.
     */
    INVALID_ADDRESS,

    /**
     * Device not found.
     */
    DEVICE_NOT_FOUND,

    /**
     * GATT connection failed.
     */
    GATT_CONNECTION_FAILED,

    /**
     * Service discovery failed.
     */
    SERVICE_DISCOVERY_FAILED,

    /**
     * OBD service not found.
     */
    SERVICE_NOT_FOUND,

    /**
     * Required characteristic not found.
     */
    CHARACTERISTIC_NOT_FOUND,

    /**
     * MTU negotiation failed.
     */
    MTU_NEGOTIATION_FAILED,

    /**
     * Notification enable failed.
     */
    NOTIFICATION_ENABLE_FAILED,

    /**
     * Write operation failed.
     */
    WRITE_FAILED,

    /**
     * Read operation failed.
     */
    READ_FAILED,

    /**
     * Connection lost.
     */
    CONNECTION_LOST,

    /**
     * Unknown error.
     */
    UNKNOWN
}
