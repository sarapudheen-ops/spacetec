package com.obdreader.data.obd.protocol

import com.spacetec.domain.repository.BluetoothConnection
import com.obdreader.data.obd.parser.DTCParser
import com.obdreader.data.obd.parser.PIDParser
import com.obdreader.data.obd.parser.ResponseParser
import com.obdreader.data.obd.isotp.ISOTPReassembler
import com.obdreader.domain.error.OBDError
import com.obdreader.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ELM327 OBD-II protocol implementation.
 * Handles communication with ELM327-compatible adapters via Bluetooth.
 */
class ELM327Protocol(
    private val connection: BluetoothConnection,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : OBDProtocol {
    
    private val commandQueue = CommandQueue()
    private val protocolDetector = ProtocolDetector()
    private val responseParser = ResponseParser()
    private val pidParser = PIDParser()
    private val dtcParser = DTCParser()
    private val isoTPReassembler = ISOTPReassembler()
    
    private val mutex = Mutex()
    private val isInitialized = AtomicBoolean(false)
    private var currentProtocol: OBDProtocolType? = null
    private var adapterVersion: String? = null
    private var supportedPIDsCache = mutableMapOf<Int, Set<Int>>()
    
    private val responseBuffer = StringBuilder()
    private var responseJob: Job? = null
    
    init {
        startResponseListener()
    }
    
    private fun startResponseListener() {
        responseJob = scope.launch {
            connection.receive().collect { data ->
                processIncomingData(data)
            }
        }
    }
    
    private suspend fun processIncomingData(data: ByteArray) {
        val dataString = String(data, Charsets.ISO_8859_1)
        responseBuffer.append(dataString)
        
        if (responseBuffer.contains(">")) {
            val response = responseBuffer.toString()
                .substringBefore(">")
                .trim()
                .replace("\r", "")
                .replace("\n", "")
            
            responseBuffer.clear()
            commandQueue.peek()?.response?.complete(response)
        }
    }
    
    override suspend fun initialize(): Result<AdapterInfo> = mutex.withLock {
        try {
            // Reset adapter
            sendCommandInternal(ATCommand.RESET.command, 2000).getOrThrow()
            delay(ATCommand.RESET.delayAfter)
            
            // Get adapter version
            val versionResponse = sendCommandInternal(ATCommand.IDENTIFY.command).getOrThrow()
            adapterVersion = parseAdapterVersion(versionResponse)
            
            // Configure adapter
            sendCommandInternal(ATCommand.ECHO_OFF.command).getOrThrow()
            sendCommandInternal(ATCommand.LINEFEEDS_OFF.command).getOrThrow()
            sendCommandInternal(ATCommand.SPACES_OFF.command).getOrThrow()
            sendCommandInternal(ATCommand.HEADERS_ON.command).getOrThrow()
            sendCommandInternal(ATCommand.ADAPTIVE_TIMING_AUTO.command).getOrThrow()
            
            // Auto-detect protocol
            sendCommandInternal(ATCommand.PROTOCOL_AUTO.command).getOrThrow()
            
            // Trigger protocol detection
            val pidResponse = sendCommandInternal("0100", 5000)
            if (pidResponse.isFailure) {
                return Result.failure(OBDError.ProtocolError("0100", "Failed to connect to vehicle"))
            }
            
            // Get detected protocol
            val protocolResponse = sendCommandInternal(ATCommand.DESCRIBE_PROTOCOL_NUM.command).getOrThrow()
            currentProtocol = protocolDetector.parseProtocolNumber(protocolResponse)
                ?: return Result.failure(OBDError.ProtocolError(ATCommand.DESCRIBE_PROTOCOL_NUM.command, protocolResponse))
            
            // Get supported PIDs
            val supportedPIDs = parseSupportedPIDs(pidResponse.getOrThrow())
            supportedPIDsCache[1] = supportedPIDs
            
            // Read battery voltage
            val voltageResponse = sendCommandInternal(ATCommand.READ_VOLTAGE.command).getOrNull()
            val voltage = voltageResponse?.let { parseVoltage(it) }
            
            isInitialized.set(true)
            
            // At this point, currentProtocol is guaranteed non-null due to the guard at line 97
            val safeProtocol = currentProtocol ?: throw IllegalStateException("Protocol should have been set but was null")
            Result.success(
                AdapterInfo(
                    version = adapterVersion ?: "Unknown",
                    protocol = safeProtocol,
                    voltage = voltage,
                    supportedPIDs = supportedPIDs
                )
            )
        } catch (e: Exception) {
            isInitialized.set(false)
            Result.failure(OBDError.InitializationError(e.message ?: "Unknown error"))
        }
    }
    
    override suspend fun sendCommand(command: String, timeout: Long): Result<String> {
        return sendCommandInternal(command, timeout)
    }
    
    private suspend fun sendCommandInternal(command: String, timeout: Long = 2000): Result<String> {
        return try {
            val deferred = commandQueue.enqueue(command, timeout)
            
            val sendResult = connection.send("$command\r".toByteArray(Charsets.ISO_8859_1))
            if (sendResult.isFailure) {
                deferred.completeExceptionally(sendResult.exceptionOrNull() ?: Exception("Unknown error"))
                return Result.failure(sendResult.exceptionOrNull() ?: Exception("Unknown error"))
            }
            
            val response = withTimeout(timeout) { deferred.await() }
            
            when {
                response.contains("UNABLE TO CONNECT") -> 
                    Result.failure(OBDError.ProtocolError(command, "Unable to connect to vehicle"))
                response.contains("NO DATA") -> 
                    Result.failure(OBDError.NoDataError(command))
                response.contains("ERROR") -> 
                    Result.failure(OBDError.AdapterError(command, response))
                response.contains("?") -> 
                    Result.failure(OBDError.UnknownCommandError(command))
                response.contains("BUSY") -> 
                    Result.failure(OBDError.BusBusyError(command))
                else -> Result.success(response)
            }
        } catch (e: TimeoutCancellationException) {
            Result.failure(OBDError.TimeoutError(command))
        } catch (e: Exception) {
            Result.failure(OBDError.CommunicationError(command, e.message ?: "Unknown error"))
        }
    }
    
    override suspend fun readPID(mode: Int, pid: Int): Result<PIDResponse> {
        val command = formatPIDCommand(mode, pid)
        val response = sendCommandInternal(command)
        
        return response.fold(
            onSuccess = { rawResponse ->
                pidParser.parse(mode, pid, rawResponse)?.let {
                    Result.success(it)
                } ?: Result.failure(OBDError.ParseError(rawResponse))
            },
            onFailure = { Result.failure(it) }
        )
    }
    
    override suspend fun readPIDs(mode: Int, pids: List<Int>): Result<List<PIDResponse>> {
        if (pids.isEmpty()) return Result.success(emptyList())
        if (pids.size > 6) {
            return Result.failure(OBDError.InvalidRequestError("Maximum 6 PIDs per batch request"))
        }
        
        val responses = mutableListOf<PIDResponse>()
        for (pid in pids) {
            val result = readPID(mode, pid)
            if (result.isSuccess) {
                responses.add(result.getOrThrow())
            }
        }
        
        return Result.success(responses)
    }
    
    override suspend fun getSupportedPIDs(mode: Int): Result<Set<Int>> {
        supportedPIDsCache[mode]?.let { return Result.success(it) }
        
        val allSupported = mutableSetOf<Int>()
        val supportPIDs = listOf(0x00, 0x20, 0x40, 0x60, 0x80, 0xA0, 0xC0, 0xE0)
        
        for (supportPID in supportPIDs) {
            val command = formatPIDCommand(mode, supportPID)
            val response = sendCommandInternal(command)
            
            if (response.isSuccess) {
                val supported = parseSupportedPIDs(response.getOrThrow(), supportPID)
                allSupported.addAll(supported)
                
                if (!supported.contains(supportPID + 0x20)) break
            } else {
                if (supportPID == 0x00) break
            }
        }
        
        supportedPIDsCache[mode] = allSupported
        return Result.success(allSupported)
    }
    
    override suspend fun readStoredDTCs(): Result<List<DTC>> {
        val response = sendCommandInternal("03", 5000)
        
        return response.fold(
            onSuccess = { rawResponse ->
                if (rawResponse.isBlank() || rawResponse == "NO DATA") {
                    Result.success(emptyList())
                } else {
                    val dtcs = dtcParser.parseMode03(rawResponse)
                    Result.success(dtcs)
                }
            },
            onFailure = { Result.failure(it) }
        )
    }
    
    override suspend fun readPendingDTCs(): Result<List<DTC>> {
        val response = sendCommandInternal("07", 5000)
        
        return response.fold(
            onSuccess = { rawResponse ->
                if (rawResponse.isBlank() || rawResponse == "NO DATA") {
                    Result.success(emptyList())
                } else {
                    val dtcs = dtcParser.parseMode07(rawResponse)
                    Result.success(dtcs)
                }
            },
            onFailure = { Result.failure(it) }
        )
    }
    
    override suspend fun readPermanentDTCs(): Result<List<DTC>> {
        val response = sendCommandInternal("0A", 5000)
        
        return response.fold(
            onSuccess = { rawResponse ->
                if (rawResponse.isBlank() || rawResponse == "NO DATA") {
                    Result.success(emptyList())
                } else {
                    val dtcs = dtcParser.parseMode0A(rawResponse)
                    Result.success(dtcs)
                }
            },
            onFailure = { Result.failure(it) }
        )
    }
    
    override suspend fun clearDTCs(): Result<Unit> {
        val response = sendCommandInternal("04", 5000)
        
        return response.fold(
            onSuccess = { rawResponse ->
                if (rawResponse.contains("44") || rawResponse.uppercase().contains("OK")) {
                    Result.success(Unit)
                } else {
                    Result.failure(OBDError.ClearDTCError(rawResponse))
                }
            },
            onFailure = { Result.failure(it) }
        )
    }
    
    override suspend fun readVehicleInfo(): Result<VehicleInfo> {
        val vinResult = readVIN()
        val vin = vinResult.getOrNull()
        
        return Result.success(
            VehicleInfo(
                vin = vin,
                calibrationIds = emptyList(),
                ecuNames = emptyList()
            )
        )
    }
    
    private suspend fun readVIN(): Result<String> {
        val response = sendCommandInternal("0902", 5000)
        
        return response.fold(
            onSuccess = { rawResponse ->
                val vin = responseParser.parseVIN(rawResponse)
                if (vin != null && vin.length == 17) {
                    Result.success(vin)
                } else {
                    Result.failure(OBDError.ParseError("Invalid VIN: $vin"))
                }
            },
            onFailure = { Result.failure(it) }
        )
    }
    
    override fun getProtocolType(): OBDProtocolType? = currentProtocol
    
    override fun isReady(): Boolean = isInitialized.get()
    
    override suspend fun reset(): Result<Unit> {
        isInitialized.set(false)
        supportedPIDsCache.clear()
        currentProtocol = null
        
        return sendCommandInternal(ATCommand.RESET.command).fold(
            onSuccess = { 
                delay(ATCommand.RESET.delayAfter)
                Result.success(Unit) 
            },
            onFailure = { Result.failure(it) }
        )
    }
    
    override suspend fun close() {
        responseJob?.cancel()
        commandQueue.clearWithError(OBDError.ConnectionClosedError())
        isInitialized.set(false)
    }
    
    // Helper methods
    
    private fun formatPIDCommand(mode: Int, pid: Int): String {
        return "${mode.toString(16).uppercase().padStart(2, '0')}${pid.toString(16).uppercase().padStart(2, '0')}"
    }
    
    private fun parseAdapterVersion(response: String): String {
        val versionRegex = Regex("ELM327\\s*v?([\\d.]+)", RegexOption.IGNORE_CASE)
        return versionRegex.find(response)?.groupValues?.get(0) ?: response.trim()
    }
    
    private fun parseVoltage(response: String): Double? {
        val voltageRegex = Regex("([\\d.]+)\\s*V?", RegexOption.IGNORE_CASE)
        return voltageRegex.find(response.trim())?.groupValues?.get(1)?.toDoubleOrNull()
    }
    
    private fun parseSupportedPIDs(response: String, basePID: Int = 0): Set<Int> {
        val supported = mutableSetOf<Int>()
        
        val dataBytes = responseParser.extractDataBytes(response, 4)
        if (dataBytes.size < 4) return supported
        
        for (byteIndex in 0 until 4) {
            val byte = dataBytes[byteIndex].toInt() and 0xFF
            for (bitIndex in 0 until 8) {
                if ((byte and (0x80 shr bitIndex)) != 0) {
                    val pid = basePID + (byteIndex * 8) + bitIndex + 1
                    supported.add(pid)
                }
            }
        }
        
        return supported
    }
}