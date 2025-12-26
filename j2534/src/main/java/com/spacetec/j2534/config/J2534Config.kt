package com.spacetec.j2534.config

/**
 * Configuration class for J2534 communication parameters
 */
data class J2534Config(
    val protocol: Long = com.spacetec.j2534.J2534Protocols.ISO15765,
    val baudrate: Long = 500000L, // Default CAN baudrate
    val flags: Long = 0L,
    val loopbackEnabled: Boolean = false,
    val canAutoBusCtrl: Boolean = true,
    val canIdFilter: Long = 0L,
    val canTxPadding: Byte = 0x00,
    val canRxPadding: Byte = 0x00,
    val isoBaudrate: Long = 10400L,
    val parity: Long = 0L, // No parity
    val dataBits: Long = 8L,
    val w0: Long = 300L, // Default timing parameter
    val w1: Long = 600L,
    val w2: Long = 600L,
    val w3: Long = 600L,
    val w4: Long = 25000L,
    val w5: Long = 500000L,
    val w6: Long = 5000L,
    val w7: Long = 5000L,
    val w8: Long = 5000L,
    val w9: Long = 5000L,
    val w10: Long = 5000L,
    val p1Min: Long = 0L,
    val p1Max: Long = 20L,
    val p2Min: Long = 25L,
    val p2Max: Long = 50L,
    val p3Min: Long = 55L,
    val p3Max: Long = 5000L,
    val p4Min: Long = 0L,
    val p4Max: Long = 20L,
    val w1Min: Long = 0L,
    val w1Max: Long = 127L,
    val blockSize: Long = 8L,
    val stMin: Long = 0L,
    val iso15765WftMax: Long = 5L,
    val maxBlkSize: Long = 0L,
    val bs: Long = 0L,
    val st: Long = 0L,
    val txHdrFlags: Long = 0L,
    val echoTx: Boolean = false
) {
    /**
     * Convert configuration to parameter map for IOCTL operations
     */
    fun toParameterMap(): Map<Long, Long> {
        return mapOf(
            com.spacetec.j2534.J2534ConfigParams.LOOPBACK to if (loopbackEnabled) 1L else 0L,
            com.spacetec.j2534.J2534ConfigParams.CAN_AUTO_BUSCTRL to if (canAutoBusCtrl) 1L else 0L,
            com.spacetec.j2534.J2534ConfigParams.CAN_BAUDRATE to baudrate,
            com.spacetec.j2534.J2534ConfigParams.CAN_TX_PADDING to canTxPadding.toLong(),
            com.spacetec.j2534.J2534ConfigParams.CAN_RX_PADDING to canRxPadding.toLong(),
            com.spacetec.j2534.J2534ConfigParams.ISO_BAUDRATE to isoBaudrate,
            com.spacetec.j2534.J2534ConfigParams.PARITY to parity,
            com.spacetec.j2534.J2534ConfigParams.DATA_BITS to dataBits,
            com.spacetec.j2534.J2534ConfigParams.W0 to w0,
            com.spacetec.j2534.J2534ConfigParams.W1 to w1,
            com.spacetec.j2534.J2534ConfigParams.W2 to w2,
            com.spacetec.j2534.J2534ConfigParams.W3 to w3,
            com.spacetec.j2534.J2534ConfigParams.W4 to w4,
            com.spacetec.j2534.J2534ConfigParams.W5 to w5,
            com.spacetec.j2534.J2534ConfigParams.W6 to w6,
            com.spacetec.j2534.J2534ConfigParams.W7 to w7,
            com.spacetec.j2534.J2534ConfigParams.W8 to w8,
            com.spacetec.j2534.J2534ConfigParams.W9 to w9,
            com.spacetec.j2534.J2534ConfigParams.W10 to w10,
            com.spacetec.j2534.J2534ConfigParams.P1_MIN to p1Min,
            com.spacetec.j2534.J2534ConfigParams.P1_MAX to p1Max,
            com.spacetec.j2534.J2534ConfigParams.P2_MIN to p2Min,
            com.spacetec.j2534.J2534ConfigParams.P2_MAX to p2Max,
            com.spacetec.j2534.J2534ConfigParams.P3_MIN to p3Min,
            com.spacetec.j2534.J2534ConfigParams.P3_MAX to p3Max,
            com.spacetec.j2534.J2534ConfigParams.P4_MIN to p4Min,
            com.spacetec.j2534.J2534ConfigParams.P4_MAX to p4Max,
            com.spacetec.j2534.J2534ConfigParams.W1_MIN to w1Min,
            com.spacetec.j2534.J2534ConfigParams.W1_MAX to w1Max,
            com.spacetec.j2534.J2534ConfigParams.ISO15765_BS to blockSize,
            com.spacetec.j2534.J2534ConfigParams.ISO15765_STMIN to stMin,
            com.spacetec.j2534.J2534ConfigParams.ISO15765_WFT_MAX to iso15765WftMax,
            com.spacetec.j2534.J2534ConfigParams.MAX_BLK_SIZE to maxBlkSize,
            com.spacetec.j2534.J2534ConfigParams.BS to bs,
            com.spacetec.j2534.J2534ConfigParams.ST to st,
            com.spacetec.j2534.J2534ConfigParams.TX_HDR_FLAGS to txHdrFlags
        )
    }
}

/**
 * Configuration class for ECU programming parameters
 */
data class ProgrammingConfig(
    val enableProgrammingVoltage: Boolean = false,
    val programmingVoltage: Long = 0L, // 0 = off, 12000 = 12V, 7200 = 7.2V, etc. (in mV)
    val programmingPin: Long = 15L, // J1962 pin number
    val verifyAfterWrite: Boolean = true,
    val blockSize: Int = 128,
    val maxRetries: Int = 3,
    val timeout: Long = 5000L,
    val flashTimeout: Long = 30000L, // Longer timeout for flash operations
    val enableFlowControl: Boolean = true,
    val blockSizeMax: Int = 255,
    val stMin: Int = 0, // Separation time minimum in ms
    val maxWaitFrames: Int = 5
)

/**
 * Configuration class for communication timing
 */
data class TimingConfig(
    val readTimeout: Long = 2000L,
    val writeTimeout: Long = 1000L,
    val connectTimeout: Long = 5000L,
    val disconnectTimeout: Long = 1000L,
    val sessionTimeout: Long = 30000L,
    val responseTimeout: Long = 2000L,
    val interMessageDelay: Long = 10L, // Minimum delay between messages in ms
    val maxRetries: Int = 3,
    val retryDelay: Long = 100L // Delay between retries in ms
)