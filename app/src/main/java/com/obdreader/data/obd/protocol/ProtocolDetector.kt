package com.obdreader.data.obd.protocol

import com.obdreader.domain.model.OBDProtocolType

/**
 * Detects and validates OBD-II protocols.
 */
class ProtocolDetector {
    
    /**
     * Parse protocol from ATDPN response.
     */
    fun parseProtocolNumber(response: String): OBDProtocolType? {
        val cleanResponse = response.trim().uppercase()
        
        // ATDPN returns format "A6" (A = Auto-detected, 6 = protocol number)
        val protocolChar = cleanResponse.lastOrNull() ?: return null
        
        return when (protocolChar) {
            '1' -> OBDProtocolType.SAE_J1850_PWM
            '2' -> OBDProtocolType.SAE_J1850_VPW
            '3' -> OBDProtocolType.ISO_9141_2
            '4' -> OBDProtocolType.ISO_14230_4_KWP_5BAUD
            '5' -> OBDProtocolType.ISO_14230_4_KWP_FAST
            '6' -> OBDProtocolType.ISO_15765_4_CAN_11BIT_500K
            '7' -> OBDProtocolType.ISO_15765_4_CAN_29BIT_500K
            '8' -> OBDProtocolType.ISO_15765_4_CAN_11BIT_250K
            '9' -> OBDProtocolType.ISO_15765_4_CAN_29BIT_250K
            'A' -> OBDProtocolType.SAE_J1939_CAN
            else -> null
        }
    }
    
    /**
     * Parse protocol from ATDP text response.
     */
    fun parseProtocolDescription(response: String): OBDProtocolType? {
        val cleanResponse = response.uppercase()
        
        return when {
            "J1850 PWM" in cleanResponse -> OBDProtocolType.SAE_J1850_PWM
            "J1850 VPW" in cleanResponse -> OBDProtocolType.SAE_J1850_VPW
            "9141" in cleanResponse -> OBDProtocolType.ISO_9141_2
            "14230" in cleanResponse && "5" in cleanResponse -> OBDProtocolType.ISO_14230_4_KWP_5BAUD
            "14230" in cleanResponse -> OBDProtocolType.ISO_14230_4_KWP_FAST
            "15765" in cleanResponse && "11" in cleanResponse && "500" in cleanResponse -> 
                OBDProtocolType.ISO_15765_4_CAN_11BIT_500K
            "15765" in cleanResponse && "29" in cleanResponse && "500" in cleanResponse -> 
                OBDProtocolType.ISO_15765_4_CAN_29BIT_500K
            "15765" in cleanResponse && "11" in cleanResponse && "250" in cleanResponse -> 
                OBDProtocolType.ISO_15765_4_CAN_11BIT_250K
            "15765" in cleanResponse && "29" in cleanResponse && "250" in cleanResponse -> 
                OBDProtocolType.ISO_15765_4_CAN_29BIT_250K
            "J1939" in cleanResponse -> OBDProtocolType.SAE_J1939_CAN
            else -> null
        }
    }
    
    /**
     * Check if protocol is CAN-based.
     */
    fun isCANProtocol(protocol: OBDProtocolType): Boolean {
        return protocol in listOf(
            OBDProtocolType.ISO_15765_4_CAN_11BIT_500K,
            OBDProtocolType.ISO_15765_4_CAN_29BIT_500K,
            OBDProtocolType.ISO_15765_4_CAN_11BIT_250K,
            OBDProtocolType.ISO_15765_4_CAN_29BIT_250K,
            OBDProtocolType.SAE_J1939_CAN
        )
    }
    
    /**
     * Get all protocols to try in order.
     */
    fun getProtocolPriority(): List<ATCommand> {
        return listOf(
            ATCommand.PROTOCOL_6,  // CAN 11-bit 500k (most common)
            ATCommand.PROTOCOL_7,  // CAN 29-bit 500k
            ATCommand.PROTOCOL_8,  // CAN 11-bit 250k
            ATCommand.PROTOCOL_9,  // CAN 29-bit 250k
            ATCommand.PROTOCOL_5,  // KWP fast
            ATCommand.PROTOCOL_4,  // KWP 5 baud
            ATCommand.PROTOCOL_3,  // ISO 9141
            ATCommand.PROTOCOL_2,  // J1850 VPW
            ATCommand.PROTOCOL_1   // J1850 PWM
        )
    }
}
