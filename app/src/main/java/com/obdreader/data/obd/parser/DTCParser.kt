package com.obdreader.data.obd.parser

import com.obdreader.domain.model.DTC
import com.obdreader.domain.model.DTCCategory
import com.obdreader.domain.model.DTCStatus
import com.obdreader.domain.model.DTCType

/**
 * Parses DTC responses from OBD-II modes 03, 07, and 0A.
 */
class DTCParser {
    
    /**
     * Parse Mode 03 response (stored DTCs).
     */
    fun parseMode03(response: String): List<DTC> {
        return parseDTCResponse(response, DTCStatus.STORED, 0x43)
    }
    
    /**
     * Parse Mode 07 response (pending DTCs).
     */
    fun parseMode07(response: String): List<DTC> {
        return parseDTCResponse(response, DTCStatus.PENDING, 0x47)
    }
    
    /**
     * Parse Mode 0A response (permanent DTCs).
     */
    fun parseMode0A(response: String): List<DTC> {
        return parseDTCResponse(response, DTCStatus.PERMANENT, 0x4A)
    }
    
    private fun parseDTCResponse(response: String, status: DTCStatus, modeResponse: Int): List<DTC> {
        val dtcs = mutableListOf<DTC>()
        val lines = response.split(Regex("[\r\n]+")).filter { it.isNotBlank() }.map { it.trim() }
        
        for (line in lines) {
            val lineDTCs = parseDTCLine(line, status, modeResponse)
            dtcs.addAll(lineDTCs)
        }
        
        return dtcs.distinctBy { it.code }
    }
    
    private fun parseDTCLine(line: String, status: DTCStatus, modeResponse: Int): List<DTC> {
        val dtcs = mutableListOf<DTC>()
        var cleanLine = line.replace(Regex("[^0-9A-Fa-f]"), "").uppercase()
        
        // Extract ECU address if headers present
        var ecuAddress: String? = null
        if (cleanLine.length >= 6 && isCANHeader(cleanLine.substring(0, 6))) {
            ecuAddress = cleanLine.substring(0, 6)
            cleanLine = cleanLine.substring(6)
        }
        
        // Find mode response
        val modeResponseHex = modeResponse.toString(16).uppercase().padStart(2, '0')
        val modeIndex = cleanLine.indexOf(modeResponseHex)
        if (modeIndex == -1) return dtcs
        
        var dataStart = modeIndex + 2
        
        // Skip DTC count for Mode 03
        if (modeResponse == 0x43) {
            dataStart += 2
        }
        
        // Parse DTC pairs
        val dtcData = cleanLine.substring(dataStart)
        var i = 0
        while (i + 4 <= dtcData.length) {
            val byte1 = dtcData.substring(i, i + 2).toIntOrNull(16)
            val byte2 = dtcData.substring(i + 2, i + 4).toIntOrNull(16)
            
            if (byte1 != null && byte2 != null && (byte1 != 0 || byte2 != 0)) {
                val dtc = parseDTCBytes(byte1, byte2, status, ecuAddress)
                if (dtc != null) {
                    dtcs.add(dtc)
                }
            }
            i += 4
        }
        
        return dtcs
    }
    
    private fun parseDTCBytes(byte1: Int, byte2: Int, status: DTCStatus, ecuAddress: String?): DTC? {
        val firstNibble = (byte1 shr 4) and 0x0F
        
        val (category, type, prefix) = when (firstNibble) {
            0, 1, 2, 3 -> Triple(DTCCategory.POWERTRAIN, DTCType.GENERIC, "P0")
            4, 5, 6, 7 -> Triple(DTCCategory.POWERTRAIN, DTCType.MANUFACTURER, "P${firstNibble - 4}")
            8, 9 -> Triple(DTCCategory.CHASSIS, DTCType.GENERIC, "C0")
            0xA, 0xB -> Triple(DTCCategory.CHASSIS, DTCType.MANUFACTURER, "C${firstNibble - 9}")
            0xC, 0xD -> Triple(DTCCategory.BODY, DTCType.GENERIC, "B0")
            0xE, 0xF -> Triple(DTCCategory.BODY, DTCType.MANUFACTURER, "B${firstNibble - 13}")
            else -> return null
        }
        
        val secondNibble = byte1 and 0x0F
        val code = "${prefix}${secondNibble.toString(16).uppercase()}${byte2.toString(16).uppercase().padStart(2, '0')}"
        
        return DTC(
            code = code,
            category = category,
            type = type,
            status = status,
            rawBytes = byteArrayOf(byte1.toByte(), byte2.toByte()),
            ecuAddress = ecuAddress,
            description = DTCDatabase.lookup(code)
        )
    }
    
    private fun isCANHeader(header: String): Boolean {
        if (header.length != 6) return false
        val commonHeaders = listOf("7E8", "7E9", "7EA", "7EB", "7EC", "7ED", "7EE", "7EF")
        return commonHeaders.any { header.startsWith(it) }
    }
}

/**
 * Database of common DTC descriptions.
 */
object DTCDatabase {
    private val descriptions = mapOf(
        "P0100" to "Mass or Volume Air Flow Circuit Malfunction",
        "P0101" to "Mass or Volume Air Flow Circuit Range/Performance",
        "P0105" to "Manifold Absolute Pressure/Barometric Pressure Circuit Malfunction",
        "P0110" to "Intake Air Temperature Circuit Malfunction",
        "P0115" to "Engine Coolant Temperature Circuit Malfunction",
        "P0120" to "Throttle Position Sensor/Switch A Circuit Malfunction",
        "P0125" to "Insufficient Coolant Temperature for Closed Loop Fuel Control",
        "P0130" to "O2 Sensor Circuit Malfunction (Bank 1, Sensor 1)",
        "P0171" to "System Too Lean (Bank 1)",
        "P0172" to "System Too Rich (Bank 1)",
        "P0300" to "Random/Multiple Cylinder Misfire Detected",
        "P0301" to "Cylinder 1 Misfire Detected",
        "P0302" to "Cylinder 2 Misfire Detected",
        "P0420" to "Catalyst System Efficiency Below Threshold (Bank 1)",
        "P0440" to "Evaporative Emission Control System Malfunction",
        "P0500" to "Vehicle Speed Sensor Malfunction",
        "P0700" to "Transmission Control System Malfunction"
    )
    
    fun lookup(code: String): String? = descriptions[code.uppercase()]
}
