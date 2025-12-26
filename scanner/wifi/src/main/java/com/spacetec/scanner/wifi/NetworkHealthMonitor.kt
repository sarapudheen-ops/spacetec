/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 *
 * This file is part of the SpaceTec professional automotive diagnostic system.
 */

package com.spacetec.obd.scanner.wifi

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Network health monitoring and adaptation system.
 *
 * Provides comprehensive monitoring of network conditions including:
 * - Response time tracking and analysis
 * - Timeout adjustment recommendations
 * - Network quality assessment
 * - Connection health monitoring with automatic recovery suggestions
 *
 * Per requirements 2.3 and 2.5:
 * - Detect disconnection within 5 seconds
 * - Adjust timeout values dynamically based on response times
 *
 * @param dispatcher Coroutine dispatcher for monitoring operations
 *
 * @author SpaceTec Development Team
 * @since 1.0.0
 */
class NetworkHealthMonitor(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    // State flows
    private val _networkCondition = MutableStateFlow(NetworkCondition())
    val networkCondition: StateFlow<NetworkCondition> = _networkCondition.asStateFlow()

    private val _healthAlerts = MutableSharedFlow<HealthAlert>(
        replay = 0,
        extraBufferCapacity = 16
    )
    val healthAlerts: SharedFlow<HealthAlert> = _healthAlerts.asSharedFlow()

    private val _isMonitoring = MutableStateFlow(false)
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()

    // Monitoring state
    private var monitoringJob: Job? = null
    private val responseTimes = ConcurrentLinkedQueue<ResponseTimeSample>()
    private val maxSamples = 100

    // Statistics
    private val totalRequests = AtomicLong(0)
    private val successfulRequests = AtomicLong(0)
    private val failedRequests = AtomicLong(0)
    private val timeoutCount = AtomicLong(0)
    private val lastActivityTime = AtomicLong(System.currentTimeMillis())

    // Thresholds
    private var config = MonitorConfig()

    // ═══════════════════════════════════════════════════════════════════════
    // MONITORING CONTROL
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Starts network health monitoring.
     */
    fun startMonitoring(config: MonitorConfig = MonitorConfig()) {
        if (_isMonitoring.value) return

        this.config = config
        _isMonitoring.value = true

        monitoringJob = scope.launch {
            while (isActive && _isMonitoring.value) {
                analyzeNetworkHealth()
                delay(config.analysisInterval)
            }
        }
    }

    /**
     * Stops network health monitoring.
     */
    fun stopMonitoring() {
        _isMonitoring.value = false
        monitoringJob?.cancel()
        monitoringJob = null
    }

    /**
     * Resets all monitoring statistics.
     */
    fun reset() {
        responseTimes.clear()
        totalRequests.set(0)
        successfulRequests.set(0)
        failedRequests.set(0)
        timeoutCount.set(0)
        lastActivityTime.set(System.currentTimeMillis())
        _networkCondition.value = NetworkCondition()
    }

    // ═══════════════════════════════════════════════════════════════════════
    // DATA RECORDING
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Records a successful response with timing.
     */
    fun recordSuccess(responseTimeMs: Long) {
        totalRequests.incrementAndGet()
        successfulRequests.incrementAndGet()
        lastActivityTime.set(System.currentTimeMillis())

        addSample(ResponseTimeSample(
            timestamp = System.currentTimeMillis(),
            responseTimeMs = responseTimeMs,
            success = true
        ))
    }

    /**
     * Records a failed request.
     */
    fun recordFailure() {
        totalRequests.incrementAndGet()
        failedRequests.incrementAndGet()
        lastActivityTime.set(System.currentTimeMillis())

        addSample(ResponseTimeSample(
            timestamp = System.currentTimeMillis(),
            responseTimeMs = -1,
            success = false
        ))
    }

    /**
     * Records a timeout.
     */
    fun recordTimeout(timeoutMs: Long) {
        totalRequests.incrementAndGet()
        timeoutCount.incrementAndGet()
        lastActivityTime.set(System.currentTimeMillis())

        addSample(ResponseTimeSample(
            timestamp = System.currentTimeMillis(),
            responseTimeMs = timeoutMs,
            success = false,
            isTimeout = true
        ))
    }

    /**
     * Adds a sample to the queue, maintaining max size.
     */
    private fun addSample(sample: ResponseTimeSample) {
        responseTimes.add(sample)
        while (responseTimes.size > maxSamples) {
            responseTimes.poll()
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ANALYSIS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Analyzes current network health and updates condition.
     */
    private suspend fun analyzeNetworkHealth() {
        val samples = responseTimes.toList()
        if (samples.isEmpty()) return

        // Calculate metrics
        val successfulSamples = samples.filter { it.success }
        val recentSamples = samples.filter { 
            System.currentTimeMillis() - it.timestamp < config.recentWindowMs 
        }

        val avgResponseTime = if (successfulSamples.isNotEmpty()) {
            successfulSamples.map { it.responseTimeMs }.average().toLong()
        } else 0L

        val minResponseTime = successfulSamples.minOfOrNull { it.responseTimeMs } ?: Long.MAX_VALUE
        val maxResponseTime = successfulSamples.maxOfOrNull { it.responseTimeMs } ?: 0L
        val jitter = maxResponseTime - minResponseTime

        val recentFailures = recentSamples.count { !it.success }
        val recentTotal = recentSamples.size
        val packetLossRate = if (recentTotal > 0) {
            recentFailures.toFloat() / recentTotal
        } else 0f

        // Determine quality
        val quality = determineQuality(avgResponseTime, packetLossRate, jitter)

        // Calculate recommended timeout
        val recommendedTimeout = calculateRecommendedTimeout(avgResponseTime, quality)

        // Update condition
        val newCondition = NetworkCondition(
            quality = quality,
            averageResponseTime = avgResponseTime,
            minResponseTime = minResponseTime,
            maxResponseTime = maxResponseTime,
            packetLossRate = packetLossRate,
            jitter = jitter,
            recommendedTimeout = recommendedTimeout,
            lastUpdated = System.currentTimeMillis()
        )

        val previousCondition = _networkCondition.value
        _networkCondition.value = newCondition

        // Check for alerts
        checkForAlerts(previousCondition, newCondition)
    }

    /**
     * Determines network quality from metrics.
     */
    private fun determineQuality(
        avgResponseTime: Long,
        packetLossRate: Float,
        jitter: Long
    ): NetworkQuality {
        // High packet loss overrides response time assessment
        if (packetLossRate > 0.2f) return NetworkQuality.VERY_POOR
        if (packetLossRate > 0.1f) return NetworkQuality.POOR

        // High jitter indicates unstable connection
        if (jitter > 2000) return NetworkQuality.POOR
        if (jitter > 1000) return NetworkQuality.FAIR

        // Base quality on response time
        return NetworkQuality.fromResponseTime(avgResponseTime)
    }

    /**
     * Calculates recommended timeout based on network conditions.
     * Per requirement 2.5: adjust timeout values dynamically.
     */
    private fun calculateRecommendedTimeout(
        avgResponseTime: Long,
        quality: NetworkQuality
    ): Long {
        val baseMultiplier = when (quality) {
            NetworkQuality.EXCELLENT -> 2.0
            NetworkQuality.GOOD -> 2.5
            NetworkQuality.FAIR -> 3.0
            NetworkQuality.POOR -> 4.0
            NetworkQuality.VERY_POOR -> 5.0
            NetworkQuality.UNKNOWN -> 3.0
        }

        val calculated = (avgResponseTime * baseMultiplier).toLong()
        
        return calculated.coerceIn(
            config.minTimeout,
            config.maxTimeout
        )
    }

    /**
     * Checks for conditions that warrant alerts.
     */
    private suspend fun checkForAlerts(
        previous: NetworkCondition,
        current: NetworkCondition
    ) {
        // Quality degradation alert
        if (current.quality.ordinal > previous.quality.ordinal + 1) {
            _healthAlerts.emit(HealthAlert(
                type = AlertType.QUALITY_DEGRADED,
                message = "Network quality degraded from ${previous.quality} to ${current.quality}",
                severity = AlertSeverity.WARNING,
                suggestion = current.suggestedAction
            ))
        }

        // High packet loss alert
        if (current.packetLossRate > config.packetLossThreshold) {
            _healthAlerts.emit(HealthAlert(
                type = AlertType.HIGH_PACKET_LOSS,
                message = "High packet loss detected: ${(current.packetLossRate * 100).toInt()}%",
                severity = AlertSeverity.WARNING,
                suggestion = "Check WiFi signal strength and reduce interference"
            ))
        }

        // Very poor quality alert
        if (current.quality == NetworkQuality.VERY_POOR) {
            _healthAlerts.emit(HealthAlert(
                type = AlertType.POOR_CONNECTION,
                message = "Network quality is very poor",
                severity = AlertSeverity.ERROR,
                suggestion = "Consider moving closer to the scanner or checking WiFi connection"
            ))
        }

        // Connection idle alert
        val idleTime = System.currentTimeMillis() - lastActivityTime.get()
        if (idleTime > config.idleThreshold) {
            _healthAlerts.emit(HealthAlert(
                type = AlertType.CONNECTION_IDLE,
                message = "Connection has been idle for ${idleTime / 1000} seconds",
                severity = AlertSeverity.INFO,
                suggestion = "Connection may have been lost. Consider reconnecting."
            ))
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // TIMEOUT RECOMMENDATIONS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Gets the current recommended timeout value.
     */
    fun getRecommendedTimeout(): Long {
        return _networkCondition.value.recommendedTimeout
    }

    /**
     * Gets timeout recommendation with explanation.
     */
    fun getTimeoutRecommendation(): TimeoutRecommendation {
        val condition = _networkCondition.value
        
        return TimeoutRecommendation(
            recommendedTimeout = condition.recommendedTimeout,
            currentQuality = condition.quality,
            averageResponseTime = condition.averageResponseTime,
            explanation = buildTimeoutExplanation(condition)
        )
    }

    /**
     * Builds explanation for timeout recommendation.
     */
    private fun buildTimeoutExplanation(condition: NetworkCondition): String {
        return buildString {
            append("Based on ")
            append(responseTimes.size)
            append(" samples: ")
            
            when (condition.quality) {
                NetworkQuality.EXCELLENT -> append("Network is excellent. Using minimal timeout.")
                NetworkQuality.GOOD -> append("Network is good. Using standard timeout.")
                NetworkQuality.FAIR -> append("Network is fair. Using extended timeout.")
                NetworkQuality.POOR -> append("Network is poor. Using long timeout to avoid failures.")
                NetworkQuality.VERY_POOR -> append("Network is very poor. Using maximum timeout.")
                NetworkQuality.UNKNOWN -> append("Not enough data. Using default timeout.")
            }
            
            if (condition.packetLossRate > 0.05f) {
                append(" High packet loss (${(condition.packetLossRate * 100).toInt()}%) detected.")
            }
            
            if (condition.jitter > 500) {
                append(" High jitter (${condition.jitter}ms) detected.")
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // STATISTICS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Gets current monitoring statistics.
     */
    fun getStatistics(): MonitorStatistics {
        val total = totalRequests.get()
        val successful = successfulRequests.get()
        val failed = failedRequests.get()
        val timeouts = timeoutCount.get()

        return MonitorStatistics(
            totalRequests = total,
            successfulRequests = successful,
            failedRequests = failed,
            timeoutCount = timeouts,
            successRate = if (total > 0) successful.toFloat() / total else 0f,
            currentCondition = _networkCondition.value,
            lastActivityTime = lastActivityTime.get(),
            monitoringDuration = System.currentTimeMillis() - (responseTimes.firstOrNull()?.timestamp ?: System.currentTimeMillis())
        )
    }

    // ═══════════════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Releases all resources.
     */
    fun release() {
        stopMonitoring()
        reset()
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// DATA CLASSES
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Configuration for network health monitoring.
 */
data class MonitorConfig(
    val analysisInterval: Long = 1000L,
    val recentWindowMs: Long = 30_000L,
    val minTimeout: Long = 1_000L,
    val maxTimeout: Long = 30_000L,
    val packetLossThreshold: Float = 0.1f,
    val idleThreshold: Long = 30_000L
)

/**
 * A single response time sample.
 */
data class ResponseTimeSample(
    val timestamp: Long,
    val responseTimeMs: Long,
    val success: Boolean,
    val isTimeout: Boolean = false
)

/**
 * Health alert from the monitor.
 */
data class HealthAlert(
    val type: AlertType,
    val message: String,
    val severity: AlertSeverity,
    val suggestion: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Types of health alerts.
 */
enum class AlertType {
    QUALITY_DEGRADED,
    HIGH_PACKET_LOSS,
    POOR_CONNECTION,
    CONNECTION_IDLE,
    CONNECTION_LOST,
    RECOVERY_SUGGESTED
}

/**
 * Alert severity levels.
 */
enum class AlertSeverity {
    INFO,
    WARNING,
    ERROR,
    CRITICAL
}

/**
 * Timeout recommendation with explanation.
 */
data class TimeoutRecommendation(
    val recommendedTimeout: Long,
    val currentQuality: NetworkQuality,
    val averageResponseTime: Long,
    val explanation: String
)

/**
 * Monitoring statistics.
 */
data class MonitorStatistics(
    val totalRequests: Long,
    val successfulRequests: Long,
    val failedRequests: Long,
    val timeoutCount: Long,
    val successRate: Float,
    val currentCondition: NetworkCondition,
    val lastActivityTime: Long,
    val monitoringDuration: Long
) {
    /**
     * Time since last activity in milliseconds.
     */
    val idleTime: Long
        get() = System.currentTimeMillis() - lastActivityTime

    /**
     * Whether the connection appears idle.
     */
    val isIdle: Boolean
        get() = idleTime > 30_000

    /**
     * Formatted success rate as percentage.
     */
    val successRatePercent: String
        get() = "${(successRate * 100).toInt()}%"
}
