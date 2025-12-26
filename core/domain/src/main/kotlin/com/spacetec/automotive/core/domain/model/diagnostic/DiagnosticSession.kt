// core/domain/src/main/kotlin/com/spacetec/automotive/core/domain/model/diagnostic/DiagnosticSession.kt
package com.spacetec.obd.core.domain.model.diagnostic

/**
 * Represents a diagnostic session with a vehicle.
 * 
 * @property id Unique session identifier
 * @property vehicleId ID of the vehicle being diagnosed
 * @property sessionType Type of diagnostic session
 * @property protocol Communication protocol used
 * @property startTime When the session started
 * @property endTime When the session ended
 * @property durationMs Duration of the session in milliseconds
 * @property connectionAttempts Number of connection attempts
 * @property connectionSuccess Whether the connection was successful
 * @property ecuCount Number of ECUs detected
 * @property dtcCount Total number of DTCs found
 * @property dtcsClearedCount Number of DTCs cleared in this session
 * @property dtcsFoundCount Number of new DTCs found
 * @property liveDataParamsCount Number of live data parameters read
 * @property freezeFramesFound Number of freeze frames found
 * @property errorCount Number of errors encountered
 * @property errors List of error messages
 * @property scannerInfo Information about the scanner used
 * @property connectionQuality Quality of the connection (0.0 to 1.0)
 * @property signalStrength Signal strength in dBm or percentage
 * @property notes User notes about the session
 */
data class DiagnosticSession(
    val id: String,
    val vehicleId: Long,
    val sessionType: String,
    val protocol: String,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val durationMs: Long? = null,
    val connectionAttempts: Int = 0,
    val connectionSuccess: Boolean = false,
    val ecuCount: Int = 0,
    val dtcCount: Int = 0,
    val dtcsClearedCount: Int = 0,
    val dtcsFoundCount: Int = 0,
    val liveDataParamsCount: Int = 0,
    val freezeFramesFound: Int = 0,
    val errorCount: Int = 0,
    val errors: List<String> = emptyList(),
    val scannerInfo: String = "",
    val connectionQuality: Float = 0f,
    val signalStrength: Int = 0,
    val notes: String = ""
) {
    /**
     * Calculates the duration if end time is available.
     */
    val calculatedDurationMs: Long?
        get() = if (endTime != null) endTime - startTime else null
    
    /**
     * Whether the session is still active.
     */
    val isActive: Boolean
        get() = endTime == null
    
    /**
     * Success rate of the session.
     */
    val successRate: Float
        get() = if (connectionAttempts > 0) {
            (if (connectionSuccess) 1 else 0).toFloat() / connectionAttempts
        } else 0f
    
    /**
     * Average time per DTC read.
     */
    val avgTimePerDtc: Float?
        get() = if (dtcCount > 0 && durationMs != null) {
            durationMs.toFloat() / dtcCount
        } else null
}