package com.spacetec.protocol.core.frame

/**
 * ISO-TP Frame Parser
 * Parses ISO-TP frames from raw CAN data according to ISO 15765-4
 */
object FrameParser {
    
    /**
     * Parse a single ISO-TP frame from raw CAN data
     * @param canId CAN ID of the frame
     * @param raw Raw CAN frame data
     * @return Parsed ISO-TP frame or null if parsing fails
     */
    fun parseFrame(canId: Int, raw: ByteArray): Frame? {
        if (raw.isEmpty()) return null
        
        val pci = raw[0].toInt() and 0xFF
        val frameType = (pci shr 4) and 0x0F
        
        return when (frameType) {
            0x00 -> SingleFrame.fromByteArray(canId, raw)
            0x01 -> FirstFrame.fromByteArray(canId, raw)
            0x02 -> ConsecutiveFrame.fromByteArray(canId, raw)
            0x03 -> FlowControlFrame.fromByteArray(canId, raw)
            else -> null
        }
    }
    
    /**
     * Parse multiple frames from a sequence of raw CAN data
     * @param canId CAN ID of the frames
     * @param rawDataList List of raw CAN frame data
     * @return List of parsed ISO-TP frames
     */
    fun parseFrames(canId: Int, rawDataList: List<ByteArray>): List<Frame> {
        return rawDataList.mapNotNull { raw ->
            parseFrame(canId, raw)
        }
    }
    
    /**
     * Reassemble a complete message from a sequence of ISO-TP frames
     * @param frames Sequence of ISO-TP frames
     * @return Complete reassembled message or null if reassembly fails
     */
    fun reassembleMessage(frames: List<Frame>): ByteArray? {
        if (frames.isEmpty()) return null
        
        // Check if we have a single frame
        if (frames.size == 1 && frames[0] is SingleFrame) {
            return frames[0].data
        }
        
        // Check if we have a multi-frame message (first frame + consecutive frames)
        if (frames.isEmpty() || frames[0] !is FirstFrame) return null
        
        val firstFrame = frames[0] as FirstFrame
        val expectedTotalLength = firstFrame.totalLength
        val messageData = mutableListOf<Byte>()
        
        // Add data from first frame
        messageData.addAll(firstFrame.data.toList())
        
        // Add data from consecutive frames
        for (i in 1 until frames.size) {
            val frame = frames[i]
            if (frame !is ConsecutiveFrame) return null // Invalid sequence
            
            messageData.addAll(frame.data.toList())
        }
        
        // Check if we have the expected total length
        if (messageData.size != expectedTotalLength) {
            return null // Incomplete message
        }
        
        return messageData.toByteArray()
    }
    
    /**
     * Validate a sequence of frames to ensure they form a valid ISO-TP message
     * @param frames Sequence of frames to validate
     * @return True if the sequence is valid, false otherwise
     */
    fun validateFrameSequence(frames: List<Frame>): Boolean {
        if (frames.isEmpty()) return false
        
        val firstFrame = frames[0]
        
        return when (firstFrame) {
            is SingleFrame -> frames.size == 1
            is FirstFrame -> validateMultiFrameSequence(frames, firstFrame.totalLength)
            is ConsecutiveFrame -> false // Multi-frame messages must start with first frame
            is FlowControlFrame -> false // Flow control frames are not part of data messages
            else -> false
        }
    }
    
    private fun validateMultiFrameSequence(frames: List<Frame>, expectedTotalLength: Int): Boolean {
        if (frames.size < 2) return false // Need at least first frame + one consecutive frame
        
        var expectedSequenceNumber = 1 // After first frame, consecutive frames start at 1
        var totalDataLength = frames[0].data.size // Start with first frame data length
        
        for (i in 1 until frames.size) {
            val frame = frames[i]
            if (frame !is ConsecutiveFrame) return false
            
            // Check sequence number
            if (frame.sequenceNumber != expectedSequenceNumber) return false
            
            totalDataLength += frame.data.size
            expectedSequenceNumber = (expectedSequenceNumber + 1) and 0x0F // Wrap around after 15
        }
        
        // Check if total length matches expected
        return totalDataLength == expectedTotalLength
    }
}