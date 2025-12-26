package com.obdreader.data.obd.protocol

/**
 * ELM327 AT Commands enumeration with descriptions and expected responses.
 */
enum class ATCommand(
    val command: String,
    val description: String,
    val expectedResponse: String? = "OK",
    val delayAfter: Long = 0
) {
    // Reset Commands
    RESET("ATZ", "Reset adapter", null, 1000),
    WARM_START("ATWS", "Warm start (soft reset)", null, 500),
    SET_DEFAULTS("ATD", "Set all to defaults"),
    
    // Echo Control
    ECHO_OFF("ATE0", "Disable echo"),
    ECHO_ON("ATE1", "Enable echo"),
    
    // Linefeed Control
    LINEFEEDS_OFF("ATL0", "Disable linefeeds"),
    LINEFEEDS_ON("ATL1", "Enable linefeeds"),
    
    // Spaces Control
    SPACES_OFF("ATS0", "Disable spaces in response"),
    SPACES_ON("ATS1", "Enable spaces in response"),
    
    // Headers Control
    HEADERS_OFF("ATH0", "Hide headers"),
    HEADERS_ON("ATH1", "Show headers"),
    
    // Protocol Commands
    PROTOCOL_AUTO("ATSP0", "Auto-detect protocol"),
    PROTOCOL_1("ATSP1", "SAE J1850 PWM"),
    PROTOCOL_2("ATSP2", "SAE J1850 VPW"),
    PROTOCOL_3("ATSP3", "ISO 9141-2"),
    PROTOCOL_4("ATSP4", "ISO 14230-4 KWP (5 baud)"),
    PROTOCOL_5("ATSP5", "ISO 14230-4 KWP (fast)"),
    PROTOCOL_6("ATSP6", "ISO 15765-4 CAN (11-bit, 500k)"),
    PROTOCOL_7("ATSP7", "ISO 15765-4 CAN (29-bit, 500k)"),
    PROTOCOL_8("ATSP8", "ISO 15765-4 CAN (11-bit, 250k)"),
    PROTOCOL_9("ATSP9", "ISO 15765-4 CAN (29-bit, 250k)"),
    PROTOCOL_A("ATSPA", "SAE J1939 CAN"),
    
    // Protocol Query
    DESCRIBE_PROTOCOL("ATDP", "Describe current protocol (text)"),
    DESCRIBE_PROTOCOL_NUM("ATDPN", "Describe current protocol (number)"),
    
    // Timing Commands
    ADAPTIVE_TIMING_OFF("ATAT0", "Adaptive timing off"),
    ADAPTIVE_TIMING_AUTO("ATAT1", "Adaptive timing auto"),
    ADAPTIVE_TIMING_AGGRESSIVE("ATAT2", "Adaptive timing aggressive"),
    
    // Information Commands
    IDENTIFY("ATI", "Adapter identification"),
    READ_VOLTAGE("ATRV", "Read battery voltage"),
    
    // CAN Commands
    CAN_FLOW_CONTROL("ATFCSH", "Set flow control header"),
    CAN_RECEIVE_ADDRESS("ATCRA", "Set CAN receive address"),
    SET_HEADER("ATSH", "Set header"),
    
    // Other
    PROTOCOL_CLOSE("ATPC", "Protocol close"),
    BUFFER_DUMP("ATBD", "Buffer dump"),
    MONITOR_ALL("ATMA", "Monitor all");
    
    companion object {
        /**
         * Set specific timeout value (x * 4ms).
         */
        fun setTimeout(multiplier: Int): String = "ATST${multiplier.toString(16).uppercase().padStart(2, '0')}"
        
        /**
         * Set header for specific ECU.
         */
        fun setHeader(header: String): String = "ATSH$header"
        
        /**
         * Set CAN receive address filter.
         */
        fun setCANReceiveAddress(address: String): String = "ATCRA$address"
    }
}
