package com.spacetec.protocol.core.frame

/**
 * ISO-TP Consecutive Frame (CF)
 * Used for subsequent frames in multi-frame messages
 * Format: [PCI: 4 bits][SN: 4 bits][Data: 1-7 bytes]
 */
data class ConsecutiveFrame(
    override val canId: Int,
    val sequenceNumber: Int, // 0-15, wraps around
    override val data: ByteArray
) : Frame {
    
    init {
        require(sequenceNumber in 0..15) { "Sequence number must be between 0 and 15, got $sequenceNumber" }
        require(data.size <= 7) { "Consecutive frame data must be 7 bytes or less, got ${data.size} bytes" }
    }
    
    override val length: Int
        get() = data.size
    
    override fun toByteArray(): ByteArray {
        val pci = (0x20 shl 4) or (sequenceNumber and 0x0F) // PCI = 0x20, Sequence Number
        val frame = ByteArray(1 + data.size)
        frame[0] = pci.toByte()
        System.arraycopy(data, 0, frame, 1, data.size)
        return frame
    }
    
    override fun getType(): FrameType = FrameType.CONSECUTIVE_FRAME
    
    companion object {
        /**
         * Parse a ConsecutiveFrame from raw CAN data
         */
        fun fromByteArray(canId: Int, raw: ByteArray): ConsecutiveFrame? {
            if (raw.isEmpty()) return null
            
            val pci = raw[0].toInt() and 0xFF
            val frameType = (pci shr 4) and 0x0F
            val sequenceNumber = pci and 0x0F
            
            if (frameType != 0x02) return null // Not a consecutive frame
            
            val data = raw.sliceArray(1 until raw.size)
            return ConsecutiveFrame(canId, sequenceNumber, data)
        }
    }
}