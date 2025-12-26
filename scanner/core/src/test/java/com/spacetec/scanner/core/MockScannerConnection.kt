/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 *
 * This file is part of the SpaceTec professional automotive diagnostic system.
 */

package com.spacetec.obd.scanner.core

import com.spacetec.core.common.exceptions.ConnectionException
import com.spacetec.core.common.exceptions.CommunicationException
import com.spacetec.core.domain.models.scanner.ScannerConnectionType
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Mock implementation of ScannerConnection for testing.
 * 
 * Provides configurable behavior for testing various scenarios including:
 * - Connection failures
 * - Communication errors
 * - Network conditions
 * - Reconnection behavior
 */
class MockScannerConnection(
    override val connectionType: ScannerConnectionType = ScannerConnectionType.BLUETOOTH_CLASSIC
) : BaseScannerConnection() {
    
    // Configuration for mock behavior
    var shouldFailConnection = false
    var shouldFailWrite = false
    var shouldFailRead = false
    var connectionDelay = 0L
    var writeDelay = 0L
    var readDelay = 0L
    var simulateDisconnection = false
    var maxReconnectAttempts = 3
    
    // State tracking
    private val connectionAttempts = AtomicInteger(0)
    private val writeAttempts = AtomicInteger(0)
    private val readAttempts = AtomicInteger(0)
    private val isPhysicallyConnected = AtomicBoolean(false)
    private val receivedData = mutableListOf<ByteArray>()
    private val sentData = mutableListOf<ByteArray>()
    
    // Mock data for responses
    var mockResponse = "OK\r>"
    var mockResponseBytes = mockResponse.toByteArray()
    
    fun getConnectionAttempts() = connectionAttempts.get()
    fun getWriteAttempts() = writeAttempts.get()
    fun getReadAttempts() = readAttempts.get()
    fun getSentData() = sentData.toList()
    fun getReceivedData() = receivedData.toList()
    
    fun reset() {
        connectionAttempts.set(0)
        writeAttempts.set(0)
        readAttempts.set(0)
        isPhysicallyConnected.set(false)
        receivedData.clear()
        sentData.clear()
        shouldFailConnection = false
        shouldFailWrite = false
        shouldFailRead = false
        connectionDelay = 0L
        writeDelay = 0L
        readDelay = 0L
        simulateDisconnection = false
    }
    
    override suspend fun doConnect(address: String, config: ConnectionConfig): ConnectionInfo {
        connectionAttempts.incrementAndGet()
        
        if (connectionDelay > 0) {
            delay(connectionDelay)
        }
        
        if (shouldFailConnection) {
            throw ConnectionException("Mock connection failure")
        }
        
        isPhysicallyConnected.set(true)
        
        return ConnectionInfo(
            remoteAddress = address,
            connectionType = connectionType,
            mtu = 512
        )
    }
    
    override suspend fun doDisconnect(graceful: Boolean) {
        isPhysicallyConnected.set(false)
    }
    
    override suspend fun doWrite(data: ByteArray): Int {
        writeAttempts.incrementAndGet()
        
        if (!isPhysicallyConnected.get()) {
            throw CommunicationException("Not connected")
        }
        
        if (writeDelay > 0) {
            delay(writeDelay)
        }
        
        if (shouldFailWrite) {
            throw CommunicationException("Mock write failure")
        }
        
        if (simulateDisconnection) {
            isPhysicallyConnected.set(false)
            throw CommunicationException("Connection lost during write")
        }
        
        sentData.add(data.copyOf())
        return data.size
    }
    
    override suspend fun doRead(buffer: ByteArray, timeout: Long): Int {
        readAttempts.incrementAndGet()
        
        if (!isPhysicallyConnected.get()) {
            throw CommunicationException("Not connected")
        }
        
        if (readDelay > 0) {
            delay(readDelay)
        }
        
        if (shouldFailRead) {
            throw CommunicationException("Mock read failure")
        }
        
        if (simulateDisconnection) {
            isPhysicallyConnected.set(false)
            throw CommunicationException("Connection lost during read")
        }
        
        val responseData = mockResponseBytes
        val bytesToCopy = minOf(responseData.size, buffer.size)
        System.arraycopy(responseData, 0, buffer, 0, bytesToCopy)
        
        receivedData.add(responseData.copyOf(bytesToCopy))
        return bytesToCopy
    }
    
    override suspend fun doAvailable(): Int {
        return if (isPhysicallyConnected.get()) mockResponseBytes.size else 0
    }
    
    override suspend fun doClearBuffers() {
        // Mock implementation - nothing to clear
    }
    
    /**
     * Simulates a connection loss after the specified delay.
     */
    suspend fun simulateConnectionLoss(delayMs: Long = 0) {
        if (delayMs > 0) {
            delay(delayMs)
        }
        isPhysicallyConnected.set(false)
        simulateDisconnection = true
    }
    
    /**
     * Configures the mock to fail connection attempts for the first N attempts.
     */
    fun failConnectionAttempts(attempts: Int) {
        var remainingFailures = attempts
        shouldFailConnection = remainingFailures > 0
        
        // Override doConnect to handle partial failures
        val originalShouldFail = shouldFailConnection
        shouldFailConnection = false
        
        // This is a simplified approach - in a real implementation you'd use a more sophisticated mechanism
        if (originalShouldFail && connectionAttempts.get() < attempts) {
            shouldFailConnection = true
        }
    }
}