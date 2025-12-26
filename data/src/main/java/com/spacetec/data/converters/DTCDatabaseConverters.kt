package com.spacetec.data.converters

import androidx.room.TypeConverter
import com.spacetec.domain.models.diagnostic.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Duration

/**
 * Enhanced type converters specifically for the DTC Database System.
 *
 * Provides comprehensive conversion between domain models and database-compatible
 * formats for all DTC-related entities including repair procedures, TSBs, and analytics.
 *
 * @author SpaceTec Team
 * @since 1.0.0
 */
class DTCDatabaseConverters {

    companion object {
        /**
         * JSON configuration for serialization operations.
         */
        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
            explicitNulls = false
        }

        // Delimiters for complex data serialization
        private const val LIST_DELIMITER = "|||"
        private const val FIELD_DELIMITER = ":::"
        private const val KEY_VALUE_DELIMITER = "==="
    }

    // ==================== DTC ENUM CONVERTERS ====================

    @TypeConverter
    fun dtcSystemToString(system: DTCSystem?): String? = system?.name

    @TypeConverter
    fun stringToDTCSystem(value: String?): DTCSystem? {
        return value?.let {
            try {
                DTCSystem.valueOf(it)
            } catch (e: Exception) {
                DTCSystem.POWERTRAIN
            }
        }
    }

    @TypeConverter
    fun dtcSubsystemToString(subsystem: DTCSubsystem?): String? = subsystem?.name

    @TypeConverter
    fun stringToDTCSubsystem(value: String?): DTCSubsystem? {
        return value?.let {
            try {
                DTCSubsystem.valueOf(it)
            } catch (e: Exception) {
                DTCSubsystem.UNKNOWN
            }
        }
    }

    @TypeConverter
    fun dtcCodeTypeToString(codeType: DTCCodeType?): String? = codeType?.name

    @TypeConverter
    fun stringToDTCCodeType(value: String?): DTCCodeType? {
        return value?.let {
            try {
                DTCCodeType.valueOf(it)
            } catch (e: Exception) {
                DTCCodeType.GENERIC
            }
        }
    }

    @TypeConverter
    fun dtcSeverityToString(severity: DTCSeverity?): String? = severity?.name

    @TypeConverter
    fun stringToDTCSeverity(value: String?): DTCSeverity? {
        return value?.let {
            try {
                DTCSeverity.valueOf(it)
            } catch (e: Exception) {
                DTCSeverity.UNKNOWN
            }
        }
    }

    // ==================== REPAIR PROCEDURE CONVERTERS ====================

    @TypeConverter
    fun repairDifficultyToString(difficulty: RepairDifficulty?): String? = difficulty?.name

    @TypeConverter
    fun stringToRepairDifficulty(value: String?): RepairDifficulty? {
        return value?.let {
            try {
                RepairDifficulty.valueOf(it)
            } catch (e: Exception) {
                RepairDifficulty.MODERATE
            }
        }
    }

    @TypeConverter
    fun repairCategoryToString(category: RepairCategory?): String? = category?.name

    @TypeConverter
    fun stringToRepairCategory(value: String?): RepairCategory? {
        return value?.let {
            try {
                RepairCategory.valueOf(it)
            } catch (e: Exception) {
                RepairCategory.GENERAL
            }
        }
    }

    @TypeConverter
    fun partCategoryToString(category: PartCategory?): String? = category?.name

    @TypeConverter
    fun stringToPartCategory(value: String?): PartCategory? {
        return value?.let {
            try {
                PartCategory.valueOf(it)
            } catch (e: Exception) {
                PartCategory.OTHER
            }
        }
    }

    // ==================== TSB CONVERTERS ====================

    @TypeConverter
    fun tsbCategoryToString(category: TSBCategory?): String? = category?.name

    @TypeConverter
    fun stringToTSBCategory(value: String?): TSBCategory? {
        return value?.let {
            try {
                TSBCategory.valueOf(it)
            } catch (e: Exception) {
                TSBCategory.TECHNICAL_UPDATE
            }
        }
    }

    @TypeConverter
    fun tsbSeverityToString(severity: TSBSeverity?): String? = severity?.name

    @TypeConverter
    fun stringToTSBSeverity(value: String?): TSBSeverity? {
        return value?.let {
            try {
                TSBSeverity.valueOf(it)
            } catch (e: Exception) {
                TSBSeverity.MEDIUM
            }
        }
    }

    @TypeConverter
    fun tsbStatusToString(status: TSBStatus?): String? = status?.name

    @TypeConverter
    fun stringToTSBStatus(value: String?): TSBStatus? {
        return value?.let {
            try {
                TSBStatus.valueOf(it)
            } catch (e: Exception) {
                TSBStatus.ACTIVE
            }
        }
    }

    @TypeConverter
    fun attachmentCategoryToString(category: AttachmentCategory?): String? = category?.name

    @TypeConverter
    fun stringToAttachmentCategory(value: String?): AttachmentCategory? {
        return value?.let {
            try {
                AttachmentCategory.valueOf(it)
            } catch (e: Exception) {
                AttachmentCategory.DOCUMENT
            }
        }
    }

    // ==================== SESSION CONVERTERS ====================

    @TypeConverter
    fun diagnosticSessionTypeToString(type: DiagnosticSessionType?): String? = type?.name

    @TypeConverter
    fun stringToDiagnosticSessionType(value: String?): DiagnosticSessionType? {
        return value?.let {
            try {
                DiagnosticSessionType.valueOf(it)
            } catch (e: Exception) {
                DiagnosticSessionType.QUICK_SCAN
            }
        }
    }

    @TypeConverter
    fun sessionStatusToString(status: SessionStatus?): String? = status?.name

    @TypeConverter
    fun stringToSessionStatus(value: String?): SessionStatus? {
        return value?.let {
            try {
                SessionStatus.valueOf(it)
            } catch (e: Exception) {
                SessionStatus.IN_PROGRESS
            }
        }
    }

    @TypeConverter
    fun mileageUnitToString(unit: MileageUnit?): String? = unit?.name

    @TypeConverter
    fun stringToMileageUnit(value: String?): MileageUnit? {
        return value?.let {
            try {
                MileageUnit.valueOf(it)
            } catch (e: Exception) {
                MileageUnit.KILOMETERS
            }
        }
    }

    @TypeConverter
    fun healthStatusToString(status: HealthStatus?): String? = status?.name

    @TypeConverter
    fun stringToHealthStatus(value: String?): HealthStatus? {
        return value?.let {
            try {
                HealthStatus.valueOf(it)
            } catch (e: Exception) {
                HealthStatus.FAIR
            }
        }
    }

    // ==================== ANALYTICS CONVERTERS ====================

    @TypeConverter
    fun patternTypeToString(type: PatternType?): String? = type?.name

    @TypeConverter
    fun stringToPatternType(value: String?): PatternType? {
        return value?.let {
            try {
                PatternType.valueOf(it)
            } catch (e: Exception) {
                PatternType.SEQUENTIAL
            }
        }
    }

    @TypeConverter
    fun trendDirectionToString(direction: TrendDirection?): String? = direction?.name

    @TypeConverter
    fun stringToTrendDirection(value: String?): TrendDirection? {
        return value?.let {
            try {
                TrendDirection.valueOf(it)
            } catch (e: Exception) {
                TrendDirection.STABLE
            }
        }
    }

    @TypeConverter
    fun seasonToString(season: Season?): String? = season?.name

    @TypeConverter
    fun stringToSeason(value: String?): Season? {
        return value?.let {
            try {
                Season.valueOf(it)
            } catch (e: Exception) {
                Season.SPRING
            }
        }
    }

    @TypeConverter
    fun correlationTypeToString(type: CorrelationType?): String? = type?.name

    @TypeConverter
    fun stringToCorrelationType(value: String?): CorrelationType? {
        return value?.let {
            try {
                CorrelationType.valueOf(it)
            } catch (e: Exception) {
                CorrelationType.RELATED
            }
        }
    }

    @TypeConverter
    fun priorityToString(priority: Priority?): String? = priority?.name

    @TypeConverter
    fun stringToPriority(value: String?): Priority? {
        return value?.let {
            try {
                Priority.valueOf(it)
            } catch (e: Exception) {
                Priority.MEDIUM
            }
        }
    }

    @TypeConverter
    fun maintenanceCategoryToString(category: MaintenanceCategory?): String? = category?.name

    @TypeConverter
    fun stringToMaintenanceCategory(value: String?): MaintenanceCategory? {
        return value?.let {
            try {
                MaintenanceCategory.valueOf(it)
            } catch (e: Exception) {
                MaintenanceCategory.CORRECTIVE
            }
        }
    }

    // ==================== SEARCH CONVERTERS ====================

    @TypeConverter
    fun dtcSortOptionToString(option: DTCSortOption?): String? = option?.name

    @TypeConverter
    fun stringToDTCSortOption(value: String?): DTCSortOption? {
        return value?.let {
            try {
                DTCSortOption.valueOf(it)
            } catch (e: Exception) {
                DTCSortOption.RELEVANCE
            }
        }
    }

    @TypeConverter
    fun sortOrderToString(order: SortOrder?): String? = order?.name

    @TypeConverter
    fun stringToSortOrder(value: String?): SortOrder? {
        return value?.let {
            try {
                SortOrder.valueOf(it)
            } catch (e: Exception) {
                SortOrder.DESCENDING
            }
        }
    }

    @TypeConverter
    fun dtcStatusFilterToString(filter: DTCStatusFilter?): String? = filter?.name

    @TypeConverter
    fun stringToDTCStatusFilter(value: String?): DTCStatusFilter? {
        return value?.let {
            try {
                DTCStatusFilter.valueOf(it)
            } catch (e: Exception) {
                DTCStatusFilter.ANY
            }
        }
    }

    // ==================== DURATION CONVERTERS ====================

    @TypeConverter
    fun durationToMinutes(duration: Duration?): Long? {
        return duration?.toMinutes()
    }

    @TypeConverter
    fun minutesToDuration(minutes: Long?): Duration? {
        return minutes?.let { Duration.ofMinutes(it) }
    }

    // ==================== COMPLEX DATA CONVERTERS ====================

    /**
     * Converts DTCStatus to JSON string.
     */
    @TypeConverter
    fun dtcStatusToJson(status: DTCStatus?): String? {
        return status?.let {
            try {
                json.encodeToString(
                    mapOf(
                        "testFailed" to it.testFailed,
                        "testFailedThisCycle" to it.testFailedThisCycle,
                        "pendingDTC" to it.pendingDTC,
                        "confirmedDTC" to it.confirmedDTC,
                        "testNotCompletedSinceClear" to it.testNotCompletedSinceClear,
                        "testFailedSinceClear" to it.testFailedSinceClear,
                        "testNotCompletedThisCycle" to it.testNotCompletedThisCycle,
                        "warningIndicatorRequested" to it.warningIndicatorRequested,
                        "rawByte" to it.rawByte?.toInt(),
                        "isPermanent" to it.isPermanent,
                        "isStored" to it.isStored
                    )
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Converts JSON string to DTCStatus.
     */
    @TypeConverter
    fun jsonToDTCStatus(jsonString: String?): DTCStatus? {
        return jsonString?.let {
            try {
                val map = json.decodeFromString<Map<String, Any?>>(it)
                DTCStatus(
                    testFailed = map["testFailed"] as? Boolean ?: false,
                    testFailedThisCycle = map["testFailedThisCycle"] as? Boolean ?: false,
                    pendingDTC = map["pendingDTC"] as? Boolean ?: false,
                    confirmedDTC = map["confirmedDTC"] as? Boolean ?: false,
                    testNotCompletedSinceClear = map["testNotCompletedSinceClear"] as? Boolean ?: false,
                    testFailedSinceClear = map["testFailedSinceClear"] as? Boolean ?: false,
                    testNotCompletedThisCycle = map["testNotCompletedThisCycle"] as? Boolean ?: false,
                    warningIndicatorRequested = map["warningIndicatorRequested"] as? Boolean ?: false,
                    rawByte = (map["rawByte"] as? Int)?.toByte(),
                    isPermanent = map["isPermanent"] as? Boolean ?: false,
                    isStored = map["isStored"] as? Boolean ?: false
                )
            } catch (e: Exception) {
                DTCStatus.DEFAULT
            }
        }
    }

    /**
     * Converts CostRange to JSON string.
     */
    @TypeConverter
    fun costRangeToJson(costRange: CostRange?): String? {
        return costRange?.let {
            try {
                json.encodeToString(
                    mapOf(
                        "min" to it.min,
                        "max" to it.max,
                        "currency" to it.currency
                    )
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Converts JSON string to CostRange.
     */
    @TypeConverter
    fun jsonToCostRange(jsonString: String?): CostRange? {
        return jsonString?.let {
            try {
                val map = json.decodeFromString<Map<String, Any?>>(it)
                CostRange(
                    min = (map["min"] as? Double) ?: 0.0,
                    max = (map["max"] as? Double) ?: 0.0,
                    currency = (map["currency"] as? String) ?: "USD"
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Converts VehicleCoverage list to JSON string.
     */
    @TypeConverter
    fun vehicleCoverageListToJson(coverage: List<VehicleCoverage>?): String? {
        return coverage?.let {
            try {
                json.encodeToString(
                    it.map { vc ->
                        mapOf(
                            "make" to vc.make,
                            "model" to vc.model,
                            "yearStart" to vc.yearStart,
                            "yearEnd" to vc.yearEnd,
                            "engines" to vc.engines,
                            "transmissions" to vc.transmissions,
                            "regions" to vc.regions
                        )
                    }
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Converts JSON string to VehicleCoverage list.
     */
    @TypeConverter
    fun jsonToVehicleCoverageList(jsonString: String?): List<VehicleCoverage>? {
        return jsonString?.let {
            try {
                val list = json.decodeFromString<List<Map<String, Any?>>>(it)
                list.map { map ->
                    VehicleCoverage(
                        make = (map["make"] as? String) ?: "",
                        model = map["model"] as? String,
                        yearStart = (map["yearStart"] as? Int) ?: 1990,
                        yearEnd = (map["yearEnd"] as? Int) ?: 2030,
                        engines = (map["engines"] as? List<String>) ?: emptyList(),
                        transmissions = (map["transmissions"] as? List<String>) ?: emptyList(),
                        regions = (map["regions"] as? List<String>) ?: emptyList()
                    )
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    /**
     * Converts TimeRange to JSON string.
     */
    @TypeConverter
    fun timeRangeToJson(timeRange: TimeRange?): String? {
        return timeRange?.let {
            try {
                json.encodeToString(
                    mapOf(
                        "startTime" to it.startTime,
                        "endTime" to it.endTime
                    )
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Converts JSON string to TimeRange.
     */
    @TypeConverter
    fun jsonToTimeRange(jsonString: String?): TimeRange? {
        return jsonString?.let {
            try {
                val map = json.decodeFromString<Map<String, Any?>>(it)
                TimeRange(
                    startTime = (map["startTime"] as? Long) ?: 0L,
                    endTime = (map["endTime"] as? Long) ?: System.currentTimeMillis()
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    // ==================== STRING LIST CONVERTERS ====================

    /**
     * Converts string list to delimited string.
     */
    @TypeConverter
    fun stringListToString(list: List<String>?): String? {
        return list?.joinToString(LIST_DELIMITER)
    }

    /**
     * Converts delimited string to string list.
     */
    @TypeConverter
    fun stringToStringList(value: String?): List<String>? {
        return value?.let {
            if (it.isBlank()) emptyList()
            else it.split(LIST_DELIMITER).filter { item -> item.isNotBlank() }
        }
    }

    /**
     * Converts integer list to delimited string.
     */
    @TypeConverter
    fun intListToString(list: List<Int>?): String? {
        return list?.joinToString(LIST_DELIMITER) { it.toString() }
    }

    /**
     * Converts delimited string to integer list.
     */
    @TypeConverter
    fun stringToIntList(value: String?): List<Int>? {
        return value?.let {
            if (it.isBlank()) emptyList()
            else it.split(LIST_DELIMITER).mapNotNull { item -> item.toIntOrNull() }
        }
    }

    /**
     * Converts double list to delimited string.
     */
    @TypeConverter
    fun doubleListToString(list: List<Double>?): String? {
        return list?.joinToString(LIST_DELIMITER) { it.toString() }
    }

    /**
     * Converts delimited string to double list.
     */
    @TypeConverter
    fun stringToDoubleList(value: String?): List<Double>? {
        return value?.let {
            if (it.isBlank()) emptyList()
            else it.split(LIST_DELIMITER).mapNotNull { item -> item.toDoubleOrNull() }
        }
    }

    // ==================== MAP CONVERTERS ====================

    /**
     * Converts string-to-string map to JSON.
     */
    @TypeConverter
    fun stringMapToJson(map: Map<String, String>?): String? {
        return map?.let {
            try {
                json.encodeToString(it)
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Converts JSON to string-to-string map.
     */
    @TypeConverter
    fun jsonToStringMap(jsonString: String?): Map<String, String>? {
        return jsonString?.let {
            try {
                json.decodeFromString<Map<String, String>>(it)
            } catch (e: Exception) {
                emptyMap()
            }
        }
    }

    /**
     * Converts string-to-int map to JSON.
     */
    @TypeConverter
    fun stringIntMapToJson(map: Map<String, Int>?): String? {
        return map?.let {
            try {
                json.encodeToString(it)
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Converts JSON to string-to-int map.
     */
    @TypeConverter
    fun jsonToStringIntMap(jsonString: String?): Map<String, Int>? {
        return jsonString?.let {
            try {
                json.decodeFromString<Map<String, Int>>(it)
            } catch (e: Exception) {
                emptyMap()
            }
        }
    }

    /**
     * Converts string-to-double map to JSON.
     */
    @TypeConverter
    fun stringDoubleMapToJson(map: Map<String, Double>?): String? {
        return map?.let {
            try {
                json.encodeToString(it)
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Converts JSON to string-to-double map.
     */
    @TypeConverter
    fun jsonToStringDoubleMap(jsonString: String?): Map<String, Double>? {
        return jsonString?.let {
            try {
                json.decodeFromString<Map<String, Double>>(it)
            } catch (e: Exception) {
                emptyMap()
            }
        }
    }
}