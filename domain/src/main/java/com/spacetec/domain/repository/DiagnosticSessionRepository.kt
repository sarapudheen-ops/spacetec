package com.spacetec.domain.repository

import com.spacetec.domain.models.vehicle.Vehicle
import java.time.LocalDate
import kotlin.time.Duration

/**
 * Represents a complete diagnostic session.
 */
data class DiagnosticSession(
    val id: String,
    val vehicleId: String,
    val vehicle: Vehicle,
    val startTimestamp: Long,
    val endTimestamp: Long?,
    val dtcCodes: List<String>,
    val diagnosticSteps: List<SessionDiagnosticStep>,
    val outcome: SessionOutcome?,
    val technicianId: String?,
    val technicianNotes: String?,
    val repairProceduresUsed: List<String>,
    val totalDiagnosticTime: Duration?,
    val totalRepairTime: Duration?
)

data class SessionDiagnosticStep(
    val stepId: String,
    val dtcCode: String,
    val stepNumber: Int,
    val description: String,
    val timestamp: Long,
    val result: StepResult,
    val notes: String?
)

enum class StepResult {
    PASSED, FAILED, INCONCLUSIVE, SKIPPED
}

data class SessionOutcome(
    val resolved: Boolean,
    val dtcsCleared: List<String>,
    val dtcsRemaining: List<String>,
    val rootCauseIdentified: Boolean,
    val rootCauseDescription: String?,
    val repairPerformed: Boolean,
    val followUpRequired: Boolean,
    val followUpNotes: String?
)

data class AnalyticsQuery(
    val vehicleId: String? = null,
    val dtcCodes: List<String>? = null,
    val technicianId: String? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val includeSuccessRates: Boolean = true,
    val includeTimeAnalytics: Boolean = true,
    val includeTrends: Boolean = true
)

data class DiagnosticAnalytics(
    val query: AnalyticsQuery,
    val totalSessions: Int,
    val successfulSessions: Int,
    val averageDiagnosticTime: Duration,
    val averageRepairTime: Duration,
    val successRate: Double,
    val mostCommonDTCs: List<DTCFrequency>,
    val procedureSuccessRates: List<ProcedureSuccessRate>,
    val timeEfficiencyMetrics: TimeEfficiencyMetrics?,
    val trends: DiagnosticTrends?
)

data class DTCFrequency(
    val code: String,
    val description: String,
    val occurrences: Int,
    val percentage: Double,
    val averageResolutionTime: Duration
)

data class ProcedureSuccessRate(
    val procedureId: String,
    val procedureName: String,
    val totalAttempts: Int,
    val successfulAttempts: Int,
    val successRate: Double
)

data class TimeEfficiencyMetrics(
    val averageDiagnosticTime: Duration,
    val medianDiagnosticTime: Duration,
    val fastestDiagnosticTime: Duration,
    val slowestDiagnosticTime: Duration,
    val averageRepairTime: Duration,
    val timeByDTCCategory: Map<String, Duration>
)

data class DiagnosticTrends(
    val dailySessionCounts: Map<LocalDate, Int>,
    val weeklySuccessRates: Map<LocalDate, Double>,
    val emergingDTCPatterns: List<EmergingPattern>,
    val seasonalPatterns: List<SeasonalPattern>
)

data class EmergingPattern(
    val dtcCode: String,
    val description: String,
    val recentOccurrences: Int,
    val previousPeriodOccurrences: Int,
    val growthPercentage: Double
)

data class SeasonalPattern(
    val dtcCode: String,
    val description: String,
    val peakMonths: List<Int>,
    val seasonalFactor: Double
)

data class DTCPatternAnalysis(
    val commonPatterns: List<DTCPattern>,
    val seasonalTrends: List<SeasonalTrend>,
    val vehicleSpecificIssues: List<VehicleIssue>,
    val emergingProblems: List<EmergingProblem>,
    val repairEffectiveness: List<RepairEffectivenessData>
)

data class DTCPattern(
    val patternId: String,
    val dtcCodes: List<String>,
    val occurrences: Int,
    val confidence: Double,
    val commonCause: String?,
    val recommendedAction: String?
)

data class SeasonalTrend(
    val dtcCode: String,
    val season: String,
    val frequency: Double,
    val possibleCauses: List<String>
)

data class VehicleIssue(
    val manufacturer: String,
    val model: String,
    val yearRange: IntRange,
    val dtcCodes: List<String>,
    val description: String,
    val affectedVehicleCount: Int,
    val severity: DTCSeverity
)

data class EmergingProblem(
    val dtcCode: String,
    val description: String,
    val firstDetected: LocalDate,
    val affectedVehicles: Int,
    val growthRate: Double,
    val severity: DTCSeverity
)

data class RepairEffectivenessData(
    val dtcCode: String,
    val procedureId: String,
    val procedureName: String,
    val successRate: Double,
    val averageTime: Duration,
    val recommendedForVehicles: List<String>
)

data class PatternAnalysisQuery(
    val vehicleManufacturers: List<String>? = null,
    val vehicleModels: List<String>? = null,
    val yearRange: IntRange? = null,
    val dtcCategories: List<String>? = null,
    val analysisStartDate: LocalDate? = null,
    val analysisEndDate: LocalDate? = null,
    val minimumOccurrences: Int = 5,
    val confidenceThreshold: Double = 0.7
)

enum class ExportFormat {
    JSON, CSV, PDF, EXCEL
}

data class AnalyticsExport(
    val format: ExportFormat,
    val data: ByteArray,
    val filename: String,
    val generatedAt: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AnalyticsExport
        return format == other.format && data.contentEquals(other.data) &&
                filename == other.filename && generatedAt == other.generatedAt
    }

    override fun hashCode(): Int {
        var result = format.hashCode()
        result = 31 * result + data.contentHashCode()
        result = 31 * result + filename.hashCode()
        result = 31 * result + generatedAt.hashCode()
        return result
    }
}

/**
 * Repository interface for diagnostic session tracking and analytics.
 */
interface DiagnosticSessionRepository {

    suspend fun recordDiagnosticSession(session: DiagnosticSession): Result<Unit>

    suspend fun updateDiagnosticSession(session: DiagnosticSession): Result<Unit>

    suspend fun getSessionById(sessionId: String): Result<DiagnosticSession?>

    suspend fun getSessionHistory(vehicleId: String, limit: Int = 100): Result<List<DiagnosticSession>>

    suspend fun getSessionsByDTC(dtcCode: String, limit: Int = 100): Result<List<DiagnosticSession>>

    suspend fun getSessionsByTechnician(technicianId: String, limit: Int = 100): Result<List<DiagnosticSession>>

    suspend fun getSessionsByDateRange(startDate: LocalDate, endDate: LocalDate, limit: Int = 500): Result<List<DiagnosticSession>>

    suspend fun generateAnalytics(query: AnalyticsQuery): Result<DiagnosticAnalytics>

    suspend fun analyzeDTCPatterns(query: PatternAnalysisQuery): Result<DTCPatternAnalysis>

    suspend fun calculateProcedureSuccessRate(procedureId: String): Result<ProcedureSuccessRate>

    suspend fun getTimeEfficiencyAnalytics(startDate: LocalDate, endDate: LocalDate): Result<TimeEfficiencyMetrics>

    suspend fun exportAnalyticsReport(query: AnalyticsQuery, format: ExportFormat): Result<AnalyticsExport>

    suspend fun cleanupOldSessions(olderThan: LocalDate): Result<Int>
}
