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
import com.spacetec.core.domain.models.scanner.ScannerState
import com.spacetec.obd.scanner.core.ConnectionState
import com.spacetec.obd.scanner.core.DiscoveredScanner
import com.spacetec.obd.scanner.core.DiscoveryOptions
import com.spacetec.obd.scanner.core.ScannerConnection
import com.spacetec.obd.scanner.core.ScannerConnectionFactory
import com.spacetec.obd.scanner.core.ScannerConnectionOptions
import com.spacetec.obd.scanner.core.ScannerError
import com.spacetec.obd.scanner.core.ScannerInitResult
import com.spacetec.obd.scanner.core.ScannerManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enhanced ScannerManager with comprehensive state management.
 * 
 * Integrates with ConnectionStateManager to provide:
 * - Unified state management across connection types
 * - State synchronization and consistency
 * - State persistence and recovery
 * - Cross-connection coordination
 * 
 * This is a wrapper around the existing ScannerManager that adds
 * the enhanced state management capabilities required by task 12.2.
 */
@Singleton
class EnhancedScannerManager @Inject constructor(
    private val baseScannerManager: ScannerManager,
    private val stateManager: ConnectionStateManager,
    private val stateSynchronizer: StateSynchronizer,
    private val connectionFactory: ScannerConnectionFactory,
    @StateDispatcher private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : ScannerManager {
    
    // ═══════════════════════════════════════════════════════════════════════
    // STATE FLOWS
    // ═══════════════════════════════════════════════════════════════════════
    
    private val _enhancedScannerState = MutableStateFlow(ScannerState.DISCONNECTED)
    private val enhancedScannerState: StateFlow<ScannerState> = _enhancedScannerState.asStateFlow()
    
    // ═══════════════════════════════════════════════════════════════════════
    // INTERNAL STATE
    // ═══════════════════════════════════════════════════════════════════════
    
    private val scope = CoroutineScope(dispatcher + SupervisorJob())
    private val stateMutex = Mutex()
    private val released = AtomicBoolean(false)
    
    // Track active connections for state management
    private val activeConnections = mutableMapOf<String, ScannerConnection>()
    
    init {
        initializeStateIntegration()
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // STATE INTEGRATION INITIALIZATION
    // ═══════════════════════════════════════════════════════════════════════
    
    private fun initializeStateIntegration() {
        // Monitor base scanner manager state changes
        baseScannerManager.scannerState
            .distinctUntilChanged()
            .onEach { baseState ->
                handleBaseScannerStateChange(baseState)
            }
            .launchIn(scope)
        
        // Monitor active scanner changes
        baseScannerManager.activeScanner
            .distinctUntilChanged()
            .onEach { scanner ->
                handleActiveScannerChange(scanner)
            }
            .launchIn(scope)
        
        // Monitor state manager events
        stateManager.stateEvents
            .onEach { event ->
                handleStateManagerEvent(event)
            }
            .launchIn(scope)
        
        // Monitor synchronization events
        stateSynchronizer.synchronizationEvents
            .onEach { event ->
                handleSynchronizationEvent(event)
            }
            .launchIn(scope)
        
        // Create enhanced scanner state from global state
        stateManager.globalState
            .map { globalState ->
                mapGlobalStateToScannerState(globalState)
            }
            .distinctUntilChanged()
            .onEach { enhancedState ->
                _enhancedScannerState.value = enhancedState
            }
            .launchIn(scope)
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // STATE CHANGE HANDLERS
    // ═══════════════════════════════════════════════════════════════════════
    
    private suspend fun handleBaseScannerStateChange(baseState: ScannerState) {
        // Update enhanced state based on base state and global state
        val globalState = stateManager.globalState.value
        val enhancedState = combineStates(baseState, globalState)
        _enhancedScannerState.value = enhancedState
    }
    
    private suspend fun handleActiveScannerChange(scanner: Scanner?) {
        stateMutex.withLock {
            if (scanner != null) {
                // Register the connection with state manager
                val connection = baseScannerManager.currentConnection
                if (connection != null) {
                    stateManager.registerConnection(
                        connectionId = connection.connectionId,
                        connection = connection,
                        scanner = scanner
                    )
                    
                    activeConnections[connection.connectionId] = connection
                }
            } else {
                // Unregister all connections
                activeConnections.keys.forEach { connectionId ->
                    stateManager.unregisterConnection(connectionId)
                }
                activeConnections.clear()
            }
        }
    }
    
    private suspend fun handleStateManagerEvent(event: StateEvent) {
        when (event) {
            is StateEvent.ConnectionError -> {
                // Propagate error to base scanner manager
                // This allows the base manager to handle recovery
            }
            
            is StateEvent.RecoveryAttempt -> {
                // Coordinate with base manager for recovery
                val connection = activeConnections[event.connectionId]
                if (connection != null && !connection.isConnected) {
                    // Trigger reconnection through base manager
                    scope.launch {
                        baseScannerManager.reconnect()
                    }
                }
            }
            
            is StateEvent.QualityDegraded -> {
                // Consider switching connections if quality is poor
                handleQualityDegradation(event.connectionId, event.quality)
            }
            
            else -> {
                // Other events are handled by the state manager
            }
        }
    }
    
    private suspend fun handleSynchronizationEvent(event: SynchronizationEvent) {
        when (event) {
            is SynchronizationEvent.ConflictResolution -> {
                // Handle connection conflicts by disconnecting secondary connections
                for (conflictingId in event.conflictingConnectionIds) {
                    val connection = activeConnections[conflictingId]
                    if (connection != null && connection.isConnected) {
                        connection.disconnect(graceful = true)
                    }
                }
            }
            
            is SynchronizationEvent.QualityBasedSwitching -> {
                // Switch to better quality connection
                handleConnectionSwitching(event.fromConnectionId, event.toConnectionId)
            }
            
            else -> {
                // Other synchronization events are informational
            }
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // ENHANCED STATE MANAGEMENT METHODS
    // ═══════════════════════════════════════════════════════════════════════
    
    private suspend fun handleQualityDegradation(connectionId: String, quality: ConnectionQuality) {
        if (quality.qualityScore < 40) { // Poor quality threshold
            // Look for alternative connections
            val stateInfo = stateManager.getConnectionState(connectionId)
            if (stateInfo != null) {
                val alternatives = stateManager.getConnectionsForScanner(stateInfo.scanner.id)
                    .filter { it.connectionId != connectionId }
                    .filter { it.connectionQuality?.qualityScore ?: 0 > quality.qualityScore + 20 }
                
                if (alternatives.isNotEmpty()) {
                    val bestAlternative = alternatives.maxByOrNull { 
                        it.connectionQuality?.qualityScore ?: 0 
                    }
                    
                    if (bestAlternative != null) {
                        // Switch to better connection
                        handleConnectionSwitching(connectionId, bestAlternative.connectionId)
                    }
                }
            }
        }
    }
    
    private suspend fun handleConnectionSwitching(fromConnectionId: String, toConnectionId: String) {
        val fromConnection = activeConnections[fromConnectionId]
        val toStateInfo = stateManager.getConnectionState(toConnectionId)
        
        if (fromConnection != null && toStateInfo != null) {
            // Disconnect current connection
            fromConnection.disconnect(graceful = true)
            
            // Connect to new scanner
            val connectResult = baseScannerManager.connect(toStateInfo.scanner)
            if (connectResult is Result.Success) {
                // Connection switch successful
                // State manager will handle the registration automatically
            }
        }
    }
    
    private fun mapGlobalStateToScannerState(globalState: GlobalConnectionState): ScannerState {
        return when {
            globalState.activeConnections > 0 -> ScannerState.CONNECTED
            globalState.connectingConnections > 0 -> ScannerState.CONNECTING
            globalState.errorConnections > 0 -> ScannerState.ERROR
            else -> ScannerState.DISCONNECTED
        }
    }
    
    private fun combineStates(baseState: ScannerState, globalState: GlobalConnectionState): ScannerState {
        // Combine base scanner state with global connection state
        // Priority: base state takes precedence, but global state provides context
        return when (baseState) {
            ScannerState.CONNECTED -> {
                if (globalState.overallHealth == ConnectionHealth.POOR) {
                    ScannerState.ERROR // Degraded connection quality
                } else {
                    ScannerState.CONNECTED
                }
            }
            ScannerState.ERROR -> {
                if (globalState.activeConnections > 0) {
                    ScannerState.CONNECTED // Alternative connection available
                } else {
                    ScannerState.ERROR
                }
            }
            else -> baseState
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // ENHANCED PUBLIC API
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Gets the enhanced scanner state that considers global connection health.
     */
    val enhancedState: StateFlow<ScannerState>
        get() = enhancedScannerState
    
    /**
     * Gets global connection state information.
     */
    val globalConnectionState: StateFlow<GlobalConnectionState>
        get() = stateManager.globalState
    
    /**
     * Gets all connection states.
     */
    val allConnectionStates: StateFlow<Map<String, ConnectionStateInfo>>
        get() = stateManager.connectionStates
    
    /**
     * Gets state management events.
     */
    val stateEvents: SharedFlow<StateEvent>
        get() = stateManager.stateEvents
    
    /**
     * Gets synchronization events.
     */
    val synchronizationEvents: SharedFlow<SynchronizationEvent>
        get() = stateSynchronizer.synchronizationEvents
    
    /**
     * Forces synchronization of all connection states.
     */
    suspend fun synchronizeAllStates(): Result<Unit> {
        return stateSynchronizer.synchronizeAllStates()
    }
    
    /**
     * Triggers recovery for a specific connection.
     */
    suspend fun triggerConnectionRecovery(connectionId: String): Result<Unit> {
        return stateManager.triggerRecovery(connectionId)
    }
    
    /**
     * Gets connection state information for a specific connection.
     */
    fun getConnectionStateInfo(connectionId: String): ConnectionStateInfo? {
        return stateManager.getConnectionState(connectionId)
    }
    
    /**
     * Gets all connections for a specific scanner.
     */
    fun getConnectionsForScanner(scannerId: String): List<ConnectionStateInfo> {
        return stateManager.getConnectionsForScanner(scannerId)
    }
    
    /**
     * Clears all persisted state (for testing or reset).
     */
    suspend fun clearAllPersistedState(): Result<Unit> {
        return try {
            stateManager.clearAllState()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(ConnectionException("Failed to clear state: ${e.message}", e))
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // DELEGATE TO BASE SCANNER MANAGER
    // ═══════════════════════════════════════════════════════════════════════
    
    override val activeScanner: StateFlow<Scanner?> = baseScannerManager.activeScanner
    
    override val scannerState: StateFlow<ScannerState> = enhancedScannerState
    
    override val discoveredScanners: SharedFlow<DiscoveredScanner> = baseScannerManager.discoveredScanners
    
    override val isDiscovering: StateFlow<Boolean> = baseScannerManager.isDiscovering
    
    override val errors: SharedFlow<ScannerError> = baseScannerManager.errors
    
    override val isConnected: Boolean get() = baseScannerManager.isConnected
    
    override val currentConnection: ScannerConnection? get() = baseScannerManager.currentConnection
    
    override fun startDiscovery(options: DiscoveryOptions): Flow<DiscoveredScanner> {
        return baseScannerManager.startDiscovery(options)
    }
    
    override suspend fun stopDiscovery() {
        baseScannerManager.stopDiscovery()
    }
    
    override suspend fun getPairedScanners(): List<Scanner> {
        return baseScannerManager.getPairedScanners()
    }
    
    override suspend fun getLastConnectedScanner(): Scanner? {
        return baseScannerManager.getLastConnectedScanner()
    }
    
    override suspend fun connect(
        scanner: Scanner,
        options: ScannerConnectionOptions
    ): Result<ScannerInitResult> {
        val result = baseScannerManager.connect(scanner, options)
        
        // Register connection with state manager if successful
        if (result is Result.Success) {
            val connection = baseScannerManager.currentConnection
            if (connection != null) {
                stateManager.registerConnection(
                    connectionId = connection.connectionId,
                    connection = connection,
                    scanner = scanner
                )
                
                activeConnections[connection.connectionId] = connection
            }
        }
        
        return result
    }
    
    override suspend fun connect(
        address: String,
        connectionType: com.spacetec.core.domain.models.scanner.ScannerConnectionType,
        options: ScannerConnectionOptions
    ): Result<ScannerInitResult> {
        return baseScannerManager.connect(address, connectionType, options)
    }
    
    override suspend fun disconnect(graceful: Boolean) {
        // Unregister connections before disconnecting
        activeConnections.keys.forEach { connectionId ->
            stateManager.unregisterConnection(connectionId)
        }
        activeConnections.clear()
        
        baseScannerManager.disconnect(graceful)
    }
    
    override suspend fun reconnect(): Result<ScannerInitResult> {
        return baseScannerManager.reconnect()
    }
    
    override suspend fun initializeScanner(): Result<com.spacetec.core.domain.models.scanner.ScannerInfo> {
        return baseScannerManager.initializeScanner()
    }
    
    override suspend fun detectProtocol(protocolHint: com.spacetec.domain.models.vehicle.ProtocolType?): Result<com.spacetec.domain.models.vehicle.DetectedProtocol> {
        return baseScannerManager.detectProtocol(protocolHint)
    }
    
    override suspend fun setProtocol(protocol: com.spacetec.domain.models.vehicle.ProtocolType): Result<Unit> {
        return baseScannerManager.setProtocol(protocol)
    }
    
    override fun getConnection(): ScannerConnection? {
        return baseScannerManager.getConnection()
    }
    
    override suspend fun sendCommand(command: String, timeout: Long): Result<String> {
        return baseScannerManager.sendCommand(command, timeout)
    }
    
    override suspend fun sendOBDRequest(request: ByteArray, timeout: Long): Result<ByteArray> {
        return baseScannerManager.sendOBDRequest(request, timeout)
    }
    
    override suspend fun readVoltage(): Result<Float> {
        return baseScannerManager.readVoltage()
    }
    
    override suspend fun getScannerInfo(): Result<com.spacetec.core.domain.models.scanner.ScannerInfo> {
        return baseScannerManager.getScannerInfo()
    }
    
    override fun release() {
        if (released.getAndSet(true)) return
        
        // Release state management components
        stateManager.release()
        stateSynchronizer.release()
        
        // Cancel scope
        scope.cancel()
        
        // Clear active connections
        activeConnections.clear()
        
        // Release base manager
        baseScannerManager.release()
    }
}