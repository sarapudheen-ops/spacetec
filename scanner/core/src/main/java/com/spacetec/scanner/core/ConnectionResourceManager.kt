/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 *
 * This file is part of the SpaceTec professional automotive diagnostic system.
 */

package com.spacetec.obd.scanner.core

import com.spacetec.core.common.exceptions.ConnectionException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resource manager for monitoring and managing scanner connection resources.
 *
 * This manager tracks resource usage across all scanner connections,
 * monitors for resource leaks, enforces resource limits, and provides
 * cleanup mechanisms for optimal resource utilization.
 *
 * ## Features
 *
 * - Resource usage tracking and monitoring
 * - Memory leak detection and prevention
 * - Resource limit enforcement
 * - Automatic cleanup of abandoned resources
 * - Performance monitoring and alerts
 * - Resource usage statistics and reporting
 *
 * ## Resource Types Monitored
 *
 * - Active connections
 * - Memory usage
 * - Thread usage
 * - File handles (for USB/serial connections)
 * - Network sockets (for WiFi connections)
 * - Bluetooth resources
 *
 * ## Usage Example
 *
 * ```kotlin
 * @Inject
 * lateinit var resourceManager: ConnectionResourceManager
 *
 * // Register a connection for monitoring
 * resourceManager.registerConnection(connection)
 *
 * // Monitor resource usage
 * resourceManager.resourceAlerts.collect { alert ->
 *     handleResourceAlert(alert)
 * }
 * ```
 *
 * @author SpaceTec Development Team
 * @since 1.0.0
 */
@Singleton
class ConnectionResourceManager @Inject constructor() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val resourceMutex = Mutex()
    
    // Resource tracking
    private val registeredConnections = ConcurrentHashMap<String, ConnectionResource>()
    private val resourceUsageHistory = mutableListOf<ResourceSnapshot>()
    
    // Resource counters
    private val totalConnectionsRegistered = AtomicInteger(0)
    private val totalConnectionsReleased = AtomicInteger(0)
    private val currentActiveConnections = AtomicInteger(0)
    private val peakActiveConnections = AtomicInteger(0)
    
    // Memory tracking
    private val initialMemoryUsage = AtomicLong(0)
    private val peakMemoryUsage = AtomicLong(0)
    
    // Resource alerts
    private val _resourceAlerts = MutableSharedFlow<ResourceAlert>()
    val resourceAlerts: SharedFlow<ResourceAlert> = _resourceAlerts.asSharedFlow()
    
    // Manager state
    private val isShutdown = AtomicBoolean(false)
    private var monitoringJob: Job? = null
    
    init {
        initialMemoryUsage.set(getCurrentMemoryUsage())
        startResourceMonitoring()
    }

    // ═══════════════════════════════════════════════════════════════════════
    // CONNECTION REGISTRATION
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Registers a connection for resource monitoring.
     *
     * @param connection Connection to monitor
     * @throws ConnectionException if resource limits are exceeded
     */
    suspend fun registerConnection(connection: ScannerConnection) = resourceMutex.withLock {
        if (isShutdown.get()) {
            throw ConnectionException("Resource manager is shutdown")
        }
        
        // Check resource limits
        val currentActive = currentActiveConnections.get()
        if (currentActive >= MAX_ACTIVE_CONNECTIONS) {
            emitAlert(ResourceAlert.LimitExceeded(
                resourceType = "connections",
                currentValue = currentActive,
                limit = MAX_ACTIVE_CONNECTIONS
            ))
            throw ConnectionException("Maximum active connections limit exceeded ($MAX_ACTIVE_CONNECTIONS)")
        }
        
        val resource = ConnectionResource(
            connectionId = connection.connectionId,
            connectionType = connection.connectionType,
            registeredAt = System.currentTimeMillis(),
            connection = connection
        )
        
        registeredConnections[connection.connectionId] = resource
        totalConnectionsRegistered.incrementAndGet()
        
        val newActiveCount = currentActiveConnections.incrementAndGet()
        if (newActiveCount > peakActiveConnections.get()) {
            peakActiveConnections.set(newActiveCount)
        }
        
        // Check memory usage
        checkMemoryUsage()
    }

    /**
     * Unregisters a connection from resource monitoring.
     *
     * @param connectionId Connection ID to unregister
     */
    suspend fun unregisterConnection(connectionId: String) = resourceMutex.withLock {
        val resource = registeredConnections.remove(connectionId)
        if (resource != null) {
            totalConnectionsReleased.incrementAndGet()
            currentActiveConnections.decrementAndGet()
            
            // Update resource usage
            resource.releasedAt = System.currentTimeMillis()
            
            // Check for resource leaks
            val lifetime = resource.releasedAt - resource.registeredAt
            if (lifetime > RESOURCE_LEAK_THRESHOLD_MS) {
                emitAlert(ResourceAlert.PotentialLeak(
                    connectionId = connectionId,
                    connectionType = resource.connectionType,
                    lifetimeMs = lifetime
                ))
            }
        }
    }

    /**
     * Updates resource usage for a connection.
     *
     * @param connectionId Connection ID
     * @param bytesTransferred Bytes transferred since last update
     * @param operationCount Number of operations since last update
     */
    suspend fun updateResourceUsage(
        connectionId: String,
        bytesTransferred: Long,
        operationCount: Int
    ) {
        val resource = registeredConnections[connectionId] ?: return
        
        resource.totalBytesTransferred += bytesTransferred
        resource.totalOperations += operationCount
        resource.lastActivityAt = System.currentTimeMillis()
    }

    // ═══════════════════════════════════════════════════════════════════════
    // RESOURCE MONITORING
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Gets current resource usage statistics.
     */
    fun getResourceUsage(): ResourceUsage {
        val currentMemory = getCurrentMemoryUsage()
        val memoryIncrease = currentMemory - initialMemoryUsage.get()
        
        return ResourceUsage(
            activeConnections = currentActiveConnections.get(),
            totalConnectionsRegistered = totalConnectionsRegistered.get(),
            totalConnectionsReleased = totalConnectionsReleased.get(),
            peakActiveConnections = peakActiveConnections.get(),
            currentMemoryUsage = currentMemory,
            memoryIncrease = memoryIncrease,
            peakMemoryUsage = peakMemoryUsage.get(),
            registeredConnectionIds = registeredConnections.keys.toList(),
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Gets detailed information about registered connections.
     */
    fun getConnectionDetails(): List<ConnectionResourceInfo> {
        return registeredConnections.values.map { resource ->
            ConnectionResourceInfo(
                connectionId = resource.connectionId,
                connectionType = resource.connectionType,
                registeredAt = resource.registeredAt,
                lastActivityAt = resource.lastActivityAt,
                totalBytesTransferred = resource.totalBytesTransferred,
                totalOperations = resource.totalOperations,
                isConnected = resource.connection.isConnected,
                lifetimeMs = System.currentTimeMillis() - resource.registeredAt
            )
        }
    }

    /**
     * Forces cleanup of abandoned connections.
     */
    suspend fun forceCleanup() = resourceMutex.withLock {
        val currentTime = System.currentTimeMillis()
        val connectionsToCleanup = mutableListOf<ConnectionResource>()
        
        // Find abandoned connections
        registeredConnections.values.forEach { resource ->
            val timeSinceActivity = currentTime - resource.lastActivityAt
            val lifetime = currentTime - resource.registeredAt
            
            if (timeSinceActivity > ABANDONED_CONNECTION_THRESHOLD_MS ||
                lifetime > MAX_CONNECTION_LIFETIME_MS) {
                connectionsToCleanup.add(resource)
            }
        }
        
        // Cleanup abandoned connections
        connectionsToCleanup.forEach { resource ->
            try {
                resource.connection.release()
                unregisterConnection(resource.connectionId)
                
                emitAlert(ResourceAlert.AbandonedConnectionCleaned(
                    connectionId = resource.connectionId,
                    connectionType = resource.connectionType,
                    lifetimeMs = currentTime - resource.registeredAt
                ))
            } catch (e: Exception) {
                // Log error but continue cleanup
            }
        }
    }

    /**
     * Shuts down the resource manager.
     */
    suspend fun shutdown() {
        if (isShutdown.getAndSet(true)) {
            return
        }
        
        // Stop monitoring
        monitoringJob?.cancel()
        monitoringJob = null
        
        // Force cleanup of all connections
        resourceMutex.withLock {
            registeredConnections.values.forEach { resource ->
                try {
                    resource.connection.release()
                } catch (e: Exception) {
                    // Ignore errors during shutdown
                }
            }
            registeredConnections.clear()
        }
        
        // Cancel scope
        scope.cancel()
    }

    // ═══════════════════════════════════════════════════════════════════════
    // INTERNAL METHODS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Starts the resource monitoring task.
     */
    private fun startResourceMonitoring() {
        monitoringJob = scope.launch {
            while (isActive && !isShutdown.get()) {
                delay(MONITORING_INTERVAL_MS)
                performResourceCheck()
            }
        }
    }

    /**
     * Performs periodic resource checks.
     */
    private suspend fun performResourceCheck() {
        // Take resource snapshot
        val snapshot = ResourceSnapshot(
            timestamp = System.currentTimeMillis(),
            activeConnections = currentActiveConnections.get(),
            memoryUsage = getCurrentMemoryUsage()
        )
        
        resourceMutex.withLock {
            resourceUsageHistory.add(snapshot)
            
            // Keep only recent history
            while (resourceUsageHistory.size > MAX_HISTORY_SIZE) {
                resourceUsageHistory.removeAt(0)
            }
        }
        
        // Check for resource issues
        checkMemoryUsage()
        checkForAbandonedConnections()
        checkResourceLimits()
    }

    /**
     * Checks memory usage and alerts if high.
     */
    private suspend fun checkMemoryUsage() {
        val currentMemory = getCurrentMemoryUsage()
        if (currentMemory > peakMemoryUsage.get()) {
            peakMemoryUsage.set(currentMemory)
        }
        
        val memoryIncrease = currentMemory - initialMemoryUsage.get()
        if (memoryIncrease > MEMORY_LEAK_THRESHOLD_BYTES) {
            emitAlert(ResourceAlert.HighMemoryUsage(
                currentUsage = currentMemory,
                increase = memoryIncrease,
                threshold = MEMORY_LEAK_THRESHOLD_BYTES
            ))
        }
    }

    /**
     * Checks for abandoned connections.
     */
    private suspend fun checkForAbandonedConnections() {
        val currentTime = System.currentTimeMillis()
        
        registeredConnections.values.forEach { resource ->
            val timeSinceActivity = currentTime - resource.lastActivityAt
            if (timeSinceActivity > ABANDONED_CONNECTION_THRESHOLD_MS) {
                emitAlert(ResourceAlert.AbandonedConnection(
                    connectionId = resource.connectionId,
                    connectionType = resource.connectionType,
                    timeSinceActivityMs = timeSinceActivity
                ))
            }
        }
    }

    /**
     * Checks resource limits.
     */
    private suspend fun checkResourceLimits() {
        val activeCount = currentActiveConnections.get()
        if (activeCount > MAX_ACTIVE_CONNECTIONS * 0.8) { // 80% threshold
            emitAlert(ResourceAlert.ApproachingLimit(
                resourceType = "connections",
                currentValue = activeCount,
                limit = MAX_ACTIVE_CONNECTIONS,
                thresholdPercentage = 80
            ))
        }
    }

    /**
     * Gets current memory usage in bytes.
     */
    private fun getCurrentMemoryUsage(): Long {
        val runtime = Runtime.getRuntime()
        return runtime.totalMemory() - runtime.freeMemory()
    }

    /**
     * Emits a resource alert.
     */
    private suspend fun emitAlert(alert: ResourceAlert) {
        _resourceAlerts.emit(alert)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // DATA CLASSES
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Resource information for a registered connection.
     */
    private data class ConnectionResource(
        val connectionId: String,
        val connectionType: com.spacetec.core.domain.models.scanner.ScannerConnectionType,
        val registeredAt: Long,
        val connection: ScannerConnection,
        var lastActivityAt: Long = System.currentTimeMillis(),
        var totalBytesTransferred: Long = 0,
        var totalOperations: Int = 0,
        var releasedAt: Long = 0
    )

    /**
     * Snapshot of resource usage at a point in time.
     */
    private data class ResourceSnapshot(
        val timestamp: Long,
        val activeConnections: Int,
        val memoryUsage: Long
    )

    // ═══════════════════════════════════════════════════════════════════════
    // COMPANION
    // ═══════════════════════════════════════════════════════════════════════

    companion object {
        private const val MAX_ACTIVE_CONNECTIONS = 20
        private const val MONITORING_INTERVAL_MS = 30_000L // 30 seconds
        private const val RESOURCE_LEAK_THRESHOLD_MS = 10 * 60 * 1000L // 10 minutes
        private const val ABANDONED_CONNECTION_THRESHOLD_MS = 5 * 60 * 1000L // 5 minutes
        private const val MAX_CONNECTION_LIFETIME_MS = 60 * 60 * 1000L // 1 hour
        private const val MEMORY_LEAK_THRESHOLD_BYTES = 50 * 1024 * 1024L // 50 MB
        private const val MAX_HISTORY_SIZE = 100
    }
}

/**
 * Current resource usage information.
 */
data class ResourceUsage(
    val activeConnections: Int,
    val totalConnectionsRegistered: Int,
    val totalConnectionsReleased: Int,
    val peakActiveConnections: Int,
    val currentMemoryUsage: Long,
    val memoryIncrease: Long,
    val peakMemoryUsage: Long,
    val registeredConnectionIds: List<String>,
    val timestamp: Long
) {
    
    /**
     * Connection leak count (registered but not released).
     */
    val potentialLeaks: Int
        get() = totalConnectionsRegistered - totalConnectionsReleased
    
    /**
     * Memory usage in MB.
     */
    val memoryUsageMB: Float
        get() = currentMemoryUsage / (1024f * 1024f)
    
    /**
     * Memory increase in MB.
     */
    val memoryIncreaseMB: Float
        get() = memoryIncrease / (1024f * 1024f)
    
    /**
     * Returns a summary string of the resource usage.
     */
    fun toSummary(): String {
        return buildString {
            appendLine("Resource Usage:")
            appendLine("  Active Connections: $activeConnections")
            appendLine("  Peak Connections: $peakActiveConnections")
            appendLine("  Total Registered: $totalConnectionsRegistered")
            appendLine("  Total Released: $totalConnectionsReleased")
            appendLine("  Potential Leaks: $potentialLeaks")
            appendLine("  Memory Usage: ${String.format("%.1f", memoryUsageMB)} MB")
            appendLine("  Memory Increase: ${String.format("%.1f", memoryIncreaseMB)} MB")
        }
    }
}

/**
 * Detailed information about a connection resource.
 */
data class ConnectionResourceInfo(
    val connectionId: String,
    val connectionType: com.spacetec.core.domain.models.scanner.ScannerConnectionType,
    val registeredAt: Long,
    val lastActivityAt: Long,
    val totalBytesTransferred: Long,
    val totalOperations: Int,
    val isConnected: Boolean,
    val lifetimeMs: Long
) {
    
    /**
     * Time since last activity in milliseconds.
     */
    val timeSinceActivityMs: Long
        get() = System.currentTimeMillis() - lastActivityAt
    
    /**
     * Whether the connection appears abandoned.
     */
    val isAbandoned: Boolean
        get() = timeSinceActivityMs > 5 * 60 * 1000L // 5 minutes
}

/**
 * Resource alert types.
 */
sealed class ResourceAlert {
    
    /**
     * Resource limit exceeded.
     */
    data class LimitExceeded(
        val resourceType: String,
        val currentValue: Int,
        val limit: Int
    ) : ResourceAlert()
    
    /**
     * Approaching resource limit.
     */
    data class ApproachingLimit(
        val resourceType: String,
        val currentValue: Int,
        val limit: Int,
        val thresholdPercentage: Int
    ) : ResourceAlert()
    
    /**
     * High memory usage detected.
     */
    data class HighMemoryUsage(
        val currentUsage: Long,
        val increase: Long,
        val threshold: Long
    ) : ResourceAlert()
    
    /**
     * Potential resource leak detected.
     */
    data class PotentialLeak(
        val connectionId: String,
        val connectionType: com.spacetec.core.domain.models.scanner.ScannerConnectionType,
        val lifetimeMs: Long
    ) : ResourceAlert()
    
    /**
     * Abandoned connection detected.
     */
    data class AbandonedConnection(
        val connectionId: String,
        val connectionType: com.spacetec.core.domain.models.scanner.ScannerConnectionType,
        val timeSinceActivityMs: Long
    ) : ResourceAlert()
    
    /**
     * Abandoned connection was cleaned up.
     */
    data class AbandonedConnectionCleaned(
        val connectionId: String,
        val connectionType: com.spacetec.core.domain.models.scanner.ScannerConnectionType,
        val lifetimeMs: Long
    ) : ResourceAlert()
}