/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 *
 * This file is part of the SpaceTec professional automotive diagnostic system.
 */

package com.spacetec.obd.scanner.core

import com.spacetec.core.common.exceptions.ConnectionException
import com.spacetec.core.common.exceptions.CommunicationException
import com.spacetec.core.common.exceptions.TimeoutException
import com.spacetec.core.common.result.Result
import com.spacetec.core.domain.models.scanner.ScannerConnectionType
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.delay

/**
 * **Feature: scanner-connection-system, Property 11: Error Categorization and Recovery**
 * 
 * Property-based test for error categorization and recovery mechanisms.
 * 
 * **Validates: Requirements 6.1, 6.4, 6.5**
 * 
 * This test verifies that:
 * 1. Connection errors are correctly categorized as recoverable or non-recoverable
 * 2. Recoverable errors trigger appropriate recovery actions
 * 3. Non-recoverable errors are handled gracefully without infinite retry loops
 * 4. Error recovery uses exponential backoff
 * 5. Maximum retry attempts are respected
 */
class ErrorCategorizationPropertyTest : StringSpec({
    
    "Property 11: Error Categorization and Recovery - Connection errors should be correctly categorized and trigger appropriate recovery actions" {
        checkAll(
            iterations = 100,
            Arb.enum<ScannerConnectionType>(),
            Arb.int(1..5), // maxReconnectAttempts
            Arb.string(5..20), // address
            Arb.boolean() // shouldRecover
        ) { connectionType, maxAttempts, address, shouldRecover ->
            
            val mockConnection = MockScannerConnection(connectionType)
            val config = ConnectionConfig(
                autoReconnect = true,
                maxReconnectAttempts = maxAttempts,
                reconnectDelay = 100L // Short delay for test performance
            )
            
            try {
                // Given: Initial successful connection
                val connectResult = mockConnection.connect(address, config)
                connectResult.shouldBeInstanceOf<Result.Success<ConnectionInfo>>()
                mockConnection.isConnected shouldBe true
                
                // When: Different types of errors occur
                if (shouldRecover) {
                    // Simulate recoverable error (temporary network issue)
                    mockConnection.shouldFailConnection = true
                    mockConnection.simulateConnectionLoss()
                    
                    // Attempt reconnection
                    val reconnectResult = mockConnection.reconnect()
                    
                    // Then: Should attempt recovery exactly maxAttempts times
                    reconnectResult.shouldBeInstanceOf<Result.Error>()
                    
                    val finalState = mockConnection.connectionState.value
                    finalState.shouldBeInstanceOf<ConnectionState.Error>()
                    finalState as ConnectionState.Error
                    
                    // Verify error is categorized correctly
                    finalState.isRecoverable shouldBe false // After max attempts exceeded
                    finalState.attemptCount shouldBe maxAttempts
                    
                    // Verify correct number of connection attempts were made
                    mockConnection.getConnectionAttempts() shouldBe (1 + maxAttempts)
                    
                } else {
                    // Simulate non-recoverable error (invalid address format)
                    mockConnection.disconnect()
                    
                    // Try to connect to invalid address
                    val invalidConnectResult = mockConnection.connect("", config)
                    
                    // Should fail immediately without retries for non-recoverable errors
                    invalidConnectResult.shouldBeInstanceOf<Result.Error>()
                    
                    val errorState = mockConnection.connectionState.value
                    errorState.shouldBeInstanceOf<ConnectionState.Error>()
                }
                
            } finally {
                mockConnection.release()
            }
        }
    }
    
    "Property 11: Communication errors should be categorized and handled appropriately" {
        checkAll(
            iterations = 50,
            Arb.enum<ScannerConnectionType>(),
            Arb.string(5..15) // address
        ) { connectionType, address ->
            
            val mockConnection = MockScannerConnection(connectionType)
            val config = ConnectionConfig(
                autoReconnect = true,
                maxReconnectAttempts = 2,
                reconnectDelay = 50L
            )
            
            try {
                // Establish connection
                mockConnection.connect(address, config)
                mockConnection.isConnected shouldBe true
                
                // Test different communication error scenarios
                
                // 1. Write failure (recoverable)
                mockConnection.shouldFailWrite = true
                val writeResult = mockConnection.write("ATZ".toByteArray())
                writeResult.shouldBeInstanceOf<Result.Error>()
                
                // Verify error statistics are updated
                val statsAfterWriteError = mockConnection.getStatistics()
                statsAfterWriteError.errors shouldBe 1
                
                // 2. Read timeout (recoverable)
                mockConnection.shouldFailWrite = false
                mockConnection.shouldFailRead = true
                val readResult = mockConnection.read(100L)
                readResult.shouldBeInstanceOf<Result.Error>()
                
                // Verify error count increased
                val statsAfterReadError = mockConnection.getStatistics()
                statsAfterReadError.errors shouldBe 2
                
                // 3. Connection loss during operation (should trigger reconnection if auto-reconnect enabled)
                mockConnection.shouldFailRead = false
                mockConnection.simulateDisconnection = true
                
                val sendResult = mockConnection.sendCommand("AT")
                sendResult.shouldBeInstanceOf<Result.Error>()
                
                // Connection should be marked as lost
                mockConnection.isConnected shouldBe false
                
            } finally {
                mockConnection.release()
            }
        }
    }
    
    "Property 11: Error recovery should use exponential backoff delays" {
        checkAll(
            iterations = 30,
            Arb.long(50..200), // baseDelay
            Arb.int(2..4) // maxAttempts (keep small for test performance)
        ) { baseDelay, maxAttempts ->
            
            val mockConnection = MockScannerConnection()
            val config = ConnectionConfig(
                autoReconnect = true,
                maxReconnectAttempts = maxAttempts,
                reconnectDelay = baseDelay,
                maxReconnectDelay = baseDelay * 8
            )
            
            try {
                // Test exponential backoff calculation
                for (attempt in 1..maxAttempts) {
                    val expectedDelay = config.getReconnectDelay(attempt)
                    
                    // Verify exponential growth pattern
                    val calculatedDelay = baseDelay * (1 shl (attempt - 1).coerceIn(0, 5))
                    val cappedDelay = calculatedDelay.coerceAtMost(config.maxReconnectDelay)
                    
                    expectedDelay shouldBe cappedDelay
                    
                    // Verify each delay is at least as long as the previous (until cap)
                    if (attempt > 1 && calculatedDelay <= config.maxReconnectDelay) {
                        val previousDelay = config.getReconnectDelay(attempt - 1)
                        expectedDelay shouldBe (previousDelay * 2).coerceAtMost(config.maxReconnectDelay)
                    }
                }
                
            } finally {
                mockConnection.release()
            }
        }
    }
    
    "Property 11: Non-recoverable errors should not trigger infinite retry loops" {
        checkAll(
            iterations = 30,
            Arb.string(5..15) // address
        ) { address ->
            
            val mockConnection = MockScannerConnection()
            val config = ConnectionConfig(
                autoReconnect = true,
                maxReconnectAttempts = 3,
                reconnectDelay = 50L
            )
            
            try {
                // Establish initial connection
                mockConnection.connect(address, config)
                
                // Configure to fail all subsequent connections (simulating hardware failure)
                mockConnection.shouldFailConnection = true
                mockConnection.simulateConnectionLoss()
                
                // Measure time taken for reconnection attempts
                val startTime = System.currentTimeMillis()
                val reconnectResult = mockConnection.reconnect()
                val endTime = System.currentTimeMillis()
                
                // Should fail after exactly maxReconnectAttempts
                reconnectResult.shouldBeInstanceOf<Result.Error>()
                
                val finalState = mockConnection.connectionState.value
                finalState.shouldBeInstanceOf<ConnectionState.Error>()
                finalState as ConnectionState.Error
                
                // After max attempts, error should be marked as non-recoverable
                finalState.isRecoverable shouldBe false
                finalState.attemptCount shouldBe config.maxReconnectAttempts
                
                // Verify reasonable time bounds (should not take too long due to exponential backoff cap)
                val totalTime = endTime - startTime
                val expectedMinTime = config.reconnectDelay * config.maxReconnectAttempts
                val expectedMaxTime = expectedMinTime * 10 // Allow for some overhead
                
                // Should complete within reasonable time bounds
                totalTime shouldBe (totalTime in expectedMinTime..expectedMaxTime)
                
            } finally {
                mockConnection.release()
            }
        }
    }
})