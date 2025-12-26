package com.spacetec.domain.repository

import com.spacetec.domain.models.diagnostic.DTC
import com.spacetec.domain.models.diagnostic.DTCSystem
import com.spacetec.domain.models.diagnostic.DTCSubsystem
import com.spacetec.domain.models.vehicle.Vehicle

/**
 * Query parameters for searching DTCs.
 * Supports comprehensive filtering, sorting, and pagination.
 */
data class DTCSearchQuery(
    val code: String? = null,
    val description: String? = null,
    val vehicle: Vehicle? = null,
    val system: DTCSystem? = null,
    val subsystem: DTCSubsystem? = null,
    val category: DTCCategory? = null,
    val severity: DTCSeverity? = null,
    val symptoms: List<String> = emptyList(),
    val systems: List<String> = emptyList(),
    val includeManufacturerSpecific: Boolean = true,
    val sortBy: DTCSortOption = DTCSortOption.RELEVANCE,
    val limit: Int = 50,
    val offset: Int = 0
)

/**
 * Result of a DTC search operation.
 */
data class DTCSearchResult(
    val dtcs: List<DTC>,
    val totalCount: Int,
    val hasMore: Boolean,
    val query: DTCSearchQuery
)

/**
 * Sort options for DTC search results.
 */
enum class DTCSortOption {
    RELEVANCE, CODE, SEVERITY, SYSTEM, DESCRIPTION
}

/**
 * DTC category for filtering.
 */
enum class DTCCategory {
    POWERTRAIN, CHASSIS, BODY, NETWORK, EMISSION, FUEL, IGNITION, TRANSMISSION
}

/**
 * DTC severity levels.
 */
enum class DTCSeverity {
    CRITICAL, HIGH, MEDIUM, LOW, INFORMATIONAL
}

/**
 * Result of a database synchronization operation.
 */
data class SyncResult(
    val success: Boolean,
    val itemsSynced: Int,
    val itemsAdded: Int,
    val itemsUpdated: Int,
    val itemsDeleted: Int,
    val conflicts: List<SyncConflict>,
    val timestamp: Long,
    val errorMessage: String? = null
)

/**
 * Represents a conflict detected during synchronization.
 */
data class SyncConflict(
    val code: String,
    val localVersion: Long,
    val remoteVersion: Long,
    val conflictType: ConflictType,
    val resolution: ConflictResolution? = null
)

enum class ConflictType {
    VERSION_MISMATCH, DATA_INCONSISTENCY, DELETED_LOCALLY, DELETED_REMOTELY, DUPLICATE_ENTRY
}

enum class ConflictResolution {
    USE_LOCAL, USE_REMOTE, MERGE, SKIP
}

/**
 * Describes the offline capabilities of the DTC system.
 */
data class OfflineCapability(
    val isFullyAvailable: Boolean,
    val availableDTCCount: Int,
    val availableRepairProcedureCount: Int,
    val availableTSBCount: Int,
    val lastSyncTimestamp: Long?,
    val storageSizeBytes: Long,
    val vehicleCoverage: List<VehicleCoverageInfo>
)

/**
 * Information about vehicle coverage in the offline database.
 */
data class VehicleCoverageInfo(
    val manufacturer: String,
    val models: List<String>,
    val yearRange: IntRange,
    val dtcCount: Int
)

/**
 * Options for DTC database synchronization.
 */
data class SyncOptions(
    val forceFullSync: Boolean = false,
    val includeManufacturerData: Boolean = true,
    val includeTSBs: Boolean = true,
    val vehicleCoverage: List<String>? = null,
    val conflictResolution: ConflictResolution = ConflictResolution.USE_REMOTE
)

/**
 * Record of a DTC occurrence in history.
 */
data class DTCHistoryRecord(
    val code: String,
    val vehicleId: String,
    val timestamp: Long,
    val status: DTCStatus,
    val mileage: Int?,
    val freezeFrameData: Map<String, String>?,
    val diagnosticSessionId: String?
)

enum class DTCStatus {
    ACTIVE, PENDING, PERMANENT, HISTORY
}

/**
 * Result of DTC code validation.
 */
data class DTCValidationResult(
    val isValid: Boolean,
    val code: String,
    val normalizedCode: String?,
    val errors: List<ValidationError>,
    val warnings: List<ValidationWarning>
)

data class ValidationError(
    val field: String,
    val message: String,
    val errorCode: String
)

data class ValidationWarning(
    val field: String,
    val message: String,
    val warningCode: String
)

/**
 * Repository interface for DTC data operations.
 * Provides comprehensive search, CRUD, and synchronization capabilities.
 */
interface DTCRepository {

    /**
     * Retrieves a DTC by its code.
     */
    suspend fun getDTCByCode(code: String): Result<DTC?>

    /**
     * Retrieves a DTC with vehicle-specific context.
     */
    suspend fun getDTCByCodeForVehicle(code: String, vehicle: Vehicle): Result<DTC?>

    /**
     * Searches DTCs based on comprehensive query parameters.
     */
    suspend fun searchDTCs(query: DTCSearchQuery): Result<DTCSearchResult>

    /**
     * Retrieves all DTCs applicable to a specific vehicle.
     */
    suspend fun getDTCsByVehicle(vehicle: Vehicle): Result<List<DTC>>

    /**
     * Retrieves all DTCs by subsystem.
     */
    suspend fun getDTCsBySubsystem(subsystem: DTCSubsystem): Result<List<DTC>>

    /**
     * Retrieves all DTCs in a specific category.
     */
    suspend fun getDTCsByCategory(category: DTCCategory): Result<List<DTC>>

    /**
     * Retrieves all DTCs related to a specific code.
     */
    suspend fun getRelatedDTCs(code: String): Result<List<DTC>>

    /**
     * Retrieves the history of a DTC for a specific vehicle.
     */
    suspend fun getDTCHistory(code: String, vehicleId: String): Result<List<DTCHistoryRecord>>

    /**
     * Retrieves all DTCs with their variants for a given code.
     */
    suspend fun getDTCVariants(code: String): Result<List<DTC>>

    /**
     * Saves a new DTC to the database.
     */
    suspend fun saveDTC(dtc: DTC): Result<Unit>

    /**
     * Updates an existing DTC in the database.
     */
    suspend fun updateDTC(dtc: DTC): Result<Unit>

    /**
     * Deletes a DTC from the database.
     */
    suspend fun deleteDTC(code: String): Result<Unit>

    /**
     * Synchronizes the DTC database with remote sources.
     */
    suspend fun syncDTCs(options: SyncOptions = SyncOptions()): Result<SyncResult>

    /**
     * Gets the current offline capability status.
     */
    suspend fun getOfflineCapability(): Result<OfflineCapability>

    /**
     * Validates a DTC code format.
     */
    suspend fun validateDTCCode(code: String): Result<DTCValidationResult>
}
