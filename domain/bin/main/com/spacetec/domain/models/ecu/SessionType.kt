package com.spacetec.domain.models.ecu

/**
 * Diagnostic session types as defined by UDS (ISO 14229).
 */
enum class SessionType(val id: Int, val description: String) {
    DEFAULT(0x01, "Default Session"),
    PROGRAMMING(0x02, "Programming Session"),
    EXTENDED(0x03, "Extended Diagnostic Session"),
    SAFETY_SYSTEM(0x04, "Safety System Diagnostic Session");

    companion object {
        fun fromId(id: Int): SessionType? = entries.find { it.id == id }
    }
}
