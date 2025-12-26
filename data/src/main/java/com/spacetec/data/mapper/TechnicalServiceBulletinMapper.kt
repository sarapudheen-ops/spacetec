package com.spacetec.data.mapper

import com.spacetec.core.database.entities.TechnicalServiceBulletinEntity
import com.spacetec.core.database.entities.AttachmentEntity
import com.spacetec.domain.models.diagnostic.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mapper for TechnicalServiceBulletin conversions between domain and data layers.
 *
 * Handles comprehensive mapping of TSBs including attachments, vehicle coverage,
 * and related DTC codes with proper serialization/deserialization.
 *
 * @author SpaceTec Team
 * @since 1.0.0
 */
@Singleton
class TechnicalServiceBulletinMapper @Inject constructor() {

    // ==================== Domain to Entity Mappings ====================

    /**
     * Converts TechnicalServiceBulletin domain model to database entity.
     */
    fun toEntity(tsb: TechnicalServiceBulletin): TechnicalServiceBulletinEntity {
        return TechnicalServiceBulletinEntity(
            id = tsb.id,
            manufacturer = tsb.manufacturer,
            bulletinNumber = tsb.bulletinNumber,
            title = tsb.title,
            description = tsb.description,
            applicableVehicles = serializeVehicleCoverage(tsb.applicableVehicles),
            publishDate = tsb.publishDate,
            category = tsb.category.name,
            severity = tsb.severity.name,
            repairProcedure = tsb.repairProcedure,
            dtcCodes = serializeStringList(tsb.dtcCodes),
            symptoms = serializeStringList(tsb.symptoms),
            rootCause = tsb.rootCause,
            supersedes = serializeStringList(tsb.supersedes),
            supersededBy = tsb.supersededBy,
            status = tsb.status.name,
            estimatedRepairTime = tsb.estimatedRepairTime,
            laborRate = tsb.laborRate,
            warrantyInfo = tsb.warrantyInfo,
            lastUpdated = tsb.lastUpdated,
            createdAt = System.currentTimeMillis()
        )
    }

    /**
     * Converts Attachment domain model to database entity.
     */
    fun toAttachmentEntity(attachment: Attachment, tsbId: String): AttachmentEntity {
        return AttachmentEntity(
            id = attachment.id,
            tsbId = tsbId,
            filename = attachment.filename,
            type = attachment.type,
            size = attachment.size,
            url = attachment.url,
            description = attachment.description,
            category = attachment.category.name,
            createdAt = System.currentTimeMillis()
        )
    }

    /**
     * Converts list of Attachments to entities.
     */
    fun toAttachmentEntityList(attachments: List<Attachment>, tsbId: String): List<AttachmentEntity> {
        return attachments.map { toAttachmentEntity(it, tsbId) }
    }

    // ==================== Entity to Domain Mappings ====================

    /**
     * Converts TechnicalServiceBulletinEntity to domain model.
     */
    fun toDomain(
        entity: TechnicalServiceBulletinEntity,
        attachments: List<AttachmentEntity> = emptyList()
    ): TechnicalServiceBulletin {
        return TechnicalServiceBulletin(
            id = entity.id,
            manufacturer = entity.manufacturer,
            bulletinNumber = entity.bulletinNumber,
            title = entity.title,
            description = entity.description,
            applicableVehicles = deserializeVehicleCoverage(entity.applicableVehicles),
            publishDate = entity.publishDate,
            category = parseTSBCategory(entity.category),
            severity = parseTSBSeverity(entity.severity),
            repairProcedure = entity.repairProcedure,
            attachments = attachments.map { toAttachmentDomain(it) },
            dtcCodes = deserializeStringList(entity.dtcCodes),
            symptoms = deserializeStringList(entity.symptoms),
            rootCause = entity.rootCause,
            supersedes = deserializeStringList(entity.supersedes),
            supersededBy = entity.supersededBy,
            status = parseTSBStatus(entity.status),
            estimatedRepairTime = entity.estimatedRepairTime,
            laborRate = entity.laborRate,
            warrantyInfo = entity.warrantyInfo,
            lastUpdated = entity.lastUpdated
        )
    }

    /**
     * Converts AttachmentEntity to domain model.
     */
    fun toAttachmentDomain(entity: AttachmentEntity): Attachment {
        return Attachment(
            id = entity.id,
            filename = entity.filename,
            type = entity.type,
            size = entity.size,
            url = entity.url,
            description = entity.description,
            category = parseAttachmentCategory(entity.category)
        )
    }

    /**
     * Converts list of entities to domain models.
     */
    fun toDomainList(entities: List<TechnicalServiceBulletinEntity>): List<TechnicalServiceBulletin> {
        return entities.map { toDomain(it) }
    }

    /**
     * Converts list of attachment entities to domain models.
     */
    fun toAttachmentDomainList(entities: List<AttachmentEntity>): List<Attachment> {
        return entities.map { toAttachmentDomain(it) }
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
     * Serializes vehicle coverage list to string.
     */
    private fun serializeVehicleCoverage(coverage: List<VehicleCoverage>): String {
        return coverage.joinToString(COVERAGE_DELIMITER) { vc ->
            "${vc.make}${COVERAGE_FIELD_DELIMITER}${vc.model ?: ""}${COVERAGE_FIELD_DELIMITER}${vc.yearStart}${COVERAGE_FIELD_DELIMITER}${vc.yearEnd}${COVERAGE_FIELD_DELIMITER}${serializeStringList(vc.engines)}${COVERAGE_FIELD_DELIMITER}${serializeStringList(vc.transmissions)}${COVERAGE_FIELD_DELIMITER}${serializeStringList(vc.regions)}"
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
                        transmissions = fields.getOrNull(5)?.let { deserializeStringList(it) } ?: emptyList(),
                        regions = fields.getOrNull(6)?.let { deserializeStringList(it) } ?: emptyList()
                    )
                } else null
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ==================== Parsing Helpers ====================

    /**
     * Parses TSBCategory from string.
     */
    private fun parseTSBCategory(category: String?): TSBCategory {
        return try {
            category?.let { TSBCategory.valueOf(it) } ?: TSBCategory.TECHNICAL_UPDATE
        } catch (e: IllegalArgumentException) {
            TSBCategory.TECHNICAL_UPDATE
        }
    }

    /**
     * Parses TSBSeverity from string.
     */
    private fun parseTSBSeverity(severity: String?): TSBSeverity {
        return try {
            severity?.let { TSBSeverity.valueOf(it) } ?: TSBSeverity.MEDIUM
        } catch (e: IllegalArgumentException) {
            TSBSeverity.MEDIUM
        }
    }

    /**
     * Parses TSBStatus from string.
     */
    private fun parseTSBStatus(status: String?): TSBStatus {
        return try {
            status?.let { TSBStatus.valueOf(it) } ?: TSBStatus.ACTIVE
        } catch (e: IllegalArgumentException) {
            TSBStatus.ACTIVE
        }
    }

    /**
     * Parses AttachmentCategory from string.
     */
    private fun parseAttachmentCategory(category: String?): AttachmentCategory {
        return try {
            category?.let { AttachmentCategory.valueOf(it) } ?: AttachmentCategory.DOCUMENT
        } catch (e: IllegalArgumentException) {
            AttachmentCategory.DOCUMENT
        }
    }

    // ==================== Utility Methods ====================

    /**
     * Creates a summary of TSBs for analytics.
     */
    fun createSummary(tsbs: List<TechnicalServiceBulletin>): TSBSummary {
        return TSBSummary(
            totalCount = tsbs.size,
            activeCount = tsbs.count { it.isActive },
            supersededCount = tsbs.count { it.isSuperseded },
            categoryDistribution = tsbs.groupBy { it.category }.mapValues { it.value.size },
            severityDistribution = tsbs.groupBy { it.severity }.mapValues { it.value.size },
            manufacturerDistribution = tsbs.groupBy { it.manufacturer }.mapValues { it.value.size },
            averageAge = calculateAverageAge(tsbs),
            withAttachmentsCount = tsbs.count { it.hasAttachments },
            withDTCsCount = tsbs.count { it.hasDTCs }
        )
    }

    /**
     * Filters TSBs by vehicle compatibility.
     */
    fun filterByVehicle(
        tsbs: List<TechnicalServiceBulletin>,
        make: String,
        model: String,
        year: Int
    ): List<TechnicalServiceBulletin> {
        return tsbs.filter { it.appliesToVehicle(make, model, year) }
    }

    /**
     * Filters TSBs by DTC codes.
     */
    fun filterByDTCs(
        tsbs: List<TechnicalServiceBulletin>,
        dtcCodes: List<String>
    ): List<TechnicalServiceBulletin> {
        return tsbs.filter { tsb ->
            dtcCodes.any { dtcCode -> tsb.relatesToDTC(dtcCode) }
        }
    }

    /**
     * Sorts TSBs by priority (severity, date, status).
     */
    fun sortByPriority(tsbs: List<TechnicalServiceBulletin>): List<TechnicalServiceBulletin> {
        return tsbs.sortedWith(
            compareByDescending<TechnicalServiceBulletin> { it.severity.level }
                .thenByDescending { it.publishDate }
                .thenBy { it.status.ordinal }
        )
    }

    /**
     * Groups TSBs by manufacturer and category.
     */
    fun groupByManufacturerAndCategory(
        tsbs: List<TechnicalServiceBulletin>
    ): Map<String, Map<TSBCategory, List<TechnicalServiceBulletin>>> {
        return tsbs.groupBy { it.manufacturer }
            .mapValues { (_, manufacturerTSBs) ->
                manufacturerTSBs.groupBy { it.category }
            }
    }

    /**
     * Finds related TSBs based on DTC codes and symptoms.
     */
    fun findRelatedTSBs(
        tsb: TechnicalServiceBulletin,
        allTSBs: List<TechnicalServiceBulletin>
    ): List<TechnicalServiceBulletin> {
        return allTSBs.filter { other ->
            other.id != tsb.id && (
                // Same DTC codes
                tsb.dtcCodes.any { code -> other.dtcCodes.contains(code) } ||
                // Similar symptoms
                tsb.symptoms.any { symptom -> 
                    other.symptoms.any { otherSymptom ->
                        otherSymptom.contains(symptom, ignoreCase = true) ||
                        symptom.contains(otherSymptom, ignoreCase = true)
                    }
                } ||
                // Same manufacturer and similar title
                (tsb.manufacturer == other.manufacturer && 
                 tsb.title.split(" ").any { word ->
                     word.length > 3 && other.title.contains(word, ignoreCase = true)
                 })
            )
        }.take(5) // Limit to 5 related TSBs
    }

    /**
     * Calculates average age of TSBs in days.
     */
    private fun calculateAverageAge(tsbs: List<TechnicalServiceBulletin>): Double {
        if (tsbs.isEmpty()) return 0.0
        
        val now = System.currentTimeMillis()
        val totalAge = tsbs.sumOf { now - it.publishDate }
        return (totalAge / tsbs.size).toDouble() / (24 * 60 * 60 * 1000) // Convert to days
    }

    companion object {
        private const val STRING_DELIMITER = "|||"
        private const val COVERAGE_DELIMITER = ";;;"
        private const val COVERAGE_FIELD_DELIMITER = ":::"
    }
}

/**
 * Summary of TSBs for analytics.
 */
data class TSBSummary(
    val totalCount: Int,
    val activeCount: Int,
    val supersededCount: Int,
    val categoryDistribution: Map<TSBCategory, Int>,
    val severityDistribution: Map<TSBSeverity, Int>,
    val manufacturerDistribution: Map<String, Int>,
    val averageAge: Double,
    val withAttachmentsCount: Int,
    val withDTCsCount: Int
) {
    /**
     * Most common category.
     */
    val mostCommonCategory: TSBCategory?
        get() = categoryDistribution.maxByOrNull { it.value }?.key

    /**
     * Most common severity.
     */
    val mostCommonSeverity: TSBSeverity?
        get() = severityDistribution.maxByOrNull { it.value }?.key

    /**
     * Most active manufacturer.
     */
    val mostActiveManufacturer: String?
        get() = manufacturerDistribution.maxByOrNull { it.value }?.key

    /**
     * Percentage of active TSBs.
     */
    val activePercentage: Double
        get() = if (totalCount > 0) (activeCount.toDouble() / totalCount) * 100 else 0.0
}