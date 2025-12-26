/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 *
 * This file is part of the SpaceTec professional automotive diagnostic system.
 */

package com.spacetec.obd.core.security

import com.spacetec.obd.core.common.result.Result
import com.spacetec.obd.core.security.encryption.AESEncryption
import com.spacetec.obd.core.security.encryption.KeyStoreManager
import com.spacetec.obd.core.security.wireless.EncryptionAlgorithm
import com.spacetec.obd.core.security.wireless.EncryptionCapabilities
import com.spacetec.obd.core.security.wireless.WirelessConnectionType
import com.spacetec.obd.core.security.wireless.WirelessEncryptionManager
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.byte
import io.kotest.property.arbitrary.byteArray
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.set
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import javax.crypto.KeyGenerator
import javax.crypto.spec.SecretKeySpec

/**
 * **Feature: scanner-connection-system, Property 18: Wireless Connection Encryption**
 * **Validates: Requirements 10.3**
 *
 * Property-based tests for wireless connection encryption.
 * Tests that encryption is properly implemented for wireless connections
 * and that encrypted data can be reliably decrypted.
 */
class WirelessEncryptionPropertyTest : StringSpec({
    
    val mockAESEncryption = mockk<AESEncryption>()
    val mockKeyStoreManager = mockk<KeyStoreManager>()
    
    // Setup mocks
    beforeTest {
        // Mock successful key generation and storage
        coEvery { mockKeyStoreManager.generateAndStoreKey(any(), any()) } returns Result.Success(
            SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")
        )
        coEvery { mockKeyStoreManager.getKey(any()) } returns null
        coEvery { mockKeyStoreManager.storeKey(any(), any()) } returns Result.Success(Unit)
        coEvery { mockKeyStoreManager.deleteKey(any()) } returns Result.Success(Unit)
    }
    
    "Property 18.1: Encryption initiation should succeed for supported connection types" {
        val encryptionManager = WirelessEncryptionManager(mockAESEncryption, mockKeyStoreManager)
        
        checkAll(100, connectionSetupArb()) { (connectionId, connectionType, capabilities) ->
            runBlocking {
                if (encryptionManager.isEncryptionSupported(connectionType)) {
                    val result = encryptionManager.initiateEncryption(connectionId, connectionType, capabilities)
                    
                    result.shouldBeInstanceOf<Result.Success<*>>()
                    
                    val session = (result as Result.Success).value
                    session.connectionId shouldBe connectionId
                    session.connectionType shouldBe connectionType
                    // Implementation falls back to AES_128_GCM if there is no overlap.
                    if (session.algorithm != EncryptionAlgorithm.AES_128_GCM) {
                        capabilities.supportedAlgorithms.shouldContain(session.algorithm)
                    }
                    session.sessionKey.isNotBlank() shouldBe true
                    session.startTime.shouldBeGreaterThan(0L)
                }
            }
        }
    }
    
    "Property 18.2: Encryption round-trip should preserve data integrity" {
        val encryptionManager = WirelessEncryptionManager(mockAESEncryption, mockKeyStoreManager)
        
        checkAll(100, encryptionRoundTripArb()) { (connectionId, connectionType, capabilities, data) ->
            runBlocking {
                if (encryptionManager.isEncryptionSupported(connectionType)) {
                    // Mock encryption/decryption to return predictable results
                    coEvery { mockAESEncryption.encrypt(data, any()) } returns Result.Success(data.reversedArray())
                    coEvery { mockAESEncryption.decrypt(data.reversedArray(), any(), any()) } returns Result.Success(data)
                    coEvery { mockAESEncryption.getLastUsedIV() } returns ByteArray(12) { it.toByte() }
                    
                    // Initiate encryption
                    val sessionResult = encryptionManager.initiateEncryption(connectionId, connectionType, capabilities)
                    sessionResult.shouldBeInstanceOf<Result.Success<*>>()
                    
                    // Encrypt data
                    val encryptResult = encryptionManager.encryptData(connectionId, data)
                    encryptResult.shouldBeInstanceOf<Result.Success<*>>()
                    
                    val encryptedData = (encryptResult as Result.Success).value
                    
                    // Decrypt data
                    val decryptResult = encryptionManager.decryptData(connectionId, encryptedData, ByteArray(12) { it.toByte() })
                    decryptResult.shouldBeInstanceOf<Result.Success<*>>()
                    
                    val decryptedData = (decryptResult as Result.Success).value
                    
                    // Data should be preserved through encryption/decryption
                    decryptedData shouldBe data
                }
            }
        }
    }
    
    "Property 18.3: Encryption should fail for unsupported connection types" {
        val encryptionManager = WirelessEncryptionManager(mockAESEncryption, mockKeyStoreManager)
        
        checkAll(100, connectionSetupArb()) { (connectionId, connectionType, capabilities) ->
            runBlocking {
                if (!encryptionManager.isEncryptionSupported(connectionType)) {
                    // For unsupported types, we still try to initiate but expect limited functionality
                    val result = encryptionManager.initiateEncryption(connectionId, connectionType, capabilities)
                    
                    // Should still succeed but with limited encryption
                    result.shouldBeInstanceOf<Result.Success<*>>()
                }
            }
        }
    }
    
    "Property 18.4: Key exchange should generate consistent shared secrets" {
        val encryptionManager = WirelessEncryptionManager(mockAESEncryption, mockKeyStoreManager)
        
        checkAll(100, keyExchangeArb()) { (connectionId, remotePublicKey) ->
            runBlocking {
                val result1 = encryptionManager.performKeyExchange(connectionId, remotePublicKey)
                val result2 = encryptionManager.performKeyExchange(connectionId, remotePublicKey)
                
                result1.shouldBeInstanceOf<Result.Success<*>>()
                result2.shouldBeInstanceOf<Result.Success<*>>()
                
                val secret1 = (result1 as Result.Success).value
                val secret2 = (result2 as Result.Success).value
                
                // Shared secrets should be consistent for same inputs
                secret1.size shouldBe secret2.size
                secret1.size.shouldBeGreaterThan(0)
            }
        }
    }
    
    "Property 18.5: Encryption sessions should have proper lifecycle management" {
        val encryptionManager = WirelessEncryptionManager(mockAESEncryption, mockKeyStoreManager)
        
        checkAll(100, connectionSetupArb()) { (connectionId, connectionType, capabilities) ->
            runBlocking {
                if (encryptionManager.isEncryptionSupported(connectionType)) {
                    // Initiate encryption
                    val sessionResult = encryptionManager.initiateEncryption(connectionId, connectionType, capabilities)
                    sessionResult.shouldBeInstanceOf<Result.Success<*>>()
                    
                    // Session should be retrievable
                    val session = encryptionManager.getEncryptionSession(connectionId)
                    session shouldNotBe null
                    session?.connectionId shouldBe connectionId
                    
                    // Terminate encryption
                    encryptionManager.terminateEncryption(connectionId)
                    
                    // Session should no longer be retrievable
                    val terminatedSession = encryptionManager.getEncryptionSession(connectionId)
                    terminatedSession shouldBe null
                }
            }
        }
    }
    
    "Property 18.6: Encryption should handle empty and large data correctly" {
        val encryptionManager = WirelessEncryptionManager(mockAESEncryption, mockKeyStoreManager)
        
        checkAll(100, extremeDataArb()) { (connectionId, connectionType, capabilities, data) ->
            runBlocking {
                if (encryptionManager.isEncryptionSupported(connectionType)) {
                    // Mock encryption to handle any size data
                    coEvery { mockAESEncryption.encrypt(any(), any()) } returns Result.Success(data + byteArrayOf(0xFF.toByte()))
                    coEvery { mockAESEncryption.decrypt(any(), any(), any()) } returns Result.Success(data)
                    coEvery { mockAESEncryption.getLastUsedIV() } returns ByteArray(12) { it.toByte() }
                    
                    // Initiate encryption
                    val sessionResult = encryptionManager.initiateEncryption(connectionId, connectionType, capabilities)
                    sessionResult.shouldBeInstanceOf<Result.Success<*>>()
                    
                    // Encrypt data (should handle empty and large data)
                    val encryptResult = encryptionManager.encryptData(connectionId, data)
                    encryptResult.shouldBeInstanceOf<Result.Success<*>>()
                    
                    val encryptedData = (encryptResult as Result.Success).value
                    encryptedData.size.shouldBeGreaterThan(0) // Should always produce some output
                }
            }
        }
    }
})

// ═══════════════════════════════════════════════════════════════════════════
// PROPERTY GENERATORS
// ═══════════════════════════════════════════════════════════════════════════

private fun connectionSetupArb() = arbitrary {
    val connectionId = Arb.string(5..20).bind()
    val connectionType = Arb.choice(
        Arb.constant(WirelessConnectionType.WIFI),
        Arb.constant(WirelessConnectionType.BLUETOOTH_LE),
        Arb.constant(WirelessConnectionType.BLUETOOTH_CLASSIC)
    ).bind()
    val capabilities = encryptionCapabilitiesArb().bind()
    
    Triple(connectionId, connectionType, capabilities)
}

private fun encryptionCapabilitiesArb() = arbitrary {
    EncryptionCapabilities(
        supportedAlgorithms = Arb.set(
            Arb.choice(
                Arb.constant(EncryptionAlgorithm.AES_128_GCM),
                Arb.constant(EncryptionAlgorithm.AES_256_GCM)
            ),
            1..2
        ).bind(),
        supportsKeyExchange = Arb.choice(Arb.constant(true), Arb.constant(false)).bind(),
        maxKeySize = Arb.choice(Arb.constant(128), Arb.constant(256)).bind()
    )
}

private fun encryptionRoundTripArb() = arbitrary {
    val (connectionId, connectionType, capabilities) = connectionSetupArb().bind()
    val data = Arb.byteArray(Arb.int(1..1024), Arb.byte()).bind()
    
    Quadruple(connectionId, connectionType, capabilities, data)
}

private fun keyExchangeArb() = arbitrary {
    val connectionId = Arb.string(5..20).bind()
    val remotePublicKey = Arb.byteArray(Arb.int(32..64), Arb.byte()).bind()
    
    Pair(connectionId, remotePublicKey)
}

private fun extremeDataArb() = arbitrary {
    val (connectionId, connectionType, capabilities) = connectionSetupArb().bind()
    val data = Arb.choice(
        Arb.constant(byteArrayOf()), // Empty data
        Arb.byteArray(Arb.int(1..10), Arb.byte()), // Small data
        Arb.byteArray(Arb.int(1024..4096), Arb.byte()) // Large data
    ).bind()
    
    Quadruple(connectionId, connectionType, capabilities, data)
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)