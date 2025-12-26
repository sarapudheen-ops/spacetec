package com.spacetec.obd.core.database.converters

import androidx.room.TypeConverter
import com.spacetec.obd.core.database.entities.*

/**
 * Room type converters for enum types
 */
class EnumConverters {
    
    // DTC Type converters
    @TypeConverter
    fun fromDTCType(type: DTCType?): String? = type?.name
    
    @TypeConverter
    fun toDTCType(typeName: String?): DTCType? = typeName?.let { DTCType.valueOf(it) }
    
    // DTC SubType converters
    @TypeConverter
    fun fromDTCSubType(subType: DTCSubType?): String? = subType?.name
    
    @TypeConverter
    fun toDTCSubType(subTypeName: String?): DTCSubType? = subTypeName?.let { DTCSubType.valueOf(it) }
    
    // DTC Category converters
    @TypeConverter
    fun fromDTCCategory(category: DTCCategory?): String? = category?.name
    
    @TypeConverter
    fun toDTCCategory(categoryName: String?): DTCCategory? = categoryName?.let { DTCCategory.valueOf(it) }
    
    // DTC Severity converters
    @TypeConverter
    fun fromDTCSeverity(severity: DTCSeverity?): String? = severity?.name
    
    @TypeConverter
    fun toDTCSeverity(severityName: String?): DTCSeverity? = severityName?.let { DTCSeverity.valueOf(it) }
    
    // Repair Difficulty converters
    @TypeConverter
    fun fromRepairDifficulty(difficulty: RepairDifficulty?): String? = difficulty?.name
    
    @TypeConverter
    fun toRepairDifficulty(difficultyName: String?): RepairDifficulty? = difficultyName?.let { RepairDifficulty.valueOf(it) }
    
    // TSB Category converters
    @TypeConverter
    fun fromTSBCategory(category: TSBCategory?): String? = category?.name
    
    @TypeConverter
    fun toTSBCategory(categoryName: String?): TSBCategory? = categoryName?.let { TSBCategory.valueOf(it) }
    
    // TSB Severity converters
    @TypeConverter
    fun fromTSBSeverity(severity: TSBSeverity?): String? = severity?.name
    
    @TypeConverter
    fun toTSBSeverity(severityName: String?): TSBSeverity? = severityName?.let { TSBSeverity.valueOf(it) }
    
    // Diagnostic Session Type converters
    @TypeConverter
    fun fromDiagnosticSessionType(type: DiagnosticSessionType?): String? = type?.name
    
    @TypeConverter
    fun toDiagnosticSessionType(typeName: String?): DiagnosticSessionType? = typeName?.let { DiagnosticSessionType.valueOf(it) }
    
    // Diagnostic Session Status converters
    @TypeConverter
    fun fromDiagnosticSessionStatus(status: DiagnosticSessionStatus?): String? = status?.name
    
    @TypeConverter
    fun toDiagnosticSessionStatus(statusName: String?): DiagnosticSessionStatus? = statusName?.let { DiagnosticSessionStatus.valueOf(it) }
    
    // Repair Action Type converters
    @TypeConverter
    fun fromRepairActionType(type: RepairActionType?): String? = type?.name
    
    @TypeConverter
    fun toRepairActionType(typeName: String?): RepairActionType? = typeName?.let { RepairActionType.valueOf(it) }
    
    // Sync Status converters
    @TypeConverter
    fun fromSyncStatus(status: SyncStatus?): String? = status?.name
    
    @TypeConverter
    fun toSyncStatus(statusName: String?): SyncStatus? = statusName?.let { SyncStatus.valueOf(it) }
}