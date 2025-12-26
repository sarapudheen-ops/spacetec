package com.spacetec.obd.core.database.dao

import androidx.room.*
import com.spacetec.obd.core.database.entities.LiveDataLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LiveDataLogDao {
    @Query("SELECT * FROM live_data_logs")
    fun getAllLiveDataLogs(): Flow<List<LiveDataLogEntity>>

    @Query("SELECT * FROM live_data_logs WHERE id = :id")
    suspend fun getLiveDataLogById(id: String): LiveDataLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLiveDataLog(log: LiveDataLogEntity)

    @Update
    suspend fun updateLiveDataLog(log: LiveDataLogEntity)

    @Delete
    suspend fun deleteLiveDataLog(log: LiveDataLogEntity)
}