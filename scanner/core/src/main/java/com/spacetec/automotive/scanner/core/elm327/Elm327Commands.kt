// scanner/core/src/main/kotlin/com/spacetec/automotive/scanner/core/elm327/Elm327Commands.kt
package com.spacetec.obd.scanner.core.elm327

/**
 * ELM327 AT Command definitions.
 * 
 * ELM327 is the most common OBD-II interface chip. These commands
 * configure the adapter's behavior, protocol settings, and retrieve
 * device information.
 */
object Elm327Commands {
    
    // ========================================================================
    // GENERAL COMMANDS
    // ========================================================================
    
    /** Reset all settings to defaults */
    const val RESET = "ATZ"
    
    /** Warm start (no full reset) */
    const val WARM_START = "ATWS"
    
    /** Set all defaults */
    const val SET_DEFAULTS = "ATD"
    
    /** Echo off */
    const val ECHO_OFF = "ATE0"
    
    /** Echo on */
    const val ECHO_ON = "ATE1"
    
    /** Linefeed off */
    const val LINEFEED_OFF = "ATL0"
    
    /** Linefeed on */
    const val LINEFEED_ON = "ATL1"
    
    /** Spaces off */
    const val SPACES_OFF = "ATS0"
    
    /** Spaces on */
    const val SPACES_ON = "ATS1"
    
    /** Headers off */
    const val HEADERS_OFF = "ATH0"
    
    /** Headers on */
    const val HEADERS_ON = "ATH1"
    
    /** Display DLC off */
    const val DLC_OFF = "ATD0"
    
    /** Display DLC on */
    const val DLC_ON = "ATD1"
    
    // ========================================================================
    // DEVICE INFORMATION
    // ========================================================================
    
    /** Get device identifier */
    const val DEVICE_ID = "ATI"
    
    /** Get device description */
    const val DEVICE_DESCRIPTION = "AT@1"
    
    /** Get stored identifier */
    const val STORED_ID = "AT@2"
    
    /** Read voltage */
    const val READ_VOLTAGE = "ATRV"
    
    /** Read ignition status */
    const val READ_IGNITION = "ATIGN"
    
    // ========================================================================
    // PROTOCOL COMMANDS
    // ========================================================================
    
    /** Set protocol to automatic */
    const val PROTOCOL_AUTO = "ATSP0"
    
    /** Set protocol (0-C) */
    fun setProtocol(protocol: Int): String = "ATSP$protocol"
    
    /** Try protocol (0-C) */
    fun tryProtocol(protocol: Int): String = "ATTP$protocol"
    
    /** Describe current protocol */
    const val DESCRIBE_PROTOCOL = "ATDP"
    
    /** Describe current protocol number */
    const val DESCRIBE_PROTOCOL_NUMBER = "ATDPN"
    
    /** Close protocol */
    const val CLOSE_PROTOCOL = "ATPC"
    
    // ========================================================================
    // CAN SPECIFIC COMMANDS
    // ========================================================================
    
    /** Set CAN ID filter */
    fun setCanIdFilter(id: Int): String = "ATCF%03X".format(id)
    
    /** Set CAN ID mask */
    fun setCanIdMask(mask: Int): String = "ATCM%03X".format(mask)
    
    /** Set CAN receive address */
    fun setCanReceiveAddress(address: Int): String = "ATCRA%03X".format(address)
    
    /** Set header (CAN ID) */
    fun setHeader(header: Int): String = "ATSH%03X".format(header)
    
    /** Set extended header (29-bit) */
    fun setExtendedHeader(header: Long): String = "ATSH%08X".format(header)
    
    /** CAN flow control */
    const val CAN_FLOW_CONTROL_ON = "ATCFC1"
    const val CAN_FLOW_CONTROL_OFF = "ATCFC0"
    
    /** CAN auto formatting */
    const val CAN_AUTO_FORMAT_ON = "ATCAF1"
    const val CAN_AUTO_FORMAT_OFF = "ATCAF0"
    
    /** Variable DLC on (allows less than 8 bytes) */
    const val VARIABLE_DLC_ON = "ATV1"
    const val VARIABLE_DLC_OFF = "ATV0"
    
    /** Set CAN baud rate divisor */
    fun setCanBaudDivisor(divisor: Int): String = "ATPB%02X%02X".format(divisor shr 8, divisor and 0xFF)
    
    // ========================================================================
    // TIMEOUT COMMANDS
    // ========================================================================
    
    /** Set timeout (x * 4ms, max 0xFF = 1020ms) */
    fun setTimeout(value: Int): String = "ATST%02X".format(value.coerceIn(0, 255))
    
    /** Set adaptive timing off */
    const val ADAPTIVE_TIMING_OFF = "ATAT0"
    
    /** Set adaptive timing mode 1 */
    const val ADAPTIVE_TIMING_1 = "ATAT1"
    
    /** Set adaptive timing mode 2 */
    const val ADAPTIVE_TIMING_2 = "ATAT2"
    
    // ========================================================================
    // K-LINE SPECIFIC COMMANDS
    // ========================================================================
    
    /** Fast init with specified address */
    fun fastInit(address: Int): String = "ATFI%02X".format(address)
    
    /** Slow init (5-baud) */
    const val SLOW_INIT = "ATSI"
    
    /** Set ISO baud rate */
    fun setIsoBaud(rate: Int): String = "ATIB%02X".format(rate)
    
    /** Set wakeup interval */
    fun setWakeupInterval(value: Int): String = "ATSW%02X".format(value)
    
    // ========================================================================
    // J1850 SPECIFIC COMMANDS
    // ========================================================================
    
    /** IFR (In-Frame Response) off */
    const val IFR_OFF = "ATIFR0"
    
    /** IFR on */
    const val IFR_ON = "ATIFR1"
    
    // ========================================================================
    // PROGRAMMABLE PARAMETERS
    // ========================================================================
    
    /** Read all programmable parameters */
    const val READ_PP_ALL = "ATPPS"
    
    /** Enable all programmable parameters */
    const val PP_ALL_ON = "ATPP FF ON"
    
    /** Disable all programmable parameters */
    const val PP_ALL_OFF = "ATPP FF OFF"
    
    // ========================================================================
    // MEMORY COMMANDS
    // ========================================================================
    
    /** Read stored data at address */
    fun readMemory(address: Int): String = "ATRD%02X".format(address)
    
    /** Store data at address */
    fun storeMemory(address: Int, data: Int): String = "ATSD%02X%02X".format(address, data)
    
    // ========================================================================
    // LOW POWER MODE
    // ========================================================================
    
    /** Enter low power mode */
    const val LOW_POWER = "ATLP"
    
    // ========================================================================
    // PROTOCOL NUMBERS
    // ========================================================================
    
    object ProtocolNumber {
        const val AUTO = 0
        const val SAE_J1850_PWM = 1
        const val SAE_J1850_VPW = 2
        const val ISO_9141_2 = 3
        const val ISO_14230_4_KWP_5BAUD = 4
        const val ISO_14230_4_KWP_FAST = 5
        const val ISO_15765_4_CAN_11BIT_500K = 6
        const val ISO_15765_4_CAN_29BIT_500K = 7
        const val ISO_15765_4_CAN_11BIT_250K = 8
        const val ISO_15765_4_CAN_29BIT_250K = 9
        const val SAE_J1939_CAN_29BIT_250K = 10 // 'A'
        const val USER_CAN_11BIT = 11 // 'B'
        const val USER_CAN_29BIT = 12 // 'C'
    }
    
    // ========================================================================
    // RESPONSE STRINGS
    // ========================================================================
    
    object Response {
        const val OK = "OK"
        const val ERROR = "?"
        const val NO_DATA = "NO DATA"
        const val UNABLE_TO_CONNECT = "UNABLE TO CONNECT"
        const val BUS_INIT_ERROR = "BUS INIT: ...ERROR"
        const val BUS_ERROR = "BUS ERROR"
        const val CAN_ERROR = "CAN ERROR"
        const val DATA_ERROR = "DATA ERROR"
        const val BUFFER_FULL = "BUFFER FULL"
        const val SEARCHING = "SEARCHING..."
        const val STOPPED = "STOPPED"
        const val ACT_ALERT = "ACT ALERT"
        const val LV_RESET = "LV RESET"
        
        fun isError(response: String): Boolean {
            val upper = response.uppercase().trim()
            return upper == ERROR ||
                    upper.contains("ERROR") ||
                    upper == NO_DATA ||
                    upper.contains("UNABLE TO CONNECT")
        }
        
        fun isSuccess(response: String): Boolean {
            val upper = response.uppercase().trim()
            return upper == OK || !isError(response)
        }
    }
}