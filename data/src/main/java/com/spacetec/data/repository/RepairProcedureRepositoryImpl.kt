package com.spacetec.data.repository

import com.spacetec.core.common.Result
import com.spacetec.core.database.dao.RepairProcedureDao
import com.spacetec.core.database.dao.RepairStepDao
import com.spacetec.data.cache.RepairProcedureCache
import com.spacetec.data.mapper.RepairProcedureMapper
import com.spacetec.domain.models.diagnostic.*
import com.spacetec.domain.repository.*
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of RepairProcedureRepository with comprehensive caching and success rate tracking.
 *
 * Provides repair procedure management including effectiveness tracking, vehicle-specific
 * filtering, and intelligent caching for performance optimization.
 *
 * @author SpaceTec Team
 * @since 1.0.0
 */
@Singleton
class RepairProcedureRepositoryImpl @Inject constructor(
    private val procedureDao: RepairProcedureDao,
    private val stepDao: RepairStepDao,
    private val mapper: RepairProcedureMapper,
    private val cache: RepairProcedureCache
) : RepairProcedureRepository {

    override suspend fun getProceduresForDTC(dtcCode: String): Result<List<RepairProcedure>> {
        return try {
            // Check cache first
            val cached = cache.getProceduresForDTC(dtcCode)
            if (cached.isNotEmpty()) {
                return Result.Success(cached)
            }

            // Query database
            val entities = procedureDao.getProceduresForDTC(dtcCode)
            val procedures = entities.map { entity ->
                val steps = stepDao.getStepsForProcedure(entity.id)
                mapper.toDomain(entity, steps)
            }

            // Cache results
            cache.cacheProceduresForDTC(dtcCode, procedures)

            Result.Success(procedures.sortedByDescending { it.successRate })
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun getProcedureById(id: String): Result<RepairProcedure?> {
        return try {
            // Check cache first
            val cached = cache.getProcedureById(id)
            if (cached != null) {
                return Result.Success(cached)
            }

            // Query database
            val entity = procedureDao.getProcedureById(id)
            if (entity != null) {
                val steps = stepDao.getStepsForProcedure(id)
                val procedure = mapper.toDomain(entity, steps)
                
                // Cache result
                cache.cacheProcedure(procedure)
                
                Result.Success(procedure)
            } else {
                Result.Success(null)
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun searchProcedures(query: ProcedureSearchQuery): Result<List<RepairProcedure>> {
        return try {
            // Generate cache key from query
            val cacheKey = generateSearchCacheKey(query)
            val cached = cache.getSearchResults(cacheKey)
            if (cached.isNotEmpty()) {
                return Result.Success(cached)
            }

            // Build database query
            val entities = when {
                !query.dtcCode.isNullOrBlank() -> {
                    procedureDao.getProceduresForDTC(query.dtcCode)
                }
                !query.title.isNullOrBlank() -> {
                    procedureDao.searchByTitle("%${query.title}%")
                }
                query.difficulty != null -> {
                    procedureDao.getProceduresByDifficulty(query.difficulty.name)
                }
                query.maxTime != null -> {
                    procedureDao.getProceduresByMaxTime(query.maxTime.toMinutes().toInt())
                }
                query.minSuccessRate != null -> {
                    procedureDao.getProceduresByMinSuccessRate(query.minSuccessRate)
                }
                query.requiredTools.isNotEmpty() -> {
                    procedureDao.getProceduresByTools(query.requiredTools)
                }
                else -> {
                    procedureDao.getAllProcedures()
                }
            }

            // Convert to domain models with steps
            val procedures = entities.map { entity ->
                val steps = stepDao.getStepsForProcedure(entity.id)
                mapper.toDomain(entity, steps)
            }

            // Apply additional filtering
            val filtered = applyQueryFilters(procedures, query)

            // Sort results
            val sorted = sortProcedures(filtered, query.sortBy)

            // Apply pagination
            val paginated = sorted.drop(query.offset).take(query.limit)

            // Cache results
            cache.cacheSearchResults(cacheKey, paginated)

            Result.Success(paginated)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun saveProcedure(procedure: RepairProcedure): Result<Unit> {
        return try {
            // Convert to entity
            val entity = mapper.toEntity(procedure)
            val stepEntities = mapper.toStepEntityList(procedure.steps, procedure.id)

            // Save to database
            procedureDao.insertProcedure(entity)
            stepDao.insertSteps(stepEntities)

            // Update cache
            cache.cacheProcedure(procedure)
            cache.invalidateSearchCache()

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun updateProcedureSuccessRate(id: String, outcome: RepairOutcome): Result<Unit> {
        return try {
            // Get current procedure
            val currentEntity = procedureDao.getProcedureById(id)
                ?: return Result.Error(IllegalArgumentException("Procedure not found: $id"))

            // Calculate new success rate
            val totalAttempts = procedureDao.getTotalAttempts(id) + 1
            val successfulAttempts = if (outcome.successful) {
                procedureDao.getSuccessfulAttempts(id) + 1
            } else {
                procedureDao.getSuccessfulAttempts(id)
            }

            val newSuccessRate = successfulAttempts.toDouble() / totalAttempts

            // Update procedure
            val updatedEntity = currentEntity.copy(
                successRate = newSuccessRate,
                lastUpdated = System.currentTimeMillis()
            )

            procedureDao.updateProcedure(updatedEntity)

            // Record outcome
            procedureDao.insertOutcome(
                procedureId = id,
                dtcCode = outcome.dtcCode,
                successful = outcome.successful,
                completionTime = outcome.completionTime?.toMinutes()?.toInt(),
                actualCost = outcome.actualCost,
                notes = outcome.notes,
                timestamp = outcome.timestamp
            )

            // Invalidate cache
            cache.invalidateProcedure(id)
            cache.invalidateSearchCache()

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Gets procedures by vehicle compatibility.
     */
    suspend fun getProceduresForVehicle(
        make: String,
        model: String,
        year: Int
    ): Result<List<RepairProcedure>> {
        return try {
            val cacheKey = "vehicle_${make}_${model}_$year"
            val cached = cache.getSearchResults(cacheKey)
            if (cached.isNotEmpty()) {
                return Result.Success(cached)
            }

            val entities = procedureDao.getProceduresForVehicle(make, model, year)
            val procedures = entities.map { entity ->
                val steps = stepDao.getStepsForProcedure(entity.id)
                mapper.toDomain(entity, steps)
            }

            // Filter by vehicle compatibility
            val compatible = mapper.filterByVehicle(procedures, make, model, year)

            cache.cacheSearchResults(cacheKey, compatible)
            Result.Success(compatible)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Gets top-rated procedures for a DTC.
     */
    suspend fun getTopRatedProcedures(dtcCode: String, limit: Int = 5): Result<List<RepairProcedure>> {
        return try {
            val result = getProceduresForDTC(dtcCode)
            when (result) {
                is Result.Success -> {
                    val topRated = mapper.sortByEffectiveness(result.data).take(limit)
                    Result.Success(topRated)
                }
                is Result.Error -> result
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Gets procedure statistics.
     */
    suspend fun getProcedureStatistics(): Result<RepairProcedureStatistics> {
        return try {
            val totalCount = procedureDao.getTotalProcedureCount()
            val averageSuccessRate = procedureDao.getAverageSuccessRate()
            val difficultyDistribution = procedureDao.getDifficultyDistribution()
            val categoryDistribution = procedureDao.getCategoryDistribution()
            val averageSteps = procedureDao.getAverageStepCount()
            val averageTime = procedureDao.getAverageEstimatedTime()

            val statistics = RepairProcedureStatistics(
                totalCount = totalCount,
                averageSuccessRate = averageSuccessRate,
                difficultyDistribution = difficultyDistribution,
                categoryDistribution = categoryDistribution,
                averageSteps = averageSteps,
                averageTime = averageTime
            )

            Result.Success(statistics)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Clears all cached data.
     */
    suspend fun clearCache(): Result<Unit> {
        return try {
            cache.clearAll()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    // ==================== Private Helper Methods ====================

    /**
     * Generates a cache key for search queries.
     */
    private fun generateSearchCacheKey(query: ProcedureSearchQuery): String {
        return buildString {
            append("search_")
            query.dtcCode?.let { append("dtc_${it}_") }
            query.title?.let { append("title_${it.hashCode()}_") }
            query.difficulty?.let { append("diff_${it.name}_") }
            query.maxTime?.let { append("time_${it.toMinutes()}_") }
            query.minSuccessRate?.let { append("rate_${it}_") }
            if (query.requiredTools.isNotEmpty()) {
                append("tools_${query.requiredTools.hashCode()}_")
            }
            append("sort_${query.sortBy.name}_")
            append("limit_${query.limit}_offset_${query.offset}")
        }
    }

    /**
     * Applies additional query filters to procedures.
     */
    private fun applyQueryFilters(
        procedures: List<RepairProcedure>,
        query: ProcedureSearchQuery
    ): List<RepairProcedure> {
        var filtered = procedures

        // Filter by difficulty
        query.difficulty?.let { difficulty ->
            filtered = filtered.filter { it.difficulty == difficulty }
        }

        // Filter by max time
        query.maxTime?.let { maxTime ->
            filtered = filtered.filter { it.estimatedTime <= maxTime }
        }

        // Filter by minimum success rate
        query.minSuccessRate?.let { minRate ->
            filtered = filtered.filter { it.successRate >= minRate }
        }

        // Filter by required tools
        if (query.requiredTools.isNotEmpty()) {
            filtered = filtered.filter { procedure ->
                query.requiredTools.any { tool ->
                    procedure.requiredTools.any { procTool ->
                        procTool.contains(tool, ignoreCase = true)
                    }
                }
            }
        }

        return filtered
    }

    /**
     * Sorts procedures based on the specified sort option.
     */
    private fun sortProcedures(
        procedures: List<RepairProcedure>,
        sortBy: ProcedureSortOption
    ): List<RepairProcedure> {
        return when (sortBy) {
            ProcedureSortOption.SUCCESS_RATE -> procedures.sortedByDescending { it.successRate }
            ProcedureSortOption.DIFFICULTY -> procedures.sortedBy { it.difficulty.level }
            ProcedureSortOption.ESTIMATED_TIME -> procedures.sortedBy { it.estimatedTime.toMinutes() }
            ProcedureSortOption.TITLE -> procedures.sortedBy { it.title }
            ProcedureSortOption.COST -> procedures.sortedBy { it.totalEstimatedCost }
        }
    }
}

/**
 * Statistics for repair procedures.
 */
data class RepairProcedureStatistics(
    val totalCount: Int,
    val averageSuccessRate: Double,
    val difficultyDistribution: Map<String, Int>,
    val categoryDistribution: Map<String, Int>,
    val averageSteps: Double,
    val averageTime: Double
)