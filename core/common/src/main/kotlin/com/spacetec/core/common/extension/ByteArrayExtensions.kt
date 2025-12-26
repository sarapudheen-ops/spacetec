package com.spacetec.obd.core.common.extension

/**
 * Extension functions for ByteArray manipulation.
 * 
 * These utilities are essential for OBD-II/UDS protocol communication
 * where data is transmitted as raw bytes and needs conversion to/from
 * human-readable formats.
 */

/**
 * Converts a ByteArray to a hexadecimal string.
 * 
 * @param separator The separator between hex bytes (default: space)
 * @param uppercase Whether to use uppercase letters (default: true)
 * @return Hex string representation
 * 
 * Example: byteArrayOf(0x03, 0xE8.toByte()).toHexString() = "03 E8"
 */
fun ByteArray.toHexString(
    separator: String = " ",
    uppercase: Boolean = true
): String = joinToString(separator) { byte ->
    val hex = (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
    if (uppercase) hex.uppercase() else hex
}

/**
 * Converts a ByteArray to a hexadecimal string without separator.
 * 
 * Example: byteArrayOf(0x03, 0xE8.toByte()).toHexStringCompact() = "03E8"
 */
fun ByteArray.toHexStringCompact(uppercase: Boolean = true): String =
    toHexString(separator = "", uppercase = uppercase)

/**
 * Converts a hexadecimal string to a ByteArray.
 * 
 * @return ByteArray representation
 * @throws IllegalArgumentException if the string is not valid hex
 * 
 * Example: "03 E8".hexToByteArray() = byteArrayOf(0x03, 0xE8.toByte())
 */
fun String.hexToByteArray(): ByteArray {
    val cleanHex = this.replace(" ", "").replace("-", "").replace(":", "")
    require(cleanHex.length % 2 == 0) { "Hex string must have even length: $this" }
    
    return ByteArray(cleanHex.length / 2) { index ->
        val start = index * 2
        cleanHex.substring(start, start + 2).toInt(16).toByte()
    }
}

/**
 * Safely converts a hex string to ByteArray, returning null on failure.
 */
fun String.hexToByteArrayOrNull(): ByteArray? = try {
    hexToByteArray()
} catch (e: Exception) {
    null
}

/**
 * Extracts bytes from a specific range.
 * 
 * @param startIndex Starting index (inclusive)
 * @param length Number of bytes to extract
 * @return Extracted ByteArray
 */
fun ByteArray.extractBytes(startIndex: Int, length: Int): ByteArray {
    require(startIndex >= 0) { "Start index must be non-negative" }
    require(length >= 0) { "Length must be non-negative" }
    require(startIndex + length <= size) { "Range exceeds array bounds" }
    
    return copyOfRange(startIndex, startIndex + length)
}

/**
 * Safely extracts bytes from a range, returning null if out of bounds.
 */
fun ByteArray.extractBytesOrNull(startIndex: Int, length: Int): ByteArray? {
    if (startIndex < 0 || length < 0 || startIndex + length > size) return null
    return copyOfRange(startIndex, startIndex + length)
}

/**
 * Converts bytes to an unsigned integer value (big-endian).
 * 
 * @param startIndex Starting index in the array
 * @param length Number of bytes to convert (1-4)
 * @return Unsigned integer value
 * 
 * Example: byteArrayOf(0x00, 0x64).toUInt(0, 2) = 100
 */
fun ByteArray.toUInt(startIndex: Int = 0, length: Int = size - startIndex): Int {
    require(length in 1..4) { "Length must be 1-4 bytes" }
    require(startIndex + length <= size) { "Range exceeds array bounds" }
    
    var result = 0
    for (i in 0 until length) {
        result = (result shl 8) or (this[startIndex + i].toInt() and 0xFF)
    }
    return result
}

/**
 * Converts bytes to an unsigned long value (big-endian).
 * 
 * @param startIndex Starting index in the array
 * @param length Number of bytes to convert (1-8)
 * @return Unsigned long value
 */
fun ByteArray.toULong(startIndex: Int = 0, length: Int = size - startIndex): Long {
    require(length in 1..8) { "Length must be 1-8 bytes" }
    require(startIndex + length <= size) { "Range exceeds array bounds" }
    
    var result = 0L
    for (i in 0 until length) {
        result = (result shl 8) or (this[startIndex + i].toLong() and 0xFF)
    }
    return result
}

/**
 * Converts bytes to a signed integer value (big-endian, two's complement).
 */
fun ByteArray.toSignedInt(startIndex: Int = 0, length: Int = size - startIndex): Int {
    val unsigned = toUInt(startIndex, length)
    val signBit = 1 shl (length * 8 - 1)
    return if (unsigned and signBit != 0) {
        unsigned - (1 shl (length * 8))
    } else {
        unsigned
    }
}

/**
 * Converts an integer to a ByteArray (big-endian).
 * 
 * @param length Number of bytes in result (1-4)
 * @return ByteArray representation
 */
fun Int.toByteArray(length: Int = 4): ByteArray {
    require(length in 1..4) { "Length must be 1-4 bytes" }
    
    return ByteArray(length) { index ->
        (this shr ((length - 1 - index) * 8) and 0xFF).toByte()
    }
}

/**
 * Converts a long to a ByteArray (big-endian).
 * 
 * @param length Number of bytes in result (1-8)
 * @return ByteArray representation
 */
fun Long.toByteArray(length: Int = 8): ByteArray {
    require(length in 1..8) { "Length must be 1-8 bytes" }
    
    return ByteArray(length) { index ->
        (this shr ((length - 1 - index) * 8) and 0xFF).toByte()
    }
}

/**
 * Calculates checksum of the byte array.
 * 
 * @param algorithm Checksum algorithm to use
 * @return Checksum byte
 */
fun ByteArray.calculateChecksum(algorithm: ChecksumAlgorithm = ChecksumAlgorithm.XOR): Byte {
    return when (algorithm) {
        ChecksumAlgorithm.XOR -> fold(0) { acc, byte -> acc xor (byte.toInt() and 0xFF) }.toByte()
        ChecksumAlgorithm.SUM -> fold(0) { acc, byte -> acc + (byte.toInt() and 0xFF) }.toByte()
        ChecksumAlgorithm.TWOS_COMPLEMENT -> {
            val sum = fold(0) { acc, byte -> acc + (byte.toInt() and 0xFF) }
            ((0x100 - (sum and 0xFF)) and 0xFF).toByte()
        }
    }
}

/**
 * Supported checksum algorithms.
 */
enum class ChecksumAlgorithm {
    /** XOR all bytes together */
    XOR,
    /** Sum all bytes */
    SUM,
    /** Two's complement of sum (used in OBD-II) */
    TWOS_COMPLEMENT
}

/**
 * Verifies the checksum of a message.
 * 
 * @param algorithm Checksum algorithm used
 * @return True if checksum is valid
 */
fun ByteArray.verifyChecksum(algorithm: ChecksumAlgorithm = ChecksumAlgorithm.XOR): Boolean {
    if (isEmpty()) return false
    
    val dataWithoutChecksum = copyOfRange(0, size - 1)
    val expectedChecksum = dataWithoutChecksum.calculateChecksum(algorithm)
    return last() == expectedChecksum
}

/**
 * Appends a checksum byte to the array.
 * 
 * @param algorithm Checksum algorithm to use
 * @return New array with checksum appended
 */
fun ByteArray.withChecksum(algorithm: ChecksumAlgorithm = ChecksumAlgorithm.XOR): ByteArray {
    val checksum = calculateChecksum(algorithm)
    return this + checksum
}

/**
 * Masks specific bits in a byte.
 * 
 * @param index Byte index
 * @param mask Bit mask to apply
 * @return Masked value
 */
fun ByteArray.getBits(index: Int, mask: Int): Int {
    require(index in indices) { "Index out of bounds" }
    return (this[index].toInt() and 0xFF) and mask
}

/**
 * Checks if a specific bit is set.
 * 
 * @param byteIndex Index of the byte
 * @param bitIndex Index of the bit (0-7, where 0 is LSB)
 * @return True if bit is set
 */
fun ByteArray.isBitSet(byteIndex: Int, bitIndex: Int): Boolean {
    require(byteIndex in indices) { "Byte index out of bounds" }
    require(bitIndex in 0..7) { "Bit index must be 0-7" }
    
    return ((this[byteIndex].toInt() and 0xFF) shr bitIndex) and 1 == 1
}

/**
 * Concatenates multiple ByteArrays.
 */
fun concatByteArrays(vararg arrays: ByteArray): ByteArray {
    val totalLength = arrays.sumOf { it.size }
    val result = ByteArray(totalLength)
    var offset = 0
    for (array in arrays) {
        array.copyInto(result, offset)
        offset += array.size
    }
    return result
}

/**
 * Extension operator to concatenate ByteArrays with + operator.
 */
operator fun ByteArray.plus(other: ByteArray): ByteArray = concatByteArrays(this, other)

/**
 * Pads the ByteArray to a specific length.
 * 
 * @param length Target length
 * @param padByte Byte to use for padding (default: 0x00)
 * @param padStart Whether to pad at start or end
 * @return Padded ByteArray
 */
fun ByteArray.padTo(length: Int, padByte: Byte = 0x00, padStart: Boolean = true): ByteArray {
    if (size >= length) return this
    
    val padding = ByteArray(length - size) { padByte }
    return if (padStart) padding + this else this + padding
}

/**
 * Reverses the byte order (for endianness conversion).
 */
fun ByteArray.reverseBytes(): ByteArray = reversedArray()

/**
 * Splits ByteArray into chunks of specified size.
 */
fun ByteArray.chunked(size: Int): List<ByteArray> {
    require(size > 0) { "Chunk size must be positive" }
    return toList().chunked(size).map { it.toByteArray() }
}

/**
 * Creates a debug string representation of the ByteArray.
 */
fun ByteArray.toDebugString(): String = buildString {
    append("[${size} bytes] ")
    append(toHexString())
    if (size > 0) {
        append(" | ASCII: ")
        append(map { byte ->
            val char = byte.toInt().toChar()
            if (char.isLetterOrDigit() || char == ' ') char else '.'
        }.joinToString(""))
    }
}