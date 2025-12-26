/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 */

package com.spacetec.j2534

import com.spacetec.core.common.exceptions.ConnectionException
import com.spacetec.core.common.exceptions.CommunicationException
import com.spacetec.core.common.result.Result
import com.spacetec.j2534.constants.J2534Constants
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Manages multiple J2534 channels with concurrent access and resource allocation.
 *
 * This class handles concurrent channel management, channel isolation,
 * interference prevention, and channel priority/resource allocation
 * for professional diagnostic operations.
 */
class J2534ChannelManager(
    private val j2534Interface: J2534Interface,
    private val device: J2534Device
) {
    
    private val mutex = Mutex()
    private val channels = ConcurrentHashMap<Int, ManagedChannel>()
    private val protocolManagers = ConcurrentHashMap<Int, J2534Protocol>()
    private val channelPriorities = ConcurrentHashMap<Int, ChannelPriority>()
    private val resourceAllocations = ConcurrentHashMap<Int, ResourceAllocation>()
    
    private val nextChannelId = AtomicInteger(1)
    private var maxConcurrentChannels = J2534Constants.MAX_CHANNELS
    
    /**
     * Managed channel information.
     */
    private data class ManagedChannel(
        val channel: J2534Channel,
        val protocol: J2534Protocol,
        val priority: ChannelPriority,
        val resources: ResourceAllocation,
        val createdAt: Long = System.currentTimeMillis(),
        val lastActivity: Long = System.currentTimeMillis(),
        var isActive: Boolean = true
    )
    
    /**
     * Channel priority levels.
     */
    enum class ChannelPriority(val level: Int) {
        LOW(1),
        NORMAL(2),
        HIGH(3),
        CRITICAL(4);
        
        companion object {
            fun fromLevel(level: Int): ChannelPriority {
                return values().find { it.level == level } ?: NORMAL
            }
        }
    }
    
    /**
     * Resource allocation for a channel.
     */
    data class ResourceAllocation(
        val maxBandwidthPercent: Int = 25,
        val maxFilters: Int = 5,
        val maxBufferSize: Int = 4096,
        val timeSliceMs: Int = 100,
        val canPreempt: Boolean = false,
        val exclusiveProtocol: Boolean = false
    )
    
    /**
     * Channel statistics and status.
     */
    data class ChannelStatus(
        val channelId: Int,
        val protocolId: Int,
        val protocolName: String,
        val priority: ChannelPriority,
        val isActive: Boolean,
        val messagesSent: Long,
        val messagesReceived: Long,
        val errors: Long,
        val uptime: Long,
        val lastActivity: Long,
        val resourceUsage: ResourceUsage
    )
    
    /**
     * Resource usage information.
     */
    data class ResourceUsage(
        val bandwidthUsagePercent: Float,
        val activeFilters: Int,
        val bufferUsagePercent: Float,
        val cpuUsagePercent: Float
    )
    
    // ═══════════════════════════════════════════════════════════════════════
    // CHANNEL CREATION AND MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Creates a new managed channel with the specified configuration.
     */
    suspend fun createChannel(
        protocolId: Int,
        flags: Int = 0,
        baudRate: Int = J2534Constants.getDefaultDataRate(protocolId),
        priority: ChannelPriority = ChannelPriority.NORMAL,
        resources: ResourceAllocation = ResourceAllocation()
    ): Result<Int> = mutex.withLock {
        
        // Check if we can create more channels
        if (channels.size >= maxConcurrentChannels) {
            return Result.Error(ConnectionException(
                "Maximum number of channels ($maxConcurrentChannels) reached"
            ))
        }
        
        // Check for protocol conflicts
        val conflictResult = checkProtocolConflicts(protocolId, resources.exclusiveProtocol)
        if (conflictResult is Result.Error) {
            return conflictResult
        }
        
        // Create the channel
        val channel = J2534Channel.create(j2534Interface, device, protocolId, flags, baudRate)
        val connectResult = channel.connect()
        if (connectResult is Result.Error) {
            return connectResult
        }
        
        val channelId = connectResult.data
        
        // Create protocol manager
        val protocol = J2534Protocol.create(channel, protocolId)
        
        // Create managed channel
        val managedChannel = ManagedChannel(
            channel = channel,
            protocol = protocol,
            priority = priority,
            resources = resources
        )
        
        // Store channel information
        channels[channelId] = managedChannel
        protocolManagers[channelId] = protocol
        channelPriorities[channelId] = priority
        resourceAllocations[channelId] = resources
        
        // Apply resource constraints
        applyResourceConstraints(channelId, resources)
        
        return Result.Success(channelId)
    }
    
    /**
     * Closes and removes a managed channel.
     */
    suspend fun closeChannel(channelId: Int): Result<Unit> = mutex.withLock {
        val managedChannel = channels[channelId]
            ?: return Result.Error(ConnectionException("Channel $channelId not found"))
        
        // Disconnect the channel
        val disconnectResult = managedChannel.channel.disconnect()
        if (disconnectResult is Result.Error) {
            return disconnectResult
        }
        
        // Remove from all tracking maps
        channels.remove(channelId)
        protocolManagers.remove(channelId)
        channelPriorities.remove(channelId)
        resourceAllocations.remove(channelId)
        
        return Result.Success(Unit)
    }
    
    /**
     * Gets a channel by ID.
     */
    fun getChannel(channelId: Int): J2534Channel? {
        return channels[channelId]?.channel
    }
    
    /**
     * Gets a protocol manager by channel ID.
     */
    fun getProtocolManager(channelId: Int): J2534Protocol? {
        return protocolManagers[channelId]
    }
    
    /**
     * Gets all active channel IDs.
     */
    fun getActiveChannels(): List<Int> {
        return channels.filterValues { it.isActive }.keys.toList()
    }
    
    /**
     * Gets channels by protocol.
     */
    fun getChannelsByProtocol(protocolId: Int): List<Int> {
        return channels.filterValues { 
            it.channel.protocolId == protocolId && it.isActive 
        }.keys.toList()
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // CHANNEL ISOLATION AND INTERFERENCE PREVENTION
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Ensures channels don't interfere with each other.
     */
    private suspend fun checkProtocolConflicts(
        protocolId: Int,
        exclusiveProtocol: Boolean
    ): Result<Unit> {
        
        // Check for exclusive protocol conflicts
        if (exclusiveProtocol) {
            val existingChannels = channels.values.filter { it.isActive }
            if (existingChannels.isNotEmpty()) {
                return Result.Error(ConnectionException(
                    "Cannot create exclusive protocol channel while other channels are active"
                ))
            }
        }
        
        // Check if any existing channel requires exclusive access
        val exclusiveChannels = channels.values.filter { 
            it.isActive && it.resources.exclusiveProtocol 
        }
        if (exclusiveChannels.isNotEmpty()) {
            return Result.Error(ConnectionException(
                "Cannot create channel while exclusive protocol channel is active"
            ))
        }
        
        // Check for protocol-specific conflicts
        when (protocolId) {
            J2534Constants.CAN, J2534Constants.ISO15765 -> {
                // CAN protocols can coexist but need bandwidth management
                val canChannels = getChannelsByProtocol(J2534Constants.CAN) + 
                                 getChannelsByProtocol(J2534Constants.ISO15765)
                if (canChannels.size >= 4) { // Limit CAN channels
                    return Result.Error(ConnectionException(
                        "Maximum number of CAN channels (4) reached"
                    ))
                }
            }
            J2534Constants.ISO14230, J2534Constants.ISO9141 -> {
                // ISO protocols typically require exclusive access to K-line
                val isoChannels = getChannelsByProtocol(J2534Constants.ISO14230) + 
                                 getChannelsByProtocol(J2534Constants.ISO9141)
                if (isoChannels.isNotEmpty()) {
                    return Result.Error(ConnectionException(
                        "Only one ISO protocol channel allowed at a time"
                    ))
                }
            }
            J2534Constants.J1850VPW, J2534Constants.J1850PWM -> {
                // J1850 protocols require exclusive access
                val j1850Channels = getChannelsByProtocol(J2534Constants.J1850VPW) + 
                                   getChannelsByProtocol(J2534Constants.J1850PWM)
                if (j1850Channels.isNotEmpty()) {
                    return Result.Error(ConnectionException(
                        "Only one J1850 protocol channel allowed at a time"
                    ))
                }
            }
        }
        
        return Result.Success(Unit)
    }
    
    /**
     * Applies resource constraints to prevent interference.
     */
    private suspend fun applyResourceConstraints(
        channelId: Int,
        resources: ResourceAllocation
    ) {
        val channel = channels[channelId]?.channel ?: return
        
        // Configure buffer sizes
        val configs = mutableListOf<J2534Config>()
        
        // Set bandwidth limitations (protocol-specific)
        when (channel.protocolId) {
            J2534Constants.CAN, J2534Constants.ISO15765 -> {
                // Adjust CAN timing based on bandwidth allocation
                val samplePoint = 80 - (resources.maxBandwidthPercent / 10)
                configs.add(J2534Config(J2534Constants.BIT_SAMPLE_POINT, samplePoint.coerceIn(60, 90)))
            }
            J2534Constants.ISO14230, J2534Constants.ISO9141 -> {
                // Adjust ISO timing based on resource allocation
                val p2Max = 50 + (100 - resources.maxBandwidthPercent)
                configs.add(J2534Config(J2534Constants.P2_MAX, p2Max.coerceIn(50, 200)))
            }
        }
        
        if (configs.isNotEmpty()) {
            channel.setConfiguration(configs)
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // CHANNEL PRIORITY AND RESOURCE ALLOCATION
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Sets the priority of a channel.
     */
    suspend fun setChannelPriority(
        channelId: Int,
        priority: ChannelPriority
    ): Result<Unit> = mutex.withLock {
        val managedChannel = channels[channelId]
            ?: return Result.Error(ConnectionException("Channel $channelId not found"))
        
        channelPriorities[channelId] = priority
        channels[channelId] = managedChannel.copy(priority = priority)
        
        // Rebalance resources based on new priority
        rebalanceResources()
        
        return Result.Success(Unit)
    }
    
    /**
     * Updates resource allocation for a channel.
     */
    suspend fun updateResourceAllocation(
        channelId: Int,
        resources: ResourceAllocation
    ): Result<Unit> = mutex.withLock {
        val managedChannel = channels[channelId]
            ?: return Result.Error(ConnectionException("Channel $channelId not found"))
        
        // Validate resource allocation
        val validationResult = validateResourceAllocation(resources)
        if (validationResult is Result.Error) {
            return validationResult
        }
        
        resourceAllocations[channelId] = resources
        channels[channelId] = managedChannel.copy(resources = resources)
        
        // Apply new resource constraints
        applyResourceConstraints(channelId, resources)
        
        // Rebalance resources
        rebalanceResources()
        
        return Result.Success(Unit)
    }
    
    /**
     * Rebalances resources across all active channels.
     */
    private suspend fun rebalanceResources() {
        val activeChannels = channels.values.filter { it.isActive }
        if (activeChannels.isEmpty()) return
        
        // Sort channels by priority (highest first)
        val sortedChannels = activeChannels.sortedByDescending { it.priority.level }
        
        // Calculate available bandwidth
        var availableBandwidth = 100
        val bandwidthAllocations = mutableMapOf<Int, Int>()
        
        // Allocate bandwidth based on priority
        for (managedChannel in sortedChannels) {
            val channelId = managedChannel.channel.getChannelId() ?: continue
            val requestedBandwidth = managedChannel.resources.maxBandwidthPercent
            val allocatedBandwidth = minOf(requestedBandwidth, availableBandwidth)
            
            bandwidthAllocations[channelId] = allocatedBandwidth
            availableBandwidth -= allocatedBandwidth
            
            if (availableBandwidth <= 0) break
        }
        
        // Apply bandwidth allocations
        for ((channelId, bandwidth) in bandwidthAllocations) {
            val managedChannel = channels[channelId] ?: continue
            val updatedResources = managedChannel.resources.copy(maxBandwidthPercent = bandwidth)
            resourceAllocations[channelId] = updatedResources
            applyResourceConstraints(channelId, updatedResources)
        }
    }
    
    /**
     * Validates resource allocation parameters.
     */
    private fun validateResourceAllocation(resources: ResourceAllocation): Result<Unit> {
        if (resources.maxBandwidthPercent < 1 || resources.maxBandwidthPercent > 100) {
            return Result.Error(ConnectionException("Bandwidth percent must be between 1 and 100"))
        }
        
        if (resources.maxFilters < 1 || resources.maxFilters > J2534Constants.MAX_FILTERS) {
            return Result.Error(ConnectionException(
                "Max filters must be between 1 and ${J2534Constants.MAX_FILTERS}"
            ))
        }
        
        if (resources.maxBufferSize < 512 || resources.maxBufferSize > 65536) {
            return Result.Error(ConnectionException("Buffer size must be between 512 and 65536 bytes"))
        }
        
        if (resources.timeSliceMs < 10 || resources.timeSliceMs > 1000) {
            return Result.Error(ConnectionException("Time slice must be between 10 and 1000 ms"))
        }
        
        return Result.Success(Unit)
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // CHANNEL MONITORING AND STATISTICS
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Gets the status of all channels.
     */
    fun getAllChannelStatus(): List<ChannelStatus> {
        return channels.mapNotNull { (channelId, managedChannel) ->
            val stats = managedChannel.channel.getStatistics()
            val resourceUsage = calculateResourceUsage(channelId)
            
            ChannelStatus(
                channelId = channelId,
                protocolId = managedChannel.channel.protocolId,
                protocolName = managedChannel.protocol.getProtocolName(),
                priority = managedChannel.priority,
                isActive = managedChannel.isActive,
                messagesSent = stats.messagesSent,
                messagesReceived = stats.messagesReceived,
                errors = stats.errors,
                uptime = stats.uptime,
                lastActivity = stats.lastActivity,
                resourceUsage = resourceUsage
            )
        }
    }
    
    /**
     * Gets the status of a specific channel.
     */
    fun getChannelStatus(channelId: Int): ChannelStatus? {
        val managedChannel = channels[channelId] ?: return null
        val stats = managedChannel.channel.getStatistics()
        val resourceUsage = calculateResourceUsage(channelId)
        
        return ChannelStatus(
            channelId = channelId,
            protocolId = managedChannel.channel.protocolId,
            protocolName = managedChannel.protocol.getProtocolName(),
            priority = managedChannel.priority,
            isActive = managedChannel.isActive,
            messagesSent = stats.messagesSent,
            messagesReceived = stats.messagesReceived,
            errors = stats.errors,
            uptime = stats.uptime,
            lastActivity = stats.lastActivity,
            resourceUsage = resourceUsage
        )
    }
    
    /**
     * Calculates resource usage for a channel.
     */
    private fun calculateResourceUsage(channelId: Int): ResourceUsage {
        val managedChannel = channels[channelId] ?: return ResourceUsage(0f, 0, 0f, 0f)
        val allocation = resourceAllocations[channelId] ?: return ResourceUsage(0f, 0, 0f, 0f)
        
        // Calculate bandwidth usage (simplified)
        val stats = managedChannel.channel.getStatistics()
        val bandwidthUsage = if (stats.uptime > 0) {
            ((stats.messagesSent + stats.messagesReceived).toFloat() / stats.uptime) * 100
        } else 0f
        
        // Get active filters
        val activeFilters = managedChannel.protocol.getActiveFilters().size
        
        // Calculate buffer usage (simplified)
        val bufferUsage = (activeFilters.toFloat() / allocation.maxFilters) * 100
        
        // Calculate CPU usage (simplified estimation)
        val cpuUsage = minOf(bandwidthUsage / 10, 100f)
        
        return ResourceUsage(
            bandwidthUsagePercent = bandwidthUsage.coerceIn(0f, 100f),
            activeFilters = activeFilters,
            bufferUsagePercent = bufferUsage.coerceIn(0f, 100f),
            cpuUsagePercent = cpuUsage.coerceIn(0f, 100f)
        )
    }
    
    /**
     * Suspends a channel temporarily.
     */
    suspend fun suspendChannel(channelId: Int): Result<Unit> = mutex.withLock {
        val managedChannel = channels[channelId]
            ?: return Result.Error(ConnectionException("Channel $channelId not found"))
        
        channels[channelId] = managedChannel.copy(isActive = false)
        
        // Clear buffers and filters
        managedChannel.channel.clearTxBuffer()
        managedChannel.channel.clearRxBuffer()
        managedChannel.protocol.clearProtocolState()
        
        // Rebalance resources
        rebalanceResources()
        
        return Result.Success(Unit)
    }
    
    /**
     * Resumes a suspended channel.
     */
    suspend fun resumeChannel(channelId: Int): Result<Unit> = mutex.withLock {
        val managedChannel = channels[channelId]
            ?: return Result.Error(ConnectionException("Channel $channelId not found"))
        
        if (managedChannel.isActive) {
            return Result.Success(Unit) // Already active
        }
        
        // Check for conflicts before resuming
        val conflictResult = checkProtocolConflicts(
            managedChannel.channel.protocolId,
            managedChannel.resources.exclusiveProtocol
        )
        if (conflictResult is Result.Error) {
            return conflictResult
        }
        
        channels[channelId] = managedChannel.copy(isActive = true)
        
        // Rebalance resources
        rebalanceResources()
        
        return Result.Success(Unit)
    }
    
    /**
     * Closes all channels and releases resources.
     */
    suspend fun closeAllChannels(): Result<Unit> = mutex.withLock {
        val channelIds = channels.keys.toList()
        var lastError: Throwable? = null
        
        for (channelId in channelIds) {
            val result = closeChannel(channelId)
            if (result is Result.Error) {
                lastError = result.exception
            }
        }
        
        return if (lastError != null) {
            Result.Error(lastError)
        } else {
            Result.Success(Unit)
        }
    }
    
    /**
     * Sets the maximum number of concurrent channels.
     */
    fun setMaxConcurrentChannels(maxChannels: Int) {
        maxConcurrentChannels = maxChannels.coerceIn(1, J2534Constants.MAX_CHANNELS)
    }
    
    /**
     * Gets the maximum number of concurrent channels.
     */
    fun getMaxConcurrentChannels(): Int = maxConcurrentChannels
    
    /**
     * Gets the number of active channels.
     */
    fun getActiveChannelCount(): Int = channels.values.count { it.isActive }
    
    companion object {
        
        /**
         * Creates a new channel manager.
         */
        fun create(
            j2534Interface: J2534Interface,
            device: J2534Device
        ): J2534ChannelManager {
            return J2534ChannelManager(j2534Interface, device)
        }
        
        /**
         * Default resource allocation for different priority levels.
         */
        fun getDefaultResourceAllocation(priority: ChannelPriority): ResourceAllocation {
            return when (priority) {
                ChannelPriority.CRITICAL -> ResourceAllocation(
                    maxBandwidthPercent = 50,
                    maxFilters = 10,
                    maxBufferSize = 8192,
                    timeSliceMs = 50,
                    canPreempt = true,
                    exclusiveProtocol = false
                )
                ChannelPriority.HIGH -> ResourceAllocation(
                    maxBandwidthPercent = 35,
                    maxFilters = 7,
                    maxBufferSize = 6144,
                    timeSliceMs = 75,
                    canPreempt = false,
                    exclusiveProtocol = false
                )
                ChannelPriority.NORMAL -> ResourceAllocation(
                    maxBandwidthPercent = 25,
                    maxFilters = 5,
                    maxBufferSize = 4096,
                    timeSliceMs = 100,
                    canPreempt = false,
                    exclusiveProtocol = false
                )
                ChannelPriority.LOW -> ResourceAllocation(
                    maxBandwidthPercent = 15,
                    maxFilters = 3,
                    maxBufferSize = 2048,
                    timeSliceMs = 200,
                    canPreempt = false,
                    exclusiveProtocol = false
                )
            }
        }
    }
}