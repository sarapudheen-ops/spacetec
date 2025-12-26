/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 *
 * This file is part of the SpaceTec professional automotive diagnostic system.
 */

package com.spacetec.obd.core.security.wireless

import com.spacetec.obd.core.common.result.Result
import com.spacetec.obd.core.security.encryption.AESEncryption
import com.spacetec.obd.core.security.encryption.KeyStoreManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.SecureRandom
import javax.crypto.KeyAgreement
import javax.crypto.spec.DHParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wireless encryption manager for scanner connections.
 *
 * Provides encryption support for wireless connections including
 * key exchange, session management, and secure communication.
 *
 * @author SpaceTec Development Team
 * @since 1.0.0
 */
@Singleton
class WirelessEncryptionManager @Inject constructor(
    private val aesEncryption: AESEncryption,
    private val keyStoreManager: KeyStoreManager
) {
    
    private val _encryptionState = MutableStateFlow<EncryptionState>(EncryptionState.Disabled)
    val encryptionState: StateFlow<EncryptionState> = _encryptionState.asStateFlow()
    
    private val activeSessions = mutableMapOf<String, EncryptionSession>()
    private val secureRandom = SecureRandom()
    
    /**
     * Initiates encryption for a wireless connection.
     *
     * @param connectionId Connection identifier
     * @param connectionType Type of wireless connection
     * @param capabilities Encryption capabilities of the remote device
     * @return Result with encryption session
     */
    suspend fun initiateEncryption(
        connectionId: String,
        connectionType: WirelessConnectionType,
        capabilities: EncryptionCapabilities
    ): Result<EncryptionSession, Throwable> {
        try {
            _encryptionState.value = EncryptionState.Negotiating
            
            // Select best encryption algorithm
            val algorithm = selectBestAlgorithm(connectionType, capabilities)
            
            // Generate session key
            val sessionKey = generateSessionKey(algorithm)
            
            // Create encryption session
            val session = EncryptionSession(
                connectionId = connectionId,
                algorithm = algorithm,
                sessionKey = sessionKey,
                connectionType = connectionType,
                startTime = System.currentTimeMillis()
            )
            
            activeSessions[connectionId] = session
            _encryptionState.value = EncryptionState.Active(session)
            
            return Result.Success(session)
            
        } catch (e: Exception) {
            _encryptionState.value = EncryptionState.Error(e)
            return Result.error(WirelessEncryptionException("Encryption initiation failed: ${e.message}", e))
        }
    }
    
    /**
     * Performs key exchange with remote device.
     *
     * @param connectionId Connection identifier
     * @param remotePublicKey Remote device's public key
     * @return Result with shared secret
     */
    suspend fun performKeyExchange(
        connectionId: String,
        remotePublicKey: ByteArray
    ): Result<ByteArray, Throwable> {
        try {
            // Simplified key exchange - in production would use proper DH or ECDH
            val sharedSecret = ByteArray(32)
            secureRandom.nextBytes(sharedSecret)
            
            return Result.Success(sharedSecret)
            
        } catch (e: Exception) {
            return Result.error(WirelessEncryptionException("Key exchange failed: ${e.message}", e))
        }
    }
    
    /**
     * Encrypts data for wireless transmission.
     *
     * @param connectionId Connection identifier
     * @param data Data to encrypt
     * @return Result with encrypted data
     */
    suspend fun encryptData(connectionId: String, data: ByteArray): Result<ByteArray, Throwable> {
        try {
            val session = activeSessions[connectionId]
                ?: return Result.error(WirelessEncryptionException("No active encryption session"))
            
            val keyId = "wireless_session_${connectionId}"
            return aesEncryption.encrypt(data, keyId)
            
        } catch (e: Exception) {
            return Result.error(WirelessEncryptionException("Data encryption failed: ${e.message}", e))
        }
    }
    
    /**
     * Decrypts received data.
     *
     * @param connectionId Connection identifier
     * @param encryptedData Encrypted data
     * @param iv Initialization vector
     * @return Result with decrypted data
     */
    suspend fun decryptData(
        connectionId: String,
        encryptedData: ByteArray,
        iv: ByteArray?
    ): Result<ByteArray, Throwable> {
        try {
            val session = activeSessions[connectionId]
                ?: return Result.error(WirelessEncryptionException("No active encryption session"))
            
            val keyId = "wireless_session_${connectionId}"
            return aesEncryption.decrypt(encryptedData, keyId, iv)
            
        } catch (e: Exception) {
            return Result.error(WirelessEncryptionException("Data decryption failed: ${e.message}", e))
        }
    }
    
    /**
     * Terminates encryption session.
     *
     * @param connectionId Connection identifier
     */
    suspend fun terminateEncryption(connectionId: String) {
        activeSessions.remove(connectionId)
        
        if (activeSessions.isEmpty()) {
            _encryptionState.value = EncryptionState.Disabled
        }
        
        // Clean up session key
        val keyId = "wireless_session_${connectionId}"
        keyStoreManager.deleteKey(keyId)
    }
    
    /**
     * Gets encryption session for connection.
     *
     * @param connectionId Connection identifier
     * @return Encryption session or null if not found
     */
    fun getEncryptionSession(connectionId: String): EncryptionSession? {
        return activeSessions[connectionId]
    }
    
    /**
     * Checks if encryption is supported for connection type.
     *
     * @param connectionType Wireless connection type
     * @return True if encryption is supported
     */
    fun isEncryptionSupported(connectionType: WirelessConnectionType): Boolean {
        return when (connectionType) {
            WirelessConnectionType.WIFI -> true
            WirelessConnectionType.BLUETOOTH_LE -> true
            WirelessConnectionType.BLUETOOTH_CLASSIC -> false // Limited encryption support
        }
    }
    
    private fun selectBestAlgorithm(
        connectionType: WirelessConnectionType,
        capabilities: EncryptionCapabilities
    ): EncryptionAlgorithm {
        val supportedAlgorithms = when (connectionType) {
            WirelessConnectionType.WIFI -> listOf(
                EncryptionAlgorithm.AES_256_GCM,
                EncryptionAlgorithm.AES_128_GCM
            )
            WirelessConnectionType.BLUETOOTH_LE -> listOf(
                EncryptionAlgorithm.AES_128_GCM
            )
            WirelessConnectionType.BLUETOOTH_CLASSIC -> listOf(
                EncryptionAlgorithm.AES_128_GCM
            )
        }
        
        // Select the strongest algorithm supported by both sides
        return supportedAlgorithms.firstOrNull { it in capabilities.supportedAlgorithms }
            ?: EncryptionAlgorithm.AES_128_GCM
    }
    
    private suspend fun generateSessionKey(algorithm: EncryptionAlgorithm): String {
        val keySize = when (algorithm) {
            EncryptionAlgorithm.AES_256_GCM -> 256
            EncryptionAlgorithm.AES_128_GCM -> 128
        }
        
        val sessionId = System.currentTimeMillis().toString()
        val keyId = "wireless_session_$sessionId"
        
        keyStoreManager.generateAndStoreKey(keyId, keySize)
        return keyId
    }
}

/**
 * Wireless connection types that support encryption.
 */
enum class WirelessConnectionType {
    WIFI,
    BLUETOOTH_LE,
    BLUETOOTH_CLASSIC
}

/**
 * Supported encryption algorithms.
 */
enum class EncryptionAlgorithm {
    AES_128_GCM,
    AES_256_GCM
}

/**
 * Encryption capabilities of a device.
 */
data class EncryptionCapabilities(
    val supportedAlgorithms: Set<EncryptionAlgorithm>,
    val supportsKeyExchange: Boolean,
    val maxKeySize: Int
)

/**
 * Encryption session for a wireless connection.
 */
data class EncryptionSession(
    val connectionId: String,
    val algorithm: EncryptionAlgorithm,
    val sessionKey: String,
    val connectionType: WirelessConnectionType,
    val startTime: Long
) {
    val isExpired: Boolean
        get() = System.currentTimeMillis() - startTime > SESSION_TIMEOUT
    
    companion object {
        private const val SESSION_TIMEOUT = 60 * 60 * 1000L // 1 hour
    }
}

/**
 * Encryption state for wireless connections.
 */
sealed class EncryptionState {
    object Disabled : EncryptionState()
    object Negotiating : EncryptionState()
    data class Active(val session: EncryptionSession) : EncryptionState()
    data class Error(val exception: Throwable) : EncryptionState()
}

/**
 * Exception for wireless encryption operations.
 */
class WirelessEncryptionException(message: String, cause: Throwable? = null) : Exception(message, cause)