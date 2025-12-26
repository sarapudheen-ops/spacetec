package com.obdreader.data.obd.protocol

import android.util.Log
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * ISO 15765-2 Transport Protocol handler for multi-frame messages
 */
class ISOTPHandler {
    
    companion object {
        private const val TAG = "ISOTPHandler"
        
        // Frame type identifiers (first nibble)
        const val FRAME_TYPE_SINGLE = 0x00
        const val FRAME_TYPE_FIRST = 0x10
        const val FRAME_TYPE_CONSECUTIVE = 0x20
        const val FRAME_TYPE_FLOW_CONTROL = 0x30
        
        // Flow control flags
        const val FC_CONTINUE = 0x00
        const val FC_WAIT = 0x01
        const val FC_ABORT = 0x02
        
        // Default flow control parameters
        const val DEFAULT_BLOCK_SIZE = 0x00  // No limit
        const val DEFAULT_ST_MIN = 0x00      // No minimum separation time
        
        // Timeouts
        const val FRAME_TIMEOUT_MS = 1000L
        const val REASSEMBLY_TIMEOUT_MS = 5000L
    }
    
    private var expectedLength = 0
    private var receivedData = mutableListOf<Byte>()
    private var expectedSequence = 1
    private var reassemblyInProgress = false
    
    /**
     * Represents a parsed ISO-TP frame
     */
    sealed class ISOTPFrame {
        data class SingleFrame(val length: Int, val data: ByteArray) : ISOTPFrame()
        data class FirstFrame(val totalLength: Int, val data: ByteArray) : ISOTPFrame()
        data class ConsecutiveFrame(val sequence: Int, val data: ByteArray) : ISOTPFrame()
        data class FlowControl(val flag: Int, val blockSize: Int, val separationTime: Int) : ISOTPFrame()
        data class ParseError(val message: String) : ISOTPFrame()
    }
    
    /**
     * Parse raw bytes into an ISO-TP frame
     */
    fun parseFrame(data: ByteArray): ISOTPFrame {
        if (data.isEmpty()) {
            return ISOTPFrame.ParseError("Empty data")
        }
        
        val pci = data[0].toInt() and 0xFF
        val frameType = pci and 0xF0
        
        return when (frameType) {
            FRAME_TYPE_SINGLE -> parseSingleFrame(data)
            FRAME_TYPE_FIRST -> parseFirstFrame(data)
            FRAME_TYPE_CONSECUTIVE -> parseConsecutiveFrame(data)
            FRAME_TYPE_FLOW_CONTROL -> parseFlowControl(data)
            else -> ISOTPFrame.ParseError("Unknown frame type: 0x${frameType.toString(16)}")
        }
    }
    
    private fun parseSingleFrame(data: ByteArray): ISOTPFrame {
        val length = data[0].toInt() and 0x0F
        if (length == 0 || length > 7) {
            return ISOTPFrame.ParseError("Invalid single frame length: $length")
        }
        if (data.size < length + 1) {
            return ISOTPFrame.ParseError("Insufficient data for single frame")
        }
        return ISOTPFrame.SingleFrame(length, data.sliceArray(1..length))
    }
    
    private fun parseFirstFrame(data: ByteArray): ISOTPFrame {
        if (data.size < 2) {
            return ISOTPFrame.ParseError("Insufficient data for first frame")
        }
        
        val lengthHigh = (data[0].toInt() and 0x0F) shl 8
        val lengthLow = data[1].toInt() and 0xFF
        val totalLength = lengthHigh or lengthLow
        
        if (totalLength == 0) {
            return ISOTPFrame.ParseError("Invalid first frame length: 0")
        }
        
        val frameData = if (data.size > 2) data.sliceArray(2 until data.size) else ByteArray(0)
        return ISOTPFrame.FirstFrame(totalLength, frameData)
    }
    
    private fun parseConsecutiveFrame(data: ByteArray): ISOTPFrame {
        val sequence = data[0].toInt() and 0x0F
        val frameData = if (data.size > 1) data.sliceArray(1 until data.size) else ByteArray(0)
        return ISOTPFrame.ConsecutiveFrame(sequence, frameData)
    }
    
    private fun parseFlowControl(data: ByteArray): ISOTPFrame {
        if (data.size < 3) {
            return ISOTPFrame.ParseError("Insufficient data for flow control")
        }
        
        val flag = data[0].toInt() and 0x0F
        val blockSize = data[1].toInt() and 0xFF
        val stMin = data[2].toInt() and 0xFF
        
        return ISOTPFrame.FlowControl(flag, blockSize, stMin)
    }
    
    /**
     * Create a flow control frame
     */
    fun createFlowControl(
        flag: Int = FC_CONTINUE,
        blockSize: Int = DEFAULT_BLOCK_SIZE,
        stMin: Int = DEFAULT_ST_MIN
    ): ByteArray {
        return byteArrayOf(
            (FRAME_TYPE_FLOW_CONTROL or flag).toByte(),
            blockSize.toByte(),
            stMin.toByte()
        )
    }
    
    /**
     * Process a frame and return complete data when reassembly is done
     */
    fun processFrame(frame: ISOTPFrame): ByteArray? {
        return when (frame) {
            is ISOTPFrame.SingleFrame -> {
                reset()
                frame.data
            }
            
            is ISOTPFrame.FirstFrame -> {
                reset()
                expectedLength = frame.totalLength
                receivedData.addAll(frame.data.toList())
                reassemblyInProgress = true
                expectedSequence = 1
                Log.d(TAG, "First frame received, expecting $expectedLength bytes total")
                null // Need flow control and more frames
            }
            
            is ISOTPFrame.ConsecutiveFrame -> {
                if (!reassemblyInProgress) {
                    Log.w(TAG, "Unexpected consecutive frame, no reassembly in progress")
                    return null
                }
                
                val actualSequence = frame.sequence
                val expectedSeq = expectedSequence % 16
                
                if (actualSequence != expectedSeq) {
                    Log.e(TAG, "Sequence error: expected $expectedSeq, got $actualSequence")
                    reset()
                    return null
                }
                
                receivedData.addAll(frame.data.toList())
                expectedSequence++
                
                Log.d(TAG, "Consecutive frame $actualSequence, total received: ${receivedData.size}/$expectedLength")
                
                if (receivedData.size >= expectedLength) {
                    val result = receivedData.take(expectedLength).toByteArray()
                    reset()
                    result
                } else {
                    null // Need more frames
                }
            }
            
            is ISOTPFrame.FlowControl -> {
                Log.d(TAG, "Flow control received: flag=${frame.flag}, bs=${frame.blockSize}, st=${frame.separationTime}")
                null // Flow control doesn't produce data
            }
            
            is ISOTPFrame.ParseError -> {
                Log.e(TAG, "Parse error: ${frame.message}")
                reset()
                null
            }
        }
    }
    
    /**
     * Check if reassembly is in progress
     */
    fun isReassembling(): Boolean = reassemblyInProgress
    
    /**
     * Get reassembly progress
     */
    fun getProgress(): Pair<Int, Int> = Pair(receivedData.size, expectedLength)
    
    /**
     * Reset the reassembly state
     */
    fun reset() {
        expectedLength = 0
        receivedData.clear()
        expectedSequence = 1
        reassemblyInProgress = false
    }
}
