/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 *
 * This file is part of the SpaceTec professional automotive diagnostic system.
 */

package com.spacetec.obd.scanner.core.state

import com.spacetec.core.common.result.Result
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for persisting connection state data.
 * 
 * Handles saving and loading connection state information to/from local storage
 * for state recovery and persistence across app restarts.
 */
interface ConnectionStateRepository {
    
    /**
     * Saves connection state information.
     */
    suspend fun saveConnectionState(stateInfo: ConnectionStateInfo)
    
    /**
     * Loads connection state information.
     */
    suspend fun loadConnectionState(connectionId: String): ConnectionStateInfo?
    
    /**
     * Loads all connection states.
     */
    suspend fun loadAllConnectionStates(): List<ConnectionStateInfo>
    
    /**
     * Removes connection state.
     */
    suspend fun removeConnectionState(connectionId: String)
    
    /**
     * Saves global state.
     */
    suspend fun saveGlobalState(globalState: GlobalConnectionState)
    
    /**
     * Loads global state.
     */
    suspend fun loadGlobalState(): GlobalConnectionState?
    
    /**
     * Cleans up connection-specific data.
     */
    suspend fun cleanupConnectionData(connectionId: String)
    
    /**
     * Clears all persisted state.
     */
    suspend fun clearAllState()
}

/**
 * File-based implementation of ConnectionStateRepository.
 */
@Singleton
class FileConnectionStateRepository @Inject constructor(
    private val stateDirectory: File,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : ConnectionStateRepository {
    
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    private val connectionStatesDir = File(stateDirectory, "connections")
    private val globalStateFile = File(stateDirectory, "global_state.json")
    
    init {
        // Ensure directories exist
        connectionStatesDir.mkdirs()
    }
    
    override suspend fun saveConnectionState(stateInfo: ConnectionStateInfo) = withContext(dispatcher) {
        try {
            val serializable = stateInfo.toSerializable()
            val jsonString = json.encodeToString(serializable)
            val file = File(connectionStatesDir, "${stateInfo.connectionId}.json")
            file.writeText(jsonString)
        } catch (e: Exception) {
            throw IOException("Failed to save connection state: ${e.message}", e)
        }
    }
    
    override suspend fun loadConnectionState(connectionId: String): ConnectionStateInfo? = withContext(dispatcher) {
        try {
            val file = File(connectionStatesDir, "$connectionId.json")
            if (!file.exists()) return@withContext null
            
            val jsonString = file.readText()
            val serializable = json.decodeFromString<SerializableConnectionStateInfo>(jsonString)
            serializable.toConnectionStateInfo()
        } catch (e: Exception) {
            // Log error but return null instead of throwing
            null
        }
    }
    
    override suspend fun loadAllConnectionStates(): List<ConnectionStateInfo> = withContext(dispatcher) {
        try {
            if (!connectionStatesDir.exists()) return@withContext emptyList()
            
            connectionStatesDir.listFiles { _, name -> name.endsWith(".json") }
                ?.mapNotNull { file ->
                    try {
                        val jsonString = file.readText()
                        val serializable = json.decodeFromString<SerializableConnectionStateInfo>(jsonString)
                        serializable.toConnectionStateInfo()
                    } catch (e: Exception) {
                        // Skip corrupted files
                        null
                    }
                } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    override suspend fun removeConnectionState(connectionId: String) = withContext(dispatcher) {
        try {
            val file = File(connectionStatesDir, "$connectionId.json")
            file.delete()
        } catch (e: Exception) {
            // Ignore errors when removing
        }
    }
    
    override suspend fun saveGlobalState(globalState: GlobalConnectionState) = withContext(dispatcher) {
        try {
            val serializable = globalState.toSerializable()
            val jsonString = json.encodeToString(serializable)
            globalStateFile.writeText(jsonString)
        } catch (e: Exception) {
            throw IOException("Failed to save global state: ${e.message}", e)
        }
    }
    
    override suspend fun loadGlobalState(): GlobalConnectionState? = withContext(dispatcher) {
        try {
            if (!globalStateFile.exists()) return@withContext null
            
            val jsonString = globalStateFile.readText()
            val serializable = json.decodeFromString<SerializableGlobalConnectionState>(jsonString)
            serializable.toGlobalConnectionState()
        } catch (e: Exception) {
            null
        }
    }
    
    override suspend fun cleanupConnectionData(connectionId: String) = withContext(dispatcher) {
        // Remove connection state file
        removeConnectionState(connectionId)
        
        // Remove any other connection-specific files
        val connectionDir = File(stateDirectory, "connection_$connectionId")
        if (connectionDir.exists()) {
            connectionDir.deleteRecursively()
        }
    }
    
    override suspend fun clearAllState() = withContext(dispatcher) {
        try {
            // Remove all connection state files
            if (connectionStatesDir.exists()) {
                connectionStatesDir.deleteRecursively()
                connectionStatesDir.mkdirs()
            }
            
            // Remove global state file
            globalStateFile.delete()
        } catch (e: Exception) {
            throw IOException("Failed to clear all state: ${e.message}", e)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// SERIALIZABLE DATA CLASSES
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Serializable version of ConnectionStateInfo.
 */
@Serializable
data class SerializableConnectionStateInfo(
    val connectionId: String,
    val scannerId: String,
    val scannerName: String,
    val scannerAddress: String,
    val connectionType: String,
    val registeredAt: Long,
    val lastStateChange: Long,
    val stateHistory: List<SerializableStateTransition> = emptyList(),
    val connectionQuality: SerializableConnectionQuality? = null
)

/**
 * Serializable version of StateTransition.
 */
@Serializable
data class SerializableStateTransition(
    val fromStateType: String,
    val toStateType: String,
    val timestamp: Long
)

/**
 * Serializable version of ConnectionQuality.
 */
@Serializable
data class SerializableConnectionQuality(
    val signalStrength: Int? = null,
    val responseTime: Long,
    val errorRate: Float,
    val throughput: Float,
    val uptime: Long,
    val lastUpdated: Long
)

/**
 * Serializable version of GlobalConnectionState.
 */
@Serializable
data class SerializableGlobalConnectionState(
    val totalConnections: Int = 0,
    val activeConnections: Int = 0,
    val connectingConnections: Int = 0,
    val errorConnections: Int = 0,
    val connectionsByType: Map<String, Int> = emptyMap(),
    val lastUpdate: Long = System.currentTimeMillis(),
    val overallHealth: String = "UNKNOWN"
)

// ═══════════════════════════════════════════════════════════════════════════
// EXTENSION FUNCTIONS FOR SERIALIZATION
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Converts ConnectionStateInfo to serializable format.
 */
private fun ConnectionStateInfo.toSerializable(): SerializableConnectionStateInfo {
    return SerializableConnectionStateInfo(
        connectionId = connectionId,
        scannerId = scanner.id,
        scannerName = scanner.name,
        scannerAddress = scanner.address,
        connectionType = connectionType.name,
        registeredAt = registeredAt,
        lastStateChange = lastStateChange,
        stateHistory = stateHistory.map { it.toSerializable() },
        connectionQuality = connectionQuality?.toSerializable()
    )
}

/**
 * Converts SerializableConnectionStateInfo back to ConnectionStateInfo.
 * Note: This creates a minimal Scanner object and ConnectionState.Disconnected
 * since we can't fully serialize the complex state objects.
 */
private fun SerializableConnectionStateInfo.toConnectionStateInfo(): ConnectionStateInfo {
    return ConnectionStateInfo(
        connectionId = connectionId,
        scanner = com.spacetec.core.domain.models.scanner.Scanner(
            id = scannerId,
            name = scannerName,
            address = scannerAddress,
            connectionType = com.spacetec.core.domain.models.scanner.ScannerConnectionType.valueOf(connectionType),
            deviceType = com.spacetec.core.domain.models.scanner.ScannerDeviceType.GENERIC
        ),
        connectionType = com.spacetec.core.domain.models.scanner.ScannerConnectionType.valueOf(connectionType),
        state = com.spacetec.scanner.core.ConnectionState.Disconnected, // Default to disconnected on load
        registeredAt = registeredAt,
        lastStateChange = lastStateChange,
        stateHistory = stateHistory.map { it.toStateTransition() },
        connectionQuality = connectionQuality?.toConnectionQuality()
    )
}

/**
 * Converts StateTransition to serializable format.
 */
private fun StateTransition.toSerializable(): SerializableStateTransition {
    return SerializableStateTransition(
        fromStateType = fromState::class.simpleName ?: "Unknown",
        toStateType = toState::class.simpleName ?: "Unknown",
        timestamp = timestamp
    )
}

/**
 * Converts SerializableStateTransition back to StateTransition.
 */
private fun SerializableStateTransition.toStateTransition(): StateTransition {
    return StateTransition(
        fromState = com.spacetec.scanner.core.ConnectionState.Disconnected, // Default states
        toState = com.spacetec.scanner.core.ConnectionState.Disconnected,
        timestamp = timestamp
    )
}

/**
 * Converts ConnectionQuality to serializable format.
 */
private fun ConnectionQuality.toSerializable(): SerializableConnectionQuality {
    return SerializableConnectionQuality(
        signalStrength = signalStrength,
        responseTime = responseTime,
        errorRate = errorRate,
        throughput = throughput,
        uptime = uptime,
        lastUpdated = lastUpdated
    )
}

/**
 * Converts SerializableConnectionQuality back to ConnectionQuality.
 */
private fun SerializableConnectionQuality.toConnectionQuality(): ConnectionQuality {
    return ConnectionQuality(
        signalStrength = signalStrength,
        responseTime = responseTime,
        errorRate = errorRate,
        throughput = throughput,
        uptime = uptime,
        lastUpdated = lastUpdated
    )
}

/**
 * Converts GlobalConnectionState to serializable format.
 */
private fun GlobalConnectionState.toSerializable(): SerializableGlobalConnectionState {
    return SerializableGlobalConnectionState(
        totalConnections = totalConnections,
        activeConnections = activeConnections,
        connectingConnections = connectingConnections,
        errorConnections = errorConnections,
        connectionsByType = connectionsByType.mapKeys { it.key.name },
        lastUpdate = lastUpdate,
        overallHealth = overallHealth.name
    )
}

/**
 * Converts SerializableGlobalConnectionState back to GlobalConnectionState.
 */
private fun SerializableGlobalConnectionState.toGlobalConnectionState(): GlobalConnectionState {
    return GlobalConnectionState(
        totalConnections = totalConnections,
        activeConnections = activeConnections,
        connectingConnections = connectingConnections,
        errorConnections = errorConnections,
        connectionsByType = connectionsByType.mapKeys { 
            com.spacetec.core.domain.models.scanner.ScannerConnectionType.valueOf(it.key) 
        },
        lastUpdate = lastUpdate,
        overallHealth = ConnectionHealth.valueOf(overallHealth)
    )
}