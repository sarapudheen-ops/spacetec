/**
 * MessageType.kt
 *
 * Defines types of diagnostic messages.
 */

package com.spacetec.protocol.core.message

/**
 * Types of diagnostic messages.
 */
enum class MessageType {
    REQUEST,
    RESPONSE,
    SINGLE_FRAME,
    FIRST_FRAME,
    CONSECUTIVE_FRAME,
    FLOW_CONTROL
}