package com.spacetec.domain.models.diagnostic

import java.io.Serializable
import java.time.Duration

/**
 * Repair procedure domain model with comprehensive repair guidance.
 *
 * Represents a step-by-step repair procedure for resolving DTCs,
 * including cost estimates, success rates, and required resources.
 *
 * @property id Unique identifier
 * @property title Procedure title
 * @property description Detailed description
 * @property steps List of repair steps
 * @property difficulty Repair difficulty level
 * @property estimatedTime Estimated completion time
 * @property requiredTools List of required tools
 * @property requiredParts List of required parts
 * @property successRate Success rate percentage (0.0 to 1.0)
 * @property laborCost Labor cost range
 * @property partsCost Parts cost range
 * @property tsbs Related Technical Service Bulletins
 * @property dtcCodes Associated DTC codes
 * @property vehicleCoverage Applicable vehicles
 * @property manufacturer Manufacturer (if specific)
 * @property category Procedure category
 * @property priority Priority level for ranking
 * @property lastUpdated Last update timestamp
 * @property createdAt Creation timestamp
 *
 * @author SpaceTec Team
 * @since 1.0.0
 */
data class RepairProcedure(
    val id: String,
    val title: String,
    val description: String,
    val steps: List<RepairStep>,
    val difficulty: RepairDifficulty,
    val estimatedTime: Duration,
    val requiredTools: List<String>,
    val requiredParts: List<Part>,
    val successRate: Double,
    val laborCost: CostRange,
    val partsCost: CostRange,
    val tsbs: List<String> = emptyList(), // TSB IDs
    val dtcCodes: List<String> = emptyList(),
    val vehicleCoverage: List<VehicleCoverage> = emptyList(),
    val manufacturer: String? = null,
    val category: RepairCategory = RepairCategory.GENERAL,
    val priority: Int = 50,
    val lastUpdated: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
) : Serializable {

    /**
     * Total estimated cost (labor + parts average).
     */
    val totalEstimatedCost: Double
        get() = laborCost.average + partsCost.average

    /**
     * Minimum total cost.
     */
    val totalMinCost: Double
        get() = laborCost.min + partsCost.min

    /**
     * Maximum total cost.
     */
    val totalMaxCost: Double
        get() = laborCost.max + partsCost.max

    /**
     * Formatted cost range for display.
     */
    val formattedCostRange: String
        get() = "${laborCost.currency} ${String.format("%.0f", totalMinCost)} - ${String.format("%.0f", totalMaxCost)}"

    /**
     * Success rate as percentage string.
     */
    val successRatePercent: String
        get() = "${(successRate * 100).toInt()}%"

    /**
     * Estimated time in hours.
     */
    val estimatedHours: Double
        get() = estimatedTime.toMinutes() / 60.0

    /**
     * Formatted estimated time.
     */
    val formattedTime: String
        get() = when {
            estimatedHours < 1.0 -> "${estimatedTime.toMinutes()}m"
            estimatedHours < 24.0 -> String.format("%.1fh", estimatedHours)
            else -> "${(estimatedHours / 24).toInt()}d ${(estimatedHours % 24).toInt()}h"
        }

    /**
     * Whether this procedure applies to a specific vehicle.
     */
    fun appliesToVehicle(make: String, model: String, year: Int): Boolean {
        if (vehicleCoverage.isEmpty()) return true
        return vehicleCoverage.any { it.matches(make, model, year) }
    }

    /**
     * Whether this procedure is associated with a specific DTC.
     */
    fun appliesToDTC(dtcCode: String): Boolean {
        return dtcCodes.isEmpty() || dtcCodes.contains(dtcCode)
    }

    /**
     * Gets the total number of steps.
     */
    val stepCount: Int
        get() = steps.size

    /**
     * Gets the total number of required parts.
     */
    val partCount: Int
        get() = requiredParts.sumOf { it.quantity }

    /**
     * Gets the total number of required tools.
     */
    val toolCount: Int
        get() = requiredTools.size

    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * Individual repair step with detailed instructions.
 *
 * @property stepNumber Step sequence number
 * @property instruction Step instruction text
 * @property estimatedTime Time for this step
 * @property requiredTools Tools needed for this step
 * @property requiredParts Parts needed for this step
 * @property warnings Safety warnings or cautions
 * @property images Reference images or diagrams
 * @property notes Additional notes
 */
data class RepairStep(
    val stepNumber: Int,
    val instruction: String,
    val estimatedTime: Duration? = null,
    val requiredTools: List<String> = emptyList(),
    val requiredParts: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val images: List<String> = emptyList(),
    val notes: String? = null
) : Serializable {

    /**
     * Whether this step has safety warnings.
     */
    val hasWarnings: Boolean
        get() = warnings.isNotEmpty()

    /**
     * Whether this step has images.
     */
    val hasImages: Boolean
        get() = images.isNotEmpty()

    /**
     * Formatted step for display.
     */
    val formattedStep: String
        get() = "Step $stepNumber: $instruction"

    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * Repair difficulty levels with time and skill estimates.
 */
enum class RepairDifficulty(
    val displayName: String,
    val description: String,
    val skillLevel: String,
    val typicalTime: String,
    val level: Int
) {
    EASY(
        displayName = "Easy",
        description = "Basic maintenance tasks",
        skillLevel = "DIY/Basic",
        typicalTime = "15-30 minutes",
        level = 1
    ),
    MODERATE(
        displayName = "Moderate", 
        description = "Standard repair procedures",
        skillLevel = "Intermediate",
        typicalTime = "1-2 hours",
        level = 2
    ),
    DIFFICULT(
        displayName = "Difficult",
        description = "Advanced repair procedures",
        skillLevel = "Advanced/Professional",
        typicalTime = "2-4 hours", 
        level = 3
    ),
    EXPERT(
        displayName = "Expert",
        description = "Specialized procedures requiring professional equipment",
        skillLevel = "Professional Only",
        typicalTime = "4+ hours",
        level = 4
    );

    /**
     * Whether this difficulty requires professional service.
     */
    val requiresProfessional: Boolean
        get() = this in listOf(DIFFICULT, EXPERT)
}

/**
 * Required part information with alternatives and cost estimates.
 *
 * @property partNumber Manufacturer part number
 * @property description Part description
 * @property quantity Required quantity
 * @property estimatedCost Estimated cost per unit
 * @property alternatives Alternative part numbers
 * @property manufacturer Part manufacturer
 * @property category Part category
 * @property isOEM Whether this is an OEM part
 */
data class Part(
    val partNumber: String,
    val description: String,
    val quantity: Int = 1,
    val estimatedCost: Double? = null,
    val alternatives: List<String> = emptyList(),
    val manufacturer: String? = null,
    val category: PartCategory = PartCategory.OTHER,
    val isOEM: Boolean = true
) : Serializable {

    /**
     * Total cost for required quantity.
     */
    val totalCost: Double?
        get() = estimatedCost?.let { it * quantity }

    /**
     * Formatted cost for display.
     */
    val formattedCost: String?
        get() = estimatedCost?.let { "$${"%.2f".format(it)}" }

    /**
     * Formatted total cost for display.
     */
    val formattedTotalCost: String?
        get() = totalCost?.let { "$${"%.2f".format(it)}" }

    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * Part categories for organization.
 */
enum class PartCategory(val displayName: String) {
    FILTER("Filter"),
    SENSOR("Sensor"),
    ACTUATOR("Actuator"),
    ELECTRICAL("Electrical"),
    MECHANICAL("Mechanical"),
    FLUID("Fluid/Chemical"),
    GASKET("Gasket/Seal"),
    FASTENER("Fastener"),
    OTHER("Other")
}

/**
 * Cost range information with currency support.
 *
 * @property min Minimum cost
 * @property max Maximum cost
 * @property currency Currency code
 */
data class CostRange(
    val min: Double,
    val max: Double,
    val currency: String = "USD"
) : Serializable {

    /**
     * Average cost.
     */
    val average: Double
        get() = (min + max) / 2.0

    /**
     * Cost range span.
     */
    val range: Double
        get() = max - min

    /**
     * Formatted range for display.
     */
    val formattedRange: String
        get() = "$currency ${"%.0f".format(min)} - ${"%.0f".format(max)}"

    /**
     * Formatted average for display.
     */
    val formattedAverage: String
        get() = "$currency ${"%.0f".format(average)}"

    companion object {
        private const val serialVersionUID = 1L

        /**
         * Creates a fixed cost range (min = max).
         */
        fun fixed(cost: Double, currency: String = "USD"): CostRange {
            return CostRange(cost, cost, currency)
        }

        /**
         * Creates a cost range with percentage variance.
         */
        fun withVariance(baseCost: Double, variancePercent: Double, currency: String = "USD"): CostRange {
            val variance = baseCost * (variancePercent / 100.0)
            return CostRange(baseCost - variance, baseCost + variance, currency)
        }
    }
}

/**
 * Vehicle coverage information for repair procedures.
 *
 * @property make Vehicle make
 * @property model Vehicle model (null for all models)
 * @property yearStart Start year (inclusive)
 * @property yearEnd End year (inclusive)
 * @property engines Applicable engines (empty for all engines)
 * @property transmissions Applicable transmissions (empty for all)
 * @property regions Applicable regions (empty for all regions)
 */
data class VehicleCoverage(
    val make: String,
    val model: String? = null,
    val yearStart: Int,
    val yearEnd: Int,
    val engines: List<String> = emptyList(),
    val transmissions: List<String> = emptyList(),
    val regions: List<String> = emptyList()
) : Serializable {

    /**
     * Whether this coverage matches a specific vehicle.
     */
    fun matches(vehicleMake: String, vehicleModel: String, vehicleYear: Int): Boolean {
        return make.equals(vehicleMake, ignoreCase = true) &&
                (model == null || model.equals(vehicleModel, ignoreCase = true)) &&
                vehicleYear in yearStart..yearEnd
    }

    /**
     * Whether this coverage matches a specific engine.
     */
    fun matchesEngine(engineCode: String): Boolean {
        return engines.isEmpty() || engines.any { it.equals(engineCode, ignoreCase = true) }
    }

    /**
     * Formatted year range for display.
     */
    val formattedYearRange: String
        get() = if (yearStart == yearEnd) yearStart.toString() else "$yearStart-$yearEnd"

    /**
     * Formatted coverage description.
     */
    val description: String
        get() = buildString {
            append(make)
            model?.let { append(" $it") }
            append(" ($formattedYearRange)")
        }

    companion object {
        private const val serialVersionUID = 1L

        /**
         * Creates coverage for all models of a make.
         */
        fun allModels(make: String, yearStart: Int, yearEnd: Int): VehicleCoverage {
            return VehicleCoverage(make = make, yearStart = yearStart, yearEnd = yearEnd)
        }

        /**
         * Creates coverage for a specific model.
         */
        fun specificModel(make: String, model: String, yearStart: Int, yearEnd: Int): VehicleCoverage {
            return VehicleCoverage(make = make, model = model, yearStart = yearStart, yearEnd = yearEnd)
        }
    }
}

/**
 * Repair procedure categories for organization.
 */
enum class RepairCategory(
    val displayName: String,
    val description: String
) {
    GENERAL("General", "General repair procedures"),
    ENGINE("Engine", "Engine-related repairs"),
    TRANSMISSION("Transmission", "Transmission repairs"),
    ELECTRICAL("Electrical", "Electrical system repairs"),
    EMISSIONS("Emissions", "Emissions system repairs"),
    BRAKES("Brakes", "Brake system repairs"),
    SUSPENSION("Suspension", "Suspension repairs"),
    HVAC("HVAC", "Climate control repairs"),
    BODY("Body", "Body and interior repairs"),
    DIAGNOSTIC("Diagnostic", "Diagnostic procedures"),
    MAINTENANCE("Maintenance", "Maintenance procedures")
}