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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.security.cert.CertificateExpiredException
import java.security.cert.CertificateNotYetValidException
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of scanner security manager.
 *
 * Provides comprehensive security verification, data integrity protection,
 * and encryption support for scanner communications.
 *
 * @author SpaceTec Development Team
 * @since 1.0.0
 */
@Singleton
class ScannerSecurityManagerImpl @Inject constructor(
    private val aesEncryption: AESEncryption,
    private val keyStoreManager: KeyStoreManager
) : ScannerSecurityManager {
    
    private val _securityState = MutableStateFlow<SecurityState>(SecurityState.Unverified)
    override val securityState: StateFlow<SecurityState> = _securityState.asStateFlow()
    
    private val trustedDevices = ConcurrentHashMap<String, ScannerAuthenticationResult>()
    private val deviceTrustCache = ConcurrentHashMap<String, TrustLevel>()
    private val securityViolationListeners = mutableListOf<SecurityViolationListener>()
    
    private var currentSecurityPolicy = SecurityPolicy()
    
    override suspend fun verifyScanner(
        deviceAddress: String,
        deviceInfo: ScannerDeviceInfo
    ): Result<ScannerAuthenticationResult, Throwable> {
        try {
            _securityState.value = SecurityState.Verifying
            
            // Check if device is already trusted
            trustedDevices[deviceAddress]?.let { cachedResult ->
                if (System.currentTimeMillis() - cachedResult.timestamp < TRUST_CACHE_DURATION) {
                    _securityState.value = SecurityState.Verified(cachedResult)
                    return Result.Success(cachedResult)
                }
            }
            
            val trustLevel = calculateTrustLevel(deviceInfo)
            val warnings = mutableListOf<String>()
            
            // Perform various authentication checks
            val isAuthentic = performAuthenticationChecks(deviceAddress, deviceInfo, warnings)
            
            val authResult = ScannerAuthenticationResult(
                isAuthentic = isAuthentic,
                trustLevel = trustLevel,
                verificationMethod = "Multi-factor verification",
                warnings = warnings
            )
            
            // Cache the result if authentic
            if (isAuthentic) {
                trustedDevices[deviceAddress] = authResult
                deviceTrustCache[deviceAddress] = trustLevel
            }
            
            _securityState.value = SecurityState.Verified(authResult)
            
            // Check against security policy
            if (!currentSecurityPolicy.allowedTrustLevels.contains(trustLevel)) {
                val violation = SecurityViolation(
                    type = ViolationType.AUTHENTICATION_FAILURE,
                    severity = ViolationSeverity.HIGH,
                    description = "Device trust level ($trustLevel) not allowed by security policy",
                    deviceAddress = deviceAddress,
                    recommendedAction = "Block device or update security policy"
                )
                handleSecurityViolation(violation)
                return Result.error(SecurityException("Device trust level not allowed"))
            }
            
            return Result.Success(authResult)
            
        } catch (e: Exception) {
            val error = SecurityException("Scanner verification failed: ${e.message}", e)
            _securityState.value = SecurityState.Error(error)
            return Result.error(error)
        }
    }
    
    override suspend fun validateCertificate(certificate: X509Certificate): Result<CertificateValidationResult, Throwable> {
        try {
            val validationErrors = mutableListOf<String>()
            var isValid = true
            
            // Check certificate validity period
            try {
                certificate.checkValidity()
            } catch (e: CertificateExpiredException) {
                validationErrors.add("Certificate has expired")
                isValid = false
            } catch (e: CertificateNotYetValidException) {
                validationErrors.add("Certificate is not yet valid")
                isValid = false
            }
            
            // Check certificate chain (simplified - in production would verify against CA)
            if (certificate.issuerDN == null) {
                validationErrors.add("Certificate has no issuer")
                isValid = false
            }
            
            // Check key usage
            val keyUsage = certificate.keyUsage
            if (keyUsage != null && !keyUsage[0]) { // Digital signature
                validationErrors.add("Certificate does not allow digital signatures")
                isValid = false
            }
            
            val result = CertificateValidationResult(
                isValid = isValid,
                issuer = certificate.issuerDN?.name ?: "Unknown",
                subject = certificate.subjectDN?.name ?: "Unknown",
                expirationDate = certificate.notAfter.time,
                validationErrors = validationErrors
            )
            
            return Result.Success(result)
            
        } catch (e: Exception) {
            return Result.error(SecurityException("Certificate validation failed: ${e.message}", e))
        }
    }
    
    override suspend fun verifyDataIntegrity(
        data: ByteArray,
        expectedChecksum: String?
    ): Result<DataIntegrityResult, Throwable> {
        try {
            val algorithm = "SHA-256"
            val digest = MessageDigest.getInstance(algorithm)
            val checksum = digest.digest(data).joinToString("") { "%02x".format(it) }
            
            val isIntact = expectedChecksum?.let { it.equals(checksum, ignoreCase = true) } ?: true
            val corruptionDetected = expectedChecksum != null && !isIntact
            
            val result = DataIntegrityResult(
                isIntact = isIntact,
                checksum = checksum,
                algorithm = algorithm,
                corruptionDetected = corruptionDetected,
                corruptionDetails = if (corruptionDetected) {
                    "Expected: $expectedChecksum, Actual: $checksum"
                } else null
            )
            
            if (corruptionDetected) {
                val violation = SecurityViolation(
                    type = ViolationType.DATA_TAMPERING,
                    severity = ViolationSeverity.HIGH,
                    description = "Data corruption detected - checksum mismatch",
                    deviceAddress = "unknown",
                    evidence = data,
                    recommendedAction = "Reject data and request retransmission"
                )
                handleSecurityViolation(violation)
            }
            
            return Result.Success(result)
            
        } catch (e: Exception) {
            return Result.error(SecurityException("Data integrity verification failed: ${e.message}", e))
        }
    }
    
    override suspend fun detectTampering(
        originalData: ByteArray,
        receivedData: ByteArray
    ): Result<TamperingDetectionResult, Throwable> {
        try {
            if (originalData.contentEquals(receivedData)) {
                return Result.Success(
                    TamperingDetectionResult(
                        tamperingDetected = false,
                        tamperingType = TamperingType.NONE,
                        confidence = 1.0f,
                        details = "Data integrity verified"
                    )
                )
            }
            
            // Analyze the differences to determine tampering type
            val tamperingType = analyzeTamperingPattern(originalData, receivedData)
            val confidence = calculateTamperingConfidence(originalData, receivedData)
            
            val result = TamperingDetectionResult(
                tamperingDetected = true,
                tamperingType = tamperingType,
                confidence = confidence,
                details = "Data modification detected: ${originalData.size} -> ${receivedData.size} bytes"
            )
            
            // Report tampering violation
            val violation = SecurityViolation(
                type = ViolationType.DATA_TAMPERING,
                severity = if (confidence > 0.8f) ViolationSeverity.CRITICAL else ViolationSeverity.HIGH,
                description = "Data tampering detected: $tamperingType (confidence: ${(confidence * 100).toInt()}%)",
                deviceAddress = "unknown",
                evidence = receivedData,
                recommendedAction = "Terminate connection and alert user"
            )
            handleSecurityViolation(violation)
            
            return Result.Success(result)
            
        } catch (e: Exception) {
            return Result.error(SecurityException("Tampering detection failed: ${e.message}", e))
        }
    }
    
    override suspend fun encryptData(
        data: ByteArray,
        connectionType: String
    ): Result<EncryptedData, Throwable> {
        try {
            // Select encryption algorithm based on connection type and policy
            val algorithm = selectEncryptionAlgorithm(connectionType)
            
            if (!currentSecurityPolicy.encryptionAlgorithms.contains(algorithm)) {
                return Result.error(SecurityException("Encryption algorithm $algorithm not allowed by policy"))
            }
            
            val keyId = "scanner_connection_key_$connectionType"
            val encryptionResult = aesEncryption.encrypt(data, keyId)
            
            if (encryptionResult is Result.Failure) {
                return encryptionResult
            }
            
            val encryptedBytes = (encryptionResult as Result.Success).value
            val iv = aesEncryption.getLastUsedIV()
            
            val encryptedData = EncryptedData(
                data = encryptedBytes,
                algorithm = algorithm,
                keyId = keyId,
                iv = iv
            )
            
            return Result.Success(encryptedData)
            
        } catch (e: Exception) {
            return Result.error(SecurityException("Data encryption failed: ${e.message}", e))
        }
    }
    
    override suspend fun decryptData(
        encryptedData: EncryptedData,
        connectionType: String
    ): Result<ByteArray, Throwable> {
        try {
            if (!currentSecurityPolicy.encryptionAlgorithms.contains(encryptedData.algorithm)) {
                return Result.error(SecurityException("Encryption algorithm ${encryptedData.algorithm} not allowed"))
            }
            
            // Check data age
            val dataAge = System.currentTimeMillis() - encryptedData.timestamp
            if (dataAge > MAX_ENCRYPTED_DATA_AGE) {
                val violation = SecurityViolation(
                    type = ViolationType.REPLAY_ATTACK,
                    severity = ViolationSeverity.MEDIUM,
                    description = "Encrypted data too old (${dataAge}ms), possible replay attack",
                    deviceAddress = "unknown",
                    recommendedAction = "Reject old data"
                )
                handleSecurityViolation(violation)
                return Result.error(SecurityException("Encrypted data too old"))
            }
            
            return aesEncryption.decrypt(encryptedData.data, encryptedData.keyId, encryptedData.iv)
            
        } catch (e: Exception) {
            return Result.error(SecurityException("Data decryption failed: ${e.message}", e))
        }
    }
    
    override suspend fun handleSecurityViolation(violation: SecurityViolation) {
        try {
            // Log the violation
            logSecurityViolation(violation)
            
            // Update security state
            _securityState.value = SecurityState.Violation(violation)
            
            // Notify listeners
            securityViolationListeners.forEach { listener ->
                try {
                    listener.onSecurityViolation(violation)
                } catch (e: Exception) {
                    // Don't let listener errors affect violation handling
                }
            }
            
            // Take action based on policy
            when (currentSecurityPolicy.violationResponsePolicy) {
                ViolationResponsePolicy.LOG_ONLY -> {
                    // Already logged above
                }
                ViolationResponsePolicy.ALERT_USER -> {
                    alertUser(violation)
                }
                ViolationResponsePolicy.TERMINATE_CONNECTION -> {
                    terminateConnection(violation.deviceAddress)
                }
                ViolationResponsePolicy.BLOCK_DEVICE -> {
                    blockDevice(violation.deviceAddress)
                }
            }
            
        } catch (e: Exception) {
            // Critical: violation handling failed
            println("CRITICAL: Security violation handling failed: ${e.message}")
        }
    }
    
    override suspend fun enforceSecurityPolicy(policy: SecurityPolicy): Result<Unit, Throwable> {
        try {
            currentSecurityPolicy = policy
            
            // Collect violations first
            val violations = mutableListOf<SecurityViolation>()
            val toRemove = mutableListOf<String>()
            
            trustedDevices.entries.forEach { (address, authResult) ->
                if (!policy.allowedTrustLevels.contains(authResult.trustLevel)) {
                    violations.add(SecurityViolation(
                        type = ViolationType.UNAUTHORIZED_ACCESS,
                        severity = ViolationSeverity.HIGH,
                        description = "Device no longer meets security policy requirements",
                        deviceAddress = address,
                        recommendedAction = "Disconnect device"
                    ))
                    toRemove.add(address)
                }
            }
            
            // Handle violations
            violations.forEach { handleSecurityViolation(it) }
            
            // Remove non-compliant devices
            toRemove.forEach { trustedDevices.remove(it) }
            
            return Result.Success(Unit)
            
        } catch (e: Exception) {
            return Result.error(SecurityException("Security policy enforcement failed: ${e.message}", e))
        }
    }
    
    override suspend fun resetSecurity() {
        _securityState.value = SecurityState.Unverified
        trustedDevices.clear()
        deviceTrustCache.clear()
        currentSecurityPolicy = SecurityPolicy()
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // PRIVATE HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════════
    
    private fun calculateTrustLevel(deviceInfo: ScannerDeviceInfo): TrustLevel {
        var score = 0
        
        // Known manufacturer
        if (TRUSTED_MANUFACTURERS.contains(deviceInfo.manufacturerId)) {
            score += 30
        }
        
        // Has firmware version
        if (!deviceInfo.firmwareVersion.isNullOrBlank()) {
            score += 20
        }
        
        // Has serial number
        if (!deviceInfo.serialNumber.isNullOrBlank()) {
            score += 20
        }
        
        // Known device type
        if (KNOWN_DEVICE_TYPES.contains(deviceInfo.deviceType)) {
            score += 15
        }
        
        // Has capabilities
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
    
    private suspend fun performAuthenticationChecks(
        deviceAddress: String,
        deviceInfo: ScannerDeviceInfo,
        warnings: MutableList<String>
    ): Boolean {
        var authentic = true
        
        // Check device name patterns
        if (deviceInfo.deviceName.contains("fake", ignoreCase = true) ||
            deviceInfo.deviceName.contains("clone", ignoreCase = true)) {
            warnings.add("Device name suggests non-authentic device")
            authentic = false
        }
        
        // Check firmware version format
        deviceInfo.firmwareVersion?.let { version ->
            if (!version.matches(Regex("\\d+\\.\\d+.*"))) {
                warnings.add("Firmware version format is unusual")
            }
        }
        
        // Check for known malicious devices
        if (BLACKLISTED_DEVICES.contains(deviceAddress) ||
            BLACKLISTED_DEVICES.contains(deviceInfo.serialNumber)) {
            warnings.add("Device is blacklisted")
            authentic = false
        }
        
        return authentic
    }
    
    private fun analyzeTamperingPattern(original: ByteArray, received: ByteArray): TamperingType {
        return when {
            received.size > original.size * 2 -> TamperingType.INJECTION_ATTACK
            received.size < original.size / 2 -> TamperingType.DATA_MODIFICATION
            original.contentEquals(received) -> TamperingType.NONE
            else -> TamperingType.DATA_MODIFICATION
        }
    }
    
    private fun calculateTamperingConfidence(original: ByteArray, received: ByteArray): Float {
        if (original.contentEquals(received)) return 0.0f
        
        val sizeDiff = kotlin.math.abs(original.size - received.size)
        val maxSize = maxOf(original.size, received.size)
        
        return (sizeDiff.toFloat() / maxSize).coerceIn(0.0f, 1.0f)
    }
    
    private fun selectEncryptionAlgorithm(connectionType: String): String {
        return when (connectionType.lowercase()) {
            "wifi", "ethernet" -> "AES-256-GCM"
            "bluetooth" -> "AES-128-GCM"
            else -> "AES-256-GCM"
        }
    }
    
    private fun logSecurityViolation(violation: SecurityViolation) {
        println("SECURITY VIOLATION: ${violation.type} - ${violation.description}")
        println("  Device: ${violation.deviceAddress}")
        println("  Severity: ${violation.severity}")
        println("  Recommended Action: ${violation.recommendedAction}")
    }
    
    private suspend fun alertUser(violation: SecurityViolation) {
        // In a real implementation, this would show a user alert
        println("SECURITY ALERT: ${violation.description}")
    }
    
    private suspend fun terminateConnection(deviceAddress: String) {
        // In a real implementation, this would terminate the connection
        println("TERMINATING CONNECTION: $deviceAddress")
        trustedDevices.remove(deviceAddress)
        deviceTrustCache.remove(deviceAddress)
    }
    
    private suspend fun blockDevice(deviceAddress: String) {
        // In a real implementation, this would add to a blocked devices list
        println("BLOCKING DEVICE: $deviceAddress")
        trustedDevices.remove(deviceAddress)
        deviceTrustCache.remove(deviceAddress)
    }
    
    /**
     * Adds a security violation listener.
     */
    fun addSecurityViolationListener(listener: SecurityViolationListener) {
        securityViolationListeners.add(listener)
    }
    
    /**
     * Removes a security violation listener.
     */
    fun removeSecurityViolationListener(listener: SecurityViolationListener) {
        securityViolationListeners.remove(listener)
    }
    
    companion object {
        private const val TRUST_CACHE_DURATION = 24 * 60 * 60 * 1000L // 24 hours
        private const val MAX_ENCRYPTED_DATA_AGE = 5 * 60 * 1000L // 5 minutes
        
        private val TRUSTED_MANUFACTURERS = setOf(
            "ELM Electronics",
            "OBDLink",
            "Veepeak",
            "BAFX Products",
            "ScanTool.net"
        )
        
        private val KNOWN_DEVICE_TYPES = setOf(
            "ELM327",
            "STN1110",
            "STN2120",
            "OBDLink MX+",
            "OBDLink LX",
            "Veepeak OBDCheck BLE+"
        )
        
        private val BLACKLISTED_DEVICES = setOf<String>(
            // Add known malicious device addresses/serials
        )
    }
}

/**
 * Listener interface for security violations.
 */
interface SecurityViolationListener {
    suspend fun onSecurityViolation(violation: SecurityViolation)
}

/**
 * Security exception for scanner security operations.
 */
class SecurityException(message: String, cause: Throwable? = null) : Exception(message, cause)