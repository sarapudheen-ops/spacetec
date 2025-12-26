/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 *
 * This file is part of the SpaceTec professional automotive diagnostic system.
 */

package com.spacetec.obd.scanner.core.state

import com.spacetec.core.common.exceptions.ConnectionException
import com.spacetec.core.common.result.Result
import com.spacetec.core.domain.models.scanner.ScannerConnectionType
import com.spacetec.obd.scanner.core.ConnectionState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Synchronizes state across different connection types and manages consistency.
 * 
 * Ensures that:
 * - State changes are propagated consistently
 * - Conflicts between connections are resolved
 * - Resource constraints are enforced
 * - Cross-connection coordination is maintained
 */
@Singleton
class StateSynchronizer @Inject constructor(
    private val stateManager: ConnectionStateManager,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    
    // ═══════════════════════════════════════════════════════════════════════
    // SYNCHRONIZATION EVENTS
    // ═══════════════════════════════════════════════════════════════════════
    
    private val _synchronizationEvents = MutableSharedFlow<SynchronizationEvent>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val synchronizationEvents: SharedFlow<SynchronizationEvent> = _synchronizationEvents.asSharedFlow()
    
    // ═══════════════════════════════════════════════════════════════════════
    // INTERNAL STATE
    // ═══════════════════════════════════════════════════════════════════════
    
    private val scope = CoroutineScope(dispatcher + SupervisorJob())
    private val syncMutex = Mutex()
    private val released = AtomicBoolean(false)
    
    // Connection priority rules
    private val connectionPriorities = mapOf(
        ScannerConnectionType.J2534 to 1,          // Highest priority
        ScannerConnectionType.USB to 2,
        ScannerConnectionType.WIFI to 3,
        ScannerConnectionType.BLUETOOTH_CLASSIC to 4,
        ScannerConnectionType.BLUETOOTH_LE to 5    // Lowest priority
    )
    
    // Active synchronization rules
    private val activeSyncRules = ConcurrentHashMap<String, SyncRule>()
    
    init {
        initializeSynchronization()
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // SYNCHRONIZATION INITIALIZATION
    // ═══════════════════════════════════════════════════════════════════════
    
    private fun initializeSynchronization() {
        // Monitor state changes for synchronization needs
        stateManager.stateEvents
            .onEach { event ->
                handleStateEvent(event)
            }
            .launchIn(scope)
        
        // Monitor global state for consistency checks
        stateManager.globalState
            .distinctUntilChanged()
            .onEach { globalState ->
                performConsistencyCheck(globalState)
            }
            .launchIn(scope)
        
        // Monitor connection states for conflict resolution
        stateManager.connectionStates
            .distinctUntilChanged()
            .onEach { connectionStates ->
                resolveConnectionConflicts(connectionStates)
            }
            .launchIn(scope)
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // EVENT HANDLING
    // ═══════════════════════════════════════════════════════════════════════
    
    private suspend fun handleStateEvent(event: StateEvent) {
        when (event) {
            is StateEvent.ConnectionRegistered -> {
                handleConnectionRegistered(event.connectionId, event.scanner)
            }
            
            is StateEvent.StateChanged -> {
                handleStateChanged(event.connectionId, event.fromState, event.toState)
            }
            
            is StateEvent.ConnectionConflict -> {
                handleConnectionConflict(event.scannerId, event.connectionIds)
            }
            
            is StateEvent.ResourceConstraint -> {
                handleResourceConstraint(event.message)
            }
            
            is StateEvent.QualityDegraded -> {
                handleQualityDegraded(event.connectionId, event.quality)
            }
            
            else -> {
                // Other events don't require synchronization action
            }
        }
    }
    
    private suspend fun handleConnectionRegistered(connectionId: String, scanner: com.spacetec.core.domain.models.scanner.Scanner) {
        syncMutex.withLock {
            // Check for existing connections to the same scanner
            val existingConnections = stateManager.getConnectionsForScanner(scanner.id)
            
            if (existingConnections.size > 1) {
                // Multiple connections to same scanner - apply priority rules
                val sortedConnections = existingConnections.sortedBy { 
                    connectionPriorities[it.connectionType] ?: Int.MAX_VALUE 
                }
                
                // Keep highest priority connection, disconnect others
                val primaryConnection = sortedConnections.first()
                val secondaryConnections = sortedConnections.drop(1)
                
                for (secondary in secondaryConnections) {
                    if (secondary.state.isConnected) {
                        _synchronizationEvents.emit(
                            SynchronizationEvent.ConnectionPriorityResolution(
                                primaryConnectionId = primaryConnection.connectionId,
                                secondaryConnectionId = secondary.connectionId,
                                reason = "Scanner ${scanner.id} has multiple connections"
                            )
                        )
                    }
                }
            }
        }
    }
    
    private suspend fun handleStateChanged(
        connectionId: String,
        fromState: ConnectionState,
        toState: ConnectionState
    ) {
        // Check if state change affects other connections
        val stateInfo = stateManager.getConnectionState(connectionId) ?: return
        
        when (toState) {
            is ConnectionState.Connected -> {
                // Connection established - check for resource conflicts
                checkResourceAvailability(connectionId, stateInfo)
            }
            
            is ConnectionState.Error -> {
                // Connection error - check if backup connection should be activated
                checkBackupConnectionActivation(connectionId, stateInfo)
            }
            
            is ConnectionState.Disconnected -> {
                // Connection lost - clean up sync rules
                cleanupSyncRules(connectionId)
            }
            
            else -> {
                // Other states don't require special handling
            }
        }
    }
    
    private suspend fun handleConnectionConflict(scannerId: String, connectionIds: List<String>) {
        syncMutex.withLock {
            val connections = connectionIds.mapNotNull { stateManager.getConnectionState(it) }
            
            if (connections.size > 1) {
                // Resolve conflict using priority rules
                val sortedConnections = connections.sortedBy { 
                    connectionPriorities[it.connectionType] ?: Int.MAX_VALUE 
                }
                
                val primaryConnection = sortedConnections.first()
                val conflictingConnections = sortedConnections.drop(1)
                
                _synchronizationEvents.emit(
                    SynchronizationEvent.ConflictResolution(
                        scannerId = scannerId,
                        primaryConnectionId = primaryConnection.connectionId,
                        conflictingConnectionIds = conflictingConnections.map { it.connectionId },
                        resolutionStrategy = "Priority-based selection"
                    )
                )
                
                // Create sync rule to prevent future conflicts
                val syncRule = SyncRule(
                    id = "conflict_prevention_$scannerId",
                    scannerId = scannerId,
                    primaryConnectionType = primaryConnection.connectionType,
                    action = SyncAction.PREVENT_SECONDARY_CONNECTIONS
                )
                
                activeSyncRules[syncRule.id] = syncRule
            }
        }
    }
    
    private suspend fun handleResourceConstraint(message: String) {
        // Implement resource constraint handling
        val globalState = stateManager.globalState.value
        
        if (globalState.activeConnections > MAX_CONCURRENT_CONNECTIONS) {
            // Find lowest priority connections to disconnect
            val allConnections = stateManager.connectionStates.value.values
                .filter { it.state.isConnected }
                .sortedByDescending { connectionPriorities[it.connectionType] ?: 0 }
            
            val connectionsToDisconnect = allConnections.drop(MAX_CONCURRENT_CONNECTIONS)
            
            for (connection in connectionsToDisconnect) {
                _synchronizationEvents.emit(
                    SynchronizationEvent.ResourceConstraintResolution(
                        connectionId = connection.connectionId,
                        reason = "Exceeded maximum concurrent connections",
                        action = "Disconnect lowest priority connection"
                    )
                )
            }
        }
    }
    
    private suspend fun handleQualityDegraded(connectionId: String, quality: ConnectionQuality) {
        val stateInfo = stateManager.getConnectionState(connectionId) ?: return
        
        // Check if there's a better alternative connection available
        val alternativeConnections = stateManager.getConnectionsForScanner(stateInfo.scanner.id)
            .filter { it.connectionId != connectionId }
            .filter { it.connectionQuality?.qualityScore ?: 0 > quality.qualityScore }
        
        if (alternativeConnections.isNotEmpty()) {
            val bestAlternative = alternativeConnections.maxByOrNull { 
                it.connectionQuality?.qualityScore ?: 0 
            }
            
            if (bestAlternative != null) {
                _synchronizationEvents.emit(
                    SynchronizationEvent.QualityBasedSwitching(
                        fromConnectionId = connectionId,
                        toConnectionId = bestAlternative.connectionId,
                        qualityImprovement = (bestAlternative.connectionQuality?.qualityScore ?: 0) - quality.qualityScore
                    )
                )
            }
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // CONSISTENCY CHECKS
    // ═══════════════════════════════════════════════════════════════════════
    
    private suspend fun performConsistencyCheck(globalState: GlobalConnectionState) {
        // Check for inconsistencies in global state
        val connectionStates = stateManager.connectionStates.value
        
        // Verify connection counts
        val actualActiveConnections = connectionStates.values.count { it.state.isConnected }
        if (actualActiveConnections != globalState.activeConnections) {
            _synchronizationEvents.emit(
                SynchronizationEvent.InconsistencyDetected(
                    type = "Connection count mismatch",
                    expected = globalState.activeConnections,
                    actual = actualActiveConnections
                )
            )
            
            // Trigger state synchronization
            stateManager.connectionStates.value.keys.forEach { connectionId ->
                stateManager.synchronizeConnectionState(connectionId)
            }
        }
        
        // Check connection type distribution
        val actualByType = connectionStates.values.groupBy { it.connectionType }.mapValues { it.value.size }
        val expectedByType = globalState.connectionsByType
        
        if (actualByType != expectedByType) {
            _synchronizationEvents.emit(
                SynchronizationEvent.InconsistencyDetected(
                    type = "Connection type distribution mismatch",
                    expected = expectedByType.toString(),
                    actual = actualByType.toString()
                )
            )
        }
    }
    
    private suspend fun resolveConnectionConflicts(connectionStates: Map<String, ConnectionStateInfo>) {
        // Group connections by scanner
        val connectionsByScanner = connectionStates.values.groupBy { it.scanner.id }
        
        for ((scannerId, connections) in connectionsByScanner) {
            val activeConnections = connections.filter { it.state.isConnected }
            
            if (activeConnections.size > 1) {
                // Multiple active connections to same scanner
                val primaryConnection = selectPrimaryConnection(activeConnections)
                val secondaryConnections = activeConnections.filter { it != primaryConnection }
                
                _synchronizationEvents.emit(
                    SynchronizationEvent.ConflictResolution(
                        scannerId = scannerId,
                        primaryConnectionId = primaryConnection.connectionId,
                        conflictingConnectionIds = secondaryConnections.map { it.connectionId },
                        resolutionStrategy = "Automatic priority-based resolution"
                    )
                )
            }
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════════
    
    private fun selectPrimaryConnection(connections: List<ConnectionStateInfo>): ConnectionStateInfo {
        return connections.minByOrNull { connectionPriorities[it.connectionType] ?: Int.MAX_VALUE }
            ?: connections.first()
    }
    
    private suspend fun checkResourceAvailability(connectionId: String, stateInfo: ConnectionStateInfo) {
        val globalState = stateManager.globalState.value
        
        // Check if we're approaching resource limits
        if (globalState.activeConnections >= MAX_CONCURRENT_CONNECTIONS * 0.8) {
            _synchronizationEvents.emit(
                SynchronizationEvent.ResourceWarning(
                    connectionId = connectionId,
                    currentUsage = globalState.activeConnections,
                    maxCapacity = MAX_CONCURRENT_CONNECTIONS
                )
            )
        }
    }
    
    private suspend fun checkBackupConnectionActivation(connectionId: String, stateInfo: ConnectionStateInfo) {
        // Look for backup connections for the same scanner
        val backupConnections = stateManager.getConnectionsForScanner(stateInfo.scanner.id)
            .filter { it.connectionId != connectionId }
            .filter { it.state.isDisconnected }
        
        if (backupConnections.isNotEmpty()) {
            val bestBackup = selectPrimaryConnection(backupConnections)
            
            _synchronizationEvents.emit(
                SynchronizationEvent.BackupActivation(
                    failedConnectionId = connectionId,
                    backupConnectionId = bestBackup.connectionId,
                    reason = "Primary connection failed"
                )
            )
        }
    }
    
    private fun cleanupSyncRules(connectionId: String) {
        // Remove sync rules related to this connection
        val rulesToRemove = activeSyncRules.values.filter { rule ->
            rule.id.contains(connectionId)
        }
        
        rulesToRemove.forEach { rule ->
            activeSyncRules.remove(rule.id)
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Forces synchronization of all connection states.
     */
    suspend fun synchronizeAllStates(): Result<Unit> = syncMutex.withLock {
        try {
            val connectionIds = stateManager.connectionStates.value.keys
            
            for (connectionId in connectionIds) {
                stateManager.synchronizeConnectionState(connectionId)
            }
            
            _synchronizationEvents.emit(SynchronizationEvent.ForcedSynchronization(connectionIds.size))
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(ConnectionException("Failed to synchronize states: ${e.message}", e))
        }
    }
    
    /**
     * Adds a custom synchronization rule.
     */
    suspend fun addSyncRule(rule: SyncRule): Result<Unit> = syncMutex.withLock {
        try {
            activeSyncRules[rule.id] = rule
            _synchronizationEvents.emit(SynchronizationEvent.SyncRuleAdded(rule))
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(ConnectionException("Failed to add sync rule: ${e.message}", e))
        }
    }
    
    /**
     * Removes a synchronization rule.
     */
    suspend fun removeSyncRule(ruleId: String): Result<Unit> = syncMutex.withLock {
        try {
            val removedRule = activeSyncRules.remove(ruleId)
            if (removedRule != null) {
                _synchronizationEvents.emit(SynchronizationEvent.SyncRuleRemoved(removedRule))
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(ConnectionException("Failed to remove sync rule: ${e.message}", e))
        }
    }
    
    /**
     * Gets all active synchronization rules.
     */
    fun getActiveSyncRules(): List<SyncRule> {
        return activeSyncRules.values.toList()
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════════
    
    fun release() {
        if (released.getAndSet(true)) return
        
        scope.cancel()
        activeSyncRules.clear()
    }
    
    companion object {
        private const val MAX_CONCURRENT_CONNECTIONS = 5
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// SYNCHRONIZATION MODELS
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Synchronization rule for managing connection behavior.
 */
data class SyncRule(
    val id: String,
    val scannerId: String,
    val primaryConnectionType: ScannerConnectionType,
    val action: SyncAction,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Actions that can be taken during synchronization.
 */
enum class SyncAction {
    PREVENT_SECONDARY_CONNECTIONS,
    SWITCH_ON_QUALITY_DEGRADATION,
    LOAD_BALANCE_CONNECTIONS,
    ENFORCE_PRIORITY_ORDER
}

/**
 * Events emitted during synchronization.
 */
sealed class SynchronizationEvent {
    data class ConnectionPriorityResolution(
        val primaryConnectionId: String,
        val secondaryConnectionId: String,
        val reason: String
    ) : SynchronizationEvent()
    
    data class ConflictResolution(
        val scannerId: String,
        val primaryConnectionId: String,
        val conflictingConnectionIds: List<String>,
        val resolutionStrategy: String
    ) : SynchronizationEvent()
    
    data class ResourceConstraintResolution(
        val connectionId: String,
        val reason: String,
        val action: String
    ) : SynchronizationEvent()
    
    data class QualityBasedSwitching(
        val fromConnectionId: String,
        val toConnectionId: String,
        val qualityImprovement: Int
    ) : SynchronizationEvent()
    
    data class InconsistencyDetected(
        val type: String,
        val expected: Any,
        val actual: Any
    ) : SynchronizationEvent()
    
    data class ResourceWarning(
        val connectionId: String,
        val currentUsage: Int,
        val maxCapacity: Int
    ) : SynchronizationEvent()
    
    data class BackupActivation(
        val failedConnectionId: String,
        val backupConnectionId: String,
        val reason: String
    ) : SynchronizationEvent()
    
    data class ForcedSynchronization(val connectionCount: Int) : SynchronizationEvent()
    
    data class SyncRuleAdded(val rule: SyncRule) : SynchronizationEvent()
    
    data class SyncRuleRemoved(val rule: SyncRule) : SynchronizationEvent()
}