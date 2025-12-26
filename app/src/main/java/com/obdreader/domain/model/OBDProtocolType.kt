package com.obdreader.domain.model

/**
 * OBD-II protocol types supported by ELM327.
 */
enum class OBDProtocolType(
    val id: Int,
    val description: String,
    val isCAN: Boolean = false
) {
    SAE_J1850_PWM(1, "SAE J1850 PWM"),
    SAE_J1850_VPW(2, "SAE J1850 VPW"),
    ISO_9141_2(3, "ISO 9141-2"),
    ISO_14230_4_KWP_5BAUD(4, "ISO 14230-4 KWP (5 baud)"),
    ISO_14230_4_KWP_FAST(5, "ISO 14230-4 KWP (fast)"),
    ISO_15765_4_CAN_11BIT_500K(6, "ISO 15765-4 CAN (11-bit, 500k)", true),
    ISO_15765_4_CAN_29BIT_500K(7, "ISO 15765-4 CAN (29-bit, 500k)", true),
    ISO_15765_4_CAN_11BIT_250K(8, "ISO 15765-4 CAN (11-bit, 250k)", true),
    ISO_15765_4_CAN_29BIT_250K(9, "ISO 15765-4 CAN (29-bit, 250k)", true),
    SAE_J1939_CAN(10, "SAE J1939 CAN", true);
    
    companion object {
        fun fromId(id: Int): OBDProtocolType? = values().find { it.id == id }
    }
}
