package com.spacetec.domain.models.diagnostic

import java.io.Serializable
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Technical Service Bulletin domain model.
 *
 * Represents manufacturer-issued technical service bulletins that provide
 * repair guidance, known issues, and service procedures for specific vehicles.
 *
 * @property id Unique TSB identifier
 * @property manufacturer Vehicle manufacturer
 * @property bulletinNumber Official bulletin number
 * @property title TSB title
 * @property description Detailed description
 * @property applicableVehicles List of applicable vehicles
 * @property publishDate Publication date
 * @property category TSB category
 * @property severity TSB severity level
 * @property repairProcedure Associated repair procedure text
 * @property attachments List of attachments
 * @property dtcCodes Related DTC codes
 * @property symptoms Related symptoms
 * @property rootCause Root cause analysis
 * @property supersedes Previous TSB numbers this supersedes
 * @property supersededBy TSB number that supersedes this one
 * @property status Current TSB status
 * @property estimatedRepairTime Estimated repair time
 * @property laborRate Labor rate information
 * @property warrantyInfo Warranty coverage information
 * @property lastUpdated Last update timestamp
 *
 * @author SpaceTec Team
 * @since 1.0.0
 */
data class TechnicalServiceBulletin(
    val id: String,
    val manufacturer: String,
    val bulletinNumber: String,
    val title: String,
    val description: String,
    val applicableVehicles: List<VehicleCoverage>,
    val publishDate: Long,
    val category: TSBCategory,
    val severity: TSBSeverity,
    val repairProcedure: String,
    val attachments: List<Attachment> = emptyList(),
    val dtcCodes: List<String> = emptyList(),
    val symptoms: List<String> = emptyList(),
    val rootCause: String? = null,
    val supersedes: List<String> = emptyList(),
    val supersededBy: String? = null,
    val status: TSBStatus = TSBStatus.ACTIVE,
    val estimatedRepairTime: String? = null,
    val laborRate: String? = null,
    val warrantyInfo: String? = null,
    val lastUpdated: Long = System.currentTimeMillis()
) : Serializable {

    /**
     * Formatted publish date.
     */
    val formattedPublishDate: String
        get() = DateTimeFormatter.ofPattern("MMM dd, yyyy")
            .format(LocalDate.ofEpochDay(publishDate / (24 * 60 * 60 * 1000)))

    /**
     * Short publish date.
     */
    val shortPublishDate: String
        get() = DateTimeFormatter.ofPattern("MM/dd/yyyy")
            .format(LocalDate.ofEpochDay(publishDate / (24 * 60 * 60 * 1000)))

    /**
     * Whether this TSB is currently active.
     */
    val isActive: Boolean
        get() = status == TSBStatus.ACTIVE

    /**
     * Whether this TSB has been superseded.
     */
    val isSuperseded: Boolean
        get() = supersededBy != null || status == TSBStatus.SUPERSEDED

    /**
     * Whether this TSB has attachments.
     */
    val hasAttachments: Boolean
        get() = attachments.isNotEmpty()

    /**
     * Whether this TSB relates to specific DTCs.
     */
    val hasDTCs: Boolean
        get() = dtcCodes.isNotEmpty()

    /**
     * Number of applicable vehicle models.
     */
    val vehicleCount: Int
        get() = applicableVehicles.size

    /**
     * Checks if this TSB applies to a specific vehicle.
     */
    fun appliesToVehicle(make: String, model: String, year: Int): Boolean {
        return manufacturer.equals(make, ignoreCase = true) &&
                applicableVehicles.any { it.matches(make, model, year) }
    }

    /**
     * Checks if this TSB relates to a specific DTC.
     */
    fun relatesToDTC(dtcCode: String): Boolean {
        return dtcCodes.contains(dtcCode)
    }

    /**
     * Gets a summary of applicable vehicles.
     */
    fun getVehicleSummary(): String {
        if (applicableVehicles.isEmpty()) return "All $manufacturer vehicles"
        
        val modelGroups = applicableVehicles.groupBy { it.model }
        return when {
            modelGroups.size == 1 -> {
                val model = modelGroups.keys.first()
                val years = applicableVehicles.map { "${it.yearStart}-${it.yearEnd}" }.distinct()
                "$manufacturer $model (${years.joinToString(", ")})"
            }
            modelGroups.size <= 3 -> {
                modelGroups.keys.filterNotNull().joinToString(", ") { "$manufacturer $it" }
            }
            else -> "$manufacturer (${modelGroups.size} models)"
        }
    }

    /**
     * Gets the priority for sorting TSBs.
     */
    val priority: Int
        get() {
            var priority = 0
            
            // Higher priority for active TSBs
            if (isActive) priority += 100
            
            // Higher priority based on severity
            priority += severity.level * 10
            
            // Higher priority for recent TSBs
            val daysSincePublish = (System.currentTimeMillis() - publishDate) / (24 * 60 * 60 * 1000)
            if (daysSincePublish < 365) priority += (365 - daysSincePublish).toInt() / 10
            
            return priority
        }

    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * TSB categories for classification.
 */
enum class TSBCategory(
    val displayName: String,
    val description: String,
    val icon: String
) {
    RECALL(
        displayName = "Recall",
        description = "Safety recall information",
        icon = "warning"
    ),
    SERVICE_CAMPAIGN(
        displayName = "Service Campaign", 
        description = "Manufacturer service campaign",
        icon = "campaign"
    ),
    TECHNICAL_UPDATE(
        displayName = "Technical Update",
        description = "Technical information update",
        icon = "update"
    ),
    REPAIR_PROCEDURE(
        displayName = "Repair Procedure",
        description = "Specific repair procedure",
        icon = "build"
    ),
    DIAGNOSTIC_PROCEDURE(
        displayName = "Diagnostic Procedure",
        description = "Diagnostic testing procedure", 
        icon = "diagnostic"
    ),
    SOFTWARE_UPDATE(
        displayName = "Software Update",
        description = "ECU software update information",
        icon = "software"
    ),
    PARTS_INFORMATION(
        displayName = "Parts Information",
        description = "Parts availability and updates",
        icon = "parts"
    ),
    WARRANTY_EXTENSION(
        displayName = "Warranty Extension",
        description = "Extended warranty coverage",
        icon = "warranty"
    );

    /**
     * Whether this category indicates a safety issue.
     */
    val isSafetyRelated: Boolean
        get() = this == RECALL
}

/**
 * TSB severity levels.
 */
enum class TSBSeverity(
    val displayName: String,
    val description: String,
    val level: Int,
    val color: String
) {
    CRITICAL(
        displayName = "Critical",
        description = "Critical safety issue requiring immediate attention",
        level = 4,
        color = "red"
    ),
    HIGH(
        displayName = "High",
        description = "High priority issue affecting vehicle operation",
        level = 3,
        color = "orange"
    ),
    MEDIUM(
        displayName = "Medium", 
        description = "Medium priority issue with moderate impact",
        level = 2,
        color = "yellow"
    ),
    LOW(
        displayName = "Low",
        description = "Low priority informational update",
        level = 1,
        color = "green"
    ),
    INFORMATIONAL(
        displayName = "Informational",
        description = "Informational notice with no immediate action required",
        level = 0,
        color = "blue"
    );

    /**
     * Whether this severity requires immediate attention.
     */
    val requiresImmediateAttention: Boolean
        get() = this in listOf(CRITICAL, HIGH)
}

/**
 * TSB status indicating current state.
 */
enum class TSBStatus(
    val displayName: String,
    val description: String
) {
    ACTIVE("Active", "Currently active and applicable"),
    SUPERSEDED("Superseded", "Replaced by a newer TSB"),
    WITHDRAWN("Withdrawn", "Withdrawn by manufacturer"),
    EXPIRED("Expired", "No longer applicable");

    /**
     * Whether this status indicates the TSB is still valid.
     */
    val isValid: Boolean
        get() = this == ACTIVE
}

/**
 * Attachment information for TSB documents.
 *
 * @property id Attachment identifier
 * @property filename Original filename
 * @property type File type/MIME type
 * @property size File size in bytes
 * @property url Download URL
 * @property description Attachment description
 * @property category Attachment category
 */
data class Attachment(
    val id: String,
    val filename: String,
    val type: String,
    val size: Long,
    val url: String? = null,
    val description: String? = null,
    val category: AttachmentCategory = AttachmentCategory.DOCUMENT
) : Serializable {

    /**
     * Formatted file size for display.
     */
    val formattedSize: String
        get() = when {
            size < 1024 -> "${size}B"
            size < 1024 * 1024 -> "${size / 1024}KB"
            size < 1024 * 1024 * 1024 -> "${size / (1024 * 1024)}MB"
            else -> "${size / (1024 * 1024 * 1024)}GB"
        }

    /**
     * File extension from filename.
     */
    val extension: String
        get() = filename.substringAfterLast('.', "")

    /**
     * Whether this is an image attachment.
     */
    val isImage: Boolean
        get() = type.startsWith("image/") || extension.lowercase() in listOf("jpg", "jpeg", "png", "gif", "bmp")

    /**
     * Whether this is a PDF document.
     */
    val isPDF: Boolean
        get() = type == "application/pdf" || extension.lowercase() == "pdf"

    /**
     * Display name for the attachment.
     */
    val displayName: String
        get() = description ?: filename

    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * Attachment categories for organization.
 */
enum class AttachmentCategory(
    val displayName: String,
    val icon: String
) {
    DOCUMENT("Document", "document"),
    IMAGE("Image", "image"),
    DIAGRAM("Diagram", "diagram"),
    PROCEDURE("Procedure", "procedure"),
    PARTS_LIST("Parts List", "parts"),
    WIRING_DIAGRAM("Wiring Diagram", "wiring"),
    OTHER("Other", "attachment")
}

// Extension functions for TSB collections

/**
 * Filters TSBs by category.
 */
fun List<TechnicalServiceBulletin>.filterByCategory(category: TSBCategory): List<TechnicalServiceBulletin> {
    return filter { it.category == category }
}

/**
 * Filters TSBs by severity.
 */
fun List<TechnicalServiceBulletin>.filterBySeverity(minSeverity: TSBSeverity): List<TechnicalServiceBulletin> {
    return filter { it.severity.level >= minSeverity.level }
}

/**
 * Filters active TSBs only.
 */
fun List<TechnicalServiceBulletin>.filterActive(): List<TechnicalServiceBulletin> {
    return filter { it.isActive }
}

/**
 * Filters TSBs for a specific vehicle.
 */
fun List<TechnicalServiceBulletin>.forVehicle(make: String, model: String, year: Int): List<TechnicalServiceBulletin> {
    return filter { it.appliesToVehicle(make, model, year) }
}

/**
 * Filters TSBs related to specific DTCs.
 */
fun List<TechnicalServiceBulletin>.forDTCs(dtcCodes: List<String>): List<TechnicalServiceBulletin> {
    return filter { tsb -> dtcCodes.any { tsb.relatesToDTC(it) } }
}

/**
 * Sorts TSBs by priority (highest first).
 */
fun List<TechnicalServiceBulletin>.sortedByPriority(): List<TechnicalServiceBulletin> {
    return sortedByDescending { it.priority }
}

/**
 * Sorts TSBs by publish date (newest first).
 */
fun List<TechnicalServiceBulletin>.sortedByDate(): List<TechnicalServiceBulletin> {
    return sortedByDescending { it.publishDate }
}

/**
 * Groups TSBs by manufacturer.
 */
fun List<TechnicalServiceBulletin>.groupByManufacturer(): Map<String, List<TechnicalServiceBulletin>> {
    return groupBy { it.manufacturer }
}

/**
 * Groups TSBs by category.
 */
fun List<TechnicalServiceBulletin>.groupByCategory(): Map<TSBCategory, List<TechnicalServiceBulletin>> {
    return groupBy { it.category }
}