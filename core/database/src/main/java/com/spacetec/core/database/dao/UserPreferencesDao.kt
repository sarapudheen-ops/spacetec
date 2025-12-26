package com.spacetec.obd.core.database.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// Placeholder DAO for user preferences
@Dao
interface UserPreferencesDao {
    @Query("SELECT 1") // Placeholder query
    suspend fun placeholder(): Int
}