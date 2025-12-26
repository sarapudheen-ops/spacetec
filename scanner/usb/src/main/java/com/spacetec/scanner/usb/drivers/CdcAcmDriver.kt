/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 *
 * This file is part of the SpaceTec professional automotive diagnostic system.
 */

package com.spacetec.obd.scanner.usb.drivers

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import com.spacetec.obd.scanner.usb.USBDriverType
import com.spacetec.obd.scanner.usb.USBParity
import com.spacetec.obd.scanner.usb.USBStopBits
import com.spacetec.obd.scanner.usb.serial.BaseUSBSerialPort
import java.io.IOException

/**
 * CDC ACM (Abstract Control Model) USB serial driver.
 *
 * This is the standard USB serial driver that works with most USB-to-serial
 * adapters that implement the CDC ACM class.
 */
class CdcAcmSerialPort(device: UsbDevice) : BaseUSBSerialPort(device, USBDriverType.CDC_ACM) {
    
    private var controlInterface: UsbInterface? = null
    private var dataInterface: UsbInterface? = null
    private var controlEndpoint: UsbEndpoint? = null
    
    private var dtr = false
    private var rts = false
    
    companion object {
        // CDC ACM control request types
        private const val USB_RT_ACM = UsbConstants.USB_TYPE_CLASS or 0x01
        
        // CDC ACM control requests
        private const val SET_LINE_CODING = 0x20
        private const val GET_LINE_CODING = 0x21
        private const val SET_CONTROL_LINE_STATE = 0x22
        private const val SEND_BREAK = 0x23
        
        // Control line state bits
        private const val CONTROL_DTR = 0x01
        private const val CONTROL_RTS = 0x02
        
        // Line coding structure size
        private const val LINE_CODING_SIZE = 7
    }
    
    @Throws(IOException::class)
    override fun findInterface() {
        val conn = connection ?: throw IOException("No connection")
        
        // CDC ACM devices typically have two interfaces:
        // - Control interface (class 0x02, subclass 0x02)
        // - Data interface (class 0x0A)
        
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            
            when (iface.interfaceClass) {
                UsbConstants.USB_CLASS_COMM -> {
                    // Control interface
                    if (conn.claimInterface(iface, true)) {
                        controlInterface = iface
                    }
                }
                UsbConstants.USB_CLASS_CDC_DATA -> {
                    // Data interface
                    if (conn.claimInterface(iface, true)) {
                        dataInterface = iface
                        usbInterface = iface
                    }
                }
            }
        }
        
        // Fallback: try to use first interface if no CDC interfaces found
        if (usbInterface == null && device.interfaceCount > 0) {
            val iface = device.getInterface(0)
            if (conn.claimInterface(iface, true)) {
                usbInterface = iface
            }
        }
        
        if (usbInterface == null) {
            throw IOException("Could not claim data interface")
        }
    }
    
    @Throws(IOException::class)
    override fun findEndpoints() {
        // Find control endpoint in control interface
        controlInterface?.let { iface ->
            for (i in 0 until iface.endpointCount) {
                val endpoint = iface.getEndpoint(i)
                if (endpoint.type == UsbConstants.USB_ENDPOINT_XFER_INT &&
                    endpoint.direction == UsbConstants.USB_DIR_IN) {
                    controlEndpoint = endpoint
                    break
                }
            }
        }
        
        // Find data endpoints
        val dataIface = usbInterface ?: throw IOException("No data interface")
        
        for (i in 0 until dataIface.endpointCount) {
            val endpoint = dataIface.getEndpoint(i)
            
            if (endpoint.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                if (endpoint.direction == UsbConstants.USB_DIR_IN) {
                    readEndpoint = endpoint
                } else {
                    writeEndpoint = endpoint
                }
            }
        }
        
        if (readEndpoint == null || writeEndpoint == null) {
            throw IOException("Could not find bulk endpoints")
        }
    }
    
    @Throws(IOException::class)
    override fun initializeChip() {
        // CDC ACM doesn't require special initialization
        // Just set initial control line state
        setControlLineState()
    }
    
    @Throws(IOException::class)
    override fun setParameters(
        baudRate: Int,
        dataBits: Int,
        stopBits: USBStopBits,
        parity: USBParity
    ) {
        val conn = connection ?: throw IOException("No connection")
        
        // Build line coding structure
        // dwDTERate (4 bytes) - baud rate
        // bCharFormat (1 byte) - stop bits: 0=1, 1=1.5, 2=2
        // bParityType (1 byte) - parity: 0=none, 1=odd, 2=even, 3=mark, 4=space
        // bDataBits (1 byte) - data bits: 5, 6, 7, 8
        
        val lineCoding = ByteArray(LINE_CODING_SIZE)
        
        // Baud rate (little-endian)
        lineCoding[0] = (baudRate and 0xFF).toByte()
        lineCoding[1] = ((baudRate shr 8) and 0xFF).toByte()
        lineCoding[2] = ((baudRate shr 16) and 0xFF).toByte()
        lineCoding[3] = ((baudRate shr 24) and 0xFF).toByte()
        
        // Stop bits
        lineCoding[4] = when (stopBits) {
            USBStopBits.ONE -> 0
            USBStopBits.ONE_POINT_FIVE -> 1
            USBStopBits.TWO -> 2
        }.toByte()
        
        // Parity
        lineCoding[5] = parity.value.toByte()
        
        // Data bits
        lineCoding[6] = dataBits.toByte()
        
        val result = conn.controlTransfer(
            USB_RT_ACM,
            SET_LINE_CODING,
            0,
            controlInterface?.id ?: 0,
            lineCoding,
            lineCoding.size,
            5000
        )
        
        if (result < 0) {
            throw IOException("Failed to set line coding: $result")
        }
    }
    
    @Throws(IOException::class)
    override fun setDTR(state: Boolean) {
        dtr = state
        setControlLineState()
    }
    
    @Throws(IOException::class)
    override fun setRTS(state: Boolean) {
        rts = state
        setControlLineState()
    }
    
    @Throws(IOException::class)
    private fun setControlLineState() {
        val conn = connection ?: return
        
        var value = 0
        if (dtr) value = value or CONTROL_DTR
        if (rts) value = value or CONTROL_RTS
        
        val result = conn.controlTransfer(
            USB_RT_ACM,
            SET_CONTROL_LINE_STATE,
            value,
            controlInterface?.id ?: 0,
            null,
            0,
            5000
        )
        
        if (result < 0) {
            throw IOException("Failed to set control line state: $result")
        }
    }
    
    @Throws(IOException::class)
    override fun getCTS(): Boolean {
        // CDC ACM doesn't provide a standard way to read CTS
        // Some devices may support it through vendor-specific commands
        return true
    }
    
    @Throws(IOException::class)
    override fun getDSR(): Boolean {
        // CDC ACM doesn't provide a standard way to read DSR
        return true
    }
    
    override fun close() {
        try {
            controlInterface?.let { iface ->
                connection?.releaseInterface(iface)
            }
        } catch (_: Exception) {}
        
        controlInterface = null
        dataInterface = null
        controlEndpoint = null
        
        super.close()
    }
}
