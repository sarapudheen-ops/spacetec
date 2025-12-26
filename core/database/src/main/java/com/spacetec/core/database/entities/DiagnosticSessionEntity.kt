package com.spacetec.core.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "diagnostic_sessions",
    indices = [
        Index(value = ["vehicleVin"]),
        Index(value = ["timestamp"])
    ]
)
data class DiagnosticSessionEntity(
    @PrimaryKey val id: Long = 0,
    val vehicleVin: String?,
    val vehicleMake: String?,
    val vehicleModel: String?,
    val vehicleYear: Int?,
    val scannerType: String,
    val protocolUsed: String,
    val timestamp: Long,
    val duration: Long,
    val dtcCount: Int,
    val status: String, // COMPLETED, FAILED, IN_PROGRESS
    val notes: String? = null
)
