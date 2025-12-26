package com.spacetec.data.cache

import com.spacetec.domain.models.diagnostic.DiagnosticAnalytics
import com.spacetec.domain.models.diagnostic.DiagnosticSession
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory cache for diagnostic sessions and analytics with intelligent eviction.
 *
 * Provides fast access to frequently used sessions and analytics while managing memory usage
 * through LRU eviction and cache size limits.
 *
 * @author SpaceTec Team
 * @since 1.0.0
 */
@Singleton
class DiagnosticSessionCache @Inject constructor() {

    private val sessionCache = ConcurrentHashMap<String, DiagnosticSession>()
    private val vehicleSessionsCache = ConcurrentHashMap<String, List<DiagnosticSession>>()
    private val querySessionsCache = ConcurrentHashMap<String, List<DiagnosticSession>>()
    private val analyticsCache = ConcurrentHashMap<String, DiagnosticAnalytics>()
    private val accessTimes = ConcurrentHashMap<String, Long>()

    companion object {
        private const val MAX_SESSIONS = 1000
        private const val MAX_VEHICLE_MAPPINGS = 200
        private const val MAX_QUERY_RESULTS = 100
        private const val MAX_ANALYTICS = 50
        private const val CACHE_TTL_MS = 15 * 60 * 1000L // 15 minutes
        private const val ANALYTICS_TTL_MS = 5 * 60 * 1000L // 5 minutes for analytics
    }

    /**
     * Gets a diagnostic session by ID from cache.
     */
    fun getSessionById(id: String): DiagnosticSession? {
        val session = sessionCache[id]
        if (session != null) {
            accessTimes[id] = System.currentTimeMillis()
        }
        return session
    }

    /**
     * Caches a diagnostic session.
     */
    fun cacheSession(session: DiagnosticSession) {
        evictSessionsIfNecessary()
        sessionCache[session.id] = session
        accessTimes[session.id] = System.currentTimeMillis()
    }

    /**
     * Gets sessions for a vehicle from cache.
     */
    fun getSessionsForVehicle(vehicleId: String): List<DiagnosticSession> {
        val sessions = vehicleSessionsCache[vehicleId]
        if (sessions != null && !isExpired("vehicle_$vehicleId")) {
            accessTimes["vehicle_$vehicleId"] = System.currentTimeMillis()
            return sessions
        }
        return emptyList()
    }

    /**
     * Caches sessions for a vehicle.
     */
    fun cacheSessionsForVehicle(vehicleId: String, sessions: List<DiagnosticSession>) {
        evictVehicleMappingsIfNecessary()
        vehicleSessionsCache[vehicleId] = sessions
        accessTimes["vehicle_$vehicleId"] = System.currentTimeMillis()
        
        // Also cache individual sessions
        sessions.forEach { cacheSession(it) }
    }

    /**
     * Gets sessions by query from cache.
     */
    fun getSessionsByQuery(cacheKey: String): List<DiagnosticSession> {
        val sessions = querySessionsCache[cacheKey]
        if (sessions != null && !isExpired(cacheKey)) {
            accessTimes[cacheKey] = System.currentTimeMillis()
            return sessions
        }
        return emptyList()
    }

    /**
     * Caches sessions by query.
     */
    fun cacheSessionsByQuery(cacheKey: String, sessions: List<DiagnosticSession>) {
        evictQueryResultsIfNecessary()
        querySessionsCache[cacheKey] = sessions
        accessTimes[cacheKey] = System.currentTimeMillis()
    }

    /**
     * Gets analytics from cache.
     */
    fun getAnalytics(cacheKey: String): DiagnosticAnalytics? {
        val analytics = analyticsCache[cacheKey]
        if (analytics != null && !isAnalyticsExpired(cacheKey)) {
            accessTimes["analytics_$cacheKey"] = System.currentTimeMillis()
            return analytics
        }
        return null
    }

    /**
     * Caches analytics results.
     */
    fun cacheAnalytics(cacheKey: String, analytics: DiagnosticAnalytics) {
        evictAnalyticsIfNecessary()
        analyticsCache[cacheKey] = analytics
        accessTimes["analytics_$cacheKey"] = System.currentTimeMillis()
    }

    /**
     * Invalidates a specific session from cache.
     */
    fun invalidateSession(id: String) {
        sessionCache.remove(id)
        accessTimes.remove(id)
        
        // Remove from vehicle mappings that contain this session
        vehicleSessionsCache.entries.removeAll { (_, sessions) ->
            sessions.any { it.id == id }
        }
        
        invalidateQueryCache()
        invalidateAnalyticsCache()
    }

    /**
     * Invalidates all query results cache.
     */
    fun invalidateQueryCache() {
        querySessionsCache.clear()
        accessTimes.keys.removeAll { key ->
            key.startsWith("dtc_") || key.startsWith("tech_") || key.startsWith("search_")
        }
    }

    /**
     * Invalidates all analytics cache.
     */
    fun invalidateAnalyticsCache() {
        analyticsCache.clear()
        accessTimes.keys.removeAll { it.startsWith("analytics_") }
    }

    /**
     * Clears all cached data.
     */
    fun clearAll() {
        sessionCache.clear()
        vehicleSessionsCache.clear()
        querySessionsCache.clear()
        analyticsCache.clear()
        accessTimes.clear()
    }

    /**
     * Gets cache statistics.
     */
    fun getStatistics(): DiagnosticSessionCacheStatistics {
        return DiagnosticSessionCacheStatistics(
            sessionCount = sessionCache.size,
            vehicleMappingCount = vehicleSessionsCache.size,
            queryResultCount = querySessionsCache.size,
            analyticsCount = analyticsCache.size,
            totalMemoryUsage = estimateMemoryUsage()
        )
    }

    // ==================== Private Helper Methods ====================

    /**
     * Evicts old sessions if cache is full.
     */
    private fun evictSessionsIfNecessary() {
        if (sessionCache.size >= MAX_SESSIONS) {
            evictLeastRecentlyUsed(sessionCache, accessTimes, MAX_SESSIONS / 4)
        }
    }

    /**
     * Evicts old vehicle mappings if cache is full.
     */
    private fun evictVehicleMappingsIfNecessary() {
        if (vehicleSessionsCache.size >= MAX_VEHICLE_MAPPINGS) {
            val keysToRemove = vehicleSessionsCache.keys
                .sortedBy { accessTimes["vehicle_$it"] ?: 0L }
                .take(MAX_VEHICLE_MAPPINGS / 4)
            
            keysToRemove.forEach { key ->
                vehicleSessionsCache.remove(key)
                accessTimes.remove("vehicle_$key")
            }
        }
    }

    /**
     * Evicts old query results if cache is full.
     */
    private fun evictQueryResultsIfNecessary() {
        if (querySessionsCache.size >= MAX_QUERY_RESULTS) {
            val keysToRemove = querySessionsCache.keys
                .sortedBy { accessTimes[it] ?: 0L }
                .take(MAX_QUERY_RESULTS / 4)
            
            keysToRemove.forEach { key ->
                querySessionsCache.remove(key)
                accessTimes.remove(key)
            }
        }
    }

    /**
     * Evicts old analytics if cache is full.
     */
    private fun evictAnalyticsIfNecessary() {
        if (analyticsCache.size >= MAX_ANALYTICS) {
            val keysToRemove = analyticsCache.keys
                .sortedBy { accessTimes["analytics_$it"] ?: 0L }
                .take(MAX_ANALYTICS / 4)
            
            keysToRemove.forEach { key ->
                analyticsCache.remove(key)
                accessTimes.remove("analytics_$key")
            }
        }
    }

    /**
     * Evicts least recently used entries from a cache.
     */
    private fun <T> evictLeastRecentlyUsed(
        cache: ConcurrentHashMap<String, T>,
        accessTimes: ConcurrentHashMap<String, Long>,
        evictCount: Int
    ) {
        val keysToRemove = cache.keys
            .sortedBy { accessTimes[it] ?: 0L }
            .take(evictCount)
        
        keysToRemove.forEach { key ->
            cache.remove(key)
            accessTimes.remove(key)
        }
    }

    /**
     * Checks if a cache entry is expired.
     */
    private fun isExpired(key: String): Boolean {
        val accessTime = accessTimes[key] ?: return true
        return System.currentTimeMillis() - accessTime > CACHE_TTL_MS
    }

    /**
     * Checks if an analytics cache entry is expired.
     */
    private fun isAnalyticsExpired(key: String): Boolean {
        val accessTime = accessTimes["analytics_$key"] ?: return true
        return System.currentTimeMillis() - accessTime > ANALYTICS_TTL_MS
    }

    /**
     * Estimates memory usage of cached data.
     */
    private fun estimateMemoryUsage(): Long {
        // Rough estimation - in production you might use more sophisticated memory measurement
        val sessionSize = sessionCache.values.sumOf { estimateSessionSize(it) }
        val vehicleMappingSize = vehicleSessionsCache.values.sumOf { list ->
            list.sumOf { estimateSessionSize(it) }
        }
        val queryResultSize = querySessionsCache.values.sumOf { list ->
            list.sumOf { estimateSessionSize(it) }
        }
        val analyticsSize = analyticsCache.values.sumOf { estimateAnalyticsSize(it) }
        
        return sessionSize + vehicleMappingSize + queryResultSize + analyticsSize
    }

    /**
     * Estimates the memory size of a diagnostic session.
     */
    private fun estimateSessionSize(session: DiagnosticSession): Long {
        // Rough estimation based on object complexity
        return (session.id.length +
                (session.vin?.length ?: 0) +
                (session.notes?.length ?: 0) +
                session.dtcs.size * 200 + // Rough estimate per DTC
                session.freezeFrames.size * 500 + // Rough estimate per freeze frame
                session.liveDataSnapshots.size * 100 + // Rough estimate per snapshot
                session.tags.sumOf { it.length } +
                400) // Object overhead
            .toLong()
    }

    /**
     * Estimates the memory size of analytics data.
     */
    private fun estimateAnalyticsSize(analytics: DiagnosticAnalytics): Long {
        // Rough estimation based on analytics complexity
        return (analytics.mostCommonDTCs.size * 100 +
                analytics.patterns.size * 200 +
                1000) // Base analytics overhead
            .toLong()
    }
}

/**
 * Cache statistics for diagnostic sessions.
 */
data class DiagnosticSessionCacheStatistics(
    val sessionCount: Int,
    val vehicleMappingCount: Int,
    val queryResultCount: Int,
    val analyticsCount: Int,
    val totalMemoryUsage: Long
)