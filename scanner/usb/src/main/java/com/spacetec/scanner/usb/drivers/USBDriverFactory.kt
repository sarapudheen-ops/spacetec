/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 *
 * This file is part of the SpaceTec professional automotive diagnostic system.
 */

package com.spacetec.obd.scanner.usb.drivers

import android.hardware.usb.UsbDevice
import com.spacetec.obd.scanner.usb.USBConstants
import com.spacetec.obd.scanner.usb.USBDriverType
import com.spacetec.obd.scanner.usb.serial.USBSerialPort

/**
 * Factory for creating USB serial port drivers.
 *
 * Automatically detects the appropriate driver based on USB vendor/product IDs.
 */
object USBDriverFactory {
    
    /**
     * Creates a serial port driver for the given USB device.
     *
     * @param device USB device
     * @return Appropriate serial port driver
     */
    fun createDriver(device: UsbDevice): USBSerialPort {
        val driverType = detectDriverType(device)
        return createDriverForType(device, driverType)
    }
    
    /**
     * Creates a serial port driver of the specified type.
     *
     * @param device USB device
     * @param driverType Driver type to use
     * @return Serial port driver
     */
    fun createDriverForType(device: UsbDevice, driverType: USBDriverType): USBSerialPort {
        return when (driverType) {
            USBDriverType.FTDI -> FtdiSerialPort(device)
            USBDriverType.CDC_ACM -> CdcAcmSerialPort(device)
            USBDriverType.CP210X -> CdcAcmSerialPort(device) // CP210x uses CDC-like interface
            USBDriverType.PROLIFIC -> CdcAcmSerialPort(device) // Prolific uses CDC-like interface
            USBDriverType.CH340 -> CdcAcmSerialPort(device) // CH340 uses CDC-like interface
            USBDriverType.UNKNOWN -> CdcAcmSerialPort(device) // Default to CDC ACM
        }
    }
    
    /**
     * Detects the appropriate driver type for a USB device.
     *
     * @param device USB device
     * @return Detected driver type
     */
    fun detectDriverType(device: UsbDevice): USBDriverType {
        val vendorId = device.vendorId
        val productId = device.productId
        
        return when (vendorId) {
            USBConstants.VendorIds.FTDI -> USBDriverType.FTDI
            USBConstants.VendorIds.SILABS_CP210X -> USBDriverType.CP210X
            USBConstants.VendorIds.PROLIFIC -> USBDriverType.PROLIFIC
            USBConstants.VendorIds.CH340 -> USBDriverType.CH340
            else -> {
                // Check if device has CDC ACM interface
                if (hasCdcAcmInterface(device)) {
                    USBDriverType.CDC_ACM
                } else {
                    USBDriverType.UNKNOWN
                }
            }
        }
    }
    
    /**
     * Checks if a device has a CDC ACM interface.
     */
    private fun hasCdcAcmInterface(device: UsbDevice): Boolean {
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            if (iface.interfaceClass == android.hardware.usb.UsbConstants.USB_CLASS_COMM ||
                iface.interfaceClass == android.hardware.usb.UsbConstants.USB_CLASS_CDC_DATA) {
                return true
            }
        }
        return false
    }
    
    /**
     * Checks if a device is a supported OBD adapter.
     *
     * @param device USB device
     * @return true if device is likely an OBD adapter
     */
    fun isOBDAdapter(device: UsbDevice): Boolean {
        val vendorId = device.vendorId
        val productId = device.productId
        
        // Check known OBD adapter vendor/product IDs
        return when (vendorId) {
            USBConstants.VendorIds.FTDI -> {
                productId in listOf(
                    USBConstants.ProductIds.FTDI_FT232R,
                    USBConstants.ProductIds.FTDI_FT232H,
                    USBConstants.ProductIds.OBDLINK_EX
                )
            }
            USBConstants.VendorIds.SILABS_CP210X -> {
                productId in listOf(
                    USBConstants.ProductIds.CP2102,
                    USBConstants.ProductIds.CP2104
                )
            }
            USBConstants.VendorIds.PROLIFIC -> {
                productId == USBConstants.ProductIds.PL2303
            }
            USBConstants.VendorIds.CH340 -> {
                productId == USBConstants.ProductIds.CH340
            }
            else -> {
                // Check for CDC ACM interface as fallback
                hasCdcAcmInterface(device)
            }
        }
    }
    
    /**
     * Gets a human-readable name for the driver type.
     *
     * @param driverType Driver type
     * @return Human-readable name
     */
    fun getDriverName(driverType: USBDriverType): String {
        return when (driverType) {
            USBDriverType.FTDI -> "FTDI"
            USBDriverType.CP210X -> "Silicon Labs CP210x"
            USBDriverType.PROLIFIC -> "Prolific PL2303"
            USBDriverType.CH340 -> "WCH CH340"
            USBDriverType.CDC_ACM -> "CDC ACM"
            USBDriverType.UNKNOWN -> "Unknown"
        }
    }
    
    /**
     * Gets the recommended serial configuration for a device.
     *
     * @param device USB device
     * @return Recommended baud rate
     */
    fun getRecommendedBaudRate(device: UsbDevice): Int {
        val vendorId = device.vendorId
        val productId = device.productId
        
        // OBDLink devices support higher baud rates
        if (vendorId == USBConstants.VendorIds.FTDI &&
            productId == USBConstants.ProductIds.OBDLINK_EX) {
            return USBConstants.HIGH_SPEED_BAUD_RATE
        }
        
        // Default to standard ELM327 baud rate
        return USBConstants.DEFAULT_BAUD_RATE
    }
}
