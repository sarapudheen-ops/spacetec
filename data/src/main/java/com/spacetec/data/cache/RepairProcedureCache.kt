package com.spacetec.data.cache

import com.spacetec.domain.models.diagnostic.RepairProcedure
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory cache for repair procedures with intelligent eviction and performance optimization.
 *
 * Provides fast access to frequently used repair procedures while managing memory usage
 * through LRU eviction and cache size limits.
 *
 * @author SpaceTec Team
 * @since 1.0.0
 */
@Singleton
class RepairProcedureCache @Inject constructor() {

    private val procedureCache = ConcurrentHashMap<String, RepairProcedure>()
    private val dtcProceduresCache = ConcurrentHashMap<String, List<RepairProcedure>>()
    private val searchResultsCache = ConcurrentHashMap<String, List<RepairProcedure>>()
    private val accessTimes = ConcurrentHashMap<String, Long>()

    companion object {
        private const val MAX_PROCEDURES = 1000
        private const val MAX_DTC_MAPPINGS = 500
        private const val MAX_SEARCH_RESULTS = 100
        private const val CACHE_TTL_MS = 30 * 60 * 1000L // 30 minutes
    }

    /**
     * Gets a repair procedure by ID from cache.
     */
    fun getProcedureById(id: String): RepairProcedure? {
        val procedure = procedureCache[id]
        if (procedure != null) {
            accessTimes[id] = System.currentTimeMillis()
        }
        return procedure
    }

    /**
     * Caches a repair procedure.
     */
    fun cacheProcedure(procedure: RepairProcedure) {
        evictIfNecessary()
        procedureCache[procedure.id] = procedure
        accessTimes[procedure.id] = System.currentTimeMillis()
    }

    /**
     * Gets repair procedures for a DTC from cache.
     */
    fun getProceduresForDTC(dtcCode: String): List<RepairProcedure> {
        val procedures = dtcProceduresCache[dtcCode]
        if (procedures != null) {
            accessTimes["dtc_$dtcCode"] = System.currentTimeMillis()
            return procedures
        }
        return emptyList()
    }

    /**
     * Caches repair procedures for a DTC.
     */
    fun cacheProceduresForDTC(dtcCode: String, procedures: List<RepairProcedure>) {
        evictDTCMappingsIfNecessary()
        dtcProceduresCache[dtcCode] = procedures
        accessTimes["dtc_$dtcCode"] = System.currentTimeMillis()
        
        // Also cache individual procedures
        procedures.forEach { cacheProcedure(it) }
    }

    /**
     * Gets search results from cache.
     */
    fun getSearchResults(cacheKey: String): List<RepairProcedure> {
        val results = searchResultsCache[cacheKey]
        if (results != null && !isExpired(cacheKey)) {
            accessTimes[cacheKey] = System.currentTimeMillis()
            return results
        }
        return emptyList()
    }

    /**
     * Caches search results.
     */
    fun cacheSearchResults(cacheKey: String, results: List<RepairProcedure>) {
        evictSearchResultsIfNecessary()
        searchResultsCache[cacheKey] = results
        accessTimes[cacheKey] = System.currentTimeMillis()
    }

    /**
     * Invalidates a specific procedure from cache.
     */
    fun invalidateProcedure(id: String) {
        procedureCache.remove(id)
        accessTimes.remove(id)
        
        // Remove from DTC mappings that contain this procedure
        dtcProceduresCache.entries.removeAll { (_, procedures) ->
            procedures.any { it.id == id }
        }
        
        invalidateSearchCache()
    }

    /**
     * Invalidates all search results cache.
     */
    fun invalidateSearchCache() {
        searchResultsCache.clear()
        accessTimes.keys.removeAll { it.startsWith("search_") }
    }

    /**
     * Clears all cached data.
     */
    fun clearAll() {
        procedureCache.clear()
        dtcProceduresCache.clear()
        searchResultsCache.clear()
        accessTimes.clear()
    }

    /**
     * Gets cache statistics.
     */
    fun getStatistics(): CacheStatistics {
        return CacheStatistics(
            procedureCount = procedureCache.size,
            dtcMappingCount = dtcProceduresCache.size,
            searchResultCount = searchResultsCache.size,
            totalMemoryUsage = estimateMemoryUsage()
        )
    }

    // ==================== Private Helper Methods ====================

    /**
     * Evicts old entries if cache is full.
     */
    private fun evictIfNecessary() {
        if (procedureCache.size >= MAX_PROCEDURES) {
            evictLeastRecentlyUsed(procedureCache, accessTimes, MAX_PROCEDURES / 4)
        }
    }

    /**
     * Evicts old DTC mappings if cache is full.
     */
    private fun evictDTCMappingsIfNecessary() {
        if (dtcProceduresCache.size >= MAX_DTC_MAPPINGS) {
            val keysToRemove = dtcProceduresCache.keys
                .sortedBy { accessTimes["dtc_$it"] ?: 0L }
                .take(MAX_DTC_MAPPINGS / 4)
            
            keysToRemove.forEach { key ->
                dtcProceduresCache.remove(key)
                accessTimes.remove("dtc_$key")
            }
        }
    }

    /**
     * Evicts old search results if cache is full.
     */
    private fun evictSearchResultsIfNecessary() {
        if (searchResultsCache.size >= MAX_SEARCH_RESULTS) {
            val keysToRemove = searchResultsCache.keys
                .sortedBy { accessTimes[it] ?: 0L }
                .take(MAX_SEARCH_RESULTS / 4)
            
            keysToRemove.forEach { key ->
                searchResultsCache.remove(key)
                accessTimes.remove(key)
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
     * Estimates memory usage of cached data.
     */
    private fun estimateMemoryUsage(): Long {
        // Rough estimation - in production you might use more sophisticated memory measurement
        val procedureSize = procedureCache.values.sumOf { estimateProcedureSize(it) }
        val dtcMappingSize = dtcProceduresCache.values.sumOf { list ->
            list.sumOf { estimateProcedureSize(it) }
        }
        val searchResultSize = searchResultsCache.values.sumOf { list ->
            list.sumOf { estimateProcedureSize(it) }
        }
        
        return procedureSize + dtcMappingSize + searchResultSize
    }

    /**
     * Estimates the memory size of a repair procedure.
     */
    private fun estimateProcedureSize(procedure: RepairProcedure): Long {
        // Rough estimation based on string lengths and object overhead
        return (procedure.title.length + 
                procedure.description.length + 
                procedure.steps.sumOf { it.instruction.length } +
                procedure.requiredTools.sumOf { it.length } +
                procedure.requiredParts.size * 100 + // Rough estimate for parts
                200) // Object overhead
            .toLong()
    }
}

/**
 * Cache statistics for monitoring and debugging.
 */
data class CacheStatistics(
    val procedureCount: Int,
    val dtcMappingCount: Int,
    val searchResultCount: Int,
    val totalMemoryUsage: Long
)