package com.spacetec.core.database.repository

import com.spacetec.core.database.dao.DiagnosticSessionDao
import com.spacetec.core.database.dao.SessionDTCDao
import com.spacetec.core.database.entities.DiagnosticSessionEntity
import com.spacetec.core.database.entities.SessionDTCEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiagnosticSessionRepository @Inject constructor(
    private val sessionDao: DiagnosticSessionDao,
    private val sessionDTCDao: SessionDTCDao
) {
    fun getAllSessions(): Flow<List<DiagnosticSessionEntity>> = sessionDao.getAllSessions()
    
    suspend fun getSessionById(id: Long): DiagnosticSessionEntity? = sessionDao.getSessionById(id)
    
    suspend fun getRecentSessions(limit: Int = 10): List<DiagnosticSessionEntity> = 
        sessionDao.getRecentSessions(limit)
    
    suspend fun insertSession(session: DiagnosticSessionEntity): Long = sessionDao.insertSession(session)
    
    suspend fun updateSession(session: DiagnosticSessionEntity) = sessionDao.updateSession(session)
    
    suspend fun getDTCsForSession(sessionId: Long): List<SessionDTCEntity> = 
        sessionDTCDao.getDTCsForSession(sessionId)
    
    fun getDTCsForSessionFlow(sessionId: Long): Flow<List<SessionDTCEntity>> = 
        sessionDTCDao.getDTCsForSessionFlow(sessionId)
    
    suspend fun insertSessionDTCs(sessionDTCs: List<SessionDTCEntity>) = 
        sessionDTCDao.insertSessionDTCs(sessionDTCs)
    
    suspend fun clearAllDTCsForSession(sessionId: Long) = 
        sessionDTCDao.clearAllDTCsForSession(sessionId, System.currentTimeMillis())
    
    suspend fun getActiveDTCCount(sessionId: Long): Int = sessionDTCDao.getActiveDTCCount(sessionId)
}
