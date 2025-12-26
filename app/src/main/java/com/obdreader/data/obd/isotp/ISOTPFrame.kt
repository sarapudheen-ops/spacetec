package com.obdreader.data.obd.isotp

/**
 * ISO-TP frame types and data structure.
 */
data class ISOTPFrame(
    val type: FrameType,
    val data: ByteArray,
    val sequenceNumber: Int = 0,
    val totalLength: Int = 0
) {
    enum class FrameType {
        SINGLE,
        FIRST,
        CONSECUTIVE,
        FLOW_CONTROL
    }
    
    companion object {
        fun parse(data: ByteArray): ISOTPFrame? {
            if (data.isEmpty()) return null
            
            val pci = data[0].toInt() and 0xFF
            val frameType = when ((pci shr 4) and 0x0F) {
                0 -> FrameType.SINGLE
                1 -> FrameType.FIRST
                2 -> FrameType.CONSECUTIVE
                3 -> FrameType.FLOW_CONTROL
                else -> return null
            }
            
            return ISOTPFrame(
                type = frameType,
                data = data.sliceArray(1 until data.size),
                sequenceNumber = pci and 0x0F,
                totalLength = if (frameType == FrameType.FIRST) ((pci and 0x0F) shl 8) or (data.getOrNull(1)?.toInt() ?: 0) else 0
            )
        }
    }
}
