package com.obdreader.util

/**
 * Utilities for hex string manipulation.
 */
object HexUtils {
    
    /**
     * Convert hex string to byte array.
     */
    fun hexToBytes(hex: String): ByteArray {
        val cleanHex = hex.replace(" ", "").uppercase()
        return cleanHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
    
    /**
     * Convert byte array to hex string.
     */
    fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02X".format(it) }
    }
    
    /**
     * Convert single byte to hex string.
     */
    fun byteToHex(byte: Byte): String {
        return "%02X".format(byte)
    }
}
