/**
 * OBDConstants.kt
 *
 * Constants related to OBD-II protocol and diagnostics.
 */

package com.spacetec.core.common.constants

object OBDConstants {

    // OBD-II Service Identifiers
    const val SERVICE_CURRENT_DATA = 0x01
    const val SERVICE_FREEZE_FRAME_DATA = 0x02
    const val SERVICE_READ_DTC = 0x03
    const val SERVICE_CLEAR_DTC = 0x04
    const val SERVICE_PENDING_DTC = 0x07
    const val SERVICE_OXYGEN_SENSORS = 0x05
    const val SERVICE_CONTROL_ONBOARD = 0x08
    const val SERVICE_VEHICLE_INFO = 0x09
    const val SERVICE_PERMANENT_DTC = 0x0A

    // Common PID Identifiers
    const val PID_ENGINE_COOLANT_TEMP = 0x05
    const val PID_ENGINE_RPM = 0x0C
    const val PID_VEHICLE_SPEED = 0x0D
    const val PID_THROTTLE_POSITION = 0x11
    const val PID_INTAKE_AIR_TEMP = 0x0F
    const val PID_MAF_AIR_FLOW_RATE = 0x10
    const val PID_FUEL_LEVEL = 0x2F
    const val PID_DISTANCE_TRAVELED_MIL = 0x21
    const val PID_FUEL_SYSTEM_STATUS = 0x03
    const val PID_SHORT_TERM_FUEL_TRIM_BANK1 = 0x06
    const val PID_LONG_TERM_FUEL_TRIM_BANK1 = 0x07

    // ECU Response Codes
    const val POSITIVE_RESPONSE_MASK = 0x40
    const val NEGATIVE_RESPONSE = 0x7F

    // Negative Response Codes (NRC)
    const val NRC_GENERAL_REJECT = 0x10
    const val NRC_SERVICE_NOT_SUPPORTED = 0x11
    const val NRC_SUBFUNCTION_NOT_SUPPORTED = 0x12
    const val NRC_INCORRECT_MESSAGE_LENGTH = 0x13
    const val NRC_RESPONSE_TOO_LONG = 0x14
    const val NRC_BUSY_REPEAT_REQUEST = 0x21
    const val NRC_CONDITIONS_NOT_CORRECT = 0x22
    const val NRC_REQUEST_SEQUENCE_ERROR = 0x24
    const val NRC_REQUEST_OUT_OF_RANGE = 0x31
    const val NRC_SECURITY_ACCESS_DENIED = 0x33
    const val NRC_INVALID_KEY = 0x35
    const val NRC_EXCEEDED_NUMBER_OF_ATTEMPTS = 0x36
    const val NRC_TIME_DELAY_NOT_EXPIRED = 0x37
    const val NRC_GENERAL_PROGRAMMING_FAILURE = 0x72
    const val NRC_REQUEST_RECEIVED_RESPONSE_PENDING = 0x78

    // Timing Parameters (in milliseconds)
    const val DEFAULT_TIMEOUT = 5000L
    const val P2_TIMEOUT = 50L
    const val P2_STAR_TIMEOUT = 5000L

    // Message Formats
    const val CAN_FRAME_SIZE = 8
    const val ISO_TP_MAX_DATA_SIZE = 4095
}