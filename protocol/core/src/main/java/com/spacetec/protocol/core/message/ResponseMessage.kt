/**
 * ResponseMessage.kt
 *
 * Represents a response message from a diagnostic operation.
 * This wraps DiagnosticMessage with additional response-specific metadata.
 */

package com.spacetec.protocol.core.message

import java.time.Instant

/**
 * A response message containing diagnostic data and response metadata.
 *
 * @property diagnosticMessage The underlying diagnostic message
 * @property success Whether the response indicates success
 * @property errorCode Error code if response failed (0 for success)
 * @property responseTime Time when response was received
 * @property requestMessage The original request message that generated this response
 */
data class ResponseMessage(
    val diagnosticMessage: DiagnosticMessage,
    val success: Boolean,
    val errorCode: Int = 0,
    val responseTime: Instant = Instant.now(),
    val requestMessage: DiagnosticMessage? = null
) {

    /**
     * The service ID of the response.
     */
    val serviceId: Int
        get() = diagnosticMessage.serviceId

    /**
     * The response data payload.
     */
    val data: ByteArray
        get() = diagnosticMessage.data

    /**
     * Whether this is a negative response.
     */
    val isNegativeResponse: Boolean
        get() = diagnosticMessage.isNegativeResponse

    /**
     * Negative response code if applicable.
     */
    val negativeResponseCode: Int
        get() = diagnosticMessage.negativeResponseCode

    /**
     * Converts the response to a hex string for logging.
     */
    fun toHexString(): String = diagnosticMessage.toHexString()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ResponseMessage

        if (diagnosticMessage != other.diagnosticMessage) return false
        if (success != other.success) return false
        if (errorCode != other.errorCode) return false
        if (responseTime != other.responseTime) return false
        if (requestMessage != other.requestMessage) return false

        return true
    }

    override fun hashCode(): Int {
        var result = diagnosticMessage.hashCode()
        result = 31 * result + success.hashCode()
        result = 31 * result + errorCode
        result = 31 * result + responseTime.hashCode()
        result = 31 * result + (requestMessage?.hashCode() ?: 0)
        return result
    }

    companion object {

        /**
         * Creates a successful response message.
         */
        fun success(
            diagnosticMessage: DiagnosticMessage,
            requestMessage: DiagnosticMessage? = null
        ): ResponseMessage = ResponseMessage(
            diagnosticMessage = diagnosticMessage,
            success = true,
            requestMessage = requestMessage
        )

        /**
         * Creates a failed response message.
         */
        fun failure(
            diagnosticMessage: DiagnosticMessage,
            errorCode: Int,
            requestMessage: DiagnosticMessage? = null
        ): ResponseMessage = ResponseMessage(
            diagnosticMessage = diagnosticMessage,
            success = false,
            errorCode = errorCode,
            requestMessage = requestMessage
        )

        /**
         * Creates a negative response message.
         */
        fun negativeResponse(
            diagnosticMessage: DiagnosticMessage,
            requestMessage: DiagnosticMessage? = null
        ): ResponseMessage = ResponseMessage(
            diagnosticMessage = diagnosticMessage,
            success = false,
            errorCode = diagnosticMessage.negativeResponseCode,
            requestMessage = requestMessage
        )
    }
}