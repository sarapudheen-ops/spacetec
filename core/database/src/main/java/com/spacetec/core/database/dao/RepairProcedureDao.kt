package com.spacetec.obd.core.database.dao

import androidx.room.*
import com.spacetec.obd.core.database.entities.*
import kotlinx.coroutines.flow.Flow

/**
 * Repair Procedure Data Access Object for repair guidance management
 */
@Dao
interface RepairProcedureDao {
    
    // Basic CRUD operations
    @Query("SELECT * FROM repair_procedures WHERE id = :id AND is_active = 1")
    suspend fun getRepairProcedureById(id: String): RepairProcedureEntity?
    
    @Query("SELECT * FROM repair_procedures WHERE id = :id AND is_active = 1")
    fun getRepairProcedureByIdFlow(id: String): Flow<RepairProcedureEntity?>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRepairProcedure(procedure: RepairProcedureEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRepairProcedures(procedures: List<RepairProcedureEntity>)
    
    @Update
    suspend fun updateRepairProcedure(procedure: RepairProcedureEntity)
    
    @Delete
    suspend fun deleteRepairProcedure(procedure: RepairProcedureEntity)
    
    @Query("UPDATE repair_procedures SET is_active = 0 WHERE id = :id")
    suspend fun softDeleteRepairProcedure(id: String)
    
    // DTC-specific procedures
    @Query("""
        SELECT * FROM repair_procedures 
        WHERE dtc_code = :dtcCode AND is_active = 1
        ORDER BY success_rate DESC, difficulty ASC, estimated_time_minutes ASC
    """)
    suspend fun getProceduresForDTC(dtcCode: String): List<RepairProcedureEntity>
    
    @Query("""
        SELECT * FROM repair_procedures 
        WHERE dtc_code = :dtcCode AND is_active = 1
        ORDER BY success_rate DESC, difficulty ASC, estimated_time_minutes ASC
    """)
    fun getProceduresForDTCFlow(dtcCode: String): Flow<List<RepairProcedureEntity>>
    
    // Search operations
    @Query("""
        SELECT * FROM repair_procedures 
        WHERE is_active = 1 
        AND (
            title LIKE '%' || :searchTerm || '%' 
            OR description LIKE '%' || :searchTerm || '%'
            OR dtc_code LIKE '%' || :searchTerm || '%'
        )
        ORDER BY success_rate DESC, difficulty ASC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun searchRepairProcedures(searchTerm: String, limit: Int = 50, offset: Int = 0): List<RepairProcedureEntity>
    
    // Success rate and effectiveness tracking
    @Query("""
        UPDATE repair_procedures 
        SET 
            times_used = times_used + 1,
            successful_repairs = CASE WHEN :successful THEN successful_repairs + 1 ELSE successful_repairs END,
            failed_repairs = CASE WHEN :successful THEN failed_repairs ELSE failed_repairs + 1 END,
            success_rate = CASE 
                WHEN (times_used + 1) > 0 
                THEN CAST((successful_repairs + CASE WHEN :successful THEN 1 ELSE 0 END) AS REAL) / (times_used + 1)
                ELSE 0.0 
            END,
            average_completion_time = CASE 
                WHEN :actualTimeMinutes IS NOT NULL AND (times_used + 1) > 0
                THEN CAST((COALESCE(average_completion_time, 0) * times_used + :actualTimeMinutes) AS REAL) / (times_used + 1)
                ELSE average_completion_time
            END,
            updated_at = :timestamp
        WHERE id = :procedureId
    """)
    suspend fun updateProcedureOutcome(
        procedureId: String, 
        successful: Boolean, 
        actualTimeMinutes: Int? = null,
        timestamp: String
    )
    
    // Statistics
    @Query("SELECT COUNT(*) FROM repair_procedures WHERE is_active = 1")
    suspend fun getTotalProcedureCount(): Int
    
    @Query("SELECT AVG(success_rate) FROM repair_procedures WHERE is_active = 1 AND times_used > 0")
    suspend fun getAverageSuccessRate(): Double?
}