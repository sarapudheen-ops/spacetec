package com.spacetec.protocol.obd.pid

import com.spacetec.protocol.obd.PIDValue

/**
 * PID Decoder
 * Decodes raw ECU response data according to PID specifications
 */
object PIDDecoder {
    
    /**
     * Decode a PID response from raw ECU data
     * @param pid The PID to decode
     * @param rawData The raw response data from the ECU
     * @return PIDValue object with decoded information, or null if decoding fails
     */
    fun decode(pid: Int, rawData: ByteArray): PIDValue? {
        // Validate response format: [41][PID][Data...]
        if (rawData.size < 3) {
            return null
        }
        
        val responseMode = rawData[0].toInt() and 0xFF
        if (responseMode != 0x41) {  // 0x41 is the response for mode 01
            return null
        }
        
        val responsePid = rawData[1].toInt() and 0xFF
        if (responsePid != pid) {
            return null  // PID mismatch
        }
        
        // Get the PID definition
        val pidDef = PIDRegistry.getPIDDefinition(pid)
        if (pidDef == null) {
            return null  // Unsupported PID
        }
        
        // Extract data portion
        val data = rawData.sliceArray(2 until rawData.size)
        
        // Validate data length
        if (data.size < pidDef.bytesRequired) {
            return null  // Insufficient data
        }
        
        // Calculate the actual value using the formula
        val actualData = data.sliceArray(0 until pidDef.bytesRequired)
        val calculatedValue = try {
            pidDef.formula(actualData)
        } catch (e: Exception) {
            return null  // Error in calculation
        }
        
        return PIDValue(
            pid = pid,
            name = pidDef.name,
            value = calculatedValue,
            unit = pidDef.unit,
            rawResponse = rawData
        )
    }
    
    /**
     * Decode multiple PIDs from a single response
     * This is used when multiple PIDs are requested in a single command
     */
    fun decodeMultiple(pids: List<Int>, rawData: ByteArray): List<PIDValue> {
        val results = mutableListOf<PIDValue>()
        
        if (rawData.size < 2) {
            return results
        }
        
        var idx = 2  // Skip response mode (0x41) and service ID
        
        for (pid in pids) {
            val pidDef = PIDRegistry.getPIDDefinition(pid) ?: continue
            
            if (idx + pidDef.bytesRequired > rawData.size) {
                break  // Not enough data remaining
            }
            
            val pidData = rawData.sliceArray(idx until idx + pidDef.bytesRequired)
            val fullResponse = byteArrayOf(0x41.toByte(), pid.toByte()) + pidData
            
            val pidValue = decode(pid, fullResponse)
            if (pidValue != null) {
                results.add(pidValue)
            }
            
            idx += pidDef.bytesRequired
        }
        
        return results
    }
    
    /**
     * Decode PID 0x00 (PID Support 01-20) or similar discovery PIDs
     */
    fun decodeDiscoveryPID(pid: Int, data: ByteArray): Set<Int> {
        val supportedPids = mutableSetOf<Int>()
        
        if (data.size < 4) {
            return supportedPids
        }
        
        // Each bit in the 4-byte response represents a PID support status
        for (i in 0 until 32) {  // 32 bits in 4 bytes
            val byteIndex = i / 8
            val bitIndex = i % 8
            val bitValue = (data[byteIndex].toInt() shr bitIndex) and 1
            if (bitValue == 1) {
                supportedPids.add(pid + i + 1)  // PIDs are 1-indexed
            }
        }
        
        return supportedPids
    }
}