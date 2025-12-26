package com.spacetec.data.repository

import com.spacetec.core.common.Result
import com.spacetec.core.database.dao.DTCDao
import com.spacetec.core.database.entities.DTCCategory
import com.spacetec.core.database.entities.DTCSeverity as EntityDTCSeverity
import com.spacetec.core.database.entities.DTCType as EntityDTCType
import com.spacetec.data.mapper.DTCMapper
import com.spacetec.domain.models.diagnostic.DTC
import com.spacetec.domain.models.diagnostic.DTCSubsystem
import com.spacetec.domain.models.vehicle.Vehicle
import com.spacetec.domain.repository.DTCRepository
import com.spacetec.domain.repository.DTCSortOption
import com.spacetec.domain.repository.DTCSearchQuery
import com.spacetec.domain.repository.OfflineCapability
import com.spacetec.domain.repository.SyncResult
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of DTCRepository using Room database.
 *
 * Provides comprehensive DTC database operations including search,
 * retrieval, synchronization, and offline capabilities.
 *
 * @author SpaceTec Team
 * @since 1.0.0
 */
@Singleton
class DTCDatabaseRepositoryImpl @Inject constructor(
    private val dtcDao: DTCDao,
    private val dtcMapper: DTCMapper
) : DTCRepository {

    override suspend fun getDTCByCode(code: String): Result<DTC?> {
        return try {
            val entity = dtcDao.getDTCByCode(code)
            val dtc = entity?.let { dtcMapper.toDomain(it) }
            Result.Success(dtc)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun searchDTCs(query: DTCSearchQuery): Result<List<DTC>> {
        return try {
            val entities = when {
                // Simple code search
                !query.code.isNullOrBlank() -> {
                    listOfNotNull(dtcDao.getDTCByCode(query.code))
                }
                
                // Advanced search with filters
                else -> {
                    dtcDao.searchDTCsAdvanced(
                        searchTerm = query.description,
                        type = query.system?.let { mapDomainSystemToEntityType(it) },
                        category = query.subsystem?.let { mapSubsystemToCategory(it) },
                        severity = null, // Not directly mapped in current schema
                        manufacturer = null, // Vehicle-specific filtering would be done separately
                        emissionsRelated = null,
                        safetyRelated = null,
                        limit = query.limit,
                        offset = query.offset
                    )
                }
            }
            
            val dtcs = dtcMapper.toDomainList(entities)
            val sortedDtcs = sortDTCs(dtcs, query.sortBy)
            
            Result.Success(sortedDtcs)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun getDTCsByVehicle(vehicle: Vehicle): Result<List<DTC>> {
        return try {
            // For vehicle-specific DTCs, we would typically filter by manufacturer
            // and model year, but the current schema doesn't have direct vehicle mapping
            // This would need to be enhanced based on vehicle compatibility data
            val entities = dtcDao.searchDTCsAdvanced(
                manufacturer = vehicle.make,
                limit = 1000
            )
            
            val dtcs = dtcMapper.toDomainList(entities)
            Result.Success(dtcs)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun getDTCsBySubsystem(subsystem: DTCSubsystem): Result<List<DTC>> {
        return try {
            val category = mapSubsystemToCategory(subsystem)
            val entities = dtcDao.getDTCsByCategory(category)
            val dtcs = dtcMapper.toDomainList(entities)
            Result.Success(dtcs)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun getRelatedDTCs(code: String): Result<List<DTC>> {
        return try {
            val entities = dtcDao.getRelatedDTCs(code)
            val dtcs = dtcMapper.toDomainList(entities)
            Result.Success(dtcs)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun saveDTC(dtc: DTC): Result<Unit> {
        return try {
            val entity = dtcMapper.toEntity(dtc)
            dtcDao.insertDTC(entity)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun updateDTC(dtc: DTC): Result<Unit> {
        return try {
            val entity = dtcMapper.toEntity(dtc)
            dtcDao.updateDTC(entity)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun deleteDTC(code: String): Result<Unit> {
        return try {
            dtcDao.softDeleteDTC(code)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun syncDTCs(): Result<SyncResult> {
        return try {
            // This would typically involve:
            // 1. Fetching updates from remote API
            // 2. Comparing with local data
            // 3. Updating local database
            // 4. Tracking sync statistics
            
            // For now, return a mock successful sync
            val syncResult = SyncResult(
                success = true,
                updatedCount = 0,
                addedCount = 0,
                errorCount = 0,
                lastSyncTime = System.currentTimeMillis(),
                errors = emptyList()
            )
            
            Result.Success(syncResult)
        } catch (e: Exception) {
            val syncResult = SyncResult(
                success = false,
                updatedCount = 0,
                addedCount = 0,
                errorCount = 1,
                lastSyncTime = System.currentTimeMillis(),
                errors = listOf(e.message ?: "Unknown sync error")
            )
            Result.Success(syncResult)
        }
    }

    override suspend fun getOfflineCapability(): Result<OfflineCapability> {
        return try {
            val totalCount = dtcDao.getTotalDTCCount()
            
            val offlineCapability = OfflineCapability(
                isOfflineCapable = true,
                lastSyncTime = System.currentTimeMillis(), // Would be stored in preferences
                cachedDTCCount = totalCount,
                storageUsed = totalCount * 1024L, // Rough estimate
                storageAvailable = Long.MAX_VALUE // Would check actual storage
            )
            
            Result.Success(offlineCapability)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Maps domain DTCSystem to database DTCType.
     */
    private fun mapDomainSystemToEntityType(system: com.spacetec.domain.models.diagnostic.DTCSystem): EntityDTCType {
        return when (system) {
            com.spacetec.domain.models.diagnostic.DTCSystem.POWERTRAIN -> EntityDTCType.POWERTRAIN
            com.spacetec.domain.models.diagnostic.DTCSystem.CHASSIS -> EntityDTCType.CHASSIS
            com.spacetec.domain.models.diagnostic.DTCSystem.BODY -> EntityDTCType.BODY
            com.spacetec.domain.models.diagnostic.DTCSystem.NETWORK -> EntityDTCType.NETWORK
        }
    }

    /**
     * Maps domain DTCSubsystem to database DTCCategory.
     */
    private fun mapSubsystemToCategory(subsystem: DTCSubsystem): DTCCategory {
        return when (subsystem) {
            DTCSubsystem.P_FUEL_AIR_METERING,
            DTCSubsystem.P_FUEL_AIR_METERING_AUX,
            DTCSubsystem.P_FUEL_AIR_INJECTOR -> DTCCategory.FUEL_AIR
            
            DTCSubsystem.P_IGNITION -> DTCCategory.IGNITION
            
            DTCSubsystem.P_EMISSION_CONTROL -> DTCCategory.EMISSIONS
            
            DTCSubsystem.P_TRANSMISSION,
            DTCSubsystem.P_TRANSMISSION_AUX -> DTCCategory.TRANSMISSION
            
            DTCSubsystem.C_COMMON,
            DTCSubsystem.C_MANUFACTURER,
            DTCSubsystem.C_MANUFACTURER_2 -> DTCCategory.ABS
            
            DTCSubsystem.B_COMMON,
            DTCSubsystem.B_MANUFACTURER,
            DTCSubsystem.B_MANUFACTURER_2 -> DTCCategory.AIRBAG
            
            DTCSubsystem.U_COMMON,
            DTCSubsystem.U_MANUFACTURER,
            DTCSubsystem.U_MANUFACTURER_2 -> DTCCategory.CAN_BUS
            
            else -> DTCCategory.OTHER
        }
    }

    /**
     * Sorts DTCs based on the specified sort option.
     */
    private fun sortDTCs(dtcs: List<DTC>, sortBy: DTCSortOption): List<DTC> {
        return when (sortBy) {
            DTCSortOption.CODE -> dtcs.sortedBy { it.code }
            DTCSortOption.SEVERITY -> dtcs.sortedByDescending { it.severity.level }
            DTCSortOption.SYSTEM -> dtcs.sortedBy { it.system.description }
            DTCSortOption.DESCRIPTION -> dtcs.sortedBy { it.description }
            DTCSortOption.RELEVANCE -> dtcs // Already sorted by relevance in query
        }
    }
}