/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 *
 * This file is part of the SpaceTec professional automotive diagnostic system.
 */

package com.spacetec.obd.core.security.integrity

import com.spacetec.obd.core.common.result.Result
import java.security.MessageDigest
import java.util.zip.CRC32
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data integrity manager for scanner communications.
 *
 * Provides comprehensive data integrity verification using multiple
 * algorithms including checksums, hashes, and corruption detection.
 *
 * @author SpaceTec Development Team
 * @since 1.0.0
 */
@Singleton
class DataIntegrityManager @Inject constructor() {
    
    /**
     * Calculates checksum for data using specified algorithm.
     *
     * @param data Data to calculate checksum for
     * @param algorithm Checksum algorithm (CRC32, MD5, SHA-1, SHA-256)
     * @return Result with checksum string
     */
    suspend fun calculateChecksum(data: ByteArray, algorithm: ChecksumAlgorithm): Result<String, Throwable> {
        return try {
            val checksum = when (algorithm) {
                ChecksumAlgorithm.CRC32 -> calculateCRC32(data)
                ChecksumAlgorithm.MD5 -> calculateHash(data, "MD5")
                ChecksumAlgorithm.SHA1 -> calculateHash(data, "SHA-1")
                ChecksumAlgorithm.SHA256 -> calculateHash(data, "SHA-256")
            }
            
            Result.Success(checksum)
            
        } catch (e: Exception) {
            Result.error(IntegrityException("Checksum calculation failed: ${e.message}", e))
        }
    }
    
    /**
     * Verifies data integrity against expected checksum.
     *
     * @param data Data to verify
     * @param expectedChecksum Expected checksum value
     * @param algorithm Algorithm used for checksum
     * @return Result with verification status
     */
    suspend fun verifyIntegrity(
        data: ByteArray,
        expectedChecksum: String,
        algorithm: ChecksumAlgorithm
    ): Result<IntegrityVerificationResult, Throwable> {
        return try {
            val calculatedChecksum = calculateChecksum(data, algorithm)
            
            if (calculatedChecksum is Result.Failure) {
                return calculatedChecksum
            }
            
            val actualChecksum = (calculatedChecksum as Result.Success).value
            val isValid = actualChecksum.equals(expectedChecksum, ignoreCase = true)
            
            val result = IntegrityVerificationResult(
                isValid = isValid,
                expectedChecksum = expectedChecksum,
                actualChecksum = actualChecksum,
                algorithm = algorithm,
                corruptionDetected = !isValid
            )
            
            Result.Success(result)
            
        } catch (e: Exception) {
            Result.error(IntegrityException("Integrity verification failed: ${e.message}", e))
        }
    }
    
    /**
     * Detects potential data corruption patterns.
     *
     * @param data Data to analyze
     * @return Result with corruption analysis
     */
    suspend fun detectCorruption(data: ByteArray): Result<CorruptionAnalysis, Throwable> {
        return try {
            val analysis = CorruptionAnalysis(
                dataSize = data.size,
                nullByteCount = data.count { it == 0.toByte() },
                repeatingPatterns = detectRepeatingPatterns(data),
                entropyScore = calculateEntropy(data),
                suspiciousPatterns = detectSuspiciousPatterns(data)
            )
            
            Result.Success(analysis)
            
        } catch (e: Exception) {
            Result.error(IntegrityException("Corruption detection failed: ${e.message}", e))
        }
    }
    
    /**
     * Creates integrity metadata for data.
     *
     * @param data Data to create metadata for
     * @return Result with integrity metadata
     */
    suspend fun createIntegrityMetadata(data: ByteArray): Result<IntegrityMetadata, Throwable> {
        return try {
            val crc32 = calculateChecksum(data, ChecksumAlgorithm.CRC32)
            val sha256 = calculateChecksum(data, ChecksumAlgorithm.SHA256)
            
            if (crc32 is Result.Failure) return crc32
            if (sha256 is Result.Failure) return sha256
            
            val metadata = IntegrityMetadata(
                size = data.size,
                crc32 = (crc32 as Result.Success).value,
                sha256 = (sha256 as Result.Success).value,
                timestamp = System.currentTimeMillis(),
                version = METADATA_VERSION
            )
            
            Result.Success(metadata)
            
        } catch (e: Exception) {
            Result.error(IntegrityException("Metadata creation failed: ${e.message}", e))
        }
    }
    
    /**
     * Validates data against integrity metadata.
     *
     * @param data Data to validate
     * @param metadata Integrity metadata
     * @return Result with validation status
     */
    suspend fun validateAgainstMetadata(
        data: ByteArray,
        metadata: IntegrityMetadata
    ): Result<MetadataValidationResult, Throwable> {
        return try {
            val issues = mutableListOf<String>()
            
            // Check size
            if (data.size != metadata.size) {
                issues.add("Size mismatch: expected ${metadata.size}, got ${data.size}")
            }
            
            // Verify CRC32
            val crc32Result = calculateChecksum(data, ChecksumAlgorithm.CRC32)
            if (crc32Result is Result.Success) {
                if (!crc32Result.value.equals(metadata.crc32, ignoreCase = true)) {
                    issues.add("CRC32 mismatch")
                }
            } else {
                issues.add("CRC32 calculation failed")
            }
            
            // Verify SHA-256
            val sha256Result = calculateChecksum(data, ChecksumAlgorithm.SHA256)
            if (sha256Result is Result.Success) {
                if (!sha256Result.value.equals(metadata.sha256, ignoreCase = true)) {
                    issues.add("SHA-256 mismatch")
                }
            } else {
                issues.add("SHA-256 calculation failed")
            }
            
            // Check metadata age
            val age = System.currentTimeMillis() - metadata.timestamp
            if (age > MAX_METADATA_AGE) {
                issues.add("Metadata is too old (${age}ms)")
            }
            
            val result = MetadataValidationResult(
                isValid = issues.isEmpty(),
                issues = issues,
                metadata = metadata
            )
            
            Result.Success(result)
            
        } catch (e: Exception) {
            Result.error(IntegrityException("Metadata validation failed: ${e.message}", e))
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // PRIVATE HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════════
    
    private fun calculateCRC32(data: ByteArray): String {
        val crc = CRC32()
        crc.update(data)
        return crc.value.toString(16).uppercase()
    }
    
    private fun calculateHash(data: ByteArray, algorithm: String): String {
        val digest = MessageDigest.getInstance(algorithm)
        val hash = digest.digest(data)
        return hash.joinToString("") { "%02x".format(it) }.uppercase()
    }
    
    private fun detectRepeatingPatterns(data: ByteArray): List<RepeatingPattern> {
        val patterns = mutableListOf<RepeatingPattern>()
        
        // Look for repeating byte sequences
        for (patternLength in 2..16) {
            if (patternLength * 3 > data.size) break
            
            for (start in 0..data.size - patternLength * 3) {
                val pattern = data.sliceArray(start until start + patternLength)
                var repetitions = 1
                var pos = start + patternLength
                
                while (pos + patternLength <= data.size) {
                    val nextSegment = data.sliceArray(pos until pos + patternLength)
                    if (pattern.contentEquals(nextSegment)) {
                        repetitions++
                        pos += patternLength
                    } else {
                        break
                    }
                }
                
                if (repetitions >= 3) {
                    patterns.add(
                        RepeatingPattern(
                            pattern = pattern,
                            startPosition = start,
                            repetitions = repetitions,
                            totalLength = repetitions * patternLength
                        )
                    )
                }
            }
        }
        
        return patterns.distinctBy { "${it.pattern.contentHashCode()}-${it.startPosition}" }
    }
    
    private fun calculateEntropy(data: ByteArray): Double {
        if (data.isEmpty()) return 0.0
        
        val frequency = IntArray(256)
        data.forEach { byte ->
            frequency[byte.toInt() and 0xFF]++
        }
        
        var entropy = 0.0
        val length = data.size.toDouble()
        
        frequency.forEach { count ->
            if (count > 0) {
                val probability = count / length
                entropy -= probability * (kotlin.math.ln(probability) / kotlin.math.ln(2.0))
            }
        }
        
        return entropy
    }
    
    private fun detectSuspiciousPatterns(data: ByteArray): List<SuspiciousPattern> {
        val patterns = mutableListOf<SuspiciousPattern>()
        
        // Check for all zeros
        if (data.all { it == 0.toByte() }) {
            patterns.add(
                SuspiciousPattern(
                    type = "ALL_ZEROS",
                    description = "Data consists entirely of zero bytes",
                    severity = SuspiciousPattern.Severity.HIGH
                )
            )
        }
        
        // Check for all same byte
        val firstByte = data.firstOrNull()
        if (firstByte != null && data.all { it == firstByte }) {
            patterns.add(
                SuspiciousPattern(
                    type = "ALL_SAME_BYTE",
                    description = "Data consists entirely of the same byte value (0x${firstByte.toString(16)})",
                    severity = SuspiciousPattern.Severity.MEDIUM
                )
            )
        }
        
        // Check for very low entropy (likely corrupted or encrypted)
        val entropy = calculateEntropy(data)
        if (entropy < 1.0) {
            patterns.add(
                SuspiciousPattern(
                    type = "LOW_ENTROPY",
                    description = "Data has very low entropy ($entropy), possibly corrupted",
                    severity = SuspiciousPattern.Severity.MEDIUM
                )
            )
        }
        
        return patterns
    }
    
    companion object {
        private const val METADATA_VERSION = 1
        private const val MAX_METADATA_AGE = 24 * 60 * 60 * 1000L // 24 hours
    }
}

/**
 * Supported checksum algorithms.
 */
enum class ChecksumAlgorithm {
    CRC32,
    MD5,
    SHA1,
    SHA256
}

/**
 * Result of integrity verification.
 */
data class IntegrityVerificationResult(
    val isValid: Boolean,
    val expectedChecksum: String,
    val actualChecksum: String,
    val algorithm: ChecksumAlgorithm,
    val corruptionDetected: Boolean
)

/**
 * Analysis of potential data corruption.
 */
data class CorruptionAnalysis(
    val dataSize: Int,
    val nullByteCount: Int,
    val repeatingPatterns: List<RepeatingPattern>,
    val entropyScore: Double,
    val suspiciousPatterns: List<SuspiciousPattern>
) {
    val corruptionLikelihood: CorruptionLikelihood
        get() = when {
            suspiciousPatterns.any { it.severity == SuspiciousPattern.Severity.HIGH } -> CorruptionLikelihood.HIGH
            suspiciousPatterns.any { it.severity == SuspiciousPattern.Severity.MEDIUM } -> CorruptionLikelihood.MEDIUM
            repeatingPatterns.isNotEmpty() || entropyScore < 2.0 -> CorruptionLikelihood.LOW
            else -> CorruptionLikelihood.NONE
        }
}

/**
 * Corruption likelihood levels.
 */
enum class CorruptionLikelihood {
    NONE,
    LOW,
    MEDIUM,
    HIGH
}

/**
 * Repeating pattern in data.
 */
data class RepeatingPattern(
    val pattern: ByteArray,
    val startPosition: Int,
    val repetitions: Int,
    val totalLength: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RepeatingPattern

        if (!pattern.contentEquals(other.pattern)) return false
        if (startPosition != other.startPosition) return false
        if (repetitions != other.repetitions) return false
        if (totalLength != other.totalLength) return false

        return true
    }

    override fun hashCode(): Int {
        var result = pattern.contentHashCode()
        result = 31 * result + startPosition
        result = 31 * result + repetitions
        result = 31 * result + totalLength
        return result
    }
}

/**
 * Suspicious pattern in data.
 */
data class SuspiciousPattern(
    val type: String,
    val description: String,
    val severity: Severity
) {
    enum class Severity {
        LOW,
        MEDIUM,
        HIGH
    }
}

/**
 * Integrity metadata for data.
 */
data class IntegrityMetadata(
    val size: Int,
    val crc32: String,
    val sha256: String,
    val timestamp: Long,
    val version: Int
)

/**
 * Result of metadata validation.
 */
data class MetadataValidationResult(
    val isValid: Boolean,
    val issues: List<String>,
    val metadata: IntegrityMetadata
)

/**
 * Exception for integrity operations.
 */
class IntegrityException(message: String, cause: Throwable? = null) : Exception(message, cause)