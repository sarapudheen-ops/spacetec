package com.spacetec.obd.core.database.converters

import androidx.room.TypeConverter
import com.spacetec.obd.core.database.entities.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.Serializable

/**
 * Room type converters for complex list and object types
 */
class ListConverters {
    
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    // String list converters
    @TypeConverter
    fun fromStringList(value: List<String>?): String? {
        return value?.let { json.encodeToString(it) }
    }
    
    @TypeConverter
    fun toStringList(value: String?): List<String>? {
        return value?.let { json.decodeFromString<List<String>>(it) }
    }
    
    // RepairStep list converters
    @TypeConverter
    fun fromRepairStepList(value: List<RepairStep>?): String? {
        return value?.let { json.encodeToString(it.map { step -> SerializableRepairStep.fromRepairStep(step) }) }
    }
    
    @TypeConverter
    fun toRepairStepList(value: String?): List<RepairStep>? {
        return value?.let { 
            json.decodeFromString<List<SerializableRepairStep>>(it).map { it.toRepairStep() }
        }
    }
    
    // RequiredPart list converters
    @TypeConverter
    fun fromRequiredPartList(value: List<RequiredPart>?): String? {
        return value?.let { json.encodeToString(it.map { part -> SerializableRequiredPart.fromRequiredPart(part) }) }
    }
    
    @TypeConverter
    fun toRequiredPartList(value: String?): List<RequiredPart>? {
        return value?.let { 
            json.decodeFromString<List<SerializableRequiredPart>>(it).map { it.toRequiredPart() }
        }
    }
    
    // VehicleCoverage list converters
    @TypeConverter
    fun fromVehicleCoverageList(value: List<VehicleCoverage>?): String? {
        return value?.let { json.encodeToString(it.map { coverage -> SerializableVehicleCoverage.fromVehicleCoverage(coverage) }) }
    }
    
    @TypeConverter
    fun toVehicleCoverageList(value: String?): List<VehicleCoverage>? {
        return value?.let { 
            json.decodeFromString<List<SerializableVehicleCoverage>>(it).map { it.toVehicleCoverage() }
        }
    }
    
    // TSBAttachment list converters
    @TypeConverter
    fun fromTSBAttachmentList(value: List<TSBAttachment>?): String? {
        return value?.let { json.encodeToString(it.map { attachment -> SerializableTSBAttachment.fromTSBAttachment(attachment) }) }
    }
    
    @TypeConverter
    fun toTSBAttachmentList(value: String?): List<TSBAttachment>? {
        return value?.let { 
            json.decodeFromString<List<SerializableTSBAttachment>>(it).map { it.toTSBAttachment() }
        }
    }
    
    // RepairAction list converters
    @TypeConverter
    fun fromRepairActionList(value: List<RepairAction>?): String? {
        return value?.let { json.encodeToString(it.map { action -> SerializableRepairAction.fromRepairAction(action) }) }
    }
    
    @TypeConverter
    fun toRepairActionList(value: String?): List<RepairAction>? {
        return value?.let { 
            json.decodeFromString<List<SerializableRepairAction>>(it).map { it.toRepairAction() }
        }
    }
    
    // ReplacedPart list converters
    @TypeConverter
    fun fromReplacedPartList(value: List<ReplacedPart>?): String? {
        return value?.let { json.encodeToString(it.map { part -> SerializableReplacedPart.fromReplacedPart(part) }) }
    }
    
    @TypeConverter
    fun toReplacedPartList(value: String?): List<ReplacedPart>? {
        return value?.let { 
            json.decodeFromString<List<SerializableReplacedPart>>(it).map { it.toReplacedPart() }
        }
    }
}

// Serializable wrapper classes for complex types
@Serializable
data class SerializableRepairStep(
    val stepNumber: Int,
    val title: String,
    val description: String,
    val estimatedTimeMinutes: Int,
    val requiredTools: List<String> = emptyList(),
    val safetyWarnings: List<String> = emptyList(),
    val images: List<String> = emptyList(),
    val videoUrl: String? = null,
    val notes: String? = null
) {
    companion object {
        fun fromRepairStep(step: RepairStep) = SerializableRepairStep(
            stepNumber = step.stepNumber,
            title = step.title,
            description = step.description,
            estimatedTimeMinutes = step.estimatedTimeMinutes,
            requiredTools = step.requiredTools,
            safetyWarnings = step.safetyWarnings,
            images = step.images,
            videoUrl = step.videoUrl,
            notes = step.notes
        )
    }
    
    fun toRepairStep() = RepairStep(
        stepNumber = stepNumber,
        title = title,
        description = description,
        estimatedTimeMinutes = estimatedTimeMinutes,
        requiredTools = requiredTools,
        safetyWarnings = safetyWarnings,
        images = images,
        videoUrl = videoUrl,
        notes = notes
    )
}

@Serializable
data class SerializableRequiredPart(
    val partNumber: String,
    val partName: String,
    val description: String,
    val manufacturer: String? = null,
    val estimatedCost: Double? = null,
    val isOEM: Boolean = true,
    val alternatives: List<String> = emptyList(),
    val notes: String? = null
) {
    companion object {
        fun fromRequiredPart(part: RequiredPart) = SerializableRequiredPart(
            partNumber = part.partNumber,
            partName = part.partName,
            description = part.description,
            manufacturer = part.manufacturer,
            estimatedCost = part.estimatedCost,
            isOEM = part.isOEM,
            alternatives = part.alternatives,
            notes = part.notes
        )
    }
    
    fun toRequiredPart() = RequiredPart(
        partNumber = partNumber,
        partName = partName,
        description = description,
        manufacturer = manufacturer,
        estimatedCost = estimatedCost,
        isOEM = isOEM,
        alternatives = alternatives,
        notes = notes
    )
}

@Serializable
data class SerializableVehicleCoverage(
    val make: String,
    val model: String,
    val yearStart: Int,
    val yearEnd: Int,
    val engineTypes: List<String> = emptyList(),
    val transmissionTypes: List<String> = emptyList(),
    val trimLevels: List<String> = emptyList(),
    val vinRanges: List<SerializableVinRange> = emptyList(),
    val productionDates: List<SerializableProductionDateRange> = emptyList()
) {
    companion object {
        fun fromVehicleCoverage(coverage: VehicleCoverage) = SerializableVehicleCoverage(
            make = coverage.make,
            model = coverage.model,
            yearStart = coverage.yearStart,
            yearEnd = coverage.yearEnd,
            engineTypes = coverage.engineTypes,
            transmissionTypes = coverage.transmissionTypes,
            trimLevels = coverage.trimLevels,
            vinRanges = coverage.vinRanges.map { SerializableVinRange.fromVinRange(it) },
            productionDates = coverage.productionDates.map { SerializableProductionDateRange.fromProductionDateRange(it) }
        )
    }
    
    fun toVehicleCoverage() = VehicleCoverage(
        make = make,
        model = model,
        yearStart = yearStart,
        yearEnd = yearEnd,
        engineTypes = engineTypes,
        transmissionTypes = transmissionTypes,
        trimLevels = trimLevels,
        vinRanges = vinRanges.map { it.toVinRange() },
        productionDates = productionDates.map { it.toProductionDateRange() }
    )
}

@Serializable
data class SerializableVinRange(
    val startVin: String,
    val endVin: String,
    val notes: String? = null
) {
    companion object {
        fun fromVinRange(range: VinRange) = SerializableVinRange(
            startVin = range.startVin,
            endVin = range.endVin,
            notes = range.notes
        )
    }
    
    fun toVinRange() = VinRange(
        startVin = startVin,
        endVin = endVin,
        notes = notes
    )
}

@Serializable
data class SerializableProductionDateRange(
    val startDate: String,
    val endDate: String,
    val notes: String? = null
) {
    companion object {
        fun fromProductionDateRange(range: ProductionDateRange) = SerializableProductionDateRange(
            startDate = range.startDate.toString(),
            endDate = range.endDate.toString(),
            notes = range.notes
        )
    }
    
    fun toProductionDateRange() = ProductionDateRange(
        startDate = java.time.LocalDate.parse(startDate),
        endDate = java.time.LocalDate.parse(endDate),
        notes = notes
    )
}

@Serializable
data class SerializableTSBAttachment(
    val fileName: String,
    val fileType: String,
    val description: String,
    val url: String? = null,
    val localPath: String? = null,
    val fileSize: Long? = null
) {
    companion object {
        fun fromTSBAttachment(attachment: TSBAttachment) = SerializableTSBAttachment(
            fileName = attachment.fileName,
            fileType = attachment.fileType,
            description = attachment.description,
            url = attachment.url,
            localPath = attachment.localPath,
            fileSize = attachment.fileSize
        )
    }
    
    fun toTSBAttachment() = TSBAttachment(
        fileName = fileName,
        fileType = fileType,
        description = description,
        url = url,
        localPath = localPath,
        fileSize = fileSize
    )
}

@Serializable
data class SerializableRepairAction(
    val actionType: String,
    val description: String,
    val timeSpentMinutes: Int,
    val success: Boolean,
    val notes: String? = null
) {
    companion object {
        fun fromRepairAction(action: RepairAction) = SerializableRepairAction(
            actionType = action.actionType.name,
            description = action.description,
            timeSpentMinutes = action.timeSpentMinutes,
            success = action.success,
            notes = action.notes
        )
    }
    
    fun toRepairAction() = RepairAction(
        actionType = RepairActionType.valueOf(actionType),
        description = description,
        timeSpentMinutes = timeSpentMinutes,
        success = success,
        notes = notes
    )
}

@Serializable
data class SerializableReplacedPart(
    val partNumber: String,
    val partName: String,
    val quantity: Int,
    val cost: Double? = null,
    val supplier: String? = null,
    val warrantyMonths: Int? = null,
    val isOEM: Boolean = true
) {
    companion object {
        fun fromReplacedPart(part: ReplacedPart) = SerializableReplacedPart(
            partNumber = part.partNumber,
            partName = part.partName,
            quantity = part.quantity,
            cost = part.cost,
            supplier = part.supplier,
            warrantyMonths = part.warrantyMonths,
            isOEM = part.isOEM
        )
    }
    
    fun toReplacedPart() = ReplacedPart(
        partNumber = partNumber,
        partName = partName,
        quantity = quantity,
        cost = cost,
        supplier = supplier,
        warrantyMonths = warrantyMonths,
        isOEM = isOEM
    )
}