package com.spacetec.domain.repository

import com.spacetec.domain.models.diagnostic.TechnicalServiceBulletin
import com.spacetec.domain.models.vehicle.Vehicle
import java.time.LocalDate

/**
 * Query parameters for searching Technical Service Bulletins.
 */
data class TSBSearchQuery(
    val dtcCode: String? = null,
    val vehicle: Vehicle? = null,
    val manufacturer: String? = null,
    val category: TSBCategory? = null,
    val severity: TSBSeverity? = null,
    val keywords: List<String> = emptyList(),
    val publishedAfter: LocalDate? = null,
    val publishedBefore: LocalDate? = null,
    val sortBy: TSBSortOption = TSBSortOption.PUBLISH_DATE_DESC,
    val limit: Int = 20,
    val offset: Int = 0
)

enum class TSBSortOption {
    PUBLISH_DATE_DESC, PUBLISH_DATE_ASC, RELEVANCE, SEVERITY
}

enum class TSBCategory {
    SAFETY, RECALL, SERVICE, MAINTENANCE, EMISSIONS, ELECTRICAL, MECHANICAL, SOFTWARE
}

enum class TSBSeverity {
    CRITICAL, HIGH, MEDIUM, LOW, INFORMATIONAL
}

data class TSBSearchResult(
    val tsbs: List<TechnicalServiceBulletin>,
    val totalCount: Int,
    val hasMore: Boolean,
    val query: TSBSearchQuery
)

data class Attachment(
    val id: String,
    val name: String,
    val type: AttachmentType,
    val url: String,
    val sizeBytes: Long,
    val downloadedLocally: Boolean
)

enum class AttachmentType {
    PDF, IMAGE, VIDEO, DIAGRAM, OTHER
}

data class AttachmentStatus(
    val attachmentId: String,
    val downloaded: Boolean,
    val downloadProgress: Float?,
    val localPath: String?,
    val sizeBytes: Long
)

/**
 * Repository interface for Technical Service Bulletin operations.
 */
interface TSBRepository {

    suspend fun getTSBsForDTC(dtcCode: String): Result<List<TechnicalServiceBulletin>>

    suspend fun getTSBsForDTCAndVehicle(dtcCode: String, vehicle: Vehicle): Result<List<TechnicalServiceBulletin>>

    suspend fun getTSBById(id: String): Result<TechnicalServiceBulletin?>

    suspend fun getTSBByBulletinNumber(manufacturer: String, bulletinNumber: String): Result<TechnicalServiceBulletin?>

    suspend fun searchTSBs(query: TSBSearchQuery): Result<TSBSearchResult>

    suspend fun getTSBsForVehicle(vehicle: Vehicle): Result<List<TechnicalServiceBulletin>>

    suspend fun getRecentTSBs(limit: Int = 20, manufacturer: String? = null): Result<List<TechnicalServiceBulletin>>

    suspend fun saveTSB(tsb: TechnicalServiceBulletin): Result<Unit>

    suspend fun updateTSB(tsb: TechnicalServiceBulletin): Result<Unit>

    suspend fun deleteTSB(id: String): Result<Unit>

    suspend fun syncTSBs(manufacturer: String? = null): Result<SyncResult>

    suspend fun downloadAttachments(tsbId: String): Result<Unit>

    suspend fun getAttachmentStatus(tsbId: String): Result<List<AttachmentStatus>>
}
