package com.spacetec.data.repository

import com.spacetec.core.common.Result
import com.spacetec.core.database.dao.TechnicalServiceBulletinDao
import com.spacetec.core.database.dao.AttachmentDao
import com.spacetec.data.cache.TSBCache
import com.spacetec.data.mapper.TechnicalServiceBulletinMapper
import com.spacetec.domain.models.diagnostic.*
import com.spacetec.domain.repository.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of TSBRepository with comprehensive caching and synchronization.
 *
 * Provides Technical Service Bulletin management including vehicle-specific filtering,
 * DTC correlation, and intelligent caching for performance optimization.
 *
 * @author SpaceTec Team
 * @since 1.0.0
 */
@Singleton
class TSBRepositoryImpl @Inject constructor(
    private val tsbDao: TechnicalServiceBulletinDao,
    private val attachmentDao: AttachmentDao,
    private val mapper: TechnicalServiceBulletinMapper,
    private val cache: TSBCache
) : TSBRepository {

    override suspend fun getTSBsForDTC(dtcCode: String): Result<List<TechnicalServiceBulletin>> {
        return try {
            // Check cache first
            val cached = cache.getTSBsForDTC(dtcCode)
            if (cached.isNotEmpty()) {
                return Result.Success(cached)
            }

            // Query database
            val entities = tsbDao.getTSBsForDTC(dtcCode)
            val tsbs = entities.map { entity ->
                val attachments = attachmentDao.getAttachmentsForTSB(entity.id)
                mapper.toDomain(entity, attachments)
            }

            // Sort by priority
            val sorted = mapper.sortByPriority(tsbs)

            // Cache results
            cache.cacheTSBsForDTC(dtcCode, sorted)

            Result.Success(sorted)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun getTSBById(id: String): Result<TechnicalServiceBulletin?> {
        return try {
            // Check cache first
            val cached = cache.getTSBById(id)
            if (cached != null) {
                return Result.Success(cached)
            }

            // Query database
            val entity = tsbDao.getTSBById(id)
            if (entity != null) {
                val attachments = attachmentDao.getAttachmentsForTSB(id)
                val tsb = mapper.toDomain(entity, attachments)
                
                // Cache result
                cache.cacheTSB(tsb)
                
                Result.Success(tsb)
            } else {
                Result.Success(null)
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun searchTSBs(query: TSBSearchQuery): Result<List<TechnicalServiceBulletin>> {
        return try {
            // Generate cache key from query
            val cacheKey = generateSearchCacheKey(query)
            val cached = cache.getSearchResults(cacheKey)
            if (cached.isNotEmpty()) {
                return Result.Success(cached)
            }

            // Build database query
            val entities = when {
                !query.bulletinNumber.isNullOrBlank() -> {
                    listOfNotNull(tsbDao.getTSBByBulletinNumber(query.bulletinNumber))
                }
                !query.manufacturer.isNullOrBlank() -> {
                    tsbDao.getTSBsByManufacturer(query.manufacturer)
                }
                !query.title.isNullOrBlank() -> {
                    tsbDao.searchByTitle("%${query.title}%")
                }
                !query.dtcCode.isNullOrBlank() -> {
                    tsbDao.getTSBsForDTC(query.dtcCode)
                }
                query.category != null -> {
                    tsbDao.getTSBsByCategory(query.category.name)
                }
                query.severity != null -> {
                    tsbDao.getTSBsBySeverity(query.severity.name)
                }
                !query.vehicleMake.isNullOrBlank() -> {
                    tsbDao.getTSBsForVehicle(query.vehicleMake, query.vehicleModel, query.modelYear)
                }
                query.publishDateFrom != null || query.publishDateTo != null -> {
                    tsbDao.getTSBsByDateRange(
                        query.publishDateFrom ?: 0L,
                        query.publishDateTo ?: System.currentTimeMillis()
                    )
                }
                else -> {
                    tsbDao.getAllActiveTSBs()
                }
            }

            // Convert to domain models with attachments
            val tsbs = entities.map { entity ->
                val attachments = attachmentDao.getAttachmentsForTSB(entity.id)
                mapper.toDomain(entity, attachments)
            }

            // Apply additional filtering
            val filtered = applyQueryFilters(tsbs, query)

            // Sort results
            val sorted = sortTSBs(filtered, query.sortBy)

            // Apply pagination
            val paginated = sorted.drop(query.offset).take(query.limit)

            // Cache results
            cache.cacheSearchResults(cacheKey, paginated)

            Result.Success(paginated)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun syncTSBs(): Result<SyncResult> {
        return try {
            // This would typically involve:
            // 1. Fetching updates from manufacturer APIs
            // 2. Comparing with local data
            // 3. Updating local database
            // 4. Tracking sync statistics

            val startTime = System.currentTimeMillis()
            var updatedCount = 0
            var addedCount = 0
            var errorCount = 0
            val errors = mutableListOf<String>()

            try {
                // Simulate sync process
                // In real implementation, this would call manufacturer APIs
                val remoteUpdates = fetchRemoteUpdates()
                
                for (update in remoteUpdates) {
                    try {
                        val existing = tsbDao.getTSBByBulletinNumber(update.bulletinNumber)
                        if (existing != null) {
                            // Update existing TSB
                            val entity = mapper.toEntity(update)
                            tsbDao.updateTSB(entity)
                            
                            // Update attachments
                            attachmentDao.deleteAttachmentsForTSB(existing.id)
                            val attachmentEntities = mapper.toAttachmentEntityList(update.attachments, update.id)
                            attachmentDao.insertAttachments(attachmentEntities)
                            
                            updatedCount++
                        } else {
                            // Add new TSB
                            val entity = mapper.toEntity(update)
                            tsbDao.insertTSB(entity)
                            
                            // Add attachments
                            val attachmentEntities = mapper.toAttachmentEntityList(update.attachments, update.id)
                            attachmentDao.insertAttachments(attachmentEntities)
                            
                            addedCount++
                        }
                        
                        // Update cache
                        cache.cacheTSB(update)
                    } catch (e: Exception) {
                        errorCount++
                        errors.add("Failed to sync TSB ${update.bulletinNumber}: ${e.message}")
                    }
                }

                // Clear search cache after sync
                cache.invalidateSearchCache()

                val syncResult = SyncResult(
                    success = errorCount == 0,
                    updatedCount = updatedCount,
                    addedCount = addedCount,
                    errorCount = errorCount,
                    lastSyncTime = System.currentTimeMillis(),
                    errors = errors
                )

                Result.Success(syncResult)
            } catch (e: Exception) {
                val syncResult = SyncResult(
                    success = false,
                    updatedCount = updatedCount,
                    addedCount = addedCount,
                    errorCount = errorCount + 1,
                    lastSyncTime = System.currentTimeMillis(),
                    errors = errors + "Sync failed: ${e.message}"
                )
                Result.Success(syncResult)
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Gets TSBs for a specific vehicle.
     */
    suspend fun getTSBsForVehicle(
        make: String,
        model: String,
        year: Int
    ): Result<List<TechnicalServiceBulletin>> {
        return try {
            val cacheKey = "vehicle_${make}_${model}_$year"
            val cached = cache.getSearchResults(cacheKey)
            if (cached.isNotEmpty()) {
                return Result.Success(cached)
            }

            val entities = tsbDao.getTSBsForVehicle(make, model, year)
            val tsbs = entities.map { entity ->
                val attachments = attachmentDao.getAttachmentsForTSB(entity.id)
                mapper.toDomain(entity, attachments)
            }

            // Filter by vehicle compatibility
            val compatible = mapper.filterByVehicle(tsbs, make, model, year)
            val sorted = mapper.sortByPriority(compatible)

            cache.cacheSearchResults(cacheKey, sorted)
            Result.Success(sorted)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Gets TSBs related to multiple DTCs.
     */
    suspend fun getTSBsForDTCs(dtcCodes: List<String>): Result<List<TechnicalServiceBulletin>> {
        return try {
            val cacheKey = "dtcs_${dtcCodes.sorted().joinToString("_")}"
            val cached = cache.getSearchResults(cacheKey)
            if (cached.isNotEmpty()) {
                return Result.Success(cached)
            }

            val entities = tsbDao.getTSBsForDTCs(dtcCodes)
            val tsbs = entities.map { entity ->
                val attachments = attachmentDao.getAttachmentsForTSB(entity.id)
                mapper.toDomain(entity, attachments)
            }

            val filtered = mapper.filterByDTCs(tsbs, dtcCodes)
            val sorted = mapper.sortByPriority(filtered)

            cache.cacheSearchResults(cacheKey, sorted)
            Result.Success(sorted)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Gets TSB statistics.
     */
    suspend fun getTSBStatistics(): Result<TSBStatistics> {
        return try {
            val totalCount = tsbDao.getTotalTSBCount()
            val activeCount = tsbDao.getActiveTSBCount()
            val supersededCount = tsbDao.getSupersededTSBCount()
            val categoryDistribution = tsbDao.getCategoryDistribution()
            val severityDistribution = tsbDao.getSeverityDistribution()
            val manufacturerDistribution = tsbDao.getManufacturerDistribution()
            val averageAge = tsbDao.getAverageAge()
            val withAttachmentsCount = tsbDao.getTSBsWithAttachmentsCount()
            val withDTCsCount = tsbDao.getTSBsWithDTCsCount()

            val statistics = TSBStatistics(
                totalCount = totalCount,
                activeCount = activeCount,
                supersededCount = supersededCount,
                categoryDistribution = categoryDistribution,
                severityDistribution = severityDistribution,
                manufacturerDistribution = manufacturerDistribution,
                averageAge = averageAge,
                withAttachmentsCount = withAttachmentsCount,
                withDTCsCount = withDTCsCount
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
    private fun generateSearchCacheKey(query: TSBSearchQuery): String {
        return buildString {
            append("search_")
            query.bulletinNumber?.let { append("bulletin_${it}_") }
            query.manufacturer?.let { append("mfg_${it}_") }
            query.title?.let { append("title_${it.hashCode()}_") }
            query.dtcCode?.let { append("dtc_${it}_") }
            query.category?.let { append("cat_${it.name}_") }
            query.severity?.let { append("sev_${it.name}_") }
            query.vehicleMake?.let { append("make_${it}_") }
            query.vehicleModel?.let { append("model_${it}_") }
            query.modelYear?.let { append("year_${it}_") }
            query.publishDateFrom?.let { append("from_${it}_") }
            query.publishDateTo?.let { append("to_${it}_") }
            append("sort_${query.sortBy.name}_")
            append("limit_${query.limit}_offset_${query.offset}")
        }
    }

    /**
     * Applies additional query filters to TSBs.
     */
    private fun applyQueryFilters(
        tsbs: List<TechnicalServiceBulletin>,
        query: TSBSearchQuery
    ): List<TechnicalServiceBulletin> {
        var filtered = tsbs

        // Filter by category
        query.category?.let { category ->
            filtered = filtered.filter { it.category == category }
        }

        // Filter by severity
        query.severity?.let { severity ->
            filtered = filtered.filter { it.severity == severity }
        }

        // Filter by vehicle make
        query.vehicleMake?.let { make ->
            filtered = filtered.filter { tsb ->
                tsb.applicableVehicles.any { it.make.equals(make, ignoreCase = true) }
            }
        }

        // Filter by vehicle model
        query.vehicleModel?.let { model ->
            filtered = filtered.filter { tsb ->
                tsb.applicableVehicles.any { 
                    it.model?.equals(model, ignoreCase = true) == true 
                }
            }
        }

        // Filter by model year
        query.modelYear?.let { year ->
            filtered = filtered.filter { tsb ->
                tsb.applicableVehicles.any { it.yearStart <= year && it.yearEnd >= year }
            }
        }

        // Filter by publish date range
        if (query.publishDateFrom != null || query.publishDateTo != null) {
            val fromDate = query.publishDateFrom ?: 0L
            val toDate = query.publishDateTo ?: System.currentTimeMillis()
            filtered = filtered.filter { it.publishDate in fromDate..toDate }
        }

        return filtered
    }

    /**
     * Sorts TSBs based on the specified sort option.
     */
    private fun sortTSBs(
        tsbs: List<TechnicalServiceBulletin>,
        sortBy: TSBSortOption
    ): List<TechnicalServiceBulletin> {
        return when (sortBy) {
            TSBSortOption.PUBLISH_DATE -> tsbs.sortedByDescending { it.publishDate }
            TSBSortOption.BULLETIN_NUMBER -> tsbs.sortedBy { it.bulletinNumber }
            TSBSortOption.TITLE -> tsbs.sortedBy { it.title }
            TSBSortOption.MANUFACTURER -> tsbs.sortedBy { it.manufacturer }
            TSBSortOption.SEVERITY -> tsbs.sortedByDescending { it.severity.level }
        }
    }

    /**
     * Simulates fetching remote updates from manufacturer APIs.
     * In real implementation, this would call actual manufacturer APIs.
     */
    private suspend fun fetchRemoteUpdates(): List<TechnicalServiceBulletin> {
        // This is a placeholder for actual API calls
        // In real implementation, you would:
        // 1. Call manufacturer APIs (Ford, GM, Toyota, etc.)
        // 2. Parse their responses
        // 3. Convert to TechnicalServiceBulletin objects
        return emptyList()
    }
}

/**
 * Statistics for Technical Service Bulletins.
 */
data class TSBStatistics(
    val totalCount: Int,
    val activeCount: Int,
    val supersededCount: Int,
    val categoryDistribution: Map<String, Int>,
    val severityDistribution: Map<String, Int>,
    val manufacturerDistribution: Map<String, Int>,
    val averageAge: Double,
    val withAttachmentsCount: Int,
    val withDTCsCount: Int
)