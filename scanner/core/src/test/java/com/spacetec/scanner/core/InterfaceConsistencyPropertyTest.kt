/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 *
 * This file is part of the SpaceTec professional automotive diagnostic system.
 */

package com.spacetec.obd.scanner.core

import android.content.Context
import com.spacetec.core.common.result.Result
import com.spacetec.core.domain.models.scanner.ScannerConnectionType
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking

/**
 * Property-based tests for interface consistency across connection types.
 *
 * **Feature: scanner-connection-system, Property 15: Interface Consistency Across Connection Types**
 *
 * Tests that all scanner connection implementations provide identical interfaces
 * and behavior regardless of the underlying connection type, ensuring that
 * higher-level diagnostic functions work consistently across all scanner types.
 *
 * ## Properties Tested
 *
 * 1. **Interface Consistency**: All connection types implement the same interface
 * 2. **Method Availability**: All required methods are available on all connection types
 * 3. **Return Type Consistency**: Methods return the same types across connection types
 * 4. **Error Handling Consistency**: Error conditions are handled consistently
 * 5. **State Management Consistency**: Connection states behave identically
 *
 * ## Test Strategy
 *
 * - Generate random connection types and addresses
 * - Create connections using the factory
 * - Verify interface consistency across all connection types
 * - Test method signatures and return types
 * - Validate error handling patterns
 *
 * **Validates: Requirements 9.1, 9.2, 9.4**
 *
 * @author SpaceTec Development Team
 * @since 1.0.0
 */
class InterfaceConsistencyPropertyTest : StringSpec({

    val mockContext = mockk<Context>(relaxed = true)
    val connectionFactory = ScannerConnectionFactory(mockContext)

    // Mock Android system services
    every { mockContext.getSystemService(Context.BLUETOOTH_SERVICE) } returns null
    every { mockContext.getSystemService(Context.WIFI_SERVICE) } returns null
    every { mockContext.getSystemService(Context.USB_SERVICE) } returns null
    every { mockContext.packageManager } returns mockk(relaxed = true)

    "Property 15: All connection types implement identical ScannerConnection interface" {
        checkAll(
            iterations = 100,
            Arb.connectionType()
        ) { connectionType ->
            
            // Create connection for the type
            val connection = connectionFactory.createConnection(connectionType)
            
            // Verify interface implementation
            connection.shouldBeInstanceOf<ScannerConnection>()
            
            // Verify all required properties are available
            connection.connectionId shouldNotBe null
            connection.connectionType shouldBe connectionType
            connection.connectionState shouldNotBe null
            connection.incomingData shouldNotBe null
            connection.config shouldNotBe null
            
            // Verify all required methods are available (compile-time check)
            // These calls verify the methods exist with correct signatures
            verifyMethodSignatures(connection)
            
            connection.release()
        }
    }

    "Property 15: Connection factory creates appropriate types for addresses" {
        checkAll(
            iterations = 100,
            Arb.addressForConnectionType()
        ) { (address, expectedType) ->
            
            // Detect connection type from address
            val detectedType = connectionFactory.detectConnectionType(address)
            detectedType shouldBe expectedType
            
            // Create connection for address
            val connection = connectionFactory.createConnectionForAddress(address)
            connection.connectionType shouldBe expectedType
            
            connection.release()
        }
    }

    "Property 15: All connection types handle invalid operations consistently" {
        checkAll(
            iterations = 50,
            Arb.connectionType(),
            Arb.string(1, 100)
        ) { connectionType, command ->
            
            val connection = connectionFactory.createConnection(connectionType)
            
            // Test operations on disconnected connection
            runBlocking {
                // All connection types should return error for operations when not connected
                val writeResult = connection.write("test".toByteArray())
                writeResult.shouldBeInstanceOf<Result.Error>()
                
                val readResult = connection.read(1000)
                readResult.shouldBeInstanceOf<Result.Error>()
                
                val sendResult = connection.sendCommand(command)
                sendResult.shouldBeInstanceOf<Result.Error>()
                
                val sendReceiveResult = connection.sendAndReceive(command, 1000)
                sendReceiveResult.shouldBeInstanceOf<Result.Error>()
            }
            
            connection.release()
        }
    }

    "Property 15: Connection state management is consistent across types" {
        checkAll(
            iterations = 50,
            Arb.connectionType()
        ) { connectionType ->
            
            val connection = connectionFactory.createConnection(connectionType)
            
            // Initial state should be Disconnected
            connection.connectionState.value.shouldBeInstanceOf<ConnectionState.Disconnected>()
            connection.isConnected shouldBe false
            
            // Statistics should be available
            val stats = connection.getStatistics()
            stats shouldNotBe null
            stats.bytesSent shouldBe 0
            stats.bytesReceived shouldBe 0
            stats.commandsSent shouldBe 0
            stats.responsesReceived shouldBe 0
            
            connection.release()
        }
    }

    "Property 15: Configuration handling is consistent across types" {
        checkAll(
            iterations = 50,
            Arb.connectionType(),
            Arb.connectionConfig()
        ) { connectionType, config ->
            
            val connection = connectionFactory.createConnectionWithConfig(connectionType, config)
            
            // Configuration should be accessible
            connection.config shouldNotBe null
            
            // Default configurations should be appropriate for connection type
            val defaultConfig = connectionFactory.getDefaultConfig(connectionType)
            defaultConfig shouldNotBe null
            
            // Optimized configurations should be creatable
            val fastConfig = connectionFactory.createOptimizedConfig(connectionType, "fast")
            fastConfig shouldNotBe null
            fastConfig.readTimeout shouldBe 1000L
            
            val slowConfig = connectionFactory.createOptimizedConfig(connectionType, "slow")
            slowConfig shouldNotBe null
            slowConfig.readTimeout shouldBe 30_000L
            
            connection.release()
        }
    }

    "Property 15: Factory validation methods work consistently" {
        checkAll(
            iterations = 100,
            Arb.addressForConnectionType()
        ) { (address, expectedType) ->
            
            // Address validation should be consistent
            val isValid = connectionFactory.validateAddress(address, expectedType)
            isValid shouldBe true
            
            // Suggestions should include the expected type
            val suggestions = connectionFactory.suggestConnectionTypes(address)
            suggestions shouldNotBe emptyList<ScannerConnectionType>()
            suggestions.contains(expectedType) shouldBe true
            
            // Factory info should be consistent
            val factoryInfo = connectionFactory.getFactoryInfo()
            factoryInfo shouldNotBe null
            factoryInfo.supportedConnectionTypes shouldNotBe emptyList<ScannerConnectionType>()
            factoryInfo.version shouldNotBe null
        }
    }

    "Property 15: Error categorization is consistent across connection types" {
        checkAll(
            iterations = 50,
            Arb.connectionType()
        ) { connectionType ->
            
            val connection = connectionFactory.createConnection(connectionType)
            
            // Test with mock connection that extends BaseScannerConnection
            if (connection is BaseScannerConnection) {
                // Test error categorization consistency
                val timeoutError = kotlinx.coroutines.TimeoutCancellationException("Test timeout")
                val isRecoverable = connection.categorizeError(timeoutError)
                isRecoverable shouldBe true
                
                val connectionError = com.spacetec.core.common.exceptions.ConnectionException("Test connection error")
                val isConnectionRecoverable = connection.categorizeError(connectionError)
                isConnectionRecoverable shouldBe true
            }
            
            connection.release()
        }
    }

}) {
    companion object {
        
        /**
         * Verifies that all required methods exist with correct signatures.
         * This is a compile-time verification that the interface is consistent.
         */
        private fun verifyMethodSignatures(connection: ScannerConnection) {
            // Connection management methods
            connection::connect
            connection::disconnect
            connection::reconnect
            
            // Data transfer methods
            connection::write
            connection::sendCommand
            connection::read
            connection::readUntil
            connection::sendAndReceive
            connection::sendAndReceiveBytes
            
            // Buffer management methods
            connection::clearBuffers
            connection::available
            
            // Statistics and lifecycle methods
            connection::getStatistics
            connection::resetStatistics
            connection::release
            connection::close
            
            // Properties
            connection.connectionId
            connection.connectionType
            connection.connectionState
            connection.incomingData
            connection.isConnected
            connection.config
        }
        
        /**
         * Generates arbitrary connection types.
         */
        private fun Arb.Companion.connectionType(): Arb<ScannerConnectionType> = arbitrary {
            choice(
                ScannerConnectionType.BLUETOOTH_CLASSIC,
                ScannerConnectionType.BLUETOOTH_LE,
                ScannerConnectionType.WIFI,
                ScannerConnectionType.USB,
                ScannerConnectionType.J2534
            ).bind()
        }
        
        /**
         * Generates arbitrary connection configurations.
         */
        private fun Arb.Companion.connectionConfig(): Arb<ConnectionConfig> = arbitrary {
            ConnectionConfig(
                connectionTimeout = choice(5000L, 10000L, 15000L).bind(),
                readTimeout = choice(1000L, 5000L, 10000L).bind(),
                writeTimeout = choice(1000L, 5000L, 10000L).bind(),
                autoReconnect = choice(true, false).bind(),
                maxReconnectAttempts = choice(1, 3, 5).bind(),
                bufferSize = choice(1024, 4096, 8192).bind()
            )
        }
        
        /**
         * Generates addresses appropriate for connection types.
         */
        private fun Arb.Companion.addressForConnectionType(): Arb<Pair<String, ScannerConnectionType>> = arbitrary {
            val connectionType = connectionType().bind()
            val address = when (connectionType) {
                ScannerConnectionType.BLUETOOTH_CLASSIC,
                ScannerConnectionType.BLUETOOTH_LE -> {
                    // Generate valid Bluetooth MAC address
                    "AA:BB:CC:DD:EE:FF"
                }
                ScannerConnectionType.WIFI -> {
                    // Generate valid IP:port address
                    "192.168.1.100:35000"
                }
                ScannerConnectionType.USB -> {
                    // Generate valid USB device path
                    "/dev/bus/usb/001/002"
                }
                ScannerConnectionType.J2534 -> {
                    // Generate valid J2534 device name
                    "MyJ2534Device"
                }
                else -> "unknown"
            }
            Pair(address, connectionType)
        }
    }
}