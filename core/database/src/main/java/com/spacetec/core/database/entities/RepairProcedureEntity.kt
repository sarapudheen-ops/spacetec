package com.spacetec.obd.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.spacetec.obd.core.database.converters.DateConverters
import com.spacetec.obd.core.database.converters.ListConverters
import com.spacetec.obd.core.database.converters.EnumConverters
import java.time.LocalDateTime

/**
 * Repair Procedure Entity for detailed repair guidance and procedures
 */
@Entity(
    tableName = "repair_procedures",
    indices = [
        Index(value = ["dtc_code"]),
        Index(value = ["difficulty"]),
        Index(value = ["success_rate"]),
        Index(value = ["estimated_time_minutes"]),
        Index(value = ["is_active"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = DTCEntity::class,
            parentColumns = ["code"],
            childColumns = ["dtc_code"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
@TypeConverters(DateConverters::class, ListConverters::class, EnumConverters::class)
data class RepairProcedureEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    
    @ColumnInfo(name = "dtc_code")
    val dtcCode: String,                        // Foreign key to DTCEntity
    
    @ColumnInfo(name = "title")
    val title: String,
    
    @ColumnInfo(name = "description")
    val description: String,
    
    @ColumnInfo(name = "steps")
    val steps: List<RepairStep>,                // Detailed repair steps
    
    @ColumnInfo(name = "difficulty")
    val difficulty: RepairDifficulty,
    
    @ColumnInfo(name = "estimated_time_minutes")
    val estimatedTimeMinutes: Int,
    
    @ColumnInfo(name = "required_tools")
    val requiredTools: List<String> = emptyList(),
    
    @ColumnInfo(name = "required_parts")
    val requiredParts: List<RequiredPart> = emptyList(),
    
    @ColumnInfo(name = "success_rate")
    val successRate: Double = 0.0,              // Success rate (0.0 to 1.0)
    
    @ColumnInfo(name = "labor_cost_min")
    val laborCostMin: Double? = null,
    
    @ColumnInfo(name = "labor_cost_max")
    val laborCostMax: Double? = null,
    
    @ColumnInfo(name = "parts_cost_min")
    val partsCostMin: Double? = null,
    
    @ColumnInfo(name = "parts_cost_max")
    val partsCostMax: Double? = null,
    
    @ColumnInfo(name = "manufacturer")
    val manufacturer: String? = null,           // Specific manufacturer procedure
    
    @ColumnInfo(name = "model_years")
    val modelYears: String? = null,             // Applicable model years (e.g., "2015-2020")
    
    @ColumnInfo(name = "engine_types")
    val engineTypes: List<String> = emptyList(),
    
    @ColumnInfo(name = "safety_precautions")
    val safetyPrecautions: List<String> = emptyList(),
    
    @ColumnInfo(name = "special_notes")
    val specialNotes: String? = null,
    
    @ColumnInfo(name = "warranty_implications")
    val warrantyImplications: String? = null,
    
    // Tracking fields
    @ColumnInfo(name = "times_used")
    val timesUsed: Int = 0,
    
    @ColumnInfo(name = "successful_repairs")
    val successfulRepairs: Int = 0,
    
    @ColumnInfo(name = "failed_repairs")
    val failedRepairs: Int = 0,
    
    @ColumnInfo(name = "average_completion_time")
    val averageCompletionTime: Int? = null,     // Average actual completion time in minutes
    
    // Metadata
    @ColumnInfo(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: LocalDateTime = LocalDateTime.now(),
    
    @ColumnInfo(name = "version")
    val version: Int = 1,
    
    @ColumnInfo(name = "source")
    val source: String,                         // MANUFACTURER, AFTERMARKET, COMMUNITY
    
    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true
)

/**
 * Individual repair step with detailed instructions
 */
data class RepairStep(
    val stepNumber: Int,
    val title: String,
    val description: String,
    val estimatedTimeMinutes: Int,
    val requiredTools: List<String> = emptyList(),
    val safetyWarnings: List<String> = emptyList(),
    val images: List<String> = emptyList(),      // Image URLs or resource IDs
    val videoUrl: String? = null,
    val notes: String? = null
)

/**
 * Required part information for repair procedures
 */
data class RequiredPart(
    val partNumber: String,
    val partName: String,
    val description: String,
    val manufacturer: String? = null,
    val estimatedCost: Double? = null,
    val isOEM: Boolean = true,
    val alternatives: List<String> = emptyList(), // Alternative part numbers
    val notes: String? = null
)