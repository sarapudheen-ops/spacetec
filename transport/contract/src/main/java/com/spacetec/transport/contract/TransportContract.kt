package com.spacetec.transport.contract

import com.spacetec.obd.core.common.result.AppResult
import kotlinx.coroutines.flow.Flow

/**
 * Interface representing a scanner connection that can be used by protocols.
 * This breaks the circular dependency between scanner and protocol modules.
 */
interface ScannerConnection {
    val isConnected: Boolean
    val connectionState: Flow<ConnectionState>
    
    suspend fun openConnection(): AppResult<Unit>
    suspend fun closeConnection(): AppResult<Unit>
    suspend fun writeBytes(data: ByteArray): AppResult<Unit>
    suspend fun readBytes(timeout: Long): AppResult<ByteArray>
    fun observeBytes(): Flow<ByteArray>
}

/**
 * Connection state enumeration
 */
sealed interface ConnectionState {
    object Connecting : ConnectionState
    object Connected : ConnectionState
    object Disconnected : ConnectionState
    data class Error(val message: String, val isRecoverable: Boolean = false) : ConnectionState
}

/**
 * Protocol types enumeration
 */
enum class ProtocolType(
    val protocolName: String,
    val description: String,
    val protocolFamily: String = "OBD"
) {
    AUTO("Auto", "Automatic protocol detection"),
    ISO_15765_4_CAN_11BIT_500K("ISO-TP CAN 11bit 500k", "CAN with ISO-TP transport, 11-bit IDs, 500k baud", "CAN"),
    ISO_15765_4_CAN_29BIT_500K("ISO-TP CAN 29bit 500k", "CAN with ISO-TP transport, 29-bit IDs, 500k baud", "CAN"),
    ISO_14230_4_KWP_FAST("KWP2000 Fast", "Keyword Protocol 2000 Fast Init", "K-Line"),
    ISO_9141_2("ISO 9141-2", "ISO 9141-2 with slow init", "K-Line"),
    SAE_J1850_PWM("J1850 PWM", "SAE J1850 Pulse Width Modulation", "J1850"),
    SAE_J1850_VPW("J1850 VPW", "SAE J1850 Variable Pulse Width", "J1850"),
    ISO_15765_4_CAN_11BIT_250K("ISO-TP CAN 11bit 250k", "CAN with ISO-TP transport, 11-bit IDs, 250k baud", "CAN"),
    ISO_15765_4_CAN_29BIT_250K("ISO-TP CAN 29bit 250k", "CAN with ISO-TP transport, 29-bit IDs, 250k baud", "CAN"),
    UDS_ON_CAN_11BIT_500K("UDS on CAN 11bit 500k", "UDS protocol over CAN, 11-bit IDs, 500k baud", "UDS"),
    UDS_ON_CAN_29BIT_500K("UDS on CAN 29bit 500k", "UDS protocol over CAN, 29-bit IDs, 500k baud", "UDS");

    companion object {
        fun fromName(name: String): ProtocolType? = entries.find { it.protocolName == name }
        fun fromString(value: String): ProtocolType? = entries.find { it.protocolName.equals(value, ignoreCase = true) }
    }
}

/**
 * Interface for protocol configuration
 */
interface ProtocolConfig {
    companion object {
        val DEFAULT: ProtocolConfig = object : ProtocolConfig {}
    }
}

/**
 * Interface for protocols that can be created by scanners
 */
interface Protocol {
    val protocolType: ProtocolType
    
    suspend fun initialize(connection: ScannerConnection, config: ProtocolConfig = ProtocolConfig.DEFAULT)
    suspend fun sendCommand(command: String): AppResult<String>
    suspend fun sendBytes(data: ByteArray): AppResult<ByteArray>
    suspend fun reset()
    suspend fun shutdown()
}

/**
 * Factory interface for creating protocols
 */
interface ProtocolFactory {
    fun createProtocol(protocolType: ProtocolType): Protocol
}