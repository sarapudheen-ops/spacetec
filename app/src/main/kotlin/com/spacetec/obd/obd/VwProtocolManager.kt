package com.spacetec.obd.obd

import android.util.Log
import kotlinx.coroutines.delay

/**
 * VW/VAG-specific protocol manager with enhanced initialization.
 */
class VwProtocolManager(private val connection: ObdConnection) {

    data class VwInitResult(
        var success: Boolean = false,
        var stage: String = "",
        var errorMessage: String? = null,
        var vin: String? = null,
        var isVwVehicle: Boolean = false,
        var supportedPids: List<String> = emptyList(),
        var ecuInfo: Map<String, String> = emptyMap(),
        var protocol: String? = null
    )

    data class EcuInfo(
        val address: String,
        val name: String,
        val softwareVersion: String? = null,
        val hardwareVersion: String? = null,
        val partNumber: String? = null
    )

    // VW Group World Manufacturer Identifiers
    private val VW_GROUP_WMI = setOf(
        "WVW", "WV1", "WV2", "WV3",  // Volkswagen
        "WAU", "WUA",                 // Audi
        "WP0", "WP1",                 // Porsche
        "TRU", "3VW",                 // VW Mexico/USA
        "9BW",                        // VW Brazil
        "VSS",                        // SEAT
        "TMB",                        // Skoda
        "WBS", "WBA",                 // BMW (for comparison)
        "WDB", "WDC", "WDD"           // Mercedes (for comparison)
    )

    suspend fun initializeVwCommunication(): VwInitResult {
        val result = VwInitResult()

        try {
            // Stage 1: Verify basic OBD communication
            result.stage = "Verifying OBD communication"
            if (!verifyObdCommunication()) {
                result.errorMessage = "Failed to verify OBD communication"
                return result
            }

            // Stage 2: Get protocol info
            result.stage = "Getting protocol info"
            result.protocol = getProtocolDescription()

            // Stage 3: Get supported PIDs
            result.stage = "Querying supported PIDs"
            result.supportedPids = getSupportedPids()
            Log.d(TAG, "Supported PIDs: ${result.supportedPids.size}")

            // Stage 4: Get VIN
            result.stage = "Reading VIN"
            result.vin = getVin()
            Log.d(TAG, "VIN: ${result.vin}")

            // Stage 5: Check if VW vehicle
            result.stage = "Verifying vehicle manufacturer"
            result.vin?.let { vin ->
                if (vin.length >= 3) {
                    result.isVwVehicle = isVwVin(vin)
                    Log.d(TAG, "Is VW Group vehicle: ${result.isVwVehicle}")
                }
            }

            // Stage 6: Get ECU info
            result.stage = "Getting ECU information"
            result.ecuInfo = getEcuInfo()

            // Stage 7: Configure for VW if applicable
            if (result.isVwVehicle) {
                result.stage = "Configuring VW-specific settings"
                configureForVw()
            }

            result.success = true
            result.stage = "Initialization complete"

        } catch (e: Exception) {
            result.errorMessage = "VW initialization error: ${e.message}"
            Log.e(TAG, "VW initialization failed", e)
        }

        return result
    }

    private suspend fun verifyObdCommunication(): Boolean {
        val response = connection.sendCommand("0100")
        return response.contains("41 00") || response.contains("4100") ||
               response.matches(Regex(".*41\\s*00.*[0-9A-Fa-f]{8}.*"))
    }

    private suspend fun getProtocolDescription(): String {
        val response = connection.sendCommand("ATDP")
        return response.replace("AUTO,", "").trim()
    }

    suspend fun getSupportedPids(): List<String> {
        val supportedPids = mutableListOf<String>()

        // Query PID ranges: 00, 20, 40, 60, 80, A0, C0
        val ranges = listOf("0100", "0120", "0140", "0160", "0180", "01A0", "01C0")
        
        for ((index, cmd) in ranges.withIndex()) {
            val response = connection.sendCommand(cmd)
            if (response.contains("NO DATA") || response.contains("ERROR")) break
            
            val pids = parseSupportedPidsBitmap(response, index * 0x20)
            supportedPids.addAll(pids)
            
            // Check if next range is supported (last bit)
            val nextRangePid = String.format("%02X", (index + 1) * 0x20)
            if (!pids.contains(nextRangePid)) break
        }

        return supportedPids
    }

    private fun parseSupportedPidsBitmap(response: String, baseOffset: Int): List<String> {
        val pids = mutableListOf<String>()
        
        // Extract hex data after response code (41 XX)
        val hex = response.replace(Regex("[^0-9A-Fa-f]"), "")
        if (hex.length < 8) return pids // Need at least 4 bytes after header
        
        // Find data start (after 41 XX)
        val dataStart = if (hex.startsWith("41")) 4 else 0
        if (hex.length < dataStart + 8) return pids
        
        val bitmap = hex.substring(dataStart, dataStart + 8)
        
        for (i in 0 until 32) {
            val byteIndex = i / 8
            val bitIndex = 7 - (i % 8)
            val byteValue = bitmap.substring(byteIndex * 2, byteIndex * 2 + 2).toIntOrNull(16) ?: 0
            
            if ((byteValue shr bitIndex) and 1 == 1) {
                pids.add(String.format("%02X", baseOffset + i + 1))
            }
        }
        
        return pids
    }

    suspend fun getVin(): String? {
        // Try Mode 09 PID 02 (standard VIN request)
        val response = connection.sendCommand("0902")
        return parseVin(response)
    }

    private fun parseVin(response: String): String? {
        val vin = StringBuilder()
        
        // Clean response and extract hex bytes
        val clean = response.replace(Regex("[^0-9A-Fa-f\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        
        val parts = clean.split(" ").filter { it.length == 2 }
        
        var foundHeader = false
        for (part in parts) {
            // Skip response headers (49 02 XX)
            if (part.uppercase() == "49" || part.uppercase() == "02") {
                foundHeader = true
                continue
            }
            
            if (foundHeader || parts.indexOf(part) > 2) {
                val value = part.toIntOrNull(16) ?: continue
                // Valid ASCII printable characters
                if (value in 0x20..0x7E) {
                    vin.append(value.toChar())
                }
            }
        }
        
        val vinStr = vin.toString()
            .replace(Regex("[^A-HJ-NPR-Z0-9]"), "") // Valid VIN characters only
            .take(17)
        
        return if (vinStr.length >= 11) vinStr else null // Minimum useful VIN length
    }

    private fun isVwVin(vin: String): Boolean {
        if (vin.length < 3) return false
        val wmi = vin.substring(0, 3).uppercase()
        return VW_GROUP_WMI.any { wmi.startsWith(it.take(wmi.length.coerceAtMost(it.length))) }
    }

    suspend fun getEcuInfo(): Map<String, String> {
        val ecuInfo = mutableMapOf<String, String>()

        // ECU Name (Mode 09 PID 0A)
        try {
            val response = connection.sendCommand("090A")
            if (!response.contains("NO DATA") && !response.contains("ERROR")) {
                ecuInfo["ECU_NAME"] = parseAsciiResponse(response)
            }
        } catch (e: Exception) { }

        // Calibration ID (Mode 09 PID 04)
        try {
            val response = connection.sendCommand("0904")
            if (!response.contains("NO DATA") && !response.contains("ERROR")) {
                ecuInfo["CALIBRATION_ID"] = parseAsciiResponse(response)
            }
        } catch (e: Exception) { }

        // CVN (Mode 09 PID 06)
        try {
            val response = connection.sendCommand("0906")
            if (!response.contains("NO DATA") && !response.contains("ERROR")) {
                ecuInfo["CVN"] = response.replace(Regex("[^0-9A-Fa-f]"), "").takeLast(8)
            }
        } catch (e: Exception) { }

        return ecuInfo
    }

    private fun parseAsciiResponse(response: String): String {
        val result = StringBuilder()
        val hex = response.replace(Regex("[^0-9A-Fa-f]"), "")
        
        // Skip header bytes and parse ASCII
        var i = 4 // Skip response header
        while (i + 1 < hex.length) {
            val value = hex.substring(i, i + 2).toIntOrNull(16) ?: 0
            if (value in 0x20..0x7E) {
                result.append(value.toChar())
            }
            i += 2
        }
        
        return result.toString().trim()
    }

    private suspend fun configureForVw() {
        // Enable headers for multi-ECU communication
        connection.sendCommand("ATH1")
        
        // Set timeout for VW vehicles (some need longer)
        connection.sendCommand("ATST FF")
        
        // Allow long messages
        connection.sendCommand("ATAL")
    }

    /**
     * Scan all ECUs (VW vehicles often have 50+ ECUs)
     */
    suspend fun scanAllEcus(): List<EcuInfo> {
        val ecus = mutableListOf<EcuInfo>()
        
        // Standard OBD ECU addresses
        val standardAddresses = listOf("7E0", "7E1", "7E2", "7E3", "7E4", "7E5", "7E6", "7E7")
        
        for (addr in standardAddresses) {
            try {
                // Set header to specific ECU
                connection.sendCommand("ATSH$addr")
                delay(100)
                
                val response = connection.sendCommand("0100")
                if (!response.contains("NO DATA") && !response.contains("ERROR")) {
                    ecus.add(EcuInfo(
                        address = addr,
                        name = getEcuName(addr)
                    ))
                }
            } catch (e: Exception) { }
        }
        
        // Reset to functional addressing
        connection.sendCommand("ATSH7DF")
        
        return ecus
    }

    private fun getEcuName(address: String): String {
        return when (address) {
            "7E0" -> "Engine Control Module (ECM)"
            "7E1" -> "Transmission Control Module (TCM)"
            "7E2" -> "ABS/ESP Module"
            "7E3" -> "Airbag Control Module"
            "7E4" -> "Body Control Module (BCM)"
            "7E5" -> "Climate Control Module"
            "7E6" -> "Steering Control Module"
            "7E7" -> "Gateway Module"
            else -> "ECU $address"
        }
    }

    companion object {
        private const val TAG = "VwProtocolManager"
    }
}
