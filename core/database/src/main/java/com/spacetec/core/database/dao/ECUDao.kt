package com.spacetec.obd.core.database.dao

import androidx.room.*
import com.spacetec.obd.core.database.entities.ECUEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ECUDao {
    @Query("SELECT * FROM ecus")
    fun getAllECUs(): Flow<List<ECUEntity>>

    @Query("SELECT * FROM ecus WHERE id = :id")
    suspend fun getECUById(id: String): ECUEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertECU(ecu: ECUEntity)

    @Update
    suspend fun updateECU(ecu: ECUEntity)

    @Delete
    suspend fun deleteECU(ecu: ECUEntity)
}