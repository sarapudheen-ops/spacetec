package com.spacetec.scanner.wifi

import com.spacetec.scanner.core.*
import kotlinx.coroutines.*
import java.net.InetSocketAddress
import java.net.Socket

class WifiScanner : BaseScanner(ScannerType.WIFI) {
    
    private var socket: Socket? = null
    private val defaultPort = 35000
    private val commonIPs = listOf("192.168.0.10", "192.168.4.1", "192.168.1.10")
    
    override suspend fun startScan() = withContext(Dispatchers.IO) {
        _state.value = ConnectionState.Scanning
        val found = mutableListOf<ScannerDevice>()
        commonIPs.forEach { ip ->
            try {
                Socket().use { s ->
                    s.connect(InetSocketAddress(ip, defaultPort), 500)
                    found.add(ScannerDevice(ip, "WiFi OBD ($ip)", ScannerType.WIFI, ip, -50))
                }
            } catch (_: Exception) {}
        }
        _devices.value = found
        _state.value = ConnectionState.Disconnected
    }
    
    override suspend fun stopScan() { _state.value = ConnectionState.Disconnected }
    
    override suspend fun connect(device: ScannerDevice) = withContext(Dispatchers.IO) {
        _state.value = ConnectionState.Connecting
        try {
            socket = Socket().apply {
                soTimeout = 5000
                connect(InetSocketAddress(device.address, defaultPort), 3000)
            }
            _state.value = ConnectionState.Connected(device)
            true
        } catch (e: Exception) {
            _state.value = ConnectionState.Error(e.message ?: "Connection failed")
            false
        }
    }
    
    override suspend fun disconnect() {
        socket?.close()
        socket = null
        _state.value = ConnectionState.Disconnected
    }
    
    override suspend fun send(data: ByteArray) = withContext(Dispatchers.IO) {
        try {
            socket?.let { s ->
                s.getOutputStream().write(data + '\r'.code.toByte())
                s.getOutputStream().flush()
                delay(100)
                val buffer = ByteArray(1024)
                val len = s.getInputStream().read(buffer)
                if (len > 0) buffer.copyOf(len) else null
            }
        } catch (_: Exception) { null }
    }
}
