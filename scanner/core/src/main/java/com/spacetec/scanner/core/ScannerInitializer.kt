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
import com.spacetec.core.domain.models.scanner.ScannerConnectionType
import com.spacetec.obd.scanner.devices.elm327.Elm327ProtocolAdapter
import com.spacetec.obd.scanner.devices.obdlink.OBDLinkProtocolAdapter
import com.spacetec.protocol.core.ProtocolType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Initialization progress information.
 */
data class InitializationProgress(
    val step: String,
    val progress: Float,
    val message: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Result of scanner initialization.
 */
data class InitializationResult(
    val success: Boolean,
    val protocolType: ProtocolType? = null,
    val deviceInfo: Map<String, String> = emptyMap(),
    val errorMessage: String? = null,
    val initializationTime: Long = 0
)

/**
 * Scanner initializer that handles the complete initialization process.
 *
 * This initializer manages:
 * - Connection establishment
 * - Protocol detection and configuration
 * - Device information gathering
 * - Error recovery and fallback mechanisms
 * - Progress reporting
 * - Initialization validation
 *
 * @param connectionFactory Factory for creating scanner connections
 * @param protocolDetectionEngine Engine for protocol detection
 * @param dispatcher Coroutine dispatcher for initialization operations
 *
 * @author SpaceTec Development Team
 * @since 1.0.0
 */
class ScannerInitializer(
    private val connectionFactory: ScannerConnectionFactory,
    private val protocolDetectionEngine: ProtocolDetectionEngine,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val _initializationProgress = MutableStateFlow<InitializationProgress?>(null)
    val initializationProgress: StateFlow<InitializationProgress?> = _initializationProgress.asStateFlow()

    private val _initializationState = MutableStateFlow<ScannerConnectionState>(ScannerConnectionState.Disconnected)
    val initializationState: StateFlow<ScannerConnectionState> = _initializationState.asStateFlow()

    /**
     * Initializes a scanner connection with full protocol setup.
     *
     * @param address Device address
     * @param config Scanner configuration
     * @param vehicleInfo Optional vehicle information for protocol optimization
     * @return Initialization result
     */
    suspend fun initialize(
        address: String,
        config: ScannerConfig = ScannerConfig.DEFAULT,
        vehicleInfo: VehicleInfo? = null
    ): Result<InitializationResult> = withContext(dispatcher) {
        val startTime = System.currentTimeMillis()
        
        try {
            // Update state to connecting
            _initializationState.value = ScannerConnectionState.Connecting("Device")
            
            // Update progress
            _initializationProgress.value = InitializationProgress(
                step = "connecting",
                progress = 0.1f,
                message = "Establishing connection..."
            )

            // Create connection based on address
            val connectionType = connectionFactory.detectConnectionType(address)
            val connection = connectionFactory.createConnection(connectionType)
            
            // Connect to device
            val connectResult = connection.connect(
                address,
                ConnectionConfig(
                    connectionTimeout = config.connectionTimeoutMs,
                    readTimeout = config.readTimeoutMs,
                    writeTimeout = config.writeTimeoutMs,
                    autoReconnect = config.autoReconnect,
                    maxReconnectAttempts = config.maxReconnectAttempts,
                    reconnectDelay = config.reconnectDelayMs,
                    maxReconnectDelay = config.maxReconnectDelayMs,
                    bufferSize = config.bufferSize,
                    keepAliveInterval = config.keepAliveIntervalMs,
                    flushAfterWrite = config.flushAfterWrite
                )
            )
            
            if (connectResult is Result.Error) {
                _initializationState.value = ScannerConnectionState.Error(
                    "Connection failed: ${connectResult.exception.message}",
                    connectResult.exception
                )
                return@withContext Result.Error(CommunicationException("Connection failed", connectResult.exception))
            }

            // Update progress
            _initializationProgress.value = InitializationProgress(
                step = "protocol_detection",
                progress = 0.3f,
                message = "Detecting protocol..."
            )

            // Update state to initializing
            _initializationState.value = ScannerConnectionState.Initializing("Device")

            // Detect protocol
            val protocolResult = if (config.enableProtocolDetection) {
                val detectionConfig = ProtocolDetectionConfig(
                    totalTimeout = config.readTimeoutMs,
                    protocolTimeout = config.readTimeoutMs / 2,
                    retriesPerProtocol = 2,
                    enableFallbackStrategies = true,
                    enableVehicleOptimization = true,
                    stopOnFirstMatch = true
                )
                
                protocolDetectionEngine.detectProtocol(connection, vehicleInfo, detectionConfig)
            } else {
                Result.Success(ProtocolDetectionResult(
                    success = true,
                    detectedProtocol = config.protocolType,
                    testedProtocols = listOf(config.protocolType)
                ))
            }

            if (protocolResult is Result.Error) {
                connection.disconnect()
                _initializationState.value = ScannerConnectionState.Error(
                    "Protocol detection failed: ${protocolResult.exception.message}",
                    protocolResult.exception
                )
                return@withContext Result.Error(ProtocolException("Protocol detection failed", protocolResult.exception))
            }

            val detectedProtocol = protocolResult.data.detectedProtocol ?: ProtocolType.AUTO

            // Update progress
            _initializationProgress.value = InitializationProgress(
                step = "device_identification",
                progress = 0.6f,
                message = "Identifying device..."
            )

            // Get device information
            val deviceInfo = when {
                Elm327ProtocolAdapter.isElm327Compatible(connection) -> {
                    val adapter = Elm327ProtocolAdapter.create(connection)
                    val initResult = adapter.initialize()
                    if (initResult is Result.Success) {
                        adapter.getDeviceInfo()
                    } else {
                        emptyMap()
                    }
                }
                OBDLinkProtocolAdapter.isOBDLinkCompatible(connection) -> {
                    val adapter = OBDLinkProtocolAdapter.create(connection)
                    val initResult = adapter.initialize()
                    if (initResult is Result.Success) {
                        adapter.getDeviceInfo()
                    } else {
                        emptyMap()
                    }
                }
                else -> {
                    // Try basic identification
                    identifyGenericDevice(connection)
                }
            }

            // Update progress
            _initializationProgress.value = InitializationProgress(
                step = "configuration",
                progress = 0.8f,
                message = "Configuring device..."
            )

            // Configure device based on protocol and settings
            configureDevice(connection, detectedProtocol, config)

            // Update progress
            _initializationProgress.value = InitializationProgress(
                step = "validation",
                progress = 0.95f,
                message = "Validating connection..."
            )

            // Validate the connection
            val validationResult = validateConnection(connection)
            if (!validationResult) {
                connection.disconnect()
                _initializationState.value = ScannerConnectionState.Error("Connection validation failed")
                return@withContext Result.Error(CommunicationException("Connection validation failed"))
            }

            // Update state to connected
            _initializationState.value = ScannerConnectionState.Connected(
                "Device",
                detectedProtocol
            )

            // Update progress
            _initializationProgress.value = InitializationProgress(
                step = "completed",
                progress = 1.0f,
                message = "Initialization completed successfully"
            )

            val result = InitializationResult(
                success = true,
                protocolType = detectedProtocol,
                deviceInfo = deviceInfo,
                initializationTime = System.currentTimeMillis() - startTime
            )

            Result.Success(result)

        } catch (e: Exception) {
            _initializationState.value = ScannerConnectionState.Error(
                "Initialization failed: ${e.message}",
                e
            )
            Result.Error(CommunicationException("Initialization failed", e))
        }
    }

    /**
     * Identifies a generic device by sending basic commands.
     */
    private suspend fun identifyGenericDevice(connection: ScannerConnection): Map<String, String> {
        val info = mutableMapOf<String, String>()
        
        try {
            // Try to get device info with common commands
            val response = connection.sendAndReceive("ATI", timeout = 2000L)
            if (response is Result.Success) {
                info["device_info"] = response.data
            }
            
            // Try to get voltage
            val voltageResponse = connection.sendAndReceive("ATRV", timeout = 2000L)
            if (voltageResponse is Result.Success) {
                info["voltage"] = voltageResponse.data
            }
        } catch (e: Exception) {
            // Ignore errors during identification
        }
        
        return info
    }

    /**
     * Configures the device based on protocol and settings.
     */
    private suspend fun configureDevice(
        connection: ScannerConnection,
        protocolType: ProtocolType,
        config: ScannerConfig
    ) {
        try {
            // Send basic configuration commands based on protocol
            val commands = mutableListOf<String>()
            
            // Common configuration commands
            commands.add("ATE${if (config.enableEcho) "1" else "0"}") // Echo
            commands.add("ATH${if (config.enableHeaders) "1" else "0"}") // Headers
            commands.add("ATL${if (config.enableLineFeeds) "1" else "0"}") // Line feeds
            commands.add("ATS${if (config.enableSpaces) "1" else "0"}") // Spaces
            commands.add("ATAT${config.adaptiveTiming}") // Adaptive timing
            
            // Protocol-specific configuration
            when (protocolType) {
                ProtocolType.ISO_15765_4_CAN_11BIT_500K,
                ProtocolType.ISO_15765_4_CAN_29BIT_500K,
                ProtocolType.ISO_15765_4_CAN_11BIT_250K,
                ProtocolType.ISO_15765_4_CAN_29BIT_250K -> {
                    // CAN-specific configuration
                    commands.add("ATCANS") // CAN auto baud
                }
                ProtocolType.ISO_14230_4_KWP_FAST,
                ProtocolType.ISO_14230_4_KWP_SLOW -> {
                    // KWP-specific configuration
                    commands.add("ATKWP") // KWP mode
                }
                ProtocolType.ISO_9141_2 -> {
                    // ISO 9141-2 specific configuration
                    commands.add("ATI9141") // ISO 9141 mode
                }
                ProtocolType.SAE_J1850_VPW,
                ProtocolType.SAE_J1850_PWM -> {
                    // J1850-specific configuration
                    commands.add("ATJ1850") // J1850 mode
                }
                else -> {
                    // Auto-detect protocol
                    commands.add("ATSP0") // Auto protocol
                }
            }
            
            // Send all configuration commands
            for (command in commands) {
                connection.sendAndReceive(command, timeout = 1000L)
            }
        } catch (e: Exception) {
            // Log error but continue - some devices may not support all commands
        }
    }

    /**
     * Validates the connection by sending a test command.
     */
    private suspend fun validateConnection(connection: ScannerConnection): Boolean {
        return try {
            val response = connection.sendAndReceive("0100", timeout = 3000L)
            response is Result.Success && !response.data.contains("ERROR")
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Cancels ongoing initialization.
     */
    fun cancelInitialization() {
        // This would cancel any ongoing operations
        _initializationProgress.value = null
    }

    /**
     * Gets the current initialization state.
     */
    fun getCurrentState(): ScannerConnectionState = initializationState.value

    /**
     * Gets the current initialization progress.
     */
    fun getCurrentProgress(): InitializationProgress? = initializationProgress.value

    companion object {
        /**
         * Creates a scanner initializer.
         *
         * @param connectionFactory Factory for creating scanner connections
         * @param protocolDetectionEngine Engine for protocol detection
         * @return Scanner initializer
         */
        fun create(
            connectionFactory: ScannerConnectionFactory,
            protocolDetectionEngine: ProtocolDetectionEngine
        ): ScannerInitializer {
            return ScannerInitializer(connectionFactory, protocolDetectionEngine)
        }
    }
}