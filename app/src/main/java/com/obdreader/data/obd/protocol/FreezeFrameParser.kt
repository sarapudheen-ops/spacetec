package com.obdreader.data.obd.protocol

import com.obdreader.domain.models.FreezeFrame
import com.obdreader.domain.model.PIDResponse
import com.obdreader.data.obd.parser.PIDParser

/**
 * Parser for Mode 02 Freeze Frame data
 */
class FreezeFrameParser(
    private val pidParser: PIDParser
) {
    
    companion object {
        const val MODE_FREEZE_FRAME = 0x02
        
        // Common freeze frame PIDs (same as Mode 01)
        val FREEZE_FRAME_PIDS = listOf(
            0x02, // DTC that triggered freeze frame
            0x03, // Fuel system status
            0x04, // Calculated engine load
            0x05, // Engine coolant temperature
            0x06, // Short term fuel trim B1
            0x07, // Long term fuel trim B1
            0x0C, // Engine RPM
            0x0D, // Vehicle speed
            0x0E, // Timing advance
            0x0F, // Intake air temperature
            0x10, // MAF air flow rate
            0x11  // Throttle position
        )
    }
    
    /**
     * Parse freeze frame response for a specific DTC
     */
    fun parseFreezeFrame(frameNumber: Int, responses: Map<Int, String>): FreezeFrame {
        val pidResponses = mutableListOf<PIDResponse>()
        var triggerDTC: String? = null
        
        for ((pid, response) in responses) {
            if (pid == 0x02) {
                // Parse trigger DTC
                triggerDTC = parseTriggerDTC(response)
            } else {
                // Parse regular PID using same formulas as Mode 01
                pidParser.parse(MODE_FREEZE_FRAME, pid, response)?.let { pidResponse ->
                    pidResponses.add(pidResponse)
                }
            }
        }
        
        return FreezeFrame(
            frameNumber = frameNumber,
            triggerDTC = triggerDTC,
            data = pidResponses,
            timestamp = System.currentTimeMillis()
        )
    }
    
    /**
     * Parse the DTC that triggered the freeze frame (PID 02)
     */
    private fun parseTriggerDTC(response: String): String? {
        val cleanResponse = response.replace(Regex("[^0-9A-Fa-f]"), "")
        
        // Skip mode and PID bytes (42 02)
        if (cleanResponse.length < 8) return null
        
        val dtcBytes = cleanResponse.substring(4, 8)
        return parseDTCBytes(dtcBytes)
    }
    
    private fun parseDTCBytes(hex: String): String? {
        if (hex.length != 4 || hex == "0000") return null
        
        val byte1 = hex.substring(0, 2).toInt(16)
        val byte2 = hex.substring(2, 4).toInt(16)
        
        val firstNibble = (byte1 shr 4) and 0x0F
        val category = when (firstNibble) {
            in 0..3 -> 'P'
            in 4..7 -> 'C'
            in 8..0xB -> 'B'
            else -> 'U'
        }
        
        val codeDigit1 = firstNibble % 4
        val codeDigit2 = byte1 and 0x0F
        val codeDigit3 = (byte2 shr 4) and 0x0F
        val codeDigit4 = byte2 and 0x0F
        
        return "$category${codeDigit1}${codeDigit2.toString(16).uppercase()}${codeDigit3.toString(16).uppercase()}${codeDigit4.toString(16).uppercase()}"
    }
    
    /**
     * Build command to request freeze frame data
     */
    fun buildFreezeFrameCommand(pid: Int, frameNumber: Int): String {
        return String.format("%02X %02X %02X", MODE_FREEZE_FRAME, pid, frameNumber)
    }
    
    /**
     * Get list of freeze frame PIDs to request
     */
    fun getFreezeFramePIDs(): List<Int> = FREEZE_FRAME_PIDS
}
