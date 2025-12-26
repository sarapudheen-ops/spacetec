package com.spacetec.protocol.obd.dtc

import com.spacetec.protocol.obd.DTC
import com.spacetec.protocol.obd.DTCStatus

/**
 * DTC Parser
 * Parses DTC codes and provides additional functionality for DTC handling
 */
object DTCParser {
    
    /**
     * Parse a DTC string into its components
     * @param dtcCode The DTC code string (e.g., "P0123")
     * @return DTCComponents object with parsed information
     */
    fun parseDTC(dtcCode: String): DTCComponents? {
        if (dtcCode.length != 5) {
            return null
        }
        
        val codeType = when (dtcCode[0]) {
            'P' -> DTCType.POWERTRAIN
            'C' -> DTCType.CHASSIS
            'B' -> DTCType.BODY
            'U' -> DTCType.NETWORK
            else -> return null
        }
        
        val system = dtcCode[1]
        val codeNumber = dtcCode.substring(2)
        
        return DTCComponents(
            code = dtcCode,
            type = codeType,
            system = system,
            number = codeNumber
        )
    }
    
    /**
     * Convert DTC string to bytes according to SAE J2012
     * @param dtcCode The DTC code string (e.g., "P0123")
     * @return ByteArray representing the DTC in ECU format
     */
    fun dtcToBytes(dtcCode: String): ByteArray? {
        if (dtcCode.length != 5) {
            return null
        }
        
        val type = when (dtcCode[0]) {
            'P' -> 0x00  // Powertrain
            'C' -> 0x40  // Chassis
            'B' -> 0x80  // Body
            'U' -> 0xC0  // Network
            else -> return null
        }
        
        val codePart = dtcCode.substring(1)  // "0123"
        val numericCode = codePart.toIntOrNull(16) ?: return null
        
        val highNibble = (numericCode and 0xF00) shr 8
        val lowNibble = numericCode and 0xFF
        
        // Combine type and high nibble for first byte
        val firstByte = (type or highNibble).toByte()
        val secondByte = lowNibble.toByte()
        
        return byteArrayOf(firstByte, secondByte)
    }
    
    /**
     * Convert bytes to DTC string according to SAE J2012
     * @param bytes The two bytes representing the DTC
     * @return DTC code string (e.g., "P0123")
     */
    fun bytesToDTC(bytes: ByteArray): String? {
        if (bytes.size != 2) {
            return null
        }
        
        val byte1 = bytes[0].toInt() and 0xFF
        val byte2 = bytes[1].toInt() and 0xFF
        
        val typePrefix = when (byte1 and 0xC0) {  // Check bits 7-6
            0x00 -> "P"  // Powertrain
            0x40 -> "C"  // Chassis
            0x80 -> "B"  // Body
            0xC0 -> "U"  // Network
            else -> return null
        }
        
        val code = ((byte1 and 0x3F) shl 8) or byte2
        return typePrefix + String.format("%04X", code)
    }
    
    /**
     * Check if a DTC is emissions-related
     * @param dtcCode The DTC code string
     * @return True if the DTC is emissions-related, false otherwise
     */
    fun isEmissionsRelated(dtcCode: String): Boolean {
        return when {
            dtcCode.startsWith("P0") -> true  // Generic emissions codes
            dtcCode.startsWith("P1") && isManufacturerEmissionsCode(dtcCode) -> true  // Manufacturer emissions codes
            else -> false
        }
    }
    
    /**
     * Check if a DTC is a manufacturer-specific emissions code
     * This is a simplified check - in practice, this would require a lookup table
     */
    private fun isManufacturerEmissionsCode(dtcCode: String): Boolean {
        // For this implementation, we'll consider some common manufacturer emissions codes
        // In a real implementation, this would use a comprehensive lookup table
        val manufacturerEmissionsCodes = setOf(
            "P1133", "P1134", "P1150", "P1151", "P0420", "P0430", "P0440", "P0455"
        )
        return dtcCode in manufacturerEmissionsCodes
    }
    
    /**
     * Get the system affected by the DTC
     */
    fun getSystemDescription(dtcCode: String): String {
        return when (dtcCode.getOrNull(1)) {
            '0' -> "Fuel and Air Metering"
            '1' -> "Fuel and Air Metering (Injector Circuit)"
            '2' -> "Fuel and Air Metering (Throttle Actuator)"
            '3' -> "Ignition System or Misfire"
            '4' -> "Auxiliary Emissions Controls"
            '5' -> "Vehicle Speed Controls and Idle Control Systems"
            '6' -> "Computer and Output Circuit"
            '7' -> "Transmission"
            '8' -> "Transmission"
            '9' -> "SAE Reserved"
            else -> "Unknown System"
        }
    }
    
    /**
     * DTC Components data class
     */
    data class DTCComponents(
        val code: String,
        val type: DTCType,
        val system: Char,
        val number: String
    )
    
    /**
     * DTC Type enum
     */
    enum class DTCType {
        POWERTRAIN,
        CHASSIS,
        BODY,
        NETWORK
    }
}