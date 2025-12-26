/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 *
 * This file is part of the SpaceTec professional automotive diagnostic system.
 */

package com.spacetec.obd.scanner.devices.obdlink

import com.spacetec.core.common.exceptions.CommunicationException
import com.spacetec.core.common.exceptions.ProtocolException
import com.spacetec.core.common.result.Result
import com.spacetec.obd.scanner.core.ScannerConnection
import com.spacetec.obd.scanner.devices.elm327.ELM327Commands
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * OBDLink-specific protocol adapter for OBD-II communication.
 *
 * This adapter provides OBDLink-specific command handling, protocol
 * initialization, and error recovery for OBDLink MX/EX/SX and similar devices.
 * OBDLink devices are ELM327-compatible but include additional proprietary
 * commands and enhanced features.
 *
 * ## Features
 *
 * - OBDLink device detection and identification
 * - Proprietary OBDLink command support
 * - Enhanced protocol initialization
 * - Device-specific configuration
 * - Voltage monitoring and status reporting
 *
 * @param connection The underlying scanner connection
 *
 * @author SpaceTec Development Team
 * @since 1.0.0
 */
class OBDLinkProtocolAdapter(private val connection: ScannerConnection) {
    private val initializationMutex = Mutex()
    private var isInitialized = false
    private var deviceModel: String = ""
    private var firmwareVersion: String = ""
    private var serialNumber: String = ""
    private var voltage: Float = 0f

    /**
     * Initializes the OBDLink adapter with device-specific settings.
     *
     * @return Success or error result
     */
    suspend fun initialize(): Result<Unit> = initializationMutex.withLock {
        if (isInitialized) {
            return@withLock Result.Success(Unit)
        }

        return@withLock try {
            // Reset the device
            executeCommand(ELM327Commands.RESET, timeout = 5000L)
            delay(1000)
            
            // Clear any pending data
            connection.clearBuffers()
            
            // Configure basic settings
            configureBasicSettings()
            
            // Detect device information
            detectDeviceInfo()
            
            isInitialized = true
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(ProtocolException("OBDLink initialization failed: ${e.message}", e))
        }
    }

    /**
     * Configures basic OBDLink settings.
     */
    private suspend fun configureBasicSettings(): Result<Unit> {
        val commands = listOf(
            "ATZ",    // Reset
            "ATE0",   // Echo off
            "ATL0",   // Line feeds off
            "ATS0",   // Spaces off
            "ATSP0",  // Protocol auto
            "ATSTFF", // Timeout max
            "ATAT1",  // Adaptive timing on
            "ATH0",   // Headers off
            "ATI",    // Get device info
        )

        for (command in commands) {
            val result = executeCommand(command, timeout = 2000L)
            if (result is Result.Error) {
                return result
            }
        }

        return Result.Success(Unit)
    }

    /**
     * Detects and stores OBDLink device information.
     */
    private suspend fun detectDeviceInfo() {
        try {
            // Get device info
            val infoResult = executeCommand("ATI", timeout = 2000L)
            if (infoResult is Result.Success) {
                deviceModel = infoResult.data
            }

            // Get firmware version
            val firmwareResult = executeCommand("@2", timeout = 2000L)
            if (firmwareResult is Result.Success) {
                firmwareVersion = firmwareResult.data
            }

            // Get serial number
            val serialResult = executeCommand("@1", timeout = 2000L)
            if (serialResult is Result.Success) {
                serialNumber = serialResult.data
            }

            // Get voltage
            val voltageResult = executeCommand("ATRV", timeout = 2000L)
            if (voltageResult is Result.Success) {
                val voltageStr = voltageResult.data.trim()
                val voltageMatch = Regex("""[\d.]+""").find(voltageStr)
                if (voltageMatch != null) {
                    voltage = voltageMatch.value.toFloatOrNull() ?: 0f
                }
            }
        } catch (e: Exception) {
            // Log error but continue - device info is optional
        }
    }

    /**
     * Executes an OBDLink command.
     *
     * @param command The command to execute
     * @param timeout Command timeout in milliseconds
     * @return Command response or error
     */
    suspend fun executeCommand(command: String, timeout: Long = 2000L): Result<String> {
        if (!connection.isConnected) {
            return Result.Error(CommunicationException("Connection not established"))
        }

        try {
            // Clear buffers before sending
            connection.clearBuffers()
            
            // Send the command
            val sendResult = connection.sendCommand(command)
            if (sendResult is Result.Error) {
                return sendResult
            }
            
            // Read the response
            val responseResult = connection.readUntil(timeout = timeout)
            if (responseResult is Result.Error) {
                return responseResult
            }
            
            val response = responseResult.data
            
            // Check for common error responses
            if (response.contains("ERROR") || 
                response.contains("UNABLE TO CONNECT") || 
                response.contains("CAN ERROR") ||
                response.contains("BUS INIT: ERROR") ||
                response.contains("NO DATA")) {
                return Result.Error(ProtocolException("OBDLink command failed: $response"))
            }
            
            return Result.Success(response)
        } catch (e: Exception) {
            return Result.Error(CommunicationException("Command execution failed: ${e.message}", e))
        }
    }

    /**
     * Sends an OBD-II command through the OBDLink device.
     *
     * @param command The OBD-II command to send
     * @param timeout Response timeout in milliseconds
     * @return Parsed OBD response or error
     */
    suspend fun sendObdCommand(command: String, timeout: Long = 5000L): Result<String> {
        if (!isInitialized) {
            val initResult = initialize()
            if (initResult is Result.Error) {
                return Result.Error(ProtocolException("OBDLink not initialized: ${initResult.exception.message}"))
            }
        }

        try {
            // Clear buffers before sending
            connection.clearBuffers()
            
            // Send the OBD command
            val sendResult = connection.sendCommand(command)
            if (sendResult is Result.Error) {
                return sendResult
            }
            
            // Read the response
            val responseResult = connection.readUntil(timeout = timeout)
            if (responseResult is Result.Error) {
                return responseResult
            }
            
            val response = responseResult.data
            
            // Parse the response
            return if (response.contains("ERROR") || 
                      response.contains("NO DATA") || 
                      response.contains("STOPPED") ||
                      response.contains("UNABLE TO CONNECT")) {
                Result.Error(ProtocolException("OBD command failed: $response"))
            } else {
                Result.Success(response)
            }
        } catch (e: Exception) {
            return Result.Error(CommunicationException("OBD command failed: ${e.message}", e))
        }
    }

    /**
     * Gets the device model.
     */
    fun getDeviceModel(): String = deviceModel

    /**
     * Gets the firmware version.
     */
    fun getFirmwareVersion(): String = firmwareVersion

    /**
     * Gets the serial number.
     */
    fun getSerialNumber(): String = serialNumber

    /**
     * Gets the measured voltage.
     */
    fun getVoltage(): Float = voltage

    /**
     * Checks if the adapter is initialized and ready.
     */
    fun isReady(): Boolean = isInitialized && connection.isConnected

    /**
     * Resets the OBDLink device.
     */
    suspend fun reset(): Result<Unit> {
        return try {
            executeCommand(ELM327Commands.RESET, timeout = 5000L)
            delay(1000) // Wait for reset to complete
            connection.clearBuffers()
            isInitialized = false
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(ProtocolException("Reset failed: ${e.message}", e))
        }
    }

    /**
     * Gets device information as a map.
     */
    fun getDeviceInfo(): Map<String, String> {
        return mapOf(
            "device_model" to deviceModel,
            "firmware_version" to firmwareVersion,
            "serial_number" to serialNumber,
            "voltage" to voltage.toString()
        )
    }

    companion object {
        /**
         * Creates an OBDLink protocol adapter for the specified connection.
         *
         * @param connection The scanner connection
         * @return OBDLink protocol adapter
         */
        fun create(connection: ScannerConnection): OBDLinkProtocolAdapter {
            return OBDLinkProtocolAdapter(connection)
        }

        /**
         * Checks if the connection is to an OBDLink-compatible device.
         *
         * @param connection The scanner connection to test
         * @return True if the device appears to be OBDLink-compatible
         */
        suspend fun isOBDLinkCompatible(connection: ScannerConnection): Boolean {
            if (!connection.isConnected) return false

            return try {
                // Send a reset command
                val resetResult = connection.sendAndReceive("ATZ", timeout = 5000L)
                if (resetResult is Result.Success) {
                    val response = resetResult.data
                    // Check if response contains OBDLink indicators
                    response.contains("OBDLink") || 
                    response.contains("ELM327") || 
                    response.contains("OBD") ||
                    response.startsWith(">") // ELM327 prompt
                } else {
                    false
                }
            } catch (e: Exception) {
                false
            }
        }
    }
}