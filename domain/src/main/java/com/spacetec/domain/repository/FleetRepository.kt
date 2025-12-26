package com.spacetec.domain.repository

import com.spacetec.domain.models.vehicle.Vehicle
import java.time.LocalDate
import kotlin.time.Duration

data class TimeRange(
    val start: LocalDate,
    val end: LocalDate
)

data class FleetAnalytics(
    val fleetId: String,
    val timeRange: TimeRange,
    val totalVehicles: Int,
    val vehiclesWithDTCs: Int,
    val mostCommonDTCs: List<DTCFrequency>,
    val costAnalysis: CostAnalysis,
    val maintenanceRecommendations: List<MaintenanceRecommendation>,
    val performanceMetrics: FleetPerformanceMetrics,
    val vehicleHealthSummary: List<VehicleHealthSummary>
)

data class CostAnalysis(
    val totalRepairCost: Double,
    val averageCostPerVehicle: Double,
    val costByCategory: Map<String, Double>,
    val costTrend: List<MonthlyCost>,
    val projectedAnnualCost: Double
)

data class MonthlyCost(
    val month: LocalDate,
    val laborCost: Double,
    val partsCost: Double,
    val totalCost: Double
)

data class MaintenanceRecommendation(
    val vehicleId: String,
    val vehicle: Vehicle,
    val recommendationType: RecommendationType,
    val description: String,
    val urgency: RecommendationUrgency,
    val estimatedCost: Double,
    val basedOnDTCs: List<String>,
    val confidenceScore: Double
)

enum class RecommendationType {
    PREVENTIVE, CORRECTIVE, INSPECTION, RECALL_CHECK, SCHEDULED_SERVICE
}

enum class RecommendationUrgency {
    IMMEDIATE, SOON, SCHEDULED, OPTIONAL
}

data class FleetPerformanceMetrics(
    val averageUptime: Double,
    val averageMileage: Int,
    val dtcsPerVehicle: Double,
    val averageDiagnosticTime: Duration,
    val resolutionRate: Double,
    val vehiclesByHealth: Map<VehicleHealthStatus, Int>
)

enum class VehicleHealthStatus {
    EXCELLENT, GOOD, FAIR, NEEDS_ATTENTION, CRITICAL
}

data class VehicleHealthSummary(
    val vehicleId: String,
    val vehicle: Vehicle,
    val healthStatus: VehicleHealthStatus,
    val activeDTCs: Int,
    val historicalDTCs: Int,
    val lastDiagnosticDate: LocalDate?,
    val upcomingMaintenance: List<String>,
    val estimatedRepairCost: Double
)

data class VehicleComparison(
    val vehicles: List<VehicleComparisonData>,
    val bestPerformer: String,
    val worstPerformer: String,
    val keyDifferences: List<ComparisonInsight>
)

data class VehicleComparisonData(
    val vehicleId: String,
    val vehicle: Vehicle,
    val dtcCount: Int,
    val repairCost: Double,
    val uptime: Double,
    val healthScore: Int
)

data class ComparisonInsight(
    val metric: String,
    val description: String,
    val actionableRecommendation: String?
)

data class FleetQuery(
    val fleetId: String,
    val vehicleIds: List<String>? = null,
    val timeRange: TimeRange? = null,
    val includeHistoricalData: Boolean = true,
    val includeProjections: Boolean = true
)

/**
 * Repository interface for fleet management and analytics.
 */
interface FleetRepository {

    suspend fun generateFleetAnalytics(fleetId: String, timeRange: TimeRange): Result<FleetAnalytics>

    suspend fun analyzeFleetPatterns(fleetId: String, query: PatternAnalysisQuery): Result<DTCPatternAnalysis>

    suspend fun correlateMaintenancePatterns(fleetId: String, timeRange: TimeRange): Result<List<MaintenanceRecommendation>>

    suspend fun compareVehicles(vehicleIds: List<String>): Result<VehicleComparison>

    suspend fun getMaintenanceRecommendations(fleetId: String, vehicleId: String? = null): Result<List<MaintenanceRecommendation>>

    suspend fun getFleetHealthSummary(fleetId: String): Result<List<VehicleHealthSummary>>

    suspend fun getCostAnalysis(fleetId: String, timeRange: TimeRange): Result<CostAnalysis>

    suspend fun addVehicleToFleet(fleetId: String, vehicle: Vehicle): Result<Unit>

    suspend fun removeVehicleFromFleet(fleetId: String, vehicleId: String): Result<Unit>

    suspend fun getFleetVehicles(fleetId: String): Result<List<Vehicle>>
}
