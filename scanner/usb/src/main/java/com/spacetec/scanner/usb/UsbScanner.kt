package com.spacetec.scanner.usb

import android.content.Context
import android.hardware.usb.*
import com.spacetec.scanner.core.*
import kotlinx.coroutines.*

class UsbScanner(private val context: Context) : BaseScanner(ScannerType.USB) {
    
    private val usbManager by lazy { context.getSystemService(Context.USB_SERVICE) as UsbManager }
    private var connection: UsbDeviceConnection? = null
    private var endpoint: UsbEndpoint? = null
    private var endpointOut: UsbEndpoint? = null
    
    // Common OBD USB VID/PIDs
    private val obdDevices = listOf(
        0x0403 to 0x6001, // FTDI
        0x067B to 0x2303, // Prolific
        0x10C4 to 0xEA60  // CP210x
    )
    
    override suspend fun startScan() = withContext(Dispatchers.IO) {
        _state.value = ConnectionState.Scanning
        val found = usbManager.deviceList.values.filter { device ->
            obdDevices.any { it.first == device.vendorId && it.second == device.productId }
        }.map { device ->
            ScannerDevice(device.deviceName, device.productName ?: "USB OBD", ScannerType.USB, device.deviceName)
        }
        _devices.value = found
        _state.value = ConnectionState.Disconnected
    }
    
    override suspend fun stopScan() { _state.value = ConnectionState.Disconnected }
    
    override suspend fun connect(device: ScannerDevice) = withContext(Dispatchers.IO) {
        _state.value = ConnectionState.Connecting
        val usbDevice = usbManager.deviceList[device.address]
        if (usbDevice == null || !usbManager.hasPermission(usbDevice)) {
            _state.value = ConnectionState.Error("No permission")
            return@withContext false
        }
        try {
            connection = usbManager.openDevice(usbDevice)
            val intf = usbDevice.getInterface(0)
            connection?.claimInterface(intf, true)
            for (i in 0 until intf.endpointCount) {
                val ep = intf.getEndpoint(i)
                if (ep.direction == UsbConstants.USB_DIR_IN) endpoint = ep
                else endpointOut = ep
            }
            _state.value = ConnectionState.Connected(device)
            true
        } catch (e: Exception) {
            _state.value = ConnectionState.Error(e.message ?: "Failed")
            false
        }
    }
    
    override suspend fun disconnect() {
        connection?.close()
        connection = null
        _state.value = ConnectionState.Disconnected
    }
    
    override suspend fun send(data: ByteArray) = withContext(Dispatchers.IO) {
        try {
            connection?.let { conn ->
                endpointOut?.let { out -> conn.bulkTransfer(out, data + '\r'.code.toByte(), data.size + 1, 1000) }
                delay(100)
                endpoint?.let { inp ->
                    val buffer = ByteArray(1024)
                    val len = conn.bulkTransfer(inp, buffer, buffer.size, 1000)
                    if (len > 0) buffer.copyOf(len) else null
                }
            }
        } catch (_: Exception) { null }
    }
}
