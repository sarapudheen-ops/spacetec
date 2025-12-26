package com.obdreader.domain.model

/**
 * Represents a Diagnostic Trouble Code from the vehicle.
 */
data class DTC(
    val code: String,
    val category: DTCCategory,
    val type: DTCType,
    val status: DTCStatus,
    val rawBytes: ByteArray,
    val ecuAddress: String? = null,
    val description: String? = null,
    val possibleCauses: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Get the system code (second character).
     */
    fun getSystemCode(): Int {
        return code.getOrNull(1)?.digitToIntOrNull() ?: 0
    }
    
    /**
     * Get description of the system this DTC relates to.
     */
    fun getSystemDescription(): String {
        return when (category) {
            DTCCategory.POWERTRAIN -> when (getSystemCode()) {
                0, 1, 2 -> "Fuel/Air Metering"
                3 -> "Ignition System/Misfire"
                4 -> "Auxiliary Emission"
                5 -> "Vehicle Speed/Idle Control"
                6 -> "Computer/Output Circuit"
                7, 8 -> "Transmission"
                else -> "Unknown"
            }
            DTCCategory.CHASSIS -> "Chassis System"
            DTCCategory.BODY -> "Body System"
            DTCCategory.NETWORK -> "Network/Communication"
        }
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as DTC
        return code == other.code && status == other.status
    }
    
    override fun hashCode(): Int {
        var result = code.hashCode()
        result = 31 * result + status.hashCode()
        return result
    }
}

/**
 * DTC category based on first character.
 */
enum class DTCCategory(val prefix: Char, val description: String) {
    POWERTRAIN('P', "Powertrain"),
    CHASSIS('C', "Chassis"),
    BODY('B', "Body"),
    NETWORK('U', "Network/Communication")
}

/**
 * DTC type - generic (standard) or manufacturer-specific.
 */
enum class DTCType {
    GENERIC,
    MANUFACTURER
}

/**
 * DTC status indicating where it came from.
 */
enum class DTCStatus {
    STORED,      // Mode 03 - confirmed, MIL on
    PENDING,     // Mode 07 - not yet confirmed
    PERMANENT    // Mode 0A - cannot be cleared by scan tool
}
