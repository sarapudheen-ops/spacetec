/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 */
package com.spacetec.vehicle.brands.volkswagen.vw

import android.util.Log
import com.spacetec.vehicle.brands.volkswagen.elm327.ELM327Connection
import com.spacetec.vehicle.brands.volkswagen.elm327.ELM327ProtocolHandler
import java.io.IOException

/**
 * VW-specific OBD2 protocol handler
 * Handles VW-specific PIDs, ECU addressing, and data parsing
 */
class VWProtocolManager(
    private val connection: ELM327Connection,
    private val protocolHandler: ELM327ProtocolHandler
) {

    enum class StandardPID(val pid: String, val description: String) {
        SUPPORTED_PIDS_01_20("0100", "Supported PIDs 01-20"),
        MONITOR_STATUS("0101", "Monitor status since DTCs cleared"),
        FREEZE_DTC("0102", "Freeze DTC"),
        FUEL_SYSTEM_STATUS("0103", "Fuel system status"),
        ENGINE_LOAD("0104", "Calculated engine load"),
        COOLANT_TEMP("0105", "Engine coolant temperature"),
        SHORT_TERM_FUEL_TRIM_1("0106", "Short term fuel trim - Bank 1"),
        LONG_TERM_FUEL_TRIM_1("0107", "Long term fuel trim - Bank 1"),
        FUEL_PRESSURE("010A", "Fuel pressure"),
        INTAKE_MANIFOLD_PRESSURE("010B", "Intake manifold absolute pressure"),
        ENGINE_RPM("010C", "Engine RPM"),
        VEHICLE_SPEED("010D", "Vehicle speed"),
        TIMING_ADVANCE("010E", "Timing advance"),
        INTAKE_AIR_TEMP("010F", "Intake air temperature"),
        MAF_AIR_FLOW("0110", "MAF air flow rate"),
        THROTTLE_POSITION("0111", "Throttle position"),
        ENGINE_RUN_TIME("011F", "Run time since engine start"),
        SUPPORTED_PIDS_21_40("0120", "Supported PIDs 21-40"),
        DISTANCE_WITH_MIL("0121", "Distance traveled with MIL on"),
        FUEL_TANK_LEVEL("012F", "Fuel Tank Level Input"),
        BAROMETRIC_PRESSURE("0133", "Barometric pressure"),
        SUPPORTED_PIDS_41_60("0140", "Supported PIDs 41-60"),
        CONTROL_MODULE_VOLTAGE("0142", "Control module voltage"),
        AMBIENT_AIR_TEMP("0146", "Ambient air temperature"),
        ENGINE_FUEL_RATE("015E", "Engine fuel rate")
    }

    @Throws(IOException::class)
    fun initializeVWCommunication(): VWInitResult {
        val result = VWInitResult()

        try {
            result.stage = "Verifying OBD communication"
            if (!verifyOBDCommunication()) {
                result.success = false
                result.errorMessage = "Failed to verify OBD communication"
                return result
            }

            result.stage = "Getting supported PIDs"
            result.supportedPIDs = getSupportedPIDs()

            result.stage = "Getting VIN"
            result.vin = getVIN()

            result.stage = "Verifying VW vehicle"
            result.vin?.let { vin ->
                if (vin.length >= 3) {
                    result.isVWVehicle = isVWVin(vin)
                }
            }

            result.stage = "Getting ECU information"
            result.ecuInfo = getECUInfo()

            if (result.isVWVehicle) {
                result.stage = "Configuring VW-specific settings"
                configureForVW()
            }

            result.success = true

        } catch (e: Exception) {
            result.success = false
            result.errorMessage = "VW initialization error: ${e.message}"
            result.exception = e
            Log.e(TAG, "VW initialization failed", e)
        }

        return result
    }

    @Throws(IOException::class)
    private fun verifyOBDCommunication(): Boolean {
        val response = protocolHandler.sendOBDCommand("0100", 5000)
        return response.success
    }

    @Throws(IOException::class)
    fun getSupportedPIDs(): List<String> {
        val supportedPIDs = mutableListOf<String>()

        parseSupportedPIDs("0100", 0x00, supportedPIDs)
        if (supportedPIDs.contains("20")) parseSupportedPIDs("0120", 0x20, supportedPIDs)
        if (supportedPIDs.contains("40")) parseSupportedPIDs("0140", 0x40, supportedPIDs)
        if (supportedPIDs.contains("60")) parseSupportedPIDs("0160", 0x60, supportedPIDs)

        Log.d(TAG, "Supported PIDs: ${supportedPIDs.size}")
        return supportedPIDs
    }

    @Throws(IOException::class)
    private fun parseSupportedPIDs(command: String, baseOffset: Int, supportedPIDs: MutableList<String>) {
        val response = protocolHandler.sendOBDCommand(command, 5000)

        if (!response.success || response.data == null || response.data.size < 6) return

        val data = response.data
        val dataStart = 2

        if (data.size >= dataStart + 4) {
            for (i in 0 until 4) {
                val b = data[dataStart + i]
                for (bit in 0 until 8) {
                    if ((b.toInt() and (0x80 shr bit)) != 0) {
                        val pid = baseOffset + (i * 8) + bit + 1
                        supportedPIDs.add(String.format("%02X", pid))
                    }
                }
            }
        }
    }

    @Throws(IOException::class)
    fun getVIN(): String? {
        val response = protocolHandler.sendOBDCommand("0902", 10000)
        if (!response.success) {
            Log.d(TAG, "VIN request failed")
            return null
        }
        return parseVIN(response.rawResponse)
    }

    private fun parseVIN(response: String): String? {
        val vin = StringBuilder()
        val clean = response.replace(Regex("[^0-9A-Fa-f]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        val parts = clean.split(" ")

        var foundHeader = false
        for (part in parts) {
            if (part.length == 2) {
                if (part == "49" || part == "02") {
                    foundHeader = true
                    continue
                }
                if (foundHeader) {
                    runCatching {
                        val value = part.toInt(16)
                        if (value in 0x20..0x7E) {
                            vin.append(value.toChar())
                        }
                    }
                }
            }
        }

        val vinStr = vin.toString().trim()
        return if (vinStr.length >= 17) vinStr.substring(0, 17) else vinStr.ifEmpty { null }
    }

    private fun isVWVin(vin: String): Boolean {
        if (vin.length < 3) return false

        val wmi = vin.substring(0, 3).uppercase()
        return VW_GROUP_WMI.contains(wmi)
    }

    @Throws(IOException::class)
    fun getECUInfo(): Map<String, String> {
        val ecuInfo = mutableMapOf<String, String>()

        runCatching {
            val response = protocolHandler.sendOBDCommand("090A", 5000)
            if (response.success && response.data != null) {
                ecuInfo["ECU_NAME"] = parseASCII(response.data)
            }
        }

        runCatching {
            val response = protocolHandler.sendOBDCommand("0904", 5000)
            if (response.success && response.data != null) {
                ecuInfo["CALIBRATION_ID"] = parseASCII(response.data)
            }
        }

        runCatching {
            val response = protocolHandler.sendOBDCommand("0906", 5000)
            if (response.success && response.data != null) {
                ecuInfo["CVN"] = bytesToHex(response.data)
            }
        }

        return ecuInfo
    }

    @Throws(IOException::class)
    private fun configureForVW() {
        connection.sendAndReceive("ATSH$ECU_ENGINE", 2000)
        connection.sendAndReceive("ATCRA7E8", 2000)
        connection.sendAndReceive("ATCFC1", 2000)
    }

    @Throws(IOException::class)
    fun readPID(pid: StandardPID): PIDResult = readPID(pid.pid)

    @Throws(IOException::class)
    fun readPID(pid: String): PIDResult {
        val result = PIDResult(pid = pid)
        val response = protocolHandler.sendOBDCommand(pid, 5000)

        result.success = response.success
        result.rawResponse = response.rawResponse

        if (response.success && response.data != null) {
            result.rawData = response.data
            result.value = parsePIDValue(pid, response.data)
            result.unit = getPIDUnit(pid)
        } else {
            result.errorMessage = response.errorType?.toString() ?: "Unknown error"
        }

        return result
    }

    private fun parsePIDValue(pid: String, data: ByteArray): Double {
        if (data.size < 3) return 0.0
        val offset = 2

        return when (pid.uppercase()) {
            "0104", "0111", "012F", "0152" -> (data[offset].toInt() and 0xFF) * 100.0 / 255.0
            "0105", "010F", "0146" -> (data[offset].toInt() and 0xFF) - 40.0
            "010C" -> if (data.size >= offset + 2) {
                ((data[offset].toInt() and 0xFF) * 256 + (data[offset + 1].toInt() and 0xFF)) / 4.0
            } else 0.0
            "010D" -> (data[offset].toInt() and 0xFF).toDouble()
            "010E" -> ((data[offset].toInt() and 0xFF) / 2.0) - 64
            "0110" -> if (data.size >= offset + 2) {
                ((data[offset].toInt() and 0xFF) * 256 + (data[offset + 1].toInt() and 0xFF)) / 100.0
            } else 0.0
            "0142" -> if (data.size >= offset + 2) {
                ((data[offset].toInt() and 0xFF) * 256 + (data[offset + 1].toInt() and 0xFF)) / 1000.0
            } else 0.0
            "011F", "0121" -> if (data.size >= offset + 2) {
                ((data[offset].toInt() and 0xFF) * 256 + (data[offset + 1].toInt() and 0xFF)).toDouble()
            } else 0.0
            "010B", "0133" -> (data[offset].toInt() and 0xFF).toDouble()
            else -> (data[offset].toInt() and 0xFF).toDouble()
        }
    }

    private fun getPIDUnit(pid: String): String = when (pid.uppercase()) {
        "0104", "0111", "012F", "0152" -> "%"
        "0105", "010F", "0146", "013C" -> "°C"
        "010C" -> "RPM"
        "010D" -> "km/h"
        "010E" -> "°"
        "0110" -> "g/s"
        "0142" -> "V"
        "011F" -> "s"
        "0121" -> "km"
        "010B", "0133" -> "kPa"
        else -> ""
    }

    @Throws(IOException::class)
    fun readDTCs(): DTCResult {
        val result = DTCResult()
        val response = protocolHandler.sendOBDCommand("03", 10000)

        if (!response.success) {
            result.success = false
            result.errorMessage = "Failed to read DTCs"
            return result
        }

        result.codes = parseDTCs(response.rawResponse)
        result.success = true
        return result
    }

    private fun parseDTCs(response: String): List<String> {
        val codes = mutableListOf<String>()
        var clean = response.replace(Regex("[^0-9A-Fa-f]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (clean.startsWith("43")) clean = clean.substring(2).trim()

        val bytes = clean.split(" ")

        var i = 0
        while (i < bytes.size - 1) {
            runCatching {
                val b1 = bytes[i].toInt(16)
                val b2 = bytes[i + 1].toInt(16)
                if (b1 != 0 || b2 != 0) {
                    val code = decodeDTC(b1, b2)
                    if (code.isNotEmpty()) codes.add(code)
                }
            }
            i += 2
        }

        return codes
    }

    private fun decodeDTC(b1: Int, b2: Int): String {
        val categories = charArrayOf('P', 'C', 'B', 'U')
        val category = categories[(b1 shr 6) and 0x03]
        val digit1 = (b1 shr 4) and 0x03
        val digit2 = b1 and 0x0F
        val digit3 = (b2 shr 4) and 0x0F
        val digit4 = b2 and 0x0F
        return String.format("%c%d%X%X%X", category, digit1, digit2, digit3, digit4)
    }

    @Throws(IOException::class)
    fun clearDTCs(): Boolean {
        val response = protocolHandler.sendOBDCommand("04", 10000)
        return response.success || response.rawResponse.uppercase().contains("44")
    }

    @Throws(IOException::class)
    fun readPendingDTCs(): DTCResult {
        val result = DTCResult()
        val response = protocolHandler.sendOBDCommand("07", 10000)

        if (!response.success) {
            result.success = false
            result.errorMessage = "Failed to read pending DTCs"
            return result
        }

        result.codes = parseDTCs(response.rawResponse)
        result.success = true
        return result
    }

    private fun parseASCII(data: ByteArray): String {
        return data.drop(2)
            .filter { it in 0x20..0x7E }
            .map { it.toInt().toChar() }
            .joinToString("")
            .trim()
    }

    private fun bytesToHex(data: ByteArray): String = data.joinToString("") { "%02X".format(it) }

    data class VWInitResult(
        var success: Boolean = false,
        var stage: String = "",
        var errorMessage: String? = null,
        var exception: Exception? = null,
        var vin: String? = null,
        var isVWVehicle: Boolean = false,
        var supportedPIDs: List<String> = emptyList(),
        var ecuInfo: Map<String, String> = emptyMap()
    )

    data class PIDResult(
        val pid: String,
        var success: Boolean = false,
        var rawResponse: String? = null,
        var rawData: ByteArray? = null,
        var value: Double = 0.0,
        var unit: String = "",
        var errorMessage: String? = null
    )

    data class DTCResult(
        var success: Boolean = false,
        var codes: List<String> = emptyList(),
        var errorMessage: String? = null
    )

    companion object {
        private const val TAG = "VWProtocolManager"

        const val ECU_ENGINE = "7E0"
        const val ECU_TRANSMISSION = "7E1"
        const val ECU_ABS = "7E2"
        const val ECU_AIRBAG = "7E3"
        const val ECU_INSTRUMENT = "7E4"
        const val ECU_CENTRAL = "7E5"
        const val ECU_GATEWAY = "7E6"

        val VW_ECU_NAMES = mapOf(
            "01" to "Engine", "02" to "Transmission", "03" to "ABS/Brakes",
            "08" to "HVAC", "09" to "Central Electronics", "15" to "Airbag",
            "17" to "Instrument Cluster", "19" to "Gateway", "25" to "Immobilizer",
            "44" to "Power Steering", "46" to "Central Comfort", "55" to "Headlight Range",
            "76" to "Parking Aid"
        )

        val VW_GROUP_WMI = setOf(
            "WVW", "WV1", "WV2", "WV3", "3VW", "1VW", "9BW",
            "WAU", "WUA", "TRU", "VSS", "TMB",
            "WP0", "WP1", "ZHW", "SCB", "VF9"
        )
    }
}