package com.spacetec.protocol.j1850

import com.spacetec.protocol.core.*

class J1850Protocol(private val transport: suspend (ByteArray) -> ByteArray?, private val vpw: Boolean = true) : BaseProtocol(ProtocolType.J1850) {
    
    override suspend fun initialize(): Boolean {
        _state.value = ProtocolState.Connecting
        val protocol = if (vpw) "ATSP2\r" else "ATSP1\r" // VPW or PWM
        val resp = transport(protocol.toByteArray())
        val testResp = transport("0100\r".toByteArray())
        return if (testResp != null && String(testResp).contains("41")) {
            _state.value = ProtocolState.Connected
            true
        } else {
            _state.value = ProtocolState.Error("J1850 init failed")
            false
        }
    }
    
    override suspend fun send(message: ProtocolMessage) = transport(message.data)
    override suspend fun close() { _state.value = ProtocolState.Disconnected }
}
