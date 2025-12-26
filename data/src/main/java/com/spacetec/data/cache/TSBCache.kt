package com.spacetec.data.cache

import com.spacetec.domain.models.diagnostic.TechnicalServiceBulletin
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory cache for Technical Service Bulletins with intelligent eviction and performance optimization.
 *
 * Provides fast access to frequently used TSBs while managing memory usage
 * through LRU eviction and cache size limits.
 *
 * @author SpaceTec Team
 * @since 1.0.0
 */
@Singleton
class TSBCache @Inject constructor() {

    private val tsbCache = ConcurrentHashMap<String, TechnicalServiceBulletin>()
    private val dtcTSBsCache = ConcurrentHashMap<String, List<TechnicalServiceBulletin>>()
    private val searchResultsCache = ConcurrentHashMap<String, List<TechnicalServiceBulletin>>()
    private val accessTimes = ConcurrentHashMap<String, Long>()

    companion object {
        private const val MAX_TSBS = 2000
        private const val MAX_DTC_MAPPINGS = 1000
        private const val MAX_SEARCH_RESULTS = 200
        private const val CACHE_TTL_MS = 60 * 60 * 1000L // 1 hour
    }

    /**
     * Gets a TSB by ID from cache.
     */
    fun getTSBById(id: String): TechnicalServiceBulletin? {
        val tsb = tsbCache[id]
        if (tsb != null) {
            accessTimes[id] = System.currentTimeMillis()
        }
        return tsb
    }

    /**
     * Caches a TSB.
     */
    fun cacheTSB(tsb: TechnicalServiceBulletin) {
        evictIfNecessary()
        tsbCache[tsb.id] = tsb
        accessTimes[tsb.id] = System.currentTimeMillis()
    }

    /**
     * Gets TSBs for a DTC from cache.
     */
    fun getTSBsForDTC(dtcCode: String): List<TechnicalServiceBulletin> {
        val tsbs = dtcTSBsCache[dtcCode]
        if (tsbs != null) {
            accessTimes["dtc_$dtcCode"] = System.currentTimeMillis()
            return tsbs
        }
        return emptyList()
    }

    /**
     * Caches TSBs for a DTC.
     */
    fun cacheTSBsForDTC(dtcCode: String, tsbs: List<TechnicalServiceBulletin>) {
        evictDTCMappingsIfNecessary()
        dtcTSBsCache[dtcCode] = tsbs
        accessTimes["dtc_$dtcCode"] = System.currentTimeMillis()
        
        // Also cache individual TSBs
        tsbs.forEach { cacheTSB(it) }
    }

    /**
     * Gets search results from cache.
     */
    fun getSearchResults(cacheKey: String): List<TechnicalServiceBulletin> {
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
    fun cacheSearchResults(cacheKey: String, results: List<TechnicalServiceBulletin>) {
        evictSearchResultsIfNecessary()
        searchResultsCache[cacheKey] = results
        accessTimes[cacheKey] = System.currentTimeMillis()
    }

    /**
     * Invalidates a specific TSB from cache.
     */
    fun invalidateTSB(id: String) {
        tsbCache.remove(id)
        accessTimes.remove(id)
        
        // Remove from DTC mappings that contain this TSB
        dtcTSBsCache.entries.removeAll { (_, tsbs) ->
            tsbs.any { it.id == id }
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
        tsbCache.clear()
        dtcTSBsCache.clear()
        searchResultsCache.clear()
        accessTimes.clear()
    }

    /**
     * Gets cache statistics.
     */
    fun getStatistics(): TSBCacheStatistics {
        return TSBCacheStatistics(
            tsbCount = tsbCache.size,
            dtcMappingCount = dtcTSBsCache.size,
            searchResultCount = searchResultsCache.size,
            totalMemoryUsage = estimateMemoryUsage()
        )
    }

    // ==================== Private Helper Methods ====================

    /**
     * Evicts old entries if cache is full.
     */
    private fun evictIfNecessary() {
        if (tsbCache.size >= MAX_TSBS) {
            evictLeastRecentlyUsed(tsbCache, accessTimes, MAX_TSBS / 4)
        }
    }

    /**
     * Evicts old DTC mappings if cache is full.
     */
    private fun evictDTCMappingsIfNecessary() {
        if (dtcTSBsCache.size >= MAX_DTC_MAPPINGS) {
            val keysToRemove = dtcTSBsCache.keys
                .sortedBy { accessTimes["dtc_$it"] ?: 0L }
                .take(MAX_DTC_MAPPINGS / 4)
            
            keysToRemove.forEach { key ->
                dtcTSBsCache.remove(key)
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
        val tsbSize = tsbCache.values.sumOf { estimateTSBSize(it) }
        val dtcMappingSize = dtcTSBsCache.values.sumOf { list ->
            list.sumOf { estimateTSBSize(it) }
        }
        val searchResultSize = searchResultsCache.values.sumOf { list ->
            list.sumOf { estimateTSBSize(it) }
        }
        
        return tsbSize + dtcMappingSize + searchResultSize
    }

    /**
     * Estimates the memory size of a TSB.
     */
    private fun estimateTSBSize(tsb: TechnicalServiceBulletin): Long {
        // Rough estimation based on string lengths and object overhead
        return (tsb.title.length + 
                tsb.description.length + 
                tsb.repairProcedure.length +
                tsb.bulletinNumber.length +
                tsb.manufacturer.length +
                tsb.dtcCodes.sumOf { it.length } +
                tsb.symptoms.sumOf { it.length } +
                tsb.attachments.size * 200 + // Rough estimate for attachments
                300) // Object overhead
            .toLong()
    }
}

/**
 * Cache statistics for TSBs.
 */
data class TSBCacheStatistics(
    val tsbCount: Int,
    val dtcMappingCount: Int,
    val searchResultCount: Int,
    val totalMemoryUsage: Long
)