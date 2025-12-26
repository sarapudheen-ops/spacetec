package com.spacetec.obd.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.spacetec.obd.core.database.converters.DateConverters
import java.time.LocalDateTime

/**
 * Coding Data Entity - placeholder for existing coding data
 */
@Entity(tableName = "coding_data")
@TypeConverters(DateConverters::class)
data class CodingDataEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    
    @ColumnInfo(name = "ecu_id")
    val ecuId: String,
    
    @ColumnInfo(name = "parameter_name")
    val parameterName: String,
    
    @ColumnInfo(name = "value")
    val value: String,
    
    @ColumnInfo(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: LocalDateTime = LocalDateTime.now()
)