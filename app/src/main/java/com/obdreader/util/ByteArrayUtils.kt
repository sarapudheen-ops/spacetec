package com.obdreader.util

/**
 * Utilities for byte array operations.
 */
object ByteArrayUtils {
    
    /**
     * Get unsigned byte value.
     */
    fun ByteArray.uByteAt(index: Int): Int = this[index].toInt() and 0xFF
    
    /**
     * Get unsigned short from two bytes (big-endian).
     */
    fun ByteArray.uShortAt(index: Int): Int {
        return (uByteAt(index) shl 8) or uByteAt(index + 1)
    }
    
    /**
     * Check if array contains pattern.
     */
    fun ByteArray.contains(pattern: ByteArray): Boolean {
        for (i in 0..this.size - pattern.size) {
            if (this.sliceArray(i until i + pattern.size).contentEquals(pattern)) {
                return true
            }
        }
        return false
    }
}
