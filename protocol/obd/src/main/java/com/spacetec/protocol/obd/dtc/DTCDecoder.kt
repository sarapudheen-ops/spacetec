package com.spacetec.protocol.obd.dtc

import com.spacetec.protocol.obd.DTC
import com.spacetec.protocol.obd.DTCSeverity
import com.spacetec.protocol.obd.DTCStatus

/**
 * DTC Decoder
 * Implements DTC decoding according to SAE J2012 standards
 */
object DTCDecoder {
    
    /**
     * Decode stored DTCs from ECU response
     * @param rawData Raw response from ECU for service 03
     * @return List of decoded DTC objects
     */
    fun decodeStoredDTCs(rawData: ByteArray): List<DTC> {
        val dtcs = mutableListOf<DTC>()
        
        // Response format: [43][DTC1 byte1][DTC1 byte2][DTC2 byte1][DTC2 byte2]...
        if (rawData.size < 3 || rawData[0].toInt() != 0x43) {  // 0x43 is response for service 03
            return dtcs
        }
        
        var idx = 1  // Skip response mode (0x43)
        
        while (idx + 1 < rawData.size) {
            val dtcBytes = byteArrayOf(rawData[idx], rawData[idx + 1])
            val dtc = decodeDTC(dtcBytes, DTCType.STORED)
            if (dtc != null) {
                dtcs.add(dtc)
            }
            idx += 2  // Move to next DTC (2 bytes per DTC)
        }
        
        return dtcs
    }
    
    /**
     * Decode pending DTCs from ECU response
     * @param rawData Raw response from ECU for service 07
     * @return List of decoded DTC objects
     */
    fun decodePendingDTCs(rawData: ByteArray): List<DTC> {
        val dtcs = mutableListOf<DTC>()
        
        // Response format: [47][DTC1 byte1][DTC1 byte2][DTC2 byte1][DTC2 byte2]...
        if (rawData.size < 3 || rawData[0].toInt() != 0x47) {  // 0x47 is response for service 07
            return dtcs
        }
        
        var idx = 1  // Skip response mode (0x47)
        
        while (idx + 1 < rawData.size) {
            val dtcBytes = byteArrayOf(rawData[idx], rawData[idx + 1])
            val dtc = decodeDTC(dtcBytes, DTCType.PENDING)
            if (dtc != null) {
                dtcs.add(dtc)
            }
            idx += 2  // Move to next DTC (2 bytes per DTC)
        }
        
        return dtcs
    }
    
    /**
     * Decode permanent DTCs from ECU response
     * @param rawData Raw response from ECU for service 0A
     * @return List of decoded DTC objects
     */
    fun decodePermanentDTCs(rawData: ByteArray): List<DTC> {
        val dtcs = mutableListOf<DTC>()
        
        // Response format: [4A][DTC1 byte1][DTC1 byte2][DTC2 byte1][DTC2 byte2]...
        if (rawData.size < 3 || rawData[0].toInt() != 0x4A) {  // 0x4A is response for service 0A
            return dtcs
        }
        
        var idx = 1  // Skip response mode (0x4A)
        
        while (idx + 1 < rawData.size) {
            val dtcBytes = byteArrayOf(rawData[idx], rawData[idx + 1])
            val dtc = decodeDTC(dtcBytes, DTCType.PERMANENT)
            if (dtc != null) {
                dtcs.add(dtc)
            }
            idx += 2  // Move to next DTC (2 bytes per DTC)
        }
        
        return dtcs
    }
    
    /**
     * Decode a single DTC from 2 bytes
     * @param dtcBytes Two bytes representing the DTC
     * @param type Type of DTC (stored, pending, permanent)
     * @return Decoded DTC object or null if decoding fails
     */
    private fun decodeDTC(dtcBytes: ByteArray, type: DTCType): DTC? {
        if (dtcBytes.size != 2) {
            return null
        }
        
        val byte1 = dtcBytes[0].toInt() and 0xFF
        val byte2 = dtcBytes[1].toInt() and 0xFF
        
        // Extract DTC components according to SAE J2012
        val dtcType = (byte1 and 0xC0) shr 6  // Bits 7-6: DTC type
        val codeGroup = (byte1 and 0x30) shr 4  // Bits 5-4: Code group
        val codeHigh = byte1 and 0x0F  // Bits 3-0: Code high nibble
        val codeLow = (byte2 and 0xF0) shr 4  // Bits 7-4: Code low nibble (high part)
        val codeLow2 = byte2 and 0x0F  // Bits 3-0: Code low nibble (low part)
        
        // Construct the DTC code
        val dtcCode = when (dtcType) {
            0 -> "P"  // Powertrain
            1 -> "C"  // Chassis
            2 -> "B"  // Body
            3 -> "U"  // Network
            else -> return null
        }
        
        val codeNumber = String.format("%02X%02X", (codeGroup shl 4) or codeHigh, (codeLow shl 4) or codeLow2)
        val fullCode = dtcCode + codeNumber
        
        // Determine DTC status based on type
        val status = when (type) {
            DTCType.STORED -> DTCStatus(
                isPending = false,
                isStored = true,
                isTestFailed = true,
                isTestFailedThisCycle = false,
                isTestCompletedSinceLastClear = true,
                isTestFailedSinceLastClear = true,
                isConfirmed = true,
                isCurrentlyActive = false
            )
            DTCType.PENDING -> DTCStatus(
                isPending = true,
                isStored = false,
                isTestFailed = true,
                isTestFailedThisCycle = true,
                isTestCompletedSinceLastClear = false,
                isTestFailedSinceLastClear = true,
                isConfirmed = false,
                isCurrentlyActive = true
            )
            DTCType.PERMANENT -> DTCStatus(
                isPending = false,
                isStored = true,
                isTestFailed = true,
                isTestFailedThisCycle = false,
                isTestCompletedSinceLastClear = true,
                isTestFailedSinceLastClear = true,
                isConfirmed = true,
                isCurrentlyActive = false
            )
        }
        
        // Determine severity based on code
        val severity = determineSeverity(fullCode)
        
        // Get description based on the DTC code
        val description = getDTCDescription(fullCode)
        
        return DTC(
            code = fullCode,
            description = description,
            status = status,
            severity = severity
        )
    }
    
    /**
     * Determine DTC severity based on code
     */
    private fun determineSeverity(dtcCode: String): DTCSeverity {
        return when {
            dtcCode.startsWith("P0") -> DTCSeverity.HIGH  // Emissions-related powertrain codes
            dtcCode.startsWith("P1") -> DTCSeverity.MEDIUM  // Manufacturer-specific powertrain codes
            dtcCode.startsWith("C") -> DTCSeverity.MEDIUM  // Chassis codes
            dtcCode.startsWith("B") -> DTCSeverity.LOW  // Body codes
            dtcCode.startsWith("U") -> DTCSeverity.MEDIUM  // Network codes
            else -> DTCSeverity.LOW
        }
    }
    
    /**
     * Get DTC description based on code
     */
    private fun getDTCDescription(dtcCode: String): String {
        return when (dtcCode) {
            // Common P-codes
            "P0123" -> "Throttle/Pedal Position Sensor/Switch A Circuit High"
            "P0245" -> "Turbocharger Wastegate Solenoid A Low"
            "P0467" -> "Evaporative Emission System Pressure Sensor Range/Performance"
            "P0189" -> "Fuel Temperature Sensor A Circuit Intermittent"
            "P03AB" -> "Turbocharger Boost Control Position Sensor B Circuit Intermittent/Erratic"
            "P0111" -> "Intake Air Temperature Circuit Range/Performance Problem"
            "P0222" -> "Throttle/Pedal Position Switch B Circuit Low"
            "P0555" -> "Brake Booster Pressure Circuit Range/Performance"
            else -> "Diagnostic Trouble Code $dtcCode"
        }
    }
    
    /**
     * DTC Type enum
     */
    private enum class DTCType {
        STORED,
        PENDING,
        PERMANENT
    }
}