/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 *
 * This file is part of the SpaceTec professional automotive diagnostic system.
 */

package com.spacetec.obd.scanner.core

import com.spacetec.core.common.exceptions.CommunicationException
import com.spacetec.core.common.exceptions.ProtocolException
import com.spacetec.core.common.result.Result
import com.spacetec.protocol.core.Protocol
import com.spacetec.protocol.core.ProtocolConfig
import com.spacetec.protocol.core.ProtocolType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Protocol adapter that handles communication between the scanner connection
 * and the diagnostic protocol layer.
 *
 * This adapter provides a standardized interface for:
 * - Protocol initialization and configuration
 * - Command/response translation
 * - Error handling and recovery
 * - Connection management
 * - State monitoring
 *
 * @param connection The underlying scanner connection
 * @param protocol The diagnostic protocol implementation
 *
 * @author SpaceTec Development Team
 * @since 1.0.0
 */
class ScannerProtocolAdapter(
    private val connection: ScannerConnection,
    private val protocol: Protocol
) {
    private val initialized = AtomicBoolean(false)

    /**
     * Gets the current connection state.
     */
    val connectionState: StateFlow<ConnectionState> = connection.connectionState

    /**
     * Gets the incoming data flow.
     */
    val incomingData: Flow<ByteArray> = connection.incomingData

    /**
     * Gets connection statistics.
     */
    fun getStatistics(): ConnectionStatistics = connection.getStatistics()

    /**
     * Initializes the protocol with the connection.
     *
     * @param config Protocol configuration
     * @return Success or error result
     */
    suspend fun initialize(config: ProtocolConfig = ProtocolConfig()): Result<Unit> {
        if (initialized.get()) {
            return Result.Success(Unit)
        }

        return try {
            // Configure the connection for the protocol
            configureConnectionForProtocol(config)
            
            // Initialize the protocol
            protocol.initialize(connection)
            
            // Verify protocol functionality
            if (protocol.verifyConnection()) {
                initialized.set(true)
                Result.Success(Unit)
            } else {
                Result.Error(ProtocolException("Protocol verification failed"))
            }
        } catch (e: Exception) {
            Result.Error(ProtocolException("Protocol initialization failed: ${e.message}", e))
        }
    }

    /**
     * Configures the connection for the specific protocol.
     */
    private suspend fun configureConnectionForProtocol(config: ProtocolConfig) {
        // Clear any pending data
        connection.clearBuffers()
        
        // Configure connection timeouts based on protocol requirements
        when (config.protocolType) {
            ProtocolType.ISO_15765_4_CAN_11BIT_500K,
            ProtocolType.ISO_15765_4_CAN_29BIT_500K -> {
                // CAN protocols may need specific timeouts
                connection.clearBuffers()
            }
            ProtocolType.ISO_14230_4_KWP_FAST -> {
                // KWP may need different configuration
                connection.clearBuffers()
            }
            else -> {
                // Default configuration
                connection.clearBuffers()
            }
        }
    }

    /**
     * Sends a diagnostic command and receives the response.
     *
     * @param command The diagnostic command to send
     * @param timeout Response timeout in milliseconds
     * @return Command response or error
     */
    suspend fun sendCommand(command: String, timeout: Long = ScannerConnection.DEFAULT_TIMEOUT): Result<String> {
        if (!initialized.get()) {
            return Result.Error(ProtocolException("Protocol not initialized"))
        }

        return try {
            // Send the command through the protocol
            val response = protocol.sendCommand(command, timeout)
            Result.Success(response)
        } catch (e: Exception) {
            Result.Error(CommunicationException("Command failed: ${e.message}", e))
        }
    }

    /**
     * Sends raw data through the connection.
     *
     * @param data Raw data to send
     * @return Number of bytes sent or error
     */
    suspend fun sendRaw(data: ByteArray): Result<Int> {
        if (!connection.isConnected) {
            return Result.Error(CommunicationException("Connection not established"))
        }

        return try {
            connection.write(data)
        } catch (e: Exception) {
            Result.Error(CommunicationException("Raw send failed: ${e.message}", e))
        }
    }

    /**
     * Receives raw data from the connection.
     *
     * @param timeout Receive timeout in milliseconds
     * @return Received data or error
     */
    suspend fun receiveRaw(timeout: Long = ScannerConnection.DEFAULT_TIMEOUT): Result<ByteArray> {
        if (!connection.isConnected) {
            return Result.Error(CommunicationException("Connection not established"))
        }

        return try {
            connection.read(timeout)
        } catch (e: Exception) {
            Result.Error(CommunicationException("Raw receive failed: ${e.message}", e))
        }
    }

    /**
     * Checks if the connection is active and protocol is initialized.
     */
    fun isReady(): Boolean = connection.isConnected && initialized.get()

    /**
     * Gets the current protocol type.
     */
    fun getProtocolType(): ProtocolType = protocol.type

    /**
     * Gets the protocol implementation.
     */
    fun getProtocol(): Protocol = protocol

    /**
     * Resets the protocol adapter.
     */
    suspend fun reset(): Result<Unit> {
        return try {
            protocol.reset()
            initialized.set(false)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(ProtocolException("Reset failed: ${e.message}", e))
        }
    }

    /**
     * Disconnects the protocol adapter.
     */
    suspend fun disconnect() {
        try {
            protocol.disconnect()
            connection.disconnect()
            initialized.set(false)
        } catch (e: Exception) {
            // Log error but don't throw to ensure cleanup
        }
    }

    /**
     * Releases all resources held by the adapter.
     */
    fun release() {
        try {
            protocol.release()
            connection.release()
        } catch (e: Exception) {
            // Log error but don't throw to ensure cleanup
        }
    }

    companion object {
        /**
         * Creates a protocol adapter for the specified connection and protocol type.
         *
         * @param connection The scanner connection
         * @param protocolType The protocol type to use
         * @return Protocol adapter instance
         */
        fun create(connection: ScannerConnection, protocolType: ProtocolType): ScannerProtocolAdapter {
            val protocol = when (protocolType) {
                ProtocolType.ISO_15765_4_CAN_11BIT_500K,
                ProtocolType.ISO_15765_4_CAN_29BIT_500K,
                ProtocolType.ISO_15765_4_CAN_11BIT_250K,
                ProtocolType.ISO_15765_4_CAN_29BIT_250K -> {
                    // Create CAN protocol implementation
                    createCANProtocol(protocolType)
                }
                ProtocolType.ISO_14230_4_KWP_FAST,
                ProtocolType.ISO_14230_4_KWP_SLOW -> {
                    // Create KWP protocol implementation
                    createKWPProtocol(protocolType)
                }
                ProtocolType.ISO_9141_2 -> {
                    // Create ISO 9141-2 protocol implementation
                    createISO9141Protocol()
                }
                ProtocolType.SAE_J1850_VPW,
                ProtocolType.SAE_J1850_PWM -> {
                    // Create J1850 protocol implementation
                    createJ1850Protocol(protocolType)
                }
                ProtocolType.SAE_J1939 -> {
                    // Create J1939 protocol implementation
                    createJ1939Protocol()
                }
                else -> {
                    // Default to a basic protocol implementation
                    createBasicProtocol(protocolType)
                }
            }

            return ScannerProtocolAdapter(connection, protocol)
        }

        private fun createCANProtocol(type: ProtocolType): Protocol {
            // Implementation would create CAN protocol instance
            return object : Protocol {
                override val type: ProtocolType = type
                override suspend fun initialize(connection: ScannerConnection) { /* Implementation */ }
                override suspend fun sendCommand(command: String, timeout: Long): String { /* Implementation */ return "" }
                override suspend fun verifyConnection(): Boolean { /* Implementation */ return true }
                override suspend fun reset() { /* Implementation */ }
                override suspend fun disconnect() { /* Implementation */ }
                override fun release() { /* Implementation */ }
            }
        }

        private fun createKWPProtocol(type: ProtocolType): Protocol {
            // Implementation would create KWP protocol instance
            return object : Protocol {
                override val type: ProtocolType = type
                override suspend fun initialize(connection: ScannerConnection) { /* Implementation */ }
                override suspend fun sendCommand(command: String, timeout: Long): String { /* Implementation */ return "" }
                override suspend fun verifyConnection(): Boolean { /* Implementation */ return true }
                override suspend fun reset() { /* Implementation */ }
                override suspend fun disconnect() { /* Implementation */ }
                override fun release() { /* Implementation */ }
            }
        }

        private fun createISO9141Protocol(): Protocol {
            // Implementation would create ISO 9141-2 protocol instance
            return object : Protocol {
                override val type: ProtocolType = ProtocolType.ISO_9141_2
                override suspend fun initialize(connection: ScannerConnection) { /* Implementation */ }
                override suspend fun sendCommand(command: String, timeout: Long): String { /* Implementation */ return "" }
                override suspend fun verifyConnection(): Boolean { /* Implementation */ return true }
                override suspend fun reset() { /* Implementation */ }
                override suspend fun disconnect() { /* Implementation */ }
                override fun release() { /* Implementation */ }
            }
        }

        private fun createJ1850Protocol(type: ProtocolType): Protocol {
            // Implementation would create J1850 protocol instance
            return object : Protocol {
                override val type: ProtocolType = type
                override suspend fun initialize(connection: ScannerConnection) { /* Implementation */ }
                override suspend fun sendCommand(command: String, timeout: Long): String { /* Implementation */ return "" }
                override suspend fun verifyConnection(): Boolean { /* Implementation */ return true }
                override suspend fun reset() { /* Implementation */ }
                override suspend fun disconnect() { /* Implementation */ }
                override fun release() { /* Implementation */ }
            }
        }

        private fun createJ1939Protocol(): Protocol {
            // Implementation would create J1939 protocol instance
            return object : Protocol {
                override val type: ProtocolType = ProtocolType.SAE_J1939
                override suspend fun initialize(connection: ScannerConnection) { /* Implementation */ }
                override suspend fun sendCommand(command: String, timeout: Long): String { /* Implementation */ return "" }
                override suspend fun verifyConnection(): Boolean { /* Implementation */ return true }
                override suspend fun reset() { /* Implementation */ }
                override suspend fun disconnect() { /* Implementation */ }
                override fun release() { /* Implementation */ }
            }
        }

        private fun createBasicProtocol(type: ProtocolType): Protocol {
            // Implementation would create basic protocol instance
            return object : Protocol {
                override val type: ProtocolType = type
                override suspend fun initialize(connection: ScannerConnection) { /* Implementation */ }
                override suspend fun sendCommand(command: String, timeout: Long): String { /* Implementation */ return "" }
                override suspend fun verifyConnection(): Boolean { /* Implementation */ return true }
                override suspend fun reset() { /* Implementation */ }
                override suspend fun disconnect() { /* Implementation */ }
                override fun release() { /* Implementation */ }
            }
        }
    }
}