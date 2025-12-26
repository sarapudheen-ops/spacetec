package com.spacetec.protocol.kline

import com.spacetec.protocol.core.*
import kotlinx.coroutines.delay

class KLineProtocol(private val transport: suspend (ByteArray) -> ByteArray?) : BaseProtocol(ProtocolType.KLINE) {
    
    override suspend fun initialize(): Boolean {
        _state.value = ProtocolState.Connecting
        // 5-baud init sequence for ISO 9141-2
        val resp = transport("ATSP3\r".toByteArray()) // ISO 9141-2
        delay(300)
        val initResp = transport("0100\r".toByteArray()) // Test supported PIDs
        return if (initResp != null && String(initResp).contains("41")) {
            _state.value = ProtocolState.Connected
            true
        } else {
            _state.value = ProtocolState.Error("K-Line init failed")
            false
        }
    }
    
    override suspend fun send(message: ProtocolMessage) = transport(message.data)
    override suspend fun close() { _state.value = ProtocolState.Disconnected }
    
    suspend fun fastInit(): Boolean {
        // Fast init for ISO 14230 (KWP2000)
        transport("ATSP4\r".toByteArray()) // ISO 14230-4 KWP
        delay(100)
        val resp = transport("0100\r".toByteArray())
        return resp != null && String(resp).contains("41")
    }
}

class KWP2000Protocol(private val transport: suspend (ByteArray) -> ByteArray?) : BaseProtocol(ProtocolType.KLINE) {
    
    override suspend fun initialize(): Boolean {
        _state.value = ProtocolState.Connecting
        transport("ATSP5\r".toByteArray()) // ISO 14230-4 KWP fast
        val resp = transport("8110F1\r".toByteArray()) // Start diagnostic session
        return if (resp != null) {
            _state.value = ProtocolState.Connected
            true
        } else {
            _state.value = ProtocolState.Error("KWP2000 init failed")
            false
        }
    }
    
    override suspend fun send(message: ProtocolMessage) = transport(message.data)
    override suspend fun close() { _state.value = ProtocolState.Disconnected }
    
    suspend fun readDTCs(): List<DTC> {
        val resp = transport("1802FF00\r".toByteArray()) ?: return emptyList()
        return parseDTCs(resp)
    }
    
    private fun parseDTCs(data: ByteArray): List<DTC> {
        val str = String(data).replace(" ", "")
        if (!str.startsWith("58")) return emptyList()
        val dtcs = mutableListOf<DTC>()
        var i = 4
        while (i + 4 <= str.length) {
            val code = str.substring(i, i + 4)
            if (code != "0000") dtcs.add(DTC("P$code", "KWP2000 DTC"))
            i += 4
        }
        return dtcs
    }
}
