package com.spacetec.j2534

/**
 * J2534 Error codes as defined in SAE J2534 standard
 */
object J2534Errors {
    const val STATUS_NOERROR = 0x00000000L
    const val ERR_NOT_SUPPORTED = 0x00000001L
    const val ERR_INVALID_CHANNEL_ID = 0x00000002L
    const val ERR_INVALID_PROTOCOL_ID = 0x00000003L
    const val ERR_NULL_PARAMETER = 0x00000004L
    const val ERR_INVALID_IOCTL_VALUE = 0x00000005L
    const val ERR_INVALID_FLAGS = 0x00000006L
    const val ERR_FAILED = 0x00000007L
    const val ERR_DEVICE_NOT_CONNECTED = 0x00000008L
    const val ERR_TIMEOUT = 0x00000009L
    const val ERR_INVALID_DEVICE_ID = 0x0000000AL
    const val ERR_INVALID_FUNCTION = 0x0000000BL
    const val ERR_INVALID_MSG = 0x0000000CL
    const val ERR_INVALID_TIME_INTERVAL = 0x0000000DL
    const val ERR_INVALID_MSG_ID = 0x0000000EL
    const val ERR_DEVICE_IN_USE = 0x0000000FL
    const val ERR_INVALID_IOCTL_ID = 0x00000010L
    const val ERR_BUFFER_EMPTY = 0x00000011L
    const val ERR_BUFFER_FULL = 0x00000012L
    const val ERR_BUFFER_OVERFLOW = 0x00000013L
    const val ERR_PIN_INVALID = 0x00000014L
    const val ERR_CHANNEL_IN_USE = 0x00000015L
    const val ERR_MSG_PROTOCOL_ID = 0x00000016L
    const val ERR_INVALID_FILTER_ID = 0x00000017L
    const val ERR_NO_FLOW_CONTROL = 0x00000018L
    const val ERR_NOT_UNIQUE = 0x00000019L
    const val ERR_INVALID_BAUDRATE = 0x0000001AL
    const val ERR_INVALID_DEVICE_STATE = 0x0000001BL
    const val ERR_INVALID_TRANSMIT_PATTERN = 0x0000001CL
    const val ERR_INSUFFICIENT_MEMORY = 0x0000001DL
    
    // Custom error codes for Android-specific operations
    const val STATUS_NOT_SUPPORTED = ERR_NOT_SUPPORTED
    const val STATUS_INVALID_CHANNEL_ID = ERR_INVALID_CHANNEL_ID
    const val STATUS_INVALID_DEVICE_ID = ERR_INVALID_DEVICE_ID
    const val STATUS_TIMEOUT = ERR_TIMEOUT
    const val STATUS_BUFFER_EMPTY = ERR_BUFFER_EMPTY
    const val STATUS_BUFFER_OVERFLOW = ERR_BUFFER_OVERFLOW
    const val STATUS_INVALID_MSG = ERR_INVALID_MSG
    const val STATUS_INVALID_MSG_ID = ERR_INVALID_MSG_ID
    const val STATUS_INVALID_FILTER_ID = ERR_INVALID_FILTER_ID
    const val STATUS_INVALID_IOCTL_ID = ERR_INVALID_IOCTL_ID
    const val STATUS_INVALID_IOCTL_VALUE = ERR_INVALID_IOCTL_VALUE
    const val STATUS_DEVICE_NOT_CONNECTED = ERR_DEVICE_NOT_CONNECTED
}

/**
 * J2534 Protocol IDs as defined in SAE J2534 standard
 */
object J2534Protocols {
    const val J1850VPW = 1L
    const val J1850PWM = 2L
    const val ISO9141 = 3L
    const val ISO14230 = 4L
    const val CAN = 5L
    const val ISO15765 = 6L
    const val SCI_A_ENGINE = 7L
    const val SCI_A_TRANS = 8L
    const val SCI_B_ENGINE = 9L
    const val SCI_B_TRANS = 10L
}

/**
 * J2534 Filter Types
 */
object J2534FilterTypes {
    const val PASS_FILTER = 1L
    const val BLOCK_FILTER = 2L
    const val FLOW_CONTROL_FILTER = 3L
}

/**
 * J2534 Connect Flags
 */
object J2534ConnectFlags {
    const val CAN_29BIT_ID = 0x0100L
    const val CAN_ID_BOTH = 0x0200L
    const val CAN_ISO_BRP = 0x0400L
    const val CAN_HS_DATA = 0x0800L
}

/**
 * J2534 Ioctl IDs
 */
object J2534IoctlIds {
    const val GET_CONFIG = 0x01L
    const val SET_CONFIG = 0x02L
    const val GET_VERSION = 0x03L
    const val GET_DLL_VERSION = 0x04L
    const val GET_API_VERSION = 0x05L
    const val GET_HARDWARE_ID = 0x06L
    const val READ_VBATT = 0x07L
    const val READ_PROG_VOLTAGE = 0x08L
    const val TX_SET_MIN_IFS = 0x09L
    const val START_SESSION = 0x0AL
    const val STOP_SESSION = 0x0BL
    const val DIAG_SERVICE_CONNECT = 0x0CL
    const val DIAG_SERVICE_DISCONNECT = 0x0DL
    const val READ_DEFAULT_TIMEOUT = 0x0EL
    const val READ_WAKEUP_TIMEOUT = 0x0FL
    const val READ_CAN_TIMEOUT = 0x10L
    const val READ_ISO_TIMEOUT = 0x11L
    const val WRITE_DEFAULT_TIMEOUT = 0x12L
    const val WRITE_WAKEUP_TIMEOUT = 0x13L
    const val WRITE_CAN_TIMEOUT = 0x14L
    const val WRITE_ISO_TIMEOUT = 0x15L
}

/**
 * J2534 Configuration Parameters
 */
object J2534ConfigParams {
    const val LOOPBACK = 1L
    const val CAN_AUTO_BUSCTRL = 2L
    const val CAN_BAUDRATE = 3L
    const val CAN_DBIT_BAUDRATE = 4L
    const val CAN_SAME_AS_TX_ID = 5L
    const val CAN_ID_FILTER = 6L
    const val CAN_TX_PADDING = 7L
    const val CAN_RX_PADDING = 8L
    const val CAN_EXT_ADDR = 9L
    const val CAN_EXT_ADDR_ENABLE = 10L
    const val CAN_FD_MODE = 11L
    const val CAN_ISO_BRS = 12L
    const val PARITY = 13L
    const val W0 = 14L
    const val W1 = 15L
    const val W2 = 16L
    const val W3 = 17L
    const val W4 = 18L
    const val W5 = 19L
    const val W6 = 20L
    const val W7 = 21L
    const val W8 = 22L
    const val W9 = 23L
    const val W10 = 24L
    const val KEYWORD_2 = 25L
    const val ISO_TX_DL = 26L
    const val TIDLE = 27L
    const val TINIL = 28L
    const val TWUP = 29L
    const val PARITY_CHECK = 30L
    const val ISO_BAUDRATE = 31L
    const val SNIFF_MODE = 32L
    const val P1_MIN = 33L
    const val P1_MAX = 34L
    const val P2_MIN = 35L
    const val P2_MAX = 36L
    const val P3_MIN = 37L
    const val P3_MAX = 38L
    const val P4_MIN = 39L
    const val P4_MAX = 40L
    const val W1_MIN = 41L
    const val W1_MAX = 42L
    const val DIAG_REQUEST = 43L
    const val DIAG_RESPONSE = 44L
    const val TX_HDR_FLAGS = 45L
    const val ISO_HDR_LEN = 46L
    const val ISO_RECV_LEN = 47L
    const val MAX_BLK_SIZE = 48L
    const val BS = 49L
    const val ST = 50L
    const val ISO_9141_KW1 = 51L
    const val ISO_9141_KW2 = 52L
    const val HDR_IGNORE = 53L
    const val HDR_DATA = 54L
    const val HDR_FLAGS = 55L
    const val ISO_RECV_PAD = 56L
    const val PKT_SZ = 57L
    const val INTER_PKT_TIMEOUT = 58L
    const val BUSY_RETRY_TIMEOUT = 59L
    const val ISO_FUNCTION = 60L
    const val ISO_ECU_CHALLENGE = 61L
    const val ISO_TP_MODE = 62L
    const val ISO_TP_BS = 63L
    const val ISO_TP_STMIN = 64L
    const val DATA_BITS = 65L
    const val FIVE_BAUD_MOD = 66L
    const val BS_TX = 67L
    const val ST_TX = 68L
    const val CHKSUM = 69L
    const val NODE_ADDRESS = 70L
    const val J1962_PINS = 71L
    const val ISO_14230_BAUDRATE = 72L
    const val TX_DL = 73L
    const val J1850BRP = 74L
    const val ISO15765_BS = 75L
    const val ISO15765_STMIN = 76L
    const val ISO15765_WFT_MAX = 77L
    const val DATA_TO_SEND = 78L
    const val ECHO_TX = 79L
}