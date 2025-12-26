package com.obdreader.data.obd.parser

import com.obdreader.util.HexUtils

/**
 * Parses OBD-II responses into structured data.
 */
class ResponseParser {
    
    /**
     * Extract data bytes from response, skipping headers and mode bytes.
     */
    fun extractDataBytes(response: String, expectedBytes: Int): ByteArray {
        val cleanResponse = response.replace(Regex("[^0-9A-Fa-f]"), "").uppercase()
        
        // Find the start of actual data (after headers and mode response)
        val dataStart = findDataStart(cleanResponse)
        val dataEnd = minOf(dataStart + expectedBytes * 2, cleanResponse.length)
        
        return if (dataStart < cleanResponse.length) {
            HexUtils.hexToBytes(cleanResponse.substring(dataStart, dataEnd))
        } else {
            byteArrayOf()
        }
    }
    
    /**
     * Parse VIN from Mode 09 PID 02 response.
     */
    fun parseVIN(response: String): String? {
        val dataBytes = extractMultiFrameData(response)
        return if (dataBytes.size >= 17) {
            String(dataBytes.sliceArray(0..16), Charsets.US_ASCII)
        } else null
    }
    
    /**
     * Parse Calibration ID from Mode 09 PID 04 response.
     */
    fun parseCalibrationId(response: String): String? {
        val dataBytes = extractMultiFrameData(response)
        return if (dataBytes.isNotEmpty()) {
            String(dataBytes, Charsets.US_ASCII).trim()
        } else null
    }
    
    /**
     * Parse ECU Name from Mode 09 PID 0A response.
     */
    fun parseECUName(response: String): String? {
        val dataBytes = extractMultiFrameData(response)
        return if (dataBytes.isNotEmpty()) {
            String(dataBytes, Charsets.US_ASCII).trim()
        } else null
    }
    
    private fun findDataStart(cleanResponse: String): Int {
        // Look for common response patterns and skip headers
        val patterns = listOf("49", "41", "43", "47", "4A")
        
        for (pattern in patterns) {
            val index = cleanResponse.indexOf(pattern)
            if (index != -1) {
                return index + pattern.length + 2 // Skip mode response and PID
            }
        }
        
        return 0
    }
    
    private fun extractMultiFrameData(response: String): ByteArray {
        val lines = response.split(Regex("[\r\n]+")).filter { it.isNotBlank() }
        val allData = mutableListOf<Byte>()
        
        for (line in lines) {
            val dataBytes = extractDataBytes(line, 100) // Large buffer
            allData.addAll(dataBytes.toList())
        }
        
        return allData.toByteArray()
    }
}
