package com.spacetec.obd.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.spacetec.obd.core.database.converters.DateConverters
import com.spacetec.obd.core.database.converters.ListConverters
import com.spacetec.obd.core.database.converters.EnumConverters
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Technical Service Bulletin Entity for manufacturer guidance and updates
 */
@Entity(
    tableName = "technical_service_bulletins",
    indices = [
        Index(value = ["manufacturer"]),
        Index(value = ["bulletin_number"], unique = true),
        Index(value = ["category"]),
        Index(value = ["severity"]),
        Index(value = ["publish_date"]),
        Index(value = ["is_active"])
    ]
)
@TypeConverters(DateConverters::class, ListConverters::class, EnumConverters::class)
data class TechnicalServiceBulletinEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    
    @ColumnInfo(name = "manufacturer")
    val manufacturer: String,
    
    @ColumnInfo(name = "bulletin_number")
    val bulletinNumber: String,                 // Manufacturer's TSB number
    
    @ColumnInfo(name = "title")
    val title: String,
    
    @ColumnInfo(name = "description")
    val description: String,
    
    @ColumnInfo(name = "detailed_content")
    val detailedContent: String,                // Full TSB content
    
    @ColumnInfo(name = "applicable_vehicles")
    val applicableVehicles: List<VehicleCoverage>,
    
    @ColumnInfo(name = "applicable_dtc_codes")
    val applicableDtcCodes: List<String> = emptyList(),
    
    @ColumnInfo(name = "publish_date")
    val publishDate: LocalDate,
    
    @ColumnInfo(name = "effective_date")
    val effectiveDate: LocalDate? = null,
    
    @ColumnInfo(name = "supersedes")
    val supersedes: List<String> = emptyList(), // Previous TSB numbers this replaces
    
    @ColumnInfo(name = "category")
    val category: TSBCategory,
    
    @ColumnInfo(name = "severity")
    val severity: TSBSeverity,
    
    @ColumnInfo(name = "repair_procedure")
    val repairProcedure: String,
    
    @ColumnInfo(name = "parts_required")
    val partsRequired: List<RequiredPart> = emptyList(),
    
    @ColumnInfo(name = "labor_time_hours")
    val laborTimeHours: Double? = null,
    
    @ColumnInfo(name = "warranty_extension")
    val warrantyExtension: Boolean = false,
    
    @ColumnInfo(name = "warranty_details")
    val warrantyDetails: String? = null,
    
    @ColumnInfo(name = "recall_related")
    val recallRelated: Boolean = false,
    
    @ColumnInfo(name = "recall_number")
    val recallNumber: String? = null,
    
    @ColumnInfo(name = "attachments")
    val attachments: List<TSBAttachment> = emptyList(),
    
    @ColumnInfo(name = "special_tools")
    val specialTools: List<String> = emptyList(),
    
    @ColumnInfo(name = "training_required")
    val trainingRequired: Boolean = false,
    
    @ColumnInfo(name = "certification_level")
    val certificationLevel: String? = null,
    
    // Tracking and analytics
    @ColumnInfo(name = "view_count")
    val viewCount: Int = 0,
    
    @ColumnInfo(name = "success_rate")
    val successRate: Double? = null,
    
    @ColumnInfo(name = "average_repair_time")
    val averageRepairTime: Double? = null,      // Actual average repair time in hours
    
    // Metadata
    @ColumnInfo(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: LocalDateTime = LocalDateTime.now(),
    
    @ColumnInfo(name = "version")
    val version: Int = 1,
    
    @ColumnInfo(name = "source_url")
    val sourceUrl: String? = null,
    
    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true
)

/**
 * Vehicle coverage information for TSBs
 */
data class VehicleCoverage(
    val make: String,
    val model: String,
    val yearStart: Int,
    val yearEnd: Int,
    val engineTypes: List<String> = emptyList(),
    val transmissionTypes: List<String> = emptyList(),
    val trimLevels: List<String> = emptyList(),
    val vinRanges: List<VinRange> = emptyList(),
    val productionDates: List<ProductionDateRange> = emptyList()
)

/**
 * VIN range specification
 */
data class VinRange(
    val startVin: String,
    val endVin: String,
    val notes: String? = null
)

/**
 * Production date range specification
 */
data class ProductionDateRange(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val notes: String? = null
)

/**
 * TSB attachment information
 */
data class TSBAttachment(
    val fileName: String,
    val fileType: String,                       // PDF, IMAGE, VIDEO, etc.
    val description: String,
    val url: String? = null,
    val localPath: String? = null,
    val fileSize: Long? = null
)

/**
 * TSB Category enumeration
 */
enum class TSBCategory {
    SAFETY,
    EMISSIONS,
    PERFORMANCE,
    COMFORT,
    ELECTRICAL,
    ENGINE,
    TRANSMISSION,
    BRAKES,
    SUSPENSION,
    BODY,
    INTERIOR,
    SOFTWARE_UPDATE,
    RECALL_RELATED,
    WARRANTY_EXTENSION,
    OTHER
}

/**
 * TSB Severity levels
 */
enum class TSBSeverity {
    CRITICAL,       // Safety-related, immediate action required
    HIGH,           // Significant impact on vehicle operation
    MEDIUM,         // Moderate impact, should be addressed
    LOW,            // Minor issue, can be addressed during regular service
    INFO            // Informational only
}