package com.spacetec.protocol.can

import com.spacetec.protocol.core.frame.Frame
import com.spacetec.protocol.core.frame.FrameType

/**
 * CAN Frame implementation for the SpaceTec diagnostic system
 * @param canId The CAN identifier
 * @param data The frame data (max 8 bytes)
 * @param isExtendedFrame Whether this is an extended frame (29-bit) or standard (11-bit)
 */
data class CANFrame(
    override val canId: Int,
    override val data: ByteArray = ByteArray(0),
    val isExtendedFrame: Boolean = true
) : Frame {
    
    init {
        require(data.size <= 8) { "CAN frame data cannot exceed 8 bytes, got ${data.size} bytes" }
    }
    
    override val length: Int
        get() = data.size
    
    override fun toByteArray(): ByteArray = data
    
    override fun getType(): FrameType = if (isExtendedFrame) FrameType.EXTENDED_CAN else FrameType.STANDARD_CAN
    
    override fun toString(): String {
        return "CANFrame(canId=0x${canId.toString(16)}, data=[${data.joinToString(", ") { String.format("0x%02X", it) }}], extended=${isExtendedFrame})"
    }
    
    companion object {
        /**
         * Creates a CAN frame from raw data
         * @param canId The CAN identifier
         * @param data The frame data
         * @param isExtendedFrame Whether this is an extended frame
         * @return A CANFrame instance or null if invalid
         */
        fun create(canId: Int, data: ByteArray, isExtendedFrame: Boolean = true): CANFrame? {
            return try {
                CANFrame(canId, data, isExtendedFrame)
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }
}