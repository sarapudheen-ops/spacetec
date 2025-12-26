package com.spacetec.protocol.uds.services

import com.spacetec.protocol.core.base.BaseUDSService
import com.spacetec.protocol.core.message.DiagnosticMessage
import com.spacetec.protocol.core.base.ProtocolError
import com.spacetec.protocol.core.base.NegativeResponseCodes
import com.spacetec.core.common.exceptions.ProtocolException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * UDS Service 0x19 - Read DTC Information
 * Implements ISO 14229-1:2020 DTC information reading functionality
 */
class ReadDTCInformationService : BaseUDSService<ReadDTCRequest, List<DTC>> {
    
    override suspend fun handleRequest(request: ReadDTCRequest): Flow<UDSResult<List<DTC>>> = flow {
        try {
            if (!isValidSubFunction(request.subFunction)) {
                emit(UDSResult.Error(
                    ProtocolError.InvalidRequest("Invalid sub-function: 0x${request.subFunction.toString(16)}")
                ))
                return@flow
            }
            
            val result = readDTCInformation(request)
            if (result.isSuccess) {
                val dtcs = result.getOrNull() ?: emptyList()
                emit(UDSResult.Success(dtcs))
            } else {
                emit(UDSResult.Error(
                    ProtocolError.CommunicationError("Read DTC information failed: ${result.exceptionOrNull()?.message}")
                ))
            }
        } catch (e: Exception) {
            emit(UDSResult.Error(ProtocolError.CommunicationError("Read DTC information error: ${e.message}")))
        }
    }
    
    /**
     * Validates the sub-function according to ISO 14229
     */
    private fun isValidSubFunction(subFunction: Int): Boolean {
        return subFunction in 0x01..0xFF // Valid range according to ISO 14229
    }
    
    /**
     * Reads DTC information based on the request
     */
    private suspend fun readDTCInformation(request: ReadDTCRequest): Result<List<DTC>> {
        return try {
            // Format request data based on sub-function
            val requestData = formatRequestData(request)
            val response = sendUdsRequest(requestData)
            
            if (response.isSuccess) {
                val responseData = response.getOrNull()
                if (responseData != null && responseData.size >= 1 && 
                    responseData[0].toInt() == 0x59) { // Positive response SID for ReadDTC
                    val dtcs = parseDTCResponse(responseData, request)
                    Result.success(dtcs)
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
     * Formats the request data based on the sub-function
     */
    private fun formatRequestData(request: ReadDTCRequest): ByteArray {
        val data = mutableListOf<Byte>()
        data.add(0x19.toByte()) // SID
        data.add(request.subFunction.toByte())
        
        // Add additional parameters based on sub-function
        when (request.subFunction) {
            0x02, 0x06, 0x0A, 0x0E, 0x10, 0x14, 0x18 -> {
                // These sub-functions require DTC status mask
                data.add(request.dtcStatusMask.toByte())
            }
            0x03, 0x04, 0x0B, 0x0F, 0x15, 0x19 -> {
                // These sub-functions require DTC mask record
                data.addAll(request.dtcMaskRecord.map { it.toByte() })
            }
            0x07, 0x08, 0x0C, 0x11, 0x16, 0x1A -> {
                // These sub-functions require snapshot record number
                data.add(request.snapshotRecordNumber.toByte())
            }
            0x09, 0x12, 0x17, 0x1B -> {
                // These sub-functions require extended data record number
                data.add(request.extendedDataRecordNumber.toByte())
            }
        }
        
        return data.toByteArray()
    }
    
    /**
     * Parses the DTC response based on the sub-function
     */
    private fun parseDTCResponse(responseData: ByteArray, request: ReadDTCRequest): List<DTC> {
        val dtcs = mutableListOf<DTC>()
        
        // Skip SID (0x59) and sub-function
        var idx = 2
        while (idx < responseData.size) {
            if (idx + 3 < responseData.size) { // Need at least 4 bytes for a DTC entry
                val dtcByte1 = responseData[idx].toInt() and 0xFF
                val dtcByte2 = responseData[idx + 1].toInt() and 0xFF
                val dtcByte3 = responseData[idx + 2].toInt() and 0xFF
                
                // Parse DTC according to ISO 15031-6
                val dtcType = when ((dtcByte1 and 0xC0) shr 6) {
                    0 -> 'P' // Powertrain
                    1 -> 'C' // Chassis
                    2 -> 'B' // Body
                    3 -> 'U' // Network
                    else -> '?'
                }
                
                val code = String.format("%c%02X%02X", dtcType, dtcByte1 and 0x3F, dtcByte2)
                
                // Parse status
                val status = DTCStatus(
                    testFailed = (dtcByte3 and 0x01) != 0,
                    testFailedThisOperationCycle = (dtcByte3 and 0x02) != 0,
                    pendingDTC = (dtcByte3 and 0x04) != 0,
                    confirmedDTC = (dtcByte3 and 0x08) != 0,
                    testNotCompletedSinceLastClear = (dtcByte3 and 0x10) != 0,
                    testFailedSinceLastClear = (dtcByte3 and 0x20) != 0,
                    testNotCompletedThisOperationCycle = (dtcByte3 and 0x40) != 0,
                    warningIndicatorRequested = (dtcByte3 and 0x80) != 0
                )
                
                dtcs.add(DTC(code, "", status))
                idx += 4 // Move to next DTC entry
            } else {
                break
            }
        }
        
        return dtcs
    }
    
    /**
     * Sends a UDS request for Read DTC Information
     */
    private suspend fun sendUdsRequest(data: ByteArray): Result<ByteArray> {
        // This would interface with the actual UDS protocol implementation
        // For now, return a simulated response
        return Result.success(data.copyOf()) // Placeholder response
    }
    
    override fun formatRequest(request: ReadDTCRequest): ByteArray {
        return formatRequestData(request)
    }
    
    override fun parseResponse(rawData: ByteArray): List<DTC>? {
        if (rawData.size < 2 || rawData[0].toInt() != 0x59) { // Positive response SID
            return null
        }
        
        // For now, return empty list - actual parsing would happen in readDTCInformation
        return emptyList()
    }
    
    // Data classes for the service
    data class ReadDTCRequest(
        val subFunction: Int,
        val dtcStatusMask: Int = 0,
        val dtcMaskRecord: List<Int> = emptyList(),
        val snapshotRecordNumber: Int = 0,
        val extendedDataRecordNumber: Int = 0
    )
    
    data class DTC(
        val code: String,
        val description: String,
        val status: DTCStatus? = null
    )
    
    data class DTCStatus(
        val testFailed: Boolean,
        val testFailedThisOperationCycle: Boolean,
        val pendingDTC: Boolean,
        val confirmedDTC: Boolean,
        val testNotCompletedSinceLastClear: Boolean,
        val testFailedSinceLastClear: Boolean,
        val testNotCompletedThisOperationCycle: Boolean,
        val warningIndicatorRequested: Boolean
    )
    
    companion object {
        // Standard sub-functions from ISO 14229
        const val REPORT_NUM_DTC_BY_STATUS_MASK = 0x01
        const val REPORT_DTC_BY_STATUS_MASK = 0x02
        const val REPORT_DTC_SNAPSHOT_IDENTIFICATION = 0x03
        const val REPORT_DTC_SNAPSHOT_RECORD_BY_DTC_NUMBER = 0x04
        const val REPORT_DTC_EXTENDED_DATA_RECORD_BY_DTC_NUMBER = 0x06
        const val REPORT_NUM_DTC_BY_SEVERITY_MASK_RECORD = 0x07
        const val REPORT_DTC_BY_SEVERITY_MASK_RECORD = 0x08
        const val REPORT_SEVERITY_INFORMATION_OF_DTC = 0x09
        const val REPORT_SUPPORTED_DTC = 0x0A
        const val REPORT_FIRST_TEST_FAILED_DTC = 0x0B
        const val REPORT_FIRST_CONFIRMED_DTC = 0x0C
        const val REPORT_MOST_RECENT_TEST_FAILED_DTC = 0x0D
        const val REPORT_MOST_RECENT_CONFIRMED_DTC = 0x0E
        const val REPORT_MINOR_EXTENSION_OF_DTC = 0x0F
        const val REPORT_DTC_WITH_PERMANENT_STATUS = 0x10
        const val REPORT_DTC_FAULT_DETECTION_COUNTER = 0x11
        const val REPORT_DTC_WITH_INFOTYPE = 0x12
        const val REPORT_AGGREGATED_DTC_BY_SEVERITY_MASK_RECORD = 0x13
        const val REPORT_NUM_AGGR_OF_DTC_BY_SEVERITY_MASK_RECORD = 0x14
        const val REPORT_SVV_DTC = 0x15
        const val REPORT_NUM_SVV_DTC = 0x16
        const val REPORT_REGULATORY_DTC = 0x17
        const val REPORT_NUM_REGULATORY_DTC = 0x18
        const val REPORT_DTC_SYMPOMS = 0x19
        const val REPORT_NUM_DTC_SYMPOMS = 0x1A
        const val REPORT_PREVIOUSLY_DETECTED_DTC = 0x1B
        const val REPORT_NUM_PREVIOUSLY_DETECTED_DTC = 0x1C
    }
}