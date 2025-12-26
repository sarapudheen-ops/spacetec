package com.spacetec.core.database.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.spacetec.core.database.entities.DTCEntity

@Dao
interface DTCDao {
    
    @Query("SELECT * FROM dtc_codes WHERE code = :code LIMIT 1")
    suspend fun getDTCByCode(code: String): DTCEntity?
    
    @Query("SELECT * FROM dtc_codes WHERE code LIKE :pattern")
    suspend fun searchDTCs(pattern: String): List<DTCEntity>
    
    @Query("SELECT * FROM dtc_codes WHERE category = :category")
    suspend fun getDTCsByCategory(category: String): List<DTCEntity>
    
    @Query("SELECT * FROM dtc_codes WHERE severity = :severity")
    suspend fun getDTCsBySeverity(severity: String): List<DTCEntity>
    
    @Query("""
        SELECT * FROM dtc_codes 
        WHERE description LIKE '%' || :query || '%' 
        OR explanation LIKE '%' || :query || '%'
        OR code LIKE '%' || :query || '%'
        LIMIT :limit
    """)
    suspend fun searchDTCsFullText(query: String, limit: Int = 50): List<DTCEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDTC(dtc: DTCEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDTCs(dtcs: List<DTCEntity>)
    
    @Update
    suspend fun updateDTC(dtc: DTCEntity)
    
    @Delete
    suspend fun deleteDTC(dtc: DTCEntity)
    
    @Query("SELECT COUNT(*) FROM dtc_codes")
    suspend fun getDTCCount(): Int
}
