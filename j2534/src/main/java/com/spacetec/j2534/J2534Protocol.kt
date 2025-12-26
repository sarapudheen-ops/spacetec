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

/**
 * Manages J2534 protocol configuration and timing requirements.
 *
 * This class handles protocol-specific configuration, message filtering,
 * flow control, and precise timing requirements for J2534 operations.
 */
class J2534Protocol(
    private val channel: J2534Channel,
    val protocolId: Int
) {
    
    private val mutex = Mutex()
    private val protocolConfig = ConcurrentHashMap<Int, Int>()
    private val activeFilters = ConcurrentHashMap<Int, FilterInfo>()
    private val flowControlFilters = ConcurrentHashMap<Int, FlowControlInfo>()
    
    /**
     * Information about an active filter.
     */
    private data class FilterInfo(
        val filterId: Int,
        val filterType: Int,
        val mask: ByteArray,
        val pattern: ByteArray,
        val createdAt: Long = System.currentTimeMillis()
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            
            other as FilterInfo
            
            if (filterId != other.filterId) return false
            if (filterType != other.filterType) return false
            if (!mask.contentEquals(other.mask)) return false
            if (!pattern.contentEquals(other.pattern)) return false
            
            return true
        }
        
        override fun hashCode(): Int {
            var result = filterId
            result = 31 * result + filterType
            result = 31 * result + mask.contentHashCode()
            result = 31 * result + pattern.contentHashCode()
            return result
        }
    }
    
    /**
     * Information about flow control configuration.
     */
    private data class FlowControlInfo(
        val filterId: Int,
        val sourceAddress: Int,
        val targetAddress: Int,
        val blockSize: Int = 0,
        val separationTime: Int = 0,
        val isActive: Boolean = true
    )
    
    // ═══════════════════════════════════════════════════════════════════════
    // PROTOCOL CONFIGURATION
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Configures the protocol with vehicle-specific requirements.
     */
    suspend fun configureForVehicle(
        vehicleProtocol: String,
        baudRate: Int? = null,
        customConfig: Map<Int, Int> = emptyMap()
    ): Result<Unit> = mutex.withLock {
        
        val configs = mutableListOf<J2534Config>()
        
        // Set baud rate if specified
        baudRate?.let { rate ->
            configs.add(J2534Config.dataRate(rate))
        }
        
        // Add protocol-specific configurations
        when (protocolId) {
            J2534Constants.CAN, J2534Constants.ISO15765 -> {
                configureCanProtocol(configs, vehicleProtocol)
            }
            J2534Constants.ISO14230 -> {
                configureKwp2000Protocol(configs, vehicleProtocol)
            }
            J2534Constants.ISO9141 -> {
                configureIso9141Protocol(configs, vehicleProtocol)
            }
            J2534Constants.J1850VPW, J2534Constants.J1850PWM -> {
                configureJ1850Protocol(configs, vehicleProtocol)
            }
        }
        
        // Add custom configurations
        customConfig.forEach { (param, value) ->
            configs.add(J2534Config(param, value))
        }
        
        // Apply configurations
        val result = channel.setConfiguration(configs)
        if (result is Result.Success) {
            configs.forEach { config ->
                protocolConfig[config.parameterId] = config.value
            }
        }
        
        return result
    }
    
    /**
     * Configures CAN protocol parameters.
     */
    private fun configureCanProtocol(configs: MutableList<J2534Config>, vehicleProtocol: String) {
        // Standard CAN configuration
        configs.add(J2534Config(J2534Constants.BIT_SAMPLE_POINT, 80))
        configs.add(J2534Config(J2534Constants.SYNC_JUMP_WIDTH, 15))
        
        // ISO 15765 specific configuration
        if (protocolId == J2534Constants.ISO15765) {
            configs.add(J2534Config(J2534Constants.ISO15765_BS, 0)) // Block size (0 = no flow control)
            configs.add(J2534Config(J2534Constants.ISO15765_STMIN, 0)) // Separation time minimum
            configs.add(J2534Config(J2534Constants.ISO15765_WFT_MAX, 0)) // Wait frame transmit max
            
            // Vehicle-specific adjustments
            when (vehicleProtocol.uppercase()) {
                "BMW", "MINI" -> {
                    configs.add(J2534Config(J2534Constants.ISO15765_BS, 8))
                    configs.add(J2534Config(J2534Constants.ISO15765_STMIN, 20))
                }
                "MERCEDES", "SMART" -> {
                    configs.add(J2534Config(J2534Constants.ISO15765_BS, 16))
                    configs.add(J2534Config(J2534Constants.ISO15765_STMIN, 10))
                }
                "AUDI", "VW", "PORSCHE" -> {
                    configs.add(J2534Config(J2534Constants.ISO15765_BS, 0))
                    configs.add(J2534Config(J2534Constants.ISO15765_STMIN, 5))
                }
            }
        }
    }
    
    /**
     * Configures KWP2000 protocol parameters.
     */
    private fun configureKwp2000Protocol(configs: MutableList<J2534Config>, vehicleProtocol: String) {
        // Standard KWP2000 timing based on ISO 14230-4 specifications
        configs.add(J2534Config(J2534Constants.P1_MIN, 0))      // Min time between end of request and start of response
        configs.add(J2534Config(J2534Constants.P1_MAX, 20))     // Max time between end of request and start of response
        configs.add(J2534Config(J2534Constants.P2_MIN, 25))     // Min time between end of tester present and response
        configs.add(J2534Config(J2534Constants.P2_MAX, 50))     // Max time between end of tester present and response
        configs.add(J2534Config(J2534Constants.P3_MIN, 55))     // Min time between end of response and next request
        configs.add(J2534Config(J2534Constants.P3_MAX, 5000))   // Max time between end of response and next request
        configs.add(J2534Config(J2534Constants.P4_MIN, 5))      // Min time between end of response data and next request
        configs.add(J2534Config(J2534Constants.P4_MAX, 20))     // Max time between end of response data and next request
        
        // Vehicle-specific timing adjustments
        when (vehicleProtocol.uppercase()) {
            "BMW", "MINI" -> {
                configs.add(J2534Config(J2534Constants.P2_MAX, 100))
                configs.add(J2534Config(J2534Constants.P3_MAX, 10000))
            }
            "MERCEDES", "SMART" -> {
                configs.add(J2534Config(J2534Constants.P2_MIN, 50))
                configs.add(J2534Config(J2534Constants.P2_MAX, 150))
            }
            "TOYOTA", "LEXUS" -> {
                configs.add(J2534Config(J2534Constants.P3_MIN, 100))
                configs.add(J2534Config(J2534Constants.P4_MAX, 50))
            }
        }
    }
    
    /**
     * Configures ISO 9141 protocol parameters.
     */
    private fun configureIso9141Protocol(configs: MutableList<J2534Config>, vehicleProtocol: String) {
        // Standard ISO 9141 timing based on ISO 9141-2 specifications
        configs.add(J2534Config(J2534Constants.P1_MIN, 0))      // Min time between end of request and start of response
        configs.add(J2534Config(J2534Constants.P1_MAX, 20))     // Max time between end of request and start of response
        configs.add(J2534Config(J2534Constants.P2_MIN, 25))     // Min time between end of tester present and response
        configs.add(J2534Config(J2534Constants.P2_MAX, 50))     // Max time between end of tester present and response
        configs.add(J2534Config(J2534Constants.P3_MIN, 55))     // Min time between end of response and next request
        configs.add(J2534Config(J2534Constants.P3_MAX, 5000))   // Max time between end of response and next request
        configs.add(J2534Config(J2534Constants.P4_MIN, 5))      // Min time between end of response data and next request
        configs.add(J2534Config(J2534Constants.P4_MAX, 20))     // Max time between end of response data and next request
        
        // ISO 9141 specific timing based on ISO 9141-2 standard
        configs.add(J2534Config(J2534Constants.W1, 300))        // Max time from K-line rising edge to first byte of response
        configs.add(J2534Config(J2534Constants.W2, 20))         // Max time between end of response and next request
        configs.add(J2534Config(J2534Constants.W3, 25))         // Min time between end of response and next request
        configs.add(J2534Config(J2534Constants.W4, 50))
        configs.add(J2534Config(J2534Constants.W5, 300))
    }
    
    /**
     * Configures J1850 protocol parameters.
     */
    private fun configureJ1850Protocol(configs: MutableList<J2534Config>, vehicleProtocol: String) {
        // J1850 timing parameters
        configs.add(J2534Config(J2534Constants.T1_MAX, 5000))
        configs.add(J2534Config(J2534Constants.T2_MAX, 10000))
        configs.add(J2534Config(J2534Constants.T4_MAX, 10000))
        configs.add(J2534Config(J2534Constants.T5_MAX, 10000))
        
        // Vehicle-specific adjustments
        when (vehicleProtocol.uppercase()) {
            "GM", "CHEVROLET", "CADILLAC", "BUICK" -> {
                configs.add(J2534Config(J2534Constants.T1_MAX, 3000))
                configs.add(J2534Config(J2534Constants.T2_MAX, 8000))
            }
            "FORD", "LINCOLN", "MERCURY" -> {
                configs.add(J2534Config(J2534Constants.T4_MAX, 15000))
                configs.add(J2534Config(J2534Constants.T5_MAX, 15000))
            }
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // MESSAGE FILTERING
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Sets up message filtering for diagnostic communication.
     */
    suspend fun setupDiagnosticFiltering(
        ecuAddress: Int,
        testerAddress: Int = 0x7E0,
        use29Bit: Boolean = false
    ): Result<List<Int>> = mutex.withLock {
        
        val filterIds = mutableListOf<Int>()
        
        when (protocolId) {
            J2534Constants.CAN, J2534Constants.ISO15765 -> {
                val canFilters = setupCanFiltering(ecuAddress, testerAddress, use29Bit)
                if (canFilters is Result.Success) {
                    filterIds.addAll(canFilters.data)
                } else {
                    return canFilters
                }
            }
            J2534Constants.ISO14230, J2534Constants.ISO9141 -> {
                val isoFilters = setupIsoFiltering(ecuAddress, testerAddress)
                if (isoFilters is Result.Success) {
                    filterIds.addAll(isoFilters.data)
                } else {
                    return isoFilters
                }
            }
            J2534Constants.J1850VPW, J2534Constants.J1850PWM -> {
                val j1850Filters = setupJ1850Filtering(ecuAddress, testerAddress)
                if (j1850Filters is Result.Success) {
                    filterIds.addAll(j1850Filters.data)
                } else {
                    return j1850Filters
                }
            }
        }
        
        return Result.Success(filterIds)
    }
    
    /**
     * Sets up CAN message filtering.
     */
    private suspend fun setupCanFiltering(
        ecuAddress: Int,
        testerAddress: Int,
        use29Bit: Boolean
    ): Result<List<Int>> {
        
        val filterIds = mutableListOf<Int>()
        
        if (use29Bit) {
            // 29-bit CAN filtering
            val mask = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
            val pattern = byteArrayOf(
                ((ecuAddress shr 24) and 0xFF).toByte(),
                ((ecuAddress shr 16) and 0xFF).toByte(),
                ((ecuAddress shr 8) and 0xFF).toByte(),
                (ecuAddress and 0xFF).toByte()
            )
            
            val filter = J2534MessageFilter.createPassFilter(protocolId, mask, pattern)
            val result = channel.startMessageFilter(filter)
            if (result is Result.Success) {
                filterIds.add(result.data)
                activeFilters[result.data] = FilterInfo(result.data, J2534Constants.PASS_FILTER, mask, pattern)
            } else {
                return result
            }
        } else {
            // 11-bit CAN filtering
            val mask = byteArrayOf(0xFF.toByte(), 0xF0.toByte())
            val pattern = byteArrayOf(
                ((ecuAddress shr 3) and 0xFF).toByte(),
                ((ecuAddress shl 5) and 0xF0).toByte()
            )
            
            val filter = J2534MessageFilter.createPassFilter(protocolId, mask, pattern)
            val result = channel.startMessageFilter(filter)
            if (result is Result.Success) {
                filterIds.add(result.data)
                activeFilters[result.data] = FilterInfo(result.data, J2534Constants.PASS_FILTER, mask, pattern)
            } else {
                return result
            }
        }
        
        // Set up flow control if ISO 15765
        if (protocolId == J2534Constants.ISO15765) {
            val flowControlResult = setupIso15765FlowControl(ecuAddress, testerAddress, use29Bit)
            if (flowControlResult is Result.Success) {
                filterIds.addAll(flowControlResult.data)
            }
        }
        
        return Result.Success(filterIds)
    }
    
    /**
     * Sets up ISO 15765 flow control filtering.
     */
    private suspend fun setupIso15765FlowControl(
        ecuAddress: Int,
        testerAddress: Int,
        use29Bit: Boolean
    ): Result<List<Int>> {
        
        val filterIds = mutableListOf<Int>()
        
        // Flow control message setup
        val maskData = if (use29Bit) {
            byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
        } else {
            byteArrayOf(0xFF.toByte(), 0xF0.toByte())
        }
        
        val patternData = if (use29Bit) {
            byteArrayOf(
                ((testerAddress shr 24) and 0xFF).toByte(),
                ((testerAddress shr 16) and 0xFF).toByte(),
                ((testerAddress shr 8) and 0xFF).toByte(),
                (testerAddress and 0xFF).toByte()
            )
        } else {
            byteArrayOf(
                ((testerAddress shr 3) and 0xFF).toByte(),
                ((testerAddress shl 5) and 0xF0).toByte()
            )
        }
        
        // Flow control response (Continue To Send)
        val flowControlData = byteArrayOf(0x30, 0x00, 0x00) // CTS with no delay
        
        val flowControlFilter = J2534MessageFilter.createFlowControlFilter(
            protocolId, maskData, patternData, flowControlData
        )
        
        val result = channel.startMessageFilter(flowControlFilter)
        if (result is Result.Success) {
            filterIds.add(result.data)
            flowControlFilters[result.data] = FlowControlInfo(
                result.data, ecuAddress, testerAddress
            )
        }
        
        return Result.Success(filterIds)
    }
    
    /**
     * Sets up ISO protocol filtering.
     */
    private suspend fun setupIsoFiltering(ecuAddress: Int, testerAddress: Int): Result<List<Int>> {
        val filterIds = mutableListOf<Int>()
        
        // Simple address-based filtering for ISO protocols
        val mask = byteArrayOf(0xFF.toByte())
        val pattern = byteArrayOf((ecuAddress and 0xFF).toByte())
        
        val filter = J2534MessageFilter.createPassFilter(protocolId, mask, pattern)
        val result = channel.startMessageFilter(filter)
        if (result is Result.Success) {
            filterIds.add(result.data)
            activeFilters[result.data] = FilterInfo(result.data, J2534Constants.PASS_FILTER, mask, pattern)
        }
        
        return Result.Success(listOf(result.data))
    }
    
    /**
     * Sets up J1850 protocol filtering.
     */
    private suspend fun setupJ1850Filtering(ecuAddress: Int, testerAddress: Int): Result<List<Int>> {
        val filterIds = mutableListOf<Int>()
        
        // J1850 header-based filtering
        val mask = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
        val pattern = byteArrayOf(
            0x68.toByte(), // Priority and addressing mode
            (ecuAddress and 0xFF).toByte(),
            (testerAddress and 0xFF).toByte()
        )
        
        val filter = J2534MessageFilter.createPassFilter(protocolId, mask, pattern)
        val result = channel.startMessageFilter(filter)
        if (result is Result.Success) {
            filterIds.add(result.data)
            activeFilters[result.data] = FilterInfo(result.data, J2534Constants.PASS_FILTER, mask, pattern)
        }
        
        return Result.Success(listOf(result.data))
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // FLOW CONTROL
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Configures flow control parameters for multi-frame messages.
     */
    suspend fun configureFlowControl(
        blockSize: Int = 0,
        separationTime: Int = 0,
        waitFrameMax: Int = 0
    ): Result<Unit> {
        
        if (protocolId != J2534Constants.ISO15765) {
            return Result.Error(ConnectionException("Flow control only supported for ISO 15765"))
        }
        
        val configs = listOf(
            J2534Config(J2534Constants.ISO15765_BS, blockSize),
            J2534Config(J2534Constants.ISO15765_STMIN, separationTime),
            J2534Config(J2534Constants.ISO15765_WFT_MAX, waitFrameMax)
        )
        
        val result = channel.setConfiguration(configs)
        if (result is Result.Success) {
            configs.forEach { config ->
                protocolConfig[config.parameterId] = config.value
            }
        }
        
        return result
    }
    
    /**
     * Enables or disables automatic flow control responses.
     */
    suspend fun setAutoFlowControl(enabled: Boolean): Result<Unit> {
        if (protocolId != J2534Constants.ISO15765) {
            return Result.Error(ConnectionException("Auto flow control only supported for ISO 15765"))
        }
        
        // Update flow control filters
        flowControlFilters.values.forEach { flowControl ->
            flowControlFilters[flowControl.filterId] = flowControl.copy(isActive = enabled)
        }
        
        return Result.Success(Unit)
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // TIMING MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Enforces precise timing requirements for J2534 operations.
     */
    suspend fun enforcePreciseTiming(
        operationType: TimingOperation,
        customTiming: Map<String, Int> = emptyMap()
    ): Result<Unit> {
        
        val timingConfigs = mutableListOf<J2534Config>()
        
        when (operationType) {
            TimingOperation.DIAGNOSTIC_SESSION -> {
                when (protocolId) {
                    J2534Constants.ISO14230, J2534Constants.ISO9141 -> {
                        timingConfigs.add(J2534Config(J2534Constants.P2_MAX, customTiming["P2_MAX"] ?: 50))
                        timingConfigs.add(J2534Config(J2534Constants.P3_MIN, customTiming["P3_MIN"] ?: 55))
                    }
                    J2534Constants.ISO15765 -> {
                        timingConfigs.add(J2534Config(J2534Constants.ISO15765_STMIN, customTiming["STMIN"] ?: 0))
                    }
                }
            }
            TimingOperation.ECU_RESET -> {
                when (protocolId) {
                    J2534Constants.ISO14230, J2534Constants.ISO9141 -> {
                        timingConfigs.add(J2534Config(J2534Constants.P3_MAX, customTiming["P3_MAX"] ?: 5000))
                    }
                }
            }
            TimingOperation.SECURITY_ACCESS -> {
                when (protocolId) {
                    J2534Constants.ISO14230, J2534Constants.ISO9141 -> {
                        timingConfigs.add(J2534Config(J2534Constants.P2_MAX, customTiming["P2_MAX"] ?: 5000))
                    }
                }
            }
            TimingOperation.PROGRAMMING -> {
                when (protocolId) {
                    J2534Constants.ISO14230, J2534Constants.ISO9141 -> {
                        timingConfigs.add(J2534Config(J2534Constants.P2_MAX, customTiming["P2_MAX"] ?: 10000))
                        timingConfigs.add(J2534Config(J2534Constants.P3_MAX, customTiming["P3_MAX"] ?: 10000))
                    }
                    J2534Constants.ISO15765 -> {
                        timingConfigs.add(J2534Config(J2534Constants.ISO15765_BS, customTiming["BS"] ?: 0))
                        timingConfigs.add(J2534Config(J2534Constants.ISO15765_STMIN, customTiming["STMIN"] ?: 20))
                    }
                }
            }
        }
        
        if (timingConfigs.isNotEmpty()) {
            val result = channel.setConfiguration(timingConfigs)
            if (result is Result.Success) {
                timingConfigs.forEach { config ->
                    protocolConfig[config.parameterId] = config.value
                }
            }
            return result
        }
        
        return Result.Success(Unit)
    }
    
    /**
     * Gets the current protocol configuration.
     */
    fun getCurrentConfiguration(): Map<Int, Int> = protocolConfig.toMap()
    
    /**
     * Gets active filters information.
     */
    fun getActiveFilters(): Map<Int, FilterInfo> = activeFilters.toMap()
    
    /**
     * Gets flow control information.
     */
    fun getFlowControlInfo(): Map<Int, FlowControlInfo> = flowControlFilters.toMap()
    
    /**
     * Gets the protocol name.
     */
    fun getProtocolName(): String = J2534Constants.protocolIdToString(protocolId)
    
    /**
     * Clears all protocol-specific filters and configuration.
     */
    suspend fun clearProtocolState(): Result<Unit> = mutex.withLock {
        // Clear all filters
        val result = channel.clearMessageFilters()
        if (result is Result.Success) {
            activeFilters.clear()
            flowControlFilters.clear()
            protocolConfig.clear()
        }
        return result
    }
    
    /**
     * Timing operation types for precise timing enforcement.
     */
    enum class TimingOperation {
        DIAGNOSTIC_SESSION,
        ECU_RESET,
        SECURITY_ACCESS,
        PROGRAMMING
    }
    
    companion object {
        
        /**
         * Creates a protocol manager for the specified channel.
         */
        fun create(channel: J2534Channel, protocolId: Int): J2534Protocol {
            return J2534Protocol(channel, protocolId)
        }
        
        /**
         * Gets recommended configuration for a specific vehicle make.
         */
        fun getRecommendedConfig(
            protocolId: Int,
            vehicleMake: String
        ): Map<Int, Int> {
            val config = mutableMapOf<Int, Int>()
            
            when (protocolId) {
                J2534Constants.ISO15765 -> {
                    when (vehicleMake.uppercase()) {
                        "BMW", "MINI" -> {
                            config[J2534Constants.ISO15765_BS] = 8
                            config[J2534Constants.ISO15765_STMIN] = 20
                        }
                        "MERCEDES", "SMART" -> {
                            config[J2534Constants.ISO15765_BS] = 16
                            config[J2534Constants.ISO15765_STMIN] = 10
                        }
                        "AUDI", "VW", "PORSCHE" -> {
                            config[J2534Constants.ISO15765_BS] = 0
                            config[J2534Constants.ISO15765_STMIN] = 5
                        }
                        "TOYOTA", "LEXUS" -> {
                            config[J2534Constants.ISO15765_BS] = 0
                            config[J2534Constants.ISO15765_STMIN] = 0
                        }
                    }
                }
                J2534Constants.ISO14230 -> {
                    when (vehicleMake.uppercase()) {
                        "BMW", "MINI" -> {
                            config[J2534Constants.P2_MAX] = 100
                            config[J2534Constants.P3_MAX] = 10000
                        }
                        "MERCEDES", "SMART" -> {
                            config[J2534Constants.P2_MIN] = 50
                            config[J2534Constants.P2_MAX] = 150
                        }
                    }
                }
            }
            
            return config
        }
    }
}