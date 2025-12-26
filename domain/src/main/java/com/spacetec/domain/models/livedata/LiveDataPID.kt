/**
 * File: LiveDataPID.kt
 * 
 * Domain model representing OBD-II Parameter IDs (PIDs) for live data
 * monitoring in the SpaceTec automotive diagnostic application. This file
 * provides comprehensive PID definitions, value decoding, unit conversion,
 * and categorization for all standard and common manufacturer-specific PIDs.
 * 
 * Structure:
 * 1. Package declaration and imports
 * 2. LiveDataPID data class with full documentation
 * 3. PIDCategory enum
 * 4. GaugeType and ValueStatus enums
 * 5. UnitConversion data class
 * 6. BitDefinition data class
 * 7. LiveDataValue data class
 * 8. PIDDefinition data class
 * 9. Standard PID definitions
 * 10. Extension functions and utilities
 * 
 * @author SpaceTec Development Team
 * @since 1.0.0
 */
package com.spacetec.domain.models.livedata

import java.io.Serializable
import java.text.DecimalFormat
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Represents an OBD-II Parameter ID (PID) for live data monitoring.
 * 
 * PIDs are standardized parameters defined by SAE J1979 that can be read
 * from a vehicle's ECU. Each PID has a specific format, decoding formula,
 * and unit of measurement.
 * 
 * ## PID Structure
 * 
 * OBD-II PIDs are organized by service (mode):
 * - **Service 01**: Current powertrain diagnostic data
 * - **Service 02**: Freeze frame data
 * - **Service 05**: Oxygen sensor monitoring
 * - **Service 09**: Vehicle information
 * 
 * ## Decoding Formula
 * 
 * Each PID has a specific formula to convert raw bytes to meaningful values:
 * - Single byte: `A` (first byte)
 * - Two bytes: `A * 256 + B` or `(A * 256 + B) / 4` etc.
 * - Bit-encoded: Individual bits represent different states
 * 
 * ## Usage Example
 * 
 * ```kotlin
 * val rpmPid = LiveDataPID.ENGINE_RPM
 * 
 * // Decode raw response bytes
 * val rawBytes = byteArrayOf(0x1A, 0xF8)
 * val rpm = rpmPid.decode(rawBytes) // 1726.0 RPM
 * 
 * // Format for display
 * val formatted = rpmPid.formatValue(rpm) // "1726"
 * 
 * // Check status
 * val status = rpmPid.getValueStatus(rpm) // NORMAL
 * ```
 * 
 * @property pid PID number (e.g., 0x0C for RPM)
 * @property service Service/Mode this PID belongs to (default: 0x01)
 * @property name Human-readable name
 * @property shortName Short name for compact display
 * @property description Detailed description
 * @property category Category for grouping
 * @property unit Unit of measurement
 * @property alternativeUnits Alternative units with conversions
 * @property dataBytes Number of data bytes in response
 * @property minValue Minimum possible value
 * @property maxValue Maximum possible value
 * @property formula Human-readable formula description
 * @property isStandard Whether this is a standard SAE PID
 * @property manufacturer Manufacturer name if manufacturer-specific
 * @property commonlySupported Whether commonly supported by vehicles
 * @property decimalPlaces Decimal places for display
 * @property displayFormat Custom format string
 * @property gaugeType Type of gauge for visualization
 * @property warningThreshold Value above which is warning
 * @property criticalThreshold Value above which is critical
 * @property isBitEncoded Whether PID is bit-encoded
 * @property bitDefinitions Bit definitions for bit-encoded PIDs
 * @property decoder Custom decoder function
 * 
 * @see PIDCategory
 * @see LiveDataValue
 * @see GaugeType
 */
data class LiveDataPID(
    val pid: Int,
    val service: Int = SERVICE_01_CURRENT_DATA,
    val name: String,
    val shortName: String,
    val description: String? = null,
    val category: PIDCategory,
    val unit: String,
    val alternativeUnits: List<UnitConversion> = emptyList(),
    val dataBytes: Int,
    val minValue: Double,
    val maxValue: Double,
    val formula: String? = null,
    val isStandard: Boolean = true,
    val manufacturer: String? = null,
    val commonlySupported: Boolean = true,
    val decimalPlaces: Int = 0,
    val displayFormat: String? = null,
    val gaugeType: GaugeType = GaugeType.NUMERIC,
    val warningThreshold: Double? = null,
    val criticalThreshold: Double? = null,
    val isBitEncoded: Boolean = false,
    val bitDefinitions: List<BitDefinition> = emptyList(),
    private val decoder: ((ByteArray) -> Double)? = null
) : Serializable {

    // ==================== Computed Properties ====================

    /**
     * Full PID identifier string.
     * 
     * Format: `S{service}_{pid}`
     * Example: `S01_0C` for Engine RPM
     */
    val identifier: String
        get() = "S${service.toHexString(2)}_${pid.toHexString(2)}"

    /**
     * PID as hexadecimal string.
     * 
     * Example: `0x0C`
     */
    val pidHex: String
        get() = "0x${pid.toHexString(2)}"

    /**
     * Service as hexadecimal string.
     * 
     * Example: `0x01`
     */
    val serviceHex: String
        get() = "0x${service.toHexString(2)}"

    /**
     * Value range as ClosedFloatingPointRange.
     */
    val valueRange: ClosedFloatingPointRange<Double>
        get() = minValue..maxValue

    /**
     * Whether this PID has warning threshold defined.
     */
    val hasWarningThreshold: Boolean
        get() = warningThreshold != null

    /**
     * Whether this PID has critical threshold defined.
     */
    val hasCriticalThreshold: Boolean
        get() = criticalThreshold != null

    /**
     * Whether this PID has any thresholds defined.
     */
    val hasThresholds: Boolean
        get() = hasWarningThreshold || hasCriticalThreshold

    /**
     * Display name with unit.
     * 
     * Example: "Engine RPM (rpm)"
     */
    val displayNameWithUnit: String
        get() = if (unit.isNotEmpty()) "$name ($unit)" else name

    /**
     * OBD-II request bytes for this PID.
     * 
     * Format: [service, pid]
     */
    val requestBytes: ByteArray
        get() = byteArrayOf(service.toByte(), pid.toByte())

    /**
     * Expected response length (service + pid + data bytes).
     */
    val expectedResponseLength: Int
        get() = 2 + dataBytes

    // ==================== Decoding Methods ====================

    /**
     * Decodes raw bytes to a numeric value using the PID formula.
     * 
     * @param bytes Raw response bytes (data portion only, without service/pid)
     * @return Decoded numeric value
     * @throws IllegalArgumentException If bytes length is insufficient
     */
    fun decode(bytes: ByteArray): Double {
        require(bytes.size >= dataBytes) {
            "Expected at least $dataBytes bytes, got ${bytes.size}"
        }

        // Use custom decoder if provided
        decoder?.let { return it(bytes) }

        // Standard decoding based on PID
        return when (pid) {
            // Engine Load (%)
            PID_ENGINE_LOAD -> bytes.a() * 100.0 / 255.0

            // Coolant Temperature (°C)
            PID_COOLANT_TEMP -> bytes.a() - 40.0

            // Short Term Fuel Trim (%)
            PID_SHORT_TERM_FUEL_TRIM_BANK1,
            PID_SHORT_TERM_FUEL_TRIM_BANK2 -> (bytes.a() - 128.0) * 100.0 / 128.0

            // Long Term Fuel Trim (%)
            PID_LONG_TERM_FUEL_TRIM_BANK1,
            PID_LONG_TERM_FUEL_TRIM_BANK2 -> (bytes.a() - 128.0) * 100.0 / 128.0

            // Fuel Pressure (kPa)
            PID_FUEL_PRESSURE -> bytes.a() * 3.0

            // Intake Manifold Pressure (kPa)
            PID_INTAKE_MANIFOLD_PRESSURE -> bytes.a().toDouble()

            // Engine RPM
            PID_ENGINE_RPM -> (bytes.a() * 256.0 + bytes.b()) / 4.0

            // Vehicle Speed (km/h)
            PID_VEHICLE_SPEED -> bytes.a().toDouble()

            // Timing Advance (degrees)
            PID_TIMING_ADVANCE -> bytes.a() / 2.0 - 64.0

            // Intake Air Temperature (°C)
            PID_INTAKE_AIR_TEMP -> bytes.a() - 40.0

            // MAF Rate (g/s)
            PID_MAF_RATE -> (bytes.a() * 256.0 + bytes.b()) / 100.0

            // Throttle Position (%)
            PID_THROTTLE_POSITION -> bytes.a() * 100.0 / 255.0

            // Oxygen Sensor Voltage (V)
            PID_O2_SENSOR_VOLTAGE_BANK1_SENSOR1,
            PID_O2_SENSOR_VOLTAGE_BANK1_SENSOR2,
            PID_O2_SENSOR_VOLTAGE_BANK1_SENSOR3,
            PID_O2_SENSOR_VOLTAGE_BANK1_SENSOR4,
            PID_O2_SENSOR_VOLTAGE_BANK2_SENSOR1,
            PID_O2_SENSOR_VOLTAGE_BANK2_SENSOR2,
            PID_O2_SENSOR_VOLTAGE_BANK2_SENSOR3,
            PID_O2_SENSOR_VOLTAGE_BANK2_SENSOR4 -> bytes.a() / 200.0

            // Runtime (seconds)
            PID_RUNTIME_SINCE_START -> bytes.a() * 256.0 + bytes.b()

            // Distance with MIL (km)
            PID_DISTANCE_WITH_MIL -> bytes.a() * 256.0 + bytes.b()

            // Fuel Rail Pressure (kPa relative to manifold vacuum)
            PID_FUEL_RAIL_PRESSURE_REL -> (bytes.a() * 256.0 + bytes.b()) * 0.079

            // Fuel Rail Pressure (kPa absolute)
            PID_FUEL_RAIL_PRESSURE_ABS -> (bytes.a() * 256.0 + bytes.b()) * 10.0

            // Commanded EGR (%)
            PID_COMMANDED_EGR -> bytes.a() * 100.0 / 255.0

            // EGR Error (%)
            PID_EGR_ERROR -> (bytes.a() - 128.0) * 100.0 / 128.0

            // Commanded Evap Purge (%)
            PID_COMMANDED_EVAP_PURGE -> bytes.a() * 100.0 / 255.0

            // Fuel Level (%)
            PID_FUEL_LEVEL -> bytes.a() * 100.0 / 255.0

            // Warm-ups Since Clear
            PID_WARMUPS_SINCE_CLEAR -> bytes.a().toDouble()

            // Distance Since Clear (km)
            PID_DISTANCE_SINCE_CLEAR -> bytes.a() * 256.0 + bytes.b()

            // Barometric Pressure (kPa)
            PID_BAROMETRIC_PRESSURE -> bytes.a().toDouble()

            // Catalyst Temperature Bank 1 Sensor 1 (°C)
            PID_CATALYST_TEMP_BANK1_SENSOR1 -> (bytes.a() * 256.0 + bytes.b()) / 10.0 - 40.0

            // Control Module Voltage (V)
            PID_CONTROL_MODULE_VOLTAGE -> (bytes.a() * 256.0 + bytes.b()) / 1000.0

            // Absolute Load (%)
            PID_ABSOLUTE_LOAD -> (bytes.a() * 256.0 + bytes.b()) * 100.0 / 255.0

            // Fuel/Air Commanded Ratio
            PID_FUEL_AIR_COMMANDED -> (bytes.a() * 256.0 + bytes.b()) * 2.0 / 65536.0

            // Relative Throttle Position (%)
            PID_RELATIVE_THROTTLE -> bytes.a() * 100.0 / 255.0

            // Ambient Air Temperature (°C)
            PID_AMBIENT_AIR_TEMP -> bytes.a() - 40.0

            // Absolute Throttle Position B (%)
            PID_THROTTLE_POSITION_B -> bytes.a() * 100.0 / 255.0

            // Absolute Throttle Position C (%)
            PID_THROTTLE_POSITION_C -> bytes.a() * 100.0 / 255.0

            // Accelerator Pedal Position D (%)
            PID_ACCELERATOR_PEDAL_D -> bytes.a() * 100.0 / 255.0

            // Accelerator Pedal Position E (%)
            PID_ACCELERATOR_PEDAL_E -> bytes.a() * 100.0 / 255.0

            // Accelerator Pedal Position F (%)
            PID_ACCELERATOR_PEDAL_F -> bytes.a() * 100.0 / 255.0

            // Commanded Throttle Actuator (%)
            PID_COMMANDED_THROTTLE -> bytes.a() * 100.0 / 255.0

            // Time With MIL On (minutes)
            PID_TIME_WITH_MIL -> bytes.a() * 256.0 + bytes.b()

            // Time Since Clear (minutes)
            PID_TIME_SINCE_CLEAR -> bytes.a() * 256.0 + bytes.b()

            // Ethanol Fuel (%)
            PID_ETHANOL_FUEL_PERCENT -> bytes.a() * 100.0 / 255.0

            // Fuel Rail Absolute Pressure (kPa)
            PID_FUEL_RAIL_ABS_PRESSURE -> (bytes.a() * 256.0 + bytes.b()) * 10.0

            // Hybrid Battery Pack Remaining (%)
            PID_HYBRID_BATTERY_REMAINING -> bytes.a() * 100.0 / 255.0

            // Engine Oil Temperature (°C)
            PID_ENGINE_OIL_TEMP -> bytes.a() - 40.0

            // Fuel Injection Timing (degrees)
            PID_FUEL_INJECTION_TIMING -> ((bytes.a() * 256.0 + bytes.b()) - 26880.0) / 128.0

            // Engine Fuel Rate (L/h)
            PID_ENGINE_FUEL_RATE -> (bytes.a() * 256.0 + bytes.b()) / 20.0

            // Default: Linear scaling
            else -> {
                if (dataBytes == 1) {
                    bytes.a() * (maxValue - minValue) / 255.0 + minValue
                } else {
                    (bytes.a() * 256.0 + bytes.b()) * (maxValue - minValue) / 65535.0 + minValue
                }
            }
        }
    }

    /**
     * Decodes raw bytes to a [LiveDataValue] object.
     * 
     * @param bytes Raw response bytes
     * @param timestamp Timestamp of the reading (default: now)
     * @return LiveDataValue containing the decoded value
     */
    fun decodeToValue(
        bytes: ByteArray,
        timestamp: Long = System.currentTimeMillis()
    ): LiveDataValue {
        val value = decode(bytes)
        return LiveDataValue(
            pid = this,
            value = value,
            rawBytes = bytes.copyOf(),
            timestamp = timestamp,
            unit = unit,
            status = getValueStatus(value)
        )
    }

    /**
     * Decodes bit-encoded PID to map of bit states.
     * 
     * @param bytes Raw response bytes
     * @return Map of bit index to boolean state
     */
    fun decodeBits(bytes: ByteArray): Map<Int, Boolean> {
        if (!isBitEncoded || bytes.isEmpty()) return emptyMap()
        
        val result = mutableMapOf<Int, Boolean>()
        val value = if (bytes.size >= 4) {
            (bytes[0].toInt() and 0xFF shl 24) or
            (bytes[1].toInt() and 0xFF shl 16) or
            (bytes[2].toInt() and 0xFF shl 8) or
            (bytes[3].toInt() and 0xFF)
        } else if (bytes.size >= 2) {
            (bytes[0].toInt() and 0xFF shl 8) or
            (bytes[1].toInt() and 0xFF)
        } else {
            bytes[0].toInt() and 0xFF
        }
        
        for (bit in bitDefinitions) {
            result[bit.bit] = bit.isSet(value)
        }
        
        return result
    }

    /**
     * Gets active bit definitions from decoded value.
     * 
     * @param bytes Raw response bytes
     * @return List of active BitDefinition objects
     */
    fun getActiveBits(bytes: ByteArray): List<BitDefinition> {
        val bitStates = decodeBits(bytes)
        return bitDefinitions.filter { bitStates[it.bit] == true }
    }

    // ==================== Formatting Methods ====================

    /**
     * Formats a value for display.
     * 
     * @param value Value to format
     * @param useAlternativeUnit Whether to use first alternative unit
     * @return Formatted value string
     */
    fun formatValue(value: Double, useAlternativeUnit: Boolean = false): String {
        val displayValue = if (useAlternativeUnit && alternativeUnits.isNotEmpty()) {
            alternativeUnits.first().convert(value)
        } else {
            value
        }
        
        return when {
            displayFormat != null -> String.format(Locale.US, displayFormat, displayValue)
            decimalPlaces == 0 -> displayValue.roundToInt().toString()
            else -> {
                val pattern = "0.${"0".repeat(decimalPlaces)}"
                DecimalFormat(pattern).format(displayValue)
            }
        }
    }

    /**
     * Formats a value with unit for display.
     * 
     * @param value Value to format
     * @param useAlternativeUnit Whether to use first alternative unit
     * @return Formatted value string with unit
     */
    fun formatValueWithUnit(value: Double, useAlternativeUnit: Boolean = false): String {
        val formatted = formatValue(value, useAlternativeUnit)
        val displayUnit = if (useAlternativeUnit && alternativeUnits.isNotEmpty()) {
            alternativeUnits.first().toUnit
        } else {
            unit
        }
        return if (displayUnit.isNotEmpty()) "$formatted $displayUnit" else formatted
    }

    // ==================== Conversion Methods ====================

    /**
     * Converts a value to an alternative unit.
     * 
     * @param value Value in original unit
     * @param targetUnit Target unit string
     * @return Converted value or null if conversion not available
     */
    fun convertTo(value: Double, targetUnit: String): Double? {
        if (targetUnit == unit) return value
        
        return alternativeUnits.find { it.toUnit == targetUnit }?.convert(value)
    }

    /**
     * Gets all available units including the primary unit.
     */
    fun getAllUnits(): List<String> {
        return listOf(unit) + alternativeUnits.map { it.toUnit }
    }

    // ==================== Status Methods ====================

    /**
     * Checks if a value is in the warning range.
     * 
     * @param value Value to check
     * @return true if value is at or above warning threshold
     */
    fun isWarning(value: Double): Boolean =
        warningThreshold?.let { value >= it } ?: false

    /**
     * Checks if a value is in the critical range.
     * 
     * @param value Value to check
     * @return true if value is at or above critical threshold
     */
    fun isCritical(value: Double): Boolean =
        criticalThreshold?.let { value >= it } ?: false

    /**
     * Gets the status of a value.
     * 
     * @param value Value to check
     * @return ValueStatus indicating normal, warning, or critical
     */
    fun getValueStatus(value: Double): ValueStatus = when {
        isCritical(value) -> ValueStatus.CRITICAL
        isWarning(value) -> ValueStatus.WARNING
        else -> ValueStatus.NORMAL
    }

    /**
     * Checks if a value is within the valid range.
     * 
     * @param value Value to check
     * @return true if value is within minValue..maxValue
     */
    fun isInRange(value: Double): Boolean = value in valueRange

    /**
     * Clamps a value to the valid range.
     * 
     * @param value Value to clamp
     * @return Clamped value
     */
    fun clampToRange(value: Double): Double = value.coerceIn(minValue, maxValue)

    /**
     * Gets the percentage of value within the range.
     * 
     * @param value Value to calculate percentage for
     * @return Percentage (0.0 to 1.0)
     */
    fun getPercentage(value: Double): Double {
        val range = maxValue - minValue
        if (range == 0.0) return 0.0
        return ((value - minValue) / range).coerceIn(0.0, 1.0)
    }

    // ==================== Companion Object ====================

    companion object {
        private const val serialVersionUID = 1L

        // Service Constants
        const val SERVICE_01_CURRENT_DATA = 0x01
        const val SERVICE_02_FREEZE_FRAME = 0x02
        const val SERVICE_05_O2_MONITORING = 0x05
        const val SERVICE_09_VEHICLE_INFO = 0x09

        // Standard PID Numbers
        const val PID_PIDS_SUPPORTED_01_20 = 0x00
        const val PID_MONITOR_STATUS = 0x01
        const val PID_FREEZE_DTC = 0x02
        const val PID_FUEL_SYSTEM_STATUS = 0x03
        const val PID_ENGINE_LOAD = 0x04
        const val PID_COOLANT_TEMP = 0x05
        const val PID_SHORT_TERM_FUEL_TRIM_BANK1 = 0x06
        const val PID_LONG_TERM_FUEL_TRIM_BANK1 = 0x07
        const val PID_SHORT_TERM_FUEL_TRIM_BANK2 = 0x08
        const val PID_LONG_TERM_FUEL_TRIM_BANK2 = 0x09
        const val PID_FUEL_PRESSURE = 0x0A
        const val PID_INTAKE_MANIFOLD_PRESSURE = 0x0B
        const val PID_ENGINE_RPM = 0x0C
        const val PID_VEHICLE_SPEED = 0x0D
        const val PID_TIMING_ADVANCE = 0x0E
        const val PID_INTAKE_AIR_TEMP = 0x0F
        const val PID_MAF_RATE = 0x10
        const val PID_THROTTLE_POSITION = 0x11
        const val PID_O2_SENSORS_PRESENT = 0x13
        const val PID_O2_SENSOR_VOLTAGE_BANK1_SENSOR1 = 0x14
        const val PID_O2_SENSOR_VOLTAGE_BANK1_SENSOR2 = 0x15
        const val PID_O2_SENSOR_VOLTAGE_BANK1_SENSOR3 = 0x16
        const val PID_O2_SENSOR_VOLTAGE_BANK1_SENSOR4 = 0x17
        const val PID_O2_SENSOR_VOLTAGE_BANK2_SENSOR1 = 0x18
        const val PID_O2_SENSOR_VOLTAGE_BANK2_SENSOR2 = 0x19
        const val PID_O2_SENSOR_VOLTAGE_BANK2_SENSOR3 = 0x1A
        const val PID_O2_SENSOR_VOLTAGE_BANK2_SENSOR4 = 0x1B
        const val PID_OBD_STANDARDS = 0x1C
        const val PID_RUNTIME_SINCE_START = 0x1F
        const val PID_PIDS_SUPPORTED_21_40 = 0x20
        const val PID_DISTANCE_WITH_MIL = 0x21
        const val PID_FUEL_RAIL_PRESSURE_REL = 0x22
        const val PID_FUEL_RAIL_PRESSURE_ABS = 0x23
        const val PID_COMMANDED_EGR = 0x2C
        const val PID_EGR_ERROR = 0x2D
        const val PID_COMMANDED_EVAP_PURGE = 0x2E
        const val PID_FUEL_LEVEL = 0x2F
        const val PID_WARMUPS_SINCE_CLEAR = 0x30
        const val PID_DISTANCE_SINCE_CLEAR = 0x31
        const val PID_BAROMETRIC_PRESSURE = 0x33
        const val PID_CATALYST_TEMP_BANK1_SENSOR1 = 0x3C
        const val PID_PIDS_SUPPORTED_41_60 = 0x40
        const val PID_CONTROL_MODULE_VOLTAGE = 0x42
        const val PID_ABSOLUTE_LOAD = 0x43
        const val PID_FUEL_AIR_COMMANDED = 0x44
        const val PID_RELATIVE_THROTTLE = 0x45
        const val PID_AMBIENT_AIR_TEMP = 0x46
        const val PID_THROTTLE_POSITION_B = 0x47
        const val PID_THROTTLE_POSITION_C = 0x48
        const val PID_ACCELERATOR_PEDAL_D = 0x49
        const val PID_ACCELERATOR_PEDAL_E = 0x4A
        const val PID_ACCELERATOR_PEDAL_F = 0x4B
        const val PID_COMMANDED_THROTTLE = 0x4C
        const val PID_TIME_WITH_MIL = 0x4D
        const val PID_TIME_SINCE_CLEAR = 0x4E
        const val PID_ETHANOL_FUEL_PERCENT = 0x52
        const val PID_FUEL_RAIL_ABS_PRESSURE = 0x59
        const val PID_HYBRID_BATTERY_REMAINING = 0x5B
        const val PID_ENGINE_OIL_TEMP = 0x5C
        const val PID_FUEL_INJECTION_TIMING = 0x5D
        const val PID_ENGINE_FUEL_RATE = 0x5E
        const val PID_PIDS_SUPPORTED_61_80 = 0x60

        // ==================== Standard PID Definitions ====================

        /**
         * Engine RPM (revolutions per minute).
         */
        val ENGINE_RPM = LiveDataPID(
            pid = PID_ENGINE_RPM,
            name = "Engine RPM",
            shortName = "RPM",
            description = "Engine revolutions per minute",
            category = PIDCategory.ENGINE,
            unit = "rpm",
            dataBytes = 2,
            minValue = 0.0,
            maxValue = 16383.75,
            formula = "(A * 256 + B) / 4",
            decimalPlaces = 0,
            gaugeType = GaugeType.RADIAL,
            warningThreshold = 6000.0,
            criticalThreshold = 7000.0
        )

        /**
         * Vehicle Speed.
         */
        val VEHICLE_SPEED = LiveDataPID(
            pid = PID_VEHICLE_SPEED,
            name = "Vehicle Speed",
            shortName = "Speed",
            description = "Current vehicle speed",
            category = PIDCategory.SPEED,
            unit = "km/h",
            alternativeUnits = listOf(
                UnitConversion("km/h", "mph", 0.621371)
            ),
            dataBytes = 1,
            minValue = 0.0,
            maxValue = 255.0,
            formula = "A",
            decimalPlaces = 0,
            gaugeType = GaugeType.RADIAL
        )

        /**
         * Engine Coolant Temperature.
         */
        val COOLANT_TEMP = LiveDataPID(
            pid = PID_COOLANT_TEMP,
            name = "Coolant Temperature",
            shortName = "Coolant",
            description = "Engine coolant temperature",
            category = PIDCategory.TEMPERATURE,
            unit = "°C",
            alternativeUnits = listOf(
                UnitConversion("°C", "°F", 1.8, 32.0)
            ),
            dataBytes = 1,
            minValue = -40.0,
            maxValue = 215.0,
            formula = "A - 40",
            decimalPlaces = 0,
            gaugeType = GaugeType.LINEAR,
            warningThreshold = 100.0,
            criticalThreshold = 110.0
        )

        /**
         * Calculated Engine Load.
         */
        val ENGINE_LOAD = LiveDataPID(
            pid = PID_ENGINE_LOAD,
            name = "Engine Load",
            shortName = "Load",
            description = "Calculated engine load",
            category = PIDCategory.ENGINE,
            unit = "%",
            dataBytes = 1,
            minValue = 0.0,
            maxValue = 100.0,
            formula = "A * 100 / 255",
            decimalPlaces = 1,
            gaugeType = GaugeType.BAR
        )

        /**
         * Throttle Position.
         */
        val THROTTLE_POSITION = LiveDataPID(
            pid = PID_THROTTLE_POSITION,
            name = "Throttle Position",
            shortName = "Throttle",
            description = "Absolute throttle position",
            category = PIDCategory.ENGINE,
            unit = "%",
            dataBytes = 1,
            minValue = 0.0,
            maxValue = 100.0,
            formula = "A * 100 / 255",
            decimalPlaces = 1,
            gaugeType = GaugeType.BAR
        )

        /**
         * Fuel Level.
         */
        val FUEL_LEVEL = LiveDataPID(
            pid = PID_FUEL_LEVEL,
            name = "Fuel Level",
            shortName = "Fuel",
            description = "Fuel tank level input",
            category = PIDCategory.FUEL,
            unit = "%",
            dataBytes = 1,
            minValue = 0.0,
            maxValue = 100.0,
            formula = "A * 100 / 255",
            decimalPlaces = 1,
            gaugeType = GaugeType.BAR,
            warningThreshold = 15.0
        )

        /**
         * Mass Air Flow Rate.
         */
        val MAF_RATE = LiveDataPID(
            pid = PID_MAF_RATE,
            name = "MAF Air Flow Rate",
            shortName = "MAF",
            description = "Mass air flow sensor rate",
            category = PIDCategory.ENGINE,
            unit = "g/s",
            dataBytes = 2,
            minValue = 0.0,
            maxValue = 655.35,
            formula = "(A * 256 + B) / 100",
            decimalPlaces = 2,
            gaugeType = GaugeType.GRAPH
        )

        /**
         * Intake Air Temperature.
         */
        val INTAKE_TEMP = LiveDataPID(
            pid = PID_INTAKE_AIR_TEMP,
            name = "Intake Air Temperature",
            shortName = "IAT",
            description = "Intake air temperature",
            category = PIDCategory.TEMPERATURE,
            unit = "°C",
            alternativeUnits = listOf(
                UnitConversion("°C", "°F", 1.8, 32.0)
            ),
            dataBytes = 1,
            minValue = -40.0,
            maxValue = 215.0,
            formula = "A - 40",
            decimalPlaces = 0,
            gaugeType = GaugeType.LINEAR
        )

        /**
         * Timing Advance.
         */
        val TIMING_ADVANCE = LiveDataPID(
            pid = PID_TIMING_ADVANCE,
            name = "Timing Advance",
            shortName = "Timing",
            description = "Ignition timing advance for cylinder 1",
            category = PIDCategory.IGNITION,
            unit = "°",
            dataBytes = 1,
            minValue = -64.0,
            maxValue = 63.5,
            formula = "A / 2 - 64",
            decimalPlaces = 1,
            gaugeType = GaugeType.NUMERIC
        )

        /**
         * Fuel Pressure.
         */
        val FUEL_PRESSURE = LiveDataPID(
            pid = PID_FUEL_PRESSURE,
            name = "Fuel Pressure",
            shortName = "FuelP",
            description = "Fuel pressure (gauge pressure)",
            category = PIDCategory.FUEL,
            unit = "kPa",
            alternativeUnits = listOf(
                UnitConversion("kPa", "psi", 0.145038)
            ),
            dataBytes = 1,
            minValue = 0.0,
            maxValue = 765.0,
            formula = "A * 3",
            decimalPlaces = 0,
            gaugeType = GaugeType.LINEAR
        )

        /**
         * Intake Manifold Absolute Pressure.
         */
        val INTAKE_MANIFOLD_PRESSURE = LiveDataPID(
            pid = PID_INTAKE_MANIFOLD_PRESSURE,
            name = "Intake Manifold Pressure",
            shortName = "MAP",
            description = "Intake manifold absolute pressure",
            category = PIDCategory.PRESSURE,
            unit = "kPa",
            alternativeUnits = listOf(
                UnitConversion("kPa", "inHg", 0.2953),
                UnitConversion("kPa", "psi", 0.145038)
            ),
            dataBytes = 1,
            minValue = 0.0,
            maxValue = 255.0,
            formula = "A",
            decimalPlaces = 0,
            gaugeType = GaugeType.LINEAR
        )

        /**
         * Short Term Fuel Trim Bank 1.
         */
        val SHORT_TERM_FUEL_TRIM_BANK1 = LiveDataPID(
            pid = PID_SHORT_TERM_FUEL_TRIM_BANK1,
            name = "Short Term Fuel Trim Bank 1",
            shortName = "STFT B1",
            description = "Short term fuel trim for bank 1",
            category = PIDCategory.FUEL,
            unit = "%",
            dataBytes = 1,
            minValue = -100.0,
            maxValue = 99.2,
            formula = "(A - 128) * 100 / 128",
            decimalPlaces = 1,
            gaugeType = GaugeType.NUMERIC,
            warningThreshold = 20.0
        )

        /**
         * Long Term Fuel Trim Bank 1.
         */
        val LONG_TERM_FUEL_TRIM_BANK1 = LiveDataPID(
            pid = PID_LONG_TERM_FUEL_TRIM_BANK1,
            name = "Long Term Fuel Trim Bank 1",
            shortName = "LTFT B1",
            description = "Long term fuel trim for bank 1",
            category = PIDCategory.FUEL,
            unit = "%",
            dataBytes = 1,
            minValue = -100.0,
            maxValue = 99.2,
            formula = "(A - 128) * 100 / 128",
            decimalPlaces = 1,
            gaugeType = GaugeType.NUMERIC,
            warningThreshold = 25.0
        )

        /**
         * Barometric Pressure.
         */
        val BAROMETRIC_PRESSURE = LiveDataPID(
            pid = PID_BAROMETRIC_PRESSURE,
            name = "Barometric Pressure",
            shortName = "Baro",
            description = "Barometric (atmospheric) pressure",
            category = PIDCategory.PRESSURE,
            unit = "kPa",
            alternativeUnits = listOf(
                UnitConversion("kPa", "inHg", 0.2953)
            ),
            dataBytes = 1,
            minValue = 0.0,
            maxValue = 255.0,
            formula = "A",
            decimalPlaces = 0,
            gaugeType = GaugeType.NUMERIC
        )

        /**
         * Ambient Air Temperature.
         */
        val AMBIENT_AIR_TEMP = LiveDataPID(
            pid = PID_AMBIENT_AIR_TEMP,
            name = "Ambient Air Temperature",
            shortName = "Ambient",
            description = "Ambient air temperature",
            category = PIDCategory.TEMPERATURE,
            unit = "°C",
            alternativeUnits = listOf(
                UnitConversion("°C", "°F", 1.8, 32.0)
            ),
            dataBytes = 1,
            minValue = -40.0,
            maxValue = 215.0,
            formula = "A - 40",
            decimalPlaces = 0,
            gaugeType = GaugeType.NUMERIC
        )

        /**
         * Engine Oil Temperature.
         */
        val ENGINE_OIL_TEMP = LiveDataPID(
            pid = PID_ENGINE_OIL_TEMP,
            name = "Engine Oil Temperature",
            shortName = "Oil Temp",
            description = "Engine oil temperature",
            category = PIDCategory.TEMPERATURE,
            unit = "°C",
            alternativeUnits = listOf(
                UnitConversion("°C", "°F", 1.8, 32.0)
            ),
            dataBytes = 1,
            minValue = -40.0,
            maxValue = 210.0,
            formula = "A - 40",
            decimalPlaces = 0,
            gaugeType = GaugeType.LINEAR,
            warningThreshold = 120.0,
            criticalThreshold = 140.0
        )

        /**
         * Control Module Voltage.
         */
        val CONTROL_MODULE_VOLTAGE = LiveDataPID(
            pid = PID_CONTROL_MODULE_VOLTAGE,
            name = "Control Module Voltage",
            shortName = "Voltage",
            description = "Control module voltage (battery)",
            category = PIDCategory.ELECTRICAL,
            unit = "V",
            dataBytes = 2,
            minValue = 0.0,
            maxValue = 65.535,
            formula = "(A * 256 + B) / 1000",
            decimalPlaces = 2,
            gaugeType = GaugeType.NUMERIC,
            warningThreshold = 13.0,
            criticalThreshold = 11.5
        )

        /**
         * Engine Fuel Rate.
         */
        val ENGINE_FUEL_RATE = LiveDataPID(
            pid = PID_ENGINE_FUEL_RATE,
            name = "Engine Fuel Rate",
            shortName = "Fuel Rate",
            description = "Engine fuel rate consumption",
            category = PIDCategory.FUEL,
            unit = "L/h",
            alternativeUnits = listOf(
                UnitConversion("L/h", "gal/h", 0.264172)
            ),
            dataBytes = 2,
            minValue = 0.0,
            maxValue = 3276.75,
            formula = "(A * 256 + B) / 20",
            decimalPlaces = 2,
            gaugeType = GaugeType.GRAPH
        )

        /**
         * Distance Traveled with MIL On.
         */
        val DISTANCE_WITH_MIL = LiveDataPID(
            pid = PID_DISTANCE_WITH_MIL,
            name = "Distance with MIL On",
            shortName = "MIL Dist",
            description = "Distance traveled while MIL is illuminated",
            category = PIDCategory.SPEED,
            unit = "km",
            alternativeUnits = listOf(
                UnitConversion("km", "mi", 0.621371)
            ),
            dataBytes = 2,
            minValue = 0.0,
            maxValue = 65535.0,
            formula = "A * 256 + B",
            decimalPlaces = 0,
            gaugeType = GaugeType.NUMERIC
        )

        /**
         * Distance Since Codes Cleared.
         */
        val DISTANCE_SINCE_CLEAR = LiveDataPID(
            pid = PID_DISTANCE_SINCE_CLEAR,
            name = "Distance Since Codes Cleared",
            shortName = "Clear Dist",
            description = "Distance traveled since diagnostic codes cleared",
            category = PIDCategory.SPEED,
            unit = "km",
            alternativeUnits = listOf(
                UnitConversion("km", "mi", 0.621371)
            ),
            dataBytes = 2,
            minValue = 0.0,
            maxValue = 65535.0,
            formula = "A * 256 + B",
            decimalPlaces = 0,
            gaugeType = GaugeType.NUMERIC
        )

        /**
         * Runtime Since Engine Start.
         */
        val RUNTIME_SINCE_START = LiveDataPID(
            pid = PID_RUNTIME_SINCE_START,
            name = "Runtime Since Start",
            shortName = "Runtime",
            description = "Time since engine start",
            category = PIDCategory.OTHER,
            unit = "s",
            dataBytes = 2,
            minValue = 0.0,
            maxValue = 65535.0,
            formula = "A * 256 + B",
            decimalPlaces = 0,
            gaugeType = GaugeType.NUMERIC
        )

        /**
         * Ethanol Fuel Percentage.
         */
        val ETHANOL_FUEL_PERCENT = LiveDataPID(
            pid = PID_ETHANOL_FUEL_PERCENT,
            name = "Ethanol Fuel %",
            shortName = "Ethanol",
            description = "Ethanol fuel percentage",
            category = PIDCategory.FUEL,
            unit = "%",
            dataBytes = 1,
            minValue = 0.0,
            maxValue = 100.0,
            formula = "A * 100 / 255",
            decimalPlaces = 1,
            gaugeType = GaugeType.BAR
        )

        /**
         * Hybrid Battery Pack Remaining Life.
         */
        val HYBRID_BATTERY_REMAINING = LiveDataPID(
            pid = PID_HYBRID_BATTERY_REMAINING,
            name = "Hybrid Battery Remaining",
            shortName = "HV Battery",
            description = "Hybrid battery pack remaining life",
            category = PIDCategory.HYBRID,
            unit = "%",
            dataBytes = 1,
            minValue = 0.0,
            maxValue = 100.0,
            formula = "A * 100 / 255",
            decimalPlaces = 1,
            gaugeType = GaugeType.BAR,
            warningThreshold = 20.0,
            criticalThreshold = 10.0
        )

        /**
         * Commanded EGR.
         */
        val COMMANDED_EGR = LiveDataPID(
            pid = PID_COMMANDED_EGR,
            name = "Commanded EGR",
            shortName = "EGR",
            description = "Commanded exhaust gas recirculation",
            category = PIDCategory.EMISSION,
            unit = "%",
            dataBytes = 1,
            minValue = 0.0,
            maxValue = 100.0,
            formula = "A * 100 / 255",
            decimalPlaces = 1,
            gaugeType = GaugeType.BAR
        )

        /**
         * Catalyst Temperature Bank 1 Sensor 1.
         */
        val CATALYST_TEMP_BANK1_SENSOR1 = LiveDataPID(
            pid = PID_CATALYST_TEMP_BANK1_SENSOR1,
            name = "Catalyst Temperature B1S1",
            shortName = "Cat Temp",
            description = "Catalyst temperature bank 1 sensor 1",
            category = PIDCategory.EMISSION,
            unit = "°C",
            alternativeUnits = listOf(
                UnitConversion("°C", "°F", 1.8, 32.0)
            ),
            dataBytes = 2,
            minValue = -40.0,
            maxValue = 6513.5,
            formula = "(A * 256 + B) / 10 - 40",
            decimalPlaces = 1,
            gaugeType = GaugeType.LINEAR,
            warningThreshold = 800.0,
            criticalThreshold = 900.0
        )

        /**
         * Map of standard PIDs by PID number.
         */
        val STANDARD_PIDS: Map<Int, LiveDataPID> by lazy {
            mapOf(
                PID_ENGINE_LOAD to ENGINE_LOAD,
                PID_COOLANT_TEMP to COOLANT_TEMP,
                PID_SHORT_TERM_FUEL_TRIM_BANK1 to SHORT_TERM_FUEL_TRIM_BANK1,
                PID_LONG_TERM_FUEL_TRIM_BANK1 to LONG_TERM_FUEL_TRIM_BANK1,
                PID_FUEL_PRESSURE to FUEL_PRESSURE,
                PID_INTAKE_MANIFOLD_PRESSURE to INTAKE_MANIFOLD_PRESSURE,
                PID_ENGINE_RPM to ENGINE_RPM,
                PID_VEHICLE_SPEED to VEHICLE_SPEED,
                PID_TIMING_ADVANCE to TIMING_ADVANCE,
                PID_INTAKE_AIR_TEMP to INTAKE_TEMP,
                PID_MAF_RATE to MAF_RATE,
                PID_THROTTLE_POSITION to THROTTLE_POSITION,
                PID_RUNTIME_SINCE_START to RUNTIME_SINCE_START,
                PID_DISTANCE_WITH_MIL to DISTANCE_WITH_MIL,
                PID_FUEL_LEVEL to FUEL_LEVEL,
                PID_DISTANCE_SINCE_CLEAR to DISTANCE_SINCE_CLEAR,
                PID_BAROMETRIC_PRESSURE to BAROMETRIC_PRESSURE,
                PID_CATALYST_TEMP_BANK1_SENSOR1 to CATALYST_TEMP_BANK1_SENSOR1,
                PID_CONTROL_MODULE_VOLTAGE to CONTROL_MODULE_VOLTAGE,
                PID_AMBIENT_AIR_TEMP to AMBIENT_AIR_TEMP,
                PID_COMMANDED_EGR to COMMANDED_EGR,
                PID_ETHANOL_FUEL_PERCENT to ETHANOL_FUEL_PERCENT,
                PID_HYBRID_BATTERY_REMAINING to HYBRID_BATTERY_REMAINING,
                PID_ENGINE_OIL_TEMP to ENGINE_OIL_TEMP,
                PID_ENGINE_FUEL_RATE to ENGINE_FUEL_RATE
            )
        }

        /**
         * Gets a standard PID by number.
         */
        fun getStandardPID(pid: Int): LiveDataPID? = STANDARD_PIDS[pid]

        /**
         * Gets all PIDs for a category.
         */
        fun getPIDsForCategory(category: PIDCategory): List<LiveDataPID> =
            STANDARD_PIDS.values.filter { it.category == category }

        /**
         * Gets commonly supported PIDs.
         */
        fun getCommonPIDs(): List<LiveDataPID> =
            STANDARD_PIDS.values.filter { it.commonlySupported }

        /**
         * Creates a PID from a definition.
         */
        fun fromDefinition(def: PIDDefinition): LiveDataPID = LiveDataPID(
            pid = def.pid,
            name = def.name,
            shortName = def.shortName,
            description = def.description,
            category = def.category,
            unit = def.unit,
            dataBytes = def.bytes,
            minValue = def.minValue,
            maxValue = def.maxValue,
            formula = def.formulaString,
            decoder = def.formula
        )
    }
}

// ==================== PID Category ====================

/**
 * Category for grouping related PIDs.
 * 
 * @property displayName Human-readable category name
 * @property icon Icon name for UI
 * @property sortOrder Order for display sorting
 */
enum class PIDCategory(
    val displayName: String,
    val icon: String,
    val sortOrder: Int
) {
    ENGINE("Engine", "engine", 0),
    FUEL("Fuel System", "fuel", 1),
    IGNITION("Ignition", "spark", 2),
    SPEED("Speed & Distance", "speedometer", 3),
    TEMPERATURE("Temperature", "thermometer", 4),
    PRESSURE("Pressure", "gauge", 5),
    OXYGEN_SENSOR("Oxygen Sensors", "o2", 6),
    EMISSION("Emissions", "emission", 7),
    ELECTRICAL("Electrical", "battery", 8),
    TRANSMISSION("Transmission", "transmission", 9),
    HYBRID("Hybrid/EV", "ev", 10),
    OTHER("Other", "misc", 99);

    companion object {
        /**
         * Gets category from PID code prefix.
         */
        fun fromPID(pid: Int): PIDCategory = when (pid) {
            in 0x04..0x11 -> ENGINE
            in 0x14..0x1B -> OXYGEN_SENSOR
            0x2F -> FUEL
            in 0x3C..0x3F -> EMISSION
            0x42 -> ELECTRICAL
            in 0x46..0x4C -> ENGINE
            0x5B -> HYBRID
            0x5C -> TEMPERATURE
            0x5E -> FUEL
            else -> OTHER
        }
    }
}

// ==================== Gauge Type ====================

/**
 * Type of gauge visualization for a PID.
 */
enum class GaugeType(val displayName: String) {
    /** Simple numeric display */
    NUMERIC("Numeric"),
    
    /** Horizontal or vertical bar */
    BAR("Bar"),
    
    /** Circular/radial gauge */
    RADIAL("Radial"),
    
    /** Linear scale gauge */
    LINEAR("Linear"),
    
    /** Time-series graph */
    GRAPH("Graph"),
    
    /** Boolean on/off indicator */
    BOOLEAN("Boolean"),
    
    /** Text display for enumerated values */
    TEXT("Text")
}

// ==================== Value Status ====================

/**
 * Status of a PID value based on thresholds.
 */
enum class ValueStatus(
    val displayName: String,
    val color: String,
    val priority: Int
) {
    /** Value is within normal range */
    NORMAL("Normal", "green", 0),
    
    /** Value is in warning range */
    WARNING("Warning", "orange", 1),
    
    /** Value is in critical range */
    CRITICAL("Critical", "red", 2);

    /** Whether attention is needed */
    val needsAttention: Boolean
        get() = this != NORMAL
}

// ==================== Unit Conversion ====================

/**
 * Definition for unit conversion.
 * 
 * @property fromUnit Source unit
 * @property toUnit Target unit
 * @property factor Multiplication factor
 * @property offset Addition offset (applied after factor)
 */
data class UnitConversion(
    val fromUnit: String,
    val toUnit: String,
    val factor: Double,
    val offset: Double = 0.0
) : Serializable {
    
    /**
     * Converts a value from source to target unit.
     * 
     * Formula: result = value * factor + offset
     */
    fun convert(value: Double): Double = value * factor + offset
    
    /**
     * Reverses conversion from target to source unit.
     * 
     * Formula: result = (value - offset) / factor
     */
    fun reverse(value: Double): Double = (value - offset) / factor
    
    companion object {
        private const val serialVersionUID = 1L
        
        // Common conversions
        val CELSIUS_TO_FAHRENHEIT = UnitConversion("°C", "°F", 1.8, 32.0)
        val KMH_TO_MPH = UnitConversion("km/h", "mph", 0.621371)
        val KPA_TO_PSI = UnitConversion("kPa", "psi", 0.145038)
        val KPA_TO_INHG = UnitConversion("kPa", "inHg", 0.2953)
        val LITERS_TO_GALLONS = UnitConversion("L", "gal", 0.264172)
        val KM_TO_MILES = UnitConversion("km", "mi", 0.621371)
        val GRAMS_TO_OUNCES = UnitConversion("g", "oz", 0.035274)
    }
}

// ==================== Bit Definition ====================

/**
 * Definition for a bit in a bit-encoded PID.
 * 
 * @property bit Bit position (0 = LSB)
 * @property name Name of the flag/feature
 * @property description Detailed description
 * @property activeHigh Whether bit being 1 means active (default: true)
 */
data class BitDefinition(
    val bit: Int,
    val name: String,
    val description: String? = null,
    val activeHigh: Boolean = true
) : Serializable {
    
    /**
     * Checks if this bit is set in a value.
     * 
     * @param value Integer value to check
     * @return true if bit indicates active state
     */
    fun isSet(value: Int): Boolean {
        val bitSet = (value shr bit) and 1 == 1
        return if (activeHigh) bitSet else !bitSet
    }
    
    companion object {
        private const val serialVersionUID = 1L
    }
}

// ==================== Live Data Value ====================

/**
 * A decoded PID value with metadata.
 * 
 * @property pid The PID definition
 * @property value Decoded numeric value
 * @property rawBytes Raw response bytes
 * @property timestamp When the value was read
 * @property unit Unit of the value
 * @property status Value status (normal/warning/critical)
 */
data class LiveDataValue(
    val pid: LiveDataPID,
    val value: Double,
    val rawBytes: ByteArray,
    val timestamp: Long = System.currentTimeMillis(),
    val unit: String = pid.unit,
    val status: ValueStatus = ValueStatus.NORMAL
) : Serializable {
    
    /**
     * Value formatted according to PID definition.
     */
    val formattedValue: String
        get() = pid.formatValue(value)
    
    /**
     * Value with unit for display.
     */
    val displayValue: String
        get() = pid.formatValueWithUnit(value)
    
    /**
     * Whether value needs attention (warning or critical).
     */
    val needsAttention: Boolean
        get() = status.needsAttention
    
    /**
     * PID identifier.
     */
    val pidIdentifier: String
        get() = pid.identifier
    
    /**
     * PID name.
     */
    val pidName: String
        get() = pid.name
    
    /**
     * Age of the value in milliseconds.
     */
    val ageMs: Long
        get() = System.currentTimeMillis() - timestamp
    
    /**
     * Whether the value is stale (older than threshold).
     */
    fun isStale(maxAgeMs: Long = 5000L): Boolean = ageMs > maxAgeMs
    
    /**
     * Converts to an alternative unit.
     */
    fun convertTo(targetUnit: String): LiveDataValue? {
        val convertedValue = pid.convertTo(value, targetUnit) ?: return null
        return copy(value = convertedValue, unit = targetUnit)
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as LiveDataValue
        
        if (pid.pid != other.pid.pid) return false
        if (value != other.value) return false
        if (timestamp != other.timestamp) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = pid.pid
        result = 31 * result + value.hashCode()
        result = 31 * result + timestamp.hashCode()
        return result
    }
    
    companion object {
        private const val serialVersionUID = 1L
    }
}

// ==================== PID Definition ====================

/**
 * Definition for registering custom PIDs.
 * 
 * @property pid PID number
 * @property name Human-readable name
 * @property shortName Short name
 * @property description Description
 * @property category Category
 * @property unit Unit string
 * @property bytes Number of data bytes
 * @property minValue Minimum value
 * @property maxValue Maximum value
 * @property formula Decoding function
 * @property formulaString Human-readable formula
 */
data class PIDDefinition(
    val pid: Int,
    val name: String,
    val shortName: String,
    val description: String?,
    val category: PIDCategory,
    val unit: String,
    val bytes: Int,
    val minValue: Double,
    val maxValue: Double,
    val formula: (ByteArray) -> Double,
    val formulaString: String? = null
)

// ==================== Extension Functions ====================

/**
 * Gets byte A (first byte) as unsigned int.
 */
private fun ByteArray.a(): Int = if (isNotEmpty()) this[0].toInt() and 0xFF else 0

/**
 * Gets byte B (second byte) as unsigned int.
 */
private fun ByteArray.b(): Int = if (size > 1) this[1].toInt() and 0xFF else 0

/**
 * Gets byte C (third byte) as unsigned int.
 */
private fun ByteArray.c(): Int = if (size > 2) this[2].toInt() and 0xFF else 0

/**
 * Gets byte D (fourth byte) as unsigned int.
 */
private fun ByteArray.d(): Int = if (size > 3) this[3].toInt() and 0xFF else 0

/**
 * Converts Int to hex string with padding.
 */
private fun Int.toHexString(minLength: Int = 2): String =
    toString(16).uppercase(Locale.ROOT).padStart(minLength, '0')

/**
 * Filters PIDs by category.
 */
fun List<LiveDataPID>.filterByCategory(category: PIDCategory): List<LiveDataPID> =
    filter { it.category == category }

/**
 * Filters PIDs by supported status.
 */
fun List<LiveDataPID>.commonlySupported(): List<LiveDataPID> =
    filter { it.commonlySupported }

/**
 * Sorts PIDs by category then name.
 */
fun List<LiveDataPID>.sortedByCategoryAndName(): List<LiveDataPID> =
    sortedWith(compareBy({ it.category.sortOrder }, { it.name }))

/**
 * Groups PIDs by category.
 */
fun List<LiveDataPID>.groupByCategory(): Map<PIDCategory, List<LiveDataPID>> =
    groupBy { it.category }

/**
 * Gets the latest value for each PID.
 */
fun List<LiveDataValue>.latestByPID(): Map<Int, LiveDataValue> =
    groupBy { it.pid.pid }
        .mapValues { (_, values) -> 
            values.maxByOrNull { it.timestamp } ?: values.firstOrNull() ?: error("No values found for PID")
        }

/**
 * Gets values that need attention.
 */
fun List<LiveDataValue>.needingAttention(): List<LiveDataValue> =
    filter { it.needsAttention }

/**
 * Gets values for a specific category.
 */
fun List<LiveDataValue>.forCategory(category: PIDCategory): List<LiveDataValue> =
    filter { it.pid.category == category }

/**
 * Calculates average value for a PID.
 */
fun List<LiveDataValue>.averageValue(): Double? =
    if (isEmpty()) null else map { it.value }.average()

/**
 * Gets min value.
 */
fun List<LiveDataValue>.minValue(): Double? =
    minOfOrNull { it.value }

/**
 * Gets max value.
 */
fun List<LiveDataValue>.maxValue(): Double? =
    maxOfOrNull { it.value }