package com.spacetec.protocol.core.frame

/**
 * ISO-TP Flow Control Frame (FC)
 * Used to control the flow of consecutive frames in multi-frame messages
 * Format: [PCI: 4 bits][FS: 4 bits][BS: 8 bits][STmin: 8 bits]
 */
data class FlowControlFrame(
    override val canId: Int,
    val flowStatus: FlowStatus,
    val blockSize: Int,      // Number of CFs before next FC (0 = infinite)
    val separationTime: Int  // Minimum separation time between CFs in ms
) : Frame {
    
    override val data: ByteArray
        get() = byteArrayOf(
            ((flowStatus.ordinal and 0x0F) or 0x30).toByte(), // PCI = 0x30, Flow Status
            blockSize.toByte(),
            separationTime.toByte()
        )
    
    override val length: Int = 3 // Fixed length for flow control frame
    
    override fun toByteArray(): ByteArray = data
    
    override fun getType(): FrameType = FrameType.FLOW_CONTROL_FRAME
    
    companion object {
        /**
         * Parse a FlowControlFrame from raw CAN data
         */
        fun fromByteArray(canId: Int, raw: ByteArray): FlowControlFrame? {
            if (raw.size < 3) return null
            
            val pci = raw[0].toInt() and 0xFF
            val frameType = (pci shr 4) and 0x0F
            val flowStatusOrdinal = pci and 0x0F
            
            if (frameType != 0x03) return null // Not a flow control frame
            if (flowStatusOrdinal > 2) return null // Invalid flow status
            
            val flowStatus = FlowStatus.values()[flowStatusOrdinal]
            val blockSize = raw[1].toInt() and 0xFF
            val separationTime = raw[2].toInt() and 0xFF
            
            return FlowControlFrame(canId, flowStatus, blockSize, separationTime)
        }
    }
}