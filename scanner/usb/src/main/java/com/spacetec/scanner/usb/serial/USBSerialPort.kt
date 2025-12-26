/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 *
 * This file is part of the SpaceTec professional automotive diagnostic system.
 */

package com.spacetec.obd.scanner.usb.serial

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import com.spacetec.obd.scanner.usb.USBConstants
import com.spacetec.obd.scanner.usb.USBDriverType
import com.spacetec.obd.scanner.usb.USBFlowControl
import com.spacetec.obd.scanner.usb.USBParity
import com.spacetec.obd.scanner.usb.USBSerialConfig
import com.spacetec.obd.scanner.usb.USBStopBits
import java.io.Closeable
import java.io.IOException

/**
 * Abstract USB serial port interface.
 *
 * Provides a common interface for different USB-to-serial chip drivers.
 * Implementations handle chip-specific initialization and communication.
 */
interface USBSerialPort : Closeable {
    
    /**
     * The USB device this port is connected to.
     */
    val device: UsbDevice
    
    /**
     * The driver type for this port.
     */
    val driverType: USBDriverType
    
    /**
     * Whether the port is currently open.
     */
    val isOpen: Boolean
    
    /**
     * Opens the serial port with the given configuration.
     *
     * @param connection USB device connection
     * @param config Serial port configuration
     * @throws IOException if opening fails
     */
    @Throws(IOException::class)
    fun open(connection: UsbDeviceConnection, config: USBSerialConfig)
    
    /**
     * Closes the serial port.
     */
    override fun close()
    
    /**
     * Reads data from the serial port.
     *
     * @param buffer Buffer to read into
     * @param timeout Read timeout in milliseconds
     * @return Number of bytes read, or -1 if no data available
     * @throws IOException if read fails
     */
    @Throws(IOException::class)
    fun read(buffer: ByteArray, timeout: Int): Int
    
    /**
     * Writes data to the serial port.
     *
     * @param data Data to write
     * @param timeout Write timeout in milliseconds
     * @return Number of bytes written
     * @throws IOException if write fails
     */
    @Throws(IOException::class)
    fun write(data: ByteArray, timeout: Int): Int
    
    /**
     * Sets the serial port parameters.
     *
     * @param baudRate Baud rate
     * @param dataBits Data bits (5, 6, 7, or 8)
     * @param stopBits Stop bits
     * @param parity Parity setting
     * @throws IOException if configuration fails
     */
    @Throws(IOException::class)
    fun setParameters(
        baudRate: Int,
        dataBits: Int,
        stopBits: USBStopBits,
        parity: USBParity
    )
    
    /**
     * Sets the DTR (Data Terminal Ready) signal.
     *
     * @param state DTR state
     * @throws IOException if setting fails
     */
    @Throws(IOException::class)
    fun setDTR(state: Boolean)
    
    /**
     * Sets the RTS (Request To Send) signal.
     *
     * @param state RTS state
     * @throws IOException if setting fails
     */
    @Throws(IOException::class)
    fun setRTS(state: Boolean)
    
    /**
     * Gets the CTS (Clear To Send) signal state.
     *
     * @return CTS state
     * @throws IOException if reading fails
     */
    @Throws(IOException::class)
    fun getCTS(): Boolean
    
    /**
     * Gets the DSR (Data Set Ready) signal state.
     *
     * @return DSR state
     * @throws IOException if reading fails
     */
    @Throws(IOException::class)
    fun getDSR(): Boolean
    
    /**
     * Purges the input and output buffers.
     *
     * @param input Whether to purge input buffer
     * @param output Whether to purge output buffer
     * @throws IOException if purge fails
     */
    @Throws(IOException::class)
    fun purgeBuffers(input: Boolean, output: Boolean)
    
    /**
     * Gets the number of bytes available to read.
     *
     * @return Number of bytes available
     */
    fun available(): Int
}

/**
 * Base implementation of USB serial port.
 *
 * Provides common functionality for all USB serial drivers.
 */
abstract class BaseUSBSerialPort(
    override val device: UsbDevice,
    override val driverType: USBDriverType
) : USBSerialPort {
    
    protected var connection: UsbDeviceConnection? = null
    protected var usbInterface: UsbInterface? = null
    protected var readEndpoint: UsbEndpoint? = null
    protected var writeEndpoint: UsbEndpoint? = null
    
    private var _isOpen = false
    override val isOpen: Boolean get() = _isOpen
    
    protected val readBuffer = ByteArray(USBConstants.READ_BUFFER_SIZE)
    protected var readBufferPosition = 0
    protected var readBufferCount = 0
    
    @Throws(IOException::class)
    override fun open(connection: UsbDeviceConnection, config: USBSerialConfig) {
        if (_isOpen) {
            throw IOException("Port already open")
        }
        
        this.connection = connection
        
        try {
            // Find and claim interface
            findInterface()
            
            // Find endpoints
            findEndpoints()
            
            // Initialize the chip
            initializeChip()
            
            // Set parameters
            setParameters(
                config.baudRate,
                config.dataBits,
                config.stopBits,
                config.parity
            )
            
            // Set control signals
            setDTR(config.dtr)
            setRTS(config.rts)
            
            _isOpen = true
            
        } catch (e: Exception) {
            close()
            throw IOException("Failed to open port: ${e.message}", e)
        }
    }
    
    override fun close() {
        _isOpen = false
        
        try {
            usbInterface?.let { iface ->
                connection?.releaseInterface(iface)
            }
        } catch (_: Exception) {}
        
        usbInterface = null
        readEndpoint = null
        writeEndpoint = null
        connection = null
        
        readBufferPosition = 0
        readBufferCount = 0
    }
    
    @Throws(IOException::class)
    override fun read(buffer: ByteArray, timeout: Int): Int {
        if (!_isOpen) {
            throw IOException("Port not open")
        }
        
        val conn = connection ?: throw IOException("Connection lost")
        val endpoint = readEndpoint ?: throw IOException("Read endpoint not available")
        
        // Check internal buffer first
        if (readBufferCount > 0) {
            val toCopy = minOf(readBufferCount, buffer.size)
            System.arraycopy(readBuffer, readBufferPosition, buffer, 0, toCopy)
            readBufferPosition += toCopy
            readBufferCount -= toCopy
            return toCopy
        }
        
        // Read from USB
        val bytesRead = conn.bulkTransfer(
            endpoint,
            readBuffer,
            readBuffer.size,
            timeout
        )
        
        if (bytesRead <= 0) {
            return bytesRead
        }
        
        // Process received data (subclasses may override for chip-specific handling)
        val processedCount = processReadData(readBuffer, bytesRead)
        
        if (processedCount <= 0) {
            return 0
        }
        
        val toCopy = minOf(processedCount, buffer.size)
        System.arraycopy(readBuffer, 0, buffer, 0, toCopy)
        
        if (processedCount > toCopy) {
            readBufferPosition = toCopy
            readBufferCount = processedCount - toCopy
        }
        
        return toCopy
    }
    
    @Throws(IOException::class)
    override fun write(data: ByteArray, timeout: Int): Int {
        if (!_isOpen) {
            throw IOException("Port not open")
        }
        
        val conn = connection ?: throw IOException("Connection lost")
        val endpoint = writeEndpoint ?: throw IOException("Write endpoint not available")
        
        var offset = 0
        var totalWritten = 0
        
        while (offset < data.size) {
            val chunkSize = minOf(endpoint.maxPacketSize, data.size - offset)
            val chunk = if (offset == 0 && chunkSize == data.size) {
                data
            } else {
                data.copyOfRange(offset, offset + chunkSize)
            }
            
            val written = conn.bulkTransfer(endpoint, chunk, chunk.size, timeout)
            
            if (written < 0) {
                throw IOException("Write failed: $written")
            }
            
            offset += written
            totalWritten += written
            
            if (written < chunkSize) {
                break // Partial write
            }
        }
        
        return totalWritten
    }
    
    override fun available(): Int {
        return readBufferCount
    }
    
    @Throws(IOException::class)
    override fun purgeBuffers(input: Boolean, output: Boolean) {
        if (input) {
            readBufferPosition = 0
            readBufferCount = 0
            
            // Drain USB buffer
            val conn = connection
            val endpoint = readEndpoint
            if (conn != null && endpoint != null) {
                val tempBuffer = ByteArray(USBConstants.READ_BUFFER_SIZE)
                while (true) {
                    val read = conn.bulkTransfer(endpoint, tempBuffer, tempBuffer.size, 10)
                    if (read <= 0) break
                }
            }
        }
    }
    
    /**
     * Finds and claims the USB interface.
     */
    @Throws(IOException::class)
    protected open fun findInterface() {
        val conn = connection ?: throw IOException("No connection")
        
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            if (conn.claimInterface(iface, true)) {
                usbInterface = iface
                return
            }
        }
        
        throw IOException("Could not claim any interface")
    }
    
    /**
     * Finds the read and write endpoints.
     */
    @Throws(IOException::class)
    protected open fun findEndpoints() {
        val iface = usbInterface ?: throw IOException("No interface")
        
        for (i in 0 until iface.endpointCount) {
            val endpoint = iface.getEndpoint(i)
            
            when (endpoint.type) {
                android.hardware.usb.UsbConstants.USB_ENDPOINT_XFER_BULK -> {
                    if (endpoint.direction == android.hardware.usb.UsbConstants.USB_DIR_IN) {
                        readEndpoint = endpoint
                    } else {
                        writeEndpoint = endpoint
                    }
                }
            }
        }
        
        if (readEndpoint == null || writeEndpoint == null) {
            throw IOException("Could not find bulk endpoints")
        }
    }
    
    /**
     * Initializes the USB-to-serial chip.
     * Subclasses must implement chip-specific initialization.
     */
    @Throws(IOException::class)
    protected abstract fun initializeChip()
    
    /**
     * Processes read data for chip-specific handling.
     * Default implementation returns data as-is.
     *
     * @param buffer Buffer containing read data
     * @param count Number of bytes read
     * @return Number of valid data bytes after processing
     */
    protected open fun processReadData(buffer: ByteArray, count: Int): Int {
        return count
    }
}
