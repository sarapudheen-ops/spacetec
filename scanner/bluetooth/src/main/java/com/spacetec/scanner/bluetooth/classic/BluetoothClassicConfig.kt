/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 *
 * This file is part of the SpaceTec professional automotive diagnostic system.
 */

package com.spacetec.obd.scanner.bluetooth.classic

import android.Manifest
import android.os.Build
import com.spacetec.obd.scanner.core.ConnectionConfig
import java.util.UUID

/**
 * Bluetooth Classic SPP constants.
 */
object BluetoothClassicConstants {

    /**
     * Standard Serial Port Profile (SPP) UUID.
     * Used by most ELM327 and OBD-II adapters.
     */
    val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    /**
     * Alternative SPP UUID used by some adapters.
     */
    val SPP_UUID_ALT: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FC")

    /**
     * Default RFCOMM channel.
     */
    const val DEFAULT_RFCOMM_CHANNEL = 1

    /**
     * Maximum connection retry attempts.
     */
    const val MAX_CONNECTION_RETRIES = 3

    /**
     * Delay between connection retries in milliseconds.
     */
    const val CONNECTION_RETRY_DELAY = 500L

    /**
     * Socket connection timeout in milliseconds.
     */
    const val SOCKET_CONNECT_TIMEOUT = 10_000L

    /**
     * Read buffer size.
     */
    const val READ_BUFFER_SIZE = 4096

    /**
     * Default MTU for Bluetooth Classic.
     */
    const val DEFAULT_MTU = 512

    /**
     * Background read interval in milliseconds.
     */
    const val BACKGROUND_READ_INTERVAL = 10L

    /**
     * Bluetooth permissions required for different API levels.
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
 * Bluetooth Classic SPP connection configuration.
 *
 * @property baseConfig Base connection configuration
 * @property uuid Service UUID to use (default: SPP)
 * @property secure Whether to use secure (encrypted) connection
 * @property useFallback Whether to use fallback socket creation methods
 * @property rfcommChannel RFCOMM channel for fallback connection
 * @property cancelDiscoveryBeforeConnect Whether to cancel discovery before connecting
 */
data class BluetoothClassicConfig(
    val baseConfig: ConnectionConfig = ConnectionConfig.BLUETOOTH,
    val uuid: UUID = BluetoothClassicConstants.SPP_UUID,
    val secure: Boolean = true,
    val useFallback: Boolean = true,
    val rfcommChannel: Int = BluetoothClassicConstants.DEFAULT_RFCOMM_CHANNEL,
    val cancelDiscoveryBeforeConnect: Boolean = true
) {
    companion object {
        /**
         * Default configuration.
         */
        val DEFAULT = BluetoothClassicConfig()

        /**
         * Configuration for insecure connection (some clones require this).
         */
        val INSECURE = BluetoothClassicConfig(
            secure = false
        )

        /**
         * Configuration with aggressive fallback.
         */
        val WITH_FALLBACK = BluetoothClassicConfig(
            useFallback = true,
            secure = true
        )

        /**
         * Configuration for ELM327 clones that may have connection issues.
         */
        val FOR_CLONES = BluetoothClassicConfig(
            secure = false,
            useFallback = true,
            baseConfig = ConnectionConfig.BLUETOOTH.copy(
                connectionTimeout = 20_000L,
                readTimeout = 8_000L
            )
        )

        /**
         * Configuration for professional OBDLink devices.
         */
        val FOR_OBDLINK = BluetoothClassicConfig(
            secure = true,
            useFallback = false,
            baseConfig = ConnectionConfig.BLUETOOTH.copy(
                connectionTimeout = 10_000L,
                readTimeout = 5_000L
            )
        )
    }
}

/**
 * Bluetooth Classic connection error types.
 */
enum class BluetoothClassicError {
    /**
     * Bluetooth is not available on the device.
     */
    BLUETOOTH_NOT_AVAILABLE,

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
     * Socket creation failed.
     */
    SOCKET_CREATION_FAILED,

    /**
     * Connection timed out.
     */
    CONNECTION_TIMEOUT,

    /**
     * Connection refused by device.
     */
    CONNECTION_REFUSED,

    /**
     * Connection lost during communication.
     */
    CONNECTION_LOST,

    /**
     * Read operation failed.
     */
    READ_FAILED,

    /**
     * Write operation failed.
     */
    WRITE_FAILED,

    /**
     * Unknown error.
     */
    UNKNOWN
}
