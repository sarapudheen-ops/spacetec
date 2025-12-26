/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 *
 * This file is part of the SpaceTec professional automotive diagnostic system.
 */

package com.spacetec.obd.scanner.usb

import com.spacetec.obd.scanner.core.ConnectionConfig

/**
 * USB Serial communication constants.
 */
object USBConstants {
    
    /**
     * Default baud rate for OBD-II adapters.
     */
    const val DEFAULT_BAUD_RATE = 38400
    
    /**
     * High-speed baud rate for professional adapters.
     */
    const val HIGH_SPEED_BAUD_RATE = 115200
    
    /**
     * Maximum baud rate supported.
     */
    const val MAX_BAUD_RATE = 921600
    
    /**
     * Default data bits.
     */
    const val DEFAULT_DATA_BITS = 8
    
    /**
     * Default stop bits.
     */
    const val DEFAULT_STOP_BITS = 1
    
    /**
     * Default parity (none).
     */
    const val DEFAULT_PARITY = 0
    
    /**
     * Read buffer size.
     */
    const val READ_BUFFER_SIZE = 4096
    
    /**
     * Write buffer size.
     */
    const val WRITE_BUFFER_SIZE = 4096
    
    /**
     * Default MTU for USB serial.
     */
    const val DEFAULT_MTU = 1024
    
    /**
     * Maximum connection retry attempts.
     */
    const val MAX_CONNECTION_RETRIES = 3
    
    /**
     * Delay between connection retries in milliseconds.
     */
    const val CONNECTION_RETRY_DELAY = 500L
    
    /**
     * Background read interval in milliseconds.
     */
    const val BACKGROUND_READ_INTERVAL = 5L
    
    /**
     * USB permission request timeout in milliseconds.
     */
    const val PERMISSION_REQUEST_TIMEOUT = 30_000L
    
    /**
     * USB device detection delay in milliseconds.
     */
    const val DEVICE_DETECTION_DELAY = 100L
    
    /**
     * USB reset delay in milliseconds.
     */
    const val USB_RESET_DELAY = 500L
    
    /**
     * Common USB Vendor IDs for OBD adapters.
     */
    object VendorIds {
        const val FTDI = 0x0403
        const val SILABS_CP210X = 0x10C4
        const val PROLIFIC = 0x067B
        const val CH340 = 0x1A86
        const val OBDLINK = 0x0403  // Uses FTDI
        const val GENERIC_CDC = 0x2341  // Arduino-like CDC
    }
    
    /**
     * Common USB Product IDs for OBD adapters.
     */
    object ProductIds {
        const val FTDI_FT232R = 0x6001
        const val FTDI_FT232H = 0x6014
        const val CP2102 = 0xEA60
        const val CP2104 = 0xEA61
        const val PL2303 = 0x2303
        const val CH340 = 0x7523
        const val OBDLINK_EX = 0x6015
    }
}

/**
 * USB serial port parity options.
 */
enum class USBParity(val value: Int) {
    NONE(0),
    ODD(1),
    EVEN(2),
    MARK(3),
    SPACE(4)
}

/**
 * USB serial port stop bits options.
 */
enum class USBStopBits(val value: Int) {
    ONE(1),
    ONE_POINT_FIVE(3),
    TWO(2)
}

/**
 * USB serial port flow control options.
 */
enum class USBFlowControl {
    NONE,
    RTS_CTS,
    DTR_DSR,
    XON_XOFF
}

/**
 * USB device driver type.
 */
enum class USBDriverType {
    /**
     * FTDI FT232 series chips.
     */
    FTDI,
    
    /**
     * Silicon Labs CP210x series chips.
     */
    CP210X,
    
    /**
     * Prolific PL2303 chips.
     */
    PROLIFIC,
    
    /**
     * WCH CH340/CH341 chips.
     */
    CH340,
    
    /**
     * CDC ACM (Abstract Control Model) - standard USB serial.
     */
    CDC_ACM,
    
    /**
     * Unknown or generic driver.
     */
    UNKNOWN
}

/**
 * USB serial port configuration.
 *
 * @property baudRate Baud rate for serial communication
 * @property dataBits Number of data bits (5, 6, 7, or 8)
 * @property stopBits Number of stop bits
 * @property parity Parity setting
 * @property flowControl Flow control setting
 * @property dtr Data Terminal Ready signal state
 * @property rts Request To Send signal state
 */
data class USBSerialConfig(
    val baudRate: Int = USBConstants.DEFAULT_BAUD_RATE,
    val dataBits: Int = USBConstants.DEFAULT_DATA_BITS,
    val stopBits: USBStopBits = USBStopBits.ONE,
    val parity: USBParity = USBParity.NONE,
    val flowControl: USBFlowControl = USBFlowControl.NONE,
    val dtr: Boolean = true,
    val rts: Boolean = true
) {
    companion object {
        /**
         * Default configuration for ELM327 adapters.
         */
        val ELM327 = USBSerialConfig(
            baudRate = 38400,
            dataBits = 8,
            stopBits = USBStopBits.ONE,
            parity = USBParity.NONE
        )
        
        /**
         * Configuration for OBDLink EX adapters.
         */
        val OBDLINK = USBSerialConfig(
            baudRate = 115200,
            dataBits = 8,
            stopBits = USBStopBits.ONE,
            parity = USBParity.NONE
        )
        
        /**
         * High-speed configuration.
         */
        val HIGH_SPEED = USBSerialConfig(
            baudRate = 115200,
            dataBits = 8,
            stopBits = USBStopBits.ONE,
            parity = USBParity.NONE
        )
        
        /**
         * Auto-detect configuration (will be adjusted based on device).
         */
        val AUTO = USBSerialConfig()
    }
}

/**
 * USB connection configuration.
 *
 * @property baseConfig Base connection configuration
 * @property serialConfig Serial port configuration
 * @property autoDetectParameters Whether to auto-detect serial parameters
 * @property autoRequestPermission Whether to automatically request USB permission
 * @property resetOnConnect Whether to reset the USB device on connect
 */
data class USBConnectionConfig(
    val baseConfig: ConnectionConfig = ConnectionConfig.USB,
    val serialConfig: USBSerialConfig = USBSerialConfig.AUTO,
    val autoDetectParameters: Boolean = true,
    val autoRequestPermission: Boolean = true,
    val resetOnConnect: Boolean = false
) {
    companion object {
        /**
         * Default configuration.
         */
        val DEFAULT = USBConnectionConfig()
        
        /**
         * Configuration for ELM327 adapters.
         */
        val FOR_ELM327 = USBConnectionConfig(
            serialConfig = USBSerialConfig.ELM327
        )
        
        /**
         * Configuration for OBDLink adapters.
         */
        val FOR_OBDLINK = USBConnectionConfig(
            serialConfig = USBSerialConfig.OBDLINK,
            baseConfig = ConnectionConfig.USB.copy(
                readTimeout = 3_000L
            )
        )
        
        /**
         * Configuration with auto-detection.
         */
        val AUTO_DETECT = USBConnectionConfig(
            autoDetectParameters = true,
            serialConfig = USBSerialConfig.AUTO
        )
    }
}

/**
 * USB connection error types.
 */
enum class USBError {
    /**
     * USB Host not supported on this device.
     */
    USB_NOT_SUPPORTED,
    
    /**
     * No USB device connected.
     */
    NO_DEVICE,
    
    /**
     * USB permission not granted.
     */
    PERMISSION_DENIED,
    
    /**
     * Device not found at specified path.
     */
    DEVICE_NOT_FOUND,
    
    /**
     * Failed to open USB device.
     */
    OPEN_FAILED,
    
    /**
     * Failed to claim USB interface.
     */
    CLAIM_FAILED,
    
    /**
     * Serial configuration failed.
     */
    CONFIG_FAILED,
    
    /**
     * Connection timed out.
     */
    CONNECTION_TIMEOUT,
    
    /**
     * Device disconnected.
     */
    DEVICE_DISCONNECTED,
    
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
