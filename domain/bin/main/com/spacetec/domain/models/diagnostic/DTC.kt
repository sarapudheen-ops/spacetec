package com.spacetec.domain.models.diagnostic

import java.io.Serializable
import java.util.Locale

/**
 * DTC System categories based on the first character of the DTC code.
 *
 * The first character of a DTC identifies which vehicle system
 * reported the fault:
 * - **P** (Powertrain): Engine, transmission, and drivetrain
 * - **C** (Chassis): ABS, steering, suspension
 * - **B** (Body): Airbags, climate control, lighting
 * - **U** (Network): CAN bus communication faults
 *
 * @property prefix The single character prefix (P, C, B, U)
 * @property description Short description of the system
 * @property fullName Full descriptive name of the system
 *
 * @author SpaceTec Team
 * @since 1.0.0
 */
enum class DTCSystem(
    val prefix: Char,
    val description: String,
    val fullName: String
) {
    /**
     * Powertrain system - Engine, transmission, and emission controls.
     */
    POWERTRAIN('P', "Powertrain", "Engine, Transmission, and Emission Controls"),

    /**
     * Chassis system - Brakes, steering, and suspension.
     */
    CHASSIS('C', "Chassis", "Brakes, Steering, and Suspension"),

    /**
     * Body system - Climate control, lighting, airbags, and accessories.
     */
    BODY('B', "Body", "Climate Control, Lighting, Airbags, and Accessories"),

    /**
     * Network system - CAN bus and inter-module communication.
     */
    NETWORK('U', "Network", "CAN Bus and Module Communication");

    companion object {
        /**
         * Gets DTCSystem from a prefix character.
         *
         * @param prefix Character prefix (P, C, B, U)
         * @return Corresponding DTCSystem or null if invalid
         */
        fun fromPrefix(prefix: Char): DTCSystem? {
            return entries.find { it.prefix.equals(prefix, ignoreCase = true) }
        }

        /**
         * Gets DTCSystem from a DTC code string.
         *
         * @param code DTC code string (e.g., "P0420")
         * @return Corresponding DTCSystem or null if invalid
         */
        fun fromCode(code: String): DTCSystem? {
            return if (code.isNotEmpty()) fromPrefix(code[0]) else null
        }
    }
}

/**
 * DTC Code Type based on the second character of the DTC code.
 *
 * The second character indicates whether the code is defined by
 * SAE/ISO standards or by the vehicle manufacturer:
 * - **0**: Generic (SAE/ISO defined)
 * - **1**: Manufacturer specific
 * - **2**: Generic (SAE/ISO defined, extended)
 * - **3**: Reserved/Joint SAE-Manufacturer
 *
 * @property typeDigit The character representing the type (0-3)
 * @property description Description of the code type
 *
 * @author SpaceTec Team
 * @since 1.0.0
 */
enum class DTCCodeType(
    val typeDigit: Char,
    val description: String
) {
    /**
     * Generic SAE/ISO standard code.
     * These codes have the same meaning across all manufacturers.
     */
    GENERIC('0', "SAE/ISO Standard (Generic)"),

    /**
     * Manufacturer-specific code.
     * The meaning may vary between manufacturers.
     */
    MANUFACTURER_SPECIFIC('1', "Manufacturer Specific"),

    /**
     * Generic SAE/ISO standard code (extended range).
     */
    GENERIC_EXTENDED('2', "SAE/ISO Standard (Extended)"),

    /**
     * Reserved for future use or joint SAE-Manufacturer codes.
     */
    RESERVED('3', "Reserved/Joint SAE-Manufacturer");

    companion object {
        /**
         * Gets DTCCodeType from type digit character.
         *
         * @param digit Type digit (0-3)
         * @return Corresponding DTCCodeType
         */
        fun fromTypeDigit(digit: Char): DTCCodeType {
            return entries.find { it.typeDigit == digit } ?: GENERIC
        }

        /**
         * Gets DTCCodeType from DTC code string.
         *
         * @param code DTC code string (e.g., "P0420")
         * @return Corresponding DTCCodeType
         */
        fun fromCode(code: String): DTCCodeType {
            return if (code.length >= 2) fromTypeDigit(code[1]) else GENERIC
        }
    }
}

/**
 * DTC Subsystem categories based on the third character of the DTC code.
 *
 * The third character identifies the specific subsystem within the
 * main system. The meaning varies depending on the main system (P, C, B, U).
 *
 * @property code Numeric code (0x0 to 0xF)
 * @property system The parent DTCSystem
 * @property description Description of the subsystem
 *
 * @author SpaceTec Team
 * @since 1.0.0
 */
enum class DTCSubsystem(
    val code: Int,
    val system: DTCSystem,
    val description: String
) {
    // ==================== Powertrain Subsystems (P codes) ====================

    /** Fuel and Air Metering (P0xxx) */
    P_FUEL_AIR_METERING(0x0, DTCSystem.POWERTRAIN, "Fuel and Air Metering"),

    /** Fuel and Air Metering (P1xxx) */
    P_FUEL_AIR_METERING_AUX(0x1, DTCSystem.POWERTRAIN, "Fuel and Air Metering"),

    /** Fuel and Air Metering - Injector Circuit (P2xxx) */
    P_FUEL_AIR_INJECTOR(0x2, DTCSystem.POWERTRAIN, "Fuel and Air Metering (Injector Circuit)"),

    /** Ignition System or Misfire (P3xxx) */
    P_IGNITION(0x3, DTCSystem.POWERTRAIN, "Ignition System or Misfire"),

    /** Auxiliary Emission Controls (P4xxx) */
    P_EMISSION_CONTROL(0x4, DTCSystem.POWERTRAIN, "Auxiliary Emission Controls"),

    /** Vehicle Speed Control and Idle Control System (P5xxx) */
    P_SPEED_IDLE(0x5, DTCSystem.POWERTRAIN, "Vehicle Speed and Idle Control"),

    /** Computer and Auxiliary Output Circuits (P6xxx) */
    P_COMPUTER(0x6, DTCSystem.POWERTRAIN, "Computer and Auxiliary Output Circuits"),

    /** Transmission (P7xxx) */
    P_TRANSMISSION(0x7, DTCSystem.POWERTRAIN, "Transmission"),

    /** Transmission (P8xxx) */
    P_TRANSMISSION_AUX(0x8, DTCSystem.POWERTRAIN, "Transmission"),

    /** SAE Reserved (P9xxx) */
    P_RESERVED_9(0x9, DTCSystem.POWERTRAIN, "SAE Reserved"),

    /** Hybrid Propulsion (PAxxxx) */
    P_HYBRID(0xA, DTCSystem.POWERTRAIN, "Hybrid Propulsion"),

    /** SAE Reserved (PBxxx) */
    P_RESERVED_B(0xB, DTCSystem.POWERTRAIN, "SAE Reserved"),

    /** SAE Reserved (PCxxx) */
    P_RESERVED_C(0xC, DTCSystem.POWERTRAIN, "SAE Reserved"),

    /** SAE Reserved (PDxxx) */
    P_RESERVED_D(0xD, DTCSystem.POWERTRAIN, "SAE Reserved"),

    /** SAE Reserved (PExxx) */
    P_RESERVED_E(0xE, DTCSystem.POWERTRAIN, "SAE Reserved"),

    /** SAE Reserved (PFxxx) */
    P_RESERVED_F(0xF, DTCSystem.POWERTRAIN, "SAE Reserved"),

    // ==================== Chassis Subsystems (C codes) ====================

    /** Common Chassis (C0xxx) */
    C_COMMON(0x0, DTCSystem.CHASSIS, "Common"),

    /** Manufacturer Specific (C1xxx) */
    C_MANUFACTURER(0x1, DTCSystem.CHASSIS, "Manufacturer Specific"),

    /** Manufacturer Specific (C2xxx) */
    C_MANUFACTURER_2(0x2, DTCSystem.CHASSIS, "Manufacturer Specific"),

    /** SAE Reserved (C3xxx) */
    C_RESERVED(0x3, DTCSystem.CHASSIS, "SAE Reserved"),

    // ==================== Body Subsystems (B codes) ====================

    /** Common Body (B0xxx) */
    B_COMMON(0x0, DTCSystem.BODY, "Common"),

    /** Manufacturer Specific (B1xxx) */
    B_MANUFACTURER(0x1, DTCSystem.BODY, "Manufacturer Specific"),

    /** Manufacturer Specific (B2xxx) */
    B_MANUFACTURER_2(0x2, DTCSystem.BODY, "Manufacturer Specific"),

    /** SAE Reserved (B3xxx) */
    B_RESERVED(0x3, DTCSystem.BODY, "SAE Reserved"),

    // ==================== Network Subsystems (U codes) ====================

    /** Common Network (U0xxx) */
    U_COMMON(0x0, DTCSystem.NETWORK, "Common"),

    /** Manufacturer Specific (U1xxx) */
    U_MANUFACTURER(0x1, DTCSystem.NETWORK, "Manufacturer Specific"),

    /** Manufacturer Specific (U2xxx) */
    U_MANUFACTURER_2(0x2, DTCSystem.NETWORK, "Manufacturer Specific"),

    /** SAE Reserved (U3xxx) */
    U_RESERVED(0x3, DTCSystem.NETWORK, "SAE Reserved"),

    /** Unknown/Default */
    UNKNOWN(-1, DTCSystem.POWERTRAIN, "Unknown");

    companion object {
        /**
         * Gets DTCSubsystem from a DTC code string.
         *
         * @param code DTC code string (e.g., "P0420")
         * @return Corresponding DTCSubsystem
         */
        fun fromCode(code: String): DTCSubsystem {
            if (code.length < 3) return UNKNOWN

            val system = DTCSystem.fromCode(code) ?: return UNKNOWN
            val subsystemChar = code[2]
            val subsystemCode = subsystemChar.digitToIntOrNull(16) ?: return UNKNOWN

            return fromCodeAndSystem(subsystemCode, system) ?: UNKNOWN
        }

        /**
         * Gets DTCSubsystem from subsystem code and system.
         *
         * @param subsystemCode Numeric subsystem code (0-15)
         * @param system Parent DTCSystem
         * @return Corresponding DTCSubsystem or null
         */
        fun fromCodeAndSystem(subsystemCode: Int, system: DTCSystem): DTCSubsystem? {
            return entries.find { it.code == subsystemCode && it.system == system }
        }
    }
}

/**
 * DTC Severity levels indicating the urgency of repair.
 *
 * Severity helps prioritize which DTCs should be addressed first
 * based on potential impact on vehicle safety and emissions.
 *
 * @property level Numeric severity level (higher = more severe)
 * @property description Human-readable description
 * @property priority Priority for sorting (lower = higher priority)
 * @property color ARGB color value for UI display
 *
 * @author SpaceTec Team
 * @since 1.0.0
 */
enum class DTCSeverity(
    val level: Int,
    val description: String,
    val priority: Int,
    val color: Long
) {
    /**
     * Critical severity - Immediate attention required.
     * May affect vehicle safety or cause severe damage.
     */
    CRITICAL(4, "Critical - Immediate attention required", 0, 0xFFFF0000),

    /**
     * High severity - Should be addressed soon.
     * May affect vehicle performance or emissions.
     */
    HIGH(3, "High - Should be addressed soon", 10, 0xFFFF6600),

    /**
     * Medium severity - Monitor and plan repair.
     * May affect fuel economy or minor performance.
     */
    MEDIUM(2, "Medium - Monitor and plan repair", 20, 0xFFFFCC00),

    /**
     * Low severity - Minor issue.
     * Cosmetic or non-critical system fault.
     */
    LOW(1, "Low - Minor issue", 30, 0xFF00CC00),

    /**
     * Unknown severity - Cannot be determined.
     */
    UNKNOWN(0, "Unknown severity", 40, 0xFF888888);

    companion object {
        /**
         * Determines severity from a DTC code.
         *
         * This uses heuristics based on the code structure
         * to estimate severity. Actual severity may vary.
         *
         * @param code DTC code string
         * @return Estimated severity level
         */
        fun fromCode(code: String): DTCSeverity {
            if (code.length < 5) return UNKNOWN

            val system = DTCSystem.fromCode(code) ?: return UNKNOWN
            val codeType = DTCCodeType.fromCode(code)
            val numericCode = code.drop(1).toIntOrNull() ?: return UNKNOWN

            return fromDTCInfo(system, codeType, numericCode)
        }

        /**
         * Determines severity from DTC components.
         *
         * @param system DTC system
         * @param codeType Generic or manufacturer specific
         * @param numericCode Numeric portion of code
         * @return Estimated severity level
         */
        fun fromDTCInfo(
            system: DTCSystem,
            codeType: DTCCodeType,
            numericCode: Int
        ): DTCSeverity {
            // Safety-related systems are higher severity
            return when (system) {
                DTCSystem.POWERTRAIN -> {
                    when {
                        // Misfire codes are high severity
                        numericCode in 300..399 -> HIGH
                        // Catalyst and emission codes
                        numericCode in 420..450 -> MEDIUM
                        // Transmission codes
                        numericCode in 700..799 -> HIGH
                        // Fuel system codes
                        numericCode in 170..179 -> HIGH
                        else -> MEDIUM
                    }
                }
                DTCSystem.CHASSIS -> {
                    // Chassis codes often affect safety
                    when {
                        // ABS/Brake codes
                        numericCode in 0..99 -> CRITICAL
                        // Stability control
                        numericCode in 100..199 -> HIGH
                        else -> MEDIUM
                    }
                }
                DTCSystem.BODY -> {
                    when {
                        // Airbag codes
                        numericCode in 0..99 -> CRITICAL
                        // Restraint system
                        numericCode in 100..199 -> HIGH
                        else -> LOW
                    }
                }
                DTCSystem.NETWORK -> {
                    // Communication faults vary in severity
                    when {
                        // Lost communication with critical modules
                        numericCode in 100..199 -> HIGH
                        else -> MEDIUM
                    }
                }
            }
        }
    }
}

/**
 * DTC Status flags per ISO 15031-6 / SAE J2012.
 *
 * The status byte provides detailed information about the current
 * state of the DTC, including whether the fault is active,
 * whether the MIL is on, and test completion status.
 *
 * ## Status Byte Bit Definitions
 * - Bit 0: Test failed (current)
 * - Bit 1: Test failed this drive cycle
 * - Bit 2: Pending DTC
 * - Bit 3: Confirmed DTC
 * - Bit 4: Test not completed since clear
 * - Bit 5: Test failed since clear
 * - Bit 6: Test not completed this drive cycle
 * - Bit 7: Warning indicator (MIL) requested
 *
 * @property testFailed Test failed at time of request
 * @property testFailedThisCycle Test failed during current drive cycle
 * @property pendingDTC Pending DTC (awaiting confirmation)
 * @property confirmedDTC Confirmed/stored DTC
 * @property testNotCompletedSinceClear Monitor not run since DTC clear
 * @property testFailedSinceClear Test has failed since last clear
 * @property testNotCompletedThisCycle Monitor not run this drive cycle
 * @property warningIndicatorRequested MIL (Check Engine Light) is on
 * @property rawByte Original status byte from ECU
 * @property isPermanent True if this is a permanent DTC (Service 0A)
 * @property isStored True if this is a stored DTC (Service 03)
 *
 * @author SpaceTec Team
 * @since 1.0.0
 */
data class DTCStatus(
    val testFailed: Boolean = false,
    val testFailedThisCycle: Boolean = false,
    val pendingDTC: Boolean = false,
    val confirmedDTC: Boolean = false,
    val testNotCompletedSinceClear: Boolean = false,
    val testFailedSinceClear: Boolean = false,
    val testNotCompletedThisCycle: Boolean = false,
    val warningIndicatorRequested: Boolean = false,
    val rawByte: Byte? = null,
    val isPermanent: Boolean = false,
    val isStored: Boolean = false
) : Serializable {

    /**
     * Converts status to a list of human-readable flag strings.
     *
     * @return List of active status flags
     */
    fun toFlagsList(): List<String> {
        return buildList {
            if (testFailed) add("Test Failed")
            if (testFailedThisCycle) add("Failed This Cycle")
            if (pendingDTC) add("Pending")
            if (confirmedDTC) add("Confirmed")
            if (testNotCompletedSinceClear) add("Not Tested Since Clear")
            if (testFailedSinceClear) add("Failed Since Clear")
            if (testNotCompletedThisCycle) add("Not Tested This Cycle")
            if (warningIndicatorRequested) add("MIL On")
            if (isPermanent) add("Permanent")
            if (isStored) add("Stored")
        }
    }

    /**
     * Gets the primary status description.
     *
     * @return Primary status as a string
     */
    fun getPrimaryStatus(): String {
        return when {
            isPermanent -> "Permanent"
            confirmedDTC && warningIndicatorRequested -> "Confirmed (MIL On)"
            confirmedDTC -> "Confirmed"
            pendingDTC -> "Pending"
            isStored -> "Stored"
            testFailed -> "Active"
            else -> "Unknown"
        }
    }

    /**
     * Checks if the DTC is currently active.
     *
     * @return True if fault is currently present
     */
    fun isActive(): Boolean = testFailed || testFailedThisCycle

    companion object {
        private const val serialVersionUID = 1L

        /** Bit masks for status byte parsing */
        private const val BIT_TEST_FAILED = 0x01
        private const val BIT_TEST_FAILED_THIS_CYCLE = 0x02
        private const val BIT_PENDING_DTC = 0x04
        private const val BIT_CONFIRMED_DTC = 0x08
        private const val BIT_NOT_COMPLETED_SINCE_CLEAR = 0x10
        private const val BIT_TEST_FAILED_SINCE_CLEAR = 0x20
        private const val BIT_NOT_COMPLETED_THIS_CYCLE = 0x40
        private const val BIT_WARNING_INDICATOR = 0x80

        /** Default status with no flags set */
        val DEFAULT = DTCStatus()

        /**
         * Parses DTCStatus from a raw status byte.
         *
         * @param statusByte Raw status byte from ECU
         * @return Parsed DTCStatus
         */
        fun fromByte(statusByte: Byte): DTCStatus {
            val status = statusByte.toInt() and 0xFF
            return DTCStatus(
                testFailed = (status and BIT_TEST_FAILED) != 0,
                testFailedThisCycle = (status and BIT_TEST_FAILED_THIS_CYCLE) != 0,
                pendingDTC = (status and BIT_PENDING_DTC) != 0,
                confirmedDTC = (status and BIT_CONFIRMED_DTC) != 0,
                testNotCompletedSinceClear = (status and BIT_NOT_COMPLETED_SINCE_CLEAR) != 0,
                testFailedSinceClear = (status and BIT_TEST_FAILED_SINCE_CLEAR) != 0,
                testNotCompletedThisCycle = (status and BIT_NOT_COMPLETED_THIS_CYCLE) != 0,
                warningIndicatorRequested = (status and BIT_WARNING_INDICATOR) != 0,
                rawByte = statusByte
            )
        }

        /**
         * Creates a status for a stored DTC (Service 03).
         *
         * @param statusByte Optional raw status byte
         * @return DTCStatus configured for stored DTC
         */
        fun stored(statusByte: Byte? = null): DTCStatus {
            return if (statusByte != null) {
                fromByte(statusByte).copy(isStored = true, confirmedDTC = true)
            } else {
                DTCStatus(
                    isStored = true,
                    confirmedDTC = true,
                    warningIndicatorRequested = true
                )
            }
        }

        /**
         * Creates a status for a pending DTC (Service 07).
         *
         * @return DTCStatus configured for pending DTC
         */
        fun pending(): DTCStatus {
            return DTCStatus(
                pendingDTC = true,
                testFailedThisCycle = true
            )
        }

        /**
         * Creates a status for a permanent DTC (Service 0A).
         *
         * @param statusByte Optional raw status byte
         * @return DTCStatus configured for permanent DTC
         */
        fun permanent(statusByte: Byte? = null): DTCStatus {
            return if (statusByte != null) {
                fromByte(statusByte).copy(isPermanent = true, confirmedDTC = true)
            } else {
                DTCStatus(
                    isPermanent = true,
                    confirmedDTC = true,
                    warningIndicatorRequested = true
                )
            }
        }
    }
}

/**
 * Freeze Frame data captured when a DTC was set.
 *
 * When certain DTCs are stored, the ECU captures a snapshot of
 * key engine parameters at the moment of the fault. This data
 * helps technicians diagnose the root cause.
 *
 * @property dtcCode The DTC that triggered this freeze frame
 * @property frameNumber Frame number (usually 0)
 * @property timestamp When the freeze frame was captured
 * @property pidValues Map of captured PID values
 * @property fuelSystemStatus Fuel system status code
 * @property calculatedLoad Calculated engine load (%)
 * @property coolantTemp Engine coolant temperature (°C)
 * @property shortTermFuelTrim Short term fuel trim (%)
 * @property longTermFuelTrim Long term fuel trim (%)
 * @property intakeManifoldPressure Intake manifold pressure (kPa)
 * @property engineRpm Engine RPM
 * @property vehicleSpeed Vehicle speed (km/h)
 * @property timingAdvance Ignition timing advance (°)
 * @property intakeAirTemp Intake air temperature (°C)
 * @property mafRate Mass air flow rate (g/s)
 * @property throttlePosition Throttle position (%)
 * @property distanceWithMil Distance with MIL on (km)
 * @property runTimeSinceStart Run time since engine start (s)
 * @property rawData Raw freeze frame data bytes
 *
 * @author SpaceTec Team
 * @since 1.0.0
 */
data class FreezeFrame(
    val dtcCode: String,
    val frameNumber: Int = 0,
    val timestamp: Long? = null,
    val pidValues: Map<Int, Any> = emptyMap(),
    val fuelSystemStatus: Int? = null,
    val calculatedLoad: Double? = null,
    val coolantTemp: Double? = null,
    val shortTermFuelTrim: Double? = null,
    val longTermFuelTrim: Double? = null,
    val intakeManifoldPressure: Double? = null,
    val engineRpm: Double? = null,
    val vehicleSpeed: Double? = null,
    val timingAdvance: Double? = null,
    val intakeAirTemp: Double? = null,
    val mafRate: Double? = null,
    val throttlePosition: Double? = null,
    val distanceWithMil: Int? = null,
    val runTimeSinceStart: Int? = null,
    val rawData: ByteArray? = null
) : Serializable {

    /**
     * Gets all captured values as formatted strings for display.
     *
     * @return Map of parameter names to formatted values
     */
    fun getFormattedValues(): Map<String, String> {
        return buildMap {
            calculatedLoad?.let { put("Calculated Load", String.format("%.1f%%", it)) }
            coolantTemp?.let { put("Coolant Temperature", String.format("%.0f°C", it)) }
            shortTermFuelTrim?.let { put("Short Term Fuel Trim", String.format("%+.1f%%", it)) }
            longTermFuelTrim?.let { put("Long Term Fuel Trim", String.format("%+.1f%%", it)) }
            intakeManifoldPressure?.let { put("Intake Manifold Pressure", String.format("%.0f kPa", it)) }
            engineRpm?.let { put("Engine RPM", String.format("%.0f RPM", it)) }
            vehicleSpeed?.let { put("Vehicle Speed", String.format("%.0f km/h", it)) }
            timingAdvance?.let { put("Timing Advance", String.format("%.1f°", it)) }
            intakeAirTemp?.let { put("Intake Air Temperature", String.format("%.0f°C", it)) }
            mafRate?.let { put("MAF Rate", String.format("%.2f g/s", it)) }
            throttlePosition?.let { put("Throttle Position", String.format("%.1f%%", it)) }
            distanceWithMil?.let { put("Distance with MIL", "$it km") }
            runTimeSinceStart?.let { put("Run Time", "${it}s") }
        }
    }

    /**
     * Checks if this freeze frame contains any data.
     *
     * @return True if any data is present
     */
    fun hasData(): Boolean {
        return pidValues.isNotEmpty() ||
                coolantTemp != null ||
                engineRpm != null ||
                vehicleSpeed != null ||
                calculatedLoad != null
    }

    /**
     * Gets a summary of the freeze frame conditions.
     *
     * @return Summary string
     */
    fun getSummary(): String {
        val parts = mutableListOf<String>()

        engineRpm?.let { parts.add("${it.toInt()} RPM") }
        vehicleSpeed?.let { parts.add("${it.toInt()} km/h") }
        coolantTemp?.let { parts.add("${it.toInt()}°C") }

        return if (parts.isNotEmpty()) {
            parts.joinToString(" | ")
        } else {
            "No data available"
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FreezeFrame

        if (dtcCode != other.dtcCode) return false
        if (frameNumber != other.frameNumber) return false
        if (rawData != null) {
            if (other.rawData == null) return false
            if (!rawData.contentEquals(other.rawData)) return false
        } else if (other.rawData != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = dtcCode.hashCode()
        result = 31 * result + frameNumber
        result = 31 * result + (rawData?.contentHashCode() ?: 0)
        return result
    }

    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * Technical Service Bulletin information.
 *
 * TSBs are issued by manufacturers to address known issues and
 * provide repair guidance for common problems.
 *
 * @property bulletinNumber Unique bulletin identifier
 * @property title Brief title of the bulletin
 * @property description Detailed description
 * @property applicableModels List of affected vehicle models
 * @property applicableYears Range of affected model years
 * @property url Link to full bulletin (if available)
 *
 * @author SpaceTec Team
 * @since 1.0.0
 */
data class TechnicalBulletin(
    val bulletinNumber: String,
    val title: String,
    val description: String? = null,
    val applicableModels: List<String> = emptyList(),
    val applicableYears: IntRange? = null,
    val url: String? = null
) : Serializable {

    /**
     * Checks if this TSB applies to a specific model year.
     *
     * @param year Model year to check
     * @return True if TSB applies
     */
    fun appliesToYear(year: Int): Boolean {
        return applicableYears?.contains(year) ?: true
    }

    /**
     * Checks if this TSB applies to a specific model.
     *
     * @param model Vehicle model name
     * @return True if TSB applies
     */
    fun appliesToModel(model: String): Boolean {
        return applicableModels.isEmpty() ||
                applicableModels.any { it.equals(model, ignoreCase = true) }
    }

    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * Estimated repair cost range.
 *
 * Provides an estimate of the labor and parts cost to repair
 * the issue indicated by a DTC.
 *
 * @property laborHoursMin Minimum estimated labor hours
 * @property laborHoursMax Maximum estimated labor hours
 * @property partsMin Minimum parts cost
 * @property partsMax Maximum parts cost
 * @property currency Currency code (default USD)
 *
 * @author SpaceTec Team
 * @since 1.0.0
 */
data class RepairCostEstimate(
    val laborHoursMin: Float,
    val laborHoursMax: Float,
    val partsMin: Float,
    val partsMax: Float,
    val currency: String = "USD"
) : Serializable {

    /**
     * Minimum total estimated cost.
     */
    val totalMin: Float
        get() = (laborHoursMin * AVERAGE_LABOR_RATE) + partsMin

    /**
     * Maximum total estimated cost.
     */
    val totalMax: Float
        get() = (laborHoursMax * AVERAGE_LABOR_RATE) + partsMax

    /**
     * Average total estimated cost.
     */
    val totalAverage: Float
        get() = (totalMin + totalMax) / 2f

    /**
     * Formatted cost range for display.
     */
    val formattedRange: String
        get() = "$currency ${formatCurrency(totalMin)} - ${formatCurrency(totalMax)}"

    /**
     * Formatted labor hours for display.
     */
    val formattedLabor: String
        get() = if (laborHoursMin == laborHoursMax) {
            String.format(Locale.US, "%.1f hours", laborHoursMin)
        } else {
            String.format(Locale.US, "%.1f - %.1f hours", laborHoursMin, laborHoursMax)
        }

    private fun formatCurrency(value: Float): String {
        return String.format(Locale.US, "%.0f", value)
    }

    companion object {
        private const val serialVersionUID = 1L

        /** Average labor rate per hour (adjustable by region) */
        const val AVERAGE_LABOR_RATE = 100f
    }
}

/**
 * Represents a Diagnostic Trouble Code (DTC).
 *
 * DTCs are standardized codes that identify faults in vehicle systems.
 * They follow the SAE J2012 format:
 *
 * ## Code Format: XNNNN
 * - **First character (X)**: System - P (Powertrain), C (Chassis), B (Body), U (Network)
 * - **Second character (N)**: Code type - 0/2 (Generic), 1/3 (Manufacturer-specific)
 * - **Third character (N)**: Subsystem - 0-F (varies by system)
 * - **Fourth-Fifth characters (NN)**: Specific fault - 00-FF
 *
 * ## Example
 * P0420 = Catalyst System Efficiency Below Threshold (Bank 1)
 * - P: Powertrain
 * - 0: Generic/SAE code
 * - 4: Auxiliary Emission Controls
 * - 20: Catalyst efficiency fault
 *
 * @property code The DTC code string (e.g., "P0420")
 * @property description Human-readable description of the fault
 * @property explanation Detailed explanation of what the code means
 * @property system The vehicle system (Powertrain, Chassis, Body, Network)
 * @property subsystem The specific subsystem category
 * @property codeType Generic (SAE) or manufacturer-specific
 * @property status Current status flags for this DTC
 * @property severity Estimated severity level
 * @property rawBytes Original bytes from ECU
 * @property firstOccurrence Timestamp when first detected
 * @property lastOccurrence Timestamp of most recent occurrence
 * @property occurrenceCount Number of times detected
 * @property ecuAddress Address of ECU that reported this DTC
 * @property ecuName Name/type of ECU
 * @property freezeFrame Associated freeze frame data
 * @property possibleCauses List of potential causes
 * @property symptoms Observable symptoms
 * @property diagnosticSteps Recommended diagnostic procedures
 * @property technicalBulletins Related TSBs
 * @property estimatedRepairCost Cost estimate for repair
 * @property relatedDTCs DTCs that commonly occur together
 *
 * @author SpaceTec Team
 * @since 1.0.0
 */
data class DTC(
    val code: String,
    val description: String,
    val explanation: String? = null,
    val system: DTCSystem,
    val subsystem: DTCSubsystem,
    val codeType: DTCCodeType,
    val status: DTCStatus = DTCStatus.DEFAULT,
    val severity: DTCSeverity = DTCSeverity.UNKNOWN,
    val rawBytes: ByteArray? = null,
    val firstOccurrence: Long? = null,
    val lastOccurrence: Long? = null,
    val occurrenceCount: Int = 1,
    val ecuAddress: Int? = null,
    val ecuName: String? = null,
    val freezeFrame: FreezeFrame? = null,
    val possibleCauses: List<String> = emptyList(),
    val symptoms: List<String> = emptyList(),
    val diagnosticSteps: List<String> = emptyList(),
    val technicalBulletins: List<TechnicalBulletin> = emptyList(),
    val estimatedRepairCost: RepairCostEstimate? = null,
    val relatedDTCs: List<String> = emptyList()
) : Serializable, Comparable<DTC> {

    // ==================== Computed Properties ====================

    /**
     * System character (P, C, B, U).
     */
    val systemChar: Char
        get() = code.firstOrNull()?.uppercaseChar() ?: 'P'

    /**
     * Type character (0-3).
     */
    val typeChar: Char
        get() = code.getOrNull(1) ?: '0'

    /**
     * Subsystem character (0-F).
     */
    val subsystemChar: Char
        get() = code.getOrNull(2) ?: '0'

    /**
     * Numeric code portion (e.g., 420 from P0420).
     */
    val numericCode: Int
        get() = code.drop(1).toIntOrNull(16) ?: 0

    /**
     * Last two digits as specific fault code.
     */
    val faultCode: Int
        get() = code.takeLast(2).toIntOrNull(16) ?: 0

    /**
     * Whether this is a generic (SAE-defined) code.
     */
    val isGeneric: Boolean
        get() = codeType == DTCCodeType.GENERIC || codeType == DTCCodeType.GENERIC_EXTENDED

    /**
     * Whether this is a manufacturer-specific code.
     */
    val isManufacturerSpecific: Boolean
        get() = codeType == DTCCodeType.MANUFACTURER_SPECIFIC

    /**
     * Whether the MIL (Check Engine Light) is on for this DTC.
     */
    val isMilOn: Boolean
        get() = status.warningIndicatorRequested

    /**
     * Whether this DTC is currently active.
     */
    val isActive: Boolean
        get() = status.testFailed || status.testFailedThisCycle

    /**
     * Whether this DTC is pending confirmation.
     */
    val isPending: Boolean
        get() = status.pendingDTC

    /**
     * Whether this DTC is confirmed/stored.
     */
    val isConfirmed: Boolean
        get() = status.confirmedDTC

    /**
     * Whether this DTC is permanent (cannot be cleared by scan tool).
     */
    val isPermanent: Boolean
        get() = status.isPermanent

    /**
     * Whether this DTC is stored (historical).
     */
    val isStored: Boolean
        get() = status.isStored

    /**
     * Has associated freeze frame data.
     */
    val hasFreezeFrame: Boolean
        get() = freezeFrame?.hasData() == true

    /**
     * Has technical service bulletins.
     */
    val hasTSBs: Boolean
        get() = technicalBulletins.isNotEmpty()

    /**
     * Short description for compact display (max 50 chars).
     */
    val shortDescription: String
        get() = if (description.length > 50) {
            "${description.take(47)}..."
        } else {
            description
        }

    /**
     * Formatted code for display (uppercase).
     */
    val formattedCode: String
        get() = code.uppercase(Locale.US)

    /**
     * Priority for sorting (lower = more important).
     */
    val priority: Int
        get() {
            val basePriority = when {
                isPermanent && isMilOn -> 0
                isConfirmed && isMilOn -> 1
                isConfirmed -> 2
                isPending -> 3
                isStored -> 4
                else -> 5
            }
            return basePriority * 100 + severity.priority
        }

    /**
     * Category string for grouping.
     */
    val category: String
        get() = "${system.description} - ${subsystem.description}"

    /**
     * Status summary for display.
     */
    val statusSummary: String
        get() = status.getPrimaryStatus()

    // ==================== Methods ====================

    /**
     * Returns a detailed summary of this DTC.
     *
     * @return Multi-line detailed summary
     */
    fun getDetailedSummary(): String {
        return buildString {
            appendLine("DTC: $formattedCode")
            appendLine("Description: $description")
            appendLine("System: ${system.fullName}")
            appendLine("Subsystem: ${subsystem.description}")
            appendLine("Type: ${codeType.description}")
            appendLine("Status: ${status.getPrimaryStatus()}")
            appendLine("Severity: ${severity.description}")

            if (status.warningIndicatorRequested) {
                appendLine("MIL: ON")
            }

            explanation?.let {
                appendLine()
                appendLine("Explanation:")
                appendLine(it)
            }

            if (possibleCauses.isNotEmpty()) {
                appendLine()
                appendLine("Possible Causes:")
                possibleCauses.forEachIndexed { index, cause ->
                    appendLine("${index + 1}. $cause")
                }
            }

            if (symptoms.isNotEmpty()) {
                appendLine()
                appendLine("Symptoms:")
                symptoms.forEach { symptom ->
                    appendLine("• $symptom")
                }
            }

            if (diagnosticSteps.isNotEmpty()) {
                appendLine()
                appendLine("Diagnostic Steps:")
                diagnosticSteps.forEachIndexed { index, step ->
                    appendLine("${index + 1}. $step")
                }
            }

            freezeFrame?.let { ff ->
                if (ff.hasData()) {
                    appendLine()
                    appendLine("Freeze Frame Data:")
                    ff.getFormattedValues().forEach { (name, value) ->
                        appendLine("  $name: $value")
                    }
                }
            }

            estimatedRepairCost?.let {
                appendLine()
                appendLine("Estimated Repair Cost: ${it.formattedRange}")
                appendLine("Labor: ${it.formattedLabor}")
            }
        }
    }

    /**
     * Checks if this DTC matches a search query.
     *
     * Searches code, description, causes, and symptoms.
     *
     * @param query Search query string
     * @return True if query matches
     */
    fun matches(query: String): Boolean {
        if (query.isBlank()) return true

        val lowerQuery = query.lowercase(Locale.US)

        return code.lowercase(Locale.US).contains(lowerQuery) ||
                description.lowercase(Locale.US).contains(lowerQuery) ||
                explanation?.lowercase(Locale.US)?.contains(lowerQuery) == true ||
                system.description.lowercase(Locale.US).contains(lowerQuery) ||
                subsystem.description.lowercase(Locale.US).contains(lowerQuery) ||
                possibleCauses.any { it.lowercase(Locale.US).contains(lowerQuery) } ||
                symptoms.any { it.lowercase(Locale.US).contains(lowerQuery) }
    }

    /**
     * Converts to a shareable text format.
     *
     * @return Text suitable for sharing
     */
    fun toShareableText(): String {
        return buildString {
            appendLine("$formattedCode - $description")
            appendLine("Status: ${status.getPrimaryStatus()}")
            appendLine("System: ${system.description}")

            if (isMilOn) {
                appendLine("Check Engine Light: ON")
            }

            freezeFrame?.let { ff ->
                if (ff.hasData()) {
                    appendLine()
                    appendLine("Conditions when fault occurred:")
                    appendLine(ff.getSummary())
                }
            }

            appendLine()
            appendLine("Generated by SpaceTec Diagnostic Tool")
        }
    }

    /**
     * Converts DTC code to raw bytes.
     *
     * @return 2-byte array representing the DTC
     */
    fun toBytes(): ByteArray {
        return DTCUtils.encodeDTC(code)
    }

    override fun compareTo(other: DTC): Int {
        return this.priority.compareTo(other.priority)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DTC

        if (code != other.code) return false
        if (ecuAddress != other.ecuAddress) return false
        if (status.isPermanent != other.status.isPermanent) return false
        if (status.isStored != other.status.isStored) return false
        if (status.pendingDTC != other.status.pendingDTC) return false

        return true
    }

    override fun hashCode(): Int {
        var result = code.hashCode()
        result = 31 * result + (ecuAddress ?: 0)
        result = 31 * result + status.isPermanent.hashCode()
        result = 31 * result + status.isStored.hashCode()
        result = 31 * result + status.pendingDTC.hashCode()
        return result
    }

    override fun toString(): String {
        return "DTC($formattedCode: $shortDescription [${status.getPrimaryStatus()}])"
    }

    // ==================== Companion Object ====================

    companion object {
        private const val serialVersionUID = 1L

        /**
         * Parses DTC from raw bytes.
         *
         * @param bytes 2-byte DTC data
         * @param statusByte Optional status byte
         * @return Parsed DTC
         */
        fun fromBytes(bytes: ByteArray, statusByte: Byte? = null): DTC {
            require(bytes.size >= 2) { "DTC requires at least 2 bytes" }

            val byte1 = bytes[0].toInt() and 0xFF
            val byte2 = bytes[1].toInt() and 0xFF

            val code = DTCUtils.decodeDTC(byte1, byte2)
            return fromCode(code).let { dtc ->
                if (statusByte != null) {
                    dtc.copy(
                        status = DTCStatus.fromByte(statusByte),
                        rawBytes = bytes.copyOf()
                    )
                } else {
                    dtc.copy(rawBytes = bytes.copyOf())
                }
            }
        }

        /**
         * Creates a DTC from a code string.
         *
         * @param code DTC code (e.g., "P0420")
         * @param description Optional description
         * @return DTC instance
         */
        fun fromCode(code: String, description: String? = null): DTC {
            val normalizedCode = DTCUtils.normalizeDTCCode(code)
            val system = DTCSystem.fromCode(normalizedCode) ?: DTCSystem.POWERTRAIN
            val codeType = DTCCodeType.fromCode(normalizedCode)
            val subsystem = DTCSubsystem.fromCode(normalizedCode)
            val severity = DTCSeverity.fromCode(normalizedCode)

            return DTC(
                code = normalizedCode,
                description = description ?: getDefaultDescription(normalizedCode),
                system = system,
                subsystem = subsystem,
                codeType = codeType,
                severity = severity
            )
        }

        /**
         * Validates DTC code format.
         *
         * @param code Code to validate
         * @return True if valid format
         */
        fun isValidCode(code: String): Boolean {
            return DTCUtils.isValidDTCCode(code)
        }

        /**
         * Parses DTC system from code.
         *
         * @param code DTC code
         * @return DTCSystem or null
         */
        fun parseSystem(code: String): DTCSystem? {
            return DTCSystem.fromCode(code)
        }

        /**
         * Parses DTC code type.
         *
         * @param code DTC code
         * @return DTCCodeType
         */
        fun parseCodeType(code: String): DTCCodeType {
            return DTCCodeType.fromCode(code)
        }

        /**
         * Parses DTC subsystem.
         *
         * @param code DTC code
         * @return DTCSubsystem
         */
        fun parseSubsystem(code: String): DTCSubsystem {
            return DTCSubsystem.fromCode(code)
        }

        /**
         * Gets a default description for unknown codes.
         */
        private fun getDefaultDescription(code: String): String {
            val system = DTCSystem.fromCode(code)
            val codeType = DTCCodeType.fromCode(code)
            val subsystem = DTCSubsystem.fromCode(code)

            return buildString {
                append(system?.description ?: "Unknown")
                append(" - ")
                append(subsystem.description)
                if (codeType == DTCCodeType.MANUFACTURER_SPECIFIC) {
                    append(" (Manufacturer Specific)")
                }
            }
        }
    }
}

/**
 * DTC type enumeration for categorizing how the DTC was retrieved.
 */
enum class DTCType {
    /** Stored/confirmed DTC (Service 03) */
    STORED,

    /** Pending DTC (Service 07) */
    PENDING,

    /** Permanent DTC (Service 0A) */
    PERMANENT
}

/**
 * Utility object for DTC parsing and formatting.
 *
 * Provides methods for encoding, decoding, and validating DTC codes
 * according to SAE J2012 standards.
 *
 * @author SpaceTec Team
 * @since 1.0.0
 */
object DTCUtils {

    /** Valid DTC code pattern */
    val DTC_PATTERN = Regex("^[PCBU][0-3][0-9A-Fa-f]{3}$")

    /** System prefix byte mapping */
    private val SYSTEM_PREFIX_MAP = mapOf(
        0 to 'P',
        1 to 'C',
        2 to 'B',
        3 to 'U'
    )

    /** Reverse system prefix mapping */
    private val PREFIX_SYSTEM_MAP = mapOf(
        'P' to 0,
        'C' to 1,
        'B' to 2,
        'U' to 3
    )

    /**
     * Decodes DTC from two raw bytes.
     *
     * The DTC is encoded as follows:
     * - Byte 1, bits 7-6: System (00=P, 01=C, 10=B, 11=U)
     * - Byte 1, bits 5-4: Second digit (0-3)
     * - Byte 1, bits 3-0: Third digit (0-F)
     * - Byte 2, bits 7-4: Fourth digit (0-F)
     * - Byte 2, bits 3-0: Fifth digit (0-F)
     *
     * @param byte1 First byte
     * @param byte2 Second byte
     * @return DTC code string (e.g., "P0420")
     */
    fun decodeDTC(byte1: Int, byte2: Int): String {
        val b1 = byte1 and 0xFF
        val b2 = byte2 and 0xFF

        // Extract system from high nibble of first byte
        val systemCode = (b1 shr 6) and 0x03
        val systemChar = SYSTEM_PREFIX_MAP[systemCode] ?: 'P'

        // Extract second digit (bits 5-4)
        val digit2 = (b1 shr 4) and 0x03

        // Extract third digit (bits 3-0)
        val digit3 = b1 and 0x0F

        // Extract fourth digit (high nibble of byte 2)
        val digit4 = (b2 shr 4) and 0x0F

        // Extract fifth digit (low nibble of byte 2)
        val digit5 = b2 and 0x0F

        return String.format(
            Locale.US,
            "%c%d%X%X%X",
            systemChar,
            digit2,
            digit3,
            digit4,
            digit5
        )
    }

    /**
     * Encodes DTC code to raw bytes.
     *
     * @param code DTC code string (e.g., "P0420")
     * @return 2-byte array
     * @throws IllegalArgumentException if code format is invalid
     */
    fun encodeDTC(code: String): ByteArray {
        val normalized = normalizeDTCCode(code)
        require(isValidDTCCode(normalized)) { "Invalid DTC code: $code" }

        val systemChar = normalized[0].uppercaseChar()
        val systemCode = PREFIX_SYSTEM_MAP[systemChar] ?: 0

        val digit2 = normalized[1].digitToIntOrNull() ?: 0
        val digit3 = normalized[2].digitToIntOrNull(16) ?: 0
        val digit4 = normalized[3].digitToIntOrNull(16) ?: 0
        val digit5 = normalized[4].digitToIntOrNull(16) ?: 0

        val byte1 = ((systemCode and 0x03) shl 6) or
                ((digit2 and 0x03) shl 4) or
                (digit3 and 0x0F)

        val byte2 = ((digit4 and 0x0F) shl 4) or
                (digit5 and 0x0F)

        return byteArrayOf(byte1.toByte(), byte2.toByte())
    }

    /**
     * Validates DTC code format.
     *
     * @param code Code to validate
     * @return True if format is valid
     */
    fun isValidDTCCode(code: String): Boolean {
        return DTC_PATTERN.matches(code.trim())
    }

    /**
     * Normalizes DTC code to standard format.
     *
     * - Trims whitespace
     * - Converts to uppercase
     * - Validates format
     *
     * @param code Raw code string
     * @return Normalized code
     */
    fun normalizeDTCCode(code: String): String {
        return code.trim().uppercase(Locale.US)
    }

    /**
     * Parses multiple DTCs from OBD response data.
     *
     * @param data Response bytes (excluding service byte)
     * @param type DTC type (stored, pending, permanent)
     * @return List of parsed DTCs
     */
    fun parseDTCsFromResponse(data: ByteArray, type: DTCType = DTCType.STORED): List<DTC> {
        val dtcs = mutableListOf<DTC>()

        var offset = 0
        while (offset + 1 < data.size) {
            val byte1 = data[offset].toInt() and 0xFF
            val byte2 = data[offset + 1].toInt() and 0xFF

            // Skip null DTCs (0x0000)
            if (byte1 == 0 && byte2 == 0) {
                offset += 2
                continue
            }

            val code = decodeDTC(byte1, byte2)
            val status = when (type) {
                DTCType.STORED -> DTCStatus.stored()
                DTCType.PENDING -> DTCStatus.pending()
                DTCType.PERMANENT -> DTCStatus.permanent()
            }

            val dtc = DTC.fromCode(code).copy(
                status = status,
                rawBytes = byteArrayOf(data[offset], data[offset + 1])
            )
            dtcs.add(dtc)

            offset += 2
        }

        return dtcs
    }

    /**
     * Gets DTCSystem from first nibble of first byte.
     *
     * @param byte1 First DTC byte
     * @return DTCSystem
     */
    fun getSystemFromByte(byte1: Int): DTCSystem {
        val systemCode = (byte1 shr 6) and 0x03
        return when (systemCode) {
            0 -> DTCSystem.POWERTRAIN
            1 -> DTCSystem.CHASSIS
            2 -> DTCSystem.BODY
            3 -> DTCSystem.NETWORK
            else -> DTCSystem.POWERTRAIN
        }
    }

    /**
     * Gets DTCCodeType from second digit.
     *
     * @param byte1 First DTC byte
     * @return DTCCodeType
     */
    fun getCodeTypeFromByte(byte1: Int): DTCCodeType {
        val typeDigit = (byte1 shr 4) and 0x03
        return when (typeDigit) {
            0 -> DTCCodeType.GENERIC
            1 -> DTCCodeType.MANUFACTURER_SPECIFIC
            2 -> DTCCodeType.GENERIC_EXTENDED
            3 -> DTCCodeType.RESERVED
            else -> DTCCodeType.GENERIC
        }
    }

    /**
     * Formats a list of DTCs for display.
     *
     * @param dtcs List of DTCs
     * @param separator Separator between codes
     * @return Formatted string
     */
    fun formatDTCList(dtcs: List<DTC>, separator: String = ", "): String {
        return dtcs.joinToString(separator) { it.formattedCode }
    }

    /**
     * Groups DTCs by system.
     *
     * @param dtcs List of DTCs
     * @return Map of system to DTCs
     */
    fun groupBySystem(dtcs: List<DTC>): Map<DTCSystem, List<DTC>> {
        return dtcs.groupBy { it.system }
    }

    /**
     * Groups DTCs by status type.
     *
     * @param dtcs List of DTCs
     * @return Map of status type to DTCs
     */
    fun groupByStatus(dtcs: List<DTC>): Map<String, List<DTC>> {
        return dtcs.groupBy { it.status.getPrimaryStatus() }
    }

    /**
     * Sorts DTCs by priority.
     *
     * @param dtcs List of DTCs
     * @return Sorted list
     */
    fun sortByPriority(dtcs: List<DTC>): List<DTC> {
        return dtcs.sorted()
    }

    /**
     * Filters DTCs with MIL on.
     *
     * @param dtcs List of DTCs
     * @return DTCs with MIL active
     */
    fun filterMilOn(dtcs: List<DTC>): List<DTC> {
        return dtcs.filter { it.isMilOn }
    }

    /**
     * Gets unique DTCs across multiple lists.
     *
     * @param stored Stored DTCs
     * @param pending Pending DTCs
     * @param permanent Permanent DTCs
     * @return Unique DTCs
     */
    fun getUniqueDTCs(
        stored: List<DTC>,
        pending: List<DTC>,
        permanent: List<DTC>
    ): List<DTC> {
        return (stored + pending + permanent)
            .distinctBy { it.code }
            .sortedBy { it.priority }
    }
}

// ==================== Extension Functions ====================

/**
 * Converts ByteArray to hex string for debugging.
 */
fun ByteArray.toHexString(): String {
    return joinToString("") { String.format("%02X", it) }
}

/**
 * Filters DTCs by system.
 */
fun List<DTC>.filterBySystem(system: DTCSystem): List<DTC> {
    return filter { it.system == system }
}

/**
 * Filters DTCs by severity.
 */
fun List<DTC>.filterBySeverity(minSeverity: DTCSeverity): List<DTC> {
    return filter { it.severity.level >= minSeverity.level }
}

/**
 * Gets only active DTCs.
 */
fun List<DTC>.filterActive(): List<DTC> {
    return filter { it.isActive }
}

/**
 * Gets only confirmed DTCs.
 */
fun List<DTC>.filterConfirmed(): List<DTC> {
    return filter { it.isConfirmed }
}

/**
 * Gets only pending DTCs.
 */
fun List<DTC>.filterPending(): List<DTC> {
    return filter { it.isPending }
}

/**
 * Gets only permanent DTCs.
 */
fun List<DTC>.filterPermanent(): List<DTC> {
    return filter { it.isPermanent }
}

/**
 * Checks if any DTC has MIL on.
 */
fun List<DTC>.hasActiveMil(): Boolean {
    return any { it.isMilOn }
}

/**
 * Gets the most severe DTC.
 */
fun List<DTC>.mostSevere(): DTC? {
    return maxByOrNull { it.severity.level }
}

/**
 * Formats as summary text.
 */
fun List<DTC>.toSummary(): String {
    if (isEmpty()) return "No DTCs found"

    val byStatus = groupBy { it.status.getPrimaryStatus() }
    return buildString {
        append("${size} DTC(s) found: ")
        byStatus.entries.joinTo(this) { (status, codes) ->
            "${codes.size} $status"
        }
    }
}
