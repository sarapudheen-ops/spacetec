package com.spacetec.j2534

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * J2534Session - Represents a communication session with an ECU
 */
class J2534Session(
    private val channel: J2534Channel,
    private val protocol: Long = J2534Protocols.ISO15765 // Default to ISO-TP for UDS
) {
    private val isActive = AtomicBoolean(false)
    private val sessionLock = ReentrantReadWriteLock()
    private var sessionConfig: SessionConfig? = null
    
    /**
     * Session configuration data class
     */
    data class SessionConfig(
        val blockSize: Int = 8,
        val stMin: Int = 0, // Separation time minimum in ms
        val blockSizeMax: Int = 255,
        val stMinMax: Int = 127,
        val flowControlTimeout: Long = 2000L,
        val transmissionTimeout: Long = 5000L,
        val receptionTimeout: Long = 5000L
    )
    
    /**
     * Start the communication session
     */
    fun start(config: SessionConfig? = null): Long {
        sessionLock.write {
            if (isActive.get()) {
                return J2534Errors.ERR_DEVICE_IN_USE
            }
            
            sessionConfig = config ?: SessionConfig()
            
            // Configure channel for ISO-TP if needed
            if (protocol == J2534Protocols.ISO15765) {
                configureIsoTp()
            }
            
            isActive.set(true)
            return J2534Errors.STATUS_NOERROR
        }
    }
    
    /**
     * Stop the communication session
     */
    fun stop(): Long {
        sessionLock.write {
            if (!isActive.get()) {
                return J2534Errors.STATUS_NOERROR
            }
            
            // Clean up any ongoing operations
            cleanup()
            
            isActive.set(false)
            return J2534Errors.STATUS_NOERROR
        }
    }
    
    /**
     * Check if session is active
     */
    fun isActive(): Boolean {
        return isActive.get()
    }
    
    /**
     * Send diagnostic request (UDS service)
     */
    fun sendDiagnosticRequest(serviceId: Byte, data: ByteArray, timeout: Long = 2000L): J2534Message? {
        sessionLock.read {
            if (!isActive.get()) {
                return null
            }
            
            // Format UDS request based on protocol
            val message = when (protocol) {
                J2534Protocols.ISO15765 -> formatUdsIsoTpRequest(serviceId, data)
                J2534Protocols.CAN -> formatUdsCanRequest(serviceId, data)
                else -> formatUdsIsoTpRequest(serviceId, data) // Default to ISO-TP
            }
            
            // Send the request
            val messages = arrayOf(message)
            val writeResult = channel.writeMessages(messages, 1, timeout)
            if (writeResult != J2534Errors.STATUS_NOERROR) {
                return null
            }
            
            // Read the response
            val response = J2534Message()
            val responses = arrayOf(response)
            val readResult = channel.readMessages(responses, 1, timeout)
            if (readResult != J2534Errors.STATUS_NOERROR) {
                return null
            }
            
            return responses[0]
        }
    }
    
    /**
     * Send raw data through the session
     */
    fun sendRawData(data: ByteArray, timeout: Long = 1000L): Long {
        sessionLock.read {
            if (!isActive.get()) {
                return J2534Errors.STATUS_DEVICE_NOT_CONNECTED
            }
            
            val message = J2534Message().apply {
                protocolID = protocol
                data = data
                txFlags = when (protocol) {
                    J2534Protocols.ISO15765 -> 0x00000040L // CAN_29BIT_ID for ISO-TP
                    else -> 0L
                }
            }
            
            val messages = arrayOf(message)
            return channel.writeMessages(messages, 1, timeout)
        }
    }
    
    /**
     * Configure ISO-TP parameters
     */
    private fun configureIsoTp() {
        sessionConfig?.let { config ->
            // Configure flow control parameters
            channel.ioctl(J2534IoctlIds.SET_CONFIG, J2534ConfigParams.ISO15765_BS.toLong(), config.blockSize.toLong())
            channel.ioctl(J2534IoctlIds.SET_CONFIG, J2534ConfigParams.ISO15765_STMIN.toLong(), config.stMin.toLong())
            channel.ioctl(J2534IoctlIds.SET_CONFIG, J2534ConfigParams.ISO15765_WFT_MAX.toLong(), 5L) // Max wait frames
        }
    }
    
    /**
     * Format UDS request for ISO-TP (ISO15765)
     */
    private fun formatUdsIsoTpRequest(serviceId: Byte, data: ByteArray): J2534Message {
        // For ISO-TP, we need to format the message with appropriate CAN ID
        // Standard UDS functional address (0x7DF) and physical address (0x7E0-0x7E7)
        val canId = 0x7E0L // ECU physical address (typically 0x7E0 for primary ECU)
        
        return J2534Message().apply {
            protocolID = J2534Protocols.ISO15765
            txFlags = 0x0040L // ISO15765_FRAME_PAD
            data = byteArrayOf(
                ((canId shr 24) and 0xFF).toByte(),
                ((canId shr 16) and 0xFF).toByte(),
                ((canId shr 8) and 0xFF).toByte(),
                (canId and 0xFF).toByte()
            ) + byteArrayOf(serviceId) + data
        }
    }
    
    /**
     * Format UDS request for direct CAN
     */
    private fun formatUdsCanRequest(serviceId: Byte, data: ByteArray): J2534Message {
        val canId = 0x7E0L // ECU physical address
        
        return J2534Message().apply {
            protocolID = J2534Protocols.CAN
            txFlags = if (canId > 0x7FF) 0x0100L else 0L // Extended CAN ID if needed
            data = if (canId > 0x7FF) {
                // 29-bit CAN ID format
                byteArrayOf(
                    ((canId shr 24) and 0xFF).toByte(),
                    ((canId shr 16) and 0xFF).toByte(),
                    ((canId shr 8) and 0xFF).toByte(),
                    (canId and 0xFF).toByte()
                ) + byteArrayOf(serviceId) + data
            } else {
                // 11-bit CAN ID format
                byteArrayOf(
                    ((canId shr 8) and 0xFF).toByte(),
                    (canId and 0xFF).toByte()
                ) + byteArrayOf(serviceId) + data
            }
        }
    }
    
    /**
     * Perform ECU programming sequence
     */
    fun performEcuProgramming(
        programmingData: ByteArray,
        blockSize: Int = 128,
        verifyAfterWrite: Boolean = true
    ): Boolean {
        sessionLock.write {
            if (!isActive.get()) {
                return false
            }
            
            try {
                // Step 1: Request download
                val requestDownloadResponse = sendDiagnosticRequest(0x34.toByte(), byteArrayOf(0x00, 0x00, 0x00, 0x00, 0xFF, 0xFF, 0xFF, 0xFF))
                if (requestDownloadResponse == null || requestDownloadResponse.data.size < 4 || requestDownloadResponse.data[2] != 0x74.toByte()) {
                    // Negative response or unexpected response
                    return false
                }
                
                // Step 2: Transfer data in blocks
                var offset = 0
                var sequenceNumber = 1
                while (offset < programmingData.size) {
                    val blockSizeActual = minOf(blockSize, programmingData.size - offset)
                    val blockData = programmingData.sliceArray(offset until offset + blockSizeActual)
                    
                    // Create transfer data request
                    val transferDataRequest = byteArrayOf(sequenceNumber.toByte()) + blockData
                    val transferResponse = sendDiagnosticRequest(0x36.toByte(), transferDataRequest)
                    
                    if (transferResponse == null || transferResponse.data.size < 3 || transferResponse.data[2] != 0x76.toByte()) {
                        // Negative response or unexpected response
                        return false
                    }
                    
                    offset += blockSizeActual
                    sequenceNumber = (sequenceNumber % 255) + 1 // Sequence numbers are 1-255
                    
                    // Small delay to avoid overwhelming the ECU
                    Thread.sleep(10)
                }
                
                // Step 3: Request transfer exit
                val transferExitResponse = sendDiagnosticRequest(0x37.toByte(), byteArrayOf())
                if (transferExitResponse == null || transferExitResponse.data.size < 3 || transferExitResponse.data[2] != 0x77.toByte()) {
                    return false
                }
                
                // Step 4: Routine control - start programming
                val routineControlResponse = sendDiagnosticRequest(0x31.toByte(), byteArrayOf(0x01, 0xFF, 0xFF)) // Start routine
                if (routineControlResponse == null || routineControlResponse.data.size < 3 || routineControlResponse.data[2] != 0x71.toByte()) {
                    return false
                }
                
                if (verifyAfterWrite) {
                    // Additional verification steps would go here
                    // This might include reading back data or performing checksums
                }
                
                return true
            } catch (e: Exception) {
                e.printStackTrace()
                return false
            }
        }
    }
    
    /**
     * Read ECU data using diagnostic request
     */
    fun readEcuData(did: Short, timeout: Long = 2000L): ByteArray? {
        sessionLock.read {
            if (!isActive.get()) {
                return null
            }
            
            // Send Read Data By Identifier (0x22) request
            val data = byteArrayOf(
                ((did.toInt() shr 8) and 0xFF).toByte(),
                (did.toInt() and 0xFF).toByte()
            )
            
            val response = sendDiagnosticRequest(0x22.toByte(), data, timeout)
            return if (response != null && response.data.size >= 4 && response.data[2] == 0x62.toByte()) {
                // Positive response, return the data part
                response.data.sliceArray(3 until response.data.size)
            } else {
                null
            }
        }
    }
    
    /**
     * Clean up resources
     */
    private fun cleanup() {
        // Any cleanup operations needed when stopping the session
        sessionConfig = null
    }
    
    /**
     * Get current session configuration
     */
    fun getSessionConfig(): SessionConfig? {
        return sessionLock.read { sessionConfig }
    }
    
    companion object {
        /**
         * Create a new session with default configuration
         */
        fun createSession(channel: J2534Channel, protocol: Long = J2534Protocols.ISO15765): J2534Session {
            return J2534Session(channel, protocol)
        }
    }
}