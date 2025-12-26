package com.spacetec.obd.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.spacetec.obd.core.database.converters.DateConverters
import java.time.LocalDateTime

/**
 * Key Entity - placeholder for existing key programming data
 */
@Entity(tableName = "keys")
@TypeConverters(DateConverters::class)
data class KeyEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    
    @ColumnInfo(name = "vehicle_id")
    val vehicleId: String,
    
    @ColumnInfo(name = "key_type")
    val keyType: String,
    
    @ColumnInfo(name = "key_data")
    val keyData: String,
    
    @ColumnInfo(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: LocalDateTime = LocalDateTime.now()
)