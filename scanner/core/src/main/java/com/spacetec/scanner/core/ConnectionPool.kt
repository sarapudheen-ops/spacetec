/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 *
 * This file is part of the SpaceTec professional automotive diagnostic system.
 */

package com.spacetec.obd.scanner.core

import com.spacetec.core.common.exceptions.ConnectionException
import com.spacetec.core.common.result.Result
import com.spacetec.core.domain.models.scanner.ScannerConnectionType
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min

/**
 * Pool entry containing a connection and its metadata.
 */
private data class PoolEntry(
    val connection: ScannerConnection,
    val lastUsed: Long = System.currentTimeMillis(),
    val created: Long = System.currentTimeMillis(),
    var isAvailable: Boolean = true
)

/**
 * Connection pool for managing scanner connections efficiently.
 *
 * This pool provides:
 * - Reusable connection instances
 * - Connection lifecycle management
 * - Resource optimization
 * - Automatic cleanup of idle connections
 * - Thread-safe operations
 *
 * @param maxPoolSize Maximum number of connections in the pool
 * @param maxIdleTimeMs Maximum time a connection can be idle before being removed
 * @param dispatcher Coroutine dispatcher for pool operations
 *
 * @author SpaceTec Development Team
 * @since 1.0.0
 */
class ScannerConnectionPool(
    private val maxPoolSize: Int = 10,
    private val maxIdleTimeMs: Long = 5 * 60 * 1000L, // 5 minutes
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val pool = ConcurrentHashMap<String, MutableList<PoolEntry>>()
    private val poolMutex = Mutex()
    private val activeConnections = ConcurrentHashMap<String, ScannerConnection>()
    private val connectionCounter = AtomicInteger(0)
    private var cleanupJob: Job? = null

    init {
        startCleanupJob()
    }

    /**
     * Gets a connection from the pool or creates a new one.
     *
     * @param connectionType Type of connection needed
     * @param address Device address
     * @param config Connection configuration
     * @return Connection or error result
     */
    suspend fun getConnection(
        connectionType: ScannerConnectionType,
        address: String,
        config: ConnectionConfig = ConnectionConfig.DEFAULT
    ): Result<ScannerConnection> = poolMutex.withLock {
        val key = getPoolKey(connectionType, address)
        
        // Try to get an available connection from the pool
        val availableConnection = getAvailableConnection(key)
        
        return if (availableConnection != null) {
            // Found an available connection
            availableConnection.isAvailable = false
            activeConnections[key] = availableConnection.connection
            Result.Success(availableConnection.connection)
        } else {
            // Create a new connection if pool size allows
            if (getTotalConnections(key) < maxPoolSize) {
                val connection = createConnection(connectionType)
                val connectResult = connection.connect(address, config)
                
                return if (connectResult is Result.Success) {
                    val entry = PoolEntry(connection, isAvailable = false)
                    addToPool(key, entry)
                    activeConnections[key] = connection
                    Result.Success(connection)
                } else {
                    connection.release()
                    connectResult.map { connection }
                }
            } else {
                Result.Error(ConnectionException("Connection pool is full"))
            }
        }
    }

    /**
     * Returns a connection to the pool.
     *
     * @param connection Connection to return
     * @param address Device address
     */
    suspend fun returnConnection(connection: ScannerConnection, address: String) = poolMutex.withLock {
        val connectionType = connection.connectionType
        val key = getPoolKey(connectionType, address)
        
        // Find the entry for this connection
        val entries = pool[key] ?: return@withLock
        
        val entry = entries.find { it.connection.connectionId == connection.connectionId }
        if (entry != null) {
            entry.isAvailable = true
            entry.lastUsed = System.currentTimeMillis()
        }
        
        activeConnections.remove(key)
    }

    /**
     * Removes a connection from the pool and releases it.
     *
     * @param connection Connection to remove
     * @param address Device address
     */
    suspend fun removeConnection(connection: ScannerConnection, address: String) = poolMutex.withLock {
        val connectionType = connection.connectionType
        val key = getPoolKey(connectionType, address)
        
        // Find and remove the entry for this connection
        val entries = pool[key]
        if (entries != null) {
            val entry = entries.find { it.connection.connectionId == connection.connectionId }
            if (entry != null) {
                entries.remove(entry)
                connection.release()
            }
        }
        
        activeConnections.remove(key)
    }

    /**
     * Gets an available connection from the pool.
     */
    private fun getAvailableConnection(key: String): PoolEntry? {
        val entries = pool[key] ?: return null
        
        // Find the oldest available connection
        return entries.find { it.isAvailable }?.apply {
            isAvailable = false
        }
    }

    /**
     * Adds a connection to the pool.
     */
    private fun addToPool(key: String, entry: PoolEntry) {
        val entries = pool.getOrPut(key) { mutableListOf() }
        entries.add(entry)
    }

    /**
     * Gets the total number of connections for a key.
     */
    private fun getTotalConnections(key: String): Int {
        val entries = pool[key]
        return entries?.size ?: 0
    }

    /**
     * Creates a new connection based on the connection type.
     */
    private fun createConnection(connectionType: ScannerConnectionType): ScannerConnection {
        return when (connectionType) {
            ScannerConnectionType.BLUETOOTH_CLASSIC -> {
                // Implementation would create Bluetooth connection
                TODO("Implement Bluetooth connection creation")
            }
            ScannerConnectionType.BLUETOOTH_LE -> {
                // Implementation would create BLE connection
                TODO("Implement BLE connection creation")
            }
            ScannerConnectionType.WIFI -> {
                // Implementation would create WiFi connection
                TODO("Implement WiFi connection creation")
            }
            ScannerConnectionType.USB -> {
                // Implementation would create USB connection
                TODO("Implement USB connection creation")
            }
            ScannerConnectionType.J2534 -> {
                // Implementation would create J2534 connection
                TODO("Implement J2534 connection creation")
            }
            else -> {
                throw ConnectionException("Unsupported connection type: $connectionType")
            }
        }
    }

    /**
     * Gets the pool key for a connection type and address.
     */
    private fun getPoolKey(connectionType: ScannerConnectionType, address: String): String {
        return "${connectionType.name}:$address"
    }

    /**
     * Starts the cleanup job to remove idle connections.
     */
    private fun startCleanupJob() {
        cleanupJob = CoroutineScope(dispatcher).launch {
            while (isActive) {
                delay(60_000L) // Clean up every minute
                cleanupIdleConnections()
            }
        }
    }

    /**
     * Cleans up idle connections that have exceeded the max idle time.
     */
    private fun cleanupIdleConnections() {
        val now = System.currentTimeMillis()
        val keysToRemove = mutableListOf<String>()
        
        for ((key, entries) in pool) {
            val entriesToRemove = mutableListOf<PoolEntry>()
            
            for (entry in entries) {
                if (entry.isAvailable && (now - entry.lastUsed) > maxIdleTimeMs) {
                    entry.connection.release()
                    entriesToRemove.add(entry)
                }
            }
            
            entries.removeAll(entriesToRemove)
            
            if (entries.isEmpty()) {
                keysToRemove.add(key)
            }
        }
        
        for (key in keysToRemove) {
            pool.remove(key)
        }
    }

    /**
     * Gets pool statistics.
     */
    fun getStatistics(): PoolStatistics {
        var totalConnections = 0
        var availableConnections = 0
        var activeConnectionsCount = 0
        
        for (entries in pool.values) {
            totalConnections += entries.size
            availableConnections += entries.count { it.isAvailable }
        }
        
        activeConnectionsCount = activeConnections.size
        
        return PoolStatistics(
            totalConnections = totalConnections,
            availableConnections = availableConnections,
            activeConnections = activeConnectionsCount,
            maxPoolSize = maxPoolSize,
            poolKeys = pool.keys.toList()
        )
    }

    /**
     * Clears all connections from the pool.
     */
    suspend fun clear() = poolMutex.withLock {
        // Release all connections in the pool
        for (entries in pool.values) {
            for (entry in entries) {
                entry.connection.release()
            }
        }
        
        pool.clear()
        activeConnections.clear()
    }

    /**
     * Shuts down the connection pool and releases all resources.
     */
    suspend fun shutdown() {
        cleanupJob?.cancel()
        clear()
    }

    /**
     * Pool statistics.
     */
    data class PoolStatistics(
        val totalConnections: Int,
        val availableConnections: Int,
        val activeConnections: Int,
        val maxPoolSize: Int,
        val poolKeys: List<String>
    )

    companion object {
        /**
         * Creates a scanner connection pool.
         *
         * @param maxPoolSize Maximum number of connections in the pool
         * @param maxIdleTime Maximum time a connection can be idle before being removed
         * @return Scanner connection pool
         */
        fun create(
            maxPoolSize: Int = 10,
            maxIdleTime: Long = 5 * 60 * 1000L
        ): ScannerConnectionPool {
            return ScannerConnectionPool(maxPoolSize, maxIdleTime)
        }
    }
}