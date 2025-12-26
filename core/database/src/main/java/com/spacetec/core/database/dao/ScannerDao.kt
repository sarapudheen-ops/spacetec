package com.spacetec.obd.core.database.dao

import androidx.room.*
import com.spacetec.obd.core.database.entities.ScannerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScannerDao {
    @Query("SELECT * FROM scanners")
    fun getAllScanners(): Flow<List<ScannerEntity>>

    @Query("SELECT * FROM scanners WHERE id = :id")
    suspend fun getScannerById(id: String): ScannerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScanner(scanner: ScannerEntity)

    @Update
    suspend fun updateScanner(scanner: ScannerEntity)

    @Delete
    suspend fun deleteScanner(scanner: ScannerEntity)
}