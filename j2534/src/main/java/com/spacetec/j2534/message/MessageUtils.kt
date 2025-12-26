package com.spacetec.j2534.message

import com.spacetec.j2534.J2534Message
import com.spacetec.j2534.J2534Protocols

/**
 * Message builder for creating J2534 messages
 */
class J2534MessageBuilder {
    private var protocolID: Long = 0L
    private var rxStatus: Long = 0L
    private var txFlags: Long = 0L
    private var timestamp: Long = 0L
    private var data: ByteArray = byteArrayOf()
    private var extraDataIndex: Int = 0

    fun protocolID(protocolID: Long) = apply { this.protocolID = protocolID }
    fun rxStatus(rxStatus: Long) = apply { this.rxStatus = rxStatus }
    fun txFlags(txFlags: Long) = apply { this.txFlags = txFlags }
    fun timestamp(timestamp: Long) = apply { this.timestamp = timestamp }
    fun data(data: ByteArray) = apply { this.data = data }
    fun extraDataIndex(extraDataIndex: Int) = apply { this.extraDataIndex = extraDataIndex }

    fun build(): J2534Message {
        return J2534Message(
            protocolID = protocolID,
            rxStatus = rxStatus,
            txFlags = txFlags,
            timestamp = timestamp,
            data = data,
            extraDataIndex = extraDataIndex
        )
    }
}

/**
 * Utility class for message parsing and formatting
 */
object MessageUtils {
    /**
     * Parse CAN message from raw data
     */
    fun parseCanMessage(message: J2534Message): CanMessage? {
        if (message.protocolID != J2534Protocols.CAN) {
            return null
        }

        val data = message.data
        if (data.size < 2) return null

        val isExtendedId = message.txFlags and 0x0100L != 0L // CAN_29BIT_ID
        val canId: Long
        val payload: ByteArray

        if (isExtendedId) {
            if (data.size < 4) return null
            canId = ((data[0].toLong() and 0xFF) shl 24) or
                    ((data[1].toLong() and 0xFF) shl 16) or
                    ((data[2].toLong() and 0xFF) shl 8) or
                    (data[3].toLong() and 0xFF)
            payload = data.sliceArray(4 until data.size)
        } else {
            canId = ((data[0].toLong() and 0xFF) shl 8) or (data[1].toLong() and 0xFF)
            payload = data.sliceArray(2 until data.size)
        }

        return CanMessage(canId, payload, message.txFlags, message.timestamp)
    }

    /**
     * Format CAN message to J2534 format
     */
    fun formatCanMessage(canId: Long, data: ByteArray, txFlags: Long = 0L): J2534Message {
        val isExtendedId = canId > 0x7FF
        val canData = if (isExtendedId) {
            byteArrayOf(
                ((canId shr 24) and 0xFF).toByte(),
                ((canId shr 16) and 0xFF).toByte(),
                ((canId shr 8) and 0xFF).toByte(),
                (canId and 0xFF).toByte()
            ) + data
        } else {
            byteArrayOf(
                ((canId shr 8) and 0xFF).toByte(),
                (canId and 0xFF).toByte()
            ) + data
        }

        return J2534Message(
            protocolID = J2534Protocols.CAN,
            txFlags = if (isExtendedId) txFlags or 0x0100L else txFlags, // CAN_29BIT_ID
            data = canData
        )
    }

    /**
     * Parse ISO-TP (ISO15765) message from raw data
     */
    fun parseIsoTpMessage(message: J2534Message): IsoTpMessage? {
        if (message.protocolID != J2534Protocols.ISO15765) {
            return null
        }

        val data = message.data
        if (data.size < 4) return null // Need at least CAN ID + first byte

        // Extract CAN ID (first 4 bytes for 29-bit)
        val canId = ((data[0].toLong() and 0xFF) shl 24) or
                ((data[1].toLong() and 0xFF) shl 16) or
                ((data[2].toLong() and 0xFF) shl 8) or
                (data[3].toLong() and 0xFF)

        val payload = data.sliceArray(4 until data.size)

        return IsoTpMessage(canId, payload, message.txFlags, message.timestamp)
    }

    /**
     * Format ISO-TP (ISO15765) message to J2534 format
     */
    fun formatIsoTpMessage(canId: Long, data: ByteArray): J2534Message {
        val formattedData = byteArrayOf(
            ((canId shr 24) and 0xFF).toByte(),
            ((canId shr 16) and 0xFF).toByte(),
            ((canId shr 8) and 0xFF).toByte(),
            (canId and 0xFF).toByte()
        ) + data

        return J2534Message(
            protocolID = J2534Protocols.ISO15765,
            txFlags = 0x0040L, // ISO15765_FRAME_PAD
            data = formattedData
        )
    }

    /**
     * Create UDS request message
     */
    fun createUdsRequest(canId: Long, serviceId: Byte, data: ByteArray = byteArrayOf()): J2534Message {
        val payload = byteArrayOf(serviceId) + data
        return formatIsoTpMessage(canId, payload)
    }

    /**
     * Create UDS response message
     */
    fun createUdsResponse(canId: Long, serviceId: Byte, data: ByteArray = byteArrayOf()): J2534Message {
        val positiveResponseId = (serviceId.toInt() or 0x40).toByte()
        val payload = byteArrayOf(positiveResponseId) + data
        return formatIsoTpMessage(canId, payload)
    }

    /**
     * Create UDS negative response message
     */
    fun createUdsNegativeResponse(canId: Long, serviceId: Byte, errorCode: Byte): J2534Message {
        val payload = byteArrayOf(0x7FL.toByte(), serviceId, errorCode)
        return formatIsoTpMessage(canId, payload)
    }
}

/**
 * Data class representing a CAN message
 */
data class CanMessage(
    val id: Long,
    val data: ByteArray,
    val flags: Long,
    val timestamp: Long
) {
    val isExtended: Boolean = id > 0x7FF
    val priority: Int = if (isExtended) ((id shr 26) and 0x7).toInt() else ((id shr 9) and 0x7).toInt()
}

/**
 * Data class representing an ISO-TP (ISO15765) message
 */
data class IsoTpMessage(
    val canId: Long,
    val data: ByteArray,
    val flags: Long,
    val timestamp: Long
) {
    val isFlowControl: Boolean = data.isNotEmpty() && (data[0] and 0xF0.toByte() == 0x30.toByte())
    val isConsecutiveFrame: Boolean = data.isNotEmpty() && (data[0] and 0xF0.toByte() == 0x20.toByte())
    val isFirstFrame: Boolean = data.isNotEmpty() && (data[0] and 0xF0.toByte() == 0x10.toByte())
    val isSingleFrame: Boolean = data.isNotEmpty() && (data[0] and 0xF0.toByte() == 0x00.toByte())
}

/**
 * Utility class for UDS (Unified Diagnostic Services) message handling
 */
object UdsUtils {
    /**
     * Parse UDS service ID from message
     */
    fun getUdsServiceId(message: J2534Message): Byte? {
        val isoTpMsg = MessageUtils.parseIsoTpMessage(message) ?: return null
        return if (isoTpMsg.data.isNotEmpty()) isoTpMsg.data[0] else null
    }

    /**
     * Check if message is a UDS positive response
     */
    fun isPositiveResponse(message: J2534Message): Boolean {
        val serviceId = getUdsServiceId(message) ?: return false
        return serviceId.toInt() and 0x40 != 0
    }

    /**
     * Check if message is a UDS negative response
     */
    fun isNegativeResponse(message: J2534Message): Boolean {
        val isoTpMsg = MessageUtils.parseIsoTpMessage(message) ?: return false
        if (isoTpMsg.data.size < 3) return false
        return isoTpMsg.data[0] == 0x7FL.toByte()
    }

    /**
     * Get original service ID from negative response
     */
    fun getNegativeResponseServiceId(message: J2534Message): Byte? {
        val isoTpMsg = MessageUtils.parseIsoTpMessage(message) ?: return null
        if (isoTpMsg.data.size < 2) return null
        return if (isoTpMsg.data[0] == 0x7FL.toByte()) isoTpMsg.data[1] else null
    }

    /**
     * Get error code from negative response
     */
    fun getNegativeResponseCode(message: J2534Message): Byte? {
        val isoTpMsg = MessageUtils.parseIsoTpMessage(message) ?: return null
        if (isoTpMsg.data.size < 3) return null
        return if (isoTpMsg.data[0] == 0x7FL.toByte()) isoTpMsg.data[2] else null
    }

    /**
     * Common UDS service IDs
     */
    object ServiceIds {
        const val DIAGNOSTIC_SESSION_CONTROL = 0x10.toByte()
        const val ECU_RESET = 0x11.toByte()
        const val CLEAR_DIAGNOSTIC_INFORMATION = 0x14.toByte()
        const val READ_DTC_INFORMATION = 0x19.toByte()
        const val READ_DATA_BY_IDENTIFIER = 0x22.toByte()
        const val READ_MEMORY_BY_ADDRESS = 0x23.toByte()
        const val SECURITY_ACCESS = 0x27.toByte()
        const val COMMUNICATION_CONTROL = 0x28.toByte()
        const val READ_SCALING_DATA_BY_IDENTIFIER = 0x24.toByte()
        const val READ_DATA_BY_PERIODIC_IDENTIFIER = 0x2A.toByte()
        const val DYNAMICALLY_DEFINE_DATA_IDENTIFIER = 0x2C.toByte()
        const val WRITE_DATA_BY_IDENTIFIER = 0x3B.toByte()
        const val INPUT_OUTPUT_CONTROL_BY_IDENTIFIER = 0x2F.toByte()
        const val ROUTINE_CONTROL = 0x31.toByte()
        const val REQUEST_DOWNLOAD = 0x34.toByte()
        const val REQUEST_UPLOAD = 0x35.toByte()
        const val TRANSFER_DATA = 0x36.toByte()
        const val REQUEST_TRANSFER_EXIT = 0x37.toByte()
        const val REQUEST_FILE_TRANSFER = 0x38.toByte()
        const val WRITE_MEMORY_BY_ADDRESS = 0x3D.toByte()
        const val TESTER_PRESENT = 0x3E.toByte()
        const val ACCESS_TIMING_PARAMETER = 0x83.toByte()
        const val SECURED_DATA_TRANSMISSION = 0x84.toByte()
        const val CONTROL_DTC_SETTING = 0x85.toByte()
        const val RESPONSE_ON_EVENT = 0x86.toByte()
        const val LINK_CONTROL = 0x87.toByte()
    }

    /**
     * Common UDS negative response codes
     */
    object NegativeResponseCodes {
        const val POSITIVE_RESPONSE = 0x00.toByte()
        const val GENERAL_REJECT = 0x10.toByte()
        const val SERVICE_NOT_SUPPORTED = 0x11.toByte()
        const val SUB_FUNCTION_NOT_SUPPORTED = 0x12.toByte()
        const val INCORRECT_MESSAGE_LENGTH_OR_INVALID_FORMAT = 0x13.toByte()
        const val RESPONSE_TOO_LONG = 0x14.toByte()
        const val BUSY_REPEAT_REQUEST = 0x21.toByte()
        const val CONDITIONS_NOT_CORRECT = 0x22.toByte()
        const val REQUEST_SEQUENCE_ERROR = 0x24.toByte()
        const val NO_RESPONSE_FROM_SUBNET_COMPONENT = 0x25.toByte()
        const val FAILURE_PREVENTS_EXECUTION_OF_REQUESTED_ACTION = 0x26.toByte()
        const val REQUEST_OUT_OF_RANGE = 0x31.toByte()
        const val SECURITY_ACCESS_DENIED = 0x33.toByte()
        const val INVALID_KEY = 0x35.toByte()
        const val EXCEEDED_NUMBER_OF_ATTEMPTS = 0x36.toByte()
        const val REQUIRED_TIME_DELAY_NOT_EXPIRED = 0x37.toByte()
        const val UPLOAD_DOWNLOAD_NOT_ACCEPTED = 0x70.toByte()
        const val TRANSFER_DATA_SUSPENDED = 0x71.toByte()
        const val GENERAL_PROGRAMMING_FAILURE = 0x72.toByte()
        const val WRONG_BLOCK_SEQUENCE_COUNTER = 0x73.toByte()
        const val REQUEST_CORRECTLY_RECEIVED_RESPONSE_PENDING = 0x78.toByte()
        const val SUB_FUNCTION_NOT_SUPPORTED_IN_ACTIVE_SESSION = 0x7E.toByte()
        const val SERVICE_NOT_SUPPORTED_IN_ACTIVE_SESSION = 0x7F.toByte()
        const val RCR_RP_NOT_COMPLETE = 0x81.toByte()
        const val ECU_BUSY = 0x82.toByte()
        const val ECU_NOT_RESPONDING = 0x83.toByte()
    }
}