package com.spacetec.data.mapper

import com.spacetec.core.database.entities.RepairProcedureEntity
import com.spacetec.core.database.entities.RepairStepEntity
import com.spacetec.domain.models.diagnostic.*
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mapper for RepairProcedure conversions between domain and data layers.
 *
 * Handles comprehensive mapping of repair procedures including steps,
 * cost estimates, success rates, and vehicle coverage information.
 *
 * @author SpaceTec Team
 * @since 1.0.0
 */
@Singleton
class RepairProcedureMapper @Inject constructor() {

    // ==================== Domain to Entity Mappings ====================

    /**
     * Converts RepairProcedure domain model to database entity.
     */
    fun toEntity(procedure: RepairProcedure): RepairProcedureEntity {
        return RepairProcedureEntity(
            id = procedure.id,
            title = procedure.title,
            description = procedure.description,
            difficulty = procedure.difficulty.name,
            estimatedTimeMinutes = procedure.estimatedTime.toMinutes().toInt(),
            requiredTools = serializeStringList(procedure.requiredTools),
            requiredParts = serializePartsList(procedure.requiredParts),
            successRate = procedure.successRate,
            laborCostMin = procedure.laborCost.min,
            laborCostMax = procedure.laborCost.max,
            laborCostCurrency = procedure.laborCost.currency,
            partsCostMin = procedure.partsCost.min,
            partsCostMax = procedure.partsCost.max,
            partsCostCurrency = procedure.partsCost.currency,
            tsbIds = serializeStringList(procedure.tsbs),
            dtcCodes = serializeStringList(procedure.dtcCodes),
            vehicleCoverage = serializeVehicleCoverage(procedure.vehicleCoverage),
            manufacturer = procedure.manufacturer,
            category = procedure.category.name,
            priority = procedure.priority,
            lastUpdated = procedure.lastUpdated,
            createdAt = procedure.createdAt
        )
    }

    /**
     * Converts RepairStep domain model to database entity.
     */
    fun toStepEntity(step: RepairStep, procedureId: String): RepairStepEntity {
        return RepairStepEntity(
            id = "${procedureId}_step_${step.stepNumber}",
            procedureId = procedureId,
            stepNumber = step.stepNumber,
            instruction = step.instruction,
            estimatedTimeMinutes = step.estimatedTime?.toMinutes()?.toInt(),
            requiredTools = serializeStringList(step.requiredTools),
            requiredParts = serializeStringList(step.requiredParts),
            warnings = serializeStringList(step.warnings),
            images = serializeStringList(step.images),
            notes = step.notes,
            createdAt = System.currentTimeMillis()
        )
    }

    /**
     * Converts list of RepairSteps to entities.
     */
    fun toStepEntityList(steps: List<RepairStep>, procedureId: String): List<RepairStepEntity> {
        return steps.map { toStepEntity(it, procedureId) }
    }

    // ==================== Entity to Domain Mappings ====================

    /**
     * Converts RepairProcedureEntity to domain model.
     */
    fun toDomain(entity: RepairProcedureEntity, steps: List<RepairStepEntity> = emptyList()): RepairProcedure {
        return RepairProcedure(
            id = entity.id,
            title = entity.title,
            description = entity.description,
            steps = steps.map { toStepDomain(it) }.sortedBy { it.stepNumber },
            difficulty = parseRepairDifficulty(entity.difficulty),
            estimatedTime = Duration.ofMinutes(entity.estimatedTimeMinutes.toLong()),
            requiredTools = deserializeStringList(entity.requiredTools),
            requiredParts = deserializePartsList(entity.requiredParts),
            successRate = entity.successRate,
            laborCost = CostRange(
                min = entity.laborCostMin,
                max = entity.laborCostMax,
                currency = entity.laborCostCurrency
            ),
            partsCost = CostRange(
                min = entity.partsCostMin,
                max = entity.partsCostMax,
                currency = entity.partsCostCurrency
            ),
            tsbs = deserializeStringList(entity.tsbIds),
            dtcCodes = deserializeStringList(entity.dtcCodes),
            vehicleCoverage = deserializeVehicleCoverage(entity.vehicleCoverage),
            manufacturer = entity.manufacturer,
            category = parseRepairCategory(entity.category),
            priority = entity.priority,
            lastUpdated = entity.lastUpdated,
            createdAt = entity.createdAt
        )
    }

    /**
     * Converts RepairStepEntity to domain model.
     */
    fun toStepDomain(entity: RepairStepEntity): RepairStep {
        return RepairStep(
            stepNumber = entity.stepNumber,
            instruction = entity.instruction,
            estimatedTime = entity.estimatedTimeMinutes?.let { Duration.ofMinutes(it.toLong()) },
            requiredTools = deserializeStringList(entity.requiredTools),
            requiredParts = deserializeStringList(entity.requiredParts),
            warnings = deserializeStringList(entity.warnings),
            images = deserializeStringList(entity.images),
            notes = entity.notes
        )
    }

    /**
     * Converts list of entities to domain models.
     */
    fun toDomainList(entities: List<RepairProcedureEntity>): List<RepairProcedure> {
        return entities.map { toDomain(it) }
    }

    /**
     * Converts list of step entities to domain models.
     */
    fun toStepDomainList(entities: List<RepairStepEntity>): List<RepairStep> {
        return entities.map { toStepDomain(it) }.sortedBy { it.stepNumber }
    }

    // ==================== Serialization Helpers ====================

    /**
     * Serializes a list of strings to a delimited string.
     */
    private fun serializeStringList(list: List<String>): String {
        return list.joinToString(STRING_DELIMITER)
    }

    /**
     * Deserializes a delimited string to a list of strings.
     */
    private fun deserializeStringList(serialized: String?): List<String> {
        return if (serialized.isNullOrBlank()) {
            emptyList()
        } else {
            serialized.split(STRING_DELIMITER).filter { it.isNotBlank() }
        }
    }

    /**
     * Serializes a list of Parts to JSON-like string.
     */
    private fun serializePartsList(parts: List<Part>): String {
        return parts.joinToString(PART_DELIMITER) { part ->
            "${part.partNumber}${PART_FIELD_DELIMITER}${part.description}${PART_FIELD_DELIMITER}${part.quantity}${PART_FIELD_DELIMITER}${part.estimatedCost ?: 0.0}${PART_FIELD_DELIMITER}${part.manufacturer ?: ""}${PART_FIELD_DELIMITER}${part.category.name}${PART_FIELD_DELIMITER}${part.isOEM}"
        }
    }

    /**
     * Deserializes parts string to list of Parts.
     */
    private fun deserializePartsList(serialized: String?): List<Part> {
        if (serialized.isNullOrBlank()) return emptyList()
        
        return try {
            serialized.split(PART_DELIMITER).mapNotNull { partString ->
                val fields = partString.split(PART_FIELD_DELIMITER)
                if (fields.size >= 4) {
                    Part(
                        partNumber = fields[0],
                        description = fields[1],
                        quantity = fields[2].toIntOrNull() ?: 1,
                        estimatedCost = fields[3].toDoubleOrNull(),
                        manufacturer = fields.getOrNull(4)?.takeIf { it.isNotBlank() },
                        category = parsePartCategory(fields.getOrNull(5)),
                        isOEM = fields.getOrNull(6)?.toBooleanStrictOrNull() ?: true
                    )
                } else null
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Serializes vehicle coverage list to string.
     */
    private fun serializeVehicleCoverage(coverage: List<VehicleCoverage>): String {
        return coverage.joinToString(COVERAGE_DELIMITER) { vc ->
            "${vc.make}${COVERAGE_FIELD_DELIMITER}${vc.model ?: ""}${COVERAGE_FIELD_DELIMITER}${vc.yearStart}${COVERAGE_FIELD_DELIMITER}${vc.yearEnd}${COVERAGE_FIELD_DELIMITER}${serializeStringList(vc.engines)}${COVERAGE_FIELD_DELIMITER}${serializeStringList(vc.transmissions)}"
        }
    }

    /**
     * Deserializes vehicle coverage string to list.
     */
    private fun deserializeVehicleCoverage(serialized: String?): List<VehicleCoverage> {
        if (serialized.isNullOrBlank()) return emptyList()
        
        return try {
            serialized.split(COVERAGE_DELIMITER).mapNotNull { coverageString ->
                val fields = coverageString.split(COVERAGE_FIELD_DELIMITER)
                if (fields.size >= 4) {
                    VehicleCoverage(
                        make = fields[0],
                        model = fields[1].takeIf { it.isNotBlank() },
                        yearStart = fields[2].toIntOrNull() ?: 1990,
                        yearEnd = fields[3].toIntOrNull() ?: 2030,
                        engines = fields.getOrNull(4)?.let { deserializeStringList(it) } ?: emptyList(),
                        transmissions = fields.getOrNull(5)?.let { deserializeStringList(it) } ?: emptyList()
                    )
                } else null
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ==================== Parsing Helpers ====================

    /**
     * Parses RepairDifficulty from string.
     */
    private fun parseRepairDifficulty(difficulty: String?): RepairDifficulty {
        return try {
            difficulty?.let { RepairDifficulty.valueOf(it) } ?: RepairDifficulty.MODERATE
        } catch (e: IllegalArgumentException) {
            RepairDifficulty.MODERATE
        }
    }

    /**
     * Parses RepairCategory from string.
     */
    private fun parseRepairCategory(category: String?): RepairCategory {
        return try {
            category?.let { RepairCategory.valueOf(it) } ?: RepairCategory.GENERAL
        } catch (e: IllegalArgumentException) {
            RepairCategory.GENERAL
        }
    }

    /**
     * Parses PartCategory from string.
     */
    private fun parsePartCategory(category: String?): PartCategory {
        return try {
            category?.let { PartCategory.valueOf(it) } ?: PartCategory.OTHER
        } catch (e: IllegalArgumentException) {
            PartCategory.OTHER
        }
    }

    // ==================== Utility Methods ====================

    /**
     * Creates a summary of repair procedures.
     */
    fun createSummary(procedures: List<RepairProcedure>): RepairProcedureSummary {
        return RepairProcedureSummary(
            totalCount = procedures.size,
            averageSuccessRate = procedures.map { it.successRate }.average(),
            averageCost = procedures.map { it.totalEstimatedCost }.average(),
            difficultyDistribution = procedures.groupBy { it.difficulty }.mapValues { it.value.size },
            categoryDistribution = procedures.groupBy { it.category }.mapValues { it.value.size },
            averageSteps = procedures.map { it.stepCount }.average(),
            averageTime = procedures.map { it.estimatedHours }.average()
        )
    }

    /**
     * Filters procedures by vehicle compatibility.
     */
    fun filterByVehicle(
        procedures: List<RepairProcedure>,
        make: String,
        model: String,
        year: Int
    ): List<RepairProcedure> {
        return procedures.filter { it.appliesToVehicle(make, model, year) }
    }

    /**
     * Sorts procedures by effectiveness (success rate and cost).
     */
    fun sortByEffectiveness(procedures: List<RepairProcedure>): List<RepairProcedure> {
        return procedures.sortedWith(
            compareByDescending<RepairProcedure> { it.successRate }
                .thenBy { it.totalEstimatedCost }
                .thenBy { it.difficulty.level }
        )
    }

    companion object {
        private const val STRING_DELIMITER = "|||"
        private const val PART_DELIMITER = ";;;"
        private const val PART_FIELD_DELIMITER = ":::"
        private const val COVERAGE_DELIMITER = ";;;"
        private const val COVERAGE_FIELD_DELIMITER = ":::"
    }
}

/**
 * Summary of repair procedures for analytics.
 */
data class RepairProcedureSummary(
    val totalCount: Int,
    val averageSuccessRate: Double,
    val averageCost: Double,
    val difficultyDistribution: Map<RepairDifficulty, Int>,
    val categoryDistribution: Map<RepairCategory, Int>,
    val averageSteps: Double,
    val averageTime: Double
) {
    /**
     * Most common difficulty level.
     */
    val mostCommonDifficulty: RepairDifficulty?
        get() = difficultyDistribution.maxByOrNull { it.value }?.key

    /**
     * Most common category.
     */
    val mostCommonCategory: RepairCategory?
        get() = categoryDistribution.maxByOrNull { it.value }?.key
}