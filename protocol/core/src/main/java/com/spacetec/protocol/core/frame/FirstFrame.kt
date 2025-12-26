package com.spacetec.protocol.core.frame

/**
 * ISO-TP First Frame (FF)
 * Used for the first frame of multi-frame messages (8+ bytes of data)
 * Format: [PCI: 4 bits][Length: 12 bits][Data: 6 bytes]
 */
data class FirstFrame(
    override val canId: Int,
    val totalLength: Int,
    override val data: ByteArray
) : Frame {
    
    init {
        require(data.size == 6) { "First frame data must be exactly 6 bytes, got ${data.size} bytes" }
        require(totalLength > 7) { "First frame total length must be greater than 7, got $totalLength" }
    }
    
    override val length: Int
        get() = totalLength
    
    override fun toByteArray(): ByteArray {
        val pci = (0x10 shl 4) or ((totalLength shr 8) and 0x0F) // PCI = 0x10, upper length bits
        val frame = ByteArray(8)
        frame[0] = pci.toByte()
        frame[1] = (totalLength and 0xFF).toByte() // Lower length bits
        System.arraycopy(data, 0, frame, 2, data.size)
        return frame
    }
    
    override fun getType(): FrameType = FrameType.FIRST_FRAME
    
    companion object {
        /**
         * Parse a FirstFrame from raw CAN data
         */
        fun fromByteArray(canId: Int, raw: ByteArray): FirstFrame? {
            if (raw.size < 8) return null // First frame must be at least 8 bytes
            
            val pci = raw[0].toInt() and 0xFF
            val frameType = (pci shr 4) and 0x0F
            
            if (frameType != 0x01) return null // Not a first frame
            
            val upperLength = pci and 0x0F
            val lowerLength = raw[1].toInt() and 0xFF
            val totalLength = (upperLength shl 8) or lowerLength
            
            // Validate total length is reasonable (not zero or excessively large)
            if (totalLength <= 7) return null // First frames are for multi-frame messages only
            if (totalLength > 4095) return null // Max ISO-TP length is 4095 bytes
            
            // Ensure we have at least 6 bytes of data after PCI and length
            if (raw.size < 8) return null
            
            val data = raw.sliceArray(2 until 8) // 6 bytes of data
            return FirstFrame(canId, totalLength, data)
        }
    }
}