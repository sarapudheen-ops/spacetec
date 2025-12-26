/**
 * File: DTCMapper.kt
 * 
 * Mapper class for transforming between DTC (Diagnostic Trouble Code) domain models
 * and their corresponding database entities in the SpaceTec automotive diagnostic
 * application. This mapper handles bidirectional conversion, batch operations,
 * and specialized mappings for DTC descriptions and history tracking.
 * 
 * Structure:
 * 1. Package declaration and imports
 * 2. DTCMapper class with injection
 * 3. Domain to Entity mapping methods
 * 4. Entity to Domain mapping methods
 * 5. Batch mapping operations
 * 6. Specialized mappings (descriptions, history, freeze frames)
 * 7. Helper extension functions
 * 
 * @author SpaceTec Development Team
 * @since 1.0.0
 */
package com.spacetec.data.mapper

import com.spacetec.core.database.entities.DTCDescriptionEntity
import com.spacetec.core.database.entities.DTCEntity
import com.spacetec.core.database.entities.DTCHistoryEntity
import com.spacetec.core.database.entities.DTCWithDescriptionEntity
import com.spacetec.core.database.entities.FreezeFrameEntity
import com.spacetec.data.datasource.remote.DTCDescriptionResponse
import com.spacetec.domain.models.diagnostic.DTC
import com.spacetec.domain.models.diagnostic.DTCSeverity
import com.spacetec.domain.models.diagnostic.DTCStatus
import com.spacetec.domain.models.diagnostic.DTCType
import com.spacetec.domain.models.diagnostic.FreezeFrame
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mapper for DTC (Diagnostic Trouble Code) conversions between domain and data layers.
 * 
 * This class provides comprehensive mapping functionality for:
 * - Converting DTC domain models to database entities and vice versa
 * - Mapping DTC descriptions from remote API responses
 * - Handling DTC history records for tracking occurrences
 * - Processing freeze frame data associated with DTCs
 * - Batch operations for efficient list transformations
 * 
 * ## Mapping Strategy
 * 
 * The mapper follows these principles:
 * - **Null Safety**: Handles nullable fields gracefully with defaults
 * - **Immutability**: Creates new instances rather than modifying existing ones
 * - **Type Conversion**: Safely converts between enum types and string representations
 * - **Enrichment**: Combines multiple sources (entity + description) when available
 * 
 * ## Usage Example
 * 
 * ```kotlin
 * @Inject lateinit var dtcMapper: DTCMapper
 * 
 * // Entity to Domain
 * val dtcEntity = dtcDao.getDTC(code)
 * val dtc = dtcMapper.toDomain(dtcEntity)
 * 
 * // Domain to Entity
 * val entity = dtcMapper.toEntity(dtc, sessionId)
 * dtcDao.insert(entity)
 * 
 * // Batch conversion
 * val entities = dtcDao.getAllDTCs()
 * val dtcs = dtcMapper.toDomainList(entities)
 * ```
 * 
 * @see DTC
 * @see DTCEntity
 * @see DTCDescriptionEntity
 */
@Singleton
class DTCMapper @Inject constructor() {

    // ==================== Domain to Entity Mappings ====================

    /**
     * Converts a DTC domain model to a database entity.
     * 
     * @param dtc The DTC domain model to convert
     * @param sessionId The session ID to associate with this DTC
     * @param vehicleId Optional vehicle ID for direct association
     * @return DTCEntity ready for database insertion
     */
    fun toEntity(
        dtc: DTC,
        sessionId: String,
        vehicleId: String? = null
    ): DTCEntity {
        return DTCEntity(
            id = dtc.id,
            code = dtc.code,
            sessionId = sessionId,
            vehicleId = vehicleId,
            type = dtc.type.name,
            status = dtc.status?.name,
            system = dtc.system,
            subsystem = dtc.subsystem,
            description = dtc.description,
            severity = dtc.severity.name,
            isPending = dtc.isPending,
            isStored = dtc.isStored,
            isPermanent = dtc.isPermanent,
            isActive = dtc.isActive,
            illuminatesMIL = dtc.illuminatesMIL,
            frameNumber = dtc.frameNumber,
            hasFreezeFrame = dtc.hasFreezeFrame,
            firstOccurrence = dtc.firstOccurrence,
            lastOccurrence = dtc.lastOccurrence,
            occurrenceCount = dtc.occurrenceCount,
            ecuAddress = dtc.ecuAddress,
            ecuName = dtc.ecuName,
            rawData = dtc.rawData,
            timestamp = dtc.timestamp,
            mileage = dtc.mileage,
            wasCleared = false,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * Converts a DTC domain model to a database entity with minimal required fields.
     * 
     * @param dtc The DTC domain model
     * @return DTCEntity with only essential fields populated
     */
    fun toEntity(dtc: DTC): DTCEntity {
        return DTCEntity(
            id = dtc.id,
            code = dtc.code,
            sessionId = "",
            vehicleId = null,
            type = dtc.type.name,
            status = dtc.status?.name,
            system = dtc.system,
            subsystem = dtc.subsystem,
            description = dtc.description,
            severity = dtc.severity.name,
            isPending = dtc.isPending,
            isStored = dtc.isStored,
            isPermanent = dtc.isPermanent,
            isActive = dtc.isActive,
            illuminatesMIL = dtc.illuminatesMIL,
            frameNumber = dtc.frameNumber,
            hasFreezeFrame = dtc.hasFreezeFrame,
            firstOccurrence = dtc.firstOccurrence,
            lastOccurrence = dtc.lastOccurrence,
            occurrenceCount = dtc.occurrenceCount,
            ecuAddress = dtc.ecuAddress,
            ecuName = dtc.ecuName,
            rawData = dtc.rawData,
            timestamp = dtc.timestamp,
            mileage = dtc.mileage,
            wasCleared = false,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * Converts a list of DTC domain models to database entities.
     * 
     * @param dtcs List of DTC domain models
     * @param sessionId Session ID to associate with all DTCs
     * @param vehicleId Optional vehicle ID
     * @return List of DTCEntity objects
     */
    fun toEntityList(
        dtcs: List<DTC>,
        sessionId: String,
        vehicleId: String? = null
    ): List<DTCEntity> {
        return dtcs.map { toEntity(it, sessionId, vehicleId) }
    }

    // ==================== Entity to Domain Mappings ====================

    /**
     * Converts a DTCEntity to a DTC domain model.
     * 
     * @param entity The database entity to convert
     * @return DTC domain model
     */
    fun toDomain(entity: DTCEntity): DTC {
        return DTC(
            id = entity.id,
            code = entity.code,
            type = parseDTCType(entity.type),
            status = entity.status?.let { parseDTCStatus(it) },
            system = entity.system ?: parseSystemFromCode(entity.code),
            subsystem = entity.subsystem,
            description = entity.description,
            severity = parseDTCSeverity(entity.severity),
            possibleCauses = emptyList(), // Loaded separately from description table
            suggestedActions = emptyList(), // Loaded separately from description table
            isPending = entity.isPending,
            isStored = entity.isStored,
            isPermanent = entity.isPermanent,
            isActive = entity.isActive,
            illuminatesMIL = entity.illuminatesMIL,
            frameNumber = entity.frameNumber,
            hasFreezeFrame = entity.hasFreezeFrame,
            firstOccurrence = entity.firstOccurrence,
            lastOccurrence = entity.lastOccurrence,
            occurrenceCount = entity.occurrenceCount,
            ecuAddress = entity.ecuAddress,
            ecuName = entity.ecuName,
            rawData = entity.rawData,
            timestamp = entity.timestamp,
            mileage = entity.mileage
        )
    }

    /**
     * Converts a DTCEntity with embedded description to a DTC domain model.
     * 
     * @param entity The combined entity with description
     * @return DTC domain model with full description data
     */
    fun toDomain(entity: DTCWithDescriptionEntity): DTC {
        val baseDTC = toDomain(entity.dtc)
        val description = entity.description
        
        return if (description != null) {
            baseDTC.copy(
                description = description.description ?: baseDTC.description,
                severity = description.severity?.let { parseDTCSeverity(it) } ?: baseDTC.severity,
                possibleCauses = description.possibleCauses?.split(CAUSE_DELIMITER) ?: emptyList(),
                suggestedActions = description.suggestedActions?.split(ACTION_DELIMITER) ?: emptyList(),
                system = description.system ?: baseDTC.system,
                subsystem = description.subsystem ?: baseDTC.subsystem
            )
        } else {
            baseDTC
        }
    }

    /**
     * Converts a list of DTCEntity objects to DTC domain models.
     * 
     * @param entities List of database entities
     * @return List of DTC domain models
     */
    fun toDomainList(entities: List<DTCEntity>): List<DTC> {
        return entities.map { toDomain(it) }
    }

    /**
     * Converts a list of DTCWithDescriptionEntity to DTC domain models.
     * 
     * @param entities List of combined entities
     * @return List of DTC domain models with descriptions
     */
    fun toDomainListWithDescriptions(entities: List<DTCWithDescriptionEntity>): List<DTC> {
        return entities.map { toDomain(it) }
    }

    // ==================== Description Entity Mappings ====================

    /**
     * Converts a DTC description API response to a database entity.
     * 
     * @param response The remote API response
     * @return DTCDescriptionEntity for caching
     */
    fun toDescriptionEntity(response: DTCDescriptionResponse): DTCDescriptionEntity {
        return DTCDescriptionEntity(
            code = response.code,
            description = response.description,
            detailedDescription = response.detailedDescription,
            system = response.system,
            subsystem = response.subsystem,
            severity = response.severity,
            possibleCauses = response.possibleCauses?.joinToString(CAUSE_DELIMITER),
            suggestedActions = response.suggestedActions?.joinToString(ACTION_DELIMITER),
            technicalNotes = response.technicalNotes,
            commonFixes = response.commonFixes?.joinToString(FIX_DELIMITER),
            estimatedRepairCost = response.estimatedRepairCost,
            laborHours = response.laborHours,
            difficultyLevel = response.difficultyLevel,
            partsRequired = response.partsRequired?.joinToString(PARTS_DELIMITER),
            relatedCodes = response.relatedCodes?.joinToString(CODE_DELIMITER),
            manufacturer = response.manufacturer,
            applicableModels = response.applicableModels?.joinToString(MODEL_DELIMITER),
            lastUpdated = response.lastUpdated ?: System.currentTimeMillis(),
            source = response.source,
            createdAt = System.currentTimeMillis()
        )
    }

    /**
     * Converts a DTCDescriptionEntity to a DTCDescription domain model.
     * 
     * @param entity The description entity
     * @return DTCDescription domain model
     */
    fun toDescriptionDomain(entity: DTCDescriptionEntity): DTCDescription {
        return DTCDescription(
            code = entity.code,
            description = entity.description,
            detailedDescription = entity.detailedDescription,
            system = entity.system,
            subsystem = entity.subsystem,
            severity = entity.severity?.let { parseDTCSeverity(it) } ?: DTCSeverity.UNKNOWN,
            possibleCauses = entity.possibleCauses?.split(CAUSE_DELIMITER) ?: emptyList(),
            suggestedActions = entity.suggestedActions?.split(ACTION_DELIMITER) ?: emptyList(),
            technicalNotes = entity.technicalNotes,
            commonFixes = entity.commonFixes?.split(FIX_DELIMITER) ?: emptyList(),
            estimatedRepairCost = entity.estimatedRepairCost,
            laborHours = entity.laborHours,
            difficultyLevel = entity.difficultyLevel?.let { parseDifficultyLevel(it) },
            partsRequired = entity.partsRequired?.split(PARTS_DELIMITER) ?: emptyList(),
            relatedCodes = entity.relatedCodes?.split(CODE_DELIMITER) ?: emptyList(),
            manufacturer = entity.manufacturer,
            applicableModels = entity.applicableModels?.split(MODEL_DELIMITER) ?: emptyList(),
            lastUpdated = entity.lastUpdated
        )
    }

    /**
     * Enriches a DTC domain model with description data.
     * 
     * @param dtc The base DTC model
     * @param descriptionEntity The description entity to merge
     * @return Enriched DTC with description data
     */
    fun enrichWithDescription(dtc: DTC, descriptionEntity: DTCDescriptionEntity?): DTC {
        if (descriptionEntity == null) return dtc
        
        return dtc.copy(
            description = descriptionEntity.description ?: dtc.description,
            severity = descriptionEntity.severity?.let { parseDTCSeverity(it) } ?: dtc.severity,
            possibleCauses = descriptionEntity.possibleCauses?.split(CAUSE_DELIMITER) ?: dtc.possibleCauses,
            suggestedActions = descriptionEntity.suggestedActions?.split(ACTION_DELIMITER) ?: dtc.suggestedActions,
            system = descriptionEntity.system ?: dtc.system,
            subsystem = descriptionEntity.subsystem ?: dtc.subsystem
        )
    }

    /**
     * Enriches multiple DTCs with their descriptions.
     * 
     * @param dtcs List of base DTC models
     * @param descriptions Map of code to description entity
     * @return List of enriched DTCs
     */
    fun enrichWithDescriptions(
        dtcs: List<DTC>,
        descriptions: Map<String, DTCDescriptionEntity>
    ): List<DTC> {
        return dtcs.map { dtc ->
            enrichWithDescription(dtc, descriptions[dtc.code])
        }
    }

    // ==================== History Entity Mappings ====================

    /**
     * Converts a DTC to a history record entity.
     * 
     * @param dtc The DTC to record
     * @param sessionId Session when DTC was found
     * @param vehicleId Vehicle identifier
     * @param wasCleared Whether DTC was cleared in this session
     * @return DTCHistoryEntity for history tracking
     */
    fun toHistoryEntity(
        dtc: DTC,
        sessionId: String,
        vehicleId: String,
        wasCleared: Boolean = false
    ): DTCHistoryEntity {
        return DTCHistoryEntity(
            id = "${dtc.code}_${sessionId}_${System.currentTimeMillis()}",
            code = dtc.code,
            sessionId = sessionId,
            vehicleId = vehicleId,
            type = dtc.type.name,
            severity = dtc.severity.name,
            description = dtc.description,
            ecuAddress = dtc.ecuAddress,
            ecuName = dtc.ecuName,
            mileage = dtc.mileage,
            timestamp = dtc.timestamp,
            wasCleared = wasCleared,
            clearedAt = if (wasCleared) System.currentTimeMillis() else null,
            createdAt = System.currentTimeMillis()
        )
    }

    /**
     * Converts a history entity to a DTCHistoryRecord domain model.
     * 
     * @param entity The history entity
     * @return DTCHistoryRecord domain model
     */
    fun toHistoryDomain(entity: DTCHistoryEntity): DTCHistoryRecord {
        return DTCHistoryRecord(
            id = entity.id,
            code = entity.code,
            sessionId = entity.sessionId,
            vehicleId = entity.vehicleId,
            type = parseDTCType(entity.type),
            severity = parseDTCSeverity(entity.severity),
            description = entity.description,
            ecuAddress = entity.ecuAddress,
            ecuName = entity.ecuName,
            mileage = entity.mileage,
            timestamp = entity.timestamp,
            wasCleared = entity.wasCleared,
            clearedAt = entity.clearedAt,
            createdAt = entity.createdAt
        )
    }

    /**
     * Converts a list of history entities to domain models.
     * 
     * @param entities List of history entities
     * @return List of DTCHistoryRecord domain models
     */
    fun toHistoryDomainList(entities: List<DTCHistoryEntity>): List<DTCHistoryRecord> {
        return entities.map { toHistoryDomain(it) }
    }

    /**
     * Converts multiple DTCs to history records for batch insertion.
     * 
     * @param dtcs List of DTCs
     * @param sessionId Session ID
     * @param vehicleId Vehicle ID
     * @param wasCleared Whether DTCs were cleared
     * @return List of history entities
     */
    fun toHistoryEntityList(
        dtcs: List<DTC>,
        sessionId: String,
        vehicleId: String,
        wasCleared: Boolean = false
    ): List<DTCHistoryEntity> {
        return dtcs.map { toHistoryEntity(it, sessionId, vehicleId, wasCleared) }
    }

    // ==================== Freeze Frame Mappings ====================

    /**
     * Converts a FreezeFrame domain model to entity.
     * 
     * @param freezeFrame The freeze frame domain model
     * @param sessionId Associated session ID
     * @return FreezeFrameEntity for database storage
     */
    fun toFreezeFrameEntity(
        freezeFrame: FreezeFrame,
        sessionId: String
    ): FreezeFrameEntity {
        return FreezeFrameEntity(
            id = freezeFrame.id,
            dtcCode = freezeFrame.dtcCode,
            sessionId = sessionId,
            frameNumber = freezeFrame.frameNumber,
            timestamp = freezeFrame.timestamp,
            pidValues = serializePIDValues(freezeFrame.pidValues),
            engineLoad = freezeFrame.engineLoad,
            coolantTemp = freezeFrame.coolantTemp,
            shortTermFuelTrim = freezeFrame.shortTermFuelTrim,
            longTermFuelTrim = freezeFrame.longTermFuelTrim,
            intakeManifoldPressure = freezeFrame.intakeManifoldPressure,
            engineRPM = freezeFrame.engineRPM,
            vehicleSpeed = freezeFrame.vehicleSpeed,
            timingAdvance = freezeFrame.timingAdvance,
            intakeAirTemp = freezeFrame.intakeAirTemp,
            mafRate = freezeFrame.mafRate,
            throttlePosition = freezeFrame.throttlePosition,
            runtimeSinceStart = freezeFrame.runtimeSinceStart,
            distanceWithMIL = freezeFrame.distanceWithMIL,
            fuelLevel = freezeFrame.fuelLevel,
            barometricPressure = freezeFrame.barometricPressure,
            controlModuleVoltage = freezeFrame.controlModuleVoltage,
            ambientAirTemp = freezeFrame.ambientAirTemp,
            rawData = freezeFrame.rawData,
            createdAt = System.currentTimeMillis()
        )
    }

    /**
     * Converts a FreezeFrameEntity to domain model.
     * 
     * @param entity The database entity
     * @return FreezeFrame domain model
     */
    fun toFreezeFrameDomain(entity: FreezeFrameEntity): FreezeFrame {
        return FreezeFrame(
            id = entity.id,
            dtcCode = entity.dtcCode,
            frameNumber = entity.frameNumber,
            timestamp = entity.timestamp,
            pidValues = deserializePIDValues(entity.pidValues),
            engineLoad = entity.engineLoad,
            coolantTemp = entity.coolantTemp,
            shortTermFuelTrim = entity.shortTermFuelTrim,
            longTermFuelTrim = entity.longTermFuelTrim,
            intakeManifoldPressure = entity.intakeManifoldPressure,
            engineRPM = entity.engineRPM,
            vehicleSpeed = entity.vehicleSpeed,
            timingAdvance = entity.timingAdvance,
            intakeAirTemp = entity.intakeAirTemp,
            mafRate = entity.mafRate,
            throttlePosition = entity.throttlePosition,
            runtimeSinceStart = entity.runtimeSinceStart,
            distanceWithMIL = entity.distanceWithMIL,
            fuelLevel = entity.fuelLevel,
            barometricPressure = entity.barometricPressure,
            controlModuleVoltage = entity.controlModuleVoltage,
            ambientAirTemp = entity.ambientAirTemp,
            rawData = entity.rawData
        )
    }

    /**
     * Converts a list of freeze frame entities to domain models.
     * 
     * @param entities List of entities
     * @return List of domain models
     */
    fun toFreezeFrameDomainList(entities: List<FreezeFrameEntity>): List<FreezeFrame> {
        return entities.map { toFreezeFrameDomain(it) }
    }

    // ==================== DTC Status Mapping ====================

    /**
     * Converts a DTC status bitmask to DTCStatusInfo.
     * 
     * @param statusByte The status byte from OBD-II response
     * @return DTCStatusInfo with parsed flags
     */
    fun parseStatusByte(statusByte: Int): DTCStatusInfo {
        return DTCStatusInfo(
            testFailed = (statusByte and 0x01) != 0,
            testFailedThisMonitoringCycle = (statusByte and 0x02) != 0,
            pendingDTC = (statusByte and 0x04) != 0,
            confirmedDTC = (statusByte and 0x08) != 0,
            testNotCompletedSinceLastClear = (statusByte and 0x10) != 0,
            testFailedSinceLastClear = (statusByte and 0x20) != 0,
            testNotCompletedThisMonitoringCycle = (statusByte and 0x40) != 0,
            warningIndicatorRequested = (statusByte and 0x80) != 0
        )
    }

    /**
     * Converts DTCStatusInfo to a status byte.
     * 
     * @param status The status info
     * @return Status byte
     */
    fun toStatusByte(status: DTCStatusInfo): Int {
        var byte = 0
        if (status.testFailed) byte = byte or 0x01
        if (status.testFailedThisMonitoringCycle) byte = byte or 0x02
        if (status.pendingDTC) byte = byte or 0x04
        if (status.confirmedDTC) byte = byte or 0x08
        if (status.testNotCompletedSinceLastClear) byte = byte or 0x10
        if (status.testFailedSinceLastClear) byte = byte or 0x20
        if (status.testNotCompletedThisMonitoringCycle) byte = byte or 0x40
        if (status.warningIndicatorRequested) byte = byte or 0x80
        return byte
    }

    // ==================== Parsing Helper Methods ====================

    /**
     * Parses DTC type from string.
     */
    private fun parseDTCType(typeString: String?): DTCType {
        return try {
            typeString?.let { DTCType.valueOf(it) } ?: DTCType.STORED
        } catch (e: IllegalArgumentException) {
            DTCType.STORED
        }
    }

    /**
     * Parses DTC status from string.
     */
    private fun parseDTCStatus(statusString: String): DTCStatus {
        return try {
            DTCStatus.valueOf(statusString)
        } catch (e: IllegalArgumentException) {
            DTCStatus.UNKNOWN
        }
    }

    /**
     * Parses DTC severity from string.
     */
    private fun parseDTCSeverity(severityString: String?): DTCSeverity {
        return try {
            severityString?.let { DTCSeverity.valueOf(it) } ?: DTCSeverity.UNKNOWN
        } catch (e: IllegalArgumentException) {
            DTCSeverity.UNKNOWN
        }
    }

    /**
     * Parses difficulty level from string.
     */
    private fun parseDifficultyLevel(levelString: String): RepairDifficulty {
        return try {
            RepairDifficulty.valueOf(levelString)
        } catch (e: IllegalArgumentException) {
            RepairDifficulty.UNKNOWN
        }
    }

    /**
     * Parses system name from DTC code.
     */
    private fun parseSystemFromCode(code: String): String {
        if (code.length < 2) return "Unknown"
        
        val prefix = code[0].uppercaseChar()
        val digit = code.getOrNull(1)?.digitToIntOrNull() ?: 0
        
        return when (prefix) {
            'P' -> when (digit) {
                0 -> "Powertrain (Generic)"
                1 -> "Powertrain (Manufacturer)"
                2 -> "Powertrain (Generic - Fuel/Air)"
                3 -> "Powertrain (Generic - Ignition)"
                else -> "Powertrain"
            }
            'C' -> when (digit) {
                0 -> "Chassis (Generic)"
                1, 2, 3 -> "Chassis (Manufacturer)"
                else -> "Chassis"
            }
            'B' -> when (digit) {
                0 -> "Body (Generic)"
                1, 2, 3 -> "Body (Manufacturer)"
                else -> "Body"
            }
            'U' -> when (digit) {
                0 -> "Network (Generic)"
                1, 2, 3 -> "Network (Manufacturer)"
                else -> "Network"
            }
            else -> "Unknown"
        }
    }

    // ==================== Serialization Helpers ====================

    /**
     * Serializes PID values map to string for storage.
     */
    private fun serializePIDValues(pidValues: Map<Int, Any>): String {
        return pidValues.entries.joinToString(PID_ENTRY_DELIMITER) { (pid, value) ->
            "${pid}${PID_KEY_VALUE_DELIMITER}${serializeValue(value)}"
        }
    }

    /**
     * Deserializes PID values string to map.
     */
    private fun deserializePIDValues(serialized: String?): Map<Int, Any> {
        if (serialized.isNullOrBlank()) return emptyMap()
        
        return try {
            serialized.split(PID_ENTRY_DELIMITER)
                .filter { it.contains(PID_KEY_VALUE_DELIMITER) }
                .associate { entry ->
                    val parts = entry.split(PID_KEY_VALUE_DELIMITER, limit = 2)
                    val pid = parts[0].toIntOrNull() ?: 0
                    val value = deserializeValue(parts.getOrElse(1) { "0" })
                    pid to value
                }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * Serializes a single value.
     */
    private fun serializeValue(value: Any): String {
        return when (value) {
            is Number -> "N:${value}"
            is Boolean -> "B:${value}"
            is ByteArray -> "A:${value.joinToString(",") { it.toString() }}"
            else -> "S:${value}"
        }
    }

    /**
     * Deserializes a single value.
     */
    private fun deserializeValue(serialized: String): Any {
        val parts = serialized.split(":", limit = 2)
        if (parts.size != 2) return serialized
        
        return when (parts[0]) {
            "N" -> parts[1].toDoubleOrNull() ?: 0.0
            "B" -> parts[1].toBooleanStrictOrNull() ?: false
            "A" -> parts[1].split(",").mapNotNull { it.toByteOrNull() }.toByteArray()
            "S" -> parts[1]
            else -> serialized
        }
    }

    // ==================== Utility Methods ====================

    /**
     * Creates a summary of DTCs for display.
     * 
     * @param dtcs List of DTCs
     * @return DTCSummary with aggregated information
     */
    fun createSummary(dtcs: List<DTC>): DTCSummary {
        return DTCSummary(
            totalCount = dtcs.size,
            storedCount = dtcs.count { it.isStored },
            pendingCount = dtcs.count { it.isPending },
            permanentCount = dtcs.count { it.isPermanent },
            criticalCount = dtcs.count { it.severity == DTCSeverity.CRITICAL },
            warningCount = dtcs.count { it.severity == DTCSeverity.WARNING },
            milIlluminated = dtcs.any { it.illuminatesMIL },
            affectedSystems = dtcs.map { it.system }.distinct(),
            codes = dtcs.map { it.code }
        )
    }

    /**
     * Groups DTCs by various criteria.
     * 
     * @param dtcs List of DTCs
     * @return DTCGrouping with various groupings
     */
    fun createGrouping(dtcs: List<DTC>): DTCGrouping {
        return DTCGrouping(
            byType = dtcs.groupBy { it.type },
            bySystem = dtcs.groupBy { it.system },
            bySeverity = dtcs.groupBy { it.severity },
            byECU = dtcs.groupBy { it.ecuAddress ?: -1 }
        )
    }

    /**
     * Merges duplicate DTCs, keeping the most recent.
     * 
     * @param dtcs List of DTCs that may contain duplicates
     * @return List with duplicates merged
     */
    fun mergeDuplicates(dtcs: List<DTC>): List<DTC> {
        return dtcs
            .groupBy { it.code }
            .map { (_, duplicates) ->
                if (duplicates.size == 1) {
                    duplicates.first()
                } else {
                    // Keep the most recent, but increment occurrence count
                    val newest = duplicates.maxByOrNull { it.timestamp }
                    if (newest != null) {
                        newest.copy(
                            occurrenceCount = duplicates.sumOf { it.occurrenceCount }.coerceAtLeast(duplicates.size),
                            firstOccurrence = duplicates.minOfOrNull { it.firstOccurrence ?: it.timestamp },
                            lastOccurrence = duplicates.maxOfOrNull { it.lastOccurrence ?: it.timestamp }
                        )
                    } else {
                        // This shouldn't happen since duplicates is not empty, but handle safely
                        duplicates.first()
                    }
                }
            }
    }

    /**
     * Compares two lists of DTCs to find new and resolved codes.
     * 
     * @param previous Previous DTC list
     * @param current Current DTC list
     * @return DTCComparison with new and resolved codes
     */
    fun compareDTCs(previous: List<DTC>, current: List<DTC>): DTCComparison {
        val previousCodes = previous.map { it.code }.toSet()
        val currentCodes = current.map { it.code }.toSet()
        
        return DTCComparison(
            newCodes = currentCodes - previousCodes,
            resolvedCodes = previousCodes - currentCodes,
            unchangedCodes = previousCodes.intersect(currentCodes),
            newDTCs = current.filter { it.code in (currentCodes - previousCodes) },
            resolvedDTCs = previous.filter { it.code in (previousCodes - currentCodes) }
        )
    }

    // ==================== Companion Object ====================

    companion object {
        // Delimiters for serialization
        private const val CAUSE_DELIMITER = "|||"
        private const val ACTION_DELIMITER = "|||"
        private const val FIX_DELIMITER = "|||"
        private const val PARTS_DELIMITER = "|||"
        private const val CODE_DELIMITER = ","
        private const val MODEL_DELIMITER = ","
        private const val PID_ENTRY_DELIMITER = ";"
        private const val PID_KEY_VALUE_DELIMITER = "="
    }
}

// ==================== Supporting Data Classes ====================

/**
 * Detailed DTC description information.
 */
data class DTCDescription(
    val code: String,
    val description: String?,
    val detailedDescription: String?,
    val system: String?,
    val subsystem: String?,
    val severity: DTCSeverity,
    val possibleCauses: List<String>,
    val suggestedActions: List<String>,
    val technicalNotes: String?,
    val commonFixes: List<String>,
    val estimatedRepairCost: String?,
    val laborHours: Float?,
    val difficultyLevel: RepairDifficulty?,
    val partsRequired: List<String>,
    val relatedCodes: List<String>,
    val manufacturer: String?,
    val applicableModels: List<String>,
    val lastUpdated: Long?
)

/**
 * DTC history record for tracking occurrences.
 */
data class DTCHistoryRecord(
    val id: String,
    val code: String,
    val sessionId: String,
    val vehicleId: String,
    val type: DTCType,
    val severity: DTCSeverity,
    val description: String?,
    val ecuAddress: Int?,
    val ecuName: String?,
    val mileage: Int?,
    val timestamp: Long,
    val wasCleared: Boolean,
    val clearedAt: Long?,
    val createdAt: Long
)

/**
 * DTC status information from status byte.
 */
data class DTCStatusInfo(
    val testFailed: Boolean,
    val testFailedThisMonitoringCycle: Boolean,
    val pendingDTC: Boolean,
    val confirmedDTC: Boolean,
    val testNotCompletedSinceLastClear: Boolean,
    val testFailedSinceLastClear: Boolean,
    val testNotCompletedThisMonitoringCycle: Boolean,
    val warningIndicatorRequested: Boolean
) {
    /** Whether this represents an active fault */
    val isActive: Boolean
        get() = testFailed && confirmedDTC
    
    /** Whether MIL should be illuminated */
    val shouldIlluminateMIL: Boolean
        get() = warningIndicatorRequested && confirmedDTC
}

/**
 * Summary of DTCs for quick overview.
 */
data class DTCSummary(
    val totalCount: Int,
    val storedCount: Int,
    val pendingCount: Int,
    val permanentCount: Int,
    val criticalCount: Int,
    val warningCount: Int,
    val milIlluminated: Boolean,
    val affectedSystems: List<String>,
    val codes: List<String>
) {
    /** Whether any issues are present */
    val hasIssues: Boolean
        get() = totalCount > 0
    
    /** Whether critical issues are present */
    val hasCriticalIssues: Boolean
        get() = criticalCount > 0 || milIlluminated
}

/**
 * Grouping of DTCs by various criteria.
 */
data class DTCGrouping(
    val byType: Map<DTCType, List<DTC>>,
    val bySystem: Map<String, List<DTC>>,
    val bySeverity: Map<DTCSeverity, List<DTC>>,
    val byECU: Map<Int, List<DTC>>
)

/**
 * Comparison between two DTC lists.
 */
data class DTCComparison(
    val newCodes: Set<String>,
    val resolvedCodes: Set<String>,
    val unchangedCodes: Set<String>,
    val newDTCs: List<DTC>,
    val resolvedDTCs: List<DTC>
) {
    /** Whether any changes occurred */
    val hasChanges: Boolean
        get() = newCodes.isNotEmpty() || resolvedCodes.isNotEmpty()
    
    /** Number of new codes */
    val newCount: Int
        get() = newCodes.size
    
    /** Number of resolved codes */
    val resolvedCount: Int
        get() = resolvedCodes.size
}

/**
 * Repair difficulty level.
 */
enum class RepairDifficulty(val displayName: String, val level: Int) {
    EASY("Easy", 1),
    MODERATE("Moderate", 2),
    DIFFICULT("Difficult", 3),
    PROFESSIONAL("Professional Only", 4),
    DEALER("Dealer Service Required", 5),
    UNKNOWN("Unknown", 0);
    
    /** Whether DIY repair is feasible */
    val isDIYFeasible: Boolean
        get() = level in 1..2
}

// ==================== Extension Functions ====================

/**
 * Converts a DTC code to its system prefix.
 */
fun String.toDTCSystemPrefix(): Char? = this.firstOrNull()?.uppercaseChar()

/**
 * Checks if a DTC code is manufacturer-specific.
 */
fun String.isManufacturerSpecificDTC(): Boolean {
    if (length < 2) return false
    val digit = this[1].digitToIntOrNull() ?: return false
    return digit in 1..3
}

/**
 * Checks if a DTC code is generic (SAE standard).
 */
fun String.isGenericDTC(): Boolean {
    if (length < 2) return false
    val digit = this[1].digitToIntOrNull() ?: return false
    return digit == 0
}

/**
 * Gets the system type from a DTC code.
 */
fun String.getDTCSystemType(): String {
    val prefix = this.toDTCSystemPrefix() ?: return "Unknown"
    return when (prefix) {
        'P' -> "Powertrain"
        'C' -> "Chassis"
        'B' -> "Body"
        'U' -> "Network"
        else -> "Unknown"
    }
}

/**
 * Validates a DTC code format.
 */
fun String.isValidDTCCode(): Boolean {
    if (length != 5) return false
    val prefix = this[0].uppercaseChar()
    if (prefix !in listOf('P', 'C', 'B', 'U')) return false
    return this.substring(1).all { it.isDigit() || it.uppercaseChar() in 'A'..'F' }
}