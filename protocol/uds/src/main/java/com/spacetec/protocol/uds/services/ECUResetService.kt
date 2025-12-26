package com.spacetec.protocol.uds.services

import com.spacetec.protocol.core.base.BaseUDSService
import com.spacetec.protocol.core.message.DiagnosticMessage
import com.spacetec.protocol.core.base.ProtocolError
import com.spacetec.protocol.core.base.NegativeResponseCodes
import com.spacetec.core.common.exceptions.ProtocolException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * UDS Service 0x11 - ECU Reset
 * Implements ISO 14229-1:2020 ECU reset functionality
 */
class ECUResetService : BaseUDSService<Int, Int>() {
    
    override suspend fun handleRequest(request: Int): Flow<UDSResult<Int>> = flow {
        try {
            val resetType = request
            if (!isValidResetType(resetType)) {
                emit(UDSResult.Error(
                    ProtocolError.InvalidRequest("Invalid reset type: 0x${resetType.toString(16)}")
                ))
                return@flow
            }
            
            val result = performReset(resetType)
            if (result.isSuccess) {
                val resetResult = result.getOrNull() ?: 0
                emit(UDSResult.Success(resetResult))
            } else {
                emit(UDSResult.Error(
                    ProtocolError.CommunicationError("ECU reset failed: ${result.exceptionOrNull()?.message}")
                ))
            }
        } catch (e: Exception) {
            emit(UDSResult.Error(ProtocolError.CommunicationError("ECU reset error: ${e.message}")))
        }
    }
    
    /**
     * Validates the reset type according to ISO 14229
     */
    private fun isValidResetType(resetType: Int): Boolean {
        return resetType in 0x01..0xFF // Valid range according to ISO 14229
    }
    
    /**
     * Performs the ECU reset operation
     */
    private suspend fun performReset(resetType: Int): Result<Int> {
        return try {
            // Simulate sending: SID(0x11) + reset type
            val requestData = byteArrayOf(0x11.toByte(), resetType.toByte())
            val response = sendUdsRequest(requestData)
            
            if (response.isSuccess) {
                val responseData = response.getOrNull()
                if (responseData != null && responseData.size >= 2 && 
                    responseData[0].toInt() == 0x51) { // Positive response SID
                    Result.success(responseData[1].toInt() and 0xFF)
                } else {
                    Result.failure(Exception("Invalid response format"))
                }
            } else {
                Result.failure(response.exceptionOrNull() ?: Exception("Unknown error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Sends a UDS request for ECU reset
     */
    private suspend fun sendUdsRequest(data: ByteArray): Result<ByteArray> {
        // This would interface with the actual UDS protocol implementation
        // For now, return a simulated response
        return Result.success(byteArrayOf(0x51.toByte(), data[1])) // Positive response
    }
    
    override fun formatRequest(request: Int): ByteArray {
        return byteArrayOf(0x11.toByte(), request.toByte())
    }
    
    override fun parseResponse(rawData: ByteArray): Int? {
        if (rawData.size < 2 || rawData[0].toInt() != 0x51) { // Positive response SID
            return null
        }
        
        return rawData[1].toInt() and 0xFF
    }
    
    companion object {
        // Standard reset types from ISO 14229
        const val HARD_RESET = 0x01
        const val KEY_OFF_ON_RESET = 0x02
        const val SOFT_RESET = 0x03
        const val ENABLE_RAPID_POWER_SHUTDOWN = 0x04
        const val DISABLE_RAPID_POWER_SHUTDOWN = 0x05
    }
}