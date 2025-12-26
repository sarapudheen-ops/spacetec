package com.spacetec.core.database.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDTCDao {
    
    @Query("SELECT * FROM session_dtcs WHERE sessionId = :sessionId")
    suspend fun getDTCsForSession(sessionId: Long): List<SessionDTCEntity>
    
    @Query("SELECT * FROM session_dtcs WHERE sessionId = :sessionId")
    fun getDTCsForSessionFlow(sessionId: Long): Flow<List<SessionDTCEntity>>
    
    @Query("SELECT * FROM session_dtcs WHERE dtcCode = :code ORDER BY timestamp DESC")
    suspend fun getSessionsForDTC(code: String): List<SessionDTCEntity>
    
    @Query("SELECT * FROM session_dtcs WHERE status = :status")
    suspend fun getDTCsByStatus(status: String): List<SessionDTCEntity>
    
    @Query("SELECT COUNT(*) FROM session_dtcs WHERE sessionId = :sessionId AND isCleared = 0")
    suspend fun getActiveDTCCount(sessionId: Long): Int
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessionDTC(sessionDTC: SessionDTCEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessionDTCs(sessionDTCs: List<SessionDTCEntity>)
    
    @Update
    suspend fun updateSessionDTC(sessionDTC: SessionDTCEntity)
    
    @Query("UPDATE session_dtcs SET isCleared = 1, clearedAt = :clearedAt WHERE sessionId = :sessionId")
    suspend fun clearAllDTCsForSession(sessionId: Long, clearedAt: Long)
    
    @Query("UPDATE session_dtcs SET isCleared = 1, clearedAt = :clearedAt WHERE id = :id")
    suspend fun clearDTC(id: Long, clearedAt: Long)
}
