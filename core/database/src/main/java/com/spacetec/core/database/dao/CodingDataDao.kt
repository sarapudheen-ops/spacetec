package com.spacetec.obd.core.database.dao

import androidx.room.*
import com.spacetec.obd.core.database.entities.CodingDataEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CodingDataDao {
    @Query("SELECT * FROM coding_data")
    fun getAllCodingData(): Flow<List<CodingDataEntity>>

    @Query("SELECT * FROM coding_data WHERE id = :id")
    suspend fun getCodingDataById(id: String): CodingDataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCodingData(codingData: CodingDataEntity)

    @Update
    suspend fun updateCodingData(codingData: CodingDataEntity)

    @Delete
    suspend fun deleteCodingData(codingData: CodingDataEntity)
}