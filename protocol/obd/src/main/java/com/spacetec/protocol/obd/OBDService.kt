package com.spacetec.protocol.obd

import kotlinx.coroutines.flow.Flow

/**
 * Interface for OBD Service Handlers
 * Each service handler implements this interface to handle specific OBD modes
 */
interface OBDService<T, R> {
    /**
     * Handle a request for the specific service
     * @param request The request parameters for the service
     * @return Flow of OBDResult with the response
     */
    suspend fun handleRequest(request: T): Flow<OBDResult<R>>
}

/**
 * Base OBD Service Implementation
 * Provides common functionality for all service handlers
 */
abstract class BaseOBDService<T, R> : OBDService<T, R> {
    /**
     * Parse raw response data into the expected format
     * @param rawData The raw response bytes from the ECU
     * @return Parsed data in the expected format
     */
    protected fun parseResponse(rawData: ByteArray): R {
        // Default implementation - to be overridden by specific service handlers
        throw NotImplementedError("parseResponse must be implemented by specific service handlers")
    }
    
    /**
     * Format request data into the proper OBD-II command format
     * @param request The request parameters
     * @return Formatted command bytes
     */
    protected fun formatRequest(request: T): ByteArray {
        // Default implementation - to be overridden by specific service handlers
        throw NotImplementedError("formatRequest must be implemented by specific service handlers")
    }
}