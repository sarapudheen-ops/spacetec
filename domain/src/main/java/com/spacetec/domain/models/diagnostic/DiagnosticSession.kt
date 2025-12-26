/**
 * File: DiagnosticSession.kt
 * 
 * Domain model representing a diagnostic session with a vehicle in the
 * SpaceTec automotive diagnostic application. A session captures all
 * diagnostic activities performed during a single connection, including
 * DTCs, ECU scans, freeze frames, and live data recordings.
 * 
 * Structure:
 * 1. Package declaration and imports
 * 2. DiagnosticSession data class with full documentation
 * 3. DiagnosticSessionType enum
 * 4. SessionStatus enum
 * 5. Supporting data classes (LiveDataSnapshot, MonitorStatus, SessionMetadata)
 * 6. MileageUnit enum
 * 7. Extension functions and utilities
 * 
 * @author SpaceTec Development Team
 * @since 1.0.0
 */
package com.spacetec.domain.models.diagnostic

import com.spacetec.domain.models.ecu.ECU
import com.spacetec.domain.models.livedata.LiveDataValue
import com.spacetec.obd.core.domain.models.scanner.ProtocolType
import java.io.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Represents a diagnostic session with a vehicle.
 * 
 * A diagnostic session is a complete record of all diagnostic activities
 * performed during a single connection to a vehicle. It tracks:
 * 
 * - **Session Lifecycle**: Start time, end time, duration, status
 * - **Vehicle Information**: VIN, mileage, protocol used
 * - **Diagnostic Results**: DTCs found, ECUs scanned, freeze frames
 * - **Live Data**: Recorded sensor values and snapshots
 * - **Actions Taken**: Whether DTCs were cleared, notes added
 * 
 * ## Session Types
 * 
 * Different session types support different diagnostic workflows:
 * 
 * | Type | Description | Typical Duration |
 * |------|-------------|------------------|
 * | QUICK_SCAN | Read DTCs and basic info | 1-2 minutes |
 * | FULL_DIAGNOSTIC | Complete vehicle scan | 5-15 minutes |
 * | LIVE_DATA | Live data monitoring | Variable |
 * | CODING | ECU coding/configuration | Variable |
 * 
 * ## Usage Example
 * 
 * ```kotlin
 * // Start a new session
 * val session = DiagnosticSession.quickScan(
 *     vehicleId = "vehicle_123",
 *     vin = "1HGBH41JXMN109186"
 * )
 * 
 * // Add DTCs found
 * val updatedSession = session.withDTCs(dtcList)
 * 
 * // Complete the session
 * val completedSession = updatedSession.complete()
 * 
 * println("Session duration: ${completedSession.durationFormatted}")
 * println("DTCs found: ${completedSession.totalDTCCount}")
 * ```
 * 
 * ## Thread Safety
 * 
 * This class is immutable and thread-safe. All modification methods
 * return new instances rather than modifying the existing object.
 * 
 * @property id Unique session identifier (UUID)
 * @property vehicleId Vehicle identifier (VIN or internal ID)
 * @property vin Vehicle Identification Number (if known)
 * @property type Session type determining the diagnostic workflow
 * @property status Current session status
 * @property startTime Session start timestamp (milliseconds since epoch)
 * @property endTime Session end timestamp (null if in progress)
 * @property protocol Protocol used for communication
 * @property scannerId Scanner/adapter identifier used
 * @property scannerName Scanner/adapter name for display
 * @property mileage Vehicle mileage at time of session
 * @property mileageUnit Unit of mileage measurement
 * @property ecus ECUs discovered during session
 * @property dtcs DTCs found during session
 * @property freezeFrames Freeze frames captured
 * @property liveDataSnapshots Live data snapshots recorded
 * @property monitorStatus OBD monitor status at session time
 * @property notes User notes added to session
 * @property tags Tags for categorization and filtering
 * @property dtcsCleared Whether DTCs were cleared in this session
 * @property dtcsClearedAt Timestamp when DTCs were cleared
 * @property metadata Additional session metadata
 * 
 * @see DiagnosticSessionType
 * @see SessionStatus
 * @see DTC
 * @see ECU
 */
data class DiagnosticSession(
    val id: String = UUID.randomUUID().toString(),
    val vehicleId: String,
    val vin: String? = null,
    val type: DiagnosticSessionType,
    val status: SessionStatus = SessionStatus.IN_PROGRESS,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val protocol: ProtocolType? = null,
    val scannerId: String? = null,
    val scannerName: String? = null,
    val mileage: Int? = null,
    val mileageUnit: MileageUnit = MileageUnit.KILOMETERS,
    val ecus: List<ECU> = emptyList(),
    val dtcs: List<DTC> = emptyList(),
    val freezeFrames: List<FreezeFrame> = emptyList(),
    val liveDataSnapshots: List<LiveDataSnapshot> = emptyList(),
    val monitorStatus: MonitorStatus? = null,
    val notes: String? = null,
    val tags: List<String> = emptyList(),
    val dtcsCleared: Boolean = false,
    val dtcsClearedAt: Long? = null,
    val metadata: SessionMetadata = SessionMetadata(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) : Serializable {

    // ==================== Computed Properties - Duration ====================

    /**
     * Session duration in milliseconds.
     * 
     * For active sessions, calculates duration from start to now.
     * For completed sessions, calculates duration from start to end.
     */
    val durationMs: Long
        get() = (endTime ?: System.currentTimeMillis()) - startTime

    /**
     * Session duration in seconds.
     */
    val durationSeconds: Long
        get() = TimeUnit.MILLISECONDS.toSeconds(durationMs)

    /**
     * Session duration in minutes.
     */
    val durationMinutes: Long
        get() = TimeUnit.MILLISECONDS.toMinutes(durationMs)

    /**
     * Session duration formatted as human-readable string.
     * 
     * Format examples:
     * - "45s" for sessions under 1 minute
     * - "5m 30s" for sessions under 1 hour
     * - "1h 15m" for sessions 1 hour or longer
     */
    val durationFormatted: String
        get() {
            val totalSeconds = durationSeconds
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            
            return when {
                hours > 0 -> "${hours}h ${minutes}m"
                minutes > 0 -> "${minutes}m ${seconds}s"
                else -> "${seconds}s"
            }
        }

    /**
     * Detailed duration format including all components.
     * 
     * Format: "HH:MM:SS"
     */
    val durationDetailed: String
        get() {
            val totalSeconds = durationSeconds
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        }

    // ==================== Computed Properties - Status ====================

    /**
     * Whether the session is complete.
     */
    val isComplete: Boolean
        get() = status == SessionStatus.COMPLETED

    /**
     * Whether the session is currently active/in progress.
     */
    val isActive: Boolean
        get() = status == SessionStatus.IN_PROGRESS

    /**
     * Whether the session failed.
     */
    val isFailed: Boolean
        get() = status == SessionStatus.FAILED

    /**
     * Whether the session was cancelled.
     */
    val isCancelled: Boolean
        get() = status == SessionStatus.CANCELLED

    /**
     * Whether the session has ended (completed, failed, or cancelled).
     */
    val hasEnded: Boolean
        get() = status != SessionStatus.IN_PROGRESS

    // ==================== Computed Properties - DTC Statistics ====================

    /**
     * Total number of DTCs found.
     */
    val totalDTCCount: Int
        get() = dtcs.size

    /**
     * Number of stored/confirmed DTCs.
     */
    val storedDTCCount: Int
        get() = dtcs.count { it.isStored }

    /**
     * Number of pending DTCs.
     */
    val pendingDTCCount: Int
        get() = dtcs.count { it.isPending }

    /**
     * Number of permanent DTCs.
     */
    val permanentDTCCount: Int
        get() = dtcs.count { it.isPermanent }

    /**
     * Whether any DTCs were found.
     */
    val hasDTCs: Boolean
        get() = dtcs.isNotEmpty()

    /**
     * Whether any critical DTCs are present.
     */
    val hasCriticalDTCs: Boolean
        get() = dtcs.any { it.severity == DTCSeverity.CRITICAL }

    /**
     * Whether MIL (check engine light) is on.
     */
    val isMILOn: Boolean
        get() = monitorStatus?.milOn ?: dtcs.any { it.isMilOn }

    /**
     * DTCs grouped by system.
     */
    val dtcsBySystem: Map<DTCSystem, List<DTC>>
        get() = dtcs.groupBy { it.system }

    /**
     * DTCs grouped by code type.
     */
    val dtcsByCodeType: Map<DTCCodeType, List<DTC>>
        get() = dtcs.groupBy { it.codeType }

    // ==================== Computed Properties - ECU Statistics ====================

    /**
     * Number of ECUs discovered.
     */
    val ecuCount: Int
        get() = ecus.size

    /**
     * Whether any ECUs were discovered.
     */
    val hasECUs: Boolean
        get() = ecus.isNotEmpty()

    /**
     * ECUs with DTCs.
     */
    val ecusWithDTCs: List<ECU>
        get() = ecus.filter { it.hasDTCs }

    /**
     * Number of ECUs with DTCs.
     */
    val ecusWithDTCsCount: Int
        get() = ecusWithDTCs.size

    // ==================== Computed Properties - Live Data ====================

    /**
     * Whether live data was recorded.
     */
    val hasLiveData: Boolean
        get() = liveDataSnapshots.isNotEmpty()

    /**
     * Number of live data snapshots.
     */
    val liveDataSnapshotCount: Int
        get() = liveDataSnapshots.size

    /**
     * Total live data recording duration in milliseconds.
     */
    val liveDataDurationMs: Long
        get() {
            if (liveDataSnapshots.size < 2) return 0
            val first = liveDataSnapshots.first().timestamp
            val last = liveDataSnapshots.last().timestamp
            return last - first
        }

    // ==================== Computed Properties - Freeze Frames ====================

    /**
     * Whether freeze frames were captured.
     */
    val hasFreezeFrames: Boolean
        get() = freezeFrames.isNotEmpty()

    /**
     * Number of freeze frames captured.
     */
    val freezeFrameCount: Int
        get() = freezeFrames.size

    // ==================== Computed Properties - Formatting ====================

    /**
     * Session date formatted for display.
     * 
     * Format: "yyyy-MM-dd HH:mm"
     */
    val dateFormatted: String
        get() = DATE_FORMAT.format(Date(startTime))

    /**
     * Session date in short format.
     * 
     * Format: "MM/dd/yyyy"
     */
    val dateShort: String
        get() = SHORT_DATE_FORMAT.format(Date(startTime))

    /**
     * Session time formatted for display.
     * 
     * Format: "HH:mm:ss"
     */
    val timeFormatted: String
        get() = TIME_FORMAT.format(Date(startTime))

    /**
     * Relative time description (e.g., "2 hours ago", "Yesterday").
     */
    val relativeTime: String
        get() {
            val now = System.currentTimeMillis()
            val diff = now - startTime
            
            return when {
                diff < TimeUnit.MINUTES.toMillis(1) -> "Just now"
                diff < TimeUnit.HOURS.toMillis(1) -> {
                    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
                    "$minutes minute${if (minutes > 1) "s" else ""} ago"
                }
                diff < TimeUnit.DAYS.toMillis(1) -> {
                    val hours = TimeUnit.MILLISECONDS.toHours(diff)
                    "$hours hour${if (hours > 1) "s" else ""} ago"
                }
                diff < TimeUnit.DAYS.toMillis(2) -> "Yesterday"
                diff < TimeUnit.DAYS.toMillis(7) -> {
                    val days = TimeUnit.MILLISECONDS.toDays(diff)
                    "$days days ago"
                }
                else -> dateShort
            }
        }

    /**
     * Mileage formatted with unit.
     */
    val mileageFormatted: String?
        get() = mileage?.let { 
            "$it ${mileageUnit.abbreviation}" 
        }

    /**
     * VIN display string (masked for privacy if needed).
     */
    val vinDisplay: String
        get() = vin ?: "Unknown"

    /**
     * Protocol display name.
     */
    val protocolDisplay: String
        get() = protocol?.description ?: "Unknown"

    // ==================== Computed Properties - Summary ====================

    /**
     * Session result summary for display.
     */
    val resultSummary: SessionResultSummary
        get() = SessionResultSummary(
            status = status,
            dtcCount = totalDTCCount,
            ecuCount = ecuCount,
            hasCriticalIssues = hasCriticalDTCs || isMILOn,
            dtcsCleared = dtcsCleared
        )

    /**
     * Health score based on session results (0-100).
     * 
     * Factors:
     * - No DTCs: 100 points base
     * - Pending DTCs: -5 points each
     * - Stored DTCs: -10 points each
     * - Permanent DTCs: -15 points each
     * - Critical DTCs: -20 points each additional
     * - MIL on: -10 points
     */
    val healthScore: Int
        get() {
            var score = 100
            
            score -= pendingDTCCount * 5
            score -= storedDTCCount * 10
            score -= permanentDTCCount * 15
            score -= dtcs.count { it.severity == DTCSeverity.CRITICAL } * 20
            
            if (isMILOn) score -= 10
            
            return score.coerceIn(0, 100)
        }

    /**
     * Health status based on score.
     */
    val healthStatus: HealthStatus
        get() = when {
            healthScore >= 90 -> HealthStatus.EXCELLENT
            healthScore >= 70 -> HealthStatus.GOOD
            healthScore >= 50 -> HealthStatus.FAIR
            healthScore >= 30 -> HealthStatus.POOR
            else -> HealthStatus.CRITICAL
        }

    // ==================== Methods - State Updates ====================

    /**
     * Creates a completed copy of this session.
     */
    fun complete(): DiagnosticSession = copy(
        status = SessionStatus.COMPLETED,
        endTime = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis()
    )

    /**
     * Creates a failed copy of this session.
     * 
     * @param reason Failure reason to store in notes
     */
    fun fail(reason: String? = null): DiagnosticSession = copy(
        status = SessionStatus.FAILED,
        endTime = System.currentTimeMillis(),
        notes = reason?.let { 
            if (notes != null) "$notes\nFailure: $it" else "Failure: $it" 
        } ?: notes,
        updatedAt = System.currentTimeMillis()
    )

    /**
     * Creates a cancelled copy of this session.
     */
    fun cancel(): DiagnosticSession = copy(
        status = SessionStatus.CANCELLED,
        endTime = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis()
    )

    // ==================== Methods - Data Updates ====================

    /**
     * Adds DTCs to the session.
     * 
     * @param newDTCs DTCs to add
     * @return New session with DTCs added
     */
    fun withDTCs(newDTCs: List<DTC>): DiagnosticSession = copy(
        dtcs = (dtcs + newDTCs).distinctBy { it.code },
        updatedAt = System.currentTimeMillis()
    )

    /**
     * Replaces all DTCs in the session.
     * 
     * @param allDTCs Complete list of DTCs
     * @return New session with DTCs replaced
     */
    fun replaceDTCs(allDTCs: List<DTC>): DiagnosticSession = copy(
        dtcs = allDTCs,
        updatedAt = System.currentTimeMillis()
    )

    /**
     * Adds ECUs to the session.
     * 
     * @param newECUs ECUs to add
     * @return New session with ECUs added
     */
    fun withECUs(newECUs: List<ECU>): DiagnosticSession = copy(
        ecus = (ecus + newECUs).distinctBy { it.address },
        updatedAt = System.currentTimeMillis()
    )

    /**
     * Replaces all ECUs in the session.
     * 
     * @param allECUs Complete list of ECUs
     * @return New session with ECUs replaced
     */
    fun replaceECUs(allECUs: List<ECU>): DiagnosticSession = copy(
        ecus = allECUs,
        updatedAt = System.currentTimeMillis()
    )

    /**
     * Adds a freeze frame to the session.
     * 
     * @param freezeFrame Freeze frame to add
     * @return New session with freeze frame added
     */
    fun withFreezeFrame(freezeFrame: FreezeFrame): DiagnosticSession = copy(
        freezeFrames = freezeFrames + freezeFrame,
        updatedAt = System.currentTimeMillis()
    )

    /**
     * Adds a live data snapshot to the session.
     * 
     * @param snapshot Live data snapshot to add
     * @return New session with snapshot added
     */
    fun withLiveDataSnapshot(snapshot: LiveDataSnapshot): DiagnosticSession = copy(
        liveDataSnapshots = liveDataSnapshots + snapshot,
        updatedAt = System.currentTimeMillis()
    )

    /**
     * Adds multiple live data snapshots to the session.
     * 
     * @param snapshots Live data snapshots to add
     * @return New session with snapshots added
     */
    fun withLiveDataSnapshots(snapshots: List<LiveDataSnapshot>): DiagnosticSession = copy(
        liveDataSnapshots = liveDataSnapshots + snapshots,
        updatedAt = System.currentTimeMillis()
    )

    /**
     * Updates monitor status.
     * 
     * @param status New monitor status
     * @return New session with monitor status updated
     */
    fun withMonitorStatus(status: MonitorStatus): DiagnosticSession = copy(
        monitorStatus = status,
        updatedAt = System.currentTimeMillis()
    )

    /**
     * Marks DTCs as cleared.
     * 
     * @return New session with DTCs cleared flag set
     */
    fun markDTCsCleared(): DiagnosticSession = copy(
        dtcsCleared = true,
        dtcsClearedAt = System.currentTimeMillis(),
        dtcs = emptyList(),
        updatedAt = System.currentTimeMillis()
    )

    /**
     * Adds or updates notes.
     * 
     * @param newNotes Notes to add
     * @param append Whether to append to existing notes
     * @return New session with updated notes
     */
    fun withNotes(newNotes: String, append: Boolean = false): DiagnosticSession = copy(
        notes = if (append && notes != null) "$notes\n$newNotes" else newNotes,
        updatedAt = System.currentTimeMillis()
    )

    /**
     * Adds a tag to the session.
     * 
     * @param tag Tag to add
     * @return New session with tag added
     */
    fun withTag(tag: String): DiagnosticSession = copy(
        tags = (tags + tag).distinct(),
        updatedAt = System.currentTimeMillis()
    )

    /**
     * Adds multiple tags to the session.
     * 
     * @param newTags Tags to add
     * @return New session with tags added
     */
    fun withTags(newTags: List<String>): DiagnosticSession = copy(
        tags = (tags + newTags).distinct(),
        updatedAt = System.currentTimeMillis()
    )

    /**
     * Removes a tag from the session.
     * 
     * @param tag Tag to remove
     * @return New session with tag removed
     */
    fun removeTag(tag: String): DiagnosticSession = copy(
        tags = tags - tag,
        updatedAt = System.currentTimeMillis()
    )

    /**
     * Updates mileage.
     * 
     * @param newMileage New mileage value
     * @param unit Mileage unit
     * @return New session with updated mileage
     */
    fun withMileage(newMileage: Int, unit: MileageUnit = mileageUnit): DiagnosticSession = copy(
        mileage = newMileage,
        mileageUnit = unit,
        updatedAt = System.currentTimeMillis()
    )

    /**
     * Updates VIN.
     * 
     * @param newVin New VIN
     * @return New session with updated VIN
     */
    fun withVIN(newVin: String): DiagnosticSession = copy(
        vin = newVin,
        updatedAt = System.currentTimeMillis()
    )

    /**
     * Updates protocol.
     * 
     * @param newProtocol Protocol type
     * @return New session with updated protocol
     */
    fun withProtocol(newProtocol: ProtocolType): DiagnosticSession = copy(
        protocol = newProtocol,
        updatedAt = System.currentTimeMillis()
    )

    /**
     * Updates metadata.
     * 
     * @param newMetadata New metadata
     * @return New session with updated metadata
     */
    fun withMetadata(newMetadata: SessionMetadata): DiagnosticSession = copy(
        metadata = newMetadata,
        updatedAt = System.currentTimeMillis()
    )

    // ==================== Methods - Summary Generation ====================

    /**
     * Generates a text summary of the session.
     */
    fun getSummary(): String = buildString {
        appendLine("=== Diagnostic Session Summary ===")
        appendLine()
        appendLine("Session ID: $id")
        appendLine("Type: ${type.displayName}")
        appendLine("Status: ${status.displayName}")
        appendLine("Date: $dateFormatted")
        appendLine("Duration: $durationFormatted")
        appendLine()
        appendLine("--- Vehicle ---")
        appendLine("VIN: $vinDisplay")
        mileageFormatted?.let { appendLine("Mileage: $it") }
        appendLine("Protocol: $protocolDisplay")
        appendLine()
        appendLine("--- Results ---")
        appendLine("ECUs Found: $ecuCount")
        appendLine("Total DTCs: $totalDTCCount")
        if (hasDTCs) {
            appendLine("  - Stored: $storedDTCCount")
            appendLine("  - Pending: $pendingDTCCount")
            appendLine("  - Permanent: $permanentDTCCount")
        }
        appendLine("MIL Status: ${if (isMILOn) "ON" else "OFF"}")
        appendLine("Health Score: $healthScore/100 (${healthStatus.displayName})")
        appendLine()
        if (hasDTCs) {
            appendLine("--- DTCs ---")
            dtcs.forEach { dtc ->
                appendLine("${dtc.code}: ${dtc.description ?: "No description"}")
            }
            appendLine()
        }
        if (dtcsCleared) {
            appendLine("Note: DTCs were cleared during this session")
            dtcsClearedAt?.let { 
                appendLine("Cleared at: ${DATE_FORMAT.format(Date(it))}")
            }
            appendLine()
        }
        notes?.let {
            appendLine("--- Notes ---")
            appendLine(it)
            appendLine()
        }
        if (tags.isNotEmpty()) {
            appendLine("Tags: ${tags.joinToString(", ")}")
        }
    }

    /**
     * Generates a short summary for list display.
     */
    fun getShortSummary(): String = buildString {
        append("$dateShort - ${type.displayName}")
        if (hasDTCs) {
            append(" - $totalDTCCount DTC${if (totalDTCCount > 1) "s" else ""}")
        } else {
            append(" - No issues")
        }
    }

    // ==================== Companion Object ====================

    companion object {
        private const val serialVersionUID = 1L

        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        private val SHORT_DATE_FORMAT = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault())
        private val TIME_FORMAT = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

        /**
         * Creates a new quick scan session.
         * 
         * @param vehicleId Vehicle identifier
         * @param vin VIN (optional)
         * @param scannerId Scanner ID (optional)
         * @return New session configured for quick scan
         */
        fun quickScan(
            vehicleId: String,
            vin: String? = null,
            scannerId: String? = null
        ): DiagnosticSession = DiagnosticSession(
            vehicleId = vehicleId,
            vin = vin,
            type = DiagnosticSessionType.QUICK_SCAN,
            scannerId = scannerId
        )

        /**
         * Creates a new full diagnostic session.
         * 
         * @param vehicleId Vehicle identifier
         * @param vin VIN (optional)
         * @param scannerId Scanner ID (optional)
         * @return New session configured for full diagnostic
         */
        fun fullDiagnostic(
            vehicleId: String,
            vin: String? = null,
            scannerId: String? = null
        ): DiagnosticSession = DiagnosticSession(
            vehicleId = vehicleId,
            vin = vin,
            type = DiagnosticSessionType.FULL_DIAGNOSTIC,
            scannerId = scannerId
        )

        /**
         * Creates a new live data recording session.
         * 
         * @param vehicleId Vehicle identifier
         * @param vin VIN (optional)
         * @param scannerId Scanner ID (optional)
         * @return New session configured for live data
         */
        fun liveDataRecording(
            vehicleId: String,
            vin: String? = null,
            scannerId: String? = null
        ): DiagnosticSession = DiagnosticSession(
            vehicleId = vehicleId,
            vin = vin,
            type = DiagnosticSessionType.LIVE_DATA,
            scannerId = scannerId
        )

        /**
         * Creates a new DTC-only scan session.
         * 
         * @param vehicleId Vehicle identifier
         * @param vin VIN (optional)
         * @return New session for DTC reading only
         */
        fun dtcScan(
            vehicleId: String,
            vin: String? = null
        ): DiagnosticSession = DiagnosticSession(
            vehicleId = vehicleId,
            vin = vin,
            type = DiagnosticSessionType.DTC_ONLY
        )

        /**
         * Creates a new coding session.
         * 
         * @param vehicleId Vehicle identifier
         * @param vin VIN (optional)
         * @return New session for ECU coding
         */
        fun coding(
            vehicleId: String,
            vin: String? = null
        ): DiagnosticSession = DiagnosticSession(
            vehicleId = vehicleId,
            vin = vin,
            type = DiagnosticSessionType.CODING
        )
    }
}

// ==================== Session Type ====================

/**
 * Type of diagnostic session determining the workflow and features.
 * 
 * @property displayName Human-readable name
 * @property description Detailed description
 * @property icon Icon resource name
 * @property estimatedDuration Estimated duration in minutes
 */
enum class DiagnosticSessionType(
    val displayName: String,
    val description: String,
    val icon: String = "diagnostic",
    val estimatedDuration: Int = 5
) {
    /**
     * Quick scan - reads DTCs and basic vehicle info.
     * Fastest option for checking current issues.
     */
    QUICK_SCAN(
        displayName = "Quick Scan",
        description = "Read DTCs and basic vehicle information",
        icon = "quick_scan",
        estimatedDuration = 2
    ),

    /**
     * Full diagnostic - complete vehicle scan.
     * Scans all ECUs, reads all DTCs, captures freeze frames.
     */
    FULL_DIAGNOSTIC(
        displayName = "Full Diagnostic",
        description = "Complete vehicle diagnostic scan including all ECUs",
        icon = "full_scan",
        estimatedDuration = 10
    ),

    /**
     * DTC only - reads and optionally clears DTCs.
     * Quick check focused only on trouble codes.
     */
    DTC_ONLY(
        displayName = "DTC Scan",
        description = "Read and clear diagnostic trouble codes only",
        icon = "dtc_scan",
        estimatedDuration = 1
    ),

    /**
     * Live data - monitors real-time sensor data.
     * Duration depends on how long monitoring continues.
     */
    LIVE_DATA(
        displayName = "Live Data",
        description = "Monitor real-time vehicle sensor data",
        icon = "live_data",
        estimatedDuration = 0 // Variable
    ),

    /**
     * Coding - ECU coding and configuration.
     * For advanced users modifying ECU parameters.
     */
    CODING(
        displayName = "Coding",
        description = "ECU coding and configuration session",
        icon = "coding",
        estimatedDuration = 15
    ),

    /**
     * Service reset - maintenance service resets.
     * Oil life, brake pads, inspection reminders.
     */
    SERVICE_RESET(
        displayName = "Service Reset",
        description = "Reset service reminders and maintenance indicators",
        icon = "service",
        estimatedDuration = 3
    ),

    /**
     * Custom - user-defined diagnostic workflow.
     */
    CUSTOM(
        displayName = "Custom",
        description = "Custom diagnostic session",
        icon = "custom",
        estimatedDuration = 5
    );

    /**
     * Whether this session type reads DTCs.
     */
    val readsDTCs: Boolean
        get() = this in listOf(QUICK_SCAN, FULL_DIAGNOSTIC, DTC_ONLY)

    /**
     * Whether this session type scans ECUs.
     */
    val scansECUs: Boolean
        get() = this in listOf(FULL_DIAGNOSTIC)

    /**
     * Whether this session type captures live data.
     */
    val capturesLiveData: Boolean
        get() = this in listOf(LIVE_DATA, FULL_DIAGNOSTIC)

    /**
     * Whether this session type supports coding.
     */
    val supportsCoding: Boolean
        get() = this == CODING
}

// ==================== Session Status ====================

/**
 * Current status of a diagnostic session.
 * 
 * @property displayName Human-readable status name
 * @property isTerminal Whether this is a final state
 */
enum class SessionStatus(
    val displayName: String,
    val isTerminal: Boolean
) {
    /** Session is currently in progress */
    IN_PROGRESS("In Progress", false),

    /** Session completed successfully */
    COMPLETED("Completed", true),

    /** Session failed due to error */
    FAILED("Failed", true),

    /** Session was cancelled by user */
    CANCELLED("Cancelled", true),

    /** Session is paused */
    PAUSED("Paused", false);

    /**
     * Whether session can be resumed.
     */
    val canResume: Boolean
        get() = this == PAUSED

    /**
     * Whether session is active (not terminal).
     */
    val isActive: Boolean
        get() = !isTerminal
}

// ==================== Mileage Unit ====================

/**
 * Unit of mileage measurement.
 * 
 * @property displayName Full unit name
 * @property abbreviation Short abbreviation
 * @property toKilometers Conversion factor to kilometers
 */
enum class MileageUnit(
    val displayName: String,
    val abbreviation: String,
    val toKilometers: Double
) {
    KILOMETERS("Kilometers", "km", 1.0),
    MILES("Miles", "mi", 1.60934);

    /**
     * Converts value from this unit to kilometers.
     */
    fun convertToKm(value: Int): Double = value * toKilometers

    /**
     * Converts value from kilometers to this unit.
     */
    fun convertFromKm(km: Double): Int = (km / toKilometers).toInt()

    /**
     * Converts value to another unit.
     */
    fun convertTo(value: Int, targetUnit: MileageUnit): Int {
        val km = convertToKm(value)
        return targetUnit.convertFromKm(km)
    }
}

// ==================== Live Data Snapshot ====================

/**
 * A snapshot of live data values at a specific point in time.
 * 
 * @property timestamp Timestamp when snapshot was taken
 * @property values Map of PID to value
 * @property label Optional label for this snapshot
 */
data class LiveDataSnapshot(
    val timestamp: Long = System.currentTimeMillis(),
    val values: Map<Int, LiveDataValue> = emptyMap(),
    val label: String? = null
) : Serializable {

    /**
     * Number of values in snapshot.
     */
    val valueCount: Int
        get() = values.size

    /**
     * Whether snapshot has any values.
     */
    val hasValues: Boolean
        get() = values.isNotEmpty()

    /**
     * Gets value for a specific PID.
     */
    fun getValue(pid: Int): LiveDataValue? = values[pid]

    /**
     * Formats timestamp for display.
     */
    val timestampFormatted: String
        get() = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
            .format(Date(timestamp))

    companion object {
        private const val serialVersionUID = 1L

        /**
         * Creates a snapshot from a list of values.
         */
        fun fromValues(
            values: List<LiveDataValue>,
            timestamp: Long = System.currentTimeMillis(),
            label: String? = null
        ): LiveDataSnapshot = LiveDataSnapshot(
            timestamp = timestamp,
            values = values.associateBy { it.pid.pid },
            label = label
        )
    }
}

// ==================== Monitor Status ====================

/**
 * OBD-II readiness monitor status.
 * 
 * @property milOn Whether MIL (check engine light) is on
 * @property dtcCount Number of DTCs stored
 * @property readinessComplete Whether all monitors are complete
 * @property monitorsComplete Number of completed monitors
 * @property monitorsTotal Total number of monitors
 * @property monitors Individual monitor statuses
 */
data class MonitorStatus(
    val milOn: Boolean = false,
    val dtcCount: Int = 0,
    val readinessComplete: Boolean = false,
    val monitorsComplete: Int = 0,
    val monitorsTotal: Int = 0,
    val monitors: Map<MonitorType, MonitorState> = emptyMap()
) : Serializable {

    /**
     * Completion percentage.
     */
    val completionPercent: Int
        get() = if (monitorsTotal > 0) {
            (monitorsComplete * 100) / monitorsTotal
        } else 0

    /**
     * Whether vehicle is ready for emissions test.
     */
    val isEmissionsReady: Boolean
        get() = readinessComplete && !milOn

    companion object {
        private const val serialVersionUID = 1L

        /**
         * Creates from PID 01 01 response.
         */
        fun fromPID0101(response: ByteArray): MonitorStatus {
            if (response.size < 4) return MonitorStatus()

            val milOn = (response[0].toInt() and 0x80) != 0
            val dtcCount = response[0].toInt() and 0x7F

            // Parse monitor support and completion from bytes B, C, D
            // This is simplified - full implementation would parse all monitors

            return MonitorStatus(
                milOn = milOn,
                dtcCount = dtcCount
            )
        }
    }
}

/**
 * Types of OBD-II readiness monitors.
 */
enum class MonitorType(val displayName: String) {
    CATALYST("Catalyst"),
    HEATED_CATALYST("Heated Catalyst"),
    EVAPORATIVE_SYSTEM("Evaporative System"),
    SECONDARY_AIR("Secondary Air System"),
    AC_REFRIGERANT("A/C Refrigerant"),
    OXYGEN_SENSOR("Oxygen Sensor"),
    OXYGEN_SENSOR_HEATER("Oxygen Sensor Heater"),
    EGR_SYSTEM("EGR System"),
    MISFIRE("Misfire"),
    FUEL_SYSTEM("Fuel System"),
    COMPONENTS("Comprehensive Components")
}

/**
 * State of an individual monitor.
 */
enum class MonitorState(val displayName: String) {
    NOT_SUPPORTED("Not Supported"),
    NOT_COMPLETE("Not Complete"),
    COMPLETE("Complete")
}

// ==================== Session Metadata ====================

/**
 * Additional metadata about the diagnostic session.
 * 
 * @property appVersion Application version
 * @property deviceModel Device model name
 * @property androidVersion Android version
 * @property location Location description
 * @property technicianId Technician identifier
 * @property workOrderId Work order/ticket ID
 * @property customFields Additional custom fields
 */
data class SessionMetadata(
    val appVersion: String? = null,
    val deviceModel: String? = null,
    val androidVersion: String? = null,
    val location: String? = null,
    val technicianId: String? = null,
    val technicianName: String? = null,
    val workOrderId: String? = null,
    val shopId: String? = null,
    val shopName: String? = null,
    val customFields: Map<String, String> = emptyMap()
) : Serializable {

    /**
     * Whether any metadata is present.
     */
    val hasData: Boolean
        get() = listOfNotNull(
            appVersion, deviceModel, location, 
            technicianId, workOrderId
        ).isNotEmpty() || customFields.isNotEmpty()

    /**
     * Adds a custom field.
     */
    fun withCustomField(key: String, value: String): SessionMetadata = copy(
        customFields = customFields + (key to value)
    )

    /**
     * Gets a custom field value.
     */
    fun getCustomField(key: String): String? = customFields[key]

    companion object {
        private const val serialVersionUID = 1L

        /**
         * Creates metadata from device info.
         */
        fun fromDevice(
            appVersion: String,
            deviceModel: String,
            androidVersion: String
        ): SessionMetadata = SessionMetadata(
            appVersion = appVersion,
            deviceModel = deviceModel,
            androidVersion = androidVersion
        )
    }
}

// ==================== Session Result Summary ====================

/**
 * Summary of session results for quick display.
 */
data class SessionResultSummary(
    val status: SessionStatus,
    val dtcCount: Int,
    val ecuCount: Int,
    val hasCriticalIssues: Boolean,
    val dtcsCleared: Boolean
) : Serializable {

    /**
     * Result icon based on status and issues.
     */
    val icon: String
        get() = when {
            status == SessionStatus.FAILED -> "error"
            status == SessionStatus.CANCELLED -> "cancelled"
            hasCriticalIssues -> "warning"
            dtcCount > 0 -> "caution"
            else -> "success"
        }

    /**
     * Result color for UI.
     */
    val color: String
        get() = when {
            status == SessionStatus.FAILED -> "red"
            hasCriticalIssues -> "red"
            dtcCount > 0 -> "orange"
            else -> "green"
        }

    /**
     * Short summary text.
     */
    val summaryText: String
        get() = when {
            status == SessionStatus.FAILED -> "Scan Failed"
            status == SessionStatus.CANCELLED -> "Cancelled"
            dtcsCleared -> "DTCs Cleared"
            hasCriticalIssues -> "$dtcCount Critical Issue${if (dtcCount > 1) "s" else ""}"
            dtcCount > 0 -> "$dtcCount DTC${if (dtcCount > 1) "s" else ""} Found"
            else -> "No Issues Found"
        }

    companion object {
        private const val serialVersionUID = 1L
    }
}

// ==================== Health Status ====================

/**
 * Vehicle health status based on diagnostic results.
 */
enum class HealthStatus(
    val displayName: String,
    val color: String,
    val icon: String
) {
    EXCELLENT("Excellent", "green", "check_circle"),
    GOOD("Good", "light_green", "check"),
    FAIR("Fair", "yellow", "warning"),
    POOR("Poor", "orange", "error"),
    CRITICAL("Critical", "red", "dangerous");

    /**
     * Whether attention is needed.
     */
    val needsAttention: Boolean
        get() = this in listOf(POOR, CRITICAL)
}

// ==================== Extension Functions ====================

/**
 * Filters sessions by status.
 */
fun List<DiagnosticSession>.filterByStatus(status: SessionStatus): List<DiagnosticSession> =
    filter { it.status == status }

/**
 * Gets only completed sessions.
 */
fun List<DiagnosticSession>.completed(): List<DiagnosticSession> =
    filter { it.isComplete }

/**
 * Gets sessions with DTCs.
 */
fun List<DiagnosticSession>.withDTCs(): List<DiagnosticSession> =
    filter { it.hasDTCs }

/**
 * Gets sessions for a specific vehicle.
 */
fun List<DiagnosticSession>.forVehicle(vehicleId: String): List<DiagnosticSession> =
    filter { it.vehicleId == vehicleId }

/**
 * Gets sessions within a date range.
 */
fun List<DiagnosticSession>.inDateRange(
    startDate: Long,
    endDate: Long
): List<DiagnosticSession> =
    filter { it.startTime in startDate..endDate }

/**
 * Sorts sessions by date (newest first).
 */
fun List<DiagnosticSession>.sortedByDateDescending(): List<DiagnosticSession> =
    sortedByDescending { it.startTime }

/**
 * Gets total DTC count across all sessions.
 */
fun List<DiagnosticSession>.totalDTCCount(): Int =
    sumOf { it.totalDTCCount }

/**
 * Gets unique DTCs across all sessions.
 */
fun List<DiagnosticSession>.uniqueDTCs(): List<DTC> =
    flatMap { it.dtcs }.distinctBy { it.code }
