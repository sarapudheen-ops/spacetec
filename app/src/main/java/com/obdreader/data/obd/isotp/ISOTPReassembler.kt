package com.obdreader.data.obd.isotp

/**
 * Reassembles multi-frame ISO-TP messages.
 */
class ISOTPReassembler {
    private val buffer = mutableListOf<Byte>()
    private var expectedLength = 0
    private var nextSequence = 1
    
    /**
     * Process an ISO-TP frame and return complete message if ready.
     */
    fun processFrame(frame: ISOTPFrame): ByteArray? {
        return when (frame.type) {
            ISOTPFrame.FrameType.SINGLE -> {
                reset()
                frame.data
            }
            ISOTPFrame.FrameType.FIRST -> {
                reset()
                expectedLength = frame.totalLength
                buffer.addAll(frame.data.toList())
                nextSequence = 1
                null
            }
            ISOTPFrame.FrameType.CONSECUTIVE -> {
                if (frame.sequenceNumber == nextSequence) {
                    buffer.addAll(frame.data.toList())
                    nextSequence = (nextSequence + 1) and 0x0F
                    
                    if (buffer.size >= expectedLength) {
                        val result = buffer.take(expectedLength).toByteArray()
                        reset()
                        result
                    } else null
                } else {
                    reset()
                    null
                }
            }
            ISOTPFrame.FrameType.FLOW_CONTROL -> null
        }
    }
    
    private fun reset() {
        buffer.clear()
        expectedLength = 0
        nextSequence = 1
    }
}
