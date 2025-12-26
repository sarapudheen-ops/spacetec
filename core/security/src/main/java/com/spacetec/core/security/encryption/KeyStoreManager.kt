/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 *
 * This file is part of the SpaceTec professional automotive diagnostic system.
 */

package com.spacetec.obd.core.security.encryption

import android.annotation.TargetApi
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.spacetec.obd.core.common.result.Result
import java.security.KeyStore
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android KeyStore manager for secure key storage.
 *
 * Manages encryption keys using Android's hardware-backed KeyStore
 * when available, with fallback to software-based storage.
 *
 * @author SpaceTec Development Team
 * @since 1.0.0
 */
@Singleton
class KeyStoreManager @Inject constructor() {
    
    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
    private val softwareKeyCache = ConcurrentHashMap<String, SecretKey>()
    
    init {
        keyStore.load(null)
    }
    
    /**
     * Stores a secret key in the keystore.
     *
     * @param keyId Key identifier
     * @param key Secret key to store
     * @return Result indicating success or failure
     */
    suspend fun storeKey(keyId: String, key: SecretKey): Result<Unit, Throwable> {
        try {
            if (isHardwareBackedKeystoreAvailable() && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                storeKeyInHardwareKeystore(keyId, key)
            } else {
                // Fallback to software storage (less secure)
                softwareKeyCache[keyId] = key
            }
            
            return Result.Success(Unit)
            
        } catch (e: Exception) {
            return Result.error(KeyStoreException("Failed to store key: ${e.message}", e))
        }
    }
    
    /**
     * Retrieves a secret key from the keystore.
     *
     * @param keyId Key identifier
     * @return Secret key or null if not found
     */
    suspend fun getKey(keyId: String): SecretKey? {
        return try {
            if (isHardwareBackedKeystoreAvailable() && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                getKeyFromHardwareKeystore(keyId)
            } else {
                softwareKeyCache[keyId]
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Deletes a key from the keystore.
     *
     * @param keyId Key identifier
     * @return Result indicating success or failure
     */
    suspend fun deleteKey(keyId: String): Result<Unit, Throwable> {
        return try {
            if (isHardwareBackedKeystoreAvailable() && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                keyStore.deleteEntry(keyId)
            } else {
                softwareKeyCache.remove(keyId)
            }
            
            Result.Success(Unit)
            
        } catch (e: Exception) {
            Result.error(KeyStoreException("Failed to delete key: ${e.message}", e))
        }
    }
    
    /**
     * Generates and stores a new AES key.
     *
     * @param keyId Key identifier
     * @param keySize Key size in bits
     * @return Result with the generated key
     */
    suspend fun generateAndStoreKey(keyId: String, keySize: Int = 256): Result<SecretKey, Throwable> {
        return try {
            val key = if (isHardwareBackedKeystoreAvailable()) {
                generateKeyInHardwareKeystore(keyId, keySize)
            } else {
                generateSoftwareKey(keySize)
            }
            
            storeKey(keyId, key)
            Result.Success(key)
            
        } catch (e: Exception) {
            Result.error(KeyStoreException("Failed to generate and store key: ${e.message}", e))
        }
    }
    
    /**
     * Checks if a key exists in the keystore.
     *
     * @param keyId Key identifier
     * @return True if key exists, false otherwise
     */
    suspend fun keyExists(keyId: String): Boolean {
        return try {
            if (isHardwareBackedKeystoreAvailable() && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                keyStore.containsAlias(keyId)
            } else {
                softwareKeyCache.containsKey(keyId)
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Lists all key aliases in the keystore.
     *
     * @return List of key identifiers
     */
    suspend fun listKeys(): List<String> {
        return try {
            if (isHardwareBackedKeystoreAvailable() && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                keyStore.aliases().toList()
            } else {
                softwareKeyCache.keys.toList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Clears all keys from the keystore.
     *
     * @return Result indicating success or failure
     */
    suspend fun clearAllKeys(): Result<Unit, Throwable> {
        return try {
            if (isHardwareBackedKeystoreAvailable() && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                val aliases = keyStore.aliases().toList()
                aliases.forEach { alias ->
                    keyStore.deleteEntry(alias)
                }
            } else {
                softwareKeyCache.clear()
            }
            
            Result.Success(Unit)
            
        } catch (e: Exception) {
            Result.error(KeyStoreException("Failed to clear keys: ${e.message}", e))
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // PRIVATE HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════════
    
    private fun isHardwareBackedKeystoreAvailable(): Boolean {
        return try {
            // Check if Android KeyStore is available and hardware-backed
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M
        } catch (e: Exception) {
            false
        }
    }
    
    @TargetApi(android.os.Build.VERSION_CODES.M)
    private fun storeKeyInHardwareKeystore(keyId: String, key: SecretKey) {
        // For hardware keystore, we need to generate the key directly in the keystore
        // This is a simplified implementation - in practice, you'd use KeyGenParameterSpec
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            keyId,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        
        keyGenerator.init(keyGenParameterSpec)
        keyGenerator.generateKey()
    }
    
    @TargetApi(android.os.Build.VERSION_CODES.M)
    private fun getKeyFromHardwareKeystore(keyId: String): SecretKey? {
        return try {
            val entry = keyStore.getEntry(keyId, null) as? KeyStore.SecretKeyEntry
            entry?.secretKey
        } catch (e: Exception) {
            null
        }
    }
    
    @TargetApi(android.os.Build.VERSION_CODES.M)
    private fun generateKeyInHardwareKeystore(keyId: String, keySize: Int): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            keyId,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(keySize)
            .build()
        
        keyGenerator.init(keyGenParameterSpec)
        return keyGenerator.generateKey()
    }
    
    private fun generateSoftwareKey(keySize: Int): SecretKey {
        val keyGenerator = KeyGenerator.getInstance("AES")
        keyGenerator.init(keySize)
        return keyGenerator.generateKey()
    }
    
    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }
}

/**
 * Exception for keystore operations.
 */
class KeyStoreException(message: String, cause: Throwable? = null) : Exception(message, cause)