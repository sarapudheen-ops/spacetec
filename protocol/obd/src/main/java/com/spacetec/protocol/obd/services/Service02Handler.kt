package com.spacetec.protocol.obd.services

import com.spacetec.protocol.obd.*
import com.spacetec.protocol.obd.pid.PIDDecoder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Service 02 Handler - Freeze Frame
 * Implements SAE J1979 Mode $02 - Show freeze frame data
 */
class Service02Handler : BaseOBDService<List<String>, Map<String, List<PIDValue>>>() {
    
    override suspend fun handleRequest(request: List<String>): Flow<OBDResult<Map<String, List<PIDValue>>>> = flow {
        try {
            val results = mutableMapOf<String, List<PIDValue>>()
            
            for (dtc in request) {
                // Format the request command - DTC format is typically 2 bytes
                val command = formatRequest(listOf(dtc))
                
                // In a real implementation, this would send the command to the ECU
                // For now, we'll simulate a response
                val simulatedResponse = simulateECUResponse(dtc)
                
                if (simulatedResponse != null) {
                    val pidValues = decodeFreezeFrameData(simulatedResponse)
                    results[dtc] = pidValues
                }
            }
            
            emit(OBDResult.Success(results))
        } catch (e: Exception) {
            emit(OBDResult.Failure("Error in Service 02: ${e.message}"))
        }
    }
    
    override fun formatRequest(request: List<String>): ByteArray {
        // Format: [Mode: 1 byte][DTC: 2 bytes each]
        val command = mutableListOf<Byte>()
        command.add(0x02.toByte()) // Service Mode 02
        
        for (dtc in request) {
            val dtcBytes = dtcToBytes(dtc)
            command.addAll(dtcBytes)
        }
        
        return command.toByteArray()
    }
    
    override fun parseResponse(rawData: ByteArray): Map<String, List<PIDValue>> {
        // This would parse the actual response from the ECU
        emptyMap()
    }
    
    /**
     * Convert DTC string to bytes according to SAE J2012
     */
    private fun dtcToBytes(dtc: String): List<Byte> {
        if (dtc.length < 5) return listOf(0x00.toByte(), 0x00.toByte())
        
        val type = when (dtc[0]) {
            'P' -> 0x00  // Powertrain
            'C' -> 0x40  // Chassis
            'B' -> 0x80  // Body
            'U' -> 0xC0  // Network
            else -> 0x00
        }
        
        val highNibble = dtc.substring(1, 3).toIntOrNull(16) ?: 0
        val lowNibble = dtc.substring(3, 5).toIntOrNull(16) ?: 0
        
        // Combine type and high nibble for first byte
        val firstByte = (type or ((highNibble and 0xF0) shr 4)).toByte()
        val secondByte = (((highNibble and 0x0F) shl 4) or (lowNibble and 0x0F)).toByte()
        
        return listOf(firstByte, secondByte)
    }
    
    /**
     * Decode freeze frame data from ECU response
     */
    private fun decodeFreezeFrameData(response: ByteArray): List<PIDValue> {
        val pidValues = mutableListOf<PIDValue>()
        
        // Response format: [41][02][DTC bytes][PID data...]
        if (response.size < 4) return pidValues  // Minimum: mode, service, DTC (2 bytes)
        
        var idx = 3  // Skip mode (41), service (02), and DTC (2 bytes)
        
        // Parse PID-value pairs from freeze frame data
        while (idx < response.size) {
            val pid = response[idx].toInt() and 0xFF
            idx++
            
            // Determine how many bytes this PID requires based on PID registry
            val bytesRequired = com.spacetec.protocol.obd.pid.PIDRegistry.getPidBytesRequired(pid)
            
            if (idx + bytesRequired <= response.size) {
                val pidData = response.sliceArray(idx until idx + bytesRequired)
                val pidValue = PIDDecoder.decode(pid, byteArrayOf(0x41, pid.toByte()) + pidData)
                if (pidValue != null) {
                    pidValues.add(pidValue)
                }
                idx += bytesRequired
            } else {
                break  // Not enough bytes remaining
            }
        }
        
        return pidValues
    }
    
    /**
     * Simulate ECU response for testing purposes
     */
    private fun simulateECUResponse(dtc: String): ByteArray? {
        // This is a placeholder - in real implementation, this would communicate with ECU
        // For now, return some sample freeze frame data
        return byteArrayOf(
            0x42,  // Response mode (41 + 0x20 for freeze frame)
            0x02,  // Service ID
            0x01, 0x23,  // DTC (P0123)
            0x0C, 0x01, 0xF4,  // Engine RPM
            0x0D, 0x28,        // Vehicle Speed
            0x05, 0x7A         // Engine Coolant Temperature
        )
    }
}