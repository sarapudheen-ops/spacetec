package com.spacetec.data.mapper

import com.spacetec.core.database.entities.DiagnosticSessionEntity
import com.spacetec.core.database.entities.LiveDataSnapshotEntity
import com.spacetec.domain.models.diagnostic.*
import com.spacetec.domain.models.livedata.LiveDataValue
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mapper for DiagnosticSession conversions between domain and data layers.
 *
 * Handles comprehensive mapping of diagnostic sessions including metadata,
 * live data snapshots, and session analytics with proper serialization.
 *
 * @author SpaceTec Team
 * @since 1.0.0
 */
@Singleton
class DiagnosticSessionMapper @Inject constructor() {

    // ==================== Domain to Entity Mappings ====================

    /**
     * Converts DiagnosticSession domain model to database entity.
     */
    fun toEntity(session: DiagnosticSession): DiagnosticSessionEntity {
        return DiagnosticSessionEntity(
            id = session.id,
            vehicleId = session.vehicleId,
            vin = session.vin,
            type = session.type.name,
            status = session.status.name,
            startTime = session.startTime,
            endTime = session.endTime,
            protocol = session.protocol?.name,
            scannerId = session.scannerId,
            scannerName = session.scannerName,
            mileage = session.mileage,
            mileageUnit = session.mileageUnit.name,
            notes = session.notes,
            tags = serializeStringList(session.tags),
            dtcsCleared = session.dtcsCleared,
            dtcsClearedAt = session.dtcsClearedAt,
            metadata = serializeSessionMetadata(session.metadata),
            createdAt = session.createdAt,
            updatedAt = session.updatedAt
        )
    }

    /**
     * Converts LiveDataSnapshot domain model to database entity.
     */
    fun toLiveDataSnapshotEntity(
        snapshot: LiveDataSnapshot,
        sessionId: String
    ): LiveDataSnapshotEntity {
        return LiveDataSnapshotEntity(
            id = "${sessionId}_${snapshot.timestamp}",
            sessionId = sessionId,
            timestamp = snapshot.timestamp,
            values = serializeLiveDataValues(snapshot.values),
            label = snapshot.label,
            createdAt = System.currentTimeMillis()
        )
    }

    /**
     * Converts list of LiveDataSnapshots to entities.
     */
    fun toLiveDataSnapshotEntityList(
        snapshots: List<LiveDataSnapshot>,
        sessionId: String
    ): List<LiveDataSnapshotEntity> {
        return snapshots.map { toLiveDataSnapshotEntity(it, sessionId) }
    }

    // ==================== Entity to Domain Mappings ====================

    /**
     * Converts DiagnosticSessionEntity to domain model.
     */
    fun toDomain(
        entity: DiagnosticSessionEntity,
        dtcs: List<DTC> = emptyList(),
        freezeFrames: List<FreezeFrame> = emptyList(),
        liveDataSnapshots: List<LiveDataSnapshot> = emptyList(),
        monitorStatus: MonitorStatus? = null
    ): DiagnosticSession {
        return DiagnosticSession(
            id = entity.id,
            vehicleId = entity.vehicleId,
            vin = entity.vin,
            type = parseSessionType(entity.type),
            status = parseSessionStatus(entity.status),
            startTime = entity.startTime,
            endTime = entity.endTime,
            protocol = entity.protocol?.let { parseProtocolType(it) },
            scannerId = entity.scannerId,
            scannerName = entity.scannerName,
            mileage = entity.mileage,
            mileageUnit = parseMileageUnit(entity.mileageUnit),
            dtcs = dtcs,
            freezeFrames = freezeFrames,
            liveDataSnapshots = liveDataSnapshots,
            monitorStatus = monitorStatus,
            notes = entity.notes,
            tags = deserializeStringList(entity.tags),
            dtcsCleared = entity.dtcsCleared,
            dtcsClearedAt = entity.dtcsClearedAt,
            metadata = deserializeSessionMetadata(entity.metadata),
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }

    /**
     * Converts LiveDataSnapshotEntity to domain model.
     */
    fun toLiveDataSnapshotDomain(entity: LiveDataSnapshotEntity): LiveDataSnapshot {
        return LiveDataSnapshot(
            timestamp = entity.timestamp,
            values = deserializeLiveDataValues(entity.values),
            label = entity.label
        )
    }

    /**
     * Converts list of entities to domain models.
     */
    fun toDomainList(entities: List<DiagnosticSessionEntity>): List<DiagnosticSession> {
        return entities.map { toDomain(it) }
    }

    /**
     * Converts list of snapshot entities to domain models.
     */
    fun toLiveDataSnapshotDomainList(entities: List<LiveDataSnapshotEntity>): List<LiveDataSnapshot> {
        return entities.map { toLiveDataSnapshotDomain(it) }
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
     * Serializes SessionMetadata to string.
     */
    private fun serializeSessionMetadata(metadata: SessionMetadata): String {
        return buildString {
            append("appVersion=${metadata.appVersion ?: ""}")
            append(METADATA_DELIMITER)
            append("deviceModel=${metadata.deviceModel ?: ""}")
            append(METADATA_DELIMITER)
            append("androidVersion=${metadata.androidVersion ?: ""}")
            append(METADATA_DELIMITER)
            append("location=${metadata.location ?: ""}")
            append(METADATA_DELIMITER)
            append("technicianId=${metadata.technicianId ?: ""}")
            append(METADATA_DELIMITER)
            append("technicianName=${metadata.technicianName ?: ""}")
            append(METADATA_DELIMITER)
            append("workOrderId=${metadata.workOrderId ?: ""}")
            append(METADATA_DELIMITER)
            append("shopId=${metadata.shopId ?: ""}")
            append(METADATA_DELIMITER)
            append("shopName=${metadata.shopName ?: ""}")
            append(METADATA_DELIMITER)
            append("customFields=${serializeCustomFields(metadata.customFields)}")
        }
    }

    /**
     * Deserializes SessionMetadata from string.
     */
    private fun deserializeSessionMetadata(serialized: String?): SessionMetadata {
        if (serialized.isNullOrBlank()) return SessionMetadata()
        
        return try {
            val fields = serialized.split(METADATA_DELIMITER).associate { field ->
                val parts = field.split("=", limit = 2)
                if (parts.size == 2) parts[0] to parts[1] else parts[0] to ""
            }
            
            SessionMetadata(
                appVersion = fields["appVersion"]?.takeIf { it.isNotBlank() },
                deviceModel = fields["deviceModel"]?.takeIf { it.isNotBlank() },
                androidVersion = fields["androidVersion"]?.takeIf { it.isNotBlank() },
                location = fields["location"]?.takeIf { it.isNotBlank() },
                technicianId = fields["technicianId"]?.takeIf { it.isNotBlank() },
                technicianName = fields["technicianName"]?.takeIf { it.isNotBlank() },
                workOrderId = fields["workOrderId"]?.takeIf { it.isNotBlank() },
                shopId = fields["shopId"]?.takeIf { it.isNotBlank() },
                shopName = fields["shopName"]?.takeIf { it.isNotBlank() },
                customFields = deserializeCustomFields(fields["customFields"] ?: "")
            )
        } catch (e: Exception) {
            SessionMetadata()
        }
    }

    /**
     * Serializes custom fields map.
     */
    private fun serializeCustomFields(customFields: Map<String, String>): String {
        return customFields.entries.joinToString(CUSTOM_FIELD_DELIMITER) { (key, value) ->
            "${key}${CUSTOM_FIELD_KV_DELIMITER}${value}"
        }
    }

    /**
     * Deserializes custom fields map.
     */
    private fun deserializeCustomFields(serialized: String): Map<String, String> {
        if (serialized.isBlank()) return emptyMap()
        
        return try {
            serialized.split(CUSTOM_FIELD_DELIMITER)
                .filter { it.contains(CUSTOM_FIELD_KV_DELIMITER) }
                .associate { field ->
                    val parts = field.split(CUSTOM_FIELD_KV_DELIMITER, limit = 2)
                    parts[0] to (parts.getOrNull(1) ?: "")
                }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * Serializes live data values map.
     */
    private fun serializeLiveDataValues(values: Map<Int, LiveDataValue>): String {
        return values.entries.joinToString(LIVE_DATA_DELIMITER) { (pid, value) ->
            "${pid}${LIVE_DATA_KV_DELIMITER}${serializeLiveDataValue(value)}"
        }
    }

    /**
     * Deserializes live data values map.
     */
    private fun deserializeLiveDataValues(serialized: String?): Map<Int, LiveDataValue> {
        if (serialized.isNullOrBlank()) return emptyMap()
        
        return try {
            serialized.split(LIVE_DATA_DELIMITER)
                .filter { it.contains(LIVE_DATA_KV_DELIMITER) }
                .associate { entry ->
                    val parts = entry.split(LIVE_DATA_KV_DELIMITER, limit = 2)
                    val pid = parts[0].toIntOrNull() ?: 0
                    val value = deserializeLiveDataValue(parts.getOrElse(1) { "" })
                    pid to value
                }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * Serializes a single LiveDataValue.
     */
    private fun serializeLiveDataValue(value: LiveDataValue): String {
        return "${value.pid.pid}${LIVE_DATA_VALUE_DELIMITER}${value.rawValue}${LIVE_DATA_VALUE_DELIMITER}${value.formattedValue}${LIVE_DATA_VALUE_DELIMITER}${value.unit}${LIVE_DATA_VALUE_DELIMITER}${value.timestamp}"
    }

    /**
     * Deserializes a single LiveDataValue.
     */
    private fun deserializeLiveDataValue(serialized: String): LiveDataValue {
        val parts = serialized.split(LIVE_DATA_VALUE_DELIMITER)
        return if (parts.size >= 5) {
            // This is a simplified version - in reality you'd need to reconstruct the full LiveDataPID
            LiveDataValue(
                pid = createSimplePID(parts[0].toIntOrNull() ?: 0),
                rawValue = parts[1].toDoubleOrNull() ?: 0.0,
                formattedValue = parts[2],
                unit = parts[3],
                timestamp = parts[4].toLongOrNull() ?: System.currentTimeMillis()
            )
        } else {
            // Fallback for malformed data
            LiveDataValue(
                pid = createSimplePID(0),
                rawValue = 0.0,
                formattedValue = "N/A",
                unit = "",
                timestamp = System.currentTimeMillis()
            )
        }
    }

    /**
     * Creates a simple LiveDataPID for deserialization.
     */
    private fun createSimplePID(pidNumber: Int): com.spacetec.domain.models.livedata.LiveDataPID {
        // This would need to be implemented based on your LiveDataPID structure
        // For now, returning a placeholder
        return com.spacetec.domain.models.livedata.LiveDataPID(
            pid = pidNumber,
            name = "PID_${pidNumber.toString(16).uppercase()}",
            description = "Parameter ID $pidNumber",
            unit = "",
            formula = "",
            minValue = 0.0,
            maxValue = 255.0
        )
    }

    // ==================== Parsing Helpers ====================

    /**
     * Parses DiagnosticSessionType from string.
     */
    private fun parseSessionType(type: String?): DiagnosticSessionType {
        return try {
            type?.let { DiagnosticSessionType.valueOf(it) } ?: DiagnosticSessionType.QUICK_SCAN
        } catch (e: IllegalArgumentException) {
            DiagnosticSessionType.QUICK_SCAN
        }
    }

    /**
     * Parses SessionStatus from string.
     */
    private fun parseSessionStatus(status: String?): SessionStatus {
        return try {
            status?.let { SessionStatus.valueOf(it) } ?: SessionStatus.IN_PROGRESS
        } catch (e: IllegalArgumentException) {
            SessionStatus.IN_PROGRESS
        }
    }

    /**
     * Parses ProtocolType from string.
     */
    private fun parseProtocolType(protocol: String): com.spacetec.obd.core.domain.models.scanner.ProtocolType {
        return try {
            com.spacetec.obd.core.domain.models.scanner.ProtocolType.valueOf(protocol)
        } catch (e: IllegalArgumentException) {
            com.spacetec.obd.core.domain.models.scanner.ProtocolType.AUTO
        }
    }

    /**
     * Parses MileageUnit from string.
     */
    private fun parseMileageUnit(unit: String?): MileageUnit {
        return try {
            unit?.let { MileageUnit.valueOf(it) } ?: MileageUnit.KILOMETERS
        } catch (e: IllegalArgumentException) {
            MileageUnit.KILOMETERS
        }
    }

    // ==================== Utility Methods ====================

    /**
     * Creates a summary of diagnostic sessions.
     */
    fun createSummary(sessions: List<DiagnosticSession>): DiagnosticSessionSummary {
        return DiagnosticSessionSummary(
            totalCount = sessions.size,
            completedCount = sessions.count { it.isComplete },
            failedCount = sessions.count { it.isFailed },
            averageDuration = sessions.filter { it.isComplete }.map { it.durationMinutes }.average(),
            typeDistribution = sessions.groupBy { it.type }.mapValues { it.value.size },
            statusDistribution = sessions.groupBy { it.status }.mapValues { it.value.size },
            totalDTCs = sessions.sumOf { it.totalDTCCount },
            averageDTCsPerSession = sessions.map { it.totalDTCCount }.average(),
            sessionsWithDTCs = sessions.count { it.hasDTCs },
            sessionsWithCriticalDTCs = sessions.count { it.hasCriticalDTCs }
        )
    }

    /**
     * Filters sessions by date range.
     */
    fun filterByDateRange(
        sessions: List<DiagnosticSession>,
        startDate: Long,
        endDate: Long
    ): List<DiagnosticSession> {
        return sessions.filter { it.startTime in startDate..endDate }
    }

    /**
     * Filters sessions by vehicle.
     */
    fun filterByVehicle(
        sessions: List<DiagnosticSession>,
        vehicleId: String
    ): List<DiagnosticSession> {
        return sessions.filter { it.vehicleId == vehicleId }
    }

    /**
     * Groups sessions by vehicle.
     */
    fun groupByVehicle(sessions: List<DiagnosticSession>): Map<String, List<DiagnosticSession>> {
        return sessions.groupBy { it.vehicleId }
    }

    /**
     * Sorts sessions by date (newest first).
     */
    fun sortByDateDescending(sessions: List<DiagnosticSession>): List<DiagnosticSession> {
        return sessions.sortedByDescending { it.startTime }
    }

    /**
     * Calculates session statistics.
     */
    fun calculateStatistics(sessions: List<DiagnosticSession>): SessionStatistics {
        val completedSessions = sessions.filter { it.isComplete }
        
        return SessionStatistics(
            totalSessions = sessions.size,
            completedSessions = completedSessions.size,
            averageDuration = completedSessions.map { it.durationMinutes }.average(),
            shortestDuration = completedSessions.minOfOrNull { it.durationMinutes } ?: 0,
            longestDuration = completedSessions.maxOfOrNull { it.durationMinutes } ?: 0,
            successRate = if (sessions.isNotEmpty()) {
                (completedSessions.size.toDouble() / sessions.size) * 100
            } else 0.0,
            averageDTCsFound = sessions.map { it.totalDTCCount }.average(),
            mostCommonType = sessions.groupBy { it.type }.maxByOrNull { it.value.size }?.key
        )
    }

    companion object {
        private const val STRING_DELIMITER = "|||"
        private const val METADATA_DELIMITER = ";;;"
        private const val CUSTOM_FIELD_DELIMITER = ":::"
        private const val CUSTOM_FIELD_KV_DELIMITER = "==="
        private const val LIVE_DATA_DELIMITER = ";;;"
        private const val LIVE_DATA_KV_DELIMITER = ":::"
        private const val LIVE_DATA_VALUE_DELIMITER = "==="
    }
}

/**
 * Summary of diagnostic sessions for analytics.
 */
data class DiagnosticSessionSummary(
    val totalCount: Int,
    val completedCount: Int,
    val failedCount: Int,
    val averageDuration: Double,
    val typeDistribution: Map<DiagnosticSessionType, Int>,
    val statusDistribution: Map<SessionStatus, Int>,
    val totalDTCs: Int,
    val averageDTCsPerSession: Double,
    val sessionsWithDTCs: Int,
    val sessionsWithCriticalDTCs: Int
) {
    /**
     * Success rate percentage.
     */
    val successRate: Double
        get() = if (totalCount > 0) (completedCount.toDouble() / totalCount) * 100 else 0.0

    /**
     * Most common session type.
     */
    val mostCommonType: DiagnosticSessionType?
        get() = typeDistribution.maxByOrNull { it.value }?.key
}

/**
 * Detailed session statistics.
 */
data class SessionStatistics(
    val totalSessions: Int,
    val completedSessions: Int,
    val averageDuration: Double,
    val shortestDuration: Long,
    val longestDuration: Long,
    val successRate: Double,
    val averageDTCsFound: Double,
    val mostCommonType: DiagnosticSessionType?
)