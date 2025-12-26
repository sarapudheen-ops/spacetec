/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 */
package com.spacetec.vehicle.brands.volkswagen.elm327

import android.util.Log
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Handles ELM327 initialization and protocol configuration
 * Optimized for VW vehicles
 */
class ELM327ProtocolHandler(private val connection: ELM327Connection) {

    private val isInitialized = AtomicBoolean(false)

    var elmVersion: String = ""
        private set
    var detectedProtocol: String = ""
        private set
    var currentProtocolNumber: Int = -1
        private set

    enum class Protocol(val code: Int, val description: String) {
        AUTO(0, "Automatic"),
        SAE_J1850_PWM(1, "SAE J1850 PWM (41.6 Kbaud)"),
        SAE_J1850_VPW(2, "SAE J1850 VPW (10.4 Kbaud)"),
        ISO_9141_2(3, "ISO 9141-2 (5 baud init, 10.4 Kbaud)"),
        ISO_14230_4_KWP_5BAUD(4, "ISO 14230-4 KWP (5 baud init, 10.4 Kbaud)"),
        ISO_14230_4_KWP_FAST(5, "ISO 14230-4 KWP (fast init, 10.4 Kbaud)"),
        ISO_15765_4_CAN_11BIT_500K(6, "ISO 15765-4 CAN (11 bit ID, 500 Kbaud)"),
        ISO_15765_4_CAN_29BIT_500K(7, "ISO 15765-4 CAN (29 bit ID, 500 Kbaud)"),
        ISO_15765_4_CAN_11BIT_250K(8, "ISO 15765-4 CAN (11 bit ID, 250 Kbaud)"),
        ISO_15765_4_CAN_29BIT_250K(9, "ISO 15765-4 CAN (29 bit ID, 250 Kbaud)"),
        SAE_J1939_CAN(10, "SAE J1939 CAN (29 bit ID, 250 Kbaud)"),
        USER1_CAN(11, "User1 CAN (11 bit ID, 125 Kbaud)"),
        USER2_CAN(12, "User2 CAN (11 bit ID, 50 Kbaud)")
    }

    fun initialize(): InitializationResult {
        val result = InitializationResult()

        if (!connection.isConnected()) {
            result.success = false
            result.errorMessage = "Not connected to adapter"
            return result
        }

        try {
            result.stage = "Resetting adapter"
            if (!resetAdapter()) {
                result.success = false
                result.errorMessage = "Failed to reset adapter"
                return result
            }

            result.stage = "Getting adapter version"
            elmVersion = getELMVersion()
            result.adapterVersion = elmVersion

            if (!isValidELMResponse(elmVersion)) {
                result.success = false
                result.errorMessage = "Invalid adapter response. May not be genuine ELM327"
                return result
            }

            result.stage = "Configuring adapter"
            if (!configureAdapter()) {
                result.success = false
                result.errorMessage = "Failed to configure adapter"
                return result
            }

            result.stage = "Detecting vehicle protocol"
            if (!detectAndSetProtocol()) {
                result.success = false
                result.errorMessage = "Failed to establish communication with vehicle"
                return result
            }

            result.success = true
            result.detectedProtocol = detectedProtocol
            result.protocolNumber = currentProtocolNumber
            isInitialized.set(true)

            Log.i(TAG, "Initialization complete. Protocol: $detectedProtocol")

        } catch (e: Exception) {
            result.success = false
            result.errorMessage = "Initialization error: ${e.message}"
            result.exception = e
            Log.e(TAG, "Initialization failed", e)
        }

        return result
    }

    @Throws(IOException::class)
    private fun resetAdapter(): Boolean {
        connection.sendAndReceive("ATZ", LONG_TIMEOUT)
        Thread.sleep(1000)
        connection.clearInputBuffer()
        val response = connection.sendAndReceive("ATI", MEDIUM_TIMEOUT)
        return response.contains("ELM") || response.contains("OBD")
    }

    @Throws(IOException::class)
    private fun getELMVersion(): String {
        val response = connection.sendAndReceive("ATI", SHORT_TIMEOUT)
        return cleanResponse(response)
    }

    private fun isValidELMResponse(response: String): Boolean {
        if (response.isEmpty()) return false
        val upper = response.uppercase()
        return upper.contains("ELM") || upper.contains("OBD") ||
                upper.contains("OBDII") || upper.contains("V1.") || upper.contains("V2.")
    }

    @Throws(IOException::class)
    private fun configureAdapter(): Boolean {
        connection.sendAndReceive("ATE0", SHORT_TIMEOUT) // Echo Off
        connection.sendAndReceive("ATL0", SHORT_TIMEOUT) // Linefeeds Off
        connection.sendAndReceive("ATS0", SHORT_TIMEOUT) // Spaces Off
        connection.sendAndReceive("ATH1", SHORT_TIMEOUT) // Headers On
        connection.sendAndReceive("ATST FF", SHORT_TIMEOUT) // Max timeout
        connection.sendAndReceive("ATAL", SHORT_TIMEOUT) // Allow long messages
        val dpn = connection.sendAndReceive("ATDPN", SHORT_TIMEOUT)
        Log.d(TAG, "Current protocol number: $dpn")
        return true
    }

    @Throws(IOException::class)
    private fun detectAndSetProtocol(): Boolean {
        if (tryAutoProtocol()) return true

        for (protocol in VW_PROTOCOL_PRIORITY) {
            if (protocol == Protocol.AUTO) continue
            Log.d(TAG, "Trying protocol: ${protocol.description}")
            if (tryProtocol(protocol)) return true
        }

        return false
    }

    @Throws(IOException::class)
    private fun tryAutoProtocol(): Boolean {
        val response = connection.sendAndReceive("ATSP0", SHORT_TIMEOUT)
        if (!response.contains(OK)) return false

        val obdResponse = connection.sendAndReceive("0100", LONG_TIMEOUT)

        if (isSuccessfulOBDResponse(obdResponse)) {
            val dpn = connection.sendAndReceive("ATDPN", SHORT_TIMEOUT)
            currentProtocolNumber = parseProtocolNumber(dpn)
            val dp = connection.sendAndReceive("ATDP", SHORT_TIMEOUT)
            detectedProtocol = cleanResponse(dp)
            Log.i(TAG, "Auto protocol detected: $detectedProtocol (Protocol $currentProtocolNumber)")
            return true
        }

        return false
    }

    @Throws(IOException::class)
    private fun tryProtocol(protocol: Protocol): Boolean {
        val cmd = "ATSP${protocol.code}"
        val response = connection.sendAndReceive(cmd, SHORT_TIMEOUT)

        if (!response.contains(OK)) return false

        if (protocol == Protocol.ISO_14230_4_KWP_5BAUD || protocol == Protocol.ISO_9141_2) {
            connection.sendAndReceive("ATSI", LONG_TIMEOUT)
        }

        val obdResponse = connection.sendAndReceive("0100", LONG_TIMEOUT)

        if (isSuccessfulOBDResponse(obdResponse)) {
            currentProtocolNumber = protocol.code
            detectedProtocol = protocol.description
            Log.i(TAG, "Protocol set successfully: ${protocol.description}")
            return true
        }

        return false
    }

    private fun isSuccessfulOBDResponse(response: String): Boolean {
        if (response.isEmpty()) return false
        val upper = response.uppercase()

        if (upper.contains(NO_DATA) || upper.contains(UNABLE_TO_CONNECT) ||
            upper.contains(CAN_ERROR) || upper.contains(BUS_ERROR) ||
            upper.contains(ERROR) || upper.contains("?")
        ) return false

        if (upper.contains("4100") || upper.contains("41 00")) return true

        return upper.matches(Regex(".*[0-9A-F]{4,}.*"))
    }

    private fun parseProtocolNumber(response: String): Int {
        val clean = cleanResponse(response)
        return try {
            val num = clean.replace("A", "").trim()
            num.toInt(16)
        } catch (e: NumberFormatException) {
            -1
        }
    }

    private fun cleanResponse(response: String): String {
        return response
            .replace("\r", "")
            .replace("\n", " ")
            .replace(">", "")
            .replace("SEARCHING...", "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    @Throws(IOException::class)
    fun sendOBDCommand(command: String, timeout: Int = MEDIUM_TIMEOUT): OBDResponse {
        if (!isInitialized.get()) {
            throw IOException("Protocol handler not initialized")
        }

        val rawResponse = connection.sendAndReceive(command, timeout)
        return parseOBDResponse(command, rawResponse)
    }

    private fun parseOBDResponse(command: String, rawResponse: String): OBDResponse {
        val response = OBDResponse(
            command = command,
            rawResponse = rawResponse,
            cleanResponse = cleanResponse(rawResponse)
        )

        val clean = response.cleanResponse.uppercase()

        when {
            clean.contains(NO_DATA) -> {
                response.success = false
                response.errorType = OBDResponse.ErrorType.NO_DATA
            }
            clean.contains(CAN_ERROR) -> {
                response.success = false
                response.errorType = OBDResponse.ErrorType.CAN_ERROR
            }
            clean.contains(BUS_ERROR) -> {
                response.success = false
                response.errorType = OBDResponse.ErrorType.BUS_ERROR
            }
            clean.contains(ERROR) || clean.contains("?") -> {
                response.success = false
                response.errorType = OBDResponse.ErrorType.COMMAND_ERROR
            }
            else -> {
                response.success = true
                response.data = parseHexData(clean)
            }
        }

        return response
    }

    private fun parseHexData(response: String): ByteArray {
        var hexOnly = response.replace(Regex("[^0-9A-Fa-f]"), "")
        if (hexOnly.length % 2 != 0) hexOnly = "0$hexOnly"

        return ByteArray(hexOnly.length / 2) { i ->
            hexOnly.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    fun isInitialized(): Boolean = isInitialized.get()

    data class OBDResponse(
        val command: String,
        val rawResponse: String,
        val cleanResponse: String,
        var success: Boolean = false,
        var data: ByteArray? = null,
        var errorType: ErrorType? = null
    ) {
        enum class ErrorType {
            NONE, NO_DATA, CAN_ERROR, BUS_ERROR, COMMAND_ERROR, TIMEOUT
        }
    }

    data class InitializationResult(
        var success: Boolean = false,
        var stage: String = "",
        var errorMessage: String? = null,
        var adapterVersion: String? = null,
        var detectedProtocol: String? = null,
        var protocolNumber: Int = -1,
        var exception: Exception? = null
    )

    companion object {
        private const val TAG = "ELM327Protocol"
        private const val SHORT_TIMEOUT = 2000
        private const val MEDIUM_TIMEOUT = 5000
        private const val LONG_TIMEOUT = 10000

        private const val OK = "OK"
        private const val ERROR = "ERROR"
        private const val NO_DATA = "NO DATA"
        private const val UNABLE_TO_CONNECT = "UNABLE TO CONNECT"
        private const val CAN_ERROR = "CAN ERROR"
        private const val BUS_ERROR = "BUS ERROR"

        val VW_PROTOCOL_PRIORITY = arrayOf(
            Protocol.ISO_15765_4_CAN_11BIT_500K,
            Protocol.ISO_15765_4_CAN_29BIT_500K,
            Protocol.ISO_14230_4_KWP_FAST,
            Protocol.ISO_14230_4_KWP_5BAUD,
            Protocol.ISO_9141_2,
            Protocol.AUTO
        )
    }
}
