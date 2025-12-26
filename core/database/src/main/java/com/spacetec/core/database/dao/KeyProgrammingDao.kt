package com.spacetec.obd.core.database.dao

import androidx.room.*
import com.spacetec.obd.core.database.entities.KeyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KeyProgrammingDao {
    @Query("SELECT * FROM keys")
    fun getAllKeys(): Flow<List<KeyEntity>>

    @Query("SELECT * FROM keys WHERE id = :id")
    suspend fun getKeyById(id: String): KeyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKey(key: KeyEntity)

    @Update
    suspend fun updateKey(key: KeyEntity)

    @Delete
    suspend fun deleteKey(key: KeyEntity)
}