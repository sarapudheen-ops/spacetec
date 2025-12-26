package com.spacetec.protocol.core.frame

/**
 * ISO-TP (ISO 15765-4) Frame interface
 * Represents a single CAN frame in the ISO-TP protocol
 */
interface Frame {
    val canId: Int
    val data: ByteArray
    val length: Int
    
    fun toByteArray(): ByteArray
    fun getType(): FrameType
}

/**
 * Frame type enumeration for ISO-TP
 */
enum class FrameType {
    SINGLE_FRAME,
    FIRST_FRAME,
    CONSECUTIVE_FRAME,
    FLOW_CONTROL_FRAME
}

/**
 * Frame control information for ISO-TP
 */
data class FrameControlInfo(
    val type: FrameType,
    val sequenceNumber: Int = 0,
    val blockSize: Int = 0,
    val separationTime: Int = 0,
    val flowStatus: FlowStatus = FlowStatus.CONTINUE
)

/**
 * Flow status for flow control frames
 */
enum class FlowStatus {
    CONTINUE,
    WAIT,
    OVERFLOW
}