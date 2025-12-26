package com.obdreader.data.obd.parser

/**
 * Definition of an OBD-II PID including parsing formula.
 */
data class PIDDefinition(
    val pid: Int,
    val name: String,
    val shortName: String,
    val description: String,
    val bytes: Int,
    val formula: (ByteArray) -> Double,
    val unit: String,
    val minValue: Double,
    val maxValue: Double,
    val category: PIDCategory = PIDCategory.OTHER
)

/**
 * Categories for organizing PIDs.
 */
enum class PIDCategory {
    ENGINE,
    FUEL,
    TEMPERATURE,
    SPEED,
    OXYGEN_SENSOR,
    EMISSION,
    VEHICLE_INFO,
    DIAGNOSTIC,
    OTHER
}

/**
 * Polling priority for PIDs.
 */
enum class PollingPriority(val intervalMs: Long) {
    CRITICAL(200),     // RPM, Speed
    HIGH(500),         // Coolant, Load
    STANDARD(1000),    // Fuel, Throttle
    LOW(2000),         // Temps
    BACKGROUND(0)      // On-demand only
}
