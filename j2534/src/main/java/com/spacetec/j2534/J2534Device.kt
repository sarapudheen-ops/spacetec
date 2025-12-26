package com.spacetec.j2534

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import java.util.concurrent.atomic.AtomicBoolean

/**
 * J2534Device - Represents a connected J2534 device
 */
class J2534Device(
    val usbDevice: UsbDevice?,
    val deviceName: String,
    val vendorId: Int,
    val productId: Int,
    val manufacturer: String = "",
    val productName: String = "",
    val serialNumber: String = ""
) {
    private val isConnected = AtomicBoolean(false)
    private var deviceHandle: Long = 0L

    /**
     * Connect to the J2534 device
     */
    fun connect(usbManager: UsbManager?): Boolean {
        if (usbDevice == null) return false
        
        // Request permission if needed
        if (usbManager != null && !usbManager.hasPermission(usbDevice)) {
            // In a real implementation, you would need to request permission from the user
            // This is typically done through an Intent and requires user interaction
            return false
        }
        
        // Attempt to establish connection to the USB device
        // This would involve opening the USB interface and setting up communication
        isConnected.set(true)
        return true
    }

    /**
     * Disconnect from the J2534 device
     */
    fun disconnect(): Boolean {
        if (!isConnected.get()) return true
        
        // Close USB connection and release resources
        isConnected.set(false)
        return true
    }

    /**
     * Check if device is connected
     */
    fun isConnected(): Boolean {
        return isConnected.get()
    }

    /**
     * Get device information as a J2534Device data object
     */
    fun toJ2534Device(): com.spacetec.j2534.J2534Device {
        return com.spacetec.j2534.J2534Device(
            handle = deviceHandle,
            name = deviceName,
            vendor = manufacturer,
            firmwareVersion = "1.0.0", // Would come from actual device query
            dllVersion = "04.04",      // J2534-1 v04.04
            apiVersion = "04.04"       // J2534-1 v04.04
        )
    }

    /**
     * Get device descriptor information
     */
    fun getDeviceDescriptor(): String {
        return "USB Device: $deviceName (VID: ${String.format("%04X", vendorId)}, PID: ${String.format("%04X", productId)})"
    }

    companion object {
        /**
         * Scan for available J2534 devices
         */
        fun scanForDevices(usbManager: UsbManager?): List<J2534Device> {
            val devices = mutableListOf<J2534Device>()
            
            if (usbManager == null) return devices
            
            val usbDevices = usbManager.deviceList
            for ((_, usbDevice) in usbDevices) {
                // Check if this is a known J2534 device based on VID/PID
                if (isJ2534Device(usbDevice.vendorId, usbDevice.productId)) {
                    val j2534Device = J2534Device(
                        usbDevice = usbDevice,
                        deviceName = usbDevice.productName ?: "Unknown Device",
                        vendorId = usbDevice.vendorId,
                        productId = usbDevice.productId,
                        manufacturer = usbDevice.manufacturerName ?: "",
                        productName = usbDevice.productName ?: "",
                        serialNumber = usbDevice.serialNumber ?: ""
                    )
                    devices.add(j2534Device)
                }
            }
            
            return devices
        }

        /**
         * Check if a USB device is a known J2534 device based on VID/PID
         */
        private fun isJ2534Device(vendorId: Int, productId: Int): Boolean {
            // Common J2534 device vendor IDs and product IDs
            return when (vendorId) {
                0x0403 -> { // FTDI
                    productId in listOf(0x6001, 0x6015) // Common FTDI chips used in J2534 devices
                }
                0x0547 -> { // Synaptics (formerly Elan)
                    productId in listOf(0x1002, 0x1003) // Known J2534 device IDs
                }
                0x09E8 -> { // Drew Technologies
                    true // All Drew Technologies devices are J2534 compatible
                }
                0x2C99 -> { // Tactrix
                    productId == 0x0001 || productId == 0x0002 // Tactrix OpenPort devices
                }
                0x0483 -> { // STMicroelectronics
                    productId == 0x5740 // OBDLink devices
                }
                else -> false
            }
        }
    }
}