package com.spacetec.domain.models.dtc

import java.time.LocalDate
import kotlin.time.Duration

/**
 * DTC type based on first character.
 */
enum class DTCType {
    POWERTRAIN, BODY, CHASSIS, NETWORK
}

/**
 * DTC sub-type for generic vs manufacturer-specific.
 */
enum class DTCSubType {
    GENERIC, MANUFACTURER_SPECIFIC
}

/**
 * DTC category for system classification.
 */
enum class DTCCategory {
    FUEL_AIR, IGNITION, EMISSIONS, AUXILIARY_EMISSION, VEHICLE_SPEED,
    IDLE_CONTROL, COMPUTER, TRANSMISSION, ENGINE, ELECTRICAL, SAFETY
}

/**
 * DTC severity levels.
 */
enum class DTCSeverity {
    CRITICAL, HIGH, MEDIUM, LOW, INFORMATIONAL
}

/**
 * Repair difficulty levels.
 */
enum class RepairDifficulty {
    BEGINNER, INTERMEDIATE, ADVANCED, EXPERT, DEALER_ONLY
}

/**
 * Fuel type for vehicles.
 */
enum class FuelType {
    GASOLINE, DIESEL, HYBRID, ELECTRIC, PLUG_IN_HYBRID, HYDROGEN, NATURAL_GAS, FLEX_FUEL
}

/**
 * TSB category.
 */
enum class TSBCategory {
    ENGINE, TRANSMISSION, ELECTRICAL, BRAKES, SUSPENSION, HVAC,
    EMISSIONS, BODY, INTERIOR, SAFETY, RECALL, SOFTWARE_UPDATE, OTHER
}

/**
 * TSB severity level.
 */
enum class TSBSeverity {
    CRITICAL, HIGH, MEDIUM, LOW, INFORMATIONAL
}

/**
 * A range of costs.
 */
data class CostRange(
    val min: Double,
    val max: Double,
    val average: Double = (min + max) / 2
) {
    init {
        require(min <= max) { "Minimum cost must be less than or equal to maximum" }
        require(min >= 0) { "Costs cannot be negative" }
    }
}

/**
 * Cost estimate for repairs.
 */
data class CostEstimate(
    val laborCost: CostRange,
    val partsCost: CostRange,
    val totalCost: CostRange,
    val currency: String = "USD",
    val regionCode: String? = null,
    val lastUpdated: Long
)

/**
 * Defines vehicle coverage for a DTC or procedure.
 */
data class VehicleCoverage(
    val manufacturer: String,
    val models: List<String>,
    val yearStart: Int,
    val yearEnd: Int?,
    val engines: List<String>,
    val transmissions: List<String>,
    val regions: List<String>,
    val notes: String?
)

/**
 * Metadata about a DTC entry.
 */
data class DTCMetadata(
    val version: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val source: String,
    val verified: Boolean,
    val verifiedBy: String?,
    val lastVerifiedAt: Long?,
    val tags: List<String>
)

/**
 * Represents a step in the diagnostic process.
 */
data class DiagnosticStep(
    val stepNumber: Int,
    val title: String,
    val description: String,
    val expectedResult: String,
    val ifPassAction: String,
    val ifFailAction: String,
    val requiredTools: List<String>,
    val safetyWarnings: List<String>,
    val imageUrls: List<String>,
    val videoUrl: String?
)

/**
 * A step in a repair procedure.
 */
data class RepairStep(
    val stepNumber: Int,
    val title: String,
    val description: String,
    val warnings: List<String>,
    val tips: List<String>,
    val imageUrls: List<String>,
    val videoUrl: String?,
    val estimatedTime: Duration,
    val requiresSpecialTool: Boolean,
    val toolsRequired: List<String>
)

/**
 * A part required for a repair.
 */
data class Part(
    val id: String,
    val name: String,
    val partNumber: String,
    val manufacturer: String?,
    val alternatePartNumbers: List<String>,
    val estimatedCost: CostRange,
    val quantity: Int,
    val notes: String?
)

/**
 * Metadata for a repair procedure.
 */
data class RepairProcedureMetadata(
    val version: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val source: String,
    val author: String?,
    val reviewedBy: String?,
    val totalAttempts: Int,
    val successfulAttempts: Int
)

/**
 * Metadata for a TSB.
 */
data class TSBMetadata(
    val version: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val supersedes: String?,
    val supersededBy: String?,
    val expirationDate: LocalDate?,
    val regions: List<String>
)

/**
 * Information for a complete DTC lookup response.
 */
data class DTCInformation(
    val dtc: com.spacetec.domain.models.diagnostic.DTC,
    val vehicle: com.spacetec.domain.models.vehicle.Vehicle?,
    val repairProcedures: List<com.spacetec.domain.models.diagnostic.RepairProcedure>,
    val tsbs: List<com.spacetec.domain.models.diagnostic.TechnicalServiceBulletin>,
    val relatedDTCs: List<com.spacetec.domain.models.diagnostic.DTC>,
    val recommendedProcedure: com.spacetec.domain.models.diagnostic.RepairProcedure?,
    val safetyWarnings: List<String>,
    val drivingRecommendations: List<String>
)
