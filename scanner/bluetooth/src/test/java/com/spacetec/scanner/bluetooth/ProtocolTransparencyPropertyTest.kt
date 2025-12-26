/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 *
 * This file is part of the SpaceTec professional automotive diagnostic system.
 */

package com.spacetec.obd.scanner.bluetooth

import com.spacetec.core.common.result.Result
import com.spacetec.core.domain.models.scanner.ScannerConnectionType
import com.spacetec.obd.scanner.core.BaseScannerConnection
import com.spacetec.obd.scanner.core.ConnectionConfig
import com.spacetec.obd.scanner.core.ConnectionInfo
import com.spacetec.obd.scanner.core.ScannerConnection
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * **Feature: scanner-connection-system, Property 4: Protocol Transparency**
 * 
 * Property-based test for protocol transparency across different connection types.
 * 
 * **Validates: Requirements 1.4, 2.4, 3.2, 4.3**
 * 
 * This test verifies that:
 * 1. The same diagnostic commands produce equivalent results regardless of underlying protocol
 * 2. Connection interface is consistent across Bluetooth Classic, BLE, WiFi, and USB
 * 3. Data transfer operations behave identically across protocols
 * 4. Error handling is consistent across connection types
 */
class ProtocolTransparencyPropertyTest : StringSpec({

    // Mock connection implementation for testing protocol transparency
    class MockProtocolConnection(
        override val connectionType: ScannerConnectionType,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ) : BaseScannerConnection(dispatcher) {

        var mockResponse = "OK\r>"
        var isPhysicallyConnected = false
        private val sentCommands = mutableListOf<String>()

        fun getSentCommands() = sentCommands.toList()
        fun clearCommands() = sentCommands.clear()

        override suspend fun doConnect(address: String, config: ConnectionConfig): ConnectionInfo {
            isPhysicallyConnected = true
            return ConnectionInfo(
                remoteAddress = address,
                connectionType = connectionType,
                mtu = when (connectionType) {
                    ScannerConnectionType.BLUETOOTH_CLASSIC -> 512
                    ScannerConnectionType.BLUETOOTH_LE -> 247
                    ScannerConnectionType.WIFI -> 1500
                    ScannerConnectionType.USB -> 4096
                }
            )
        }

        override suspend fun doDisconnect(graceful: Boolean) {
            isPhysicallyConnected = false
        }

        override suspend fun doWrite(data: ByteArray): Int {
            if (!isPhysicallyConnected) throw Exception("Not connected")
            val command = String(data, Charsets.US_ASCII).trim()
            sentCommands.add(command)
            return data.size
        }

        override suspend fun doRead(buffer: ByteArray, timeout: Long): Int {
            if (!isPhysicallyConnected) throw Exception("Not connected")
            val responseBytes = mockResponse.toByteArray(Charsets.US_ASCII)
            val bytesToCopy = minOf(responseBytes.size, buffer.size)
            System.arraycopy(responseBytes, 0, buffer, 0, bytesToCopy)
            return bytesToCopy
        }

        override suspend fun doAvailable(): Int {
            return if (isPhysicallyConnected) mockResponse.length else 0
        }

        override suspend fun doClearBuffers() {
            // Mock implementation
        }
    }

    // Generator for OBD commands
    val obdCommandArb = Arb.element(
        "ATZ",      // Reset
        "ATE0",     // Echo off
        "ATL0",     // Linefeeds off
        "ATS0",     // Spaces off
        "ATH1",     // Headers on
        "ATSP0",    // Auto protocol
        "0100",     // Supported PIDs 01-20
        "0120",     // Supported PIDs 21-40
        "010C",     // Engine RPM
        "010D",     // Vehicle speed
        "0105",     // Coolant temp
        "03",       // Read DTCs
        "04"        // Clear DTCs
    )

    val connectionTypeArb = Arb.enum<ScannerConnectionType>()

    val addressArb = Arb.element(
        "AA:BB:CC:DD:EE:FF",  // Bluetooth MAC
        "192.168.0.10:35000", // WiFi IP:port
        "/dev/ttyUSB0"        // USB path
    )

    "Property 4: Protocol Transparency - Same commands should produce equivalent results across all connection types" {
        checkAll(
            iterations = 100,
            obdCommandArb,
            Arb.string(5..20) // mock response
        ) { command, response ->
            // Given: Connections of all types with same mock response
            val connections = ScannerConnectionType.values().map { type ->
                MockProtocolConnection(type).apply {
                    mockResponse = "$response\r>"
                }
            }

            val config = ConnectionConfig.DEFAULT
            val results = mutableMapOf<ScannerConnectionType, String?>()

            try {
                // When: Sending the same command through each connection type
                connections.forEach { connection ->
                    val address = when (connection.connectionType) {
                        ScannerConnectionType.BLUETOOTH_CLASSIC,
                        ScannerConnectionType.BLUETOOTH_LE -> "AA:BB:CC:DD:EE:FF"
                        ScannerConnectionType.WIFI -> "192.168.0.10:35000"
                        ScannerConnectionType.USB -> "/dev/ttyUSB0"
                    }

                    val connectResult = connection.connect(address, config)
                    connectResult.shouldBeInstanceOf<Result.Success<ConnectionInfo>>()

                    val sendResult = connection.sendAndReceive(command, 5000L, ">")
                    if (sendResult is Result.Success) {
                        results[connection.connectionType] = sendResult.data
                    }
                }

                // Then: All connection types should return equivalent responses
                val uniqueResponses = results.values.filterNotNull().toSet()
                
                // All responses should be equivalent (same content)
                if (uniqueResponses.isNotEmpty()) {
                    uniqueResponses.size shouldBe 1
                }

                // All connection types should have sent the same command
                connections.forEach { connection ->
                    val sentCommands = connection.getSentCommands()
                    sentCommands.isNotEmpty() shouldBe true
                    sentCommands.last() shouldBe command
                }

            } finally {
                connections.forEach { it.release() }
            }
        }
    }

    "Property 4: Protocol Transparency - Connection interface should be identical across types" {
        checkAll(
            iterations = 50,
            connectionTypeArb
        ) { connectionType ->
            // Given: A connection of any type
            val connection = MockProtocolConnection(connectionType)

            try {
                // Then: All connections should implement the same interface
                connection.shouldBeInstanceOf<ScannerConnection>()
                connection.shouldBeInstanceOf<BaseScannerConnection>()

                // And: Should have the same properties available
                connection.connectionId shouldNotBe null
                connection.connectionType shouldBe connectionType
                connection.connectionState shouldNotBe null
                connection.incomingData shouldNotBe null
                connection.isConnected shouldBe false // Not connected yet
                connection.config shouldNotBe null

            } finally {
                connection.release()
            }
        }
    }

    "Property 4: Protocol Transparency - Write operations should behave identically" {
        checkAll(
            iterations = 100,
            connectionTypeArb,
            Arb.byteArray(Arb.int(1..100), Arb.byte())
        ) { connectionType, data ->
            // Given: A connected connection of any type
            val connection = MockProtocolConnection(connectionType)
            val config = ConnectionConfig.DEFAULT

            try {
                val address = "AA:BB:CC:DD:EE:FF"
                connection.connect(address, config)

                // When: Writing data
                val writeResult = connection.write(data)

                // Then: Write should succeed and return correct byte count
                writeResult.shouldBeInstanceOf<Result.Success<Int>>()
                (writeResult as Result.Success).data shouldBe data.size

            } finally {
                connection.release()
            }
        }
    }

    "Property 4: Protocol Transparency - Statistics tracking should be consistent" {
        checkAll(
            iterations = 50,
            connectionTypeArb,
            Arb.int(1..10) // number of commands to send
        ) { connectionType, commandCount ->
            // Given: A connected connection
            val connection = MockProtocolConnection(connectionType)
            val config = ConnectionConfig.DEFAULT

            try {
                connection.connect("AA:BB:CC:DD:EE:FF", config)
                connection.resetStatistics()

                // When: Sending multiple commands
                repeat(commandCount) {
                    connection.sendCommand("ATZ")
                }

                // Then: Statistics should accurately reflect operations
                val stats = connection.getStatistics()
                stats.commandsSent shouldBe commandCount
                stats.bytesSent shouldBeGreaterThanOrEqual (commandCount * 4L) // "ATZ\r" = 4 bytes

            } finally {
                connection.release()
            }
        }
    }

    "Property 4: Protocol Transparency - Error handling should be consistent across types" {
        checkAll(
            iterations = 50,
            connectionTypeArb
        ) { connectionType ->
            // Given: A connection that is not connected
            val connection = MockProtocolConnection(connectionType)

            try {
                // When: Attempting to write without connecting
                val writeResult = connection.write("test".toByteArray())

                // Then: Should return error consistently across all types
                writeResult.shouldBeInstanceOf<Result.Error>()

                // And: Attempting to read should also fail
                val readResult = connection.read(1000L)
                readResult.shouldBeInstanceOf<Result.Error>()

            } finally {
                connection.release()
            }
        }
    }

    "Property 4: Protocol Transparency - Disconnect behavior should be consistent" {
        checkAll(
            iterations = 50,
            connectionTypeArb,
            Arb.boolean() // graceful disconnect
        ) { connectionType, graceful ->
            // Given: A connected connection
            val connection = MockProtocolConnection(connectionType)
            val config = ConnectionConfig.DEFAULT

            try {
                connection.connect("AA:BB:CC:DD:EE:FF", config)
                connection.isConnected shouldBe true

                // When: Disconnecting
                connection.disconnect(graceful)

                // Then: Connection should be disconnected
                connection.isConnected shouldBe false

                // And: Subsequent operations should fail
                val writeResult = connection.write("test".toByteArray())
                writeResult.shouldBeInstanceOf<Result.Error>()

            } finally {
                connection.release()
            }
        }
    }

    "Property 4: Protocol Transparency - Configuration should apply consistently" {
        checkAll(
            iterations = 50,
            connectionTypeArb,
            Arb.long(1000..30000), // connectionTimeout
            Arb.long(500..10000),  // readTimeout
            Arb.boolean()          // autoReconnect
        ) { connectionType, connTimeout, readTimeout, autoReconnect ->
            // Given: A custom configuration
            val config = ConnectionConfig(
                connectionTimeout = connTimeout,
                readTimeout = readTimeout,
                autoReconnect = autoReconnect
            )

            val connection = MockProtocolConnection(connectionType)

            try {
                // When: Connecting with custom config
                connection.connect("AA:BB:CC:DD:EE:FF", config)

                // Then: Configuration should be applied
                connection.config.connectionTimeout shouldBe connTimeout
                connection.config.readTimeout shouldBe readTimeout
                connection.config.autoReconnect shouldBe autoReconnect

            } finally {
                connection.release()
            }
        }
    }
})

// Extension to check greater than or equal for Long
private infix fun Long.shouldBeGreaterThanOrEqual(expected: Long) {
    if (this < expected) {
        throw AssertionError("Expected $this to be greater than or equal to $expected")
    }
}
