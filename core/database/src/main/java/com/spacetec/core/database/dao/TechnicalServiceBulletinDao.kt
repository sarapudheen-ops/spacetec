package com.spacetec.obd.core.database.dao

import androidx.room.*
import com.spacetec.obd.core.database.entities.*
import kotlinx.coroutines.flow.Flow

/**
 * Technical Service Bulletin Data Access Object for TSB management
 */
@Dao
interface TechnicalServiceBulletinDao {
    
    // Basic CRUD operations
    @Query("SELECT * FROM technical_service_bulletins WHERE id = :id AND is_active = 1")
    suspend fun getTSBById(id: String): TechnicalServiceBulletinEntity?
    
    @Query("SELECT * FROM technical_service_bulletins WHERE id = :id AND is_active = 1")
    fun getTSBByIdFlow(id: String): Flow<TechnicalServiceBulletinEntity?>
    
    @Query("SELECT * FROM technical_service_bulletins WHERE bulletin_number = :bulletinNumber AND is_active = 1")
    suspend fun getTSBByBulletinNumber(bulletinNumber: String): TechnicalServiceBulletinEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTSB(tsb: TechnicalServiceBulletinEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTSBs(tsbs: List<TechnicalServiceBulletinEntity>)
    
    @Update
    suspend fun updateTSB(tsb: TechnicalServiceBulletinEntity)
    
    @Delete
    suspend fun deleteTSB(tsb: TechnicalServiceBulletinEntity)
    
    @Query("UPDATE technical_service_bulletins SET is_active = 0 WHERE id = :id")
    suspend fun softDeleteTSB(id: String)
    
    // DTC-related TSBs
    @Query("""
        SELECT * FROM technical_service_bulletins 
        WHERE is_active = 1 
        AND applicable_dtc_codes LIKE '%' || :dtcCode || '%'
        ORDER BY severity DESC, publish_date DESC
    """)
    suspend fun getTSBsForDTC(dtcCode: String): List<TechnicalServiceBulletinEntity>
    
    @Query("""
        SELECT * FROM technical_service_bulletins 
        WHERE is_active = 1 
        AND applicable_dtc_codes LIKE '%' || :dtcCode || '%'
        ORDER BY severity DESC, publish_date DESC
    """)
    fun getTSBsForDTCFlow(dtcCode: String): Flow<List<TechnicalServiceBulletinEntity>>
    
    // Manufacturer-specific TSBs
    @Query("""
        SELECT * FROM technical_service_bulletins 
        WHERE is_active = 1 AND manufacturer = :manufacturer
        ORDER BY severity DESC, publish_date DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getTSBsByManufacturer(manufacturer: String, limit: Int = 50, offset: Int = 0): List<TechnicalServiceBulletinEntity>
    
    // Search operations
    @Query("""
        SELECT * FROM technical_service_bulletins 
        WHERE is_active = 1 
        AND (
            title LIKE '%' || :searchTerm || '%' 
            OR description LIKE '%' || :searchTerm || '%'
            OR bulletin_number LIKE '%' || :searchTerm || '%'
            OR applicable_dtc_codes LIKE '%' || :searchTerm || '%'
        )
        ORDER BY severity DESC, publish_date DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun searchTSBs(searchTerm: String, limit: Int = 50, offset: Int = 0): List<TechnicalServiceBulletinEntity>
    
    // Recent TSBs
    @Query("""
        SELECT * FROM technical_service_bulletins 
        WHERE is_active = 1 
        ORDER BY publish_date DESC 
        LIMIT :limit
    """)
    suspend fun getRecentTSBs(limit: Int = 20): List<TechnicalServiceBulletinEntity>
    
    // Statistics
    @Query("SELECT COUNT(*) FROM technical_service_bulletins WHERE is_active = 1")
    suspend fun getTotalTSBCount(): Int
    
    @Query("SELECT COUNT(*) FROM technical_service_bulletins WHERE is_active = 1 AND manufacturer = :manufacturer")
    suspend fun getTSBCountByManufacturer(manufacturer: String): Int
}