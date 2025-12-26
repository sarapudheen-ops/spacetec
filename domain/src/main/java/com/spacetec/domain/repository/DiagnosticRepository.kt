package com.spacetec.domain.repository

import com.spacetec.domain.models.diagnostic.DiagnosticReport
import com.spacetec.domain.models.vehicle.VehicleDtc
import com.spacetec.core.common.result.Result
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for diagnostic-related operations.
 * 
 * Handles reading, clearing, and managing diagnostic trouble codes (DTCs),
 * as well as generating diagnostic reports.
 */
interface DiagnosticRepository {
    
    /**
     * Read diagnostic trouble codes from the vehicle
     */
    suspend fun readDTCs(): Result<List<VehicleDtc>>
    
    /**
     * Clear diagnostic trouble codes from the vehicle
     */
    suspend fun clearDTCs(): Result<Boolean>
    
    /**
     * Read freeze frame data for specific DTC
     */
    suspend fun readFreezeFrame(dtcs: List<String>): Result<Map<String, ByteArray>>
    
    /**
     * Get detailed information about specific DTCs
     */
    suspend fun getDtcDetails(dtcs: List<String>): Result<List<VehicleDtc>>
    
    /**
     * Perform a quick diagnostic scan
     */
    suspend fun quickScan(): Result<DiagnosticReport>
    
    /**
     * Perform a full system diagnostic scan
     */
    suspend fun fullSystemScan(): Result<DiagnosticReport>
    
    /**
     * Get historical diagnostic data for a vehicle
     */
    suspend fun getHistoricalData(vehicleId: String): Result<List<DiagnosticReport>>
    
    /**
     * Save diagnostic report
     */
    suspend fun saveDiagnosticReport(report: DiagnosticReport): Result<Boolean>
    
    /**
     * Get list of diagnostic reports
     */
    fun getDiagnosticReports(): Flow<List<DiagnosticReport>>
    
    /**
     * Get supported DTCs for the connected vehicle
     */
    suspend fun getSupportedDTCs(): Result<List<String>>
    
    /**
     * Get DTC explanation and repair suggestions
     */
    suspend fun getDtcExplanation(dtc: String): Result<VehicleDtc>
    
    /**
     * Check if vehicle supports OBD diagnostics
     */
    suspend fun isOBDSupported(): Boolean
    
    /**
     * Get diagnostic capabilities of the connected vehicle
     */
    suspend fun getDiagnosticCapabilities(): Result<List<String>>
}