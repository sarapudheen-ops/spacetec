package com.spacetec.obd.core.domain.model.dtc

import kotlinx.serialization.Serializable

/**
 * Severity levels for DTCs.
 */
@Serializable
enum class DtcSeverity(val displayName: String) {
    /**
     * Informational DTCs that don't indicate a problem.
     */
    INFO("Informational"),

    /**
     * Low severity - may not cause noticeable symptoms.
     */
    LOW("Low"),

    /**
     * Minor severity - may cause minor performance issues.
     */
    MINOR("Minor"),

    /**
     * Medium severity - may cause moderate performance issues.
     */
    MEDIUM("Medium"),

    /**
     * Major severity - causes noticeable performance issues.
     */
    MAJOR("Major"),

    /**
     * High severity - causes significant performance issues.
     */
    HIGH("High"),

    /**
     * Critical severity - immediate attention required.
     */
    CRITICAL("Critical")
}

/**
 * System classification for DTC codes.
 */
@Serializable
enum class DtcSystem(val prefix: String, val description: String, val displayName: String) {
    POWERTRAIN("P", "Powertrain (Engine and Transmission)", "Powertrain"),
    BODY("B", "Body (Interior components)", "Body"),
    CHASSIS("C", "Chassis (Brakes, Suspension)", "Chassis"),
    NETWORK("U", "Network (Communication)", "Network");

    companion object {
        /**
         * Determines the system from a DTC code.
         */
        fun fromCode(code: String): DtcSystem {
            return when (code.getOrNull(0)) {
                'P' -> POWERTRAIN
                'B' -> BODY
                'C' -> CHASSIS
                'U' -> NETWORK
                else -> POWERTRAIN // Default to powertrain for unknown codes
            }
        }
    }
}

/**
 * Status of a DTC.
 */
@Serializable
enum class DtcStatus {
    /**
     * DTC is currently active and confirmed.
     */
    ACTIVE,

    /**
     * DTC is currently active but pending confirmation.
     */
    PENDING,

    /**
     * DTC is confirmed but no longer active.
     */
    CONFIRMED,

    /**
     * DTC has been cleared.
     */
    INACTIVE,

    /**
     * DTC has been historically recorded but is no longer active.
     */
    HISTORICAL,

    /**
     * DTC is permanent and cannot be cleared.
     */
    PERMANENT,

    /**
     * Status is unknown.
     */
    UNKNOWN
}

/**
 * Functional categories for DTCs.
 */
@Serializable
enum class DtcCategory(val displayName: String, val description: String) {
    FUEL_SYSTEM("Fuel System", "Fuel injection, fuel pump, fuel pressure"),
    IGNITION("Ignition", "Spark plugs, ignition coils, timing"),
    EMISSIONS("Emissions", "Catalytic converter, O2 sensors, EGR, EVAP"),
    ENGINE_MECHANICAL("Engine Mechanical", "Valves, pistons, timing chain"),
    TRANSMISSION("Transmission", "Transmission control, shifting, torque converter"),
    DRIVETRAIN("Drivetrain", "Differential, transfer case, axles"),
    ELECTRICAL("Electrical", "Wiring, grounds, power supply"),
    SENSORS("Sensors", "Various engine and vehicle sensors"),
    ACTUATORS("Actuators", "Solenoids, motors, valves"),
    COMMUNICATION("Communication", "CAN bus, module communication"),
    BODY_CONTROL("Body Control", "Lighting, wipers, door locks"),
    CLIMATE_CONTROL("Climate Control", "A/C, heating, ventilation"),
    AIRBAG_SRS("Airbag/SRS", "Airbags, seatbelt pretensioners"),
    ABS_TRACTION("ABS/Traction", "Antilock brakes, stability control"),
    STEERING("Steering", "Power steering, electric steering"),
    SUSPENSION("Suspension", "Air suspension, active damping"),
    INSTRUMENT("Instrument", "Gauges, displays, warning lights"),
    INFOTAINMENT("Infotainment", "Radio, navigation, Bluetooth"),
    SECURITY("Security", "Immobilizer, alarm, keyless entry"),
    OTHER("Other", "Miscellaneous or unclassified");

    companion object {
        /**
         * Attempts to determine category from DTC code pattern.
         */
        fun fromDtcCode(code: String): DtcCategory {
            if (code.length < 3) return OTHER

            val system = DtcSystem.fromCode(code)
            val subsystem = code.getOrNull(2)?.digitToIntOrNull() ?: return OTHER

            return when (system) {
                DtcSystem.POWERTRAIN -> when (subsystem) {
                    0 -> FUEL_SYSTEM
                    1 -> FUEL_SYSTEM
                    2 -> FUEL_SYSTEM
                    3 -> IGNITION
                    4 -> EMISSIONS
                    5 -> SENSORS
                    6 -> ELECTRICAL
                    7, 8, 9 -> TRANSMISSION
                    else -> OTHER
                }
                DtcSystem.BODY -> when (subsystem) {
                    0, 1 -> BODY_CONTROL
                    2, 3 -> CLIMATE_CONTROL
                    4 -> INSTRUMENT
                    5 -> INFOTAINMENT
                    else -> OTHER
                }
                DtcSystem.CHASSIS -> when (subsystem) {
                    0, 1, 2 -> ABS_TRACTION
                    3, 4 -> STEERING
                    5, 6 -> SUSPENSION
                    else -> OTHER
                }
                DtcSystem.NETWORK -> COMMUNICATION
            }
        }
    }
}