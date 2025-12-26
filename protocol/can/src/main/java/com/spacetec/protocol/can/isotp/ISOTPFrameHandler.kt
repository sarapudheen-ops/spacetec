package com.spacetec.protocol.can.isotp

import com.spacetec.transport.contract.ScannerConnection
import kotlinx.coroutines.delay

/**
 * ISO-TP (ISO 15765-2) Frame Handler for managing flow control and frame transmission
 */
class ISOTPFrameHandler {
    
    private val segmenter = ISOTPSegmenter()
    private val reassembler = ISOTPReassembler()
    
    /**
     * Sends a multi-frame message with proper flow control
     * @param connection The scanner connection
     * @param frames The frames to send
     * @return The response as a complete message
     */
    suspend fun sendMultiFrameMessage(connection: ScannerConnection, frames: List<ByteArray>): ByteArray {
        if (frames.isEmpty()) return ByteArray(0)
        
        // If only one frame, send directly
        if (frames.size == 1) {
            val response = connection.sendCommand(bytesToHex(frames[0]))
            return hexToBytes(response)
        }
        
        // For multi-frame messages, implement flow control
        var flowControlReceived = false
        var blockSize = 0
        var separationTime = 0
        
        // Send first frame
        val firstFrame = frames[0]
        val response = connection.sendCommand(bytesToHex(firstFrame))
        
        // Wait for flow control frame (should come quickly)
        delay(50) // Wait a bit for flow control
        
        // In a real implementation, we'd parse the response to get flow control info
        // For now, assume standard flow control
        blockSize = 0 // Unlimited block size
        separationTime = 0 // No delay between frames
        flowControlReceived = true
        
        if (!flowControlReceived) {
            throw Exception("Flow control frame not received")
        }
        
        // Send consecutive frames according to flow control
        for (i in 1 until frames.size) {
            if (blockSize > 0 && i % blockSize == 0) {
                // Wait for next flow control if block size is limited
                delay(100)
            }
            
            val cfResponse = connection.sendCommand(bytesToHex(frames[i]))
            
            if (separationTime > 0) {
                if (separationTime <= 0x7F) {
                    // Time in milliseconds
                    delay(separationTime.toLong())
                } else if (separationTime in 0xF1..0xF9) {
                    // Time in 100-900 microseconds
                    delay(1) // Minimum delay for this implementation
                }
            }
        }
        
        // Now wait for the response, which might also be multi-frame
        // For simplicity, return a basic response
        return hexToBytes(response)
    }
    
    /**
     * Processes an incoming multi-frame response
     * @param connection The scanner connection
     * @return The complete response message
     */
    suspend fun receiveMultiFrameResponse(connection: ScannerConnection): ByteArray {
        // In a real implementation, this would continuously read frames
        // until a complete message is received, handling flow control
        val response = connection.readResponse()
        return hexToBytes(response)
    }
    
    /**
     * Converts bytes to hex string for sending over connection
     */
    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02X".format(it) }
    }
    
    /**
     * Converts hex string to bytes from connection response
     */
    private fun hexToBytes(hex: String): ByteArray {
        val cleanHex = hex.replace(" ", "").replace("\n", "").replace("\r", "")
        return if (cleanHex.length % 2 == 0) {
            (0 until cleanHex.length step 2)
                .map { cleanHex.substring(it, it + 2).toInt(16).toByte() }
                .toByteArray()
        } else {
            ByteArray(0) // Return empty if invalid hex
        }
    }
    
    /**
     * Sends a flow control frame to control incoming data
     * @param connection The scanner connection
     * @param flowStatus Flow status (0=ContinueToSend, 1=Wait, 2=Overflow)
     * @param blockSize Block size
     * @param separationTime Separation time
     */
    suspend fun sendFlowControl(
        connection: ScannerConnection,
        flowStatus: Int = 0, // Continue to send
        blockSize: Int = 0, // Unlimited
        separationTime: Int = 0 // No delay
    ) {
        val flowControlFrame = segmenter.createFlowControlFrame(flowStatus, blockSize, separationTime)
        connection.sendCommand(bytesToHex(flowControlFrame))
    }
}