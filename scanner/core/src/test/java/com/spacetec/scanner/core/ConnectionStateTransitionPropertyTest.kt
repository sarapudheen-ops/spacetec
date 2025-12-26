/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 *
 * This file is part of the SpaceTec professional automotive diagnostic system.
 */

package com.spacetec.obd.scanner.core

import com.spacetec.core.common.result.Result
import com.spacetec.core.domain.models.scanner.ScannerConnectionType
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout

/**
 * **Feature: scanner-connection-system, Property 3: Automatic Reconnection with Exponential Backoff**
 * 
 * Property-based test for connection state transitions and automatic reconnection behavior.
 * 
 * **Validates: Requirements 1.3, 2.3, 6.2**
 * 
 * This test verifies that:
 * 1. Connection loss triggers automatic reconnection attempts
 * 2. Reconnection uses exponential backoff delays
 * 3. Maximum reconnection attempts are respected
 * 4. Connection state transitions are correct throughout the process
 */
class ConnectionStateTransitionPropertyTest : StringSpec({
    
    "Property 3: Automatic Reconnection with Exponential Backoff - Connection loss should trigger exactly the configured number of reconnection attempts with exponential backoff" {
        checkAll(
            iterations = 100,
            Arb.enum<ScannerConnectionType>(),
            Arb.int(1..5), // maxReconnectAttempts
            Arb.long(100..1000), // baseDelay
            Arb.string(5..20) // address
        ) { connectionType, maxAttempts, baseDelay, address ->
            
            // Given: A mock connection configured for reconnection testing
            val mockConnection = MockScannerConnection(connectionType)
            val config = ConnectionConfig(
                autoReconnect = true,
                maxReconnectAttempts = maxAttempts,
                reconnectDelay = baseDelay,
                maxReconnectDelay = baseDelay * 10
            )
            
            try {
                // When: Connection is established and then lost
                val connectResult = mockConnection.connect(address, config)
                connectResult.shouldBeInstanceOf<Result.Success<ConnectionInfo>>()
                
                // Verify initial connected state
                mockConnection.connectionState.value.shouldBeInstanceOf<ConnectionState.Connected>()
                mockConnection.isConnected shouldBe true
                
                // Configure mock to fail subsequent connection attempts
                mockConnection.shouldFailConnection = true
                
                // Simulate connection loss and trigger reconnection
                mockConnection.simulateConnectionLoss()
                
                // When: Reconnection is attempted
                val reconnectResult = mockConnection.reconnect()
                
                // Then: Reconnection should fail after exactly maxAttempts attempts
                reconnectResult.shouldBeInstanceOf<Result.Error>()
                
                // Verify the final state is Error with correct attempt count
                val finalState = mockConnection.connectionState.value
                finalState.shouldBeInstanceOf<ConnectionState.Error>()
                finalState as ConnectionState.Error
                finalState.attemptCount shouldBe maxAttempts
                finalState.isRecoverable shouldBe false
                
                // Verify that exactly maxAttempts connection attempts were made
                // (1 initial successful + maxAttempts failed reconnection attempts)
                mockConnection.getConnectionAttempts() shouldBe (1 + maxAttempts)
                
            } finally {
                mockConnection.release()
            }
        }
    }
    
    "Property 3: Exponential backoff delays should increase correctly between reconnection attempts" {
        checkAll(
            iterations = 50,
            Arb.long(100..500), // baseDelay
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
                // Verify exponential backoff calculation
                for (attempt in 1..maxAttempts) {
                    val expectedDelay = config.getReconnectDelay(attempt)
                    val calculatedDelay = baseDelay * (1 shl (attempt - 1).coerceIn(0, 5))
                    val cappedDelay = calculatedDelay.coerceAtMost(config.maxReconnectDelay)
                    
                    expectedDelay shouldBe cappedDelay
                    
                    // Verify exponential growth (until cap is reached)
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
    
    "Property 3: Connection state should transition correctly during reconnection process" {
        checkAll(
            iterations = 50,
            Arb.string(5..15), // address
            Arb.int(1..3) // maxAttempts
        ) { address, maxAttempts ->
            
            val mockConnection = MockScannerConnection()
            val config = ConnectionConfig(
                autoReconnect = true,
                maxReconnectAttempts = maxAttempts,
                reconnectDelay = 50L // Short delay for test performance
            )
            
            try {
                // Initial connection
                mockConnection.connect(address, config)
                mockConnection.connectionState.value.shouldBeInstanceOf<ConnectionState.Connected>()
                
                // Configure to fail reconnection attempts
                mockConnection.shouldFailConnection = true
                
                // Start reconnection and observe state transitions
                val reconnectJob = kotlinx.coroutines.GlobalScope.async {
                    mockConnection.reconnect()
                }
                
                // Allow some time for reconnection attempts to start
                delay(10)
                
                // Should be in Reconnecting state during attempts
                var currentState = mockConnection.connectionState.value
                var foundReconnectingState = false
                
                // Check for Reconnecting state within a reasonable time
                withTimeout(5000) {
                    while (!reconnectJob.isCompleted) {
                        currentState = mockConnection.connectionState.value
                        if (currentState is ConnectionState.Reconnecting) {
                            foundReconnectingState = true
                            // Verify attempt count is within expected range
                            currentState.attempt shouldBe (currentState.attempt in 1..maxAttempts)
                            currentState.maxAttempts shouldBe maxAttempts
                        }
                        delay(10)
                    }
                }
                
                // Wait for completion
                reconnectJob.await()
                
                // Final state should be Error after all attempts fail
                val finalState = mockConnection.connectionState.value
                finalState.shouldBeInstanceOf<ConnectionState.Error>()
                finalState as ConnectionState.Error
                finalState.attemptCount shouldBe maxAttempts
                
                // We should have observed the Reconnecting state
                foundReconnectingState shouldBe true
                
            } finally {
                mockConnection.release()
            }
        }
    }
    
    "Property 3: Successful reconnection should restore Connected state" {
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
                // Initial connection
                mockConnection.connect(address, config)
                mockConnection.connectionState.value.shouldBeInstanceOf<ConnectionState.Connected>()
                
                // Simulate connection loss
                mockConnection.simulateConnectionLoss()
                
                // Configure to succeed on reconnection (don't set shouldFailConnection)
                mockConnection.shouldFailConnection = false
                
                // Attempt reconnection
                val reconnectResult = mockConnection.reconnect()
                
                // Should succeed
                reconnectResult.shouldBeInstanceOf<Result.Success<ConnectionInfo>>()
                
                // Should be back in Connected state
                mockConnection.connectionState.value.shouldBeInstanceOf<ConnectionState.Connected>()
                mockConnection.isConnected shouldBe true
                
            } finally {
                mockConnection.release()
            }
        }
    }
})