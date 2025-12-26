package com.spacetec.obd.obd

object ObdCommands {
    // Mode 01 - Current Data
    const val ENGINE_RPM = "010C"
    const val VEHICLE_SPEED = "010D"
    const val COOLANT_TEMP = "0105"
    const val ENGINE_LOAD = "0104"
    const val THROTTLE_POS = "0111"
    const val FUEL_LEVEL = "012F"
    const val INTAKE_TEMP = "010F"
    const val MAF_RATE = "0110"
    const val FUEL_PRESSURE = "010A"
    const val TIMING_ADVANCE = "010E"
    const val SHORT_FUEL_TRIM = "0106"
    const val LONG_FUEL_TRIM = "0107"
    
    // Mode 03 - Read DTCs
    const val READ_DTCS = "03"
    
    // Mode 04 - Clear DTCs
    const val CLEAR_DTCS = "04"
    
    // Mode 07 - Pending DTCs
    const val PENDING_DTCS = "07"
    
    // Mode 09 - Vehicle Info
    const val VIN = "0902"
    
    fun parseRpm(response: String): Int? {
        val data = parseResponse(response, 4) ?: return null
        val a = data[0]
        val b = data[1]
        return ((a * 256) + b) / 4
    }
    
    fun parseSpeed(response: String): Int? {
        val data = parseResponse(response, 2) ?: return null
        return data[0]
    }
    
    fun parseCoolantTemp(response: String): Int? {
        val data = parseResponse(response, 2) ?: return null
        return data[0] - 40
    }
    
    fun parseEngineLoad(response: String): Int? {
        val data = parseResponse(response, 2) ?: return null
        return (data[0] * 100) / 255
    }
    
    fun parseThrottle(response: String): Int? {
        val data = parseResponse(response, 2) ?: return null
        return (data[0] * 100) / 255
    }
    
    fun parseFuelLevel(response: String): Int? {
        val data = parseResponse(response, 2) ?: return null
        return (data[0] * 100) / 255
    }
    
    fun parseIntakeTemp(response: String): Int? {
        val data = parseResponse(response, 2) ?: return null
        return data[0] - 40
    }
    
    fun parseMaf(response: String): Float? {
        val data = parseResponse(response, 4) ?: return null
        val a = data[0]
        val b = data[1]
        return ((a * 256) + b) / 100f
    }
    
    fun parseDtcs(response: String): List<String> {
        val dtcs = mutableListOf<String>()
        val clean = response.replace(" ", "").replace("43", "")
        
        if (clean.length < 4 || clean == "00" || clean.contains("NODATA")) {
            return dtcs
        }
        
        for (i in clean.indices step 4) {
            if (i + 4 <= clean.length) {
                val dtcBytes = clean.substring(i, i + 4)
                if (dtcBytes != "0000") {
                    val dtc = decodeDtc(dtcBytes)
                    if (dtc != null) dtcs.add(dtc)
                }
            }
        }
        return dtcs
    }
    
    private fun decodeDtc(hex: String): String? {
        if (hex.length != 4) return null
        
        val firstChar = when (hex[0]) {
            '0' -> 'P'; '1' -> 'P'; '2' -> 'P'; '3' -> 'P'
            '4' -> 'C'; '5' -> 'C'; '6' -> 'C'; '7' -> 'C'
            '8' -> 'B'; '9' -> 'B'; 'A' -> 'B'; 'B' -> 'B'
            'C' -> 'U'; 'D' -> 'U'; 'E' -> 'U'; 'F' -> 'U'
            else -> return null
        }
        
        val secondChar = (hex[0].digitToIntOrNull(16) ?: return null) % 4
        return "$firstChar$secondChar${hex.substring(1)}"
    }
    
    private fun parseResponse(response: String, expectedBytes: Int): List<Int>? {
        val clean = response.replace(" ", "")
        if (clean.length < expectedBytes * 2 + 4) return null
        
        val dataStart = 4 // Skip mode + PID bytes
        val bytes = mutableListOf<Int>()
        
        for (i in 0 until expectedBytes) {
            val idx = dataStart + (i * 2)
            if (idx + 2 <= clean.length) {
                val byte = clean.substring(idx, idx + 2).toIntOrNull(16) ?: return null
                bytes.add(byte)
            }
        }
        return bytes
    }
}
