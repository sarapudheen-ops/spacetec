// scanner/core/src/main/kotlin/com/spacetec/automotive/scanner/core/elm327/Elm327Adapter.kt
package com.spacetec.obd.scanner.core.elm327

import com.spacetec.obd.core.common.extension.toHexString
import com.spacetec.obd.core.common.result.AppResult
import com.spacetec.obd.core.common.result.Result
import com.spacetec.obd.core.common.result.SpaceTecError
import com.spacetec.core.common.transport.DiagnosticTransport
import com.spacetec.transport.contract.Protocol
import com.spacetec.transport.contract.ProtocolConfig
import com.spacetec.transport.contract.ProtocolType
import com.spacetec.obd.scanner.core.Scanner
import com.spacetec.obd.scanner.core.ScannerCapabilities
import com.spacetec.obd.scanner.core.ScannerConnectionConfig
import com.spacetec.obd.scanner.core.ScannerConnectionState
import com.spacetec.obd.scanner.core.ScannerDeviceInfo
import com.spacetec.obd.scanner.core.ScannerType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * Abstract adapter for ELM327-compatible OBD-II interfaces.
 * 
 * This class handles the ELM327 AT command protocol and provides
 * a common implementation for Bluetooth, WiFi, and USB ELM327 devices.
 * Subclasses implement the transport-specific connection logic.
 */
abstract class Elm327Adapter : Scanner, DiagnosticTransport {
    
    // ========================================================================
    // ABSTRACT PROPERTIES - To be implemented by subclasses
    // ========================================================================
    
    abstract override val id: String
    abstract override val name: String
    abstract override val type: ScannerType
    
    /**
     * Establishes the physical connection to the device.
     */
    protected abstract suspend fun openConnection(): AppResult<Unit>
    
    /**
     * Closes the physical connection.
     */
    protected abstract suspend fun closeConnection()
    
    /**
     * Writes raw bytes to the device.
     */
    protected abstract suspend fun writeBytes(data: ByteArray): AppResult<Unit>
    
    /**
     * Reads raw bytes from the device.
     */
    protected abstract suspend fun readBytes(timeout: Long): AppResult<ByteArray>
    
    /**
     * Observes incoming bytes as a flow.
     */
    protected abstract fun observeBytes(): Flow<ByteArray>
    
    // ========================================================================
    // STATE MANAGEMENT
    // ========================================================================
    
    protected val _connectionState = MutableStateFlow<ScannerConnectionState>(
        ScannerConnectionState.Disconnected
    )
    override val connectionState: StateFlow<ScannerConnectionState> = 
        _connectionState.asStateFlow()
    
    protected val _deviceInfo = MutableStateFlow<ScannerDeviceInfo?>(null)
    override val deviceInfo: StateFlow<ScannerDeviceInfo?> = _deviceInfo.asStateFlow()
    
    override val isConnected: Boolean
        get() = connectionState.value.isConnected
    
    override val capabilities: ScannerCapabilities = ScannerCapabilities.ELM327_BASIC
    
    private var currentProtocol: Protocol? = null
    private var connectionConfig: ScannerConnectionConfig = ScannerConnectionConfig()
    
    private val commandMutex = Mutex()
    private val responseBuffer = StringBuilder()
    
    // ========================================================================
    // CONNECTION MANAGEMENT
    // ========================================================================
    
    override suspend fun connect(config: ScannerConnectionConfig): AppResult<Unit> {
        if (isConnected) {
            return Result.success(Unit)
        }
        
        connectionConfig = config
        _connectionState.value = ScannerConnectionState.Connecting(name)
        
        return try {
            // Open physical connection
            val openResult = openConnection()
            if (openResult.isFailure) {
                _connectionState.value = ScannerConnectionState.Error(
                    message = openResult.errorOrNull()?.message ?: "Connection failed"
                )
                return openResult
            }
            
            // Initialize ELM327
            _connectionState.value = ScannerConnectionState.Initializing(name)
            val initResult = initializeElm327()
            
            if (initResult.isSuccess) {
                _connectionState.value = ScannerConnectionState.Connected(
                    deviceName = name,
                    protocol = null
                )
                Timber.i("Connected to ELM327: $name")
                Result.success(Unit)
            } else {
                closeConnection()
                _connectionState.value = ScannerConnectionState.Error(
                    message = initResult.errorOrNull()?.message ?: "Initialization failed"
                )
                initResult
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Connection error")
            _connectionState.value = ScannerConnectionState.Error(
                message = e.message ?: "Unknown error",
                cause = e
            )
            Result.failure(SpaceTecError.ConnectionError.ConnectionFailed(
                reason = e.message
            ))
        }
    }
    
    override suspend fun disconnect() {
        try {
            currentProtocol = null
            closeConnection()
        } finally {
            _connectionState.value = ScannerConnectionState.Disconnected
            _deviceInfo.value = null
            Timber.i("Disconnected from $name")
        }
    }
    
    // ========================================================================
    // ELM327 INITIALIZATION
    // ========================================================================
    
    private suspend fun initializeElm327(): AppResult<Unit> {
        // Reset the adapter
        var response = sendAtCommand(Elm327Commands.RESET, timeout = 3000)
        if (response.isFailure) {
            return Result.failure(response.exceptionOrNull() ?: Exception("Unknown error"))
        }
        
        // Small delay after reset
        delay(500)
        
        // Disable echo
        response = sendAtCommand(Elm327Commands.ECHO_OFF)
        if (response.isFailure) {
            return Result.failure(response.exceptionOrNull() ?: Exception("Unknown error"))
        }
        
        // Disable linefeeds
        sendAtCommand(Elm327Commands.LINEFEED_OFF)
        
        // Disable spaces in responses
        sendAtCommand(Elm327Commands.SPACES_OFF)
        
        // Enable headers (we need them for multi-ECU)
        sendAtCommand(Elm327Commands.HEADERS_ON)
        
        // Set adaptive timing
        sendAtCommand(Elm327Commands.ADAPTIVE_TIMING_2)
        
        // Get device information
        val deviceIdResult = sendAtCommand(Elm327Commands.DEVICE_ID)
        val voltageResult = sendAtCommand(Elm327Commands.READ_VOLTAGE)
        
        val firmwareVersion = deviceIdResult.getOrNull()?.trim() ?: "Unknown"
        val voltage = parseVoltage(voltageResult.getOrNull() ?: "")
        
        _deviceInfo.value = ScannerDeviceInfo(
            deviceName = name,
            deviceAddress = id,
            firmwareVersion = firmwareVersion,
            chipType = if (firmwareVersion.contains("ELM327")) "ELM327" else "Unknown",
            voltage = voltage
        )
        
        Timber.i("ELM327 initialized: $firmwareVersion, ${voltage}V")
        
        return Result.success(Unit)
    }
    
    private fun parseVoltage(response: String): Float {
        return try {
            response.replace("V", "").trim().toFloat()
        } catch (e: Exception) {
            0f
        }
    }
    
    // ========================================================================
    // VEHICLE INITIALIZATION
    // ========================================================================
    
    override suspend fun initializeVehicle(protocolConfig: ProtocolConfig): AppResult<Protocol> {
        if (!isConnected) {
            return Result.failure(SpaceTecError.ConnectionError.ConnectionLost())
        }
        
        // Set protocol
        val protocolNumber = when (protocolConfig.protocolType) {
            ProtocolType.AUTO -> Elm327Commands.ProtocolNumber.AUTO
            ProtocolType.ISO_15765_4_CAN_11BIT_500K -> Elm327Commands.ProtocolNumber.ISO_15765_4_CAN_11BIT_500K
            ProtocolType.ISO_15765_4_CAN_29BIT_500K -> Elm327Commands.ProtocolNumber.ISO_15765_4_CAN_29BIT_500K
            ProtocolType.ISO_15765_4_CAN_11BIT_250K -> Elm327Commands.ProtocolNumber.ISO_15765_4_CAN_11BIT_250K
            ProtocolType.ISO_15765_4_CAN_29BIT_250K -> Elm327Commands.ProtocolNumber.ISO_15765_4_CAN_29BIT_250K
            ProtocolType.ISO_14230_4_KWP_FAST -> Elm327Commands.ProtocolNumber.ISO_14230_4_KWP_FAST
            ProtocolType.ISO_14230_4_KWP -> Elm327Commands.ProtocolNumber.ISO_14230_4_KWP_5BAUD
            ProtocolType.ISO_9141_2 -> Elm327Commands.ProtocolNumber.ISO_9141_2
            ProtocolType.SAE_J1850_PWM -> Elm327Commands.ProtocolNumber.SAE_J1850_PWM
            ProtocolType.SAE_J1850_VPW -> Elm327Commands.ProtocolNumber.SAE_J1850_VPW
            else -> Elm327Commands.ProtocolNumber.AUTO
        }
        
        val setProtocolResult = sendAtCommand(Elm327Commands.setProtocol(protocolNumber))
        if (setProtocolResult.isFailure) {
            return Result.failure(setProtocolResult.errorOrNull()!!)
        }
        
        // Try a test command to verify vehicle connection
        val testResult = sendObdCommand("0100") // Supported PIDs request
        if (testResult.isFailure) {
            return Result.failure(SpaceTecError.ConnectionError.ConnectionFailed(
                reason = "Cannot communicate with vehicle"
            ))
        }
        
        val testResponse = testResult.getOrNull() ?: ""
        if (Elm327Commands.Response.isError(testResponse)) {
            return Result.failure(SpaceTecError.ProtocolError.NoResponse(
                message = testResponse
            ))
        }
        
        // Get detected protocol
        val protocolDescResult = sendAtCommand(Elm327Commands.DESCRIBE_PROTOCOL_NUMBER)
        val detectedProtocol = parseProtocolNumber(protocolDescResult.getOrNull() ?: "")
        
        // Create and configure protocol
        val protocol = createProtocol(detectedProtocol)
        protocol.setTransport(this)
        
        val initResult = protocol.initialize(protocolConfig)
        if (initResult.isSuccess) {
            currentProtocol = protocol
            _connectionState.value = ScannerConnectionState.Connected(
                deviceName = name,
                protocol = detectedProtocol
            )
        }
        
        return initResult.map { protocol }
    }
    
    private fun parseProtocolNumber(response: String): ProtocolType {
        val number = response.trim().firstOrNull()?.digitToIntOrNull() ?: return ProtocolType.AUTO
        
        return when (number) {
            1 -> ProtocolType.SAE_J1850_PWM
            2 -> ProtocolType.SAE_J1850_VPW
            3 -> ProtocolType.ISO_9141_2
            4 -> ProtocolType.ISO_14230_4_KWP
            5 -> ProtocolType.ISO_14230_4_KWP_FAST
            6 -> ProtocolType.ISO_15765_4_CAN_11BIT_500K
            7 -> ProtocolType.ISO_15765_4_CAN_29BIT_500K
            8 -> ProtocolType.ISO_15765_4_CAN_11BIT_250K
            9 -> ProtocolType.ISO_15765_4_CAN_29BIT_250K
            else -> ProtocolType.AUTO
        }
    }
    
    protected abstract fun createProtocol(protocolType: ProtocolType): Protocol
    
    // ========================================================================
    // COMMAND INTERFACE
    // ========================================================================
    
    override suspend fun sendCommand(command: String, timeout: Long): AppResult<String> {
        return if (command.startsWith("AT", ignoreCase = true)) {
            sendAtCommand(command, timeout)
        } else {
            sendObdCommand(command, timeout)
        }
    }
    
    private suspend fun sendAtCommand(
        command: String,
        timeout: Long = DEFAULT_AT_TIMEOUT
    ): AppResult<String> = commandMutex.withLock {
        try {
            val fullCommand = "$command\r"
            Timber.d("AT TX: $command")
            
            val writeResult = writeBytes(fullCommand.toByteArray(Charsets.US_ASCII))
            if (writeResult.isFailure) {
                return@withLock writeResult.mapError { it }
            }
            
            val response = readResponse(timeout)
            Timber.d("AT RX: $response")
            
            Result.success(response)
        } catch (e: Exception) {
            Timber.e(e, "AT command error: $command")
            Result.failure(SpaceTecError.fromThrowable(e))
        }
    }
    
    private suspend fun sendObdCommand(
        command: String,
        timeout: Long = DEFAULT_OBD_TIMEOUT
    ): AppResult<String> = commandMutex.withLock {
        try {
            val fullCommand = "$command\r"
            Timber.d("OBD TX: $command")
            
            val writeResult = writeBytes(fullCommand.toByteArray(Charsets.US_ASCII))
            if (writeResult.isFailure) {
                return@withLock writeResult.mapError { it }
            }
            
            val response = readResponse(timeout)
            Timber.d("OBD RX: $response")
            
            Result.success(response)
        } catch (e: Exception) {
            Timber.e(e, "OBD command error: $command")
            Result.failure(SpaceTecError.fromThrowable(e))
        }
    }
    
    private suspend fun readResponse(timeout: Long): String {
        responseBuffer.clear()
        val startTime = System.currentTimeMillis()
        
        while (System.currentTimeMillis() - startTime < timeout) {
            val readResult = readBytes(100)
            if (readResult.isSuccess) {
                val bytes = readResult.getOrNull()
                val text = String(bytes, Charsets.US_ASCII)
                responseBuffer.append(text)
                
                // Check for prompt (response complete)
                if (responseBuffer.contains(">")) {
                    break
                }
            } else {
                delay(10)
            }
        }
        
        return responseBuffer.toString()
            .replace("\r", "\n")
            .replace(">", "")
            .trim()
            .lines()
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }
    
    // ========================================================================
    // RAW DATA INTERFACE
    // ========================================================================
    
    override suspend fun sendRaw(data: ByteArray): AppResult<Unit> {
        Timber.d("Raw TX: ${data.toHexString()}")
        return writeBytes(data)
    }
    
    override suspend fun receiveRaw(timeout: Long): AppResult<ByteArray> {
        return readBytes(timeout).also { result ->
            result.getOrNull()?.let { bytes ->
                Timber.d("Raw RX: ${bytes.toHexString()}")
            }
        }
    }
    
    override fun observeData(): Flow<ByteArray> = observeBytes()
    
    // ========================================================================
    // DIAGNOSTIC TRANSPORT IMPLEMENTATION
    // ========================================================================
    
    override val isConnected: Boolean
        get() = connectionState.value.isConnected
    
    override suspend fun send(request: ByteArray): ByteArray? {
        // Convert bytes to hex string for ELM327
        val hexCommand = request.toHexString(separator = "")
        val result = sendObdCommand(hexCommand)
        return if (result.isSuccess) {
            result.getOrNull()?.toByteArray()
        } else {
            null
        }
    }
    
    override suspend fun send(request: ByteArray, timeoutMs: Long): ByteArray? {
        // Convert bytes to hex string for ELM327 with custom timeout
        val hexCommand = request.toHexString(separator = "")
        val result = sendObdCommand(hexCommand, timeoutMs)
        return if (result.isSuccess) {
            result.getOrNull()?.toByteArray()
        } else {
            null
        }
    }
    
    override suspend fun connect(): Boolean {
        val result = this.connect()
        return result.isSuccess
    }
    
    override suspend fun disconnect() {
        this.disconnect()
    }
    
    // ========================================================================
    // UTILITY METHODS
    // ========================================================================
    
    override suspend fun reset(): AppResult<Unit> {
        return sendAtCommand(Elm327Commands.RESET, timeout = 3000).map { Unit }
    }
    
    override fun getCurrentProtocol(): Protocol? = currentProtocol
    
    override fun supportsProtocol(protocol: ProtocolType): Boolean {
        return capabilities.supportedProtocols.contains(protocol)
    }
    
    companion object {
        private const val DEFAULT_AT_TIMEOUT = 2000L
        private const val DEFAULT_OBD_TIMEOUT = 5000L
    }
}