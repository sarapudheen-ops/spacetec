/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 *
 * This file is part of the SpaceTec professional automotive diagnostic system.
 */

package com.spacetec.obd.scanner.wifi

import com.spacetec.core.common.exceptions.CommunicationException
import com.spacetec.core.common.exceptions.ConnectionException
import com.spacetec.core.domain.models.scanner.ScannerConnectionType
import com.spacetec.obd.scanner.core.BaseScannerConnection
import com.spacetec.obd.scanner.core.ConnectionConfig
import com.spacetec.obd.scanner.core.ConnectionInfo
import com.spacetec.obd.scanner.core.ConnectionState
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Mock implementation of WiFi connection for testing.
 *
 * Provides configurable behavior for testing various scenarios including:
 * - Connection establishment timing
 * - Connection failures
 * - Disconnection detection
 * - Network conditions
 */
class MockWiFiConnection : BaseScannerConnection() {

    override val connectionType: ScannerConnectionType = ScannerConnectionType.WIFI

    // Configuration for mock behavior
    var shouldFailConnection = false
    var shouldFailWrite = false
    var shouldFailRead = false
    var connectionDelay = 0L
    var writeDelay = 0L
    var readDelay = 0L
    var simulateDisconnection = false
    var disconnectionDetectionDelay = 0L

    // State tracking
    private val connectionAttempts = AtomicInteger(0)
    private val writeAttempts = AtomicInteger(0)
    private val readAttempts = AtomicInteger(0)
    private val isPhysicallyConnected = AtomicBoolean(false)
    private val connectionStartTime = AtomicLong(0)
    private val connectionEndTime = AtomicLong(0)
    private val disconnectionTime = AtomicLong(0)
    private val disconnectionDetectedTime = AtomicLong(0)

    // Mock data for responses
    var mockResponse = "OK\r>"
    var mockResponseBytes = mockResponse.toByteArray()

    // Getters for test verification
    fun getConnectionAttempts() = connectionAttempts.get()
    fun getWriteAttempts() = writeAttempts.get()
    fun getReadAttempts() = readAttempts.get()
    fun getConnectionDuration() = connectionEndTime.get() - connectionStartTime.get()
    fun getDisconnectionDetectionTime() = disconnectionDetectedTime.get() - disconnectionTime.get()
    fun isPhysicallyConnected() = isPhysicallyConnected.get()

    fun reset() {
        connectionAttempts.set(0)
        writeAttempts.set(0)
        readAttempts.set(0)
        isPhysicallyConnected.set(false)
        connectionStartTime.set(0)
        connectionEndTime.set(0)
        disconnectionTime.set(0)
        disconnectionDetectedTime.set(0)
        shouldFailConnection = false
        shouldFailWrite = false
        shouldFailRead = false
        connectionDelay = 0L
        writeDelay = 0L
        readDelay = 0L
        simulateDisconnection = false
        disconnectionDetectionDelay = 0L
    }

    override suspend fun doConnect(address: String, config: ConnectionConfig): ConnectionInfo {
        connectionAttempts.incrementAndGet()
        connectionStartTime.set(System.currentTimeMillis())

        if (connectionDelay > 0) {
            delay(connectionDelay)
        }

        if (shouldFailConnection) {
            throw ConnectionException("Mock WiFi connection failure")
        }

        isPhysicallyConnected.set(true)
        connectionEndTime.set(System.currentTimeMillis())

        return ConnectionInfo(
            remoteAddress = address,
            connectionType = connectionType,
            mtu = WiFiConstants.DEFAULT_MTU
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

        return bytesToCopy
    }

    override suspend fun doAvailable(): Int {
        return if (isPhysicallyConnected.get()) mockResponseBytes.size else 0
    }

    override suspend fun doClearBuffers() {
        // Mock implementation - nothing to clear
    }

    /**
     * Simulates a connection loss and tracks detection timing.
     */
    suspend fun simulateConnectionLoss(delayMs: Long = 0) {
        if (delayMs > 0) {
            delay(delayMs)
        }
        disconnectionTime.set(System.currentTimeMillis())
        isPhysicallyConnected.set(false)
        simulateDisconnection = true

        // Simulate detection delay
        if (disconnectionDetectionDelay > 0) {
            delay(disconnectionDetectionDelay)
        }
        disconnectionDetectedTime.set(System.currentTimeMillis())

        // Update connection state
        _connectionState.value = ConnectionState.Error(
            ConnectionException("Connection lost"),
            isRecoverable = true
        )
    }

    /**
     * Simulates connection with specific timing for testing.
     */
    suspend fun connectWithTiming(
        address: String,
        config: ConnectionConfig,
        actualConnectionTime: Long
    ): ConnectionInfo {
        connectionAttempts.incrementAndGet()
        connectionStartTime.set(System.currentTimeMillis())

        // Simulate actual connection time
        delay(actualConnectionTime)

        if (shouldFailConnection) {
            throw ConnectionException("Mock WiFi connection failure")
        }

        isPhysicallyConnected.set(true)
        connectionEndTime.set(System.currentTimeMillis())

        return ConnectionInfo(
            remoteAddress = address,
            connectionType = connectionType,
            mtu = WiFiConstants.DEFAULT_MTU
        )
    }
}
