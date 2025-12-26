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
import com.spacetec.obd.core.security.integrity.ChecksumAlgorithm
import com.spacetec.obd.core.security.integrity.DataIntegrityManager
import com.spacetec.obd.core.security.scanner.ScannerDeviceInfo
import com.spacetec.obd.core.security.scanner.ScannerSecurityManagerImpl
import com.spacetec.obd.core.security.scanner.TrustLevel
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.byte
import io.kotest.property.arbitrary.byteArray
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.orNull
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking

/**
 * **Feature: scanner-connection-system, Property 17: Security Verification and Data Integrity**
 * **Validates: Requirements 10.1, 10.2**
 *
 * Property-based tests for security verification and data integrity protection.
 * Tests that security verification correctly identifies authentic devices and
 * data integrity protection detects corruption or tampering.
 */
class SecurityVerificationPropertyTest : StringSpec({
    
    val mockAESEncryption = mockk<AESEncryption>()
    val mockKeyStoreManager = mockk<KeyStoreManager>()
    val mockDataIntegrityManager = mockk<DataIntegrityManager>()
    
    // Setup mocks
    beforeTest {
        coEvery { mockAESEncryption.encrypt(any(), any()) } returns Result.Success(byteArrayOf(1, 2, 3))
        coEvery { mockAESEncryption.decrypt(any(), any(), any()) } returns Result.Success(byteArrayOf(4, 5, 6))
        coEvery { mockKeyStoreManager.getKey(any()) } returns null
        coEvery { mockKeyStoreManager.storeKey(any(), any()) } returns Result.Success(Unit)
    }
    
    "Property 17.1: Scanner verification should correctly identify authentic devices" {
        val securityManager = ScannerSecurityManagerImpl(mockAESEncryption, mockKeyStoreManager)

        // Allow all trust levels so verification doesn't fail due to default policy restrictions.
        securityManager.enforceSecurityPolicy(
            com.spacetec.obd.core.security.scanner.SecurityPolicy(
                allowedTrustLevels = com.spacetec.obd.core.security.scanner.TrustLevel.values().toSet()
            )
        )
        
        checkAll(100, scannerDeviceInfoArb()) { deviceInfo ->
            runBlocking {
                val result = securityManager.verifyScanner("test-address", deviceInfo)
                
                // Verification should always return a result
                result.shouldBeInstanceOf<Result.Success<*>>()
                
                val authResult = (result as Result.Success).value
                
                // Authentic devices should have higher trust levels
                if (isDeviceInfoComplete(deviceInfo)) {
                    authResult.trustLevel shouldNotBe TrustLevel.UNKNOWN
                }
                
                // Verification method should be specified
                authResult.verificationMethod.isNotBlank() shouldBe true
            }
        }
    }
    
    "Property 17.2: Data integrity verification should detect corruption consistently" {
        val integrityManager = DataIntegrityManager()
        
        checkAll(100, Arb.byteArray(Arb.int(1..1024), Arb.byte())) { originalData ->
            runBlocking {
                // Calculate checksum for original data
                val checksumResult = integrityManager.calculateChecksum(originalData, ChecksumAlgorithm.SHA256)
                checksumResult.shouldBeInstanceOf<Result.Success<*>>()
                
                val checksum = (checksumResult as Result.Success).value
                
                // Verify integrity with correct checksum
                val verifyResult = integrityManager.verifyIntegrity(originalData, checksum, ChecksumAlgorithm.SHA256)
                verifyResult.shouldBeInstanceOf<Result.Success<*>>()
                
                val verificationResult = (verifyResult as Result.Success).value
                verificationResult.isValid shouldBe true
                verificationResult.corruptionDetected shouldBe false
                verificationResult.actualChecksum shouldBe checksum
            }
        }
    }
    
    "Property 17.3: Data integrity verification should detect tampering" {
        val integrityManager = DataIntegrityManager()
        
        checkAll(100, Arb.byteArray(Arb.int(2..1024), Arb.byte())) { originalData ->
            runBlocking {
                // Calculate checksum for original data
                val checksumResult = integrityManager.calculateChecksum(originalData, ChecksumAlgorithm.SHA256)
                checksumResult.shouldBeInstanceOf<Result.Success<*>>()
                
                val originalChecksum = (checksumResult as Result.Success).value
                
                // Create tampered data (modify first byte)
                val tamperedData = originalData.copyOf()
                tamperedData[0] = (tamperedData[0] + 1).toByte()
                
                // Verify integrity with original checksum should fail
                val verifyResult = integrityManager.verifyIntegrity(tamperedData, originalChecksum, ChecksumAlgorithm.SHA256)
                verifyResult.shouldBeInstanceOf<Result.Success<*>>()
                
                val verificationResult = (verifyResult as Result.Success).value
                verificationResult.isValid shouldBe false
                verificationResult.corruptionDetected shouldBe true
                verificationResult.actualChecksum shouldNotBe originalChecksum
            }
        }
    }
    
    "Property 17.4: Tampering detection should identify data modifications" {
        val integrityManager = DataIntegrityManager()
        
        checkAll(100, Arb.byteArray(Arb.int(2..1024), Arb.byte())) { originalData ->
            runBlocking {
                // Create modified data
                val modifiedData = originalData.copyOf()
                if (modifiedData.isNotEmpty()) {
                    modifiedData[0] = (modifiedData[0] + 1).toByte()
                }

                // DataIntegrityManager does not provide a dedicated "tampering detection" API.
                // Use checksum verification to confirm modifications are detected.
                val checksumResult = integrityManager.calculateChecksum(originalData, ChecksumAlgorithm.SHA256)
                checksumResult.shouldBeInstanceOf<Result.Success<*>>()

                val checksum = (checksumResult as Result.Success).value
                val verifyResult = integrityManager.verifyIntegrity(modifiedData, checksum, ChecksumAlgorithm.SHA256)
                verifyResult.shouldBeInstanceOf<Result.Success<*>>()

                val verification = (verifyResult as Result.Success).value
                verification.isValid shouldBe false
                verification.corruptionDetected shouldBe true
            }
        }
    }
    
    "Property 17.5: Integrity metadata should be consistent and verifiable" {
        val integrityManager = DataIntegrityManager()
        
        checkAll(100, Arb.byteArray(Arb.int(1..1024), Arb.byte())) { data ->
            runBlocking {
                // Create integrity metadata
                val metadataResult = integrityManager.createIntegrityMetadata(data)
                metadataResult.shouldBeInstanceOf<Result.Success<*>>()
                
                val metadata = (metadataResult as Result.Success).value
                
                // Metadata should match data properties
                metadata.size shouldBe data.size
                metadata.crc32.isNotBlank() shouldBe true
                metadata.sha256.isNotBlank() shouldBe true
                metadata.timestamp.shouldBeGreaterThan(0L)
                
                // Validate data against metadata should succeed
                val validationResult = integrityManager.validateAgainstMetadata(data, metadata)
                validationResult.shouldBeInstanceOf<Result.Success<*>>()
                
                val validation = (validationResult as Result.Success).value
                validation.isValid shouldBe true
                validation.issues.isEmpty() shouldBe true
            }
        }
    }
})

// ═══════════════════════════════════════════════════════════════════════════
// PROPERTY GENERATORS
// ═══════════════════════════════════════════════════════════════════════════

private fun scannerDeviceInfoArb() = arbitrary {
    ScannerDeviceInfo(
        deviceName = Arb.choice(
            Arb.constant("ELM327"),
            Arb.constant("OBDLink MX+"),
            Arb.constant("Veepeak BLE+"),
            Arb.string(5..20)
        ).bind(),
        firmwareVersion = Arb.choice(
            Arb.constant("1.5"),
            Arb.constant("2.1"),
            Arb.constant("v1.0.3"),
            Arb.string(3..10)
        ).orNull().bind(),
        hardwareVersion = Arb.choice(
            Arb.constant("v1.0"),
            Arb.constant("2.0"),
            Arb.string(3..10)
        ).orNull().bind(),
        serialNumber = Arb.choice(
            Arb.string(8..16),
            Arb.constant("SN123456789")
        ).orNull().bind(),
        manufacturerId = Arb.choice(
            Arb.constant("ELM Electronics"),
            Arb.constant("OBDLink"),
            Arb.constant("Veepeak"),
            Arb.string(5..20)
        ).orNull().bind(),
        deviceType = Arb.choice(
            Arb.constant("ELM327"),
            Arb.constant("STN1110"),
            Arb.constant("OBDLink MX+"),
            Arb.string(5..15)
        ).bind(),
        capabilities = Arb.list(Arb.string(3..10), 0..5).bind()
    )
}

private fun calculateExpectedTrustLevel(deviceInfo: ScannerDeviceInfo): TrustLevel {
    var score = 0
    
    val trustedManufacturers = setOf("ELM Electronics", "OBDLink", "Veepeak")
    if (trustedManufacturers.contains(deviceInfo.manufacturerId)) {
        score += 30
    }
    
    if (!deviceInfo.firmwareVersion.isNullOrBlank()) {
        score += 20
    }
    
    if (!deviceInfo.serialNumber.isNullOrBlank()) {
        score += 20
    }
    
    val knownDeviceTypes = setOf("ELM327", "STN1110", "OBDLink MX+")
    if (knownDeviceTypes.contains(deviceInfo.deviceType)) {
        score += 15
    }
    
    if (deviceInfo.capabilities.isNotEmpty()) {
        score += 15
    }
    
    return when {
        score >= 80 -> TrustLevel.VERIFIED
        score >= 60 -> TrustLevel.HIGH
        score >= 40 -> TrustLevel.MEDIUM
        score >= 20 -> TrustLevel.LOW
        else -> TrustLevel.UNKNOWN
    }
}

private fun isDeviceInfoComplete(deviceInfo: ScannerDeviceInfo): Boolean {
    return !deviceInfo.firmwareVersion.isNullOrBlank() &&
           !deviceInfo.serialNumber.isNullOrBlank() &&
           !deviceInfo.manufacturerId.isNullOrBlank()
}