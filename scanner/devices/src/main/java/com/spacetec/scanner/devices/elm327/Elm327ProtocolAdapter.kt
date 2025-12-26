/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 *
 * This file is part of the SpaceTec professional automotive diagnostic system.
 */

package com.spacetec.obd.scanner.devices.elm327

import com.spacetec.core.common.exceptions.CommunicationException
import com.spacetec.core.common.exceptions.ProtocolException
import com.spacetec.core.common.result.Result
import com.spacetec.obd.scanner.core.ScannerConnection
import com.spacetec.obd.scanner.core.ScannerProtocolAdapter
import com.spacetec.protocol.core.Protocol
import com.spacetec.protocol.core.ProtocolConfig
import com.spacetec.protocol.core.ProtocolType
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.regex.Pattern

/**
 * ELM327-specific protocol adapter for OBD-II communication.
 *
 * This adapter provides ELM327-specific command handling, protocol
 * initialization, and error recovery for ELM327-based OBD-II adapters.
 *
 * ## Features
 *
 * - Automatic ELM327 initialization sequence
 * - AT command handling and response parsing
 * - Protocol auto-detection and configuration
 * - Error recovery and retry mechanisms
 * - STN11xx/STN22xx extended command support
 *
 * @param connection The underlying scanner connection
 *
 * @author SpaceTec Development Team
 * @since 1.0.0
 */
class Elm327ProtocolAdapter(private val connection: ScannerConnection) {
    private val initializationMutex = Mutex()
    private var isInitialized = false
    private var firmwareVersion: String = ""
    private var hardwareVersion: String = ""
    private var voltage: Float = 0f

    /**
     * Initializes the ELM327 adapter with standard settings.
     *
     * @param config Protocol configuration
     * @return Success or error result
     */
    suspend fun initialize(config: ProtocolConfig = ProtocolConfig()): Result<Unit> = initializationMutex.withLock {
        if (isInitialized) {
            return@withLock Result.Success(Unit)
        }

        return@withLock try {
            // Reset the ELM327
            executeCommand(ELM327Commands.RESET, timeout = 5000L)
            
            // Wait for reset to complete
            delay(1000)
            
            // Clear any pending data
            connection.clearBuffers()
            
            // Configure basic settings
            configureBasicSettings()
            
            // Detect device information
            detectDeviceInfo()
            
            // Configure protocol if specified
            if (config.protocolType != ProtocolType.AUTO) {
                setProtocol(config.protocolType)
            }
            
            isInitialized = true
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(ProtocolException("ELM327 initialization failed: ${e.message}", e))
        }
    }

    /**
     * Configures basic ELM327 settings.
     */
    private suspend fun configureBasicSettings(): Result<Unit> {
        val commands = listOf(
            ELM327Commands.ECHO_OFF,      // Turn off echo
            ELM327Commands.LINEFEEDS_OFF, // Turn off line feeds
            ELM327Commands.SPACES_OFF,    // Turn off spaces
            ELM327Commands.TIMEOUT_OFF,   // Turn off timeout
            ELM327Commands.HEADERS_OFF    // Turn off headers
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
     * Detects and stores device information.
     */
    private suspend fun detectDeviceInfo() {
        try {
            // Get firmware version
            val firmwareResult = executeCommand(ELM327Commands.GET_FIRMWARE_VERSION, timeout = 2000L)
            if (firmwareResult is Result.Success) {
                firmwareVersion = firmwareResult.data
            }

            // Get device description
            val descriptionResult = executeCommand(ELM327Commands.GET_DESCRIPTION, timeout = 2000L)
            if (descriptionResult is Result.Success) {
                hardwareVersion = descriptionResult.data
            }

            // Get voltage
            val voltageResult = executeCommand(ELM327Commands.GET_VOLTAGE, timeout = 2000L)
            if (voltageResult is Result.Success) {
                val voltageStr = voltageResult.data.trim()
                val voltageMatch = Pattern.compile("\\d+\\.\\d+").matcher(voltageStr)
                if (voltageMatch.find()) {
                    voltage = voltageMatch.group().toFloatOrNull() ?: 0f
                }
            }
        } catch (e: Exception) {
            // Log error but continue - device info is optional
        }
    }

    /**
     * Sets the communication protocol.
     *
     * @param protocolType The protocol to set
     * @return Success or error result
     */
    suspend fun setProtocol(protocolType: ProtocolType): Result<Unit> {
        val protocolCode = when (protocolType) {
            ProtocolType.ISO_15765_4_CAN_11BIT_500K -> "6"
            ProtocolType.ISO_15765_4_CAN_29BIT_500K -> "7"
            ProtocolType.ISO_15765_4_CAN_11BIT_250K -> "8"
            ProtocolType.ISO_15765_4_CAN_29BIT_250K -> "9"
            ProtocolType.ISO_14230_4_KWP_FAST -> "3"
            ProtocolType.ISO_14230_4_KWP_SLOW -> "2"
            ProtocolType.ISO_9141_2 -> "4"
            ProtocolType.SAE_J1850_VPW -> "1"
            ProtocolType.SAE_J1850_PWM -> "0"
            ProtocolType.SAE_J1939 -> "A"
            else -> "0" // Auto-detect
        }

        val command = ELM327Commands.setProtocol(protocolCode)
        val result = executeCommand(command, timeout = 5000L)
        
        return if (result is Result.Success) {
            Result.Success(Unit)
        } else {
            result.map { }
        }
    }

    /**
     * Gets the current protocol description.
     *
     * @return Protocol description or error
     */
    suspend fun getCurrentProtocol(): Result<String> {
        return executeCommand(ELM327Commands.DESCRIBE_PROTOCOL, timeout = 2000L)
    }

    /**
     * Gets the current protocol number.
     *
     * @return Protocol number or error
     */
    suspend fun getCurrentProtocolNumber(): Result<String> {
        return executeCommand(ELM327Commands.DESCRIBE_PROTOCOL_NUMBER, timeout = 2000L)
    }

    /**
     * Executes an ELM327 AT command.
     *
     * @param command The AT command to execute
     * @param timeout Command timeout in milliseconds
     * @return Command response or error
     */
    suspend fun executeCommand(command: String, timeout: Long = 2000L): Result<String> {
        if (!connection.isConnected) {
            return Result.Error(CommunicationException("Connection not established"))
        }

        return try {
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
            
            val response = cleanResponse(responseResult.data)
            
            // Check for common error responses
            if (containsError(response)) {
                return Result.Error(ProtocolException("ELM327 command failed: $response"))
            }
            
            Result.Success(response)
        } catch (e: Exception) {
            Result.Error(CommunicationException("Command execution failed: ${e.message}", e))
        }
    }

    /**
     * Sends an OBD-II command and parses the response.
     *
     * @param command The OBD-II command to send (e.g., "0100", "0902")
     * @param timeout Response timeout in milliseconds
     * @return Parsed OBD response or error
     */
    suspend fun sendObdCommand(command: String, timeout: Long = 5000L): Result<String> {
        if (!isInitialized) {
            val initResult = initialize()
            if (initResult is Result.Error) {
                return Result.Error(ProtocolException("ELM327 not initialized: ${initResult.exception.message}"))
            }
        }

        return try {
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
            
            val response = cleanObdResponse(responseResult.data)
            
            // Parse the response
            if (containsError(response) || response.isEmpty() || response == "NO DATA") {
                Result.Error(ProtocolException("OBD command failed: $response"))
            } else {
                Result.Success(response)
            }
        } catch (e: Exception) {
            Result.Error(CommunicationException("OBD command failed: ${e.message}", e))
        }
    }

    /**
     * Gets the firmware version of the ELM327 device.
     */
    fun getFirmwareVersion(): String = firmwareVersion

    /**
     * Gets the hardware version of the ELM327 device.
     */
    fun getHardwareVersion(): String = hardwareVersion

    /**
     * Gets the measured voltage from the ELM327 device.
     */
    fun getVoltage(): Float = voltage

    /**
     * Checks if the adapter is initialized and ready.
     */
    fun isReady(): Boolean = isInitialized && connection.isConnected

    /**
     * Cleans an AT command response by removing prompts and formatting.
     */
    private fun cleanResponse(response: String): String {
        // Remove common prompts and clean up
        return response
            .replace(Regex(">\\s*$"), "")  // Remove trailing prompt
            .replace(Regex("\\s+"), " ")   // Normalize whitespace
            .trim()
    }

    /**
     * Cleans an OBD response - removes echo, prompts, and extracts hex data.
     */
    private fun cleanObdResponse(response: String): String {
        var cleaned = response
            .replace(Regex(">\\s*$"), "")  // Remove trailing prompt
            .replace(Regex("SEARCHING\\.\\.\\."), "") // Remove searching messages
        
        // Split by lines and filter
        val lines = cleaned.split(Regex("[\r\n]+"))
            .map { it.trim() }
            .filter { line ->
                line.isNotEmpty() &&
                !line.startsWith("AT", ignoreCase = true) &&
                !line.startsWith("SEARCHING", ignoreCase = true) &&
                line != "OK"
            }
        
        return lines.joinToString(" ")
            .replace(Regex("[^0-9A-Fa-f\\s]"), "")  // Keep only hex characters and spaces
            .replace(Regex("\\s+"), " ")            // Normalize spaces
            .trim()
            .uppercase()
    }

    /**
     * Checks if the response contains an error.
     */
    private fun containsError(response: String): Boolean {
        val upper = response.uppercase()
        val errorPatterns = listOf(
            "ERROR", "UNABLE TO CONNECT", "CAN ERROR", "BUS INIT",
            "NO DATA", "STOPPED", "FB ERROR", "DATA ERROR", "BUFFER FULL"
        )
        return errorPatterns.any { upper.contains(it) }
    }

    /**
     * Resets the ELM327 adapter.
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
     * Performs a soft reset of the ELM327 without full reinitialization.
     */
    suspend fun softReset(): Result<Unit> {
        return try {
            executeCommand(ELM327Commands.RESET_INTERFACE, timeout = 3000L)
            delay(500)
            connection.clearBuffers()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(ProtocolException("Soft reset failed: ${e.message}", e))
        }
    }

    /**
     * Gets device information as a map.
     */
    fun getDeviceInfo(): Map<String, String> {
        return mapOf(
            "firmware_version" to firmwareVersion,
            "hardware_version" to hardwareVersion,
            "voltage" to voltage.toString()
        )
    }

    companion object {
        /**
         * Creates an ELM327 protocol adapter for the specified connection.
         *
         * @param connection The scanner connection
         * @return ELM327 protocol adapter
         */
        fun create(connection: ScannerConnection): Elm327ProtocolAdapter {
            return Elm327ProtocolAdapter(connection)
        }

        /**
         * Checks if the connection is to an ELM327-compatible device.
         *
         * @param connection The scanner connection to test
         * @return True if the device appears to be ELM327-compatible
         */
        suspend fun isElm327Compatible(connection: ScannerConnection): Boolean {
            if (!connection.isConnected) return false

            return try {
                // Send a reset command
                val resetResult = connection.sendAndReceive(ELM327Commands.RESET, timeout = 5000L)
                if (resetResult is Result.Success) {
                    val response = resetResult.data
                    // Check if response contains ELM327 indicators
                    response.contains("ELM") || 
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