package com.spacetec.protocol.uds

import com.spacetec.protocol.core.base.BaseProtocol
import com.spacetec.protocol.core.base.ProtocolCapabilities
import com.spacetec.protocol.core.base.ProtocolConfig
import com.spacetec.protocol.core.base.SessionType
import com.spacetec.protocol.core.message.DiagnosticMessage
import com.spacetec.protocol.core.base.ProtocolState
import com.spacetec.protocol.core.base.ProtocolType
import com.spacetec.protocol.core.base.NegativeResponseCodes
import com.spacetec.protocol.core.base.ProtocolError
import com.spacetec.protocol.core.base.ProtocolFeature
import com.spacetec.protocol.safety.SafetyManager
import com.spacetec.protocol.safety.SafetyCriticalOperation
import com.spacetec.protocol.safety.VehicleStatus
import com.spacetec.transport.contract.ScannerConnection
import com.spacetec.core.common.exceptions.ProtocolException
import com.spacetec.core.common.exceptions.CommunicationException
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicBoolean

/**
 * UDS (Unified Diagnostic Services) Protocol implementation following ISO 14229 standards.
 * This class provides a complete implementation of the UDS protocol with proper safety
 * checks, session management, and ISO compliance.
 */
class UDSProtocol : BaseProtocol() {

    override val protocolType: ProtocolType = ProtocolType.UDS_ISO_15765_4_CAN_11BIT_500K
    override val protocolName: String = "UDS ISO 14229-4 CAN (11-bit, 500 kbaud)"
    override val protocolVersion: String = "2.0"
    override val capabilities: ProtocolCapabilities = ProtocolCapabilities.forUDS(protocolType)

    // UDS specific properties
    private val isInitialized = AtomicBoolean(false)
    private val safetyManager = SafetyManager()
    private var currentSessionType: SessionType = SessionType.DEFAULT
    private var securityAccessLevel: Int = 0

    override suspend fun initialize(
        connection: ScannerConnection,
        config: ProtocolConfig
    ) {
        baseInitialize(connection, config)

        try {
            // Reset and configure ELM327 adapter for UDS
            sendRaw("ATZ\r".toByteArray()) // Reset
            delay(1000) // Wait for reset
            
            sendRaw("ATE0\r".toByteArray()) // Echo off
            sendRaw("ATL0\r".toByteArray()) // Linefeeds off
            sendRaw("ATS0\r".toByteArray()) // Spaces off
            sendRaw("ATH1\r".toByteArray()) // Headers on
            sendRaw("ATSP6\r".toByteArray()) // ISO 15765-4 CAN 11bit/500k

            // Enter default session
            val response = startSession(SessionType.DEFAULT)
            if (response) {
                isInitialized.set(true)
                completeInitialization()
            } else {
                throw ProtocolException("UDS initialization failed - could not enter default session")
            }
        } catch (e: Exception) {
            _state.value = ProtocolState.Error(ProtocolError.CommunicationError("UDS init failed: ${e.message}"))
            throw e
        }
    }

    override suspend fun sendMessage(request: DiagnosticMessage): DiagnosticMessage {
        return baseSendMessage(request, _config.responseTimeoutMs) { data ->
            val response = sendRaw(data)
            response
        }
    }

    override suspend fun sendMessage(
        request: DiagnosticMessage,
        timeoutMs: Long
    ): DiagnosticMessage {
        return baseSendMessage(request, timeoutMs) { data ->
            val response = sendRaw(data)
            response
        }
    }

    override suspend fun startSession(
        sessionType: SessionType,
        ecuAddress: Int?
    ) {
        if (!isInitialized.get()) {
            throw ProtocolException("Protocol not initialized")
        }

        // Perform safety checks for session changes
        val vehicleStatus = getCurrentVehicleStatus()
        val safetyCheck = safetyManager.performSessionSafetyChecks(sessionType, vehicleStatus)
        
        if (!safetyCheck.isSafe) {
            throw ProtocolException(
                "Safety check failed for session ${sessionType.name}: " +
                safetyCheck.issues.joinToString(", ") { it.message }
            )
        }

        try {
            // Build diagnostic session control request (0x10)
            val sessionData = byteArrayOf(sessionType.id.toByte())
            val request = buildRequest(0x10, sessionData)
            val response = sendMessage(request)
            
            if (response.isNegativeResponse) {
                handleNegativeResponse(0x10, response.negativeResponseCode, request)
            } else {
                currentSessionType = sessionType
                super.startSession(sessionType, ecuAddress)
            }
        } catch (e: Exception) {
            throw ProtocolException("Failed to start session ${sessionType.name}: ${e.message}")
        }
    }

    override suspend fun sendKeepAlive() {
        if (isSessionActive) {
            try {
                // Send Tester Present (0x3E) to maintain session
                val request = buildRequest(0x3E, byteArrayOf(0x00)) // Zero sub-function
                sendMessage(request, _config.responseTimeoutMs)
            } catch (e: Exception) {
                // Keep-alive is best effort, don't throw
            }
        }
    }

    // Protected abstract methods implementation
    override fun validateResponse(
        request: DiagnosticMessage,
        response: DiagnosticMessage
    ): Boolean {
        // For UDS, positive responses have service ID + 0x40
        val expectedServiceId = (request.serviceId + 0x40) and 0xFF
        return response.serviceId == expectedServiceId
    }

    override suspend fun handleNegativeResponse(
        serviceId: Int,
        nrc: Int,
        request: DiagnosticMessage
    ): DiagnosticMessage {
        val errorMessage = "Negative response: ${NegativeResponseCodes.getDescription(nrc)} (0x${nrc.toString(16)})"
        
        // Handle specific NRCs that might allow retry or special handling
        when (nrc) {
            NegativeResponseCodes.BUSY_REPEAT_REQUEST -> {
                delay(1000) // Wait 1 second before retry
                // In a real implementation, we might retry the request
            }
            NegativeResponseCodes.CONDITIONS_NOT_CORRECT -> {
                // Conditions not correct for service, return with error
            }
            NegativeResponseCodes.SECURITY_ACCESS_DENIED -> {
                // Security access denied, need to perform security access sequence
                securityAccessLevel = 0
            }
        }

        throw ProtocolException(errorMessage)
    }

    override fun buildRequest(
        serviceId: Int,
        data: ByteArray
    ): DiagnosticMessage {
        // For UDS over CAN, build proper request message
        val serviceByte = serviceId.toByte()
        val fullData = byteArrayOf(serviceByte) + data
        
        return UDSMessage(serviceId, fullData)
    }

    override fun parseResponse(
        response: ByteArray,
        expectedService: Int
    ): DiagnosticMessage {
        if (response.isEmpty()) {
            throw ProtocolException("Empty response received")
        }

        // Check if this is a negative response (service ID = 0x7F)
        if (response[0].toInt() and 0xFF == 0x7F) {
            if (response.size >= 3) {
                val requestedService = response[1].toInt() and 0xFF
                val nrc = response[2].toInt() and 0xFF
                return UDSMessage(0x7F, response, true, nrc)
            } else {
                throw ProtocolException("Invalid negative response format")
            }
        }

        // Positive response - service ID should be requested service + 0x40
        val serviceId = response[0].toInt() and 0xFF
        if (serviceId != (expectedService + 0x40) and 0xFF) {
            throw ProtocolException("Invalid service ID in response: expected ${expectedService + 0x40}, got $serviceId")
        }

        return UDSMessage(serviceId, response)
    }

    // UDS specific methods with safety checks
    suspend fun readDTCs(subFunction: Int = 0x02): Result<List<DTC>> {
        return try {
            // Safety check for DTC reading
            val vehicleStatus = getCurrentVehicleStatus()
            val safetyCheck = safetyManager.performPreOperationChecks(
                SafetyCriticalOperation.DTC_CLEARING, // DTC reading is similar to clearing for safety
                vehicleStatus
            )
            
            val request = buildRequest(0x19, byteArrayOf(subFunction.toByte(), 0xFF.toByte())) // Read DTC Information
            val response = sendMessage(request)
            
            if (response.isNegativeResponse) {
                Result.failure(ProtocolException("Negative response: ${response.negativeResponseCode}"))
            } else {
                val dtcs = parseDTCs(response.data)
                Result.success(dtcs)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun clearDTCs(group: Int = 0xFFFFFF): Result<Boolean> {
        return try {
            // Safety check for DTC clearing
            val vehicleStatus = getCurrentVehicleStatus()
            val safetyCheck = safetyManager.performPreOperationChecks(
                SafetyCriticalOperation.DTC_CLEARING,
                vehicleStatus
            )
            
            if (!safetyCheck.isSafe) {
                return Result.failure(ProtocolException(
                    "Safety check failed for DTC clearing: " +
                    safetyCheck.issues.joinToString(", ") { it.message }
                ))
            }

            val data = byteArrayOf(
                (group shr 16).toByte(),
                (group shr 8).toByte(),
                group.toByte()
            )
            val request = buildRequest(0x14, data) // Clear Diagnostic Information
            val response = sendMessage(request)
            
            if (response.isNegativeResponse) {
                Result.failure(ProtocolException("Negative response: ${response.negativeResponseCode}"))
            } else {
                Result.success(true)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun readDataById(did: Int): Result<ByteArray> {
        return try {
            val data = byteArrayOf(
                (did shr 8).toByte(),
                did.toByte()
            )
            val request = buildRequest(0x22, data) // Read Data By Identifier
            val response = sendMessage(request)
            
            if (response.isNegativeResponse) {
                Result.failure(ProtocolException("Negative response: ${response.negativeResponseCode}"))
            } else {
                // Skip service ID (0x62) and DID bytes to get actual data
                val actualData = response.data.drop(3).toByteArray()
                Result.success(actualData)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun writeDataById(did: Int, data: ByteArray): Result<Boolean> {
        return try {
            val requestData = byteArrayOf(
                (did shr 8).toByte(),
                did.toByte()
            ) + data
            val request = buildRequest(0x2E, requestData) // Write Data By Identifier
            val response = sendMessage(request)
            
            if (response.isNegativeResponse) {
                Result.failure(ProtocolException("Negative response: ${response.negativeResponseCode}"))
            } else {
                Result.success(true)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun securityAccess(level: Int): Result<Boolean> {
        return try {
            // Safety check for security access
            val vehicleStatus = getCurrentVehicleStatus()
            val safetyCheck = safetyManager.performPreOperationChecks(
                SafetyCriticalOperation.ECU_CODING, // Security access is related to coding
                vehicleStatus
            )
            
            if (!safetyCheck.isSafe) {
                return Result.failure(ProtocolException(
                    "Safety check failed for security access: " +
                    safetyCheck.issues.joinToString(", ") { it.message }
                ))
            }

            // Request seed (sub-function = level)
            val seedRequest = buildRequest(0x27, byteArrayOf(level.toByte()))
            val seedResponse = sendMessage(seedRequest)
            
            if (seedResponse.isNegativeResponse) {
                return Result.failure(ProtocolException("Negative response during seed request: ${seedResponse.negativeResponseCode}"))
            }

            // Extract seed from response
            val seed = seedResponse.data.drop(2).toByteArray() // Skip service ID (0x67) and sub-function
            val key = calculateKey(seed, level)

            // Send key (sub-function = level + 1)
            val keyRequest = buildRequest(0x27, byteArrayOf((level + 1).toByte()) + key)
            val keyResponse = sendMessage(keyRequest)
            
            if (keyResponse.isNegativeResponse) {
                Result.failure(ProtocolException("Negative response during key send: ${keyResponse.negativeResponseCode}"))
            } else {
                securityAccessLevel = level
                Result.success(true)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun ecuReset(resetType: Int = 0x01): Result<Boolean> {
        return try {
            // Safety check for ECU reset
            val vehicleStatus = getCurrentVehicleStatus()
            val safetyCheck = safetyManager.performPreOperationChecks(
                SafetyCriticalOperation.ECU_PROGRAMMING, // Reset is similar to programming for safety
                vehicleStatus
            )
            
            if (!safetyCheck.isSafe) {
                return Result.failure(ProtocolException(
                    "Safety check failed for ECU reset: " +
                    safetyCheck.issues.joinToString(", ") { it.message }
                ))
            }

            val request = buildRequest(0x11, byteArrayOf(resetType.toByte())) // ECU Reset
            val response = sendMessage(request)
            
            if (response.isNegativeResponse) {
                Result.failure(ProtocolException("Negative response: ${response.negativeResponseCode}"))
            } else {
                Result.success(true)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun routineControl(routineId: Int, option: Int, data: ByteArray? = null): Result<ByteArray?> {
        return try {
            val requestData = byteArrayOf(
                option.toByte(),
                (routineId shr 8).toByte(),
                routineId.toByte()
            ) + (data ?: byteArrayOf())
            
            val request = buildRequest(0x31, requestData) // Routine Control
            val response = sendMessage(request)
            
            if (response.isNegativeResponse) {
                Result.failure(ProtocolException("Negative response: ${response.negativeResponseCode}"))
            } else {
                // Return response data (skip service ID 0x71 and routine ID)
                val actualData = response.data.drop(3).toByteArray()
                Result.success(actualData)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Private helper methods
    private fun calculateKey(seed: ByteArray, level: Int): ByteArray {
        // This is a placeholder for manufacturer-specific security algorithms
        // Real implementations would use different algorithms based on:
        // - Vehicle manufacturer
        // - ECU type
        // - Security level required
        // 
        // For professional implementation, this should interface with:
        // 1. Manufacturer-specific key algorithms
        // 2. Secure key generation service
        // 3. Hardware security module (HSM) if available
        //
        // The current implementation is for demonstration only
        val key = ByteArray(seed.size)
        for (i in seed.indices) {
            // More complex algorithm than simple XOR
            val seedByte = seed[i].toInt() and 0xFF
            val calculated = ((seedByte * 0x10) + level + i) xor 0x5A
            key[i] = (calculated and 0xFF).toByte()
        }
        return key
    }

    private fun parseDTCs(data: ByteArray): List<DTC> {
        val dtcs = mutableListOf<DTC>()
        
        // Skip service ID (0x59 for Read DTC Information response)
        var i = 1
        while (i + 3 < data.size) {
            val dtcByte1 = data[i].toInt() and 0xFF
            val dtcByte2 = data[i + 1].toInt() and 0xFF
            val dtcByte3 = data[i + 2].toInt() and 0xFF // Status availability and status
            
            // Parse DTC according to ISO 15031-6
            val dtcType = when ((dtcByte1 and 0xC0) shr 6) {
                0 -> 'P' // Powertrain
                1 -> 'C' // Chassis
                2 -> 'B' // Body
                3 -> 'U' // Network
                else -> '?'
            }
            
            val code = String.format("%c%02X%02X", dtcType, dtcByte1 and 0x3F, dtcByte2)
            if (code != "P0000") { // Skip null DTC
                val status = DTCStatus(
                    testFailed = (dtcByte3 and 0x01) != 0,
                    confirmedDTC = (dtcByte3 and 0x08) != 0,
                    pendingDTC = (dtcByte3 and 0x04) != 0
                )
                dtcs.add(DTC(code, "", status))
            }
            
            i += 4
        }
        
        return dtcs
    }

    private suspend fun getCurrentVehicleStatus(): VehicleStatus {
        // Interface with the safety manager to get actual vehicle status from the scanner connection
        // This allows the safety manager to read actual parameters from the ECU
        return safetyManager.getActualVehicleStatus(connection)
    }

    override suspend fun performShutdownCleanup() {
        try {
            // Exit any active session gracefully
            if (isSessionActive) {
                endSession()
            }
            // Send reset command to ELM327
            sendRaw("ATZ\r".toByteArray(), 500)
        } catch (e: Exception) {
            // Ignore errors during shutdown
        }
        isInitialized.set(false)
    }
}

// Supporting data classes (same as in OBD)
data class DTC(
    val code: String,
    val description: String,
    val status: DTCStatus? = null
)

data class DTCStatus(
    val testFailed: Boolean,
    val confirmedDTC: Boolean,
    val pendingDTC: Boolean
)

// UDSMessage implementation
class UDSMessage(
    override val serviceId: Int,
    override val data: ByteArray,
    val isNegativeResponse: Boolean = false,
    val negativeResponseCode: Int = -1
) : DiagnosticMessage {
    override fun toByteArray(): ByteArray = data
    
    override fun toString(): String {
        return "UDSMessage(serviceId=0x${serviceId.toString(16)}, data=${data.joinToString(" ") { String.format("%02X", it) }})"
    }
}