/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 *
 * This file is part of the SpaceTec professional automotive diagnostic system.
 */

package com.spacetec.obd.scanner.usb.drivers

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import com.spacetec.obd.scanner.usb.USBDriverType
import com.spacetec.obd.scanner.usb.USBParity
import com.spacetec.obd.scanner.usb.USBStopBits
import com.spacetec.obd.scanner.usb.serial.BaseUSBSerialPort
import java.io.IOException

/**
 * FTDI USB serial driver.
 *
 * Supports FTDI FT232R, FT232H, and compatible chips commonly used
 * in OBD-II adapters like OBDLink EX.
 */
class FtdiSerialPort(device: UsbDevice) : BaseUSBSerialPort(device, USBDriverType.FTDI) {
    
    private var dtr = false
    private var rts = false
    private var currentBaudRate = 9600
    
    companion object {
        // FTDI request types
        private const val FTDI_DEVICE_OUT_REQTYPE = UsbConstants.USB_TYPE_VENDOR or UsbConstants.USB_DIR_OUT
        private const val FTDI_DEVICE_IN_REQTYPE = UsbConstants.USB_TYPE_VENDOR or UsbConstants.USB_DIR_IN
        
        // FTDI requests
        private const val SIO_RESET = 0
        private const val SIO_MODEM_CTRL = 1
        private const val SIO_SET_FLOW_CTRL = 2
        private const val SIO_SET_BAUD_RATE = 3
        private const val SIO_SET_DATA = 4
        private const val SIO_GET_MODEM_STATUS = 5
        private const val SIO_SET_EVENT_CHAR = 6
        private const val SIO_SET_ERROR_CHAR = 7
        private const val SIO_SET_LATENCY_TIMER = 9
        private const val SIO_GET_LATENCY_TIMER = 10
        private const val SIO_SET_BITMODE = 11
        private const val SIO_READ_PINS = 12
        
        // Reset values
        private const val SIO_RESET_SIO = 0
        private const val SIO_RESET_PURGE_RX = 1
        private const val SIO_RESET_PURGE_TX = 2
        
        // Modem control bits
        private const val SIO_SET_DTR_MASK = 0x01
        private const val SIO_SET_DTR_HIGH = (1 or (SIO_SET_DTR_MASK shl 8))
        private const val SIO_SET_DTR_LOW = (0 or (SIO_SET_DTR_MASK shl 8))
        private const val SIO_SET_RTS_MASK = 0x02
        private const val SIO_SET_RTS_HIGH = (2 or (SIO_SET_RTS_MASK shl 8))
        private const val SIO_SET_RTS_LOW = (0 or (SIO_SET_RTS_MASK shl 8))
        
        // Modem status bits
        private const val MODEM_STATUS_CTS = 0x10
        private const val MODEM_STATUS_DSR = 0x20
        
        // Data characteristics
        private const val FTDI_DATA_BITS_7 = 7
        private const val FTDI_DATA_BITS_8 = 8
        private const val FTDI_PARITY_NONE = 0 shl 8
        private const val FTDI_PARITY_ODD = 1 shl 8
        private const val FTDI_PARITY_EVEN = 2 shl 8
        private const val FTDI_PARITY_MARK = 3 shl 8
        private const val FTDI_PARITY_SPACE = 4 shl 8
        private const val FTDI_STOP_BITS_1 = 0 shl 11
        private const val FTDI_STOP_BITS_15 = 1 shl 11
        private const val FTDI_STOP_BITS_2 = 2 shl 11
        
        // FTDI chip types
        private const val TYPE_AM = 0
        private const val TYPE_BM = 1
        private const val TYPE_2232C = 2
        private const val TYPE_R = 3
        private const val TYPE_2232H = 4
        private const val TYPE_4232H = 5
        private const val TYPE_232H = 6
        
        // Base clock frequency
        private const val FTDI_BASE_CLOCK = 3000000
        private const val FTDI_BASE_CLOCK_H = 12000000
        
        // Status bytes in read data
        private const val MODEM_STATUS_HEADER_LENGTH = 2
    }
    
    private var chipType = TYPE_R
    
    @Throws(IOException::class)
    override fun initializeChip() {
        val conn = connection ?: throw IOException("No connection")
        
        // Detect chip type based on device descriptor
        detectChipType()
        
        // Reset the device
        var result = conn.controlTransfer(
            FTDI_DEVICE_OUT_REQTYPE,
            SIO_RESET,
            SIO_RESET_SIO,
            0,
            null,
            0,
            5000
        )
        
        if (result < 0) {
            throw IOException("Failed to reset FTDI device: $result")
        }
        
        // Set latency timer to minimum for better responsiveness
        result = conn.controlTransfer(
            FTDI_DEVICE_OUT_REQTYPE,
            SIO_SET_LATENCY_TIMER,
            1, // 1ms latency
            0,
            null,
            0,
            5000
        )
        
        // Ignore latency timer errors - not all chips support it
    }
    
    private fun detectChipType() {
        // Detect chip type based on bcdDevice
        val bcdDevice = device.version
        
        chipType = when {
            bcdDevice.startsWith("2.") -> TYPE_AM
            bcdDevice.startsWith("4.") -> TYPE_BM
            bcdDevice.startsWith("5.") -> TYPE_2232C
            bcdDevice.startsWith("6.") -> TYPE_R
            bcdDevice.startsWith("7.") -> TYPE_2232H
            bcdDevice.startsWith("8.") -> TYPE_4232H
            bcdDevice.startsWith("9.") -> TYPE_232H
            else -> TYPE_R // Default to FT232R
        }
    }
    
    @Throws(IOException::class)
    override fun setParameters(
        baudRate: Int,
        dataBits: Int,
        stopBits: USBStopBits,
        parity: USBParity
    ) {
        val conn = connection ?: throw IOException("No connection")
        
        // Set baud rate
        setBaudRate(baudRate)
        
        // Build data characteristics value
        var config = dataBits
        
        config = config or when (parity) {
            USBParity.NONE -> FTDI_PARITY_NONE
            USBParity.ODD -> FTDI_PARITY_ODD
            USBParity.EVEN -> FTDI_PARITY_EVEN
            USBParity.MARK -> FTDI_PARITY_MARK
            USBParity.SPACE -> FTDI_PARITY_SPACE
        }
        
        config = config or when (stopBits) {
            USBStopBits.ONE -> FTDI_STOP_BITS_1
            USBStopBits.ONE_POINT_FIVE -> FTDI_STOP_BITS_15
            USBStopBits.TWO -> FTDI_STOP_BITS_2
        }
        
        val result = conn.controlTransfer(
            FTDI_DEVICE_OUT_REQTYPE,
            SIO_SET_DATA,
            config,
            0,
            null,
            0,
            5000
        )
        
        if (result < 0) {
            throw IOException("Failed to set data characteristics: $result")
        }
    }
    
    @Throws(IOException::class)
    private fun setBaudRate(baudRate: Int) {
        val conn = connection ?: throw IOException("No connection")
        
        // Calculate divisor
        val divisor = calculateDivisor(baudRate)
        
        val result = conn.controlTransfer(
            FTDI_DEVICE_OUT_REQTYPE,
            SIO_SET_BAUD_RATE,
            divisor and 0xFFFF,
            (divisor shr 16) and 0xFFFF,
            null,
            0,
            5000
        )
        
        if (result < 0) {
            throw IOException("Failed to set baud rate: $result")
        }
        
        currentBaudRate = baudRate
    }
    
    private fun calculateDivisor(baudRate: Int): Int {
        val baseClock = if (chipType >= TYPE_2232H) FTDI_BASE_CLOCK_H else FTDI_BASE_CLOCK
        
        // Simple divisor calculation
        // For more accurate rates, a more complex algorithm would be needed
        val divisor = baseClock / baudRate
        
        return if (divisor > 0x3FFF) {
            // Use sub-integer divisor
            val subDivisor = (baseClock * 8) / baudRate
            val encodedDivisor = subDivisor / 8
            val fracPart = subDivisor % 8
            
            val fracCode = when (fracPart) {
                0 -> 0
                1 -> 3
                2 -> 2
                3 -> 4
                4 -> 1
                5 -> 5
                6 -> 6
                7 -> 7
                else -> 0
            }
            
            (encodedDivisor and 0x3FFF) or (fracCode shl 14)
        } else {
            divisor
        }
    }
    
    @Throws(IOException::class)
    override fun setDTR(state: Boolean) {
        val conn = connection ?: throw IOException("No connection")
        
        dtr = state
        val value = if (state) SIO_SET_DTR_HIGH else SIO_SET_DTR_LOW
        
        val result = conn.controlTransfer(
            FTDI_DEVICE_OUT_REQTYPE,
            SIO_MODEM_CTRL,
            value,
            0,
            null,
            0,
            5000
        )
        
        if (result < 0) {
            throw IOException("Failed to set DTR: $result")
        }
    }
    
    @Throws(IOException::class)
    override fun setRTS(state: Boolean) {
        val conn = connection ?: throw IOException("No connection")
        
        rts = state
        val value = if (state) SIO_SET_RTS_HIGH else SIO_SET_RTS_LOW
        
        val result = conn.controlTransfer(
            FTDI_DEVICE_OUT_REQTYPE,
            SIO_MODEM_CTRL,
            value,
            0,
            null,
            0,
            5000
        )
        
        if (result < 0) {
            throw IOException("Failed to set RTS: $result")
        }
    }
    
    @Throws(IOException::class)
    override fun getCTS(): Boolean {
        return (getModemStatus() and MODEM_STATUS_CTS) != 0
    }
    
    @Throws(IOException::class)
    override fun getDSR(): Boolean {
        return (getModemStatus() and MODEM_STATUS_DSR) != 0
    }
    
    @Throws(IOException::class)
    private fun getModemStatus(): Int {
        val conn = connection ?: throw IOException("No connection")
        
        val buffer = ByteArray(2)
        val result = conn.controlTransfer(
            FTDI_DEVICE_IN_REQTYPE,
            SIO_GET_MODEM_STATUS,
            0,
            0,
            buffer,
            buffer.size,
            5000
        )
        
        if (result < 1) {
            throw IOException("Failed to get modem status: $result")
        }
        
        return buffer[0].toInt() and 0xFF
    }
    
    @Throws(IOException::class)
    override fun purgeBuffers(input: Boolean, output: Boolean) {
        val conn = connection ?: throw IOException("No connection")
        
        if (input) {
            conn.controlTransfer(
                FTDI_DEVICE_OUT_REQTYPE,
                SIO_RESET,
                SIO_RESET_PURGE_RX,
                0,
                null,
                0,
                5000
            )
        }
        
        if (output) {
            conn.controlTransfer(
                FTDI_DEVICE_OUT_REQTYPE,
                SIO_RESET,
                SIO_RESET_PURGE_TX,
                0,
                null,
                0,
                5000
            )
        }
        
        super.purgeBuffers(input, output)
    }
    
    /**
     * FTDI chips include 2 status bytes at the beginning of each read packet.
     * This method strips those bytes.
     */
    override fun processReadData(buffer: ByteArray, count: Int): Int {
        if (count <= MODEM_STATUS_HEADER_LENGTH) {
            return 0
        }
        
        // Strip modem status bytes
        val dataCount = count - MODEM_STATUS_HEADER_LENGTH
        System.arraycopy(buffer, MODEM_STATUS_HEADER_LENGTH, buffer, 0, dataCount)
        
        return dataCount
    }
}
