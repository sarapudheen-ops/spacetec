package com.spacetec.protocol.core.frame

/**
 * ISO-TP Single Frame (SF)
 * Used for messages that fit in a single CAN frame (≤ 7 bytes of data)
 * Format: [PCI: 4 bits][Length: 4 bits][Data: 0-7 bytes]
 */
data class SingleFrame(
    override val canId: Int,
    override val data: ByteArray
) : Frame {
    
    init {
        require(data.size <= 7) { "Single frame data must be 7 bytes or less, got ${data.size} bytes" }
    }
    
    override val length: Int
        get() = data.size
    
    override fun toByteArray(): ByteArray {
        val pci = (0x00 shl 4) or (data.size and 0x0F) // PCI = 0x00, Length = data size
        val frame = ByteArray(1 + data.size)
        frame[0] = pci.toByte()
        System.arraycopy(data, 0, frame, 1, data.size)
        return frame
    }
    
    override fun getType(): FrameType = FrameType.SINGLE_FRAME
    
    companion object {
        /**
         * Parse a SingleFrame from raw CAN data
         */
        fun fromByteArray(canId: Int, raw: ByteArray): SingleFrame? {
            if (raw.isEmpty()) return null
            
            val pci = raw[0].toInt() and 0xFF
            val frameType = (pci shr 4) and 0x0F
            val length = pci and 0x0F
            
            if (frameType != 0x00) return null // Not a single frame
            if (raw.size < length + 1) return null // Not enough data
            
            val data = raw.sliceArray(1 until minOf(raw.size, 1 + length))
            return SingleFrame(canId, data)
        }
    }
}