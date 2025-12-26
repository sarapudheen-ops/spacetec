package com.spacetec.core.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "dtc_codes",
    indices = [
        Index(value = ["code"], unique = true),
        Index(value = ["category"]),
        Index(value = ["severity"])
    ]
)
data class DTCEntity(
    @PrimaryKey val id: Long = 0,
    val code: String,
    val description: String,
    val explanation: String,
    val category: String, // P, C, B, U
    val system: String,
    val severity: String, // CRITICAL, HIGH, MEDIUM, LOW, INFORMATIONAL
    val possibleCauses: String, // JSON array
    val symptoms: String, // JSON array
    val diagnosticSteps: String, // JSON array
    val repairCostEstimate: String? = null,
    val isEmissionRelated: Boolean = false,
    val manufacturer: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
