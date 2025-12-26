package com.spacetec.protocol.can.isotp

import com.spacetec.protocol.core.frame.Frame
import com.spacetec.protocol.core.frame.FrameType

/**
 * ISO-TP (ISO 15765-2) Reassembler for combining CAN frames into complete messages
 */
class ISOTPReassembler {
    
    private var expectedLength: Int = 0
    private var receivedData = ByteArray(0)
    private var sequenceNumber: Int = 0
    private var isReceiving: Boolean = false
    
    /**
     * Reassembles ISO-TP frames into a complete message
     * @param frame The incoming CAN frame
     * @return True if the message is complete, false otherwise
     */
    fun processFrame(frame: ByteArray): Boolean {
        if (frame.isEmpty()) return false
        
        val pci = frame[0].toInt() and 0xFF
        val frameType = (pci shr 4) and 0x0F
        
        when (frameType) {
            0x00 -> { // Single frame
                val dataLength = pci and 0x0F
                if (frame.size < dataLength + 1) return false
                
                receivedData = frame.sliceArray(1 until (1 + dataLength))
                isReceiving = false
                return true
            }
            0x01 -> { // First frame
                if (frame.size < 8) return false
                
                val upperLength = pci and 0x0F
                val lowerLength = frame[1].toInt() and 0xFF
                expectedLength = (upperLength shl 8) or lowerLength
                
                // Copy data from first frame (bytes 2-7)
                val dataFromFirstFrame = frame.sliceArray(2 until 8)
                receivedData = dataFromFirstFrame
                sequenceNumber = 1
                isReceiving = true
                return false
            }
            0x02 -> { // Consecutive frame
                if (!isReceiving) return false
                
                val receivedSeqNum = pci and 0x0F
                if (receivedSeqNum != sequenceNumber) {
                    // Sequence error, reset
                    reset()
                    return false
                }
                
                // Copy data from this frame (bytes 1-end)
                val frameData = frame.sliceArray(1 until frame.size)
                receivedData += frameData
                
                // Check if we've received all expected data
                if (receivedData.size >= expectedLength) {
                    // Trim to expected length if necessary
                    if (receivedData.size > expectedLength) {
                        receivedData = receivedData.sliceArray(0 until expectedLength)
                    }
                    isReceiving = false
                    return true
                }
                
                // Increment sequence number
                sequenceNumber = (sequenceNumber + 1) % 16
                return false
            }
            0x03 -> { // Flow control frame
                // This is sent by receiver, not processed by reassembler
                return false
            }
        }
        
        return false
    }
    
    /**
     * Gets the complete reassembled message
     */
    fun getCompleteMessage(): ByteArray {
        return receivedData
    }
    
    /**
     * Checks if we're currently receiving a multi-frame message
     */
    fun isReceiving(): Boolean {
        return isReceiving
    }
    
    /**
     * Resets the reassembly state
     */
    fun reset() {
        expectedLength = 0
        receivedData = ByteArray(0)
        sequenceNumber = 0
        isReceiving = false
    }
    
    /**
     * Processes multiple frames until a complete message is received
     */
    fun processFrames(frames: List<ByteArray>): ByteArray? {
        reset()
        
        for (frame in frames) {
            if (processFrame(frame)) {
                return getCompleteMessage()
            }
        }
        
        return null // Message not complete
    }
}