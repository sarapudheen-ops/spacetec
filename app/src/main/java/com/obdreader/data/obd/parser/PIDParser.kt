package com.obdreader.data.obd.parser

import com.obdreader.domain.model.PIDResponse
import com.obdreader.util.HexUtils

/**
 * Parses OBD-II PID responses into structured data.
 */
class PIDParser {
    
    private val definitions: Map<Int, PIDDefinition> = buildPIDDefinitions()
    
    /**
     * Parse a single PID response.
     */
    fun parse(mode: Int, pid: Int, rawResponse: String): PIDResponse? {
        val definition = definitions[pid] ?: return null
        
        return try {
            val dataBytes = extractDataBytes(rawResponse, mode, pid, definition.bytes)
            if (dataBytes.size < definition.bytes) return null
            
            val value = definition.formula(dataBytes)
            
            PIDResponse(
                mode = mode,
                pid = pid,
                name = definition.name,
                rawData = dataBytes,
                value = value,
                unit = definition.unit,
                minValue = definition.minValue,
                maxValue = definition.maxValue
            )
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Parse multiple PIDs from a batch response.
     */
    fun parseBatch(mode: Int, pids: List<Int>, rawResponse: String): List<PIDResponse> {
        val responses = mutableListOf<PIDResponse>()
        val lines = rawResponse.split(Regex("[\r\n]+")).filter { it.isNotBlank() }
        
        for (line in lines) {
            for (pid in pids) {
                val response = parse(mode, pid, line)
                if (response != null) {
                    responses.add(response)
                }
            }
        }
        
        return responses
    }
    
    private fun extractDataBytes(response: String, mode: Int, pid: Int, expectedBytes: Int): ByteArray {
        val cleanResponse = response.replace(Regex("[^0-9A-Fa-f]"), "").uppercase()
        
        val modeResponse = (mode + 0x40).toString(16).uppercase().padStart(2, '0')
        val pidHex = pid.toString(16).uppercase().padStart(2, '0')
        val searchPattern = "$modeResponse$pidHex"
        
        val startIndex = cleanResponse.indexOf(searchPattern)
        if (startIndex == -1) return byteArrayOf()
        
        val dataStart = startIndex + 4
        val dataEnd = minOf(dataStart + expectedBytes * 2, cleanResponse.length)
        
        return HexUtils.hexToBytes(cleanResponse.substring(dataStart, dataEnd))
    }
    
    private fun buildPIDDefinitions(): Map<Int, PIDDefinition> {
        return mapOf(
            0x04 to PIDDefinition(0x04, "Calculated Engine Load", "Load", "Calculated engine load value", 1, PIDFormulas.CALCULATED_ENGINE_LOAD, "%", 0.0, 100.0, PIDCategory.ENGINE),
            0x05 to PIDDefinition(0x05, "Engine Coolant Temperature", "Coolant", "Engine coolant temperature", 1, PIDFormulas.ENGINE_COOLANT_TEMP, "°C", -40.0, 215.0, PIDCategory.TEMPERATURE),
            0x06 to PIDDefinition(0x06, "Short Term Fuel Trim - Bank 1", "STFT B1", "Short term fuel trim - Bank 1", 1, PIDFormulas.SHORT_TERM_FUEL_TRIM_B1, "%", -100.0, 99.2, PIDCategory.FUEL),
            0x07 to PIDDefinition(0x07, "Long Term Fuel Trim - Bank 1", "LTFT B1", "Long term fuel trim - Bank 1", 1, PIDFormulas.LONG_TERM_FUEL_TRIM_B1, "%", -100.0, 99.2, PIDCategory.FUEL),
            0x0A to PIDDefinition(0x0A, "Fuel Pressure", "Fuel Pres", "Fuel pressure gauge", 1, PIDFormulas.FUEL_PRESSURE, "kPa", 0.0, 765.0, PIDCategory.FUEL),
            0x0B to PIDDefinition(0x0B, "Intake Manifold Pressure", "MAP", "Intake manifold absolute pressure", 1, PIDFormulas.INTAKE_MANIFOLD_PRESSURE, "kPa", 0.0, 255.0, PIDCategory.ENGINE),
            0x0C to PIDDefinition(0x0C, "Engine RPM", "RPM", "Engine speed in rotations per minute", 2, PIDFormulas.ENGINE_RPM, "rpm", 0.0, 16383.75, PIDCategory.ENGINE),
            0x0D to PIDDefinition(0x0D, "Vehicle Speed", "Speed", "Current vehicle speed", 1, PIDFormulas.VEHICLE_SPEED, "km/h", 0.0, 255.0, PIDCategory.SPEED),
            0x0E to PIDDefinition(0x0E, "Timing Advance", "Timing", "Timing advance relative to TDC", 1, PIDFormulas.TIMING_ADVANCE, "°", -64.0, 63.5, PIDCategory.ENGINE),
            0x0F to PIDDefinition(0x0F, "Intake Air Temperature", "IAT", "Intake air temperature", 1, PIDFormulas.INTAKE_AIR_TEMP, "°C", -40.0, 215.0, PIDCategory.TEMPERATURE),
            0x10 to PIDDefinition(0x10, "MAF Air Flow Rate", "MAF", "Mass air flow sensor rate", 2, PIDFormulas.MAF_AIR_FLOW, "g/s", 0.0, 655.35, PIDCategory.ENGINE),
            0x11 to PIDDefinition(0x11, "Throttle Position", "Throttle", "Throttle position", 1, PIDFormulas.THROTTLE_POSITION, "%", 0.0, 100.0, PIDCategory.ENGINE),
            0x1F to PIDDefinition(0x1F, "Run Time Since Engine Start", "Run Time", "Time since engine start", 2, PIDFormulas.RUN_TIME, "s", 0.0, 65535.0, PIDCategory.ENGINE)
        )
    }
}
