package com.spacetec.data.repository

import com.spacetec.core.common.Result
import com.spacetec.core.database.dao.DiagnosticSessionDao
import com.spacetec.core.database.dao.DTCDao
import com.spacetec.core.database.dao.FreezeFrameDao
import com.spacetec.core.database.dao.LiveDataSnapshotDao
import com.spacetec.data.cache.DiagnosticSessionCache
import com.spacetec.data.mapper.DiagnosticSessionMapper
import com.spacetec.data.mapper.DTCMapper
import com.spacetec.domain.models.diagnostic.*
import com.spacetec.domain.repository.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of DiagnosticSessionRepository with comprehensive analytics and caching.
 *
 * Provides diagnostic session management including pattern analysis, performance tracking,
 * and intelligent caching for analytics queries.
 *
 * @author SpaceTec Team
 * @since 1.0.0
 */
@Singleton
class DiagnosticSessionRepositoryImpl @Inject constructor(
    private val sessionDao: DiagnosticSessionDao,
    private val dtcDao: DTCDao,
    private val freezeFrameDao: FreezeFrameDao,
    private val liveDataDao: LiveDataSnapshotDao,
    private val sessionMapper: DiagnosticSessionMapper,
    private val dtcMapper: DTCMapper,
    private val cache: DiagnosticSessionCache
) : DiagnosticSessionRepository {

    override suspend fun recordDiagnosticSession(session: DiagnosticSession): Result<Unit> {
        return try {
            // Convert to entity
            val entity = sessionMapper.toEntity(session)
            
            // Save session
            sessionDao.insertSession(entity)

            // Save related DTCs
            if (session.dtcs.isNotEmpty()) {
                val dtcEntities = session.dtcs.map { dtc ->
                    dtcMapper.toEntity(dtc, session.id, session.vehicleId)
                }
                dtcDao.insertDTCs(dtcEntities)
            }

            // Save freeze frames
            if (session.freezeFrames.isNotEmpty()) {
                val freezeFrameEntities = session.freezeFrames.map { ff ->
                    dtcMapper.toFreezeFrameEntity(ff, session.id)
                }
                freezeFrameDao.insertFreezeFrames(freezeFrameEntities)
            }

            // Save live data snapshots
            if (session.liveDataSnapshots.isNotEmpty()) {
                val snapshotEntities = sessionMapper.toLiveDataSnapshotEntityList(
                    session.liveDataSnapshots, 
                    session.id
                )
                liveDataDao.insertSnapshots(snapshotEntities)
            }

            // Update cache
            cache.cacheSession(session)
            cache.invalidateAnalyticsCache()

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun getSessionHistory(vehicleId: String): Result<List<DiagnosticSession>> {
        return try {
            // Check cache first
            val cached = cache.getSessionsForVehicle(vehicleId)
            if (cached.isNotEmpty()) {
                return Result.Success(cached)
            }

            // Query database
            val entities = sessionDao.getSessionsForVehicle(vehicleId)
            val sessions = entities.map { entity ->
                buildCompleteSession(entity)
            }

            // Sort by date (newest first)
            val sorted = sessionMapper.sortByDateDescending(sessions)

            // Cache results
            cache.cacheSessionsForVehicle(vehicleId, sorted)

            Result.Success(sorted)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun getSessionsByDTC(dtcCode: String): Result<List<DiagnosticSession>> {
        return try {
            // Check cache first
            val cacheKey = "dtc_$dtcCode"
            val cached = cache.getSessionsByQuery(cacheKey)
            if (cached.isNotEmpty()) {
                return Result.Success(cached)
            }

            // Query database
            val sessionIds = dtcDao.getSessionsWithDTC(dtcCode)
            val entities = sessionDao.getSessionsByIds(sessionIds)
            val sessions = entities.map { entity ->
                buildCompleteSession(entity)
            }

            // Filter to only sessions that actually contain the DTC
            val filtered = sessions.filter { session ->
                session.dtcs.any { it.code == dtcCode }
            }

            // Sort by date (newest first)
            val sorted = sessionMapper.sortByDateDescending(filtered)

            // Cache results
            cache.cacheSessionsByQuery(cacheKey, sorted)

            Result.Success(sorted)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun generateAnalytics(query: AnalyticsQuery): Result<DiagnosticAnalytics> {
        return try {
            // Generate cache key
            val cacheKey = generateAnalyticsCacheKey(query)
            val cached = cache.getAnalytics(cacheKey)
            if (cached != null) {
                return Result.Success(cached)
            }

            // Query sessions based on criteria
            val sessions = getSessionsForAnalytics(query)

            // Calculate analytics
            val analytics = calculateAnalytics(sessions, query)

            // Cache results
            cache.cacheAnalytics(cacheKey, analytics)

            Result.Success(analytics)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Gets session statistics for a time period.
     */
    suspend fun getSessionStatistics(timeRange: TimeRange): Result<SessionStatistics> {
        return try {
            val sessions = sessionDao.getSessionsInTimeRange(timeRange.startTime, timeRange.endTime)
            val completeSessions = sessions.map { buildCompleteSession(it) }
            
            val statistics = sessionMapper.calculateStatistics(completeSessions)
            Result.Success(statistics)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Gets sessions for a specific technician.
     */
    suspend fun getSessionsForTechnician(
        technicianId: String,
        timeRange: TimeRange? = null
    ): Result<List<DiagnosticSession>> {
        return try {
            val cacheKey = "tech_${technicianId}_${timeRange?.startTime}_${timeRange?.endTime}"
            val cached = cache.getSessionsByQuery(cacheKey)
            if (cached.isNotEmpty()) {
                return Result.Success(cached)
            }

            val entities = if (timeRange != null) {
                sessionDao.getSessionsForTechnicianInTimeRange(
                    technicianId, 
                    timeRange.startTime, 
                    timeRange.endTime
                )
            } else {
                sessionDao.getSessionsForTechnician(technicianId)
            }

            val sessions = entities.map { buildCompleteSession(it) }
            val sorted = sessionMapper.sortByDateDescending(sessions)

            cache.cacheSessionsByQuery(cacheKey, sorted)
            Result.Success(sorted)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Gets performance metrics for sessions.
     */
    suspend fun getPerformanceMetrics(timeRange: TimeRange): Result<SessionPerformanceMetrics> {
        return try {
            val sessions = sessionDao.getSessionsInTimeRange(timeRange.startTime, timeRange.endTime)
            val completeSessions = sessions.map { buildCompleteSession(it) }

            val metrics = calculatePerformanceMetrics(completeSessions)
            Result.Success(metrics)
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
     * Builds a complete DiagnosticSession with all related data.
     */
    private suspend fun buildCompleteSession(entity: com.spacetec.core.database.entities.DiagnosticSessionEntity): DiagnosticSession {
        // Get related DTCs
        val dtcEntities = dtcDao.getDTCsForSession(entity.id)
        val dtcs = dtcMapper.toDomainList(dtcEntities)

        // Get freeze frames
        val freezeFrameEntities = freezeFrameDao.getFreezeFramesForSession(entity.id)
        val freezeFrames = dtcMapper.toFreezeFrameDomainList(freezeFrameEntities)

        // Get live data snapshots
        val snapshotEntities = liveDataDao.getSnapshotsForSession(entity.id)
        val liveDataSnapshots = sessionMapper.toLiveDataSnapshotDomainList(snapshotEntities)

        // Get monitor status (if available)
        val monitorStatus = sessionDao.getMonitorStatusForSession(entity.id)

        return sessionMapper.toDomain(
            entity = entity,
            dtcs = dtcs,
            freezeFrames = freezeFrames,
            liveDataSnapshots = liveDataSnapshots,
            monitorStatus = monitorStatus
        )
    }

    /**
     * Gets sessions for analytics based on query criteria.
     */
    private suspend fun getSessionsForAnalytics(query: AnalyticsQuery): List<DiagnosticSession> {
        val entities = when {
            query.vehicleIds.isNotEmpty() -> {
                sessionDao.getSessionsForVehicles(query.vehicleIds, query.timeRange.startTime, query.timeRange.endTime)
            }
            query.technicians.isNotEmpty() -> {
                sessionDao.getSessionsForTechnicians(query.technicians, query.timeRange.startTime, query.timeRange.endTime)
            }
            query.dtcCodes.isNotEmpty() -> {
                val sessionIds = dtcDao.getSessionsWithDTCs(query.dtcCodes)
                sessionDao.getSessionsByIds(sessionIds).filter { entity ->
                    entity.startTime in query.timeRange.startTime..query.timeRange.endTime
                }
            }
            else -> {
                sessionDao.getSessionsInTimeRange(query.timeRange.startTime, query.timeRange.endTime)
            }
        }

        return entities.map { buildCompleteSession(it) }
    }

    /**
     * Calculates comprehensive analytics from sessions.
     */
    private suspend fun calculateAnalytics(
        sessions: List<DiagnosticSession>,
        query: AnalyticsQuery
    ): DiagnosticAnalytics {
        val completedSessions = sessions.filter { it.isComplete }
        
        // Calculate basic metrics
        val totalSessions = sessions.size
        val averageSessionTime = if (completedSessions.isNotEmpty()) {
            java.time.Duration.ofMillis(
                completedSessions.map { it.durationMs }.average().toLong()
            )
        } else {
            java.time.Duration.ZERO
        }
        
        val successRate = if (sessions.isNotEmpty()) {
            completedSessions.size.toDouble() / sessions.size
        } else 0.0

        // Calculate DTC frequency
        val allDTCs = sessions.flatMap { it.dtcs }
        val dtcFrequency = allDTCs.groupBy { it.code }
            .map { (code, dtcs) ->
                DTCFrequency(
                    dtcCode = code,
                    count = dtcs.size,
                    percentage = (dtcs.size.toDouble() / allDTCs.size) * 100,
                    trend = TrendDirection.STABLE, // Would need historical data to calculate
                    description = dtcs.first().description
                )
            }
            .sortedByDescending { it.count }

        // Calculate patterns (simplified)
        val patterns = if (query.includePatterns) {
            identifyPatterns(sessions)
        } else emptyList()

        // Calculate efficiency metrics
        val efficiencyMetrics = if (query.includeEfficiency) {
            calculateEfficiencyMetrics(completedSessions)
        } else {
            EfficiencyMetrics(
                averageDiagnosticTime = averageSessionTime,
                averageRepairTime = java.time.Duration.ZERO,
                firstTimeFixRate = 0.0,
                reworkRate = 0.0
            )
        }

        // Calculate cost analysis
        val costAnalysis = if (query.includeSuccess) {
            calculateCostAnalysis(sessions)
        } else {
            CostAnalysis(
                totalCost = 0.0,
                averageCostPerSession = 0.0,
                averageCostPerDTC = 0.0,
                costByCategory = emptyMap()
            )
        }

        return DiagnosticAnalytics(
            timeRange = query.timeRange,
            totalSessions = totalSessions,
            averageSessionTime = averageSessionTime,
            successRate = successRate,
            mostCommonDTCs = dtcFrequency.take(10),
            patterns = patterns,
            efficiencyMetrics = efficiencyMetrics,
            costAnalysis = costAnalysis
        )
    }

    /**
     * Identifies patterns in diagnostic sessions.
     */
    private fun identifyPatterns(sessions: List<DiagnosticSession>): List<DTCPattern> {
        // Simplified pattern identification
        // In a real implementation, this would use more sophisticated algorithms
        
        val dtcPairs = sessions.flatMap { session ->
            session.dtcs.flatMap { dtc1 ->
                session.dtcs.filter { it != dtc1 }.map { dtc2 ->
                    listOf(dtc1.code, dtc2.code).sorted()
                }
            }
        }.groupBy { it }
        
        return dtcPairs.filter { it.value.size >= 3 } // At least 3 occurrences
            .map { (codes, occurrences) ->
                DTCPattern(
                    pattern = "Co-occurrence: ${codes.joinToString(" + ")}",
                    dtcCodes = codes,
                    frequency = occurrences.size,
                    confidence = occurrences.size.toDouble() / sessions.size
                )
            }
            .sortedByDescending { it.frequency }
    }

    /**
     * Calculates efficiency metrics from completed sessions.
     */
    private fun calculateEfficiencyMetrics(sessions: List<DiagnosticSession>): EfficiencyMetrics {
        if (sessions.isEmpty()) {
            return EfficiencyMetrics(
                averageDiagnosticTime = java.time.Duration.ZERO,
                averageRepairTime = java.time.Duration.ZERO,
                firstTimeFixRate = 0.0,
                reworkRate = 0.0
            )
        }

        val averageDiagnosticTime = java.time.Duration.ofMillis(
            sessions.map { it.durationMs }.average().toLong()
        )

        // Simplified calculations - in real implementation would track repair outcomes
        val firstTimeFixRate = sessions.count { !it.hasDTCs }.toDouble() / sessions.size
        val reworkRate = 1.0 - firstTimeFixRate

        return EfficiencyMetrics(
            averageDiagnosticTime = averageDiagnosticTime,
            averageRepairTime = java.time.Duration.ofHours(2), // Placeholder
            firstTimeFixRate = firstTimeFixRate,
            reworkRate = reworkRate
        )
    }

    /**
     * Calculates cost analysis from sessions.
     */
    private fun calculateCostAnalysis(sessions: List<DiagnosticSession>): CostAnalysis {
        // Simplified cost calculation - in real implementation would track actual costs
        val estimatedCostPerSession = 150.0 // Average diagnostic cost
        val totalCost = sessions.size * estimatedCostPerSession
        
        val costByCategory = sessions.groupBy { it.type }
            .mapValues { (_, typeSessions) -> typeSessions.size * estimatedCostPerSession }
            .mapKeys { it.key.displayName }

        val averageCostPerDTC = if (sessions.sumOf { it.totalDTCCount } > 0) {
            totalCost / sessions.sumOf { it.totalDTCCount }
        } else 0.0

        return CostAnalysis(
            totalCost = totalCost,
            averageCostPerSession = estimatedCostPerSession,
            averageCostPerDTC = averageCostPerDTC,
            costByCategory = costByCategory
        )
    }

    /**
     * Calculates performance metrics.
     */
    private fun calculatePerformanceMetrics(sessions: List<DiagnosticSession>): SessionPerformanceMetrics {
        val completedSessions = sessions.filter { it.isComplete }
        
        return SessionPerformanceMetrics(
            totalSessions = sessions.size,
            completedSessions = completedSessions.size,
            averageDuration = if (completedSessions.isNotEmpty()) {
                completedSessions.map { it.durationMinutes }.average()
            } else 0.0,
            successRate = if (sessions.isNotEmpty()) {
                (completedSessions.size.toDouble() / sessions.size) * 100
            } else 0.0,
            averageDTCsFound = sessions.map { it.totalDTCCount }.average(),
            mostCommonType = sessions.groupBy { it.type }.maxByOrNull { it.value.size }?.key
        )
    }

    /**
     * Generates a cache key for analytics queries.
     */
    private fun generateAnalyticsCacheKey(query: AnalyticsQuery): String {
        return buildString {
            append("analytics_")
            append("${query.timeRange.startTime}_${query.timeRange.endTime}_")
            if (query.vehicleIds.isNotEmpty()) {
                append("vehicles_${query.vehicleIds.sorted().joinToString("_")}_")
            }
            if (query.dtcCodes.isNotEmpty()) {
                append("dtcs_${query.dtcCodes.sorted().joinToString("_")}_")
            }
            if (query.technicians.isNotEmpty()) {
                append("techs_${query.technicians.sorted().joinToString("_")}_")
            }
            append("patterns_${query.includePatterns}_")
            append("efficiency_${query.includeEfficiency}_")
            append("success_${query.includeSuccess}")
        }
    }
}

/**
 * Performance metrics for diagnostic sessions.
 */
data class SessionPerformanceMetrics(
    val totalSessions: Int,
    val completedSessions: Int,
    val averageDuration: Double,
    val successRate: Double,
    val averageDTCsFound: Double,
    val mostCommonType: DiagnosticSessionType?
)