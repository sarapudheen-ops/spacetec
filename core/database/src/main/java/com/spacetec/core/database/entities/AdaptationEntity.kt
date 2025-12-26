package com.spacetec.obd.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.spacetec.obd.core.database.converters.DateConverters
import java.time.LocalDateTime

/**
 * Adaptation Entity - placeholder for existing adaptation data
 */
@Entity(tableName = "adaptations")
@TypeConverters(DateConverters::class)
data class AdaptationEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    
    @ColumnInfo(name = "ecu_id")
    val ecuId: String,
    
    @ColumnInfo(name = "channel_name")
    val channelName: String,
    
    @ColumnInfo(name = "value")
    val value: String,
    
    @ColumnInfo(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: LocalDateTime = LocalDateTime.now()
)