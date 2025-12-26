package com.spacetec.protocol.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.crypto.spec.IvParameterSpec
import kotlin.random.Random

/**
 * Security manager for automotive diagnostic communication
 * Provides encryption, authentication, and secure communication features
 */
class SecurityManager {
    
    /**
     * Security levels for different operations
     */
    enum class SecurityLevel {
        UNSECURED,        // No security (basic diagnostics)
        AUTHENTICATED,    // Basic authentication required
        ENCRYPTED,        // Communication is encrypted
        SECURE_ACCESS     // High security for critical operations
    }
    
    /**
     * Security context for a session
     */
    data class SecurityContext(
        val level: SecurityLevel,
        val sessionId: String,
        val timestamp: Long,
        val encryptionKey: ByteArray? = null,
        val iv: ByteArray? = null,
        val isAuthenticated: Boolean = false
    )
    
    /**
     * Security token for authentication
     */
    data class SecurityToken(
        val token: String,
        val expiry: Long,
        val permissions: Set<SecurityPermission>
    )
    
    /**
     * Security permissions for different operations
     */
    enum class SecurityPermission {
        READ_DTC,
        CLEAR_DTC,
        READ_LIVE_DATA,
        ECU_PROGRAMMING,
        ECU_CODING,
        VEHICLE_CONFIG,
        SECURITY_ACCESS,
        ALL
    }
    
    private val secureRandom = SecureRandom()
    private val _activeSessions = mutableMapOf<String, SecurityContext>()
    private val securityTokens = mutableMapOf<String, SecurityToken>()
    
    /**
     * Creates a new security context for a session
     */
    fun createSecurityContext(level: SecurityLevel): SecurityContext {
        val sessionId = generateSessionId()
        val context = SecurityContext(
            level = level,
            sessionId = sessionId,
            timestamp = System.currentTimeMillis()
        )
        
        activeSessions[sessionId] = context
        return context
    }
    
    /**
     * Authenticates a security token
     */
    fun authenticateToken(token: String): Boolean {
        val securityToken = securityTokens[token] ?: return false
        
        // Check if token is expired
        if (securityToken.expiry < System.currentTimeMillis()) {
            securityTokens.remove(token)
            return false
        }
        
        return true
    }
    
    /**
     * Creates a security token with specified permissions
     */
    fun createSecurityToken(permissions: Set<SecurityPermission>, expiryMinutes: Int = 60): SecurityToken {
        val token = generateSecureToken()
        val expiry = System.currentTimeMillis() + (expiryMinutes * 60 * 1000L)
        
        val securityToken = SecurityToken(
            token = token,
            expiry = expiry,
            permissions = permissions
        )
        
        securityTokens[token] = securityToken
        return securityToken
    }
    
    /**
     * Checks if a token has permission for a specific operation
     */
    fun hasPermission(token: String, permission: SecurityPermission): Boolean {
        val securityToken = securityTokens[token] ?: return false
        
        // Check if token is expired
        if (securityToken.expiry < System.currentTimeMillis()) {
            securityTokens.remove(token)
            return false
        }
        
        return securityToken.permissions.contains(permission) || 
               securityToken.permissions.contains(SecurityPermission.ALL)
    }
    
    /**
     * Encrypts data using AES encryption
     */
    fun encryptData(data: ByteArray, key: ByteArray, iv: ByteArray): Result<ByteArray> {
        return try {
            val secretKey = SecretKeySpec(key, "AES")
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val ivSpec = IvParameterSpec(iv)
            
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec)
            val encryptedData = cipher.doFinal(data)
            
            Result.success(encryptedData)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Decrypts data using AES encryption
     */
    fun decryptData(encryptedData: ByteArray, key: ByteArray, iv: ByteArray): Result<ByteArray> {
        return try {
            val secretKey = SecretKeySpec(key, "AES")
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val ivSpec = IvParameterSpec(iv)
            
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
            val decryptedData = cipher.doFinal(encryptedData)
            
            Result.success(decryptedData)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Generates a secure hash of the data
     */
    fun generateHash(data: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        val hashBytes = md.digest(data)
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Verifies the integrity of data using a hash
     */
    fun verifyHash(data: ByteArray, expectedHash: String): Boolean {
        val actualHash = generateHash(data)
        return actualHash.equals(expectedHash, ignoreCase = true)
    }
    
    /**
     * Performs challenge-response authentication
     */
    fun performChallengeResponse(challenge: String): String {
        // In a real implementation, this would use a more sophisticated algorithm
        // For now, we'll use a simple transformation
        val reversed = challenge.reversed()
        val hashed = generateHash(reversed.toByteArray())
        return hashed.substring(0, minOf(32, hashed.length))
    }
    
    /**
     * Validates security for a specific operation
     */
    fun validateSecurity(
        operation: SecurityPermission,
        securityContext: SecurityContext,
        token: String? = null
    ): SecurityValidationResult {
        // Check session validity
        if (securityContext.timestamp + (30 * 60 * 1000L) < System.currentTimeMillis()) {
            // Session expired (30 minutes)
            return SecurityValidationResult(
                isValid = false,
                reason = "Session expired"
            )
        }
        
        // Check security level requirements
        val requiredLevel = when (operation) {
            SecurityPermission.ECU_PROGRAMMING,
            SecurityPermission.ECU_CODING,
            SecurityPermission.SECURITY_ACCESS -> SecurityLevel.SECURE_ACCESS
            SecurityPermission.VEHICLE_CONFIG -> SecurityLevel.ENCRYPTED
            SecurityPermission.READ_DTC,
            SecurityPermission.CLEAR_DTC -> SecurityLevel.AUTHENTICATED
            SecurityPermission.READ_LIVE_DATA -> SecurityLevel.UNSECURED
            SecurityPermission.ALL -> SecurityLevel.SECURE_ACCESS
        }
        
        if (securityContext.level.ordinal < requiredLevel.ordinal) {
            return SecurityValidationResult(
                isValid = false,
                reason = "Insufficient security level. Required: $requiredLevel, Current: ${securityContext.level}"
            )
        }
        
        // For high-security operations, token is required
        if (requiredLevel == SecurityLevel.SECURE_ACCESS || operation == SecurityPermission.ALL) {
            if (token == null) {
                return SecurityValidationResult(
                    isValid = false,
                    reason = "Security token required for this operation"
                )
            }
            
            if (!authenticateToken(token)) {
                return SecurityValidationResult(
                    isValid = false,
                    reason = "Invalid or expired security token"
                )
            }
            
            if (!hasPermission(token, operation)) {
                return SecurityValidationResult(
                    isValid = false,
                    reason = "Security token does not have permission for this operation"
                )
            }
        }
        
        return SecurityValidationResult(isValid = true)
    }
    
    /**
     * Generates a secure session key
     */
    fun generateSessionKey(): ByteArray {
        val key = ByteArray(32) // 256-bit key
        secureRandom.nextBytes(key)
        return key
    }
    
    /**
     * Generates a secure IV for encryption
     */
    fun generateIV(): ByteArray {
        val iv = ByteArray(16) // 128-bit IV for AES
        secureRandom.nextBytes(iv)
        return iv
    }
    
    /**
     * Gets a security context by session ID
     */
    fun getSecurityContext(sessionId: String): SecurityContext? {
        return _activeSessions[sessionId]
    }

    /**
     * Cleans up expired sessions and tokens
     */
    fun cleanupExpired(): Int {
        val now = System.currentTimeMillis()
        var cleanedCount = 0

        // Clean up expired sessions (older than 1 hour)
        val expiredSessions = _activeSessions.filter {
            it.value.timestamp + (60 * 60 * 1000L) < now
        }
        expiredSessions.forEach { _activeSessions.remove(it.key) }
        cleanedCount += expiredSessions.size

        // Clean up expired tokens
        val expiredTokens = securityTokens.filter {
            it.value.expiry < now
        }
        expiredTokens.forEach { securityTokens.remove(it.key) }
        cleanedCount += expiredTokens.size

        return cleanedCount
    }
    
    // Private helper methods
    private fun generateSessionId(): String {
        val randomBytes = ByteArray(16)
        secureRandom.nextBytes(randomBytes)
        return randomBytes.joinToString("") { "%02x".format(it) }
    }
    
    private fun generateSecureToken(): String {
        val randomBytes = ByteArray(32)
        secureRandom.nextBytes(randomBytes)
        return randomBytes.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Result of security validation
     */
    data class SecurityValidationResult(
        val isValid: Boolean,
        val reason: String = ""
    )
}

/**
 * Extension function to easily validate security
 */
fun SecurityManager.validateOperation(
    operation: SecurityManager.SecurityPermission,
    sessionId: String,
    token: String? = null
): SecurityManager.SecurityValidationResult {
    val context = this.getSecurityContext(sessionId)
    if (context == null) {
        return SecurityManager.SecurityValidationResult(
            isValid = false,
            reason = "Invalid session ID"
        )
    }

    return validateSecurity(operation, context, token)
}