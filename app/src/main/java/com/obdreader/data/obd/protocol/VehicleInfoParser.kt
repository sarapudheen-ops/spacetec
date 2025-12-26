package com.obdreader.data.obd.protocol

import android.util.Log
import com.obdreader.domain.model.VehicleInfo

/**
 * Parser for Mode 09 Vehicle Information
 */
class VehicleInfoParser {
    
    companion object {
        private const val TAG = "VehicleInfoParser"
        
        // Mode 09 InfoType IDs
        const val INFOTYPE_VIN_COUNT = 0x01
        const val INFOTYPE_VIN = 0x02
        const val INFOTYPE_CAL_ID_COUNT = 0x03
        const val INFOTYPE_CAL_ID = 0x04
        const val INFOTYPE_CVN_COUNT = 0x05
        const val INFOTYPE_CVN = 0x06
        const val INFOTYPE_PERF_COUNT = 0x07
        const val INFOTYPE_PERF = 0x08
        const val INFOTYPE_ECU_NAME_COUNT = 0x09
        const val INFOTYPE_ECU_NAME = 0x0A
        
        // VIN year codes
        val VIN_YEAR_CODES = mapOf(
            'A' to 2010, 'B' to 2011, 'C' to 2012, 'D' to 2013, 'E' to 2014,
            'F' to 2015, 'G' to 2016, 'H' to 2017, 'J' to 2018, 'K' to 2019,
            'L' to 2020, 'M' to 2021, 'N' to 2022, 'P' to 2023, 'R' to 2024,
            'S' to 2025, 'T' to 2026, 'V' to 2027, 'W' to 2028, 'X' to 2029,
            'Y' to 2030, '1' to 2001, '2' to 2002, '3' to 2003, '4' to 2004,
            '5' to 2005, '6' to 2006, '7' to 2007, '8' to 2008, '9' to 2009
        )
        
        // World Manufacturer Identifier (first 3 chars of VIN)
        val WMI_MANUFACTURERS = mapOf(
            "1G1" to "Chevrolet", "1G2" to "Pontiac", "1GC" to "Chevrolet Truck",
            "1FA" to "Ford", "1FB" to "Ford", "1FC" to "Ford", "1FD" to "Ford",
            "1C3" to "Chrysler", "1C4" to "Chrysler", "1C6" to "Chrysler",
            "JHM" to "Honda", "1HG" to "Honda", "2HG" to "Honda", "5J6" to "Honda",
            "JT" to "Toyota", "1NX" to "Toyota", "2T1" to "Toyota", "4T1" to "Toyota",
            "JN1" to "Nissan", "1N4" to "Nissan", "1N6" to "Nissan", "5N1" to "Nissan",
            "WVW" to "Volkswagen", "3VW" to "Volkswagen",
            "WBA" to "BMW", "WBS" to "BMW", "5UX" to "BMW",
            "WDB" to "Mercedes-Benz", "WDC" to "Mercedes-Benz", "WDD" to "Mercedes-Benz"
        )
    }
    
    /**
     * Parse VIN from Mode 09 response
     */
    fun parseVIN(response: String): String? {
        val cleanResponse = response.replace(Regex("[\\s\\r\\n>]"), "")
        
        // Handle multi-line response (ISO-TP)
        val lines = cleanResponse.split(Regex("49020[1-5]"))
            .filter { it.isNotEmpty() }
        
        val vinHex = if (lines.size > 1) {
            lines.joinToString("")
        } else {
            // Single line response
            if (cleanResponse.startsWith("4902")) {
                cleanResponse.substring(4)
            } else {
                cleanResponse
            }
        }
        
        // Convert hex to ASCII
        val vin = hexToAscii(vinHex)
        
        return if (vin.length >= 17) {
            val extractedVin = vin.substring(0, 17).filter { it.isLetterOrDigit() }
            if (isValidVIN(extractedVin)) extractedVin else null
        } else null
    }
    
    /**
     * Validate VIN format and check digit
     */
    fun isValidVIN(vin: String): Boolean {
        if (vin.length != 17) return false
        
        // Check for invalid characters
        if (vin.any { it in "IOQ" }) return false
        
        // Validate check digit (position 9)
        return validateCheckDigit(vin)
    }
    
    private fun validateCheckDigit(vin: String): Boolean {
        val transliteration = mapOf(
            'A' to 1, 'B' to 2, 'C' to 3, 'D' to 4, 'E' to 5, 'F' to 6, 'G' to 7, 'H' to 8,
            'J' to 1, 'K' to 2, 'L' to 3, 'M' to 4, 'N' to 5, 'P' to 7, 'R' to 9,
            'S' to 2, 'T' to 3, 'U' to 4, 'V' to 5, 'W' to 6, 'X' to 7, 'Y' to 8, 'Z' to 9
        )
        
        val weights = listOf(8, 7, 6, 5, 4, 3, 2, 10, 0, 9, 8, 7, 6, 5, 4, 3, 2)
        
        var sum = 0
        for (i in vin.indices) {
            val char = vin[i]
            val value = if (char.isDigit()) {
                char.digitToInt()
            } else {
                transliteration[char] ?: return false
            }
            sum += value * weights[i]
        }
        
        val checkDigit = sum % 11
        val expected = if (checkDigit == 10) 'X' else '0' + checkDigit
        
        return vin[8] == expected
    }
    
    /**
     * Decode VIN into vehicle information
     */
    fun decodeVIN(vin: String): VehicleInfo? {
        if (!isValidVIN(vin)) return null
        
        val wmi = vin.substring(0, 3)
        val manufacturer = findManufacturer(wmi)
        val year = VIN_YEAR_CODES[vin[9]]
        val plantCode = vin[10]
        val serialNumber = vin.substring(11, 17)
        
        return VehicleInfo(
            vin = vin,
            manufacturer = manufacturer,
            year = year,
            plantCode = plantCode,
            serialNumber = serialNumber,
            countryOfOrigin = getCountryFromWMI(wmi),
            vehicleType = getVehicleType(vin)
        )
    }
    
    private fun findManufacturer(wmi: String): String? {
        // Try exact 3-character match first
        WMI_MANUFACTURERS[wmi]?.let { return it }
        
        // Try 2-character prefix
        WMI_MANUFACTURERS[wmi.substring(0, 2)]?.let { return it }
        
        return null
    }
    
    private fun getCountryFromWMI(wmi: String): String {
        return when (wmi[0]) {
            '1', '4', '5' -> "United States"
            '2' -> "Canada"
            '3' -> "Mexico"
            'J' -> "Japan"
            'K' -> "South Korea"
            'S' -> "United Kingdom"
            'W' -> "Germany"
            'Y' -> "Sweden/Finland"
            'Z' -> "Italy"
            'V' -> "France"
            else -> "Unknown"
        }
    }
    
    private fun getVehicleType(vin: String): String {
        // Position 4-8 describe vehicle attributes (manufacturer-specific)
        // This is simplified - full decoding requires manufacturer-specific tables
        return when {
            vin.contains("TRUCK", ignoreCase = true) -> "Truck"
            vin.substring(3, 4).let { it == "A" || it == "B" } -> "Passenger Car"
            else -> "Vehicle"
        }
    }
    
    /**
     * Parse Calibration ID
     */
    fun parseCalibrationID(response: String): List<String> {
        val cleanResponse = response.replace(Regex("[\\s\\r\\n>]"), "")
        
        // Remove mode/infotype prefix
        val dataHex = if (cleanResponse.startsWith("4904")) {
            cleanResponse.substring(4)
        } else {
            cleanResponse
        }
        
        // Each calibration ID is typically 16 characters (32 hex digits)
        val calIds = mutableListOf<String>()
        for (i in dataHex.indices step 32) {
            if (i + 32 <= dataHex.length) {
                val calIdHex = dataHex.substring(i, i + 32)
                val calId = hexToAscii(calIdHex).trim()
                if (calId.isNotEmpty()) {
                    calIds.add(calId)
                }
            }
        }
        
        return calIds
    }
    
    /**
     * Parse Calibration Verification Number (CVN)
     */
    fun parseCVN(response: String): List<String> {
        val cleanResponse = response.replace(Regex("[\\s\\r\\n>]"), "")
        
        val dataHex = if (cleanResponse.startsWith("4906")) {
            cleanResponse.substring(4)
        } else {
            cleanResponse
        }
        
        // Each CVN is 4 bytes (8 hex digits)
        val cvns = mutableListOf<String>()
        for (i in dataHex.indices step 8) {
            if (i + 8 <= dataHex.length) {
                val cvn = dataHex.substring(i, i + 8).uppercase()
                if (cvn != "00000000") {
                    cvns.add(cvn)
                }
            }
        }
        
        return cvns
    }
    
    /**
     * Parse ECU Name
     */
    fun parseECUName(response: String): String? {
        val cleanResponse = response.replace(Regex("[\\s\\r\\n>]"), "")
        
        val dataHex = if (cleanResponse.startsWith("490A")) {
            cleanResponse.substring(4)
        } else {
            cleanResponse
        }
        
        return hexToAscii(dataHex).trim().takeIf { it.isNotEmpty() }
    }
    
    private fun hexToAscii(hex: String): String {
        val cleanHex = hex.replace(" ", "")
        val sb = StringBuilder()
        
        for (i in cleanHex.indices step 2) {
            if (i + 2 <= cleanHex.length) {
                try {
                    val charCode = cleanHex.substring(i, i + 2).toInt(16)
                    if (charCode in 32..126) { // Printable ASCII
                        sb.append(charCode.toChar())
                    }
                } catch (e: Exception) {
                    // Skip invalid hex
                }
            }
        }
        
        return sb.toString()
    }
    
    /**
     * Build Mode 09 command
     */
    fun buildCommand(infoType: Int): String {
        return String.format("09 %02X", infoType)
    }
}
