package com.spacetec.obd.protocol.can

import com.spacetec.protocol.core.*

data class CANFrame(
    val id: Int,
    val data: ByteArray,
    val extended: Boolean = false,
    val rtr: Boolean = false
)

class CANProtocol(private val transport: suspend (ByteArray) -> ByteArray?) {
    
    private var bitrate = 500000
    
    suspend fun initialize(): Boolean {
        // Set CAN protocol on ELM327
        val resp = transport("ATSP6\r".toByteArray()) // ISO 15765-4 CAN
        return resp != null
    }
    
    suspend fun send(message: ByteArray): ByteArray? {
        return transport(message)
    }
    
    suspend fun close() {
        // Cleanup if needed
    }
    
    suspend fun sendFrame(frame: CANFrame): Boolean {
        val cmd = buildString {
            append(String.format("%03X", frame.id))
            frame.data.forEach { append(String.format("%02X", it.toInt() and 0xFF)) }
        }
        return transport("$cmd\r".toByteArray()) != null
    }
    
    suspend fun setFilter(id: Int, mask: Int = 0x7FF) {
        transport("ATCF${String.format("%03X", id)}\r".toByteArray())
        transport("ATCM${String.format("%03X", mask)}\r".toByteArray())
    }
    
    suspend fun setBitrate(rate: Int) {
        bitrate = rate
        // ELM327 doesn't support arbitrary bitrates, but real CAN adapters do
    }
}
