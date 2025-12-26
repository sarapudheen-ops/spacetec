package com.spacetec.core.common

import java.nio.ByteBuffer
import java.nio.ByteOrder

// Hex utilities
fun ByteArray.toHexString() = joinToString("") { "%02X".format(it) }
fun String.hexToByteArray() = chunked(2).map { it.toInt(16).toByte() }.toByteArray()

// Byte manipulation
fun ByteArray.getUInt16(offset: Int, littleEndian: Boolean = false): Int {
    val order = if (littleEndian) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN
    return ByteBuffer.wrap(this, offset, 2).order(order).short.toInt() and 0xFFFF
}

fun ByteArray.getUInt32(offset: Int, littleEndian: Boolean = false): Long {
    val order = if (littleEndian) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN
    return ByteBuffer.wrap(this, offset, 4).order(order).int.toLong() and 0xFFFFFFFFL
}

// String extensions
fun String.removeOBDWhitespace() = replace(" ", "").replace("\r", "").replace("\n", "").replace(">", "")

// Result wrapper
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String, val cause: Throwable? = null) : Result<Nothing>()
}

inline fun <T> runCatchingResult(block: () -> T): Result<T> = try {
    Result.Success(block())
} catch (e: Exception) {
    Result.Error(e.message ?: "Unknown error", e)
}

// Retry utility
suspend fun <T> retry(times: Int = 3, delayMs: Long = 100, block: suspend () -> T?): T? {
    repeat(times) {
        block()?.let { return it }
        kotlinx.coroutines.delay(delayMs)
    }
    return null
}

// VIN utilities
object VINUtils {
    fun isValid(vin: String) = vin.length == 17 && vin.all { it.isLetterOrDigit() } && !vin.contains(Regex("[IOQ]"))
    
    fun getYear(vin: String): Int {
        if (vin.length < 10) return 0
        val codes = "ABCDEFGHJKLMNPRSTVWXY123456789"
        val idx = codes.indexOf(vin[9])
        return if (idx >= 0) 2010 + idx else 0
    }
    
    fun getCountry(vin: String) = when {
        vin.isEmpty() -> "Unknown"
        vin[0] in "12345" -> "North America"
        vin[0] == 'J' -> "Japan"
        vin[0] == 'K' -> "Korea"
        vin[0] in "SVWZ" -> "Europe"
        else -> "Other"
    }
}
