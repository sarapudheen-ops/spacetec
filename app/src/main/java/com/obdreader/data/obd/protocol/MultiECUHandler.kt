package com.obdreader.data.obd.protocol

import android.util.Log

/**
 * Handler for multi-ECU vehicle communication
 */
class MultiECUHandler {
    
    companion object {
        private const val TAG = "MultiECUHandler"
        
        // Standard ECU request addresses (11-bit CAN)
        val ECU_ADDRESSES = mapOf(
            "7E0" to ECUType.ENGINE,
            "7E1" to ECUType.TRANSMISSION,
            "7E2" to ECUType.ABS,
            "7E3" to ECUType.AIRBAG,
            "7E4" to ECUType.BODY,
            "7E5" to ECUType.CLIMATE,
            "7DF" to ECUType.BROADCAST  // Functional addressing
        )
        
        // Response address offset
        const val RESPONSE_OFFSET = 0x08
        
        // Header patterns for parsing
        private val HEADER_PATTERN_11BIT = Regex("^([0-9A-Fa-f]{3})([0-9A-Fa-f]+)")
        private val HEADER_PATTERN_29BIT = Regex("^([0-9A-Fa-f]{8})([0-9A-Fa-f]+)")
    }
    
    enum class ECUType(val description: String) {
        ENGINE("Engine Control Unit"),
        TRANSMISSION("Transmission Control Unit"),
        ABS("Anti-lock Braking System"),
        AIRBAG("Airbag Control Unit"),
        BODY("Body Control Module"),
        CLIMATE("Climate Control"),
        BROADCAST("All ECUs"),
        UNKNOWN("Unknown ECU")
    }
    
    data class ECUResponse(
        val ecuAddress: String,
        val ecuType: ECUType,
        val responseData: ByteArray,
        val rawResponse: String
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as ECUResponse
            return ecuAddress == other.ecuAddress && responseData.contentEquals(other.responseData)
        }
        
        override fun hashCode(): Int {
            var result = ecuAddress.hashCode()
            result = 31 * result + responseData.contentHashCode()
            return result
        }
    }
    
    /**
     * Parse response with headers enabled (ATH1)
     */
    fun parseMultiECUResponse(response: String): List<ECUResponse> {
        val responses = mutableListOf<ECUResponse>()
        
        // Split by newlines and process each line
        val lines = response.split(Regex("[\\r\\n]+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith(">") && it != "OK" }
        
        for (line in lines) {
            val cleanLine = line.replace(" ", "")
            
            // Try 11-bit header
            val match11 = HEADER_PATTERN_11BIT.find(cleanLine)
            if (match11 != null) {
                val header = match11.groupValues[1].uppercase()
                val data = match11.groupValues[2]
                
                val ecuType = getECUTypeFromResponse(header)
                val dataBytes = hexStringToBytes(data)
                
                responses.add(ECUResponse(
                    ecuAddress = header,
                    ecuType = ecuType,
                    responseData = dataBytes,
                    rawResponse = line
                ))
                continue
            }
            
            // Try 29-bit header
            val match29 = HEADER_PATTERN_29BIT.find(cleanLine)
            if (match29 != null) {
                val header = match29.groupValues[1].uppercase()
                val data = match29.groupValues[2]
                
                val dataBytes = hexStringToBytes(data)
                
                responses.add(ECUResponse(
                    ecuAddress = header,
                    ecuType = ECUType.UNKNOWN,
                    responseData = dataBytes,
                    rawResponse = line
                ))
            }
        }
        
        return responses
    }
    
    /**
     * Get ECU type from response address
     */
    private fun getECUTypeFromResponse(responseAddress: String): ECUType {
        val addr = responseAddress.uppercase()
        
        // Response addresses are request + 8
        return when (addr) {
            "7E8" -> ECUType.ENGINE
            "7E9" -> ECUType.TRANSMISSION
            "7EA" -> ECUType.ABS
            "7EB" -> ECUType.AIRBAG
            "7EC" -> ECUType.BODY
            "7ED" -> ECUType.CLIMATE
            else -> ECUType.UNKNOWN
        }
    }
    
    /**
     * Create command to target specific ECU
     */
    fun createECUCommand(ecuType: ECUType, command: String): List<String> {
        val header = ECU_ADDRESSES.entries.find { it.value == ecuType }?.key
            ?: return listOf(command) // Use broadcast if not found
        
        return listOf(
            "ATSH$header", // Set header
            command
        )
    }
    
    /**
     * Create command with CAN receive filter
     */
    fun createFilteredCommand(ecuType: ECUType, command: String): List<String> {
        val requestAddr = ECU_ADDRESSES.entries.find { it.value == ecuType }?.key
            ?: return listOf(command)
        
        val responseAddr = calculateResponseAddress(requestAddr)
        
        return listOf(
            "ATSH$requestAddr",    // Set transmit header
            "ATCRA$responseAddr",   // Set receive filter
            command,
            "ATCRA"                 // Clear filter
        )
    }
    
    /**
     * Calculate expected response address from request address
     */
    fun calculateResponseAddress(requestAddress: String): String {
        return try {
            val addr = requestAddress.toInt(16)
            String.format("%03X", addr + RESPONSE_OFFSET)
        } catch (e: Exception) {
            Log.e(TAG, "Invalid address: $requestAddress", e)
            requestAddress
        }
    }
    
    /**
     * Group responses by ECU
     */
    fun groupByECU(responses: List<ECUResponse>): Map<ECUType, List<ECUResponse>> {
        return responses.groupBy { it.ecuType }
    }
    
    /**
     * Get primary ECU response (usually engine)
     */
    fun getPrimaryResponse(responses: List<ECUResponse>): ECUResponse? {
        return responses.find { it.ecuType == ECUType.ENGINE }
            ?: responses.firstOrNull()
    }
    
    private fun hexStringToBytes(hex: String): ByteArray {
        val cleanHex = hex.replace(" ", "")
        return ByteArray(cleanHex.length / 2) { i ->
            cleanHex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}
