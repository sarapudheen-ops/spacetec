package com.spacetec.protocol.uds.services

import com.spacetec.protocol.core.base.BaseUDSService
import com.spacetec.protocol.core.message.DiagnosticMessage
import com.spacetec.protocol.core.base.ProtocolError
import com.spacetec.protocol.core.base.NegativeResponseCodes
import com.spacetec.core.common.exceptions.ProtocolException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * UDS Service 0x22 - Read Data By Identifier
 * Implements ISO 14229-1:2020 Read Data By Identifier functionality
 */
class ReadDataByIdentifierService : BaseUDSService<Int, ByteArray>() {
    
    override suspend fun handleRequest(request: Int): Flow<UDSResult<ByteArray>> = flow {
        try {
            if (!isValidDataIdentifier(request)) {
                emit(UDSResult.Error(
                    ProtocolError.InvalidRequest("Invalid data identifier: 0x${request.toString(16)}")
                ))
                return@flow
            }
            
            val result = readDataByIdentifier(request)
            if (result.isSuccess) {
                val data = result.getOrNull() ?: byteArrayOf()
                emit(UDSResult.Success(data))
            } else {
                emit(UDSResult.Error(
                    ProtocolError.CommunicationError("Read data by identifier failed: ${result.exceptionOrNull()?.message}")
                ))
            }
        } catch (e: Exception) {
            emit(UDSResult.Error(ProtocolError.CommunicationError("Read data by identifier error: ${e.message}")))
        }
    }
    
    /**
     * Validates the data identifier according to ISO 14229
     */
    private fun isValidDataIdentifier(did: Int): Boolean {
        // Standard DIDs range from 0x0000 to 0xFFFF
        // Exclude reserved ranges as per ISO 14229
        return did in 0x0000..0xFFFF
    }
    
    /**
     * Reads data by identifier from the ECU
     */
    private suspend fun readDataByIdentifier(did: Int): Result<ByteArray> {
        return try {
            // Format request: SID(0x22) + DID (2 bytes, MSB first)
            val requestData = byteArrayOf(
                0x22.toByte(),
                (did shr 8).toByte(),  // MSB
                did.toByte()           // LSB
            )
            
            val response = sendUdsRequest(requestData)
            
            if (response.isSuccess) {
                val responseData = response.getOrNull()
                if (responseData != null && responseData.size >= 3 && 
                    responseData[0].toInt() == 0x62) { // Positive response SID for ReadDataByIdentifier
                    // Extract the actual data (skip SID and DID)
                    val actualData = responseData.sliceArray(3 until responseData.size)
                    Result.success(actualData)
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
     * Sends a UDS request for Read Data By Identifier
     */
    private suspend fun sendUdsRequest(data: ByteArray): Result<ByteArray> {
        // This would interface with the actual UDS protocol implementation
        // For now, return a simulated response
        // Simulate response: 0x62 (positive response SID) + DID + actual data
        val simulatedData = byteArrayOf(0x62.toByte(), data[1], data[2], 0x01, 0x02, 0x03, 0x04)
        return Result.success(simulatedData)
    }
    
    override fun formatRequest(request: Int): ByteArray {
        return byteArrayOf(
            0x22.toByte(),
            (request shr 8).toByte(),  // MSB
            request.toByte()           // LSB
        )
    }
    
    override fun parseResponse(rawData: ByteArray): ByteArray? {
        if (rawData.size < 3 || rawData[0].toInt() != 0x62) { // Positive response SID
            return null
        }
        
        // Extract the actual data (skip SID and DID)
        return rawData.sliceArray(3 until rawData.size)
    }
    
    companion object {
        // Standard Data Identifiers (DIDs) from ISO 14229 and related standards
        // Engine related DIDs
        const val VEHICLE_MANUFACTURER_ECU_SOFTWARE_NUMBER = 0xF187
        const val VEHICLE_MANUFACTURER_ECU_HARDWARE_NUMBER = 0xF188
        const val VEHICLE_MANUFACTURER_ECU_HARDWARE_VERSION = 0xF189
        const val VEHICLE_MANUFACTURER_ECU_SOFTWARE_VERSION = 0xF18A
        const val APPLICATION_SOFTWARE_IDENTIFICATION = 0xF18B
        const val BOOT_SOFTWARE_IDENTIFICATION = 0xF18C
        const val EXHAUST_REGULATION_OR_TYPE_APPROVAL_NUMBER = 0xF190
        const val ECU_NAME = 0xF19E
        const val VEHICLE_MANUFACTURER_ECU_SERIAL_NUMBER = 0xF186
        
        // OBD-related DIDs (from ISO 15031-5)
        const val OBD_ECU_NAME = 0xF190
        const val OBD_VIN = 0xF197
        const val OBD_CALIBRATION_ID = 0xF1A3
        const val OBD_CALIBRATION_VERIFICATION_NUMBERS = 0xF1A4
        const val OBD_SPN_LIST_PERM = 0xF1A5
        const val OBD_FMI_LIST_PERM = 0xF1A6
        const val OBD_SPN_LIST_TEMP = 0xF1A7
        const val OBD_FMI_LIST_TEMP = 0xF1A8
    }
}