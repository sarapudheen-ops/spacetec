/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 *
 * This file is part of the SpaceTec professional automotive diagnostic system.
 */

package com.spacetec.obd.scanner.core

import com.spacetec.core.common.exceptions.CommunicationException
import com.spacetec.core.common.exceptions.ProtocolException
import com.spacetec.core.common.exceptions.TimeoutException
import com.spacetec.core.common.result.Result
import com.spacetec.core.domain.models.scanner.ScannerConnectionType
import com.spacetec.protocol.core.ProtocolDetector
import com.spacetec.protocol.core.ProtocolType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Vehicle information for protocol detection optimization.
 */
data class VehicleInfo(
    val make: String? = null,
    val model: String? = null,
    val year: Int? = null,
    val engineType: String? = null,
    val region: String? = null
) {
    /**
     * Checks if this is likely a heavy-duty vehicle.
     */
    val isHeavyDuty: Boolean
        get() = make?.uppercase() in HEAVY_DUTY_MAKES ||
                model?.uppercase()?.contains("TRUCK") == true ||
                model?.uppercase()?.contains("BUS") == true
    
    /**
     * Checks if this is likely a European vehicle.
     */
    val isEuropean: Boolean
        get() = make?.uppercase() in EUROPEAN_MAKES ||
                region?.uppercase() == "EUROPE" ||
                region?.uppercase() == "EU"
    
    /**
     * Checks if this is likely an American vehicle.
     */
    val isAmerican: Boolean
        get() = make?.uppercase() in AMERICAN_MAKES ||
                region?.uppercase() == "USA" ||
                region?.uppercase() == "NORTH_AMERICA"
    
    /**
     * Checks if this is likely an Asian vehicle.
     */
    val isAsian: Boolean
        get() = make?.uppercase() in ASIAN_MAKES ||
                region?.uppercase() == "ASIA" ||
                region?.uppercase() == "JAPAN"
    
    /**
     * Checks if this is a modern vehicle (2008+) that should support CAN.
     */
    val isModern: Boolean
        get() = year != null && year >= 2008
    
    companion object {
        private val HEAVY_DUTY_MAKES = setOf(
            "FREIGHTLINER", "PETERBILT", "KENWORTH", "MACK", "VOLVO TRUCKS",
            "INTERNATIONAL", "WESTERN STAR", "CATERPILLAR", "CUMMINS"
        )
        
        private val EUROPEAN_MAKES = setOf(
            "BMW", "MERCEDES", "MERCEDES-BENZ", "AUDI", "VOLKSWAGEN", "VW",
            "PORSCHE", "VOLVO", "SAAB", "PEUGEOT", "CITROEN", "RENAULT",
            "FIAT", "ALFA ROMEO", "JAGUAR", "LAND ROVER", "MINI", "SMART",
            "OPEL", "VAUXHALL", "SKODA", "SEAT"
        )
        
        private val AMERICAN_MAKES = setOf(
            "FORD", "CHEVROLET", "CHEVY", "GMC", "CADILLAC", "BUICK",
            "PONTIAC", "OLDSMOBILE", "SATURN", "HUMMER", "LINCOLN",
            "MERCURY", "CHRYSLER", "DODGE", "JEEP", "PLYMOUTH", "RAM"
        )
        
        private val ASIAN_MAKES = setOf(
            "TOYOTA", "HONDA", "NISSAN", "MAZDA", "SUBARU", "MITSUBISHI",
            "SUZUKI", "ISUZU", "ACURA", "LEXUS", "INFINITI", "HYUNDAI",
            "KIA", "DAEWOO", "SSANGYONG"
        )
    }
}

/**
 * Configuration for protocol detection.
 */
data class ProtocolDetectionConfig(
    val totalTimeout: Long = 30_000L,
    val protocolTimeout: Long = 5_000L,
    val retriesPerProtocol: Int = 2,
    val retryDelay: Long = 500L,
    val enableFallbackStrategies: Boolean = true,
    val enableVehicleOptimization: Boolean = true,
    val stopOnFirstMatch: Boolean = true,
    val skipProtocols: Set<ProtocolType> = emptySet(),
    val preferredProtocol: ProtocolType? = null,
    val validateProtocol: Boolean = true
) {
    companion object {
        val DEFAULT = ProtocolDetectionConfig()
        
        val FAST = ProtocolDetectionConfig(
            totalTimeout = 15_000L,
            protocolTimeout = 2_000L,
            retriesPerProtocol = 1,
            retryDelay = 250L,
            stopOnFirstMatch = true
        )
        
        val COMPREHENSIVE = ProtocolDetectionConfig(
            totalTimeout = 60_000L,
            protocolTimeout = 10_000L,
            retriesPerProtocol = 3,
            retryDelay = 1_000L,
            enableFallbackStrategies = true,
            stopOnFirstMatch = false
        )
    }
}

/**
 * Result of protocol detection.
 */
data class ProtocolDetectionResult(
    val success: Boolean,
    val detectedProtocol: ProtocolType? = null,
    val detectionTime: Long = 0,
    val testedProtocols: List<ProtocolType> = emptyList(),
    val fallbackUsed: Boolean = false,
    val confidence: Float = 0f,
    val errorMessage: String? = null,
    val vehicleInfo: VehicleInfo? = null
) {
    val isHighConfidence: Boolean
        get() = confidence >= 0.8f
    
    val isLowConfidence: Boolean
        get() = confidence < 0.5f
}

/**
 * Progress of protocol detection.
 */
sealed class ProtocolDetectionProgress {
    data object Started : ProtocolDetectionProgress()
    
    data class TestingProtocol(
        val protocol: ProtocolType,
        val protocolIndex: Int,
        val totalProtocols: Int,
        val progress: Float,
        val isFallback: Boolean = false
    ) : ProtocolDetectionProgress()
    
    data class ProtocolTested(
        val protocol: ProtocolType,
        val success: Boolean,
        val responseTime: Long,
        val confidence: Float = 0f
    ) : ProtocolDetectionProgress()
    
    data class FallbackStrategy(
        val strategy: String,
        val reason: String
    ) : ProtocolDetectionProgress()
    
    data class Detected(
        val result: ProtocolDetectionResult
    ) : ProtocolDetectionProgress()
    
    data class Failed(
        val error: String,
        val partialResult: ProtocolDetectionResult? = null
    ) : ProtocolDetectionProgress()
    
    data class Cancelled(
        val reason: String = "User cancelled"
    ) : ProtocolDetectionProgress()
}

/**
 * Fallback strategy for protocol detection.
 */
enum class FallbackStrategy(
    val description: String,
    val protocols: List<ProtocolType>
) {
    MODERN_VEHICLES_ONLY(
        description = "Modern vehicles (CAN protocols only)",
        protocols = listOf(
            ProtocolType.ISO_15765_4_CAN_11BIT_500K,
            ProtocolType.ISO_15765_4_CAN_29BIT_500K,
            ProtocolType.ISO_15765_4_CAN_11BIT_250K,
            ProtocolType.ISO_15765_4_CAN_29BIT_250K
        )
    ),
    
    LEGACY_PROTOCOLS_ONLY(
        description = "Legacy protocols (pre-CAN)",
        protocols = listOf(
            ProtocolType.ISO_14230_4_KWP_FAST,
            ProtocolType.ISO_14230_4_KWP_SLOW,
            ProtocolType.ISO_9141_2,
            ProtocolType.SAE_J1850_VPW,
            ProtocolType.SAE_J1850_PWM
        )
    ),
    
    COMMON_PROTOCOLS_ONLY(
        description = "Most common protocols",
        protocols = listOf(
            ProtocolType.ISO_15765_4_CAN_11BIT_500K,
            ProtocolType.SAE_J1850_VPW,
            ProtocolType.ISO_14230_4_KWP_FAST,
            ProtocolType.ISO_9141_2
        )
    ),
    
    HEAVY_DUTY_FOCUS(
        description = "Heavy duty vehicle protocols",
        protocols = listOf(
            ProtocolType.ISO_15765_4_CAN_29BIT_500K,
            ProtocolType.ISO_15765_4_CAN_29BIT_250K,
            ProtocolType.SAE_J1939,
            ProtocolType.ISO_15765_4_CAN_11BIT_500K
        )
    )
}

/**
 * Protocol detection engine that integrates with the existing ProtocolDetector.
 * 
 * Provides enhanced protocol detection with vehicle-specific optimization,
 * fallback strategies, and comprehensive error handling.
 */
@Singleton
class ProtocolDetectionEngine @Inject constructor(
    private val protocolDetector: ProtocolDetector,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    
    private val detectionMutex = Mutex()
    private val isCancelled = AtomicBoolean(false)
    
    private val _detectionState = MutableStateFlow<ProtocolDetectionState>(ProtocolDetectionState.Idle)
    val detectionState: StateFlow<ProtocolDetectionState> = _detectionState.asStateFlow()
    
    /**
     * Detects vehicle protocol with enhanced features.
     */
    suspend fun detectProtocol(
        connection: ScannerConnection,
        vehicleInfo: VehicleInfo? = null,
        config: ProtocolDetectionConfig = ProtocolDetectionConfig.DEFAULT
    ): Result<ProtocolDetectionResult> = detectionMutex.withLock {
        withContext(dispatcher) {
            isCancelled.set(false)
            _detectionState.value = ProtocolDetectionState.Detecting(0f, null)
            
            val startTime = System.currentTimeMillis()
            
            try {
                withTimeout(config.totalTimeout) {
                    // Try primary detection strategy
                    val primaryResult = tryPrimaryDetection(connection, vehicleInfo, config)
                    
                    if (primaryResult.success) {
                        val finalResult = primaryResult.copy(
                            detectionTime = System.currentTimeMillis() - startTime
                        )
                        _detectionState.value = ProtocolDetectionState.Completed(finalResult)
                        return@withTimeout Result.Success(finalResult)
                    }
                    
                    // Try fallback strategies if enabled
                    if (config.enableFallbackStrategies) {
                        val fallbackResult = tryFallbackStrategies(
                            connection = connection,
                            vehicleInfo = vehicleInfo,
                            config = config,
                            primaryResult = primaryResult
                        )
                        
                        if (fallbackResult.success) {
                            val finalResult = fallbackResult.copy(
                                detectionTime = System.currentTimeMillis() - startTime,
                                fallbackUsed = true
                            )
                            _detectionState.value = ProtocolDetectionState.Completed(finalResult)
                            return@withTimeout Result.Success(finalResult)
                        }
                    }
                    
                    // All strategies failed
                    val error = ProtocolException("No compatible protocol detected")
                    _detectionState.value = ProtocolDetectionState.Failed(error)
                    Result.Error(error)
                }
                
            } catch (e: CancellationException) {
                _detectionState.value = ProtocolDetectionState.Cancelled
                throw e
            } catch (e: Exception) {
                val error = ProtocolException("Protocol detection failed: ${e.message}", e)
                _detectionState.value = ProtocolDetectionState.Failed(error)
                Result.Error(error)
            }
        }
    }
    
    /**
     * Detects protocol with progress updates.
     */
    fun detectProtocolWithProgress(
        connection: ScannerConnection,
        vehicleInfo: VehicleInfo? = null,
        config: ProtocolDetectionConfig = ProtocolDetectionConfig.DEFAULT
    ): Flow<ProtocolDetectionProgress> = flow {
        emit(ProtocolDetectionProgress.Started)
        
        isCancelled.set(false)
        val startTime = System.currentTimeMillis()
        
        try {
            // Try primary detection
            val primaryResult = tryPrimaryDetectionWithProgress(
                connection = connection,
                vehicleInfo = vehicleInfo,
                config = config
            ) { progress ->
                emit(progress)
            }
            
            if (primaryResult.success) {
                val finalResult = primaryResult.copy(
                    detectionTime = System.currentTimeMillis() - startTime
                )
                emit(ProtocolDetectionProgress.Detected(finalResult))
                return@flow
            }
            
            // Try fallback strategies
            if (config.enableFallbackStrategies) {
                val fallbackResult = tryFallbackStrategiesWithProgress(
                    connection = connection,
                    vehicleInfo = vehicleInfo,
                    config = config,
                    primaryResult = primaryResult
                ) { progress ->
                    emit(progress)
                }
                
                if (fallbackResult.success) {
                    val finalResult = fallbackResult.copy(
                        detectionTime = System.currentTimeMillis() - startTime,
                        fallbackUsed = true
                    )
                    emit(ProtocolDetectionProgress.Detected(finalResult))
                    return@flow
                }
            }
            
            // All strategies failed
            emit(ProtocolDetectionProgress.Failed(
                error = "No compatible protocol detected",
                partialResult = primaryResult
            ))
            
        } catch (e: CancellationException) {
            emit(ProtocolDetectionProgress.Cancelled())
        } catch (e: Exception) {
            emit(ProtocolDetectionProgress.Failed(
                error = "Detection error: ${e.message}"
            ))
        }
    }
    
    /**
     * Tests a specific protocol.
     */
    suspend fun testProtocol(
        connection: ScannerConnection,
        protocol: ProtocolType,
        timeout: Long = 5000L
    ): Result<Boolean> {
        return withContext(dispatcher) {
            try {
                val result = protocolDetector.testProtocol(connection, protocol)
                Result.Success(result)
            } catch (e: Exception) {
                Result.Error(ProtocolException("Protocol test failed: ${e.message}", e))
            }
        }
    }
    
    /**
     * Cancels ongoing detection.
     */
    fun cancelDetection() {
        isCancelled.set(true)
        protocolDetector.cancelDetection()
    }
    
    /**
     * Gets optimized protocol order based on vehicle information.
     */
    fun getOptimizedProtocolOrder(
        vehicleInfo: VehicleInfo?,
        config: ProtocolDetectionConfig
    ): List<ProtocolType> {
        val order = mutableListOf<ProtocolType>()
        
        // Add preferred protocol first
        config.preferredProtocol?.let { preferred ->
            if (preferred !in config.skipProtocols) {
                order.add(preferred)
            }
        }
        
        // Get base order from existing detector
        val baseOrder = protocolDetector.getDetectionOrder(
            vehicleYear = vehicleInfo?.year,
            vehicleMake = vehicleInfo?.make,
            config = com.spacetec.protocol.core.DetectionConfig(
                totalTimeoutMs = config.totalTimeout,
                testTimeoutMs = config.protocolTimeout,
                retriesPerProtocol = config.retriesPerProtocol,
                skipProtocols = config.skipProtocols,
                preferredProtocol = config.preferredProtocol,
                stopOnFirstMatch = config.stopOnFirstMatch
            )
        )
        
        // Add protocols not already in the list
        for (protocol in baseOrder) {
            if (protocol !in order && protocol !in config.skipProtocols) {
                order.add(protocol)
            }
        }
        
        return order
    }
    
    /**
     * Tries primary detection strategy.
     */
    private suspend fun tryPrimaryDetection(
        connection: ScannerConnection,
        vehicleInfo: VehicleInfo?,
        config: ProtocolDetectionConfig
    ): ProtocolDetectionResult {
        val protocolOrder = getOptimizedProtocolOrder(vehicleInfo, config)
        val testedProtocols = mutableListOf<ProtocolType>()
        
        for (protocol in protocolOrder) {
            if (isCancelled.get()) {
                throw CancellationException("Detection cancelled")
            }
            
            testedProtocols.add(protocol)
            
            val testResult = testProtocolWithRetry(connection, protocol, config)
            
            if (testResult.success) {
                val confidence = calculateConfidence(protocol, vehicleInfo, testResult.responseTime)
                
                return ProtocolDetectionResult(
                    success = true,
                    detectedProtocol = protocol,
                    testedProtocols = testedProtocols,
                    confidence = confidence,
                    vehicleInfo = vehicleInfo
                )
            }
        }
        
        return ProtocolDetectionResult(
            success = false,
            testedProtocols = testedProtocols,
            errorMessage = "Primary detection failed",
            vehicleInfo = vehicleInfo
        )
    }
    
    /**
     * Tries primary detection with progress updates.
     */
    private suspend fun tryPrimaryDetectionWithProgress(
        connection: ScannerConnection,
        vehicleInfo: VehicleInfo?,
        config: ProtocolDetectionConfig,
        onProgress: suspend (ProtocolDetectionProgress) -> Unit
    ): ProtocolDetectionResult {
        val protocolOrder = getOptimizedProtocolOrder(vehicleInfo, config)
        val testedProtocols = mutableListOf<ProtocolType>()
        
        for ((index, protocol) in protocolOrder.withIndex()) {
            if (isCancelled.get()) {
                throw CancellationException("Detection cancelled")
            }
            
            val progress = (index + 1).toFloat() / protocolOrder.size
            onProgress(ProtocolDetectionProgress.TestingProtocol(
                protocol = protocol,
                protocolIndex = index + 1,
                totalProtocols = protocolOrder.size,
                progress = progress
            ))
            
            testedProtocols.add(protocol)
            
            val testResult = testProtocolWithRetry(connection, protocol, config)
            val confidence = if (testResult.success) {
                calculateConfidence(protocol, vehicleInfo, testResult.responseTime)
            } else 0f
            
            onProgress(ProtocolDetectionProgress.ProtocolTested(
                protocol = protocol,
                success = testResult.success,
                responseTime = testResult.responseTime,
                confidence = confidence
            ))
            
            if (testResult.success) {
                return ProtocolDetectionResult(
                    success = true,
                    detectedProtocol = protocol,
                    testedProtocols = testedProtocols,
                    confidence = confidence,
                    vehicleInfo = vehicleInfo
                )
            }
        }
        
        return ProtocolDetectionResult(
            success = false,
            testedProtocols = testedProtocols,
            errorMessage = "Primary detection failed",
            vehicleInfo = vehicleInfo
        )
    }
    
    /**
     * Tries fallback strategies.
     */
    private suspend fun tryFallbackStrategies(
        connection: ScannerConnection,
        vehicleInfo: VehicleInfo?,
        config: ProtocolDetectionConfig,
        primaryResult: ProtocolDetectionResult
    ): ProtocolDetectionResult {
        val strategies = selectFallbackStrategies(vehicleInfo, primaryResult)
        
        for (strategy in strategies) {
            if (isCancelled.get()) {
                throw CancellationException("Detection cancelled")
            }
            
            val result = tryStrategyProtocols(connection, strategy, config, primaryResult.testedProtocols)
            
            if (result.success) {
                return result.copy(
                    testedProtocols = primaryResult.testedProtocols + result.testedProtocols,
                    fallbackUsed = true
                )
            }
        }
        
        return ProtocolDetectionResult(
            success = false,
            testedProtocols = primaryResult.testedProtocols,
            errorMessage = "All fallback strategies failed",
            vehicleInfo = vehicleInfo
        )
    }
    
    /**
     * Tries fallback strategies with progress updates.
     */
    private suspend fun tryFallbackStrategiesWithProgress(
        connection: ScannerConnection,
        vehicleInfo: VehicleInfo?,
        config: ProtocolDetectionConfig,
        primaryResult: ProtocolDetectionResult,
        onProgress: suspend (ProtocolDetectionProgress) -> Unit
    ): ProtocolDetectionResult {
        val strategies = selectFallbackStrategies(vehicleInfo, primaryResult)
        
        for (strategy in strategies) {
            if (isCancelled.get()) {
                throw CancellationException("Detection cancelled")
            }
            
            onProgress(ProtocolDetectionProgress.FallbackStrategy(
                strategy = strategy.description,
                reason = "Primary detection failed, trying fallback strategy"
            ))
            
            val result = tryStrategyProtocolsWithProgress(
                connection = connection,
                strategy = strategy,
                config = config,
                alreadyTested = primaryResult.testedProtocols,
                onProgress = onProgress
            )
            
            if (result.success) {
                return result.copy(
                    testedProtocols = primaryResult.testedProtocols + result.testedProtocols,
                    fallbackUsed = true
                )
            }
        }
        
        return ProtocolDetectionResult(
            success = false,
            testedProtocols = primaryResult.testedProtocols,
            errorMessage = "All fallback strategies failed",
            vehicleInfo = vehicleInfo
        )
    }
    
    /**
     * Selects appropriate fallback strategies based on vehicle info and primary results.
     */
    private fun selectFallbackStrategies(
        vehicleInfo: VehicleInfo?,
        primaryResult: ProtocolDetectionResult
    ): List<FallbackStrategy> {
        val strategies = mutableListOf<FallbackStrategy>()
        
        when {
            vehicleInfo?.isModern == true -> {
                strategies.add(FallbackStrategy.MODERN_VEHICLES_ONLY)
                strategies.add(FallbackStrategy.COMMON_PROTOCOLS_ONLY)
            }
            vehicleInfo?.isHeavyDuty == true -> {
                strategies.add(FallbackStrategy.HEAVY_DUTY_FOCUS)
                strategies.add(FallbackStrategy.MODERN_VEHICLES_ONLY)
            }
            vehicleInfo?.year != null && vehicleInfo.year < 2008 -> {
                strategies.add(FallbackStrategy.LEGACY_PROTOCOLS_ONLY)
                strategies.add(FallbackStrategy.COMMON_PROTOCOLS_ONLY)
            }
            else -> {
                strategies.add(FallbackStrategy.COMMON_PROTOCOLS_ONLY)
                strategies.add(FallbackStrategy.MODERN_VEHICLES_ONLY)
                strategies.add(FallbackStrategy.LEGACY_PROTOCOLS_ONLY)
            }
        }
        
        return strategies
    }
    
    /**
     * Tries protocols from a specific strategy.
     */
    private suspend fun tryStrategyProtocols(
        connection: ScannerConnection,
        strategy: FallbackStrategy,
        config: ProtocolDetectionConfig,
        alreadyTested: List<ProtocolType>
    ): ProtocolDetectionResult {
        val protocolsToTest = strategy.protocols.filter { it !in alreadyTested && it !in config.skipProtocols }
        val testedProtocols = mutableListOf<ProtocolType>()
        
        for (protocol in protocolsToTest) {
            if (isCancelled.get()) {
                throw CancellationException("Detection cancelled")
            }
            
            testedProtocols.add(protocol)
            
            val testResult = testProtocolWithRetry(connection, protocol, config)
            
            if (testResult.success) {
                val confidence = calculateConfidence(protocol, null, testResult.responseTime)
                
                return ProtocolDetectionResult(
                    success = true,
                    detectedProtocol = protocol,
                    testedProtocols = testedProtocols,
                    confidence = confidence
                )
            }
        }
        
        return ProtocolDetectionResult(
            success = false,
            testedProtocols = testedProtocols,
            errorMessage = "Strategy ${strategy.description} failed"
        )
    }
    
    /**
     * Tries strategy protocols with progress updates.
     */
    private suspend fun tryStrategyProtocolsWithProgress(
        connection: ScannerConnection,
        strategy: FallbackStrategy,
        config: ProtocolDetectionConfig,
        alreadyTested: List<ProtocolType>,
        onProgress: suspend (ProtocolDetectionProgress) -> Unit
    ): ProtocolDetectionResult {
        val protocolsToTest = strategy.protocols.filter { it !in alreadyTested && it !in config.skipProtocols }
        val testedProtocols = mutableListOf<ProtocolType>()
        
        for ((index, protocol) in protocolsToTest.withIndex()) {
            if (isCancelled.get()) {
                throw CancellationException("Detection cancelled")
            }
            
            val progress = (index + 1).toFloat() / protocolsToTest.size
            onProgress(ProtocolDetectionProgress.TestingProtocol(
                protocol = protocol,
                protocolIndex = index + 1,
                totalProtocols = protocolsToTest.size,
                progress = progress,
                isFallback = true
            ))
            
            testedProtocols.add(protocol)
            
            val testResult = testProtocolWithRetry(connection, protocol, config)
            val confidence = if (testResult.success) {
                calculateConfidence(protocol, null, testResult.responseTime)
            } else 0f
            
            onProgress(ProtocolDetectionProgress.ProtocolTested(
                protocol = protocol,
                success = testResult.success,
                responseTime = testResult.responseTime,
                confidence = confidence
            ))
            
            if (testResult.success) {
                return ProtocolDetectionResult(
                    success = true,
                    detectedProtocol = protocol,
                    testedProtocols = testedProtocols,
                    confidence = confidence
                )
            }
        }
        
        return ProtocolDetectionResult(
            success = false,
            testedProtocols = testedProtocols,
            errorMessage = "Strategy ${strategy.description} failed"
        )
    }
    
    /**
     * Tests a protocol with retry logic.
     */
    private suspend fun testProtocolWithRetry(
        connection: ScannerConnection,
        protocol: ProtocolType,
        config: ProtocolDetectionConfig
    ): ProtocolTestResult {
        val startTime = System.currentTimeMillis()
        var lastException: Exception? = null
        
        repeat(config.retriesPerProtocol) { attempt ->
            try {
                val success = protocolDetector.testProtocol(connection, protocol)
                val responseTime = System.currentTimeMillis() - startTime
                
                return ProtocolTestResult(
                    success = success,
                    responseTime = responseTime
                )
                
            } catch (e: Exception) {
                lastException = e
                if (attempt < config.retriesPerProtocol - 1) {
                    delay(config.retryDelay)
                }
            }
        }
        
        val responseTime = System.currentTimeMillis() - startTime
        return ProtocolTestResult(
            success = false,
            responseTime = responseTime,
            error = lastException?.message
        )
    }
    
    /**
     * Calculates confidence score for a detected protocol.
     */
    private fun calculateConfidence(
        protocol: ProtocolType,
        vehicleInfo: VehicleInfo?,
        responseTime: Long
    ): Float {
        var confidence = 0.5f // Base confidence
        
        // Response time factor (faster = higher confidence)
        confidence += when {
            responseTime < 1000 -> 0.3f
            responseTime < 2000 -> 0.2f
            responseTime < 3000 -> 0.1f
            else -> 0f
        }
        
        // Vehicle compatibility factor
        vehicleInfo?.let { info ->
            confidence += when {
                // Modern vehicles with CAN protocols
                info.isModern && protocol.name.contains("CAN") -> 0.2f
                // Heavy duty with 29-bit CAN
                info.isHeavyDuty && protocol.name.contains("29BIT") -> 0.2f
                // European vehicles with KWP
                info.isEuropean && protocol.name.contains("KWP") -> 0.1f
                // American vehicles with J1850
                info.isAmerican && protocol.name.contains("J1850") -> 0.1f
                // Asian vehicles with ISO 9141
                info.isAsian && protocol.name.contains("9141") -> 0.1f
                else -> 0f
            }
        }
        
        return confidence.coerceIn(0f, 1f)
    }
}

/**
 * Result of a single protocol test.
 */
private data class ProtocolTestResult(
    val success: Boolean,
    val responseTime: Long,
    val error: String? = null
)

/**
 * State of protocol detection.
 */
sealed class ProtocolDetectionState {
    data object Idle : ProtocolDetectionState()
    
    data class Detecting(
        val progress: Float,
        val currentProtocol: ProtocolType?
    ) : ProtocolDetectionState()
    
    data class Completed(
        val result: ProtocolDetectionResult
    ) : ProtocolDetectionState()
    
    data class Failed(
        val error: Throwable
    ) : ProtocolDetectionState()
    
    data object Cancelled : ProtocolDetectionState()
}

/**
 * Protocol types enum for compatibility with existing system.
 */
enum class ProtocolType {
    ISO_15765_4_CAN_11BIT_500K,
    ISO_15765_4_CAN_29BIT_500K,
    ISO_15765_4_CAN_11BIT_250K,
    ISO_15765_4_CAN_29BIT_250K,
    ISO_14230_4_KWP_FAST,
    ISO_14230_4_KWP_SLOW,
    ISO_9141_2,
    SAE_J1850_VPW,
    SAE_J1850_PWM,
    SAE_J1939;
    
    val displayName: String
        get() = when (this) {
            ISO_15765_4_CAN_11BIT_500K -> "ISO 15765-4 CAN 11-bit 500k"
            ISO_15765_4_CAN_29BIT_500K -> "ISO 15765-4 CAN 29-bit 500k"
            ISO_15765_4_CAN_11BIT_250K -> "ISO 15765-4 CAN 11-bit 250k"
            ISO_15765_4_CAN_29BIT_250K -> "ISO 15765-4 CAN 29-bit 250k"
            ISO_14230_4_KWP_FAST -> "ISO 14230-4 KWP Fast Init"
            ISO_14230_4_KWP_SLOW -> "ISO 14230-4 KWP 5-baud Init"
            ISO_9141_2 -> "ISO 9141-2"
            SAE_J1850_VPW -> "SAE J1850 VPW"
            SAE_J1850_PWM -> "SAE J1850 PWM"
            SAE_J1939 -> "SAE J1939"
        }
}