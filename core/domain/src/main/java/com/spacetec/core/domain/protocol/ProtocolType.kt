/**
 * ProtocolType.kt
 *
 * Defines the different diagnostic protocol types supported by SpaceTec.
 * Domain enum for protocol type identification and configuration.
 */

package com.spacetec.core.domain.protocol

/**
 * Enumeration of supported diagnostic protocol types.
 * This domain enum defines all protocol types that the system can handle.
 */
enum class ProtocolType(
    val id: Int,
    val description: String,
    val maxBaudRate: Int = 500000
) {
    // OBD-II Protocols
    OBD_II_ISO_9141_2(1, "OBD-II ISO 9141-2", 10400),
    OBD_II_ISO_14230_4_KWP_FAST(2, "OBD-II ISO 14230-4 KWP Fast", 10400),
    OBD_II_ISO_14230_4_KWP_5BAUD(3, "OBD-II ISO 14230-4 KWP 5-baud", 10400),
    OBD_II_ISO_15765_4_CAN_11BIT_500K(4, "OBD-II ISO 15765-4 CAN 11-bit 500k", 500000),
    OBD_II_ISO_15765_4_CAN_29BIT_500K(5, "OBD-II ISO 15765-4 CAN 29-bit 500k", 500000),
    OBD_II_J1850_PWM(6, "OBD-II J1850 PWM", 41600),
    OBD_II_J1850_VPW(7, "OBD-II J1850 VPW", 10400),

    // UDS Protocols
    UDS_ISO_15765_4_CAN_11BIT_500K(8, "UDS ISO 15765-4 CAN 11-bit 500k", 500000),
    UDS_ISO_15765_4_CAN_29BIT_500K(9, "UDS ISO 15765-4 CAN 29-bit 500k", 500000),
    UDS_ISO_14229_4_DOIP(10, "UDS ISO 14229-4 DoIP"),

    // Manufacturer Specific
    VAG_KWP1281(11, "VAG KWP1281"),
    VAG_KWP2000(12, "VAG KWP2000"),
    BMW_DS2(13, "BMW DS2"),
    MERCEDES_XENTRY(14, "Mercedes Xentry");

    companion object {
        /**
         * Find a protocol type by its ID.
         */
        fun fromId(id: Int): ProtocolType? = values().find { it.id == id }
    }
}