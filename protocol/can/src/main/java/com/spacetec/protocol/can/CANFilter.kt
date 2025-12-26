package com.spacetec.protocol.can

/**
 * CAN Filter for filtering CAN messages based on ID and mask
 * @param id The CAN ID to filter on
 * @param mask The mask to apply to the CAN ID
 * @param isExtended Whether this is for extended (29-bit) or standard (11-bit) frames
 */
data class CANFilter(
    val id: Int,
    val mask: Int,
    val isExtended: Boolean = true
) {
    
    /**
     * Checks if a CAN frame matches this filter
     * @param frame The CAN frame to check
     * @return True if the frame matches the filter, false otherwise
     */
    fun matches(frame: CANFrame): Boolean {
        val maskedFrameId = frame.canId and mask
        val maskedFilterId = id and mask
        return maskedFrameId == maskedFilterId
    }
    
    /**
     * Creates a filter for a specific CAN ID with exact match
     * @param canId The CAN ID to match
     * @param isExtended Whether this is for extended frames
     * @return A CANFilter instance
     */
    companion object {
        fun forExactId(canId: Int, isExtended: Boolean = true): CANFilter {
            val mask = if (isExtended) 0x1FFFFFFF else 0x7FF // 29-bit or 11-bit mask
            return CANFilter(canId, mask, isExtended)
        }
        
        /**
         * Creates a filter for a range of CAN IDs
         * @param baseId The base CAN ID
         * @param rangeMask The mask that defines the range (0 bits = don't care)
         * @param isExtended Whether this is for extended frames
         * @return A CANFilter instance
         */
        fun forRange(baseId: Int, rangeMask: Int, isExtended: Boolean = true): CANFilter {
            return CANFilter(baseId, rangeMask, isExtended)
        }
    }
}