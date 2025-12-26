/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 */

package com.spacetec.obd.scanner.j2534

import com.spacetec.core.common.exceptions.ConnectionException
import com.spacetec.core.common.exceptions.CommunicationException
import com.spacetec.core.common.exceptions.TimeoutException
import com.spacetec.core.common.result.Result
import com.spacetec.core.domain.models.scanner.ScannerConnectionType
import com.spacetec.j2534.*
import com.spacetec.j2534.constants.J2534Constants
import com.spacetec.obd.scanner.core.BaseScannerConnection
import com.spacetec.obd.scanner.core.ConnectionConfig
import com.spacetec.obd.scanner.core.ConnectionInfo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * J2534 Pass-Thru scanner connection implementation.
 *
 * This class provides a ScannerConnection implementation for J2534 Pass-Thru
 * devices, enabling professional diagnostic tool compatibility with precise
 * timing and multi-channel protocol support.
 *
 * ## Features
 * - Native J2534 API integration
 * - Multi-channel protocol support
 * - Professional diagnostic tool compatibility
 * - Precise timing requirements
 * - Message filtering and flow control
 * - Resource management and channel isolation
 *
 * ## Usage Example
 *
 * ```kotlin
 * val connection = J2534Connection(context)
 * 
 * // Connect to device
 * val result = connection.connect("MyJ2534Device")
 * if (result is Result.Success) {
 *     // Configure for ISO 15765 (CAN with TP)
 *     connection.configureProtocol(J2534Constants.ISO15765, "BMW")
 *     
 *     // Send diagnostic message
 *     val response = connection.sendAndReceive("22 F1 90") // Read VIN
 *     println("VIN Response: ${response.getOrNull()}")
 *     
 *     connection.disconnect()
 * }
 * ```
 *
 * @author SpaceTec Development Team
 * @since 1.0.0
 */
class J2534Connection(
    dispatcher: CoroutineDispatcher = Dispatchers.IO
) : BaseScannerConnection(dispatcher) {
    
    override val connectionType: ScannerConnectionType = ScannerConnectionType.J2534
    
    // J2534 components
    private var j2534Interface: J2534Interface? = null
    private var j2534Device: J2534Device? = null
    private var channelManager: J2534ChannelManager? = null
    private var currentChannel: J2534Channel? = null
    private var currentProtocol: J2534Protocol? = null
    
    // Connection state
    private var deviceName: String? = null
    private var protocolId: Int = J2534Constants.ISO15765 // Default to ISO 15765
    private var currentChannelId: Int? = null
    
    // ═══════════════════════════════════════════════════════════════════════
    // CONNECTION MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════════
    
    override suspend fun doConnect(
        address: String,
        config: ConnectionConfig
    ): ConnectionInfo = withContext(dispatcher) {
        
        deviceName = address
        
        // Initialize J2534 interface
        val interface = J2534Interface.create()
        val initResult = interface.initialize()
        if (initResult is Result.Error) {
            throw ConnectionException("Failed to initialize J2534 interface", initResult.exception)
        }
        j2534Interface = interface
        
        // Create and open device
        val device = J2534Device.create(interface, address)
        val openResult = device.open()
        if (openResult is Result.Error) {
            throw ConnectionException("Failed to open J2534 device: $address", openResult.exception)
        }
        j2534Device = device
        
        // Create channel manager
        val manager = J2534ChannelManager.create(interface, device)
        channelManager = manager
        
        // Create default channel (ISO 15765)
        val channelResult = createDefaultChannel(config)
        if (channelResult is Result.Error) {
            throw ConnectionException("Failed to create default channel", channelResult.exception)
        }
        currentChannelId = channelResult.data
        
        return@withContext ConnectionInfo(
            remoteAddress = address,
            connectionType = connectionType,
            mtu = 4095 // J2534 max message size minus header
        )
    }
    
    override suspend fun doDisconnect(graceful: Boolean) = withContext(dispatcher) {
        try {
            // Close all channels
            channelManager?.closeAllChannels()
            
            // Close device
            j2534Device?.close()
            
            // Shutdown interface
            j2534Interface?.shutdown()
            
        } finally {
            // Clean up references
            currentChannel = null
            currentProtocol = null
            currentChannelId = null
            channelManager = null
            j2534Device = null
            j2534Interface = null
        }
    }
    
    /**
     * Creates the default communication channel.
     */
    private suspend fun createDefaultChannel(config: ConnectionConfig): Result<Int> {
        val manager = channelManager ?: return Result.Error(ConnectionException("Channel manager not initialized"))
        
        // Determine protocol from config or use default
        val protocol = protocolId
        val baudRate = when (protocol) {
            J2534Constants.CAN, J2534Constants.ISO15765 -> 500000
            J2534Constants.ISO14230, J2534Constants.ISO9141 -> 10400
            J2534Constants.J1850VPW -> 10400
            J2534Constants.J1850PWM -> 41600
            else -> J2534Constants.getDefaultDataRate(protocol)
        }
        
        // Create channel with normal priority
        val channelResult = manager.createChannel(
            protocolId = protocol,
            baudRate = baudRate,
            priority = J2534ChannelManager.ChannelPriority.NORMAL
        )
        
        if (channelResult is Result.Success) {
            val channelId = channelResult.data
            currentChannel = manager.getChannel(channelId)
            currentProtocol = manager.getProtocolManager(channelId)
        }
        
        return channelResult
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // DATA TRANSFER
    // ═══════════════════════════════════════════════════════════════════════
    
    override suspend fun doWrite(data: ByteArray): Int = withContext(dispatcher) {
        val channel = currentChannel ?: throw ConnectionException("No active channel")
        
        // Create J2534 message
        val message = J2534Message.createTxMessage(protocolId, data)
        val messages = listOf(message)
        
        // Send message
        val result = channel.writeMessages(messages, config.writeTimeout)
        when (result) {
            is Result.Success -> return@withContext result.data
            is Result.Error -> throw result.exception
            is Result.Loading -> throw CommunicationException("Write operation still in progress")
        }
    }
    
    override suspend fun doRead(buffer: ByteArray, timeout: Long): Int = withContext(dispatcher) {
        val channel = currentChannel ?: throw ConnectionException("No active channel")
        
        // Read messages
        val result = channel.readMessages(maxMessages = 1, timeout = timeout)
        when (result) {
            is Result.Success -> {
                val messages = result.data
                if (messages.isEmpty()) {
                    return@withContext 0
                }
                
                val message = messages.first()
                val messageData = message.messageData
                val bytesToCopy = minOf(messageData.size, buffer.size)
                
                System.arraycopy(messageData, 0, buffer, 0, bytesToCopy)
                return@withContext bytesToCopy
            }
            is Result.Error -> {
                if (result.exception is TimeoutException) {
                    return@withContext 0 // Timeout is not an error for read
                }
                throw result.exception
            }
            is Result.Loading -> throw CommunicationException("Read operation still in progress")
        }
    }
    
    override suspend fun doAvailable(): Int = withContext(dispatcher) {
        val channel = currentChannel ?: return@withContext 0
        
        // Try to read without blocking
        val result = channel.readMessages(maxMessages = 10, timeout = 1)
        return@withContext when (result) {
            is Result.Success -> result.data.sumOf { it.messageData.size }
            else -> 0
        }
    }
    
    override suspend fun doClearBuffers() = withContext(dispatcher) {
        val channel = currentChannel ?: return@withContext
        
        // Clear J2534 buffers
        channel.clearTxBuffer()
        channel.clearRxBuffer()
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // J2534-SPECIFIC FUNCTIONALITY
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Configures the connection for a specific protocol and vehicle.
     */
    suspend fun configureProtocol(
        protocolId: Int,
        vehicleMake: String = "",
        customConfig: Map<Int, Int> = emptyMap()
    ): Result<Unit> = connectionMutex.withLock {
        
        if (!isConnected) {
            return Result.Error(ConnectionException("Not connected"))
        }
        
        // If protocol changed, create new channel
        if (protocolId != this.protocolId) {
            val manager = channelManager ?: return Result.Error(ConnectionException("Channel manager not available"))
            
            // Close current channel
            currentChannelId?.let { channelId ->
                manager.closeChannel(channelId)
            }
            
            // Create new channel for the protocol
            val channelResult = manager.createChannel(
                protocolId = protocolId,
                baudRate = J2534Constants.getDefaultDataRate(protocolId),
                priority = J2534ChannelManager.ChannelPriority.NORMAL
            )
            
            if (channelResult is Result.Error) {
                return channelResult
            }
            
            this.protocolId = protocolId
            currentChannelId = channelResult.data
            currentChannel = manager.getChannel(channelResult.data)
            currentProtocol = manager.getProtocolManager(channelResult.data)
        }
        
        // Configure protocol for vehicle
        val protocol = currentProtocol ?: return Result.Error(ConnectionException("Protocol manager not available"))
        return protocol.configureForVehicle(vehicleMake, customConfig = customConfig)
    }
    
    /**
     * Sets up diagnostic filtering for ECU communication.
     */
    suspend fun setupDiagnosticFiltering(
        ecuAddress: Int,
        testerAddress: Int = 0x7E0,
        use29Bit: Boolean = false
    ): Result<List<Int>> {
        val protocol = currentProtocol ?: return Result.Error(ConnectionException("Protocol manager not available"))
        return protocol.setupDiagnosticFiltering(ecuAddress, testerAddress, use29Bit)
    }
    
    /**
     * Configures flow control for multi-frame messages.
     */
    suspend fun configureFlowControl(
        blockSize: Int = 0,
        separationTime: Int = 0,
        waitFrameMax: Int = 0
    ): Result<Unit> {
        val protocol = currentProtocol ?: return Result.Error(ConnectionException("Protocol manager not available"))
        return protocol.configureFlowControl(blockSize, separationTime, waitFrameMax)
    }
    
    /**
     * Performs fast initialization for ISO protocols.
     */
    suspend fun fastInit(initData: ByteArray): Result<Unit> {
        val channel = currentChannel ?: return Result.Error(ConnectionException("No active channel"))
        return channel.fastInit(initData)
    }
    
    /**
     * Performs five baud initialization for ISO protocols.
     */
    suspend fun fiveBaudInit(address: Int): Result<Unit> {
        val channel = currentChannel ?: return Result.Error(ConnectionException("No active channel"))
        return channel.fiveBaudInit(address)
    }
    
    /**
     * Sends a raw J2534 message.
     */
    suspend fun sendJ2534Message(message: J2534Message): Result<Unit> {
        val channel = currentChannel ?: return Result.Error(ConnectionException("No active channel"))
        
        val result = channel.writeMessages(listOf(message), config.writeTimeout)
        return when (result) {
            is Result.Success -> Result.Success(Unit)
            is Result.Error -> result
            is Result.Loading -> Result.Error(CommunicationException("Send operation still in progress"))
        }
    }
    
    /**
     * Receives J2534 messages.
     */
    suspend fun receiveJ2534Messages(
        maxMessages: Int = 10,
        timeout: Long = config.readTimeout
    ): Result<List<J2534Message>> {
        val channel = currentChannel ?: return Result.Error(ConnectionException("No active channel"))
        return channel.readMessages(maxMessages, timeout)
    }
    
    /**
     * Gets the current protocol ID.
     */
    fun getCurrentProtocolId(): Int = protocolId
    
    /**
     * Gets the current protocol name.
     */
    fun getCurrentProtocolName(): String = J2534Constants.protocolIdToString(protocolId)
    
    /**
     * Gets channel status information.
     */
    fun getChannelStatus(): J2534ChannelManager.ChannelStatus? {
        val channelId = currentChannelId ?: return null
        return channelManager?.getChannelStatus(channelId)
    }
    
    /**
     * Gets device information.
     */
    suspend fun getDeviceInfo(): Result<J2534Device.DeviceInfo> {
        val device = j2534Device ?: return Result.Error(ConnectionException("Device not available"))
        return device.getDeviceInfo()
    }
    
    /**
     * Creates an additional channel for multi-protocol communication.
     */
    suspend fun createAdditionalChannel(
        protocolId: Int,
        priority: J2534ChannelManager.ChannelPriority = J2534ChannelManager.ChannelPriority.NORMAL
    ): Result<Int> {
        val manager = channelManager ?: return Result.Error(ConnectionException("Channel manager not available"))
        
        return manager.createChannel(
            protocolId = protocolId,
            baudRate = J2534Constants.getDefaultDataRate(protocolId),
            priority = priority
        )
    }
    
    /**
     * Switches to a different channel.
     */
    suspend fun switchToChannel(channelId: Int): Result<Unit> = connectionMutex.withLock {
        val manager = channelManager ?: return Result.Error(ConnectionException("Channel manager not available"))
        
        val channel = manager.getChannel(channelId)
            ?: return Result.Error(ConnectionException("Channel $channelId not found"))
        
        val protocol = manager.getProtocolManager(channelId)
            ?: return Result.Error(ConnectionException("Protocol manager for channel $channelId not found"))
        
        currentChannelId = channelId
        currentChannel = channel
        currentProtocol = protocol
        protocolId = channel.protocolId
        
        return Result.Success(Unit)
    }
    
    /**
     * Gets all active channels.
     */
    fun getActiveChannels(): List<Int> {
        return channelManager?.getActiveChannels() ?: emptyList()
    }
    
    /**
     * Gets all channel status information.
     */
    fun getAllChannelStatus(): List<J2534ChannelManager.ChannelStatus> {
        return channelManager?.getAllChannelStatus() ?: emptyList()
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // UTILITY METHODS
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Converts hex string to J2534 message.
     */
    fun createMessageFromHex(hexData: String, flags: Int = 0): J2534Message {
        return J2534Message.fromHexString(protocolId, hexData, flags)
    }
    
    /**
     * Sends a hex command and receives response.
     */
    suspend fun sendHexCommand(
        hexCommand: String,
        timeout: Long = config.readTimeout
    ): Result<String> {
        val message = createMessageFromHex(hexCommand)
        
        // Send message
        val sendResult = sendJ2534Message(message)
        if (sendResult is Result.Error) {
            return sendResult
        }
        
        // Wait for response
        delay(50) // Small delay for response
        
        val receiveResult = receiveJ2534Messages(1, timeout)
        return when (receiveResult) {
            is Result.Success -> {
                val messages = receiveResult.data
                if (messages.isNotEmpty()) {
                    val responseData = messages.first().messageData
                    val hexResponse = responseData.joinToString(" ") { "%02X".format(it) }
                    Result.Success(hexResponse)
                } else {
                    Result.Error(TimeoutException("No response received"))
                }
            }
            is Result.Error -> receiveResult
            is Result.Loading -> Result.Error(CommunicationException("Receive operation still in progress"))
        }
    }
    
    companion object {
        
        /**
         * Creates a new J2534 connection instance.
         */
        fun create(): J2534Connection {
            return J2534Connection()
        }
        
        /**
         * Discovers available J2534 devices.
         */
        suspend fun discoverDevices(): Result<List<String>> {
            return J2534Device.discoverDevices()
        }
        
        /**
         * Gets supported protocols for a device.
         */
        suspend fun getSupportedProtocols(deviceName: String): Result<List<Int>> {
            val interface = J2534Interface.create()
            val initResult = interface.initialize()
            if (initResult is Result.Error) {
                return initResult
            }
            
            return try {
                val device = J2534Device.create(interface, deviceName)
                val openResult = device.open()
                if (openResult is Result.Error) {
                    return openResult
                }
                
                val infoResult = device.getDeviceInfo()
                device.close()
                interface.shutdown()
                
                when (infoResult) {
                    is Result.Success -> Result.Success(infoResult.data.supportedProtocols)
                    is Result.Error -> infoResult
                    is Result.Loading -> Result.Error(ConnectionException("Device info query still in progress"))
                }
            } catch (e: Exception) {
                interface.shutdown()
                Result.Error(ConnectionException("Failed to get supported protocols", e))
            }
        }
    }
}