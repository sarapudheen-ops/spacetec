package com.spacetec.core.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "session_dtcs",
    foreignKeys = [
        ForeignKey(
            entity = DiagnosticSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["sessionId"]),
        Index(value = ["dtcCode"]),
        Index(value = ["status"])
    ]
)
data class SessionDTCEntity(
    @PrimaryKey val id: Long = 0,
    val sessionId: Long,
    val dtcCode: String,
    val status: String, // STORED, PENDING, PERMANENT
    val ecuAddress: String?,
    val freezeFrameData: String?, // JSON
    val timestamp: Long,
    val isCleared: Boolean = false,
    val clearedAt: Long? = null
)
