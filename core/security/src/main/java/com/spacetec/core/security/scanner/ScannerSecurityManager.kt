/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 *
 * This file is part of the SpaceTec professional automotive diagnostic system.
 */

package com.spacetec.obd.core.security.scanner

import com.spacetec.obd.core.common.result.Result
import com.spacetec.obd.core.security.encryption.AESEncryption
import com.spacetec.obd.core.security.encryption.KeyStoreManager
import kotlinx.coroutines.flow.StateFlow
import java.security.cert.X509Certificate

/**
 * Security manager for scanner connections.
 * 
 * Provides comprehensive security verification, data integrity protection,
 * and encryption support for scanner communications.
 *
 * @author SpaceTec Development Team
 * @since 1.0.0
 */
interface ScannerSecurityManager {
    
    /**
     * Current security state of the connection.
     */
    val securityState: StateFlow<SecurityState>
    
    /**
     * Verifies the authenticity of a scanner device.
     *
     * @param deviceAddress Scanner device address
     * @param deviceInfo Device information for verification
     * @return Result indicating verification success or failure
     */
    suspend fun verifyScanner(
        deviceAddress: String,
        deviceInfo: ScannerDeviceInfo
    ): Result<ScannerAuthenticationResult, Throwable>
    
    /**
     * Validates a certificate for scanner authentication.
     *
     * @param certificate X.509 certificate to validate
     * @return Result indicating certificate validity
     */
    suspend fun validateCertificate(certificate: X509Certificate): Result<CertificateValidationResult, Throwable>
    
    /**
     * Checks data integrity using checksums and validation.
     *
     * @param data Data to verify
     * @param expectedChecksum Expected checksum (if available)
     * @return Result indicating data integrity status
     */
    suspend fun verifyDataIntegrity(
        data: ByteArray,
        expectedChecksum: String? = null
    ): Result<DataIntegrityResult, Throwable>
    
    /**
     * Detects potential data tampering.
     *
     * @param originalData Original data
     * @param receivedData Received data
     * @return Result indicating tampering detection status
     */
    suspend fun detectTampering(
        originalData: ByteArray,
        receivedData: ByteArray
    ): Result<TamperingDetectionResult, Throwable>
    
    /**
     * Encrypts data for wireless transmission.
     *
     * @param data Data to encrypt
     * @param connectionType Type of connection (for encryption selection)
     * @return Result with encrypted data
     */
    suspend fun encryptData(
        data: ByteArray,
        connectionType: String
    ): Result<EncryptedData, Throwable>
    
    /**
     * Decrypts received data.
     *
     * @param encryptedData Encrypted data to decrypt
     * @param connectionType Type of connection
     * @return Result with decrypted data
     */
    suspend fun decryptData(
        encryptedData: EncryptedData,
        connectionType: String
    ): Result<ByteArray, Throwable>
    
    /**
     * Handles security violations by terminating connections and alerting.
     *
     * @param violation Security violation details
     */
    suspend fun handleSecurityViolation(violation: SecurityViolation)
    
    /**
     * Enforces security policies for scanner connections.
     *
     * @param policy Security policy to enforce
     * @return Result indicating policy enforcement status
     */
    suspend fun enforceSecurityPolicy(policy: SecurityPolicy): Result<Unit, Throwable>
    
    /**
     * Resets security state and clears sensitive data.
     */
    suspend fun resetSecurity()
}

/**
 * Security state of a scanner connection.
 */
sealed class SecurityState {
    object Unverified : SecurityState()
    object Verifying : SecurityState()
    data class Verified(val authResult: ScannerAuthenticationResult) : SecurityState()
    data class Violation(val violation: SecurityViolation) : SecurityState()
    data class Error(val error: Throwable) : SecurityState()
}

/**
 * Information about a scanner device for security verification.
 */
data class ScannerDeviceInfo(
    val deviceName: String,
    val firmwareVersion: String?,
    val hardwareVersion: String?,
    val serialNumber: String?,
    val manufacturerId: String?,
    val deviceType: String,
    val capabilities: List<String> = emptyList()
)

/**
 * Result of scanner authentication.
 */
data class ScannerAuthenticationResult(
    val isAuthentic: Boolean,
    val trustLevel: TrustLevel,
    val certificate: X509Certificate? = null,
    val verificationMethod: String,
    val timestamp: Long = System.currentTimeMillis(),
    val warnings: List<String> = emptyList()
)

/**
 * Trust level for scanner devices.
 */
enum class TrustLevel {
    UNKNOWN,
    LOW,
    MEDIUM,
    HIGH,
    VERIFIED
}

/**
 * Result of certificate validation.
 */
data class CertificateValidationResult(
    val isValid: Boolean,
    val issuer: String,
    val subject: String,
    val expirationDate: Long,
    val validationErrors: List<String> = emptyList()
)

/**
 * Result of data integrity verification.
 */
data class DataIntegrityResult(
    val isIntact: Boolean,
    val checksum: String,
    val algorithm: String,
    val corruptionDetected: Boolean = false,
    val corruptionDetails: String? = null
)

/**
 * Result of tampering detection.
 */
data class TamperingDetectionResult(
    val tamperingDetected: Boolean,
    val tamperingType: TamperingType,
    val confidence: Float, // 0.0 to 1.0
    val details: String
)

/**
 * Types of tampering that can be detected.
 */
enum class TamperingType {
    NONE,
    DATA_MODIFICATION,
    INJECTION_ATTACK,
    REPLAY_ATTACK,
    MAN_IN_THE_MIDDLE,
    UNKNOWN
}

/**
 * Encrypted data container.
 */
data class EncryptedData(
    val data: ByteArray,
    val algorithm: String,
    val keyId: String,
    val iv: ByteArray? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EncryptedData

        if (!data.contentEquals(other.data)) return false
        if (algorithm != other.algorithm) return false
        if (keyId != other.keyId) return false
        if (iv != null) {
            if (other.iv == null) return false
            if (!iv.contentEquals(other.iv)) return false
        } else if (other.iv != null) return false
        if (timestamp != other.timestamp) return false

        return true
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + algorithm.hashCode()
        result = 31 * result + keyId.hashCode()
        result = 31 * result + (iv?.contentHashCode() ?: 0)
        result = 31 * result + timestamp.hashCode()
        return result
    }
}

/**
 * Security violation details.
 */
data class SecurityViolation(
    val type: ViolationType,
    val severity: ViolationSeverity,
    val description: String,
    val deviceAddress: String,
    val timestamp: Long = System.currentTimeMillis(),
    val evidence: ByteArray? = null,
    val recommendedAction: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SecurityViolation

        if (type != other.type) return false
        if (severity != other.severity) return false
        if (description != other.description) return false
        if (deviceAddress != other.deviceAddress) return false
        if (timestamp != other.timestamp) return false
        if (evidence != null) {
            if (other.evidence == null) return false
            if (!evidence.contentEquals(other.evidence)) return false
        } else if (other.evidence != null) return false
        if (recommendedAction != other.recommendedAction) return false

        return true
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + severity.hashCode()
        result = 31 * result + description.hashCode()
        result = 31 * result + deviceAddress.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + (evidence?.contentHashCode() ?: 0)
        result = 31 * result + recommendedAction.hashCode()
        return result
    }
}

/**
 * Types of security violations.
 */
enum class ViolationType {
    AUTHENTICATION_FAILURE,
    DATA_TAMPERING,
    UNAUTHORIZED_ACCESS,
    ENCRYPTION_FAILURE,
    CERTIFICATE_INVALID,
    REPLAY_ATTACK,
    INJECTION_ATTACK,
    UNKNOWN_DEVICE
}

/**
 * Severity levels for security violations.
 */
enum class ViolationSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

/**
 * Security policy configuration.
 */
data class SecurityPolicy(
    val requireAuthentication: Boolean = true,
    val requireEncryption: Boolean = false,
    val allowedTrustLevels: Set<TrustLevel> = setOf(TrustLevel.MEDIUM, TrustLevel.HIGH, TrustLevel.VERIFIED),
    val maxAuthenticationAttempts: Int = 3,
    val certificateValidationRequired: Boolean = false,
    val dataIntegrityCheckRequired: Boolean = true,
    val tamperingDetectionEnabled: Boolean = true,
    val encryptionAlgorithms: Set<String> = setOf("AES-256-GCM"),
    val violationResponsePolicy: ViolationResponsePolicy = ViolationResponsePolicy.TERMINATE_CONNECTION
)

/**
 * Response policy for security violations.
 */
enum class ViolationResponsePolicy {
    LOG_ONLY,
    ALERT_USER,
    TERMINATE_CONNECTION,
    BLOCK_DEVICE
}