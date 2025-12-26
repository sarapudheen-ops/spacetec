/**
 * NRCConstants.kt
 *
 * Constants for Negative Response Codes (NRC) used in UDS and KWP2000 protocols.
 */

package com.spacetec.core.common

import com.spacetec.core.common.constants.OBDConstants

object NRCConstants {

    // Negative Response Codes
    const val GENERAL_REJECT = OBDConstants.NRC_GENERAL_REJECT
    const val SERVICE_NOT_SUPPORTED = OBDConstants.NRC_SERVICE_NOT_SUPPORTED
    const val SUB_FUNCTION_NOT_SUPPORTED = OBDConstants.NRC_SUBFUNCTION_NOT_SUPPORTED
    const val INCORRECT_MESSAGE_LENGTH = OBDConstants.NRC_INCORRECT_MESSAGE_LENGTH
    const val RESPONSE_TOO_LONG = OBDConstants.NRC_RESPONSE_TOO_LONG
    const val BUSY_REPEAT_REQUEST = OBDConstants.NRC_BUSY_REPEAT_REQUEST
    const val CONDITIONS_NOT_CORRECT = OBDConstants.NRC_CONDITIONS_NOT_CORRECT
    const val REQUEST_SEQUENCE_ERROR = OBDConstants.NRC_REQUEST_SEQUENCE_ERROR
    const val REQUEST_OUT_OF_RANGE = OBDConstants.NRC_REQUEST_OUT_OF_RANGE
    const val SECURITY_ACCESS_DENIED = OBDConstants.NRC_SECURITY_ACCESS_DENIED
    const val INVALID_KEY = OBDConstants.NRC_INVALID_KEY
    const val EXCEEDED_NUMBER_OF_ATTEMPTS = OBDConstants.NRC_EXCEEDED_NUMBER_OF_ATTEMPTS
    const val TIME_DELAY_NOT_EXPIRED = OBDConstants.NRC_TIME_DELAY_NOT_EXPIRED
    const val GENERAL_PROGRAMMING_FAILURE = OBDConstants.NRC_GENERAL_PROGRAMMING_FAILURE
    const val REQUEST_RECEIVED_RESPONSE_PENDING = OBDConstants.NRC_REQUEST_RECEIVED_RESPONSE_PENDING

    // Additional NRCs not in OBDConstants
    const val SERVICE_NOT_SUPPORTED_IN_ACTIVE_SESSION = 0x7F

    private val descriptions = mapOf(
        GENERAL_REJECT to "General reject",
        SERVICE_NOT_SUPPORTED to "Service not supported",
        SUB_FUNCTION_NOT_SUPPORTED to "Sub-function not supported",
        INCORRECT_MESSAGE_LENGTH to "Incorrect message length or invalid format",
        RESPONSE_TOO_LONG to "Response too long",
        BUSY_REPEAT_REQUEST to "Busy, repeat request",
        CONDITIONS_NOT_CORRECT to "Conditions not correct",
        REQUEST_SEQUENCE_ERROR to "Request sequence error",
        REQUEST_OUT_OF_RANGE to "Request out of range",
        SECURITY_ACCESS_DENIED to "Security access denied",
        INVALID_KEY to "Invalid key",
        EXCEEDED_NUMBER_OF_ATTEMPTS to "Exceeded number of attempts",
        TIME_DELAY_NOT_EXPIRED to "Time delay not expired",
        GENERAL_PROGRAMMING_FAILURE to "General programming failure",
        REQUEST_RECEIVED_RESPONSE_PENDING to "Request received, response pending",
        SERVICE_NOT_SUPPORTED_IN_ACTIVE_SESSION to "Service not supported in active session"
    )

    private val names = mapOf(
        GENERAL_REJECT to "GENERAL_REJECT",
        SERVICE_NOT_SUPPORTED to "SERVICE_NOT_SUPPORTED",
        SUB_FUNCTION_NOT_SUPPORTED to "SUB_FUNCTION_NOT_SUPPORTED",
        INCORRECT_MESSAGE_LENGTH to "INCORRECT_MESSAGE_LENGTH",
        RESPONSE_TOO_LONG to "RESPONSE_TOO_LONG",
        BUSY_REPEAT_REQUEST to "BUSY_REPEAT_REQUEST",
        CONDITIONS_NOT_CORRECT to "CONDITIONS_NOT_CORRECT",
        REQUEST_SEQUENCE_ERROR to "REQUEST_SEQUENCE_ERROR",
        REQUEST_OUT_OF_RANGE to "REQUEST_OUT_OF_RANGE",
        SECURITY_ACCESS_DENIED to "SECURITY_ACCESS_DENIED",
        INVALID_KEY to "INVALID_KEY",
        EXCEEDED_NUMBER_OF_ATTEMPTS to "EXCEEDED_NUMBER_OF_ATTEMPTS",
        TIME_DELAY_NOT_EXPIRED to "TIME_DELAY_NOT_EXPIRED",
        GENERAL_PROGRAMMING_FAILURE to "GENERAL_PROGRAMMING_FAILURE",
        REQUEST_RECEIVED_RESPONSE_PENDING to "REQUEST_RECEIVED_RESPONSE_PENDING",
        SERVICE_NOT_SUPPORTED_IN_ACTIVE_SESSION to "SERVICE_NOT_SUPPORTED_IN_ACTIVE_SESSION"
    )

    fun getDescription(nrc: Int): String = descriptions[nrc] ?: "Unknown NRC (0x${nrc.toString(16).uppercase()})"

    fun getName(nrc: Int): String = names[nrc] ?: "UNKNOWN_NRC"
}