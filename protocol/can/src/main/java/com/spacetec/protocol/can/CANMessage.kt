package com.spacetec.protocol.can

import com.spacetec.protocol.core.message.DiagnosticMessage

/**
 * CAN Message implementation for the SpaceTec diagnostic system
 * @param serviceId The diagnostic service ID
 * @param data The message data
 * @param isNegativeResponse Whether this is a negative response
 * @param negativeResponseCode The negative response code if applicable
 */
class CANMessage(
    override val serviceId: Int,
    override val data: ByteArray,
    val isNegativeResponse: Boolean = false,
    val negativeResponseCode: Int = -1
) : DiagnosticMessage {
    
    override fun toByteArray(): ByteArray = data
    
    override fun toString(): String {
        return "CANMessage(serviceId=0x${serviceId.toString(16)}, data=${data.joinToString(" ") { String.format("%02X", it) }}, isNegativeResponse=$isNegativeResponse, nrc=${if (isNegativeResponse) String.format("0x%02X", negativeResponseCode) else "N/A"})"
    }
}