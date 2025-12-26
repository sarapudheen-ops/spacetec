/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 *
 * This file is part of the SpaceTec professional automotive diagnostic system.
 */

package com.spacetec.obd.core.security.encryption

import com.spacetec.obd.core.common.result.Result
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AES encryption implementation for secure data transmission.
 *
 * Provides AES-GCM encryption/decryption with secure key management
 * for scanner communications.
 *
 * @author SpaceTec Development Team
 * @since 1.0.0
 */
@Singleton
class AESEncryption @Inject constructor(
    private val keyStoreManager: KeyStoreManager
) {
    
    private val secureRandom = SecureRandom()
    private var lastUsedIV: ByteArray? = null
    
    /**
     * Encrypts data using AES-GCM.
     *
     * @param data Data to encrypt
     * @param keyId Key identifier for encryption
     * @return Result with encrypted data
     */
    suspend fun encrypt(data: ByteArray, keyId: String): Result<ByteArray, Throwable> {
        try {
            val key = getOrCreateKey(keyId)
            val iv = generateIV()
            lastUsedIV = iv
            
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec)
            
            val encryptedData = cipher.doFinal(data)
            
            return Result.Success(encryptedData)
            
        } catch (e: Exception) {
            return Result.error(EncryptionException("AES encryption failed: ${e.message}", e))
        }
    }
    
    /**
     * Decrypts data using AES-GCM.
     *
     * @param encryptedData Encrypted data
     * @param keyId Key identifier for decryption
     * @param iv Initialization vector used for encryption
     * @return Result with decrypted data
     */
    suspend fun decrypt(encryptedData: ByteArray, keyId: String, iv: ByteArray?): Result<ByteArray, Throwable> {
        try {
            if (iv == null) {
                return Result.error(EncryptionException("IV is required for AES-GCM decryption"))
            }
            
            val key = getOrCreateKey(keyId)
            
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec)
            
            val decryptedData = cipher.doFinal(encryptedData)
            
            return Result.Success(decryptedData)
            
        } catch (e: Exception) {
            return Result.error(EncryptionException("AES decryption failed: ${e.message}", e))
        }
    }
    
    /**
     * Generates a new AES key.
     *
     * @param keySize Key size in bits (128, 192, or 256)
     * @return Generated secret key
     */
    fun generateKey(keySize: Int = 256): SecretKey {
        val keyGenerator = KeyGenerator.getInstance("AES")
        keyGenerator.init(keySize)
        return keyGenerator.generateKey()
    }
    
    /**
     * Gets the last used initialization vector.
     *
     * @return Last used IV or null if none
     */
    fun getLastUsedIV(): ByteArray? = lastUsedIV?.copyOf()
    
    /**
     * Generates a random initialization vector.
     *
     * @return Random IV bytes
     */
    private fun generateIV(): ByteArray {
        val iv = ByteArray(GCM_IV_LENGTH)
        secureRandom.nextBytes(iv)
        return iv
    }
    
    /**
     * Gets or creates an encryption key for the given ID.
     *
     * @param keyId Key identifier
     * @return Secret key
     */
    private suspend fun getOrCreateKey(keyId: String): SecretKey {
        // Try to get existing key from keystore
        val existingKey = keyStoreManager.getKey(keyId)
        if (existingKey != null) {
            return existingKey
        }
        
        // Generate new key
        val newKey = generateKey()
        keyStoreManager.storeKey(keyId, newKey)
        return newKey
    }
    
    companion object {
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12 // 96 bits
        private const val GCM_TAG_LENGTH = 128 // 128 bits
    }
}

/**
 * Exception for encryption operations.
 */
class EncryptionException(message: String, cause: Throwable? = null) : Exception(message, cause)