package com.spacetec.domain.models.diagnostic

import java.io.Serializable

/**
 * DTC pattern analysis results for trend identification and insights.
 *
 * @property timeRange Analysis time range
 * @property totalSessions Total diagnostic sessions analyzed
 * @property totalDTCs Total unique DTCs found
 * @property totalOccurrences Total DTC occurrences
 * @property commonPatterns Identified common patterns
 * @property seasonalTrends Seasonal trend analysis
 * @property vehicleSpecificIssues Vehicle-specific issues
 * @property emergingProblems Newly emerging problems
 * @property repairEffectiveness Repair effectiveness analysis
 * @property correlations DTC correlations
 * @property frequencyAnalysis Frequency analysis results
 * @property costAnalysis Cost impact analysis
 * @property generatedAt Analysis generation timestamp
 */
data class DTCPatternAnalysis(
    val timeRange: TimeRange,
    val totalSessions: Int,
    val totalDTCs: Int,
    val totalOccurrences: Int,
    val commonPatterns: List<DTCPattern>,
    val seasonalTrends: List<SeasonalTrend>,
    val vehicleSpecificIssues: List<VehicleIssue>,
    val emergingProblems: List<EmergingProblem>,
    val repairEffectiveness: List<RepairEffectiveness>,
    val correlations: List<DTCCorrelation>,
    val frequencyAnalysis: DTCFrequencyAnalysis,
    val costAnalysis: DTCCostAnalysis,
    val generatedAt: Long = System.currentTimeMillis()
) : Serializable {

    /**
     * Average DTCs per session.
     */
    val averageDTCsPerSession: Double
        get() = if (totalSessions > 0) totalDTCs.toDouble() / totalSessions else 0.0

    /**
     * Most common DTC pattern.
     */
    val topPattern: DTCPattern?
        get() = commonPatterns.maxByOrNull { it.frequency }

    /**
     * Most problematic vehicle issue.
     */
    val topVehicleIssue: VehicleIssue?
        get() = vehicleSpecificIssues.maxByOrNull { it.severity }

    /**
     * Summary of key findings.
     */
    fun getSummary(): String = buildString {
        appendLine("DTC Pattern Analysis Summary")
        appendLine("Time Range: ${timeRange.durationDays} days")
        appendLine("Sessions Analyzed: $totalSessions")
        appendLine("Unique DTCs: $totalDTCs")
        appendLine("Total Occurrences: $totalOccurrences")
        appendLine("Average DTCs/Session: ${"%.1f".format(averageDTCsPerSession)}")
        appendLine()
        
        if (commonPatterns.isNotEmpty()) {
            appendLine("Top Patterns:")
            commonPatterns.take(3).forEach { pattern ->
                appendLine("- ${pattern.description} (${pattern.frequency} occurrences)")
            }
            appendLine()
        }
        
        if (emergingProblems.isNotEmpty()) {
            appendLine("Emerging Issues:")
            emergingProblems.take(3).forEach { problem ->
                appendLine("- ${problem.description} (+${problem.growthRate}%)")
            }
        }
    }

    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * Identified DTC pattern with frequency and confidence metrics.
 *
 * @property id Pattern identifier
 * @property description Pattern description
 * @property dtcCodes DTCs involved in pattern
 * @property frequency Pattern frequency
 * @property confidence Confidence level (0.0 to 1.0)
 * @property patternType Type of pattern
 * @property vehicles Affected vehicles
 * @property timeframe Timeframe when pattern occurs
 * @property rootCause Identified root cause
 * @property recommendedAction Recommended action
 */
data class DTCPattern(
    val id: String,
    val description: String,
    val dtcCodes: List<String>,
    val frequency: Int,
    val confidence: Double,
    val patternType: PatternType,
    val vehicles: List<String> = emptyList(),
    val timeframe: String? = null,
    val rootCause: String? = null,
    val recommendedAction: String? = null
) : Serializable {

    /**
     * Whether this is a high-confidence pattern.
     */
    val isHighConfidence: Boolean
        get() = confidence >= 0.8

    /**
     * Whether this pattern affects multiple DTCs.
     */
    val isMultiDTC: Boolean
        get() = dtcCodes.size > 1

    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * Types of DTC patterns.
 */
enum class PatternType(
    val displayName: String,
    val description: String
) {
    SEQUENTIAL("Sequential", "DTCs that occur in sequence"),
    SIMULTANEOUS("Simultaneous", "DTCs that occur together"),
    CASCADING("Cascading", "One DTC leads to others"),
    SEASONAL("Seasonal", "DTCs that occur seasonally"),
    MILEAGE_BASED("Mileage-Based", "DTCs that occur at specific mileages"),
    VEHICLE_SPECIFIC("Vehicle-Specific", "DTCs specific to certain vehicles"),
    COMPONENT_FAILURE("Component Failure", "DTCs indicating component failure"),
    MAINTENANCE_RELATED("Maintenance-Related", "DTCs related to maintenance needs")
}

/**
 * Seasonal trend analysis for DTCs.
 *
 * @property season Season identifier
 * @property dtcCode DTC code
 * @property occurrences Number of occurrences in season
 * @property trend Trend direction
 * @property significance Statistical significance
 * @property description Trend description
 */
data class SeasonalTrend(
    val season: Season,
    val dtcCode: String,
    val occurrences: Int,
    val trend: TrendDirection,
    val significance: Double,
    val description: String
) : Serializable {

    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * Seasons for trend analysis.
 */
enum class Season(val displayName: String) {
    SPRING("Spring"),
    SUMMER("Summer"),
    FALL("Fall"),
    WINTER("Winter")
}

/**
 * Trend directions.
 */
enum class TrendDirection(val displayName: String) {
    INCREASING("Increasing"),
    DECREASING("Decreasing"),
    STABLE("Stable")
}

/**
 * Vehicle-specific issue identification.
 *
 * @property make Vehicle make
 * @property model Vehicle model
 * @property yearRange Year range affected
 * @property dtcCodes Related DTC codes
 * @property description Issue description
 * @property severity Issue severity
 * @property affectedVehicles Number of affected vehicles
 * @property recommendedAction Recommended action
 */
data class VehicleIssue(
    val make: String,
    val model: String,
    val yearRange: IntRange,
    val dtcCodes: List<String>,
    val description: String,
    val severity: Int,
    val affectedVehicles: Int,
    val recommendedAction: String? = null
) : Serializable {

    /**
     * Formatted year range.
     */
    val formattedYearRange: String
        get() = "${yearRange.first}-${yearRange.last}"

    /**
     * Vehicle description.
     */
    val vehicleDescription: String
        get() = "$make $model ($formattedYearRange)"

    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * Emerging problem identification.
 *
 * @property dtcCode DTC code
 * @property description Problem description
 * @property growthRate Growth rate percentage
 * @property recentOccurrences Recent occurrences
 * @property projectedImpact Projected impact
 * @property confidence Confidence in prediction
 */
data class EmergingProblem(
    val dtcCode: String,
    val description: String,
    val growthRate: Double,
    val recentOccurrences: Int,
    val projectedImpact: String,
    val confidence: Double
) : Serializable {

    /**
     * Whether this is a high-growth problem.
     */
    val isHighGrowth: Boolean
        get() = growthRate > 50.0

    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * Repair effectiveness analysis.
 *
 * @property repairProcedureId Repair procedure ID
 * @property dtcCode Related DTC code
 * @property successRate Success rate
 * @property totalAttempts Total repair attempts
 * @property averageTime Average repair time
 * @property averageCost Average repair cost
 * @property effectiveness Overall effectiveness score
 */
data class RepairEffectiveness(
    val repairProcedureId: String,
    val dtcCode: String,
    val successRate: Double,
    val totalAttempts: Int,
    val averageTime: Double,
    val averageCost: Double,
    val effectiveness: Double
) : Serializable {

    /**
     * Whether this is a highly effective repair.
     */
    val isHighlyEffective: Boolean
        get() = effectiveness > 0.8

    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * DTC correlation analysis.
 *
 * @property dtcCode1 First DTC code
 * @property dtcCode2 Second DTC code
 * @property correlation Correlation coefficient
 * @property coOccurrences Number of co-occurrences
 * @property significance Statistical significance
 * @property relationshipType Type of relationship
 */
data class DTCCorrelation(
    val dtcCode1: String,
    val dtcCode2: String,
    val correlation: Double,
    val coOccurrences: Int,
    val significance: Double,
    val relationshipType: CorrelationType
) : Serializable {

    /**
     * Whether this is a strong correlation.
     */
    val isStrongCorrelation: Boolean
        get() = kotlin.math.abs(correlation) > 0.7

    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * Types of DTC correlations.
 */
enum class CorrelationType(val displayName: String) {
    CAUSAL("Causal"),
    RELATED("Related"),
    COINCIDENTAL("Coincidental")
}

/**
 * DTC frequency analysis results.
 *
 * @property topDTCs Most frequent DTCs
 * @property systemFrequency Frequency by system
 * @property severityDistribution Distribution by severity
 * @property manufacturerFrequency Frequency by manufacturer
 * @property timeDistribution Distribution over time
 */
data class DTCFrequencyAnalysis(
    val topDTCs: List<DTCFrequency>,
    val systemFrequency: Map<DTCSystem, Int>,
    val severityDistribution: Map<DTCSeverity, Int>,
    val manufacturerFrequency: Map<String, Int>,
    val timeDistribution: Map<String, Int>
) : Serializable {

    /**
     * Most frequent DTC.
     */
    val mostFrequentDTC: DTCFrequency?
        get() = topDTCs.maxByOrNull { it.count }

    /**
     * Most problematic system.
     */
    val mostProblematicSystem: DTCSystem?
        get() = systemFrequency.maxByOrNull { it.value }?.key

    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * DTC frequency information.
 *
 * @property dtcCode DTC code
 * @property count Number of occurrences
 * @property percentage Percentage of total occurrences
 * @property trend Trend over time
 * @property description DTC description
 */
data class DTCFrequency(
    val dtcCode: String,
    val count: Int,
    val percentage: Double,
    val trend: TrendDirection,
    val description: String? = null
) : Serializable {

    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * DTC cost analysis results.
 *
 * @property totalCost Total cost across all DTCs
 * @property averageCostPerDTC Average cost per DTC
 * @property costBySystem Cost breakdown by system
 * @property costBySeverity Cost breakdown by severity
 * @property mostExpensiveDTCs Most expensive DTCs to repair
 * @property costTrends Cost trends over time
 */
data class DTCCostAnalysis(
    val totalCost: Double,
    val averageCostPerDTC: Double,
    val costBySystem: Map<DTCSystem, Double>,
    val costBySeverity: Map<DTCSeverity, Double>,
    val mostExpensiveDTCs: List<DTCCostInfo>,
    val costTrends: Map<String, Double>
) : Serializable {

    /**
     * Most expensive system to repair.
     */
    val mostExpensiveSystem: DTCSystem?
        get() = costBySystem.maxByOrNull { it.value }?.key

    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * DTC cost information.
 *
 * @property dtcCode DTC code
 * @property averageCost Average repair cost
 * @property minCost Minimum repair cost
 * @property maxCost Maximum repair cost
 * @property repairCount Number of repairs
 * @property description DTC description
 */
data class DTCCostInfo(
    val dtcCode: String,
    val averageCost: Double,
    val minCost: Double,
    val maxCost: Double,
    val repairCount: Int,
    val description: String? = null
) : Serializable {

    /**
     * Cost range.
     */
    val costRange: Double
        get() = maxCost - minCost

    /**
     * Formatted average cost.
     */
    val formattedAverageCost: String
        get() = "$${"%.0f".format(averageCost)}"

    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * Fleet analytics for multiple vehicles.
 *
 * @property fleetId Fleet identifier
 * @property timeRange Analysis time range
 * @property totalVehicles Total vehicles in fleet
 * @property vehiclesWithDTCs Vehicles with DTCs
 * @property mostCommonDTCs Most common DTCs across fleet
 * @property costAnalysis Fleet cost analysis
 * @property maintenanceRecommendations Maintenance recommendations
 * @property performanceMetrics Fleet performance metrics
 * @property vehicleComparison Vehicle comparison analysis
 * @property predictiveInsights Predictive maintenance insights
 */
data class FleetAnalytics(
    val fleetId: String,
    val timeRange: TimeRange,
    val totalVehicles: Int,
    val vehiclesWithDTCs: Int,
    val mostCommonDTCs: List<DTCFrequency>,
    val costAnalysis: FleetCostAnalysis,
    val maintenanceRecommendations: List<MaintenanceRecommendation>,
    val performanceMetrics: FleetPerformanceMetrics,
    val vehicleComparison: List<VehicleComparison>,
    val predictiveInsights: List<PredictiveInsight>
) : Serializable {

    /**
     * Fleet health percentage.
     */
    val fleetHealthPercent: Double
        get() = if (totalVehicles > 0) {
            ((totalVehicles - vehiclesWithDTCs).toDouble() / totalVehicles) * 100
        } else 100.0

    /**
     * DTC rate per vehicle.
     */
    val dtcRatePerVehicle: Double
        get() = if (totalVehicles > 0) {
            mostCommonDTCs.sumOf { it.count }.toDouble() / totalVehicles
        } else 0.0

    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * Fleet cost analysis.
 *
 * @property totalMaintenanceCost Total maintenance cost
 * @property averageCostPerVehicle Average cost per vehicle
 * @property costByCategory Cost breakdown by category
 * @property projectedCosts Projected future costs
 */
data class FleetCostAnalysis(
    val totalMaintenanceCost: Double,
    val averageCostPerVehicle: Double,
    val costByCategory: Map<String, Double>,
    val projectedCosts: Map<String, Double>
) : Serializable {

    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * Maintenance recommendation for fleet.
 *
 * @property vehicleId Vehicle identifier
 * @property recommendation Recommendation text
 * @property priority Priority level
 * @property estimatedCost Estimated cost
 * @property dueDate Due date
 * @property category Maintenance category
 */
data class MaintenanceRecommendation(
    val vehicleId: String,
    val recommendation: String,
    val priority: Priority,
    val estimatedCost: Double,
    val dueDate: Long? = null,
    val category: MaintenanceCategory
) : Serializable {

    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * Priority levels.
 */
enum class Priority(val displayName: String, val level: Int) {
    LOW("Low", 1),
    MEDIUM("Medium", 2),
    HIGH("High", 3),
    CRITICAL("Critical", 4)
}

/**
 * Maintenance categories.
 */
enum class MaintenanceCategory(val displayName: String) {
    PREVENTIVE("Preventive"),
    CORRECTIVE("Corrective"),
    PREDICTIVE("Predictive"),
    EMERGENCY("Emergency")
}

/**
 * Fleet performance metrics.
 *
 * @property uptime Fleet uptime percentage
 * @property mtbf Mean time between failures
 * @property mttr Mean time to repair
 * @property availability Fleet availability
 * @property reliability Fleet reliability score
 */
data class FleetPerformanceMetrics(
    val uptime: Double,
    val mtbf: Double,
    val mttr: Double,
    val availability: Double,
    val reliability: Double
) : Serializable {

    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * Vehicle comparison within fleet.
 *
 * @property vehicleId Vehicle identifier
 * @property dtcCount DTC count
 * @property maintenanceCost Maintenance cost
 * @property uptime Uptime percentage
 * @property performanceScore Performance score
 * @property ranking Fleet ranking
 */
data class VehicleComparison(
    val vehicleId: String,
    val dtcCount: Int,
    val maintenanceCost: Double,
    val uptime: Double,
    val performanceScore: Double,
    val ranking: Int
) : Serializable {

    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * Predictive maintenance insight.
 *
 * @property vehicleId Vehicle identifier
 * @property prediction Prediction text
 * @property confidence Confidence level
 * @property timeframe Predicted timeframe
 * @property recommendedAction Recommended action
 * @property potentialCost Potential cost if not addressed
 */
data class PredictiveInsight(
    val vehicleId: String,
    val prediction: String,
    val confidence: Double,
    val timeframe: String,
    val recommendedAction: String,
    val potentialCost: Double
) : Serializable {

    /**
     * Whether this is a high-confidence prediction.
     */
    val isHighConfidence: Boolean
        get() = confidence > 0.8

    companion object {
        private const val serialVersionUID = 1L
    }
}