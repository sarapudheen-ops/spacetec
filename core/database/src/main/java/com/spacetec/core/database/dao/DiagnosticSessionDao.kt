package com.spacetec.core.database.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DiagnosticSessionDao {
    
    @Query("SELECT * FROM diagnostic_sessions ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<DiagnosticSessionEntity>>
    
    @Query("SELECT * FROM diagnostic_sessions WHERE id = :id")
    suspend fun getSessionById(id: Long): DiagnosticSessionEntity?
    
    @Query("SELECT * FROM diagnostic_sessions WHERE vehicleVin = :vin ORDER BY timestamp DESC")
    suspend fun getSessionsByVin(vin: String): List<DiagnosticSessionEntity>
    
    @Query("SELECT * FROM diagnostic_sessions ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentSessions(limit: Int = 10): List<DiagnosticSessionEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: DiagnosticSessionEntity): Long
    
    @Update
    suspend fun updateSession(session: DiagnosticSessionEntity)
    
    @Delete
    suspend fun deleteSession(session: DiagnosticSessionEntity)
    
    @Query("DELETE FROM diagnostic_sessions WHERE timestamp < :cutoffTime")
    suspend fun deleteOldSessions(cutoffTime: Long)
}
