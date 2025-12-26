package com.spacetec.protocol.can.isotp

import com.spacetec.protocol.core.frame.Frame
import com.spacetec.protocol.core.frame.FrameType

/**
 * ISO-TP (ISO 15765-2) Segmenter for splitting large messages into CAN frames
 */
class ISOTPSegmenter {
    
    /**
     * Segments a large message into ISO-TP frames
     * @param data The data to be segmented
     * @return List of CAN frames containing the segmented data
     */
    fun segment(data: ByteArray): List<ByteArray> {
        val frames = mutableListOf<ByteArray>()
        
        if (data.size <= 7) {
            // Single frame - data fits in one CAN frame
            val frame = ByteArray(data.size + 1)
            frame[0] = (0x00 or data.size).toByte() // Single frame PCI
            System.arraycopy(data, 0, frame, 1, data.size)
            frames.add(frame)
        } else if (data.size <= 0xFFF) {
            // First frame + consecutive frames
            val totalLength = data.size
            val firstFrameData = ByteArray(8)
            
            // First frame PCI (0x10 = first frame, upper 4 bits = length upper bits)
            val pci = (0x10 shl 4) or ((totalLength shr 8) and 0x0F)
            firstFrameData[0] = pci.toByte()
            firstFrameData[1] = (totalLength and 0xFF).toByte() // Lower length bits
            System.arraycopy(data, 0, firstFrameData, 2, 6) // First 6 bytes of data
            
            frames.add(firstFrameData)
            
            // Consecutive frames
            var offset = 6
            var sequenceNumber = 1
            while (offset < totalLength) {
                val remaining = totalLength - offset
                val frameSize = minOf(7, remaining) // Max 7 bytes per consecutive frame
                val cfData = ByteArray(8)
                
                // Consecutive frame PCI (0x20 + sequence number)
                cfData[0] = ((0x20 or (sequenceNumber and 0x0F)) and 0xFF).toByte()
                System.arraycopy(data, offset, cfData, 1, frameSize)
                
                frames.add(cfData)
                
                offset += frameSize
                sequenceNumber = (sequenceNumber + 1) % 16
            }
        } else {
            // Extended addressing for messages > 4095 bytes
            val totalLength = data.size
            val firstFrameData = ByteArray(8)
            
            // First frame PCI with extended addressing (0x10 + length in next 4 bytes)
            firstFrameData[0] = 0x10.toByte() // First frame
            firstFrameData[1] = 0x00.toByte() // Extended address (0 for now)
            firstFrameData[2] = ((totalLength shr 24) and 0xFF).toByte() // Length bytes
            firstFrameData[3] = ((totalLength shr 16) and 0xFF).toByte()
            firstFrameData[4] = ((totalLength shr 8) and 0xFF).toByte()
            firstFrameData[5] = (totalLength and 0xFF).toByte()
            
            // Fill with remaining data
            val dataBytesAvailable = minOf(3, totalLength) // Only 3 bytes available after length
            System.arraycopy(data, 0, firstFrameData, 5, dataBytesAvailable)
            
            frames.add(firstFrameData)
            
            // Consecutive frames
            var offset = dataBytesAvailable
            var sequenceNumber = 1
            while (offset < totalLength) {
                val remaining = totalLength - offset
                val frameSize = minOf(7, remaining)
                val cfData = ByteArray(8)
                
                cfData[0] = ((0x20 or (sequenceNumber and 0x0F)) and 0xFF).toByte()
                System.arraycopy(data, offset, cfData, 1, frameSize)
                
                frames.add(cfData)
                
                offset += frameSize
                sequenceNumber = (sequenceNumber + 1) % 16
            }
        }
        
        return frames
    }
    
    /**
     * Creates a flow control frame
     * @param flowStatus Flow status (0=ContinueToSend, 1=Wait, 2=Overflow)
     * @param blockSize Block size (0=disable, 1-0xFF=number of frames)
     * @param separationTime Separation time (0x00-0x7F: 0-127ms, 0xF1-0xF9: 100-900us)
     */
    fun createFlowControlFrame(flowStatus: Int, blockSize: Int, separationTime: Int): ByteArray {
        val frame = ByteArray(8)
        frame[0] = 0x30.toByte() // Flow control PCI
        frame[1] = flowStatus.toByte()
        frame[2] = blockSize.toByte()
        frame[3] = separationTime.toByte()
        // Remaining bytes are padding
        return frame
    }
}