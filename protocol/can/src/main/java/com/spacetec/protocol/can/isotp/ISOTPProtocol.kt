package com.spacetec.protocol.can.isotp

import com.spacetec.protocol.core.base.BaseProtocol
import com.spacetec.protocol.core.base.ProtocolCapabilities
import com.spacetec.protocol.core.base.ProtocolConfig
import com.spacetec.protocol.core.base.ProtocolState
import com.spacetec.protocol.core.base.ProtocolType
import com.spacetec.protocol.core.message.DiagnosticMessage
import com.spacetec.protocol.core.base.SessionType
import com.spacetec.transport.contract.ScannerConnection
import com.spacetec.core.common.exceptions.ProtocolException
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ISO-TP (ISO 15765-2) Protocol implementation for Transport Protocol over CAN
 * This class handles multi-frame CAN messages according to ISO 15765-2 standard
 */
class ISOTPProtocol : BaseProtocol() {
    
    override val protocolType: ProtocolType = ProtocolType.ISO_15765_4_CAN_11BIT_500K
    override val protocolName: String = "ISO-TP (ISO 15765-2) Transport Protocol"
    override val protocolVersion: String = "2.0"
    override val capabilities: ProtocolCapabilities = ProtocolCapabilities.forCAN(protocolType)
    
    private val isInitialized = AtomicBoolean(false)
    private val segmenter = ISOTPSegmenter()
    private val reassembler = ISOTPReassembler()
    private val frameHandler = ISOTPFrameHandler()
    
    override suspend fun initialize(
        connection: ScannerConnection,
        config: ProtocolConfig
    ) {
        baseInitialize(connection, config)
        
        try {
            // Configure CAN for ISO-TP communication
            sendRaw("ATSP6\r".toByteArray()) // Set protocol to ISO 15765-4
            delay(100)
            
            sendRaw("ATSTFF\r".toByteArray()) // Set timeout to maximum
            delay(100)
            
            sendRaw("ATAT1\r".toByteArray()) // Enable auto timing
            delay(100)
            
            isInitialized.set(true)
            completeInitialization()
        } catch (e: Exception) {
            _state.value = ProtocolState.Error(
                com.spacetec.protocol.core.base.ProtocolError.CommunicationError("ISO-TP init failed: ${e.message}")
            )
            throw e
        }
    }
    
    override suspend fun sendMessage(request: DiagnosticMessage): DiagnosticMessage {
        return baseSendMessage(request, _config.responseTimeoutMs) { data ->
            // Segment the message if it exceeds CAN frame size
            val frames = if (data.size > 7) {
                segmenter.segment(data)
            } else {
                listOf(data)
            }
            
            // Send frames and handle flow control
            val response = frameHandler.sendMultiFrameMessage(connection!!, frames)
            response
        }
    }
    
    override suspend fun sendMessage(
        request: DiagnosticMessage,
        timeoutMs: Long
    ): DiagnosticMessage {
        return baseSendMessage(request, timeoutMs) { data ->
            // Segment the message if it exceeds CAN frame size
            val frames = if (data.size > 7) {
                segmenter.segment(data)
            } else {
                listOf(data)
            }
            
            // Send frames and handle flow control
            val response = frameHandler.sendMultiFrameMessage(connection!!, frames)
            response
        }
    }
    
    override suspend fun startSession(sessionType: SessionType, ecuAddress: Int?) {
        if (!isInitialized.get()) {
            throw ProtocolException("ISO-TP protocol not initialized")
        }
        super.startSession(sessionType, ecuAddress)
    }
    
    override suspend fun sendKeepAlive() {
        // ISO-TP doesn't typically use keep-alive, but we can send a tester present
        if (isSessionActive) {
            try {
                val request = buildRequest(0x3E, byteArrayOf(0x00)) // Tester Present
                sendMessage(request, _config.responseTimeoutMs)
            } catch (e: Exception) {
                // Keep-alive is best effort
            }
        }
    }
    
    override fun validateResponse(
        request: DiagnosticMessage,
        response: DiagnosticMessage
    ): Boolean {
        // For ISO-TP, responses follow standard UDS format with service ID + 0x40
        val expectedServiceId = (request.serviceId + 0x40) and 0xFF
        return response.serviceId == expectedServiceId
    }
    
    override suspend fun handleNegativeResponse(
        serviceId: Int,
        nrc: Int,
        request: DiagnosticMessage
    ): DiagnosticMessage {
        throw ProtocolException("Negative response in ISO-TP: NRC ${String.format("0x%02X", nrc)}")
    }
    
    override fun buildRequest(
        serviceId: Int,
        data: ByteArray
    ): DiagnosticMessage {
        val serviceByte = serviceId.toByte()
        val fullData = byteArrayOf(serviceByte) + data
        return CANMessage(serviceId, fullData)
    }
    
    override fun parseResponse(
        response: ByteArray,
        expectedService: Int
    ): DiagnosticMessage {
        if (response.isEmpty()) {
            throw ProtocolException("Empty response received")
        }
        
        // Check if this is a negative response (service ID = 0x7F)
        if (response[0].toInt() and 0xFF == 0x7F) {
            if (response.size >= 3) {
                val requestedService = response[1].toInt() and 0xFF
                val nrc = response[2].toInt() and 0xFF
                return CANMessage(0x7F, response, true, nrc)
            } else {
                throw ProtocolException("Invalid negative response format")
            }
        }
        
        // Positive response - service ID should be requested service + 0x40
        val serviceId = response[0].toInt() and 0xFF
        if (serviceId != (expectedService + 0x40) and 0xFF) {
            throw ProtocolException("Invalid service ID in response")
        }
        
        return CANMessage(serviceId, response)
    }
    
    override suspend fun performShutdownCleanup() {
        try {
            // Reset CAN adapter settings
            sendRaw("ATZ\r".toByteArray(), 500)
        } catch (e: Exception) {
            // Ignore errors during shutdown
        }
        isInitialized.set(false)
    }
}