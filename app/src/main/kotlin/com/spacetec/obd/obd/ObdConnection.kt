package com.spacetec.obd.obd

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

@SuppressLint("MissingPermission")
class ObdConnection(private val context: Context) {

    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var currentDevice: BluetoothDevice? = null

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _connectionStatus = MutableStateFlow("")
    val connectionStatus: StateFlow<String> = _connectionStatus

    private val _currentProtocol = MutableStateFlow("AUTO")
    val currentProtocol: StateFlow<String> = _currentProtocol

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    }

    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Connecting : ConnectionState()
        data class Connected(val deviceName: String) : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }

    fun getPairedDevices(): List<BluetoothDevice> {
        return try {
            bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "getPairedDevices error: ${e.message}")
            emptyList()
        }
    }

    suspend fun connect(device: BluetoothDevice): Boolean = withContext(Dispatchers.IO) {
        try {
            currentDevice = device
            _connectionState.value = ConnectionState.Connecting

            for (attempt in 1..MAX_RETRIES) {
                updateStatus("Connection attempt $attempt/$MAX_RETRIES")
                
                if (tryAllConnectionStrategies(device)) {
                    if (initializeElm327()) {
                        _connectionState.value = ConnectionState.Connected(device.name ?: "OBD-II")
                        return@withContext true
                    }
                }
                
                if (attempt < MAX_RETRIES) {
                    updateStatus("Retrying in 1 second...")
                    delay(1000)
                }
            }

            _connectionState.value = ConnectionState.Error("Failed after $MAX_RETRIES attempts")
            false
        } catch (e: Exception) {
            Log.e(TAG, "connect error: ${e.message}", e)
            _connectionState.value = ConnectionState.Error(e.message ?: "Connection failed")
            false
        }
    }

    private suspend fun tryAllConnectionStrategies(device: BluetoothDevice): Boolean {
        bluetoothAdapter?.cancelDiscovery()

        // Strategy 1: Standard secure RFCOMM
        updateStatus("Trying secure RFCOMM...")
        if (trySecureConnection(device)) return true

        // Strategy 2: Insecure RFCOMM
        updateStatus("Trying insecure RFCOMM...")
        if (tryInsecureConnection(device)) return true

        // Strategy 3: Reflection with channel scanning
        updateStatus("Trying reflection method...")
        if (tryReflectionConnection(device)) return true

        // Strategy 4: Alternative UUIDs
        for ((index, uuid) in ALTERNATIVE_UUIDS.withIndex()) {
            updateStatus("Trying alternative UUID ${index + 1}/${ALTERNATIVE_UUIDS.size}...")
            if (tryConnectionWithUUID(device, uuid)) return true
        }

        return false
    }

    private suspend fun trySecureConnection(device: BluetoothDevice): Boolean {
        return trySocket {
            device.createRfcommSocketToServiceRecord(SPP_UUID)
        }
    }

    private suspend fun tryInsecureConnection(device: BluetoothDevice): Boolean {
        return trySocket {
            device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
        }
    }

    private suspend fun tryReflectionConnection(device: BluetoothDevice): Boolean {
        // Try channels 1-10
        for (channel in 1..10) {
            try {
                val method = device.javaClass.getMethod("createRfcommSocket", Int::class.java)
                if (trySocket { method.invoke(device, channel) as? BluetoothSocket }) {
                    Log.d(TAG, "Reflection succeeded on channel $channel")
                    return true
                }
            } catch (e: Exception) {
                closeSocket()
            }
        }

        // Try insecure reflection
        try {
            val method = device.javaClass.getMethod("createInsecureRfcommSocket", Int::class.java)
            if (trySocket { method.invoke(device, 1) as? BluetoothSocket }) {
                Log.d(TAG, "Insecure reflection succeeded")
                return true
            }
        } catch (e: Exception) {
            closeSocket()
        }

        return false
    }

    private suspend fun tryConnectionWithUUID(device: BluetoothDevice, uuid: UUID): Boolean {
        return trySocket {
            device.createRfcommSocketToServiceRecord(uuid)
        }
    }

    private suspend fun trySocket(createSocket: () -> BluetoothSocket?): Boolean {
        closeSocket()
        return try {
            withTimeoutOrNull(CONNECTION_TIMEOUT) {
                try {
                    socket = createSocket()
                    socket?.connect()
                    inputStream = socket?.inputStream
                    outputStream = socket?.outputStream
                    socket?.isConnected == true
                } catch (e: Exception) {
                    Log.d(TAG, "Socket failed: ${e.message}")
                    closeSocket()
                    false
                }
            } ?: false
        } catch (e: Exception) {
            Log.e(TAG, "trySocket error: ${e.message}")
            closeSocket()
            false
        }
    }

    private suspend fun initializeElm327(): Boolean {
        updateStatus("Initializing ELM327...")

        // Reset adapter
        val resetResponse = sendCommandWithTimeout("ATZ", 2000)
        delay(1000)
        clearInputBuffer()

        // Get version to verify connection
        val version = sendCommandWithTimeout("ATI", 1500)
        if (!isValidElmResponse(version)) {
            updateStatus("Invalid adapter response")
            return false
        }
        updateStatus("Adapter: ${version.take(30)}")

        // Configure adapter
        sendCommandWithTimeout("ATE0", 500)   // Echo off
        sendCommandWithTimeout("ATL0", 500)   // Linefeeds off
        sendCommandWithTimeout("ATS0", 500)   // Spaces off
        sendCommandWithTimeout("ATH1", 500)   // Headers on (for multi-ECU)
        sendCommandWithTimeout("ATAT1", 500)  // Adaptive timing
        sendCommandWithTimeout("ATST FF", 500) // Max timeout

        // Detect protocol
        updateStatus("Detecting vehicle protocol...")
        if (!detectProtocol()) {
            updateStatus("Failed to detect protocol")
            return false
        }

        updateStatus("Connected successfully")
        return true
    }

    private suspend fun detectProtocol(): Boolean {
        // Try auto protocol first
        sendCommandWithTimeout("ATSP0", 500)
        val autoResponse = sendCommandWithTimeout("0100", 5000)

        if (isSuccessfulObdResponse(autoResponse)) {
            val protocol = sendCommandWithTimeout("ATDP", 500)
            Log.d(TAG, "Auto protocol: $protocol")
            _currentProtocol.value = "AUTO"
            return true
        }

        // Try VW-preferred protocols in order
        for (protocol in VW_PROTOCOL_PRIORITY) {
            updateStatus("Trying ${protocol.description}...")
            sendCommandWithTimeout("ATSP${protocol.code}", 500)
            
            // KWP protocols need slow init
            if (protocol.code in 3..5) {
                sendCommandWithTimeout("ATSI", 5000)
            }
            
            val response = sendCommandWithTimeout("0100", 5000)
            if (isSuccessfulObdResponse(response)) {
                Log.d(TAG, "Protocol ${protocol.description} succeeded")
                _currentProtocol.value = mapProtocolCodeToId(protocol.code)
                return true
            }
        }

        return false
    }

    private fun isValidElmResponse(response: String): Boolean {
        val upper = response.uppercase()
        return upper.contains("ELM") || upper.contains("OBD") || 
               upper.contains("V1.") || upper.contains("V2.") ||
               upper.contains("STN") || upper.contains("OBDLINK")
    }

    private fun isSuccessfulObdResponse(response: String): Boolean {
        val upper = response.uppercase()
        if (upper.contains("NO DATA") || upper.contains("UNABLE") ||
            upper.contains("ERROR") || upper.contains("?") ||
            upper.contains("CAN ERROR") || upper.contains("BUS ERROR")) {
            return false
        }
        return upper.contains("41 00") || upper.contains("4100") ||
               upper.matches(Regex(".*[0-9A-F]{8,}.*"))
    }

    private fun mapProtocolCodeToId(code: Int): String {
        return when (code) {
            0 -> "AUTO"
            1 -> "SAE_J1850_PWM"
            2 -> "SAE_J1850_VPW"
            3 -> "ISO_9141_2"
            4 -> "ISO_14230_4"  // KWP2000 5-baud init
            5 -> "ISO_14230_4"  // KWP2000 fast init
            6 -> "ISO_15765_4"  // CAN 11-bit
            7 -> "ISO_15765_4_29"  // CAN 29-bit
            8 -> "ISO_15765_4"  // CAN 11-bit (250k)
            9 -> "ISO_15765_4_29"  // CAN 29-bit (250k)
            else -> "AUTO"
        }
    }

    private fun mapProtocolIdToCode(protocolId: String): Int {
        return when (protocolId) {
            "AUTO" -> 0
            "ISO_9141_2" -> 3
            "ISO_14230_4" -> 5  // KWP2000 fast init
            "ISO_15765_4" -> 6  // 11-bit CAN
            "ISO_15765_4_29" -> 7  // 29-bit CAN
            "SAE_J1850_PWM" -> 1
            "SAE_J1850_VPW" -> 2
            else -> 0  // Default to AUTO
        }
    }

    suspend fun sendCommand(command: String): String = sendCommandWithTimeout(command, 2000)

    private suspend fun sendCommandWithTimeout(command: String, timeout: Long): String = 
        withContext(Dispatchers.IO) {
            try {
                clearInputBuffer()
                outputStream?.write("$command\r".toByteArray())
                outputStream?.flush()
                readResponseWithTimeout(timeout)
            } catch (e: Exception) {
                Log.e(TAG, "Command error: ${e.message}")
                "ERROR"
            }
        }

    private suspend fun readResponseWithTimeout(timeout: Long): String {
        val response = StringBuilder()
        val startTime = System.currentTimeMillis()

        while (System.currentTimeMillis() - startTime < timeout) {
            val available = inputStream?.available() ?: 0
            if (available > 0) {
                val buffer = ByteArray(available)
                val read = inputStream?.read(buffer) ?: 0
                if (read > 0) {
                    val chunk = String(buffer, 0, read)
                    response.append(chunk)
                    if (chunk.contains(">")) break
                }
            } else {
                delay(20)
            }
        }

        return response.toString()
            .replace(">", "")
            .replace("\r", " ")
            .replace("\n", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun clearInputBuffer() {
        try {
            while ((inputStream?.available() ?: 0) > 0) {
                inputStream?.read()
            }
        } catch (e: Exception) { }
    }

    private fun closeSocket() {
        runCatching { inputStream?.close() }
        runCatching { outputStream?.close() }
        runCatching { socket?.close() }
        inputStream = null
        outputStream = null
        socket = null
    }

    fun disconnect() {
        closeSocket()
        currentDevice = null
        _connectionState.value = ConnectionState.Disconnected
        _connectionStatus.value = ""
    }

    suspend fun setProtocol(protocolId: String): Boolean {
        if (!isConnected()) return false

        val protocolCode = mapProtocolIdToCode(protocolId)
        val response = sendCommandWithTimeout("ATSP$protocolCode", 500)

        if (!response.contains("OK")) {
            Log.e(TAG, "Failed to set protocol: $response")
            return false
        }

        _currentProtocol.value = protocolId
        Log.d(TAG, "Protocol set to: $protocolId")
        return true
    }

    fun isConnected(): Boolean = socket?.isConnected == true

    private fun updateStatus(status: String) {
        _connectionStatus.value = status
        Log.d(TAG, status)
    }

    enum class Protocol(val code: Int, val description: String) {
        CAN_11BIT_500K(6, "CAN 11-bit 500k"),
        CAN_29BIT_500K(7, "CAN 29-bit 500k"),
        KWP_FAST(5, "KWP2000 Fast"),
        KWP_5BAUD(4, "KWP2000 5-baud"),
        ISO_9141(3, "ISO 9141-2"),
        CAN_11BIT_250K(8, "CAN 11-bit 250k"),
        CAN_29BIT_250K(9, "CAN 29-bit 250k")
    }

    companion object {
        private const val TAG = "ObdConnection"
        private const val MAX_RETRIES = 3
        private const val CONNECTION_TIMEOUT = 10000L

        private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private val ALTERNATIVE_UUIDS = arrayOf(
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"),
            UUID.fromString("0000110E-0000-1000-8000-00805F9B34FB"),
            UUID.fromString("0000112F-0000-1000-8000-00805F9B34FB"),
            UUID.fromString("00001112-0000-1000-8000-00805F9B34FB")
        )

        // VW vehicles typically use CAN 500k or KWP2000
        val VW_PROTOCOL_PRIORITY = arrayOf(
            Protocol.CAN_11BIT_500K,
            Protocol.CAN_29BIT_500K,
            Protocol.KWP_FAST,
            Protocol.KWP_5BAUD,
            Protocol.ISO_9141,
            Protocol.CAN_11BIT_250K,
            Protocol.CAN_29BIT_250K
        )
    }
}
