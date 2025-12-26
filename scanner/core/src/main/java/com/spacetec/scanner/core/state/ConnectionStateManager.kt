/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 *
 * This file is part of the SpaceTec professional automotive diagnostic system.
 */

package com.spacetec.obd.scanner.core.state

import com.spacetec.core.common.exceptions.ConnectionException
import com.spacetec.core.common.result.Result
import com.spacetec.core.domain.models.scanner.Scanner
import com.spacetec.core.domain.models.scanner.ScannerConnectionType
import com.spacetec.core.domain.models.scanner.ScannerState
import com.spacetec.obd.scanner.core.ConnectionState
import com.spacetec.obd.scanner.core.ScannerConnection
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
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
 * Centralized state manager for scanner connections.
 * 
 * Provides unified state management across all connection types with:
 * - State synchronization and consistency
 * - State persistence and recovery
 * - Cross-connection state coordination
 * - Performance monitoring and optimization
 * 
 * Requirements: 6.3, 7.2
 */
@Singleton
class ConnectionStateManager @Inject constructor(
    private val stateRepository: ConnectionStateRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    
    // ═══════════════════════════════════════════════════════════════════════
    // STATE FLOWS
    // ═══════════════════════════════════════════════════════════════════════
    
    private val _globalState = MutableStateFlow(GlobalConnectionState())
    val globalState: StateFlow<GlobalConnectionState> = _globalState.asStateFlow()
    
    private val _connectionStates = MutableStateFlow<Map<String, ConnectionStateInfo>>(emptyMap())
    val connectionStates: StateFlow<Map<String, ConnectionStateInfo>> = _connectionStates.asStateFlow()
    
    private val _stateEvents = MutableSharedFlow<StateEvent>(
        replay = 0,
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val stateEvents: SharedFlow<StateEvent> = _stateEvents.asSharedFlow()
    
    // ═══════════════════════════════════════════════════════════════════════
    // INTERNAL STATE
    // ═══════════════════════════════════════════════════════════════════════
    
    private val scope = CoroutineScope(dispatcher + SupervisorJob())
    private val stateMutex = Mutex()
    private val connectionRegistry = ConcurrentHashMap<String, ScannerConnection>()
    private val stateSubscriptions = ConcurrentHashMap<String, kotlinx.coroutines.Job>()
    private val released = AtomicBoolean(false)
    
    init {
        // Initialize state synchronization
        initializeStateSynchronization()
        
        // Load persisted state
        scope.launch {
            loadPersistedState()
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // CONNECTION REGISTRATION
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Registers a connection for state management.
     */
    suspend fun registerConnection(
        connectionId: String,
        connection: ScannerConnection,
        scanner: Scanner
    ) = stateMutex.withLock {
        if (released.get()) return@withLock
        
        // Store connection reference
        connectionRegistry[connectionId] = connection
        
        // Create initial state info
        val stateInfo = ConnectionStateInfo(
            connectionId = connectionId,
            scanner = scanner,
            connectionType = scanner.connectionType,
            state = connection.connectionState.value,
            registeredAt = System.currentTimeMillis(),
            lastStateChange = System.currentTimeMillis()
        )
        
        // Update state map
        val currentStates = _connectionStates.value.toMutableMap()
        currentStates[connectionId] = stateInfo
        _connectionStates.value = currentStates
        
        // Subscribe to connection state changes
        subscribeToConnectionState(connectionId, connection)
        
        // Update global state
        updateGlobalState()
        
        // Emit registration event
        _stateEvents.emit(StateEvent.ConnectionRegistered(connectionId, scanner))
        
        // Persist state
        persistConnectionState(stateInfo)
    }
    
    /**
     * Unregisters a connection from state management.
     */
    suspend fun unregisterConnection(connectionId: String) = stateMutex.withLock {
        // Remove from registry
        connectionRegistry.remove(connectionId)
        
        // Cancel state subscription
        stateSubscriptions.remove(connectionId)?.cancel()
        
        // Remove from state map
        val currentStates = _connectionStates.value.toMutableMap()
        val removedState = currentStates.remove(connectionId)
        _connectionStates.value = currentStates
        
        // Update global state
        updateGlobalState()
        
        // Emit unregistration event
        if (removedState != null) {
            _stateEvents.emit(StateEvent.ConnectionUnregistered(connectionId, removedState.scanner))
            
            // Remove persisted state
            stateRepository.removeConnectionState(connectionId)
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // STATE SYNCHRONIZATION
    // ═══════════════════════════════════════════════════════════════════════
    
    private fun subscribeToConnectionState(connectionId: String, connection: ScannerConnection) {
        val job = connection.connectionState
            .distinctUntilChanged()
            .onEach { newState ->
                handleConnectionStateChange(connectionId, newState)
            }
            .launchIn(scope)
        
        stateSubscriptions[connectionId] = job
    }
    
    private suspend fun handleConnectionStateChange(connectionId: String, newState: ConnectionState) {
        stateMutex.withLock {
            val currentStates = _connectionStates.value.toMutableMap()
            val currentInfo = currentStates[connectionId] ?: return@withLock
            
            // Update state info
            val updatedInfo = currentInfo.copy(
                state = newState,
                lastStateChange = System.currentTimeMillis(),
                stateHistory = currentInfo.stateHistory + StateTransition(
                    fromState = currentInfo.state,
                    toState = newState,
                    timestamp = System.currentTimeMillis()
                )
            )
            
            currentStates[connectionId] = updatedInfo
            _connectionStates.value = currentStates
            
            // Update global state
            updateGlobalState()
            
            // Emit state change event
            _stateEvents.emit(StateEvent.StateChanged(connectionId, currentInfo.state, newState))
            
            // Persist updated state
            persistConnectionState(updatedInfo)
            
            // Handle state-specific logic
            handleStateSpecificLogic(connectionId, newState, updatedInfo)
        }
    }
    
    private suspend fun handleStateSpecificLogic(
        connectionId: String,
        newState: ConnectionState,
        stateInfo: ConnectionStateInfo
    ) {
        when (newState) {
            is ConnectionState.Connected -> {
                // Connection established successfully
                _stateEvents.emit(StateEvent.ConnectionEstablished(connectionId, stateInfo.scanner))
                
                // Update connection quality metrics
                updateConnectionQuality(connectionId, newState.info)
            }
            
            is ConnectionState.Error -> {
                // Connection error occurred
                _stateEvents.emit(StateEvent.ConnectionError(connectionId, newState.exception))
                
                // Check if recovery is needed
                if (newState.isRecoverable && shouldAttemptRecovery(stateInfo)) {
                    scheduleRecovery(connectionId)
                }
            }
            
            is ConnectionState.Disconnected -> {
                // Connection lost
                _stateEvents.emit(StateEvent.ConnectionLost(connectionId, stateInfo.scanner))
                
                // Clean up connection-specific state
                cleanupConnectionState(connectionId)
            }
            
            is ConnectionState.Reconnecting -> {
                // Reconnection in progress
                _stateEvents.emit(StateEvent.ReconnectionAttempt(connectionId, newState.attempt))
            }
            
            else -> {
                // Other state changes (connecting, etc.)
            }
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // GLOBAL STATE MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════════
    
    private suspend fun updateGlobalState() {
        val connections = _connectionStates.value.values
        
        val newGlobalState = GlobalConnectionState(
            totalConnections = connections.size,
            activeConnections = connections.count { it.state.isConnected },
            connectingConnections = connections.count { it.state.isConnecting },
            errorConnections = connections.count { it.state.isError },
            connectionsByType = connections.groupBy { it.connectionType }
                .mapValues { (_, conns) -> conns.size },
            lastUpdate = System.currentTimeMillis(),
            overallHealth = calculateOverallHealth(connections)
        )
        
        _globalState.value = newGlobalState
        
        // Persist global state
        stateRepository.saveGlobalState(newGlobalState)
    }
    
    private fun calculateOverallHealth(connections: Collection<ConnectionStateInfo>): ConnectionHealth {
        if (connections.isEmpty()) return ConnectionHealth.UNKNOWN
        
        val totalConnections = connections.size
        val healthyConnections = connections.count { 
            it.state.isConnected && it.connectionQuality?.isHealthy == true 
        }
        val errorConnections = connections.count { it.state.isError }
        
        return when {
            errorConnections > totalConnections / 2 -> ConnectionHealth.POOR
            healthyConnections > totalConnections * 0.8 -> ConnectionHealth.EXCELLENT
            healthyConnections > totalConnections * 0.6 -> ConnectionHealth.GOOD
            else -> ConnectionHealth.FAIR
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // STATE PERSISTENCE
    // ═══════════════════════════════════════════════════════════════════════
    
    private suspend fun persistConnectionState(stateInfo: ConnectionStateInfo) {
        try {
            stateRepository.saveConnectionState(stateInfo)
        } catch (e: Exception) {
            // Log error but don't fail the operation
            _stateEvents.emit(StateEvent.PersistenceError("Failed to persist state for ${stateInfo.connectionId}", e))
        }
    }
    
    private suspend fun loadPersistedState() {
        try {
            // Load global state
            val globalState = stateRepository.loadGlobalState()
            if (globalState != null) {
                _globalState.value = globalState
            }
            
            // Load connection states
            val connectionStates = stateRepository.loadAllConnectionStates()
            if (connectionStates.isNotEmpty()) {
                _connectionStates.value = connectionStates.associateBy { it.connectionId }
                
                // Emit recovery event
                _stateEvents.emit(StateEvent.StateRecovered(connectionStates.size))
            }
        } catch (e: Exception) {
            _stateEvents.emit(StateEvent.PersistenceError("Failed to load persisted state", e))
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // CONNECTION QUALITY MONITORING
    // ═══════════════════════════════════════════════════════════════════════
    
    private suspend fun updateConnectionQuality(connectionId: String, connectionInfo: com.spacetec.scanner.core.ConnectionInfo) {
        val connection = connectionRegistry[connectionId] ?: return
        val stats = connection.getStatistics()
        
        val quality = ConnectionQuality(
            signalStrength = connectionInfo.signalStrength,
            responseTime = stats.averageResponseTime,
            errorRate = stats.errorRate,
            throughput = stats.throughputBps,
            uptime = stats.connectionUptime,
            lastUpdated = System.currentTimeMillis()
        )
        
        // Update connection state with quality info
        stateMutex.withLock {
            val currentStates = _connectionStates.value.toMutableMap()
            val currentInfo = currentStates[connectionId] ?: return@withLock
            
            val updatedInfo = currentInfo.copy(connectionQuality = quality)
            currentStates[connectionId] = updatedInfo
            _connectionStates.value = currentStates
            
            // Persist updated state
            persistConnectionState(updatedInfo)
        }
        
        // Check if quality degradation requires action
        if (!quality.isHealthy) {
            _stateEvents.emit(StateEvent.QualityDegraded(connectionId, quality))
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // RECOVERY MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════════
    
    private fun shouldAttemptRecovery(stateInfo: ConnectionStateInfo): Boolean {
        val recentErrors = stateInfo.stateHistory
            .filter { it.toState is ConnectionState.Error }
            .filter { System.currentTimeMillis() - it.timestamp < 60_000 } // Last minute
        
        return recentErrors.size < 3 // Don't attempt recovery if too many recent errors
    }
    
    private fun scheduleRecovery(connectionId: String) {
        scope.launch {
            try {
                // Wait before attempting recovery
                kotlinx.coroutines.delay(5000)
                
                val connection = connectionRegistry[connectionId]
                if (connection != null && !connection.isConnected) {
                    _stateEvents.emit(StateEvent.RecoveryAttempt(connectionId))
                    
                    // Attempt reconnection
                    val result = connection.reconnect()
                    if (result is Result.Success) {
                        _stateEvents.emit(StateEvent.RecoverySuccess(connectionId))
                    } else {
                        _stateEvents.emit(StateEvent.RecoveryFailed(connectionId, result.exception))
                    }
                }
            } catch (e: Exception) {
                _stateEvents.emit(StateEvent.RecoveryFailed(connectionId, e))
            }
        }
    }
    
    private suspend fun cleanupConnectionState(connectionId: String) {
        // Remove any temporary state or cached data for this connection
        stateRepository.cleanupConnectionData(connectionId)
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // STATE SYNCHRONIZATION INITIALIZATION
    // ═══════════════════════════════════════════════════════════════════════
    
    private fun initializeStateSynchronization() {
        // Monitor global state changes and emit events
        globalState
            .distinctUntilChanged()
            .onEach { newGlobalState ->
                _stateEvents.emit(StateEvent.GlobalStateChanged(newGlobalState))
            }
            .launchIn(scope)
        
        // Monitor connection state changes for cross-connection coordination
        connectionStates
            .distinctUntilChanged()
            .onEach { states ->
                handleCrossConnectionCoordination(states)
            }
            .launchIn(scope)
    }
    
    private suspend fun handleCrossConnectionCoordination(states: Map<String, ConnectionStateInfo>) {
        // Check for conflicts (e.g., multiple connections to same scanner)
        val scannerConnections = states.values.groupBy { it.scanner.id }
        
        for ((scannerId, connections) in scannerConnections) {
            if (connections.size > 1) {
                val activeConnections = connections.filter { it.state.isConnected }
                if (activeConnections.size > 1) {
                    // Multiple active connections to same scanner - emit conflict event
                    _stateEvents.emit(StateEvent.ConnectionConflict(scannerId, activeConnections.map { it.connectionId }))
                }
            }
        }
        
        // Check for resource constraints
        val activeConnections = states.values.count { it.state.isConnected }
        if (activeConnections > MAX_CONCURRENT_CONNECTIONS) {
            _stateEvents.emit(StateEvent.ResourceConstraint("Too many active connections: $activeConnections"))
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Gets the current state of a specific connection.
     */
    fun getConnectionState(connectionId: String): ConnectionStateInfo? {
        return _connectionStates.value[connectionId]
    }
    
    /**
     * Gets all connections for a specific scanner.
     */
    fun getConnectionsForScanner(scannerId: String): List<ConnectionStateInfo> {
        return _connectionStates.value.values.filter { it.scanner.id == scannerId }
    }
    
    /**
     * Gets connections by type.
     */
    fun getConnectionsByType(type: ScannerConnectionType): List<ConnectionStateInfo> {
        return _connectionStates.value.values.filter { it.connectionType == type }
    }
    
    /**
     * Forces state synchronization for a connection.
     */
    suspend fun synchronizeConnectionState(connectionId: String) {
        val connection = connectionRegistry[connectionId] ?: return
        handleConnectionStateChange(connectionId, connection.connectionState.value)
    }
    
    /**
     * Triggers recovery for a specific connection.
     */
    suspend fun triggerRecovery(connectionId: String): Result<Unit> {
        val stateInfo = getConnectionState(connectionId) 
            ?: return Result.Error(ConnectionException("Connection not found: $connectionId"))
        
        if (!stateInfo.state.isError) {
            return Result.Error(ConnectionException("Connection is not in error state"))
        }
        
        scheduleRecovery(connectionId)
        return Result.Success(Unit)
    }
    
    /**
     * Clears all persisted state (for testing or reset).
     */
    suspend fun clearAllState() = stateMutex.withLock {
        stateRepository.clearAllState()
        _connectionStates.value = emptyMap()
        _globalState.value = GlobalConnectionState()
        _stateEvents.emit(StateEvent.StateCleared)
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════════
    
    fun release() {
        if (released.getAndSet(true)) return
        
        // Cancel all subscriptions
        stateSubscriptions.values.forEach { it.cancel() }
        stateSubscriptions.clear()
        
        // Cancel scope
        scope.cancel()
        
        // Clear registrations
        connectionRegistry.clear()
    }
    
    companion object {
        private const val MAX_CONCURRENT_CONNECTIONS = 10
    }
}