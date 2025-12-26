package com.spacetec.obd.protocol.obd

import com.spacetec.obd.core.common.extension.hexToByteArray
import com.spacetec.obd.core.common.extension.toHexString
import com.spacetec.obd.core.common.result.AppResult
import com.spacetec.obd.core.common.result.Result
import com.spacetec.obd.core.common.result.SpaceTecError
import com.spacetec.obd.core.domain.model.dtc.DtcCode
import com.spacetec.obd.core.domain.model.dtc.DtcStatus
import com.spacetec.obd.core.domain.model.dtc.FreezeFrame
import com.spacetec.obd.core.domain.model.dtc.FreezeFrameParameter
import com.spacetec.obd.protocol.core.MessageHeader
import com.spacetec.obd.protocol.core.ProtocolConfig
import com.spacetec.obd.protocol.core.ProtocolHandler
import com.spacetec.obd.protocol.core.ProtocolMessage
import com.spacetec.obd.protocol.core.ProtocolService
import com.spacetec.obd.protocol.core.ProtocolType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import timber.log.Timber
import javax.inject.Inject

/**
 * OBD-II Protocol implementation.
 * 
 * Implements the On-Board Diagnostics II protocol according to
 * SAE J1979 / ISO 15031-5 standards, supporting services 01-0A.
 */
class ObdProtocol @Inject constructor() : ProtocolHandler() {
    
    override val protocolId: ProtocolType = ProtocolType.ISO_15765_4_CAN_11BIT_500K
    override val name: String = "OBD-II"
    
    override val supportedServices: List<Int> = listOf(
        ProtocolService.OBD_SERVICE_01_CURRENT_DATA,
        ProtocolService.OBD_SERVICE_02_FREEZE_FRAME,
        ProtocolService.OBD_SERVICE_03_STORED_DTCS,
        ProtocolService.OBD_SERVICE_04_CLEAR_DTCS,
        ProtocolService.OBD_SERVICE_05_O2_SENSOR_TEST,
        ProtocolService.OBD_SERVICE_06_MONITORING_TEST,
        ProtocolService.OBD_SERVICE_07_PENDING_DTCS,
        ProtocolService.OBD_SERVICE_08_CONTROL,
        ProtocolService.OBD_SERVICE_09_VEHICLE_INFO,
        ProtocolService.OBD_SERVICE_0A_PERMANENT_DTCS
    )
    
    private var detectedProtocol: ProtocolType = ProtocolType.AUTO
    private val ecuAddresses = mutableListOf<String>()
    
    // ========================================================================
    // INITIALIZATION
    // ========================================================================
    
    override suspend fun performInitialization(): AppResult<Unit> {
        Timber.d("Initializing OBD-II protocol")
        
        // If auto-detect, try each protocol
        if (config.protocolType == ProtocolType.AUTO) {
            return autoDetectProtocol()
        }
        
        // Use specified protocol
        detectedProtocol = config.protocolType
        return initializeProtocol(detectedProtocol)
    }
    
    override suspend fun performShutdown() {
        Timber.d("Shutting down OBD-II protocol")
        ecuAddresses.clear()
    }
    
    private suspend fun autoDetectProtocol(): AppResult<Unit> {
        Timber.d("Auto-detecting OBD-II protocol")
        
        val protocolsToTry = listOf(
            ProtocolType.ISO_15765_4_CAN_11BIT_500K,
            ProtocolType.ISO_15765_4_CAN_29BIT_500K,
            ProtocolType.ISO_15765_4_CAN_11BIT_250K,
            ProtocolType.ISO_14230_4_KWP_FAST,
            ProtocolType.ISO_14230_4_KWP,
            ProtocolType.ISO_9141_2,
            ProtocolType.SAE_J1850_VPW,
            ProtocolType.SAE_J1850_PWM
        )
        
        for (protocol in protocolsToTry) {
            val result = initializeProtocol(protocol)
            if (result.isSuccess) {
                // Verify by sending a simple request
                val testResult = testProtocol()
                if (testResult.isSuccess) {
                    detectedProtocol = protocol
                    Timber.i("Detected protocol: ${protocol.displayName}")
                    return Result.success(Unit)
                }
            }
        }
        
        return Result.failure(SpaceTecError.ProtocolError.ProtocolNotSupported(
            message = "Could not auto-detect OBD-II protocol"
        ))
    }
    
    private suspend fun initializeProtocol(protocol: ProtocolType): AppResult<Unit> {
        // Protocol-specific initialization would go here
        // For CAN-based protocols, this might involve setting baud rate, etc.
        return Result.success(Unit)
    }
    
    private suspend fun testProtocol(): AppResult<Unit> {
        // Send Service 01 PID 00 to test communication
        val message = ProtocolMessage.request(
            serviceId = ProtocolService.OBD_SERVICE_01_CURRENT_DATA,
            data = byteArrayOf(0x00) // PID 00 - Supported PIDs
        )
        
        return sendMessage(message).map { Unit }
    }
    
    // ========================================================================
    // MESSAGE FORMATTING
    // ========================================================================
    
    override fun formatMessage(message: ProtocolMessage): ProtocolMessage {
        // Add CAN header and length byte for ISO-TP
        val payload = message.payload
        val length = payload.size
        
        // For single frame messages (length <= 7)
        val frameData = if (length <= 7) {
            byteArrayOf(length.toByte()) + payload + ByteArray(7 - length) { config.paddingByte }
        } else {
            // Multi-frame would need ISO-TP handling
            payload
        }
        
        return message.copy(
            rawBytes = frameData
        )
    }
    
    override fun parseResponse(bytes: ByteArray): ProtocolMessage {
        if (bytes.isEmpty()) {
            return ProtocolMessage(
                header = MessageHeader.functionalRequest(),
                serviceId = 0,
                direction = com.spacetec.obd.protocol.core.MessageDirection.RESPONSE
            )
        }
        
        // Parse ISO-TP frame
        val frameType = (bytes[0].toInt() and 0xF0) shr 4
        
        return when (frameType) {
            0 -> parseSingleFrame(bytes)
            1 -> parseFirstFrame(bytes)
            2 -> parseConsecutiveFrame(bytes)
            3 -> parseFlowControlFrame(bytes)
            else -> parseLegacyFrame(bytes)
        }
    }
    
    private fun parseSingleFrame(bytes: ByteArray): ProtocolMessage {
        val length = bytes[0].toInt() and 0x0F
        if (length == 0 || bytes.size < length + 1) {
            return createEmptyResponse()
        }
        
        val serviceId = bytes[1].toInt() and 0xFF
        val data = if (length > 1) bytes.copyOfRange(2, 1 + length) else byteArrayOf()
        
        return ProtocolMessage(
            header = MessageHeader.functionalRequest(),
            serviceId = serviceId,
            data = data,
            direction = com.spacetec.obd.protocol.core.MessageDirection.RESPONSE,
            rawBytes = bytes
        )
    }
    
    private fun parseFirstFrame(bytes: ByteArray): ProtocolMessage {
        // First frame of multi-frame message
        val length = ((bytes[0].toInt() and 0x0F) shl 8) or (bytes[1].toInt() and 0xFF)
        val serviceId = bytes[2].toInt() and 0xFF
        val data = bytes.copyOfRange(3, bytes.size)
        
        return ProtocolMessage(
            header = MessageHeader.functionalRequest(),
            serviceId = serviceId,
            data = data,
            direction = com.spacetec.obd.protocol.core.MessageDirection.RESPONSE,
            rawBytes = bytes
        )
    }
    
    private fun parseConsecutiveFrame(bytes: ByteArray): ProtocolMessage {
        val sequenceNumber = bytes[0].toInt() and 0x0F
        val data = bytes.copyOfRange(1, bytes.size)
        
        return ProtocolMessage(
            header = MessageHeader.functionalRequest(),
            serviceId = 0,
            subFunction = sequenceNumber,
            data = data,
            direction = com.spacetec.obd.protocol.core.MessageDirection.RESPONSE,
            rawBytes = bytes
        )
    }
    
    private fun parseFlowControlFrame(bytes: ByteArray): ProtocolMessage {
        val flowStatus = bytes[0].toInt() and 0x0F
        val blockSize = bytes[1].toInt() and 0xFF
        val separationTime = bytes[2].toInt() and 0xFF
        
        return ProtocolMessage(
            header = MessageHeader.functionalRequest(),
            serviceId = 0x30, // Flow control indicator
            data = byteArrayOf(flowStatus.toByte(), blockSize.toByte(), separationTime.toByte()),
            direction = com.spacetec.obd.protocol.core.MessageDirection.RESPONSE,
            rawBytes = bytes
        )
    }
    
    private fun parseLegacyFrame(bytes: ByteArray): ProtocolMessage {
        // For non-ISO-TP protocols (K-Line, J1850)
        if (bytes.isEmpty()) return createEmptyResponse()
        
        val serviceId = bytes[0].toInt() and 0xFF
        val data = if (bytes.size > 1) bytes.copyOfRange(1, bytes.size) else byteArrayOf()
        
        return ProtocolMessage(
            header = MessageHeader.functionalRequest(),
            serviceId = serviceId,
            data = data,
            direction = com.spacetec.obd.protocol.core.MessageDirection.RESPONSE,
            rawBytes = bytes
        )
    }
    
    private fun createEmptyResponse(): ProtocolMessage = ProtocolMessage(
        header = MessageHeader.functionalRequest(),
        serviceId = 0,
        direction = com.spacetec.obd.protocol.core.MessageDirection.RESPONSE
    )
    
    // ========================================================================
    // DTC OPERATIONS
    // ========================================================================
    
    /**
     * Reads stored DTCs using Service 03.
     */
    suspend fun readStoredDtcs(): AppResult<List<DtcCode>> {
        return readDtcsWithService(
            ProtocolService.OBD_SERVICE_03_STORED_DTCS,
            DtcStatus.CONFIRMED
        )
    }
    
    /**
     * Reads pending DTCs using Service 07.
     */
    suspend fun readPendingDtcs(): AppResult<List<DtcCode>> {
        return readDtcsWithService(
            ProtocolService.OBD_SERVICE_07_PENDING_DTCS,
            DtcStatus.PENDING
        )
    }
    
    /**
     * Reads permanent DTCs using Service 0A.
     */
    suspend fun readPermanentDtcs(): AppResult<List<DtcCode>> {
        return readDtcsWithService(
            ProtocolService.OBD_SERVICE_0A_PERMANENT_DTCS,
            DtcStatus.PERMANENT
        )
    }
    
    private suspend fun readDtcsWithService(
        serviceId: Int,
        status: DtcStatus
    ): AppResult<List<DtcCode>> {
        val message = ProtocolMessage.request(serviceId = serviceId)
        
        return sendMessage(message).map { response ->
            parseDtcResponse(response, status)
        }
    }
    
    private fun parseDtcResponse(response: ProtocolMessage, status: DtcStatus): List<DtcCode> {
        val dtcs = mutableListOf<DtcCode>()
        val data = response.data
        
        // First byte is number of DTCs (for some ECUs)
        // DTCs are 2 bytes each
        var offset = 0
        
        while (offset + 1 < data.size) {
            val dtcBytes = data.copyOfRange(offset, offset + 2)
            
            // Skip null DTCs (0x0000)
            if (dtcBytes[0].toInt() != 0 || dtcBytes[1].toInt() != 0) {
                val dtc = DtcCode.fromObdBytes(dtcBytes)
                if (dtc != null) {
                    dtcs.add(dtc.copy(status = status))
                }
            }
            
            offset += 2
        }
        
        Timber.d("Parsed ${dtcs.size} DTCs from response")
        return dtcs
    }
    
    /**
     * Clears all DTCs using Service 04.
     */
    suspend fun clearDtcs(): AppResult<Unit> {
        val message = ProtocolMessage.request(
            serviceId = ProtocolService.OBD_SERVICE_04_CLEAR_DTCS
        )
        
        return sendMessage(message).map { response ->
            if (response.isPositiveResponse) {
                Timber.i("DTCs cleared successfully")
                Unit
            } else {
                throw IllegalStateException("Clear DTCs failed")
            }
        }
    }
    
    // ========================================================================
    // FREEZE FRAME
    // ========================================================================
    
    /**
     * Reads freeze frame data for a specific frame number.
     */
    suspend fun readFreezeFrame(frameNumber: Int = 0): AppResult<FreezeFrame> {
        val parameters = mutableListOf<FreezeFrameParameter>()
        
        // Read supported PIDs for freeze frame
        val supportedPidsResult = readFreezeFramePid(0x00, frameNumber)
        if (supportedPidsResult.isFailure) {
            return Result.failure(supportedPidsResult.exceptionOrNull() ?: Exception("Unknown error"))
        }
        
        val supportedPidsResultValue = supportedPidsResult.getOrNull()
        val supportedPids = if (supportedPidsResultValue != null) {
            parseSupportedPids(supportedPidsResultValue)
        } else {
            emptyList()
        }
        
        // Read each supported PID
        for (pid in supportedPids) {
            if (pid == 0x00) continue // Skip supported PIDs PID
            
            val pidResult = readFreezeFramePid(pid, frameNumber)
            if (pidResult.isSuccess) {
                val pidValue = pidResult.getOrNull()
                if (pidValue != null) {
                    val parameter = parsePidValue(pid, pidValue)
                if (parameter != null) {
                    parameters.add(parameter)
                }
            }
        }
        
        // Get DTC that triggered freeze frame (PID 02)
        val dtcResult = readFreezeFramePid(0x02, frameNumber)
        val dtcCode = if (dtcResult.isSuccess) {
            val dtcBytes = dtcResult.getOrNull()
            if (dtcBytes != null) {
                DtcCode.fromObdBytes(dtcBytes)?.code ?: ""
            } else {
                ""
            }
        } else ""
        
        return Result.success(FreezeFrame(
            dtcCode = dtcCode,
            frameNumber = frameNumber,
            parameters = parameters
        ))
    }
    
    private suspend fun readFreezeFramePid(pid: Int, frameNumber: Int): AppResult<ByteArray> {
        val message = ProtocolMessage.request(
            serviceId = ProtocolService.OBD_SERVICE_02_FREEZE_FRAME,
            data = byteArrayOf(pid.toByte(), frameNumber.toByte())
        )
        
        return sendMessage(message).map { response ->
            // Response format: [PID] [Frame#] [Data...]
            if (response.data.size >= 2) {
                response.data.copyOfRange(2, response.data.size)
            } else {
                byteArrayOf()
            }
        }
    }
    
    private fun parseSupportedPids(data: ByteArray): List<Int> {
        val pids = mutableListOf<Int>()
        
        for (byteIndex in data.indices) {
            val byte = data[byteIndex].toInt() and 0xFF
            for (bitIndex in 0..7) {
                if ((byte shr (7 - bitIndex)) and 1 == 1) {
                    val pid = (byteIndex * 8) + bitIndex + 1
                    pids.add(pid)
                }
            }
        }
        
        return pids
    }
    
    // ========================================================================
    // CURRENT DATA (SERVICE 01)
    // ========================================================================
    
    /**
     * Reads a current data PID.
     */
    suspend fun readPid(pid: Int): AppResult<ByteArray> {
        val message = ProtocolMessage.request(
            serviceId = ProtocolService.OBD_SERVICE_01_CURRENT_DATA,
            data = byteArrayOf(pid.toByte())
        )
        
        return sendMessage(message).map { response ->
            // Response format: [PID] [Data...]
            if (response.data.isNotEmpty() && response.data[0].toInt() and 0xFF == pid) {
                response.data.copyOfRange(1, response.data.size)
            } else {
                response.data
            }
        }
    }
    
    /**
     * Reads multiple PIDs in a single request.
     */
    suspend fun readMultiplePids(pids: List<Int>): AppResult<Map<Int, ByteArray>> {
        if (pids.isEmpty()) return Result.success(emptyMap())
        if (pids.size > 6) {
            // OBD-II supports max 6 PIDs per request
            return Result.failure(SpaceTecError.ValidationError.OutOfRange(
                message = "Maximum 6 PIDs per request"
            ))
        }
        
        val message = ProtocolMessage.request(
            serviceId = ProtocolService.OBD_SERVICE_01_CURRENT_DATA,
            data = pids.map { it.toByte() }.toByteArray()
        )
        
        return sendMessage(message).map { response ->
            parseMultiplePidResponse(response.data, pids)
        }
    }
    
    private fun parseMultiplePidResponse(data: ByteArray, requestedPids: List<Int>): Map<Int, ByteArray> {
        val result = mutableMapOf<Int, ByteArray>()
        var offset = 0
        
        while (offset < data.size) {
            val pid = data[offset].toInt() and 0xFF
            val pidLength = getPidDataLength(pid)
            
            if (offset + 1 + pidLength <= data.size) {
                result[pid] = data.copyOfRange(offset + 1, offset + 1 + pidLength)
            }
            
            offset += 1 + pidLength
        }
        
        return result
    }
    
    private fun getPidDataLength(pid: Int): Int = when (pid) {
        in 0x00..0x20 -> 4 // Supported PIDs bitmask
        0x01 -> 4 // Monitor status
        0x02 -> 2 // DTC that caused freeze frame
        0x03 -> 2 // Fuel system status
        0x04, 0x05 -> 1 // Load, coolant temp
        0x06..0x09 -> 1 // Short term fuel trims
        0x0A, 0x0B -> 1 // Fuel pressure, intake manifold pressure
        0x0C..0x0D -> 2 // RPM, speed
        0x0E..0x12 -> 1 // Timing advance, air intake temp, etc.
        0x13 -> 1 // O2 sensor present
        0x14..0x1B -> 2 // O2 sensor voltages
        0x1C -> 1 // OBD standard
        0x1D -> 1 // O2 sensors present
        0x1E -> 1 // Aux input status
        0x1F -> 2 // Runtime since start
        0x20 -> 4 // Supported PIDs (21-40)
        0x21 -> 2 // Distance with MIL on
        0x22 -> 2 // Fuel rail pressure (relative)
        0x23 -> 2 // Fuel rail pressure (absolute)
        0x24..0x2B -> 2 // O2 sensor data
        0x2C -> 1 // Commanded EGR
        0x2D -> 1 // EGR error
        0x2E -> 1 // Fuel level
        0x2F -> 1 // Fuel level
        0x30 -> 1 // Number of warm-ups
        0x31 -> 2 // Distance since codes cleared
        0x32 -> 2 // Evap system vapor pressure
        0x33 -> 1 // Barometric pressure
        0x34..0x3B -> 2 // Wide range O2 sensor data
        0x3C..0x3F -> 1 // Catalyst temperature
        0x40 -> 4 // Supported PIDs (41-60)
        else -> 1 // Default to 1 byte
    }
    
    /**
     * Creates a flow to continuously monitor specific PIDs.
     */
    fun monitorPids(pids: List<Int>, interval: Long = 1000): Flow<AppResult<Map<Int, ByteArray>>> {
        return flow {
            while (true) {
                if (!isActive) {
                    emit(Result.failure(SpaceTecError.ConnectionError.ConnectionLost()))
                    break
                }
                
                val result = readMultiplePids(pids)
                emit(result)
                
                kotlinx.coroutines.delay(interval)
            }
        }
    }
    
    // ========================================================================
    // VEHICLE INFORMATION (SERVICE 09)
    // ========================================================================
    
    /**
     * Reads vehicle information using Service 09.
     */
    suspend fun readVehicleInfo(pid: Int): AppResult<String> {
        val message = ProtocolMessage.request(
            serviceId = ProtocolService.OBD_SERVICE_09_VEHICLE_INFO,
            data = byteArrayOf(pid.toByte())
        )
        
        return sendMessage(message).map { response ->
            parseVehicleInfoResponse(response.data, pid)
        }
    }
    
    private fun parseVehicleInfoResponse(data: ByteArray, pid: Int): String {
        return when (pid) {
            ProtocolService.VehicleInfoPid.VIN -> {
                // VIN is ASCII characters, skip first byte which is message count
                data.drop(1).joinToString("") { byte ->
                    if (byte.toInt() in 32..126) byte.toInt().toChar().toString() else ""
                }.trim()
            }
            ProtocolService.VehicleInfoPid.CALIBRATION_ID -> {
                // Calibration ID is ASCII characters
                data.joinToString("") { byte ->
                    if (byte.toInt() in 32..126) byte.toInt().toChar().toString() else ""
                }.trim()
            }
            else -> data.toHexString()
        }
    }
    
    /**
     * Reads the vehicle's VIN.
     */
    suspend fun readVin(): AppResult<String> {
        return readVehicleInfo(ProtocolService.VehicleInfoPid.VIN)
    }
    
    /**
     * Reads the calibration ID.
     */
    suspend fun readCalibrationId(): AppResult<String> {
        return readVehicleInfo(ProtocolService.VehicleInfoPid.CALIBRATION_ID)
    }
    
    // ========================================================================
    // HELPER FUNCTIONS
    // ========================================================================
    
    /**
     * Checks if the response indicates a positive response.
     */
    private fun ProtocolMessage.isPositiveResponse(): Boolean {
        return serviceId >= 0x40
    }
    
    /**
     * Checks if the response is a negative response.
     */
    private fun ProtocolMessage.isNegativeResponse(): Boolean {
        return serviceId == 0x7F
    }
    
    /**
     * Gets the negative response code if this is a negative response.
     */
    private fun ProtocolMessage.negativeResponseCode(): Int? {
        if (!isNegativeResponse() || data.size < 2) return null
        return data[1].toInt() and 0xFF
    }
    
    /**
     * Parses PID value into a FreezeFrameParameter.
     */
    private fun parsePidValue(pid: Int, data: ByteArray): FreezeFrameParameter? {
        return when (pid) {
            0x04 -> FreezeFrameParameter(
                pid = pid,
                name = "Calculated Engine Load",
                value = calculateEngineLoad(data),
                unit = "%"
            )
            0x05 -> FreezeFrameParameter(
                pid = pid,
                name = "Engine Coolant Temperature",
                value = data[0].toInt() - 40,
                unit = "°C"
            )
            0x0C -> FreezeFrameParameter(
                pid = pid,
                name = "Engine RPM",
                value = ((data[0].toInt() shl 8) or data[1].toInt()) / 4.0,
                unit = "RPM"
            )
            0x0D -> FreezeFrameParameter(
                pid = pid,
                name = "Vehicle Speed",
                value = data[0].toInt(),
                unit = "km/h"
            )
            else -> FreezeFrameParameter(
                pid = pid,
                name = "PID $pid",
                value = data.joinToString(" ") { String.format("%02X", it) },
                unit = "raw"
            )
        }
    }
    
    private fun calculateEngineLoad(data: ByteArray): Double {
        if (data.isEmpty()) return 0.0
        return (data[0].toInt() * 100.0) / 255.0
    }
}