@file:Suppress("MagicNumber")

package com.spacetec.obd.obd

/**
 * Complete PID decoder with formulas for all standard OBD-II PIDs.
 */
object PidDecoder {

    // Common scaling factors used in OBD-II PID formulas
    private const val BYTE_TO_PERCENT = 100.0 / 255.0
    private const val BYTE_MINUS_128_TO_PERCENT = 100.0 / 128.0
    private const val BYTE_MINUS_40 = -40.0
    private const val BYTE_DIV_2_MINUS_64 = 0.5
    private const val TWO_BYTE_DIV_4 = 4.0
    private const val TWO_BYTE_DIV_100 = 100.0
    private const val TWO_BYTE_DIV_20 = 20.0
    private const val TWO_BYTE_DIV_1000 = 1000.0
    private const val TWO_BYTE_MUL_0_079 = 0.079
    private const val TWO_BYTE_MUL_10 = 10.0
    private const val TWO_BYTE_DIV_4_MINUS_32768 = 4.0
    private const val TWO_BYTE_MINUS_32768_DIV_4 = -8192.0
    private const val TWO_BYTE_DIV_65536_MUL_2 = 2.0 / 65536.0
    private const val BYTE_TO_255_RANGE = 255.0
    private const val BYTE_TO_256_BASE = 256.0
    private const val BYTE_MUL_3 = 3.0

    data class PidValue(
        val pid: Int,
        val name: String,
        val value: Double,
        val unit: String,
        val rawBytes: ByteArray,
        val formula: String
    )

    data class PidDefinition(
        val pid: Int,
        val name: String,
        val shortName: String,
        val unit: String,
        val minValue: Double,
        val maxValue: Double,
        val bytes: Int,
        val formula: (ByteArray) -> Double
    )

    // All standard OBD-II PIDs
    val PIDS = mapOf(
        0x04 to PidDefinition(0x04, "Calculated Engine Load", "LOAD", "%", 0.0, 100.0, 1) { d -> d[0].toUByte().toDouble() * BYTE_TO_PERCENT },
        0x05 to PidDefinition(0x05, "Engine Coolant Temperature", "COOLANT", "°C", -40.0, 215.0, 1) { d -> d[0].toUByte().toDouble() + BYTE_MINUS_40 },
        0x06 to PidDefinition(0x06, "Short Term Fuel Trim Bank 1", "STFT1", "%", -100.0, 99.2, 1) { d -> (d[0].toUByte().toDouble() - 128.0) * BYTE_MINUS_128_TO_PERCENT },
        0x07 to PidDefinition(0x07, "Long Term Fuel Trim Bank 1", "LTFT1", "%", -100.0, 99.2, 1) { d -> (d[0].toUByte().toDouble() - 128.0) * BYTE_MINUS_128_TO_PERCENT },
        0x08 to PidDefinition(0x08, "Short Term Fuel Trim Bank 2", "STFT2", "%", -100.0, 99.2, 1) { d -> (d[0].toUByte().toDouble() - 128.0) * BYTE_MINUS_128_TO_PERCENT },
        0x09 to PidDefinition(0x09, "Long Term Fuel Trim Bank 2", "LTFT2", "%", -100.0, 99.2, 1) { d -> (d[0].toUByte().toDouble() - 128.0) * BYTE_MINUS_128_TO_PERCENT },
        0x0A to PidDefinition(0x0A, "Fuel Pressure", "FUEL_PRES", "kPa", 0.0, 765.0, 1) { d -> d[0].toUByte().toDouble() * BYTE_MUL_3 },
        0x0B to PidDefinition(0x0B, "Intake Manifold Pressure", "MAP", "kPa", 0.0, BYTE_TO_255_RANGE, 1) { d -> d[0].toUByte().toDouble() },
        0x0C to PidDefinition(0x0C, "Engine RPM", "RPM", "rpm", 0.0, 16383.75, 2) { d -> ((d[0].toUByte().toInt() * 256) + d[1].toUByte().toInt()) / TWO_BYTE_DIV_4 },
        0x0D to PidDefinition(0x0D, "Vehicle Speed", "SPEED", "km/h", 0.0, BYTE_TO_255_RANGE, 1) { d -> d[0].toUByte().toDouble() },
        0x0E to PidDefinition(0x0E, "Timing Advance", "TIMING", "°", -64.0, 63.5, 1) { d -> d[0].toUByte().toDouble() / 2.0 - 64.0 },
        0x0F to PidDefinition(0x0F, "Intake Air Temperature", "IAT", "°C", -40.0, 215.0, 1) { d -> d[0].toUByte().toDouble() + BYTE_MINUS_40 },
        0x10 to PidDefinition(0x10, "MAF Air Flow Rate", "MAF", "g/s", 0.0, 655.35, 2) { d -> ((d[0].toUByte().toInt() * 256) + d[1].toUByte().toInt()) / TWO_BYTE_DIV_100 },
        0x11 to PidDefinition(0x11, "Throttle Position", "TPS", "%", 0.0, 100.0, 1) { d -> d[0].toUByte().toDouble() * BYTE_TO_PERCENT },
        0x1C to PidDefinition(0x1C, "OBD Standard", "OBD_STD", "", 0.0, BYTE_TO_255_RANGE, 1) { d -> d[0].toUByte().toDouble() },
        0x1F to PidDefinition(0x1F, "Run Time Since Start", "RUNTIME", "sec", 0.0, 65535.0, 2) { d -> (d[0].toUByte().toInt() * 256 + d[1].toUByte().toInt()).toDouble() },
        0x21 to PidDefinition(0x21, "Distance with MIL On", "DIST_MIL", "km", 0.0, 65535.0, 2) { d -> (d[0].toUByte().toInt() * 256 + d[1].toUByte().toInt()).toDouble() },
        0x22 to PidDefinition(0x22, "Fuel Rail Pressure", "FRP", "kPa", 0.0, 5177.265, 2) { d -> ((d[0].toUByte().toInt() * 256) + d[1].toUByte().toInt()) * TWO_BYTE_MUL_0_079 },
        0x23 to PidDefinition(0x23, "Fuel Rail Gauge Pressure", "FRPD", "kPa", 0.0, 655350.0, 2) { d -> ((d[0].toUByte().toInt() * 256) + d[1].toUByte().toInt()) * TWO_BYTE_MUL_10 },
        0x2C to PidDefinition(0x2C, "Commanded EGR", "EGR_CMD", "%", 0.0, 100.0, 1) { d -> d[0].toUByte().toDouble() * BYTE_TO_PERCENT },
        0x2D to PidDefinition(0x2D, "EGR Error", "EGR_ERR", "%", -100.0, 99.2, 1) { d -> (d[0].toUByte().toDouble() - 128.0) * BYTE_MINUS_128_TO_PERCENT },
        0x2E to PidDefinition(0x2E, "Commanded Evaporative Purge", "EVAP_CMD", "%", 0.0, 100.0, 1) { d -> d[0].toUByte().toDouble() * BYTE_TO_PERCENT },
        0x2F to PidDefinition(0x2F, "Fuel Tank Level", "FUEL_LVL", "%", 0.0, 100.0, 1) { d -> d[0].toUByte().toDouble() * BYTE_TO_PERCENT },
        0x30 to PidDefinition(0x30, "Warm-ups Since Codes Cleared", "WARMUPS", "", 0.0, BYTE_TO_255_RANGE, 1) { d -> d[0].toUByte().toDouble() },
        0x31 to PidDefinition(0x31, "Distance Since Codes Cleared", "DIST_CLR", "km", 0.0, 65535.0, 2) { d -> (d[0].toUByte().toInt() * 256 + d[1].toUByte().toInt()).toDouble() },
        0x32 to PidDefinition(0x32, "Evap System Vapor Pressure", "EVAP_VP", "Pa", TWO_BYTE_MINUS_32768_DIV_4, 8191.75, 2) { d -> ((d[0].toInt() * 256 + d[1].toUByte().toInt()) - 32768) / TWO_BYTE_DIV_4_MINUS_32768 },
        0x33 to PidDefinition(0x33, "Barometric Pressure", "BARO", "kPa", 0.0, BYTE_TO_255_RANGE, 1) { d -> d[0].toUByte().toDouble() },
        0x42 to PidDefinition(0x42, "Control Module Voltage", "VPWR", "V", 0.0, 65.535, 2) { d -> ((d[0].toUByte().toInt() * 256) + d[1].toUByte().toInt()) / TWO_BYTE_DIV_1000 },
        0x43 to PidDefinition(0x43, "Absolute Load Value", "ABS_LOAD", "%", 0.0, 25700.0, 2) { d -> ((d[0].toUByte().toInt() * 256) + d[1].toUByte().toInt()) * BYTE_TO_PERCENT },
        0x44 to PidDefinition(0x44, "Commanded Air-Fuel Ratio", "AFR_CMD", "", 0.0, 2.0, 2) { d -> ((d[0].toUByte().toInt() * 256) + d[1].toUByte().toInt()) * TWO_BYTE_DIV_65536_MUL_2 },
        0x45 to PidDefinition(0x45, "Relative Throttle Position", "REL_TPS", "%", 0.0, 100.0, 1) { d -> d[0].toUByte().toDouble() * BYTE_TO_PERCENT },
        0x46 to PidDefinition(0x46, "Ambient Air Temperature", "AAT", "°C", -40.0, 215.0, 1) { d -> d[0].toUByte().toDouble() + BYTE_MINUS_40 },
        0x47 to PidDefinition(0x47, "Absolute Throttle Position B", "TPS_B", "%", 0.0, 100.0, 1) { d -> d[0].toUByte().toDouble() * BYTE_TO_PERCENT },
        0x48 to PidDefinition(0x48, "Absolute Throttle Position C", "TPS_C", "%", 0.0, 100.0, 1) { d -> d[0].toUByte().toDouble() * BYTE_TO_PERCENT },
        0x49 to PidDefinition(0x49, "Accelerator Pedal Position D", "APP_D", "%", 0.0, 100.0, 1) { d -> d[0].toUByte().toDouble() * BYTE_TO_PERCENT },
        0x4A to PidDefinition(0x4A, "Accelerator Pedal Position E", "APP_E", "%", 0.0, 100.0, 1) { d -> d[0].toUByte().toDouble() * BYTE_TO_PERCENT },
        0x4B to PidDefinition(0x4B, "Accelerator Pedal Position F", "APP_F", "%", 0.0, 100.0, 1) { d -> d[0].toUByte().toDouble() * BYTE_TO_PERCENT },
        0x4C to PidDefinition(0x4C, "Commanded Throttle Actuator", "TAC_CMD", "%", 0.0, 100.0, 1) { d -> d[0].toUByte().toDouble() * BYTE_TO_PERCENT },
        0x4D to PidDefinition(0x4D, "Time Run with MIL On", "MIL_TIME", "min", 0.0, 65535.0, 2) { d -> (d[0].toUByte().toInt() * 256 + d[1].toUByte().toInt()).toDouble() },
        0x4E to PidDefinition(0x4E, "Time Since Codes Cleared", "CLR_TIME", "min", 0.0, 65535.0, 2) { d -> (d[0].toUByte().toInt() * 256 + d[1].toUByte().toInt()).toDouble() },
        0x51 to PidDefinition(0x51, "Fuel Type", "FUEL_TYP", "", 0.0, 23.0, 1) { d -> d[0].toUByte().toDouble() },
        0x52 to PidDefinition(0x52, "Ethanol Fuel %", "ETHANOL", "%", 0.0, 100.0, 1) { d -> d[0].toUByte().toDouble() * BYTE_TO_PERCENT },
        0x5C to PidDefinition(0x5C, "Engine Oil Temperature", "OIL_TEMP", "°C", -40.0, 210.0, 1) { d -> d[0].toUByte().toDouble() + BYTE_MINUS_40 },
        0x5E to PidDefinition(0x5E, "Engine Fuel Rate", "FUEL_RATE", "L/h", 0.0, 3276.75, 2) { d -> ((d[0].toUByte().toInt() * 256) + d[1].toUByte().toInt()) / TWO_BYTE_DIV_20 }
    )

    fun decode(pid: Int, data: ByteArray): PidValue? {
        val def = PIDS[pid] ?: return null
        if (data.size < def.bytes) return null
        
        val value = def.formula(data)
        return PidValue(
            pid = pid,
            name = def.name,
            value = value,
            unit = def.unit,
            rawBytes = data,
            formula = def.shortName
        )
    }

    fun getPidName(pid: Int): String = PIDS[pid]?.name ?: "Unknown PID 0x${pid.toString(16).uppercase()}"
    fun getPidUnit(pid: Int): String = PIDS[pid]?.unit ?: ""
}