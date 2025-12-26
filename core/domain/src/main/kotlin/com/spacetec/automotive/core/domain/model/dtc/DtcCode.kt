// core/domain/src/main/kotlin/com/spacetec/automotive/core/domain/model/dtc/DtcCode.kt
package com.spacetec.obd.core.domain.model.dtc

/**
 * Represents a Diagnostic Trouble Code (DTC) read from a vehicle.
 *
 * @property code The DTC code string (e.g., "P0301")
 * @property rawBytes The raw bytes received from the ECU
 * @property ecuAddress Address of the ECU that reported the code
 * @property ecuName Name of the ECU (if available)
 * @property status Current status of the DTC
 * @property system The system the DTC belongs to
 * @property severity The severity level
 * @property milStatus Whether the MIL (Check Engine Light) is on
 * @property freezeFrameAvailable Whether freeze frame data is available
 * @property occurrenceCount Number of times this DTC has occurred
 * @property firstOccurrence Timestamp of first occurrence
 * @property lastOccurrence Timestamp of last occurrence
 * @property timestamp When the DTC was read
 * @property isCleared Whether the DTC has been cleared
 * @property clearedAt When the DTC was cleared (if cleared)
 * @property notes User notes about this DTC
 */
data class DtcCode(
    val code: String,
    val rawBytes: ByteArray = byteArrayOf(),
    val ecuAddress: String = "",
    val ecuName: String = "",
    val status: DtcStatus = DtcStatus.UNKNOWN,
    val system: DtcSystem = DtcSystem.POWERTRAIN,
    val severity: DtcSeverity = DtcSeverity.INFO,
    val milStatus: Boolean = false,
    val freezeFrameAvailable: Boolean = false,
    val occurrenceCount: Int = 1,
    val firstOccurrence: Long = System.currentTimeMillis(),
    val lastOccurrence: Long = System.currentTimeMillis(),
    val timestamp: Long = System.currentTimeMillis(),
    val isCleared: Boolean = false,
    val clearedAt: Long? = null,
    val notes: String = ""
) {
    /**
     * Returns true if this DTC is currently active.
     */
    val isActive: Boolean
        get() = !isCleared && status in listOf(DtcStatus.ACTIVE, DtcStatus.CONFIRMED, DtcStatus.PENDING)

    /**
     * Returns the age of this DTC in milliseconds.
     */
    val age: Long
        get() = System.currentTimeMillis() - timestamp

    /**
     * Returns true if this DTC is emissions-related.
     */
    val isEmissionsRelated: Boolean
        get() = code.startsWith("P0") || code.startsWith("P1") || code.startsWith("P2")

    /**
     * Returns the DTC category based on the code.
     */
    val category: DtcCategory
        get() = DtcCategory.fromDtcCode(code)
}