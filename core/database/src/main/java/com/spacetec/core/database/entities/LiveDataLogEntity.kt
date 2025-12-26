package com.spacetec.obd.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.spacetec.obd.core.database.converters.DateConverters
import java.time.LocalDateTime

/**
 * Live Data Log Entity - placeholder for existing live data logging
 */
@Entity(tableName = "live_data_logs")
@TypeConverters(DateConverters::class)
data class LiveDataLogEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    
    @ColumnInfo(name = "vehicle_id")
    val vehicleId: String,
    
    @ColumnInfo(name = "parameter_name")
    val parameterName: String,
    
    @ColumnInfo(name = "value")
    val value: String,
    
    @ColumnInfo(name = "unit")
    val unit: String? = null,
    
    @ColumnInfo(name = "timestamp")
    val timestamp: LocalDateTime = LocalDateTime.now(),
    
    @ColumnInfo(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now()
)