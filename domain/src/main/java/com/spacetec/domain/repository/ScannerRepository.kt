package com.spacetec.domain.repository

import com.spacetec.obd.core.domain.models.scanner.ScannerInfo
import com.spacetec.obd.core.domain.models.scanner.ConnectionState
import com.spacetec.core.common.result.Result
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for scanner-related operations.
 * 
 * Handles discovery, connection, and communication with OBD scanners.
 */
interface ScannerRepository {
    
    /**
     * Get flow of available scanners
     */
    fun getAvailableScanners(): Flow<List<ScannerInfo>>
    
    /**
     * Start scanning for available scanners
     */
    suspend fun startScanning(): Result<Boolean>
    
    /**
     * Stop scanning for scanners
     */
    suspend fun stopScanning(): Result<Boolean>
    
    /**
     * Connect to a specific scanner
     */
    suspend fun connect(scannerInfo: ScannerInfo): Result<Boolean>
    
    /**
     * Disconnect from the current scanner
     */
    suspend fun disconnect(): Result<Boolean>
    
    /**
     * Get the current connection state
     */
    fun getConnectionState(): Flow<ConnectionState>
    
    /**
     * Send data to the connected scanner
     */
    suspend fun send(data: String): Result<String>
    
    /**
     * Send raw bytes to the connected scanner
     */
    suspend fun sendRaw(data: ByteArray): Result<ByteArray>
    
    /**
     * Check if a scanner is currently connected
     */
    suspend fun isConnected(): Boolean
    
    /**
     * Get the currently connected scanner info
     */
    suspend fun getCurrentScanner(): ScannerInfo?
    
    /**
     * Get supported connection types
     */
    fun getSupportedConnectionTypes(): List<String>
}