package com.spacetec.protocol.uds.services

import com.spacetec.protocol.core.base.BaseUDSService
import com.spacetec.protocol.core.base.SessionType
import com.spacetec.protocol.core.message.DiagnosticMessage
import com.spacetec.protocol.core.base.ProtocolError
import com.spacetec.protocol.core.base.NegativeResponseCodes
import com.spacetec.core.common.exceptions.ProtocolException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * UDS Service 0x10 - Diagnostic Session Control
 * Implements ISO 14229-1:2020 diagnostic session management
 */
class DiagnosticSessionControlService : BaseUDSService<Int, SessionType>() {
    
    override suspend fun handleRequest(request: Int): Flow<UDSResult<SessionType>> = flow {
        try {
            val sessionType = SessionType.fromId(request)
            if (sessionType == null) {
                emit(UDSResult.Error(
                    ProtocolError.InvalidRequest("Invalid session type: 0x${request.toString(16)}")
                ))
                return@flow
            }
            
            // Validate session transition rules according to ISO 14229
            val validationResult = validateSessionTransition(sessionType)
            if (!validationResult.isValid) {
                emit(UDSResult.Error(
                    ProtocolError.InvalidRequest("Session transition not allowed: ${validationResult.message}")
                ))
                return@flow
            }
            
            // Attempt to enter the requested session
            val result = enterSession(sessionType)
            if (result.isSuccess) {
                emit(UDSResult.Success(sessionType))
            } else {
                emit(UDSResult.Error(
                    ProtocolError.CommunicationError("Failed to enter session: ${result.exception?.message}")
                ))
            }
        } catch (e: Exception) {
            emit(UDSResult.Error(ProtocolError.CommunicationError("Session control error: ${e.message}")))
        }
    }
    
    /**
     * Validates session transition according to ISO 14229 rules
     */
    private fun validateSessionTransition(requestedSession: SessionType): ValidationResult {
        // According to ISO 14229, all sessions can transition to default session
        if (requestedSession == SessionType.DEFAULT) {
            return ValidationResult(true, "Transition to default session is always allowed")
        }
        
        // Additional validation rules would go here
        // For example, certain sessions may require security access before transition
        return ValidationResult(true, "Session transition is valid")
    }
    
    /**
     * Enters the specified diagnostic session
     */
    private suspend fun enterSession(sessionType: SessionType): Result<Unit> {
        // In a real implementation, this would send the UDS request to the ECU
        // For now, we'll simulate the session change
        return try {
            // Simulate sending: SID(0x10) + session parameter
            val requestData = byteArrayOf(0x10.toByte(), sessionType.id.toByte())
            val response = sendUdsRequest(requestData)
            
            if (response.isSuccess) {
                val responseData = response.getOrNull()
                if (responseData != null && responseData.size >= 2 && 
                    responseData[0].toInt() == 0x50) { // Positive response SID
                    Result.success(Unit)
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
     * Sends a UDS request for diagnostic session control
     */
    private suspend fun sendUdsRequest(data: ByteArray): Result<ByteArray> {
        // This would interface with the actual UDS protocol implementation
        // For now, return a simulated response
        return Result.success(byteArrayOf(0x50.toByte(), data[1])) // Positive response
    }
    
    override fun formatRequest(request: Int): ByteArray {
        return byteArrayOf(0x10.toByte(), request.toByte())
    }
    
    override fun parseResponse(rawData: ByteArray): SessionType? {
        if (rawData.size < 2 || rawData[0].toInt() != 0x50) { // Positive response SID
            return null
        }
        
        val sessionTypeId = rawData[1].toInt() and 0xFF
        return SessionType.fromId(sessionTypeId)
    }
    
    data class ValidationResult(
        val isValid: Boolean,
        val message: String
    )
}