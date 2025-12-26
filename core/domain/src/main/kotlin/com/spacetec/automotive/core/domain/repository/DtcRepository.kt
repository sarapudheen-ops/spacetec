package com.spacetec.obd.core.domain.repository

import com.spacetec.obd.core.common.result.AppResult
import com.spacetec.obd.core.domain.model.dtc.DtcCategory
import com.spacetec.obd.core.domain.model.dtc.DtcCode
import com.spacetec.obd.core.domain.model.dtc.DtcDefinition
import com.spacetec.obd.core.domain.model.dtc.DtcSeverity
import com.spacetec.obd.core.domain.model.dtc.DtcStatus
import com.spacetec.obd.core.domain.model.dtc.FreezeFrame
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for DTC (Diagnostic Trouble Code) operations.
 * 
 * This interface defines the contract for accessing DTC data from
 * both local storage (database) and remote sources (vehicle ECUs).
 * Implementations handle the actual data retrieval and storage.
 */
interface DtcRepository {
    
    // ========================================================================
    // DTC READING OPERATIONS
    // ========================================================================
    
    /**
     * Reads all DTCs from the connected vehicle.
     * 
     * This performs a comprehensive scan across all ECUs using
     * OBD-II services 03, 07, and 0A (or equivalent UDS services).
     * 
     * @return Flow of DTCs as they are read from the vehicle
     */
    fun readAllDtcs(): Flow<AppResult<List<DtcCode>>>
    
    /**
     * Reads stored/confirmed DTCs (OBD-II Service 03, UDS 0x19 01).
     */
    suspend fun readStoredDtcs(): AppResult<List<DtcCode>>
    
    /**
     * Reads pending DTCs (OBD-II Service 07, UDS 0x19 07).
     */
    suspend fun readPendingDtcs(): AppResult<List<DtcCode>>
    
    /**
     * Reads permanent DTCs (OBD-II Service 0A).
     */
    suspend fun readPermanentDtcs(): AppResult<List<DtcCode>>
    
    /**
     * Reads DTCs from a specific ECU.
     * 
     * @param ecuAddress The address of the target ECU
     * @return List of DTCs from that ECU
     */
    suspend fun readDtcsFromEcu(ecuAddress: String): AppResult<List<DtcCode>>
    
    /**
     * Reads DTCs with specific status.
     * 
     * @param status The DTC status to filter by
     * @return List of DTCs matching the status
     */
    suspend fun readDtcsByStatus(status: DtcStatus): AppResult<List<DtcCode>>
    
    // ========================================================================
    // DTC CLEARING OPERATIONS
    // ========================================================================
    
    /**
     * Clears all DTCs from the vehicle (OBD-II Service 04, UDS 0x14).
     * 
     * @param backup Whether to backup DTCs before clearing
     * @return Success or failure result
     */
    suspend fun clearAllDtcs(backup: Boolean = true): AppResult<Unit>
    
    /**
     * Clears DTCs from a specific ECU.
     * 
     * @param ecuAddress The address of the target ECU
     * @param backup Whether to backup DTCs before clearing
     * @return Success or failure result
     */
    suspend fun clearDtcsFromEcu(ecuAddress: String, backup: Boolean = true): AppResult<Unit>
    
    /**
     * Clears specific DTCs by code.
     * 
     * @param dtcCodes List of DTC codes to clear
     * @return Success or failure result
     */
    suspend fun clearSpecificDtcs(dtcCodes: List<String>): AppResult<Unit>
    
    /**
     * Verifies that DTCs were successfully cleared.
     * 
     * @return True if no DTCs remain
     */
    suspend fun verifyClearSuccess(): AppResult<Boolean>
    
    // ========================================================================
    // FREEZE FRAME OPERATIONS
    // ========================================================================
    
    /**
     * Reads freeze frame data for a specific DTC.
     * 
     * @param dtcCode The DTC code to get freeze frame for
     * @return Freeze frame data or null if not available
     */
    suspend fun readFreezeFrame(dtcCode: String): AppResult<FreezeFrame?>
    
    /**
     * Reads all available freeze frames.
     * 
     * @return List of all freeze frames
     */
    suspend fun readAllFreezeFrames(): AppResult<List<FreezeFrame>>
    
    /**
     * Reads extended data records for a DTC (UDS 0x19 06).
     * 
     * @param dtcCode The DTC code
     * @return Extended data as key-value pairs
     */
    suspend fun readExtendedData(dtcCode: String): AppResult<Map<String, Any>>
    
    // ========================================================================
    // DTC DEFINITION LOOKUP
    // ========================================================================
    
    /**
     * Looks up the definition for a DTC code.
     * 
     * @param dtcCode The DTC code to look up
     * @return DTC definition from the database
     */
    suspend fun getDtcDefinition(dtcCode: String): AppResult<DtcDefinition>
    
    /**
     * Searches DTC definitions by text.
     * 
     * @param query Search query string
     * @param limit Maximum number of results
     * @return List of matching DTC definitions
     */
    suspend fun searchDtcDefinitions(query: String, limit: Int = 50): AppResult<List<DtcDefinition>>
    
    /**
     * Gets DTC definitions by category.
     * 
     * @param category The DTC category
     * @return List of definitions in that category
     */
    suspend fun getDtcsByCategory(category: DtcCategory): AppResult<List<DtcDefinition>>
    
    /**
     * Gets DTC definitions by severity.
     * 
     * @param severity The severity level
     * @return List of definitions with that severity
     */
    suspend fun getDtcsBySeverity(severity: DtcSeverity): AppResult<List<DtcDefinition>>
    
    /**
     * Gets manufacturer-specific DTC definitions.
     * 
     * @param manufacturer The manufacturer name
     * @return List of manufacturer-specific definitions
     */
    suspend fun getManufacturerDtcs(manufacturer: String): AppResult<List<DtcDefinition>>
    
    // ========================================================================
    // DTC HISTORY & STORAGE
    // ========================================================================
    
    /**
     * Saves DTCs to local storage.
     * 
     * @param dtcs List of DTCs to save
     * @param sessionId Associated diagnostic session ID
     */
    suspend fun saveDtcs(dtcs: List<DtcCode>, sessionId: Long): AppResult<Unit>
    
    /**
     * Gets DTC history for a vehicle.
     * 
     * @param vehicleId The vehicle ID
     * @return List of historical DTCs
     */
    suspend fun getDtcHistory(vehicleId: Long): AppResult<List<DtcCode>>
    
    /**
     * Gets DTC history for a specific code.
     * 
     * @param vehicleId The vehicle ID
     * @param dtcCode The DTC code
     * @return List of occurrences of this DTC
     */
    suspend fun getDtcCodeHistory(vehicleId: Long, dtcCode: String): AppResult<List<DtcCode>>
    
    /**
     * Observes DTCs in real-time.
     * 
     * @return Flow of DTC updates
     */
    fun observeDtcs(): Flow<List<DtcCode>>
    
    /**
     * Observes pending DTCs for early warning.
     * 
     * @return Flow of pending DTC updates
     */
    fun observePendingDtcs(): Flow<List<DtcCode>>
    
    // ========================================================================
    // DATABASE SYNC
    // ========================================================================
    
    /**
     * Syncs DTC definitions with the server.
     * 
     * @return Number of definitions updated
     */
    suspend fun syncDtcDefinitions(): AppResult<Int>
    
    /**
     * Gets the last sync timestamp.
     */
    suspend fun getLastSyncTimestamp(): Long
    
    /**
     * Checks if database update is available.
     */
    suspend fun isUpdateAvailable(): AppResult<Boolean>
    
    /**
     * Gets total count of DTC definitions in database.
     */
    suspend fun getDtcDefinitionCount(): Int
}