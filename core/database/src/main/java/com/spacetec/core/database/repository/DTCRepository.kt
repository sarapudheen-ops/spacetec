package com.spacetec.core.database.repository

import com.spacetec.core.database.dao.DTCDao
import com.spacetec.core.database.entities.DTCEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DTCRepository @Inject constructor(
    private val dtcDao: DTCDao
) {
    suspend fun getDTCByCode(code: String): DTCEntity? = dtcDao.getDTCByCode(code)
    
    suspend fun searchDTCs(query: String): List<DTCEntity> = dtcDao.searchDTCsFullText(query)
    
    suspend fun getDTCsByCategory(category: String): List<DTCEntity> = dtcDao.getDTCsByCategory(category)
    
    suspend fun getDTCsBySeverity(severity: String): List<DTCEntity> = dtcDao.getDTCsBySeverity(severity)
    
    suspend fun insertDTC(dtc: DTCEntity): Long = dtcDao.insertDTC(dtc)
    
    suspend fun insertDTCs(dtcs: List<DTCEntity>) = dtcDao.insertDTCs(dtcs)
    
    suspend fun getDTCCount(): Int = dtcDao.getDTCCount()
}
