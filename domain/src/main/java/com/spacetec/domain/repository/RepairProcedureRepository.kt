package com.spacetec.domain.repository

import com.spacetec.domain.models.diagnostic.RepairProcedure
import com.spacetec.domain.models.vehicle.Vehicle
import kotlin.time.Duration

/**
 * Query parameters for searching repair procedures.
 */
data class ProcedureSearchQuery(
    val dtcCode: String? = null,
    val vehicle: Vehicle? = null,
    val difficulty: RepairDifficulty? = null,
    val maxTime: Duration? = null,
    val maxCost: Double? = null,
    val keywords: List<String> = emptyList(),
    val sortBy: ProcedureSortOption = ProcedureSortOption.EFFECTIVENESS,
    val limit: Int = 20,
    val offset: Int = 0
)

enum class ProcedureSortOption {
    EFFECTIVENESS, DIFFICULTY_ASC, DIFFICULTY_DESC, TIME_ASC, TIME_DESC, COST_ASC, COST_DESC, SUCCESS_RATE
}

enum class RepairDifficulty {
    BEGINNER, INTERMEDIATE, ADVANCED, EXPERT, DEALER_ONLY
}

data class CostRange(
    val min: Double,
    val max: Double,
    val currency: String = "USD"
)

data class ProcedureSearchResult(
    val procedures: List<RepairProcedure>,
    val totalCount: Int,
    val hasMore: Boolean,
    val query: ProcedureSearchQuery
)

data class RepairOutcome(
    val procedureId: String,
    val dtcCode: String,
    val vehicleId: String,
    val success: Boolean,
    val timestamp: Long,
    val actualTime: Duration?,
    val actualLaborCost: Double?,
    val actualPartsCost: Double?,
    val technicianNotes: String?,
    val followUpRequired: Boolean,
    val followUpDTCs: List<String>
)

data class ProcedureEffectiveness(
    val procedureId: String,
    val totalAttempts: Int,
    val successfulAttempts: Int,
    val successRate: Double,
    val averageTime: Duration,
    val averageLaborCost: Double,
    val averagePartsCost: Double,
    val commonFollowUpIssues: List<FollowUpIssue>
)

data class FollowUpIssue(
    val dtcCode: String,
    val frequency: Int,
    val percentage: Double
)

data class RepairGuidance(
    val dtcCode: String,
    val vehicle: Vehicle?,
    val procedures: List<RepairProcedure>,
    val recommendedProcedure: RepairProcedure?,
    val estimatedTotalTime: Duration,
    val estimatedTotalCost: CostRange,
    val requiredTools: List<String>,
    val safetyWarnings: List<String>,
    val technicalNotes: String?
)

data class ComponentInfo(
    val id: String,
    val name: String,
    val location: String,
    val partNumber: String?,
    val alternatePartNumbers: List<String>,
    val specifications: Map<String, String>,
    val imageUrl: String?,
    val diagramUrl: String?
)

/**
 * Repository interface for repair procedure data operations.
 */
interface RepairProcedureRepository {

    suspend fun getProceduresForDTC(dtcCode: String): Result<List<RepairProcedure>>

    suspend fun getProceduresForDTCAndVehicle(dtcCode: String, vehicle: Vehicle): Result<List<RepairProcedure>>

    suspend fun getProcedureById(id: String): Result<RepairProcedure?>

    suspend fun searchProcedures(query: ProcedureSearchQuery): Result<ProcedureSearchResult>

    suspend fun getRankedProcedures(dtcCode: String, vehicle: Vehicle? = null): Result<List<RepairProcedure>>

    suspend fun saveProcedure(procedure: RepairProcedure): Result<Unit>

    suspend fun updateProcedure(procedure: RepairProcedure): Result<Unit>

    suspend fun updateProcedureSuccessRate(procedureId: String, outcome: RepairOutcome): Result<Unit>

    suspend fun getProcedureEffectiveness(procedureId: String): Result<ProcedureEffectiveness>

    suspend fun generateRepairGuidance(dtcCode: String, vehicle: Vehicle): Result<RepairGuidance>

    suspend fun getRequiredTools(dtcCode: String): Result<List<String>>

    suspend fun getComponentInfo(procedureId: String): Result<List<ComponentInfo>>
}
