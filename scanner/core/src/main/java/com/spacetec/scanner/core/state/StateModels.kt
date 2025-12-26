/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 *
 * This file is part of the SpaceTec professional automotive diagnostic system.
 */

package com.spacetec.obd.scanner.core.state

import com.spacetec.core.domain.models.scanner.Scanner
import com.spacetec.core.domain.models.scanner.ScannerConnectionType
import com.spacetec.obd.scanner.core.ConnectionState

/**
 * Information about a connection's state.
 */
data class ConnectionStateInfo(
    val connectionId: String,
    val scanner: Scanner,
    val connectionType: ScannerConnectionType,
    val state: ConnectionState,
    val registeredAt: Long,
    val lastStateChange: Long,
    val stateHistory: List<StateTransition> = emptyList(),
    val connectionQuality: ConnectionQuality? = null
) {
    /**
     * Time since last state change in milliseconds.
     */
    val timeSinceLastChange: Long
        get() = System.currentTimeMillis() - lastStateChange
    
    /**
     * Total time since registration in milliseconds.
     */
    val totalTime: Long
        get() = System.currentTimeMillis() - registeredAt
    
    /**
     * Number of state transitions.
     */
    val transitionCount: Int
        get() = stateHistory.size
    
    /**
     * Whether the connection is stable (no recent errors).
     */
    val isStable: Boolean
        get() {
            val recentErrors = stateHistory
                .filter { it.toState is ConnectionState.Error }
                .filter { System.currentTimeMillis() - it.timestamp < 60_000 }
            return recentErrors.isEmpty()
        }
}

/**
 * Represents a state transition.
 */
data class StateTransition(
    val fromState: ConnectionState,
    val toState: ConnectionState,
    val timestamp: Long
) {
    /**
     * Duration in the previous state.
     */
    val duration: Long
        get() = timestamp - (fromState as? ConnectionState.Connected)?.info?.connectedAt ?: timestamp
}

/**
 * Connection quality metrics.
 */
data class ConnectionQuality(
    val signalStrength: Int? = null,
    val responseTime: Long,
    val errorRate: Float,
    val throughput: Float,
    val uptime: Long,
    val lastUpdated: Long
) {
    /**
     * Overall quality score (0-100).
     */
    val qualityScore: Int
        get() {
            var score = 100
            
            // Deduct for poor signal strength
            signalStrength?.let { rssi ->
                when {
                    rssi < -90 -> score -= 30
                    rssi < -80 -> score -= 20
                    rssi < -70 -> score -= 10
                }
            }
            
            // Deduct for slow response time
            when {
                responseTime > 5000 -> score -= 30
                responseTime > 2000 -> score -= 20
                responseTime > 1000 -> score -= 10
            }
            
            // Deduct for high error rate
            when {
                errorRate > 20 -> score -= 40
                errorRate > 10 -> score -= 25
                errorRate > 5 -> score -= 15
            }
            
            return score.coerceIn(0, 100)
        }
    
    /**
     * Whether the connection is considered healthy.
     */
    val isHealthy: Boolean
        get() = qualityScore >= 60
    
    /**
     * Quality level.
     */
    val level: QualityLevel
        get() = when {
            qualityScore >= 80 -> QualityLevel.EXCELLENT
            qualityScore >= 60 -> QualityLevel.GOOD
            qualityScore >= 40 -> QualityLevel.FAIR
            else -> QualityLevel.POOR
        }
}

/**
 * Quality level enumeration.
 */
enum class QualityLevel {
    EXCELLENT,
    GOOD,
    FAIR,
    POOR
}

/**
 * Global connection state across all connections.
 */
data class GlobalConnectionState(
    val totalConnections: Int = 0,
    val activeConnections: Int = 0,
    val connectingConnections: Int = 0,
    val errorConnections: Int = 0,
    val connectionsByType: Map<ScannerConnectionType, Int> = emptyMap(),
    val lastUpdate: Long = System.currentTimeMillis(),
    val overallHealth: ConnectionHealth = ConnectionHealth.UNKNOWN
) {
    /**
     * Whether any connections are active.
     */
    val hasActiveConnections: Boolean
        get() = activeConnections > 0
    
    /**
     * Connection success rate.
     */
    val successRate: Float
        get() = if (totalConnections > 0) {
            (activeConnections.toFloat() / totalConnections) * 100
        } else 0f
}

/**
 * Overall connection health.
 */
enum class ConnectionHealth {
    UNKNOWN,
    POOR,
    FAIR,
    GOOD,
    EXCELLENT
}

/**
 * State events emitted by the state manager.
 */
sealed class StateEvent {
    /**
     * Connection was registered.
     */
    data class ConnectionRegistered(val connectionId: String, val scanner: Scanner) : StateEvent()
    
    /**
     * Connection was unregistered.
     */
    data class ConnectionUnregistered(val connectionId: String, val scanner: Scanner) : StateEvent()
    
    /**
     * Connection state changed.
     */
    data class StateChanged(
        val connectionId: String,
        val fromState: ConnectionState,
        val toState: ConnectionState
    ) : StateEvent()
    
    /**
     * Connection established successfully.
     */
    data class ConnectionEstablished(val connectionId: String, val scanner: Scanner) : StateEvent()
    
    /**
     * Connection error occurred.
     */
    data class ConnectionError(val connectionId: String, val exception: Throwable) : StateEvent()
    
    /**
     * Connection lost.
     */
    data class ConnectionLost(val connectionId: String, val scanner: Scanner) : StateEvent()
    
    /**
     * Reconnection attempt.
     */
    data class ReconnectionAttempt(val connectionId: String, val attempt: Int) : StateEvent()
    
    /**
     * Connection quality degraded.
     */
    data class QualityDegraded(val connectionId: String, val quality: ConnectionQuality) : StateEvent()
    
    /**
     * Recovery attempt initiated.
     */
    data class RecoveryAttempt(val connectionId: String) : StateEvent()
    
    /**
     * Recovery succeeded.
     */
    data class RecoverySuccess(val connectionId: String) : StateEvent()
    
    /**
     * Recovery failed.
     */
    data class RecoveryFailed(val connectionId: String, val exception: Throwable) : StateEvent()
    
    /**
     * Global state changed.
     */
    data class GlobalStateChanged(val globalState: GlobalConnectionState) : StateEvent()
    
    /**
     * State recovered from persistence.
     */
    data class StateRecovered(val connectionCount: Int) : StateEvent()
    
    /**
     * Persistence error occurred.
     */
    data class PersistenceError(val message: String, val exception: Throwable) : StateEvent()
    
    /**
     * Connection conflict detected.
     */
    data class ConnectionConflict(val scannerId: String, val connectionIds: List<String>) : StateEvent()
    
    /**
     * Resource constraint detected.
     */
    data class ResourceConstraint(val message: String) : StateEvent()
    
    /**
     * All state cleared.
     */
    object StateCleared : StateEvent()
}
