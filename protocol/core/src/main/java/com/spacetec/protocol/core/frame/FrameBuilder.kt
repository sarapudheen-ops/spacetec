package com.spacetec.protocol.core.frame

/**
 * ISO-TP Frame Builder
 * Builds ISO-TP frames from data according to ISO 15765-4
 */
object FrameBuilder {
    
    /**
     * Build frames from a complete message
     * @param canId CAN ID for the frames
     * @param data The complete message data to be framed
     * @return List of ISO-TP frames
     */
    fun buildFrames(canId: Int, data: ByteArray): List<Frame> {
        val frames = mutableListOf<Frame>()
        
        if (data.size <= 7) {
            // Single frame for small messages
            frames.add(SingleFrame(canId, data))
        } else {
            // Multi-frame message requires first frame + consecutive frames
            val firstFrameData = data.sliceArray(0 until 6)
            frames.add(FirstFrame(canId, data.size, firstFrameData))
            
            // Create consecutive frames for remaining data
            var offset = 6
            var sequenceNumber = 1 // Starts at 1, wraps around after 15
            
            while (offset < data.size) {
                val remaining = data.size - offset
                val frameSize = minOf(remaining, 7)
                val frameData = data.sliceArray(offset until offset + frameSize)
                
                frames.add(ConsecutiveFrame(canId, sequenceNumber, frameData))
                
                offset += frameSize
                sequenceNumber = (sequenceNumber + 1) and 0x0F // Wrap around after 15
            }
        }
        
        return frames
    }
    
    /**
     * Build a flow control frame
     * @param canId CAN ID for the frame
     * @param flowStatus Flow status (CONTINUE, WAIT, OVERFLOW)
     * @param blockSize Block size (number of CFs before next FC)
     * @param separationTime Minimum separation time between CFs (ms)
     * @return Flow control frame
     */
    fun buildFlowControlFrame(
        canId: Int,
        flowStatus: FlowStatus,
        blockSize: Int = 0,
        separationTime: Int = 0
    ): FlowControlFrame {
        return FlowControlFrame(canId, flowStatus, blockSize, separationTime)
    }
    
    /**
     * Build a flow control frame to continue transmission
     * @param canId CAN ID for the frame
     * @param blockSize Block size (0 = infinite)
     * @param separationTime Minimum separation time between CFs (ms)
     * @return Flow control frame with CONTINUE status
     */
    fun buildContinueFrame(canId: Int, blockSize: Int = 0, separationTime: Int = 0): FlowControlFrame {
        return buildFlowControlFrame(canId, FlowStatus.CONTINUE, blockSize, separationTime)
    }
    
    /**
     * Build a flow control frame to request wait
     * @param canId CAN ID for the frame
     * @return Flow control frame with WAIT status
     */
    fun buildWaitFrame(canId: Int): FlowControlFrame {
        return buildFlowControlFrame(canId, FlowStatus.WAIT, 0, 0)
    }
    
    /**
     * Build a flow control frame to indicate overflow
     * @param canId CAN ID for the frame
     * @return Flow control frame with OVERFLOW status
     */
    fun buildOverflowFrame(canId: Int): FlowControlFrame {
        return buildFlowControlFrame(canId, FlowStatus.OVERFLOW, 0, 0)
    }
}