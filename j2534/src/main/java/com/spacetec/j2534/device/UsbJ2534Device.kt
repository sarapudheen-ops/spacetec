package com.spacetec.j2534.device

import android.hardware.usb.*
import android.content.Context
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * USB communication handler for J2534 devices
 */
class UsbJ2534Device(
    private val context: Context,
    private val usbDevice: UsbDevice,
    private val usbManager: UsbManager
) {
    private var connection: UsbDeviceConnection? = null
    private var interfaceEndpoint: UsbInterface? = null
    private var endpointIn: UsbEndpoint? = null
    private var endpointOut: UsbEndpoint? = null
    private val isConnected = AtomicBoolean(false)
    private val connectionLock = ReentrantLock()
    
    /**
     * Connect to the USB device
     */
    fun connect(): Boolean {
        return connectionLock.withLock {
            if (isConnected.get()) {
                return true
            }
            
            // Request permission if needed
            if (!usbManager.hasPermission(usbDevice)) {
                // In a real implementation, you would need to request permission
                // This requires an activity context and user interaction
                return false
            }
            
            // Find the appropriate interface
            var usbInterface: UsbInterface? = null
            for (i in 0 until usbDevice.interfaceCount) {
                val currentInterface = usbDevice.getInterface(i)
                if (currentInterface.interfaceClass == UsbConstants.USB_CLASS_VENDOR_SPEC) {
                    usbInterface = currentInterface
                    break
                }
            }
            
            if (usbInterface == null) {
                // Try to find any interface if vendor-specific isn't available
                usbInterface = usbDevice.getInterface(0)
            }
            
            if (usbInterface == null) {
                return false
            }
            
            // Establish connection
            val conn = usbManager.openDevice(usbDevice)
            if (conn == null) {
                return false
            }
            
            // Claim the interface
            if (!conn.claimInterface(usbInterface, true)) {
                conn.close()
                return false
            }
            
            // Find endpoints
            var epIn: UsbEndpoint? = null
            var epOut: UsbEndpoint? = null
            
            for (i in 0 until usbInterface.endpointCount) {
                val endpoint = usbInterface.getEndpoint(i)
                when (endpoint.type) {
                    UsbConstants.USB_ENDPOINT_XFER_BULK -> {
                        when (endpoint.direction) {
                            UsbConstants.USB_DIR_IN -> epIn = endpoint
                            UsbConstants.USB_DIR_OUT -> epOut = endpoint
                        }
                    }
                }
            }
            
            if (epIn == null || epOut == null) {
                conn.releaseInterface(usbInterface)
                conn.close()
                return false
            }
            
            // Set connection parameters
            this.connection = conn
            this.interfaceEndpoint = usbInterface
            this.endpointIn = epIn
            this.endpointOut = epOut
            
            isConnected.set(true)
            return true
        }
    }
    
    /**
     * Disconnect from the USB device
     */
    fun disconnect() {
        connectionLock.withLock {
            if (!isConnected.get()) {
                return
            }
            
            connection?.let { conn ->
                interfaceEndpoint?.let { intf ->
                    conn.releaseInterface(intf)
                }
                conn.close()
            }
            
            connection = null
            interfaceEndpoint = null
            endpointIn = null
            endpointOut = null
            isConnected.set(false)
        }
    }
    
    /**
     * Write data to the USB device
     */
    fun writeData(data: ByteArray): Int {
        return connectionLock.withLock {
            if (!isConnected.get() || endpointOut == null) {
                return -1
            }
            
            connection?.let { conn ->
                endpointOut?.let { endpoint ->
                    conn.bulkTransfer(endpoint, data, data.size, 1000) // 1 second timeout
                } ?: -1
            } ?: -1
        }
    }
    
    /**
     * Read data from the USB device
     */
    fun readData(buffer: ByteArray): Int {
        return connectionLock.withLock {
            if (!isConnected.get() || endpointIn == null) {
                return -1
            }
            
            connection?.let { conn ->
                endpointIn?.let { endpoint ->
                    conn.bulkTransfer(endpoint, buffer, buffer.size, 1000) // 1 second timeout
                } ?: -1
            } ?: -1
        }
    }
    
    /**
     * Check if device is connected
     */
    fun isConnected(): Boolean {
        return isConnected.get()
    }
    
    /**
     * Get USB device information
     */
    fun getDeviceInfo(): String {
        return "USB Device: ${usbDevice.productName ?: "Unknown"} " +
               "(VID: ${String.format("%04X", usbDevice.vendorId)}, " +
               "PID: ${String.format("%04X", usbDevice.productId)})"
    }
    
    /**
     * Get USB device descriptor
     */
    fun getDeviceDescriptor(): UsbDevice? {
        return usbDevice
    }
    
    companion object {
        /**
         * Find J2534 compatible USB devices
         */
        fun findJ2534Devices(context: Context): List<UsbDevice> {
            val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
                ?: return emptyList()
            
            val devices = mutableListOf<UsbDevice>()
            val deviceList = usbManager.deviceList
            
            for ((_, device) in deviceList) {
                if (isJ2534Device(device)) {
                    devices.add(device)
                }
            }
            
            return devices
        }
        
        /**
         * Check if a USB device is J2534 compatible
         */
        private fun isJ2534Device(device: UsbDevice): Boolean {
            // Common J2534 device vendor IDs
            val vendorId = device.vendorId
            val productId = device.productId
            
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