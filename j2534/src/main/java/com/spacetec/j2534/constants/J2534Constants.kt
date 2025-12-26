/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 *
 * This file is part of the SpaceTec professional automotive diagnostic system.
 */

package com.spacetec.j2534.constants

/**
 * J2534 API constants and definitions.
 *
 * Based on the SAE J2534 specification for Pass-Thru vehicle communication.
 * These constants define the standard interface for automotive diagnostic tools.
 *
 * @author SpaceTec Development Team
 * @since 1.0.0
 */
object J2534Constants {
    
    // ═══════════════════════════════════════════════════════════════════════
    // RETURN CODES
    // ═══════════════════════════════════════════════════════════════════════
    
    /** Operation completed successfully */
    const val STATUS_NOERROR = 0x00000000
    
    /** Device not connected */
    const val ERR_NOT_SUPPORTED = 0x00000001
    
    /** Invalid channel ID */
    const val ERR_INVALID_CHANNEL_ID = 0x00000002
    
    /** Invalid protocol ID */
    const val ERR_INVALID_PROTOCOL_ID = 0x00000003
    
    /** Null parameter */
    const val ERR_NULL_PARAMETER = 0x00000004
    
    /** Invalid IOCTL value */
    const val ERR_INVALID_IOCTL_VALUE = 0x00000005
    
    /** Invalid flags */
    const val ERR_INVALID_FLAGS = 0x00000006
    
    /** Failed to connect */
    const val ERR_FAILED = 0x00000007
    
    /** Device not connected */
    const val ERR_DEVICE_NOT_CONNECTED = 0x00000008
    
    /** Timeout occurred */
    const val ERR_TIMEOUT = 0x00000009
    
    /** Invalid message */
    const val ERR_INVALID_MSG = 0x0000000A
    
    /** Invalid time interval */
    const val ERR_INVALID_TIME_INTERVAL = 0x0000000B
    
    /** Exceeded limit */
    const val ERR_EXCEEDED_LIMIT = 0x0000000C
    
    /** Invalid message ID */
    const val ERR_INVALID_MSG_ID = 0x0000000D
    
    /** Device in use */
    const val ERR_DEVICE_IN_USE = 0x0000000E
    
    /** Invalid IOCTL ID */
    const val ERR_INVALID_IOCTL_ID = 0x0000000F
    
    /** Buffer empty */
    const val ERR_BUFFER_EMPTY = 0x00000010
    
    /** Buffer full */
    const val ERR_BUFFER_FULL = 0x00000011
    
    /** Buffer overflow */
    const val ERR_BUFFER_OVERFLOW = 0x00000012
    
    /** Pin invalid */
    const val ERR_PIN_INVALID = 0x00000013
    
    /** Channel in use */
    const val ERR_CHANNEL_IN_USE = 0x00000014
    
    /** Message protocol ID */
    const val ERR_MSG_PROTOCOL_ID = 0x00000015
    
    /** Invalid filter ID */
    const val ERR_INVALID_FILTER_ID = 0x00000016
    
    /** No flow control */
    const val ERR_NO_FLOW_CONTROL = 0x00000017
    
    /** Not unique */
    const val ERR_NOT_UNIQUE = 0x00000018
    
    /** Invalid baudrate */
    const val ERR_INVALID_BAUDRATE = 0x00000019
    
    /** Invalid device ID */
    const val ERR_INVALID_DEVICE_ID = 0x0000001A
    
    // ═══════════════════════════════════════════════════════════════════════
    // PROTOCOL IDs
    // ═══════════════════════════════════════════════════════════════════════
    
    /** J1850 VPW protocol */
    const val J1850VPW = 0x00000001
    
    /** J1850 PWM protocol */
    const val J1850PWM = 0x00000002
    
    /** ISO 9141 protocol */
    const val ISO9141 = 0x00000003
    
    /** ISO 14230 (KWP2000) protocol */
    const val ISO14230 = 0x00000004
    
    /** CAN protocol */
    const val CAN = 0x00000005
    
    /** ISO 15765 (CAN with TP) protocol */
    const val ISO15765 = 0x00000006
    
    /** SCI A Engine protocol */
    const val SCI_A_ENGINE = 0x00000007
    
    /** SCI A Trans protocol */
    const val SCI_A_TRANS = 0x00000008
    
    /** SCI B Engine protocol */
    const val SCI_B_ENGINE = 0x00000009
    
    /** SCI B Trans protocol */
    const val SCI_B_TRANS = 0x0000000A
    
    // ═══════════════════════════════════════════════════════════════════════
    // CONNECT FLAGS
    // ═══════════════════════════════════════════════════════════════════════
    
    /** CAN 29-bit ID */
    const val CAN_29BIT_ID = 0x00000100
    
    /** ISO 9141 no checksum */
    const val ISO9141_NO_CHECKSUM = 0x00000200
    
    /** CAN ID both */
    const val CAN_ID_BOTH = 0x00000800
    
    /** ISO 9141 K line only */
    const val ISO9141_K_LINE_ONLY = 0x00001000
    
    /** SNIFF mode */
    const val SNIFF_MODE = 0x10000000
    
    /** Block duplicate messages */
    const val BLOCK_DUPLICATE_MSGS = 0x20000000
    
    // ═══════════════════════════════════════════════════════════════════════
    // FILTER TYPES
    // ═══════════════════════════════════════════════════════════════════════
    
    /** Pass filter */
    const val PASS_FILTER = 0x00000001
    
    /** Block filter */
    const val BLOCK_FILTER = 0x00000002
    
    /** Flow control filter */
    const val FLOW_CONTROL_FILTER = 0x00000003
    
    // ═══════════════════════════════════════════════════════════════════════
    // IOCTL IDs
    // ═══════════════════════════════════════════════════════════════════════
    
    /** Get configuration */
    const val GET_CONFIG = 0x01
    
    /** Set configuration */
    const val SET_CONFIG = 0x02
    
    /** Read version */
    const val READ_VBATT = 0x03
    
    /** Five baud init */
    const val FIVE_BAUD_INIT = 0x04
    
    /** Fast init */
    const val FAST_INIT = 0x05
    
    /** Clear TX buffer */
    const val CLEAR_TX_BUFFER = 0x07
    
    /** Clear RX buffer */
    const val CLEAR_RX_BUFFER = 0x08
    
    /** Clear periodic messages */
    const val CLEAR_PERIODIC_MSGS = 0x09
    
    /** Clear message filters */
    const val CLEAR_MSG_FILTERS = 0x0A
    
    /** Clear functional message lookup table */
    const val CLEAR_FUNCT_MSG_LOOKUP_TABLE = 0x0B
    
    /** Add to functional message lookup table */
    const val ADD_TO_FUNCT_MSG_LOOKUP_TABLE = 0x0C
    
    /** Delete from functional message lookup table */
    const val DELETE_FROM_FUNCT_MSG_LOOKUP_TABLE = 0x0D
    
    /** Read programming voltage */
    const val READ_PROG_VOLTAGE = 0x0E
    
    // ═══════════════════════════════════════════════════════════════════════
    // CONFIGURATION PARAMETERS
    // ═══════════════════════════════════════════════════════════════════════
    
    /** Data rate */
    const val DATA_RATE = 0x01
    
    /** Loop back */
    const val LOOPBACK = 0x03
    
    /** Node address */
    const val NODE_ADDRESS = 0x04
    
    /** Network line */
    const val NETWORK_LINE = 0x05
    
    /** P1 minimum */
    const val P1_MIN = 0x06
    
    /** P1 maximum */
    const val P1_MAX = 0x07
    
    /** P2 minimum */
    const val P2_MIN = 0x08
    
    /** P2 maximum */
    const val P2_MAX = 0x09
    
    /** P3 minimum */
    const val P3_MIN = 0x0A
    
    /** P3 maximum */
    const val P3_MAX = 0x0B
    
    /** P4 minimum */
    const val P4_MIN = 0x0C
    
    /** P4 maximum */
    const val P4_MAX = 0x0D
    
    /** W1 */
    const val W1 = 0x0E
    
    /** W2 */
    const val W2 = 0x0F
    
    /** W3 */
    const val W3 = 0x10
    
    /** W4 */
    const val W4 = 0x11
    
    /** W5 */
    const val W5 = 0x12
    
    /** Tidle */
    const val TIDLE = 0x13
    
    /** Tinil */
    const val TINIL = 0x14
    
    /** Twup */
    const val TWUP = 0x15
    
    /** Parity */
    const val PARITY = 0x16
    
    /** Bit sample point */
    const val BIT_SAMPLE_POINT = 0x17
    
    /** Sync jump width */
    const val SYNC_JUMP_WIDTH = 0x18
    
    /** W0 */
    const val W0 = 0x19
    
    /** T1 maximum */
    const val T1_MAX = 0x1A
    
    /** T2 maximum */
    const val T2_MAX = 0x1B
    
    /** T4 maximum */
    const val T4_MAX = 0x1C
    
    /** T5 maximum */
    const val T5_MAX = 0x1D
    
    /** ISO 15765 BS */
    const val ISO15765_BS = 0x1E
    
    /** ISO 15765 ST min */
    const val ISO15765_STMIN = 0x1F
    
    /** BS TX */
    const val BS_TX = 0x20
    
    /** ST min TX */
    const val STMIN_TX = 0x21
    
    /** T3 maximum */
    const val T3_MAX = 0x22
    
    /** ISO 15765 wait limit */
    const val ISO15765_WFT_MAX = 0x23
    
    // ═══════════════════════════════════════════════════════════════════════
    // MESSAGE FLAGS
    // ═══════════════════════════════════════════════════════════════════════
    
    /** ISO 15765 frame pad */
    const val ISO15765_FRAME_PAD = 0x00000040
    
    /** ISO 15765 addr type */
    const val ISO15765_ADDR_TYPE = 0x00000080
    
    /** CAN 29-bit ID */
    const val CAN_29BIT_ID_FLAG = 0x00000100
    
    /** Wait P3 min only */
    const val WAIT_P3_MIN_ONLY = 0x00000200
    
    /** SCI mode */
    const val SCI_MODE = 0x00400000
    
    /** SCI TX done */
    const val SCI_TX_DONE = 0x00800000
    
    /** TX normal transmit */
    const val TX_NORMAL_TRANSMIT = 0x00000000
    
    /** Start of message */
    const val START_OF_MESSAGE = 0x00000001
    
    /** Get RX status */
    const val GET_RX_STATUS = 0x00000001
    
    /** TX message type */
    const val TX_MSG_TYPE = 0x00000001
    
    /** RX message type */
    const val RX_MSG_TYPE = 0x00000001
    
    /** Indication */
    const val INDICATION = 0x00000001
    
    /** Break */
    const val BREAK = 0x00000004
    
    /** TX done */
    const val TX_DONE = 0x00000008
    
    /** ISO 15765 padding error */
    const val ISO15765_PADDING_ERROR = 0x00000010
    
    /** ISO 15765 addr type error */
    const val ISO15765_ADDR_TYPE_ERROR = 0x00000080
    
    // ═══════════════════════════════════════════════════════════════════════
    // TIMING CONSTANTS
    // ═══════════════════════════════════════════════════════════════════════
    
    /** Default timeout in milliseconds */
    const val DEFAULT_TIMEOUT_MS = 5000L
    
    /** Fast init timeout */
    const val FAST_INIT_TIMEOUT_MS = 1000L
    
    /** Five baud init timeout */
    const val FIVE_BAUD_INIT_TIMEOUT_MS = 10000L
    
    /** Maximum message length */
    const val MAX_MESSAGE_LENGTH = 4128
    
    /** Maximum number of filters */
    const val MAX_FILTERS = 10
    
    /** Maximum number of channels */
    const val MAX_CHANNELS = 16
    
    // ═══════════════════════════════════════════════════════════════════════
    // UTILITY FUNCTIONS
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Converts a J2534 error code to a human-readable string.
     */
    fun errorCodeToString(errorCode: Int): String {
        return when (errorCode) {
            STATUS_NOERROR -> "No Error"
            ERR_NOT_SUPPORTED -> "Not Supported"
            ERR_INVALID_CHANNEL_ID -> "Invalid Channel ID"
            ERR_INVALID_PROTOCOL_ID -> "Invalid Protocol ID"
            ERR_NULL_PARAMETER -> "Null Parameter"
            ERR_INVALID_IOCTL_VALUE -> "Invalid IOCTL Value"
            ERR_INVALID_FLAGS -> "Invalid Flags"
            ERR_FAILED -> "Failed"
            ERR_DEVICE_NOT_CONNECTED -> "Device Not Connected"
            ERR_TIMEOUT -> "Timeout"
            ERR_INVALID_MSG -> "Invalid Message"
            ERR_INVALID_TIME_INTERVAL -> "Invalid Time Interval"
            ERR_EXCEEDED_LIMIT -> "Exceeded Limit"
            ERR_INVALID_MSG_ID -> "Invalid Message ID"
            ERR_DEVICE_IN_USE -> "Device In Use"
            ERR_INVALID_IOCTL_ID -> "Invalid IOCTL ID"
            ERR_BUFFER_EMPTY -> "Buffer Empty"
            ERR_BUFFER_FULL -> "Buffer Full"
            ERR_BUFFER_OVERFLOW -> "Buffer Overflow"
            ERR_PIN_INVALID -> "Pin Invalid"
            ERR_CHANNEL_IN_USE -> "Channel In Use"
            ERR_MSG_PROTOCOL_ID -> "Message Protocol ID"
            ERR_INVALID_FILTER_ID -> "Invalid Filter ID"
            ERR_NO_FLOW_CONTROL -> "No Flow Control"
            ERR_NOT_UNIQUE -> "Not Unique"
            ERR_INVALID_BAUDRATE -> "Invalid Baudrate"
            ERR_INVALID_DEVICE_ID -> "Invalid Device ID"
            else -> "Unknown Error (0x${errorCode.toString(16).uppercase()})"
        }
    }
    
    /**
     * Converts a protocol ID to a human-readable string.
     */
    fun protocolIdToString(protocolId: Int): String {
        return when (protocolId) {
            J1850VPW -> "J1850 VPW"
            J1850PWM -> "J1850 PWM"
            ISO9141 -> "ISO 9141"
            ISO14230 -> "ISO 14230 (KWP2000)"
            CAN -> "CAN"
            ISO15765 -> "ISO 15765 (CAN with TP)"
            SCI_A_ENGINE -> "SCI A Engine"
            SCI_A_TRANS -> "SCI A Trans"
            SCI_B_ENGINE -> "SCI B Engine"
            SCI_B_TRANS -> "SCI B Trans"
            else -> "Unknown Protocol (0x${protocolId.toString(16).uppercase()})"
        }
    }
    
    /**
     * Checks if a protocol supports CAN.
     */
    fun isCanProtocol(protocolId: Int): Boolean {
        return protocolId == CAN || protocolId == ISO15765
    }
    
    /**
     * Checks if a protocol supports ISO timing parameters.
     */
    fun isIsoProtocol(protocolId: Int): Boolean {
        return protocolId == ISO9141 || protocolId == ISO14230
    }
    
    /**
     * Gets the default data rate for a protocol.
     */
    fun getDefaultDataRate(protocolId: Int): Int {
        return when (protocolId) {
            J1850VPW -> 10400
            J1850PWM -> 41600
            ISO9141 -> 10400
            ISO14230 -> 10400
            CAN -> 500000
            ISO15765 -> 500000
            SCI_A_ENGINE, SCI_A_TRANS, SCI_B_ENGINE, SCI_B_TRANS -> 8192
            else -> 10400
        }
    }
}