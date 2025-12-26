package com.obdreader.data.obd.protocol

import com.obdreader.domain.model.DTC
import com.obdreader.domain.model.PIDResponse
import com.obdreader.domain.model.VehicleInfo
import com.obdreader.domain.model.OBDProtocolType
import kotlinx.coroutines.flow.Flow

/**
 * Interface defining OBD-II protocol operations.
 * Implementations handle communication with ELM327-compatible adapters.
 */
interface OBDProtocol {
    
    /**
     * Initialize the OBD adapter to a known state.
     * Executes the initialization sequence: ATZ, ATE0, ATL0, etc.
     */
    suspend fun initialize(): Result<AdapterInfo>
    
    /**
     * Send a raw command to the adapter and receive response.
     */
    suspend fun sendCommand(command: String, timeout: Long = DEFAULT_TIMEOUT): Result<String>
    
    /**
     * Read a single PID value.
     */
    suspend fun readPID(mode: Int, pid: Int): Result<PIDResponse>
    
    /**
     * Read multiple PIDs in a batch (up to 6).
     */
    suspend fun readPIDs(mode: Int, pids: List<Int>): Result<List<PIDResponse>>
    
    /**
     * Get supported PIDs for a mode.
     */
    suspend fun getSupportedPIDs(mode: Int): Result<Set<Int>>
    
    /**
     * Read diagnostic trouble codes (Mode 03).
     */
    suspend fun readStoredDTCs(): Result<List<DTC>>
    
    /**
     * Read pending DTCs (Mode 07).
     */
    suspend fun readPendingDTCs(): Result<List<DTC>>
    
    /**
     * Read permanent DTCs (Mode 0A).
     */
    suspend fun readPermanentDTCs(): Result<List<DTC>>
    
    /**
     * Clear DTCs and freeze frame data (Mode 04).
     */
    suspend fun clearDTCs(): Result<Unit>
    
    /**
     * Read vehicle information (Mode 09).
     */
    suspend fun readVehicleInfo(): Result<VehicleInfo>
    
    /**
     * Get current protocol type.
     */
    fun getProtocolType(): OBDProtocolType?
    
    /**
     * Check if adapter is ready for commands.
     */
    fun isReady(): Boolean
    
    /**
     * Reset the adapter.
     */
    suspend fun reset(): Result<Unit>
    
    /**
     * Close and cleanup resources.
     */
    suspend fun close()
    
    companion object {
        const val DEFAULT_TIMEOUT = 2000L
        const val RESET_DELAY = 1000L
    }
}

/**
 * Information about the connected OBD adapter.
 */
data class AdapterInfo(
    val version: String,
    val protocol: OBDProtocolType,
    val voltage: Double?,
    val supportedPIDs: Set<Int>
)
