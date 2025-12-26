package com.spacetec.core.common.transport

/**
 * Interface for diagnostic transport layer that handles communication between
 * scanner hardware and protocol layers.
 * 
 * This interface provides a clean abstraction for sending diagnostic requests
 * and receiving responses, without creating circular dependencies between
 * scanner and protocol modules.
 */
interface DiagnosticTransport {
    /**
     * Sends a diagnostic request and returns the response.
     * 
     * @param request The diagnostic request as raw bytes
     * @return The response as raw bytes, or null if no response received
     */
    suspend fun send(request: ByteArray): ByteArray?
    
    /**
     * Sends a diagnostic request with a timeout.
     * 
     * @param request The diagnostic request as raw bytes
     * @param timeoutMs Timeout in milliseconds
     * @return The response as raw bytes, or null if no response received or timeout
     */
    suspend fun send(request: ByteArray, timeoutMs: Long): ByteArray?
    
    /**
     * Establishes the transport connection.
     * 
     * @return true if connection was successful, false otherwise
     */
    suspend fun connect(): Boolean
    
    /**
     * Closes the transport connection.
     */
    suspend fun disconnect()
    
    /**
     * Checks if the transport is currently connected.
     * 
     * @return true if connected, false otherwise
     */
    val isConnected: Boolean
}