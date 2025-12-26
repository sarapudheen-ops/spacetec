/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 *
 * This file is part of the SpaceTec professional automotive diagnostic system.
 */

package com.spacetec.obd.scanner.wifi

import com.spacetec.core.common.result.Result
import com.spacetec.obd.scanner.core.ConnectionConfig
import com.spacetec.obd.scanner.core.ConnectionInfo
import com.spacetec.obd.scanner.core.ConnectionState
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.longs.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.delay

/**
 * **Feature: scanner-connection-system, Property 6: Disconnection Detection Timing**
 *
 * Property-based test for WiFi disconnection detection timing.
 *
 * **Validates: Requirements 2.3, 3.3**
 *
 * This test verifies that:
 * 1. Disconnection is detected within 5 seconds (per requirement 2.3)
 * 2. Appropriate reconnection behavior is triggered
 * 3. Connection state is updated correctly on disconnection
 * 4. Detection timing is consistent across different scenarios
 */
class DisconnectionDetectionPropertyTest : StringSpec({

    "Property 6: Disconnection Detection Timing - Disconnection should be detected within 5 seconds" {
        checkAll(
            iterations = 50,
            Arb.string(7..15),
            Arb.int(0..4000) // disconnection detection delay (should be < 5000ms)
        ) { address, detectionDelayMs ->

            val mockConnection = MockWiFiConnection()
            val config = ConnectionConfig(
                connectionTimeout = 10000L,
                readTimeout = 5000L,
                autoReconnect = false
            )

            try {
                // Given: An established connection
                mockConnection.connectionDelay = 100L
                val connectResult = mockConnection.connect(address, config)
                connectResult.shouldBeInstanceOf<Result.Success<ConnectionInfo>>()
                mockConnection.isConnected shouldBe true

                // Configure detection delay
                mockConnection.disconnectionDetectionDelay = detectionDelayMs.toLong()

                // When: Connection is lost
                val disconnectionStart = System.currentTimeMillis()
                mockConnection.simulateConnectionLoss()
                val disconnectionEnd = System.currentTimeMillis()

                // Then: Disconnection should be detected within 5 seconds
                val detectionTime = mockConnection.getDisconnectionDetectionTime()
                detectionTime shouldBeLessThanOrEqual WiFiConstants.DISCONNECTION_DETECTION_TIMEOUT

                // Connection state should reflect disconnection
                val state = mockConnection.connectionState.value
                state.shouldBeInstanceOf<ConnectionState.Error>()
                (state as ConnectionState.Error).isRecoverable shouldBe true

                // Physical connection should be lost
                mockConnection.isPhysicallyConnected() shouldBe false

            } finally {
                mockConnection.release()
            }
        }
    }

    "Property 6: Disconnection should trigger appropriate state transition" {
        checkAll(
            iterations = 30,
            Arb.string(7..15)
        ) { address ->

            val mockConnection = MockWiFiConnection()
            val config = ConnectionConfig(
                connectionTimeout = 10000L,
                readTimeout = 5000L,
                autoReconnect = false
            )

            try {
                // Given: An established connection
                mockConnection.connectionDelay = 100L
                mockConnection.connect(address, config)
                
                // Verify initial state
                mockConnection.connectionState.value.shouldBeInstanceOf<ConnectionState.Connected>()

                // When: Connection is lost
                mockConnection.simulateConnectionLoss()

                // Then: State should transition to Error
                val state = mockConnection.connectionState.value
                state.shouldBeInstanceOf<ConnectionState.Error>()

                // Error should be marked as recoverable
                (state as ConnectionState.Error).isRecoverable shouldBe true

                // isConnected should return false
                mockConnection.isConnected shouldBe false

            } finally {
                mockConnection.release()
            }
        }
    }

    "Property 6: Disconnection detection should work consistently across multiple connections" {
        checkAll(
            iterations = 20,
            Arb.int(2..5) // number of connection cycles
        ) { cycles ->

            val mockConnection = MockWiFiConnection()
            val config = ConnectionConfig(
                connectionTimeout = 10000L,
                readTimeout = 5000L,
                autoReconnect = false
            )

            try {
                for (cycle in 1..cycles) {
                    // Connect
                    mockConnection.reset()
                    mockConnection.connectionDelay = 100L
                    val connectResult = mockConnection.connect("192.168.0.$cycle:35000", config)
                    connectResult.shouldBeInstanceOf<Result.Success<ConnectionInfo>>()

                    // Simulate disconnection
                    mockConnection.disconnectionDetectionDelay = (cycle * 500).toLong()
                    mockConnection.simulateConnectionLoss()

                    // Verify detection within 5 seconds
                    val detectionTime = mockConnection.getDisconnectionDetectionTime()
                    detectionTime shouldBeLessThanOrEqual WiFiConstants.DISCONNECTION_DETECTION_TIMEOUT

                    // Verify state
                    mockConnection.connectionState.value.shouldBeInstanceOf<ConnectionState.Error>()
                }

            } finally {
                mockConnection.release()
            }
        }
    }

    "Property 6: Disconnection during communication should be detected promptly" {
        checkAll(
            iterations = 30,
            Arb.string(7..15)
        ) { address ->

            val mockConnection = MockWiFiConnection()
            val config = ConnectionConfig(
                connectionTimeout = 10000L,
                readTimeout = 5000L,
                autoReconnect = false
            )

            try {
                // Given: An established connection with ongoing communication
                mockConnection.connectionDelay = 100L
                mockConnection.connect(address, config)

                // Perform some communication
                mockConnection.write("ATZ\r".toByteArray())

                // When: Connection is lost during communication
                mockConnection.simulateDisconnection = true
                
                // Try to write - should fail
                val writeResult = mockConnection.write("ATI\r".toByteArray())
                writeResult.shouldBeInstanceOf<Result.Error>()

                // Then: Connection should be detected as lost
                mockConnection.isPhysicallyConnected() shouldBe false

            } finally {
                mockConnection.release()
            }
        }
    }

    "Property 6: Immediate reconnection should be attempted after disconnection detection" {
        checkAll(
            iterations = 30,
            Arb.string(7..15)
        ) { address ->

            val mockConnection = MockWiFiConnection()
            val config = ConnectionConfig(
                connectionTimeout = 10000L,
                readTimeout = 5000L,
                autoReconnect = true,
                maxReconnectAttempts = 3
            )

            try {
                // Given: An established connection
                mockConnection.connectionDelay = 100L
                mockConnection.connect(address, config)
                val initialAttempts = mockConnection.getConnectionAttempts()

                // When: Connection is lost
                mockConnection.simulateConnectionLoss()

                // Then: State should indicate error (recoverable)
                val state = mockConnection.connectionState.value
                state.shouldBeInstanceOf<ConnectionState.Error>()
                (state as ConnectionState.Error).isRecoverable shouldBe true

                // Reconnection can be attempted
                mockConnection.shouldFailConnection = false
                mockConnection.simulateDisconnection = false
                val reconnectResult = mockConnection.reconnect()

                // Should succeed
                reconnectResult.shouldBeInstanceOf<Result.Success<ConnectionInfo>>()
                mockConnection.isConnected shouldBe true

                // Additional connection attempt should have been made
                mockConnection.getConnectionAttempts() shouldBe (initialAttempts + 1)

            } finally {
                mockConnection.release()
            }
        }
    }

    "Property 6: Detection timing should be independent of connection duration" {
        checkAll(
            iterations = 20,
            Arb.int(100..2000), // connection duration before disconnect
            Arb.int(100..3000) // detection delay
        ) { connectionDurationMs, detectionDelayMs ->

            val mockConnection = MockWiFiConnection()
            val config = ConnectionConfig(
                connectionTimeout = 10000L,
                readTimeout = 5000L,
                autoReconnect = false
            )

            try {
                // Connect
                mockConnection.connectionDelay = 100L
                mockConnection.connect("192.168.0.10:35000", config)

                // Wait for specified duration
                delay(connectionDurationMs.toLong())

                // Configure detection delay
                mockConnection.disconnectionDetectionDelay = detectionDelayMs.toLong()

                // Simulate disconnection
                mockConnection.simulateConnectionLoss()

                // Detection should still be within 5 seconds regardless of connection duration
                val detectionTime = mockConnection.getDisconnectionDetectionTime()
                detectionTime shouldBeLessThanOrEqual WiFiConstants.DISCONNECTION_DETECTION_TIMEOUT

            } finally {
                mockConnection.release()
            }
        }
    }
})
