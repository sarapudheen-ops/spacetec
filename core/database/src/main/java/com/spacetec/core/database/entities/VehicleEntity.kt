package com.spacetec.obd.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.spacetec.obd.core.database.converters.DateConverters
import java.time.LocalDateTime

/**
 * Vehicle Entity - placeholder for existing vehicle data
 */
@Entity(tableName = "vehicles")
@TypeConverters(DateConverters::class)
data class VehicleEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    
    @ColumnInfo(name = "vin")
    val vin: String? = null,
    
    @ColumnInfo(name = "make")
    val make: String,
    
    @ColumnInfo(name = "model")
    val model: String,
    
    @ColumnInfo(name = "year")
    val year: Int,
    
    @ColumnInfo(name = "engine_type")
    val engineType: String? = null,
    
    @ColumnInfo(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: LocalDateTime = LocalDateTime.now()
)