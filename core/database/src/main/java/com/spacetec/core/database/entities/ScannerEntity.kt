package com.spacetec.obd.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.spacetec.obd.core.database.converters.DateConverters
import java.time.LocalDateTime

/**
 * Scanner Entity - placeholder for existing scanner data
 */
@Entity(tableName = "scanners")
@TypeConverters(DateConverters::class)
data class ScannerEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    
    @ColumnInfo(name = "name")
    val name: String,
    
    @ColumnInfo(name = "type")
    val type: String,
    
    @ColumnInfo(name = "address")
    val address: String,
    
    @ColumnInfo(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: LocalDateTime = LocalDateTime.now()
)