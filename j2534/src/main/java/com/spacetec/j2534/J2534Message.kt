/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 *
 * This file is part of the SpaceTec professional automotive diagnostic system.
 */

package com.spacetec.j2534

import com.spacetec.j2534.constants.J2534Constants

/**
 * Represents a J2534 message for communication with vehicle ECUs.
 *
 * This class encapsulates the data and metadata required for J2534 message
 * transmission and reception, including protocol-specific flags and timing.
 *
 * @property protocolId The protocol ID for this message
 * @property rxStatus Receive status flags
 * @property txFlags Transmit flags
 * @property timestamp Message timestamp in microseconds
 * @property dataSize Size of the data array
 * @property extraDataIndex Index of extra data in the data array
 * @property data Message data bytes
 *
 * @author SpaceTec Development Team
 * @since 1.0.0
 */
data class J2534Message(
    val protocolId: Int = 0,
    val rxStatus: Int = 0,
    val txFlags: Int = 0,
    val timestamp: Long = 0L,
    val dataSize: Int = 0,
    val extraDataIndex: Int = 0,
    val data: ByteArray = ByteArray(J2534Constants.MAX_MESSAGE_LENGTH)
) {
    
    /**
     * Gets the actual message data (excluding extra data).
     */
    val messageData: ByteArray
        get() = if (dataSize > 0) data.copyOf(dataSize) else byteArrayOf()
    
    /**
     * Gets the extra data portion of the message.
     */
    val extraData: ByteArray
        get() = if (extraDataIndex > 0 && extraDataIndex < data.size) {
            data.copyOfRange(extraDataIndex, data.size)
        } else {
            byteArrayOf()
        }
    
    /**
     * Checks if this is a CAN message.
     */
    val isCanMessage: Boolean
        get() = J2534Constants.isCanProtocol(protocolId)
    
    /**
     * Checks if this is an ISO message.
     */
    val isIsoMessage: Boolean
        get() = J2534Constants.isIsoProtocol(protocolId)
    
    /**
     * Checks if this message uses 29-bit CAN ID.
     */
    val is29BitCan: Boolean
        get() = (txFlags and J2534Constants.CAN_29BIT_ID_FLAG) != 0
    
    /**
     * Checks if this message has TX done flag.
     */
    val isTxDone: Boolean
        get() = (rxStatus and J2534Constants.TX_DONE) != 0
    
    /**
     * Checks if this message has indication flag.
     */
    val hasIndication: Boolean
        get() = (rxStatus and J2534Constants.INDICATION) != 0
    
    /**
     * Checks if this message has break flag.
     */
    val hasBreak: Boolean
        get() = (rxStatus and J2534Constants.BREAK) != 0
    
    /**
     * Creates a copy of this message with new data.
     */
    fun withData(newData: ByteArray): J2534Message {
        val newMessage = copy(
            dataSize = newData.size,
            data = ByteArray(J2534Constants.MAX_MESSAGE_LENGTH)
        )
        System.arraycopy(newData, 0, newMessage.data, 0, minOf(newData.size, J2534Constants.MAX_MESSAGE_LENGTH))
        return newMessage
    }
    
    /**
     * Creates a copy of this message with new flags.
     */
    fun withFlags(newTxFlags: Int): J2534Message {
        return copy(txFlags = newTxFlags)
    }
    
    /**
     * Creates a copy of this message with new protocol.
     */
    fun withProtocol(newProtocolId: Int): J2534Message {
        return copy(protocolId = newProtocolId)
    }
    
    /**
     * Converts this message to a hex string representation.
     */
    fun toHexString(): String {
        val dataHex = messageData.joinToString(" ") { "%02X".format(it) }
        return "Protocol: ${J2534Constants.protocolIdToString(protocolId)}, " +
                "Data: [$dataHex], " +
                "Size: $dataSize, " +
                "Flags: 0x${txFlags.toString(16).uppercase()}"
    }
    
    /**
     * Validates the message data and parameters.
     */
    fun validate(): Boolean {
        return dataSize >= 0 && 
               dataSize <= J2534Constants.MAX_MESSAGE_LENGTH &&
               extraDataIndex >= 0 &&
               extraDataIndex <= data.size &&
               protocolId > 0
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as J2534Message
        
        if (protocolId != other.protocolId) return false
        if (rxStatus != other.rxStatus) return false
        if (txFlags != other.txFlags) return false
        if (timestamp != other.timestamp) return false
        if (dataSize != other.dataSize) return false
        if (extraDataIndex != other.extraDataIndex) return false
        if (!data.contentEquals(other.data)) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = protocolId
        result = 31 * result + rxStatus
        result = 31 * result + txFlags
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + dataSize
        result = 31 * result + extraDataIndex
        result = 31 * result + data.contentHashCode()
        return result
    }
    
    companion object {
        
        /**
         * Creates a new message for transmission.
         */
        fun createTxMessage(
            protocolId: Int,
            data: ByteArray,
            flags: Int = 0
        ): J2534Message {
            return J2534Message(
                protocolId = protocolId,
                txFlags = flags,
                dataSize = data.size,
                timestamp = System.currentTimeMillis() * 1000 // Convert to microseconds
            ).withData(data)
        }
        
        /**
         * Creates a new CAN message.
         */
        fun createCanMessage(
            data: ByteArray,
            use29Bit: Boolean = false
        ): J2534Message {
            val flags = if (use29Bit) J2534Constants.CAN_29BIT_ID_FLAG else 0
            return createTxMessage(J2534Constants.CAN, data, flags)
        }
        
        /**
         * Creates a new ISO 15765 message.
         */
        fun createIso15765Message(
            data: ByteArray,
            use29Bit: Boolean = false,
            framePad: Boolean = false
        ): J2534Message {
            var flags = if (use29Bit) J2534Constants.CAN_29BIT_ID_FLAG else 0
            if (framePad) flags = flags or J2534Constants.ISO15765_FRAME_PAD
            return createTxMessage(J2534Constants.ISO15765, data, flags)
        }
        
        /**
         * Creates a new ISO 14230 (KWP2000) message.
         */
        fun createKwp2000Message(data: ByteArray): J2534Message {
            return createTxMessage(J2534Constants.ISO14230, data)
        }
        
        /**
         * Creates a new ISO 9141 message.
         */
        fun createIso9141Message(
            data: ByteArray,
            noChecksum: Boolean = false
        ): J2534Message {
            val flags = if (noChecksum) J2534Constants.ISO9141_NO_CHECKSUM else 0
            return createTxMessage(J2534Constants.ISO9141, data, flags)
        }
        
        /**
         * Creates a new J1850 VPW message.
         */
        fun createJ1850VpwMessage(data: ByteArray): J2534Message {
            return createTxMessage(J2534Constants.J1850VPW, data)
        }
        
        /**
         * Creates a new J1850 PWM message.
         */
        fun createJ1850PwmMessage(data: ByteArray): J2534Message {
            return createTxMessage(J2534Constants.J1850PWM, data)
        }
        
        /**
         * Creates an empty message for receiving data.
         */
        fun createRxMessage(protocolId: Int): J2534Message {
            return J2534Message(
                protocolId = protocolId,
                dataSize = 0
            )
        }
        
        /**
         * Parses a hex string into message data.
         */
        fun parseHexData(hexString: String): ByteArray {
            val cleanHex = hexString.replace("\\s+".toRegex(), "").replace("0x", "")
            require(cleanHex.length % 2 == 0) { "Hex string must have even length" }
            
            return cleanHex.chunked(2)
                .map { it.toInt(16).toByte() }
                .toByteArray()
        }
        
        /**
         * Creates a message from hex string data.
         */
        fun fromHexString(protocolId: Int, hexData: String, flags: Int = 0): J2534Message {
            val data = parseHexData(hexData)
            return createTxMessage(protocolId, data, flags)
        }
    }
}

/**
 * Represents a J2534 message filter configuration.
 *
 * @property filterType Type of filter (PASS_FILTER, BLOCK_FILTER, FLOW_CONTROL_FILTER)
 * @property maskMsg Mask message for filtering
 * @property patternMsg Pattern message for filtering
 * @property flowControlMsg Flow control message (for flow control filters)
 */
data class J2534MessageFilter(
    val filterType: Int,
    val maskMsg: J2534Message,
    val patternMsg: J2534Message,
    val flowControlMsg: J2534Message? = null
) {
    
    /**
     * Checks if this is a pass filter.
     */
    val isPassFilter: Boolean
        get() = filterType == J2534Constants.PASS_FILTER
    
    /**
     * Checks if this is a block filter.
     */
    val isBlockFilter: Boolean
        get() = filterType == J2534Constants.BLOCK_FILTER
    
    /**
     * Checks if this is a flow control filter.
     */
    val isFlowControlFilter: Boolean
        get() = filterType == J2534Constants.FLOW_CONTROL_FILTER
    
    /**
     * Validates the filter configuration.
     */
    fun validate(): Boolean {
        return when (filterType) {
            J2534Constants.PASS_FILTER, J2534Constants.BLOCK_FILTER -> {
                maskMsg.validate() && patternMsg.validate()
            }
            J2534Constants.FLOW_CONTROL_FILTER -> {
                maskMsg.validate() && patternMsg.validate() && flowControlMsg?.validate() == true
            }
            else -> false
        }
    }
    
    /**
     * Checks if a message matches this filter.
     */
    fun matches(message: J2534Message): Boolean {
        if (message.protocolId != maskMsg.protocolId) return false
        
        val messageData = message.messageData
        val maskData = maskMsg.messageData
        val patternData = patternMsg.messageData
        
        if (messageData.size != maskData.size || messageData.size != patternData.size) {
            return false
        }
        
        for (i in messageData.indices) {
            val maskedMessage = messageData[i].toInt() and maskData[i].toInt()
            val maskedPattern = patternData[i].toInt() and maskData[i].toInt()
            if (maskedMessage != maskedPattern) {
                return false
            }
        }
        
        return true
    }
    
    companion object {
        
        /**
         * Creates a pass filter.
         */
        fun createPassFilter(
            protocolId: Int,
            mask: ByteArray,
            pattern: ByteArray
        ): J2534MessageFilter {
            return J2534MessageFilter(
                filterType = J2534Constants.PASS_FILTER,
                maskMsg = J2534Message.createTxMessage(protocolId, mask),
                patternMsg = J2534Message.createTxMessage(protocolId, pattern)
            )
        }
        
        /**
         * Creates a block filter.
         */
        fun createBlockFilter(
            protocolId: Int,
            mask: ByteArray,
            pattern: ByteArray
        ): J2534MessageFilter {
            return J2534MessageFilter(
                filterType = J2534Constants.BLOCK_FILTER,
                maskMsg = J2534Message.createTxMessage(protocolId, mask),
                patternMsg = J2534Message.createTxMessage(protocolId, pattern)
            )
        }
        
        /**
         * Creates a flow control filter.
         */
        fun createFlowControlFilter(
            protocolId: Int,
            mask: ByteArray,
            pattern: ByteArray,
            flowControl: ByteArray
        ): J2534MessageFilter {
            return J2534MessageFilter(
                filterType = J2534Constants.FLOW_CONTROL_FILTER,
                maskMsg = J2534Message.createTxMessage(protocolId, mask),
                patternMsg = J2534Message.createTxMessage(protocolId, pattern),
                flowControlMsg = J2534Message.createTxMessage(protocolId, flowControl)
            )
        }
    }
}

/**
 * Represents a J2534 configuration parameter.
 *
 * @property parameterId Parameter ID (from J2534Constants)
 * @property value Parameter value
 */
data class J2534Config(
    val parameterId: Int,
    val value: Int
) {
    
    /**
     * Gets the parameter name as a string.
     */
    val parameterName: String
        get() = when (parameterId) {
            J2534Constants.DATA_RATE -> "DATA_RATE"
            J2534Constants.LOOPBACK -> "LOOPBACK"
            J2534Constants.NODE_ADDRESS -> "NODE_ADDRESS"
            J2534Constants.NETWORK_LINE -> "NETWORK_LINE"
            J2534Constants.P1_MIN -> "P1_MIN"
            J2534Constants.P1_MAX -> "P1_MAX"
            J2534Constants.P2_MIN -> "P2_MIN"
            J2534Constants.P2_MAX -> "P2_MAX"
            J2534Constants.P3_MIN -> "P3_MIN"
            J2534Constants.P3_MAX -> "P3_MAX"
            J2534Constants.P4_MIN -> "P4_MIN"
            J2534Constants.P4_MAX -> "P4_MAX"
            J2534Constants.W1 -> "W1"
            J2534Constants.W2 -> "W2"
            J2534Constants.W3 -> "W3"
            J2534Constants.W4 -> "W4"
            J2534Constants.W5 -> "W5"
            J2534Constants.TIDLE -> "TIDLE"
            J2534Constants.TINIL -> "TINIL"
            J2534Constants.TWUP -> "TWUP"
            J2534Constants.PARITY -> "PARITY"
            J2534Constants.BIT_SAMPLE_POINT -> "BIT_SAMPLE_POINT"
            J2534Constants.SYNC_JUMP_WIDTH -> "SYNC_JUMP_WIDTH"
            J2534Constants.W0 -> "W0"
            J2534Constants.T1_MAX -> "T1_MAX"
            J2534Constants.T2_MAX -> "T2_MAX"
            J2534Constants.T4_MAX -> "T4_MAX"
            J2534Constants.T5_MAX -> "T5_MAX"
            J2534Constants.ISO15765_BS -> "ISO15765_BS"
            J2534Constants.ISO15765_STMIN -> "ISO15765_STMIN"
            J2534Constants.BS_TX -> "BS_TX"
            J2534Constants.STMIN_TX -> "STMIN_TX"
            J2534Constants.T3_MAX -> "T3_MAX"
            J2534Constants.ISO15765_WFT_MAX -> "ISO15765_WFT_MAX"
            else -> "UNKNOWN_PARAM_${parameterId}"
        }
    
    override fun toString(): String {
        return "$parameterName = $value"
    }
    
    companion object {
        
        /**
         * Creates a data rate configuration.
         */
        fun dataRate(rate: Int) = J2534Config(J2534Constants.DATA_RATE, rate)
        
        /**
         * Creates a loopback configuration.
         */
        fun loopback(enabled: Boolean) = J2534Config(J2534Constants.LOOPBACK, if (enabled) 1 else 0)
        
        /**
         * Creates a node address configuration.
         */
        fun nodeAddress(address: Int) = J2534Config(J2534Constants.NODE_ADDRESS, address)
        
        /**
         * Creates default configurations for a protocol.
         */
        fun defaultsForProtocol(protocolId: Int): List<J2534Config> {
            val configs = mutableListOf<J2534Config>()
            
            // Add data rate
            configs.add(dataRate(J2534Constants.getDefaultDataRate(protocolId)))
            
            // Add protocol-specific defaults
            when (protocolId) {
                J2534Constants.CAN, J2534Constants.ISO15765 -> {
                    configs.add(J2534Config(J2534Constants.BIT_SAMPLE_POINT, 80))
                    configs.add(J2534Config(J2534Constants.SYNC_JUMP_WIDTH, 15))
                    if (protocolId == J2534Constants.ISO15765) {
                        configs.add(J2534Config(J2534Constants.ISO15765_BS, 0))
                        configs.add(J2534Config(J2534Constants.ISO15765_STMIN, 0))
                    }
                }
                J2534Constants.ISO9141, J2534Constants.ISO14230 -> {
                    configs.add(J2534Config(J2534Constants.P1_MIN, 0))
                    configs.add(J2534Config(J2534Constants.P1_MAX, 20))
                    configs.add(J2534Config(J2534Constants.P2_MIN, 25))
                    configs.add(J2534Config(J2534Constants.P2_MAX, 50))
                    configs.add(J2534Config(J2534Constants.P3_MIN, 55))
                    configs.add(J2534Config(J2534Constants.P3_MAX, 5000))
                    configs.add(J2534Config(J2534Constants.P4_MIN, 5))
                    configs.add(J2534Config(J2534Constants.P4_MAX, 20))
                }
                J2534Constants.J1850VPW, J2534Constants.J1850PWM -> {
                    configs.add(J2534Config(J2534Constants.T1_MAX, 5000))
                    configs.add(J2534Config(J2534Constants.T2_MAX, 10000))
                    configs.add(J2534Config(J2534Constants.T4_MAX, 10000))
                    configs.add(J2534Config(J2534Constants.T5_MAX, 10000))
                }
            }
            
            return configs
        }
    }
}