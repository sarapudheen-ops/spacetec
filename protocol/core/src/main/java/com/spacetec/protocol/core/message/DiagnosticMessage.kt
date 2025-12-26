/**
 * DiagnosticMessage.kt
 *
 * Represents a diagnostic message exchanged between the diagnostic tool and vehicle ECU.
 * This is the core data structure for all diagnostic communication.
 */

package com.spacetec.protocol.core.message

import java.time.Instant

/**
 * A diagnostic message containing service identifier and data payload.
 *
 * @property serviceId The OBD/UDS service identifier (0x00-0xFF)
 * @property data The message data payload
 * @property timestamp When the message was created/sent
 * @property isRequest true if this is a request message, false for response
 */
data class DiagnosticMessage(
    val serviceId: Int,
    val data: ByteArray,
    val timestamp: Instant = Instant.now(),
    val isRequest: Boolean = true
) {

    /**
     * The total message length including service ID.
     */
    val length: Int
        get() = data.size + 1

    /**
     * Whether this is a negative response.
     */
    val isNegativeResponse: Boolean
        get() = !isRequest && data.isNotEmpty() && data[0] == 0x7F.toByte()

    /**
     * Negative response code if this is a negative response.
     */
    val negativeResponseCode: Int
        get() = if (isNegativeResponse && data.size >= 2) data[1].toInt() and 0xFF else 0

    /**
     * Converts the message to a byte array for transmission.
     */
    fun toByteArray(): ByteArray = byteArrayOf(serviceId.toByte()) + data

    /**
     * Converts the message to a hex string for logging.
     */
    fun toHexString(): String = toByteArray().joinToString(" ") { String.format("%02X", it) }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DiagnosticMessage

        if (serviceId != other.serviceId) return false
        if (!data.contentEquals(other.data)) return false
        if (timestamp != other.timestamp) return false
        if (isRequest != other.isRequest) return false

        return true
    }

    override fun hashCode(): Int {
        var result = serviceId
        result = 31 * result + data.contentHashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + isRequest.hashCode()
        return result
    }

    companion object {
        /**
         * Creates a diagnostic message from a byte array.
         */
        fun fromByteArray(bytes: ByteArray, isRequest: Boolean = false): DiagnosticMessage {
            if (bytes.isEmpty()) {
                return DiagnosticMessage(0, byteArrayOf(), isRequest = isRequest)
            }
            val serviceId = bytes[0].toInt() and 0xFF
            val data = bytes.drop(1).toByteArray()
            return DiagnosticMessage(serviceId, data, isRequest = isRequest)
        }
    }
}