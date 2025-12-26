/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 *
 * This file is part of the SpaceTec professional automotive diagnostic system.
 */

package com.spacetec.obd.core.security.violation

import com.spacetec.obd.core.security.scanner.SecurityViolation
import com.spacetec.obd.core.security.scanner.ViolationSeverity
import com.spacetec.obd.core.security.scanner.ViolationType
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Security violation handler for scanner connections.
 *
 * Handles security violations by logging, alerting, and taking
 * appropriate response actions based on violation severity.
 *
 * @author SpaceTec Development Team
 * @since 1.0.0
 */
@Singleton
class SecurityViolationHandler @Inject constructor() {
    
    private val _violations = MutableSharedFlow<SecurityViolation>()
    val violations: SharedFlow<SecurityViolation> = _violations.asSharedFlow()
    
    private val violationHistory = mutableListOf<SecurityViolation>()
    private val deviceViolationCounts = ConcurrentHashMap<String, Int>()
    private val blockedDevices = mutableSetOf<String>()
    
    /**
     * Handles a security violation.
     *
     * @param violation Security violation to handle
     */
    suspend fun handleViolation(violation: SecurityViolation) {
        // Record violation
        violationHistory.add(violation)
        val currentCount = deviceViolationCounts[violation.deviceAddress] ?: 0
        deviceViolationCounts[violation.deviceAddress] = currentCount + 1
        
        // Emit violation event
        _violations.emit(violation)
        
        // Log violation
        logViolation(violation)
        
        // Take response action
        when (violation.severity) {
            ViolationSeverity.LOW -> handleLowSeverityViolation(violation)
            ViolationSeverity.MEDIUM -> handleMediumSeverityViolation(violation)
            ViolationSeverity.HIGH -> handleHighSeverityViolation(violation)
            ViolationSeverity.CRITICAL -> handleCriticalViolation(violation)
        }
        
        // Check for repeated violations
        checkRepeatedViolations(violation.deviceAddress)
    }
    
    /**
     * Checks if a device is blocked.
     *
     * @param deviceAddress Device address to check
     * @return True if device is blocked
     */
    fun isDeviceBlocked(deviceAddress: String): Boolean {
        return blockedDevices.contains(deviceAddress)
    }
    
    /**
     * Blocks a device from connecting.
     *
     * @param deviceAddress Device address to block
     * @param reason Reason for blocking
     */
    suspend fun blockDevice(deviceAddress: String, reason: String) {
        blockedDevices.add(deviceAddress)
        
        val blockViolation = SecurityViolation(
            type = ViolationType.UNAUTHORIZED_ACCESS,
            severity = ViolationSeverity.CRITICAL,
            description = "Device blocked: $reason",
            deviceAddress = deviceAddress,
            recommendedAction = "Device permanently blocked"
        )
        
        _violations.emit(blockViolation)
        logViolation(blockViolation)
    }
    
    /**
     * Unblocks a previously blocked device.
     *
     * @param deviceAddress Device address to unblock
     */
    fun unblockDevice(deviceAddress: String) {
        blockedDevices.remove(deviceAddress)
        deviceViolationCounts.remove(deviceAddress)
    }
    
    /**
     * Gets violation history for a device.
     *
     * @param deviceAddress Device address
     * @return List of violations for the device
     */
    fun getDeviceViolationHistory(deviceAddress: String): List<SecurityViolation> {
        return violationHistory.filter { it.deviceAddress == deviceAddress }
    }
    
    /**
     * Gets violation count for a device.
     *
     * @param deviceAddress Device address
     * @return Number of violations
     */
    fun getDeviceViolationCount(deviceAddress: String): Int {
        return deviceViolationCounts[deviceAddress] ?: 0
    }
    
    /**
     * Gets all blocked devices.
     *
     * @return Set of blocked device addresses
     */
    fun getBlockedDevices(): Set<String> {
        return blockedDevices.toSet()
    }
    
    /**
     * Clears violation history.
     */
    fun clearViolationHistory() {
        violationHistory.clear()
        deviceViolationCounts.clear()
    }
    
    /**
     * Gets recent violations within specified time window.
     *
     * @param timeWindowMs Time window in milliseconds
     * @return List of recent violations
     */
    fun getRecentViolations(timeWindowMs: Long): List<SecurityViolation> {
        val cutoffTime = System.currentTimeMillis() - timeWindowMs
        return violationHistory.filter { it.timestamp >= cutoffTime }
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // PRIVATE HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════════
    
    private suspend fun handleLowSeverityViolation(violation: SecurityViolation) {
        // Just log for low severity violations
        println("LOW SEVERITY VIOLATION: ${violation.description}")
    }
    
    private suspend fun handleMediumSeverityViolation(violation: SecurityViolation) {
        println("MEDIUM SEVERITY VIOLATION: ${violation.description}")
        
        // Alert user for medium severity
        alertUser(violation)
    }
    
    private suspend fun handleHighSeverityViolation(violation: SecurityViolation) {
        println("HIGH SEVERITY VIOLATION: ${violation.description}")
        
        // Alert user and consider connection termination
        alertUser(violation)
        
        if (shouldTerminateConnection(violation)) {
            terminateConnection(violation.deviceAddress, violation.description)
        }
    }
    
    private suspend fun handleCriticalViolation(violation: SecurityViolation) {
        println("CRITICAL VIOLATION: ${violation.description}")
        
        // Immediate response for critical violations
        alertUser(violation)
        terminateConnection(violation.deviceAddress, violation.description)
        
        // Consider blocking device for critical violations
        if (shouldBlockDevice(violation)) {
            blockDevice(violation.deviceAddress, violation.description)
        }
    }
    
    private suspend fun checkRepeatedViolations(deviceAddress: String) {
        val violationCount = deviceViolationCounts[deviceAddress] ?: 0
        
        if (violationCount >= MAX_VIOLATIONS_BEFORE_BLOCK) {
            blockDevice(deviceAddress, "Too many security violations ($violationCount)")
        }
    }
    
    private fun shouldTerminateConnection(violation: SecurityViolation): Boolean {
        return when (violation.type) {
            ViolationType.DATA_TAMPERING -> true
            ViolationType.AUTHENTICATION_FAILURE -> true
            ViolationType.UNAUTHORIZED_ACCESS -> true
            ViolationType.INJECTION_ATTACK -> true
            ViolationType.REPLAY_ATTACK -> true
            else -> false
        }
    }
    
    private fun shouldBlockDevice(violation: SecurityViolation): Boolean {
        return when (violation.type) {
            ViolationType.INJECTION_ATTACK -> true
            ViolationType.UNAUTHORIZED_ACCESS -> true
            ViolationType.UNKNOWN_DEVICE -> false // Don't block unknown devices immediately
            else -> false
        }
    }
    
    private fun logViolation(violation: SecurityViolation) {
        val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date(violation.timestamp))
        
        println("=== SECURITY VIOLATION ===")
        println("Timestamp: $timestamp")
        println("Type: ${violation.type}")
        println("Severity: ${violation.severity}")
        println("Device: ${violation.deviceAddress}")
        println("Description: ${violation.description}")
        println("Recommended Action: ${violation.recommendedAction}")
        
        if (violation.evidence != null) {
            println("Evidence: ${violation.evidence.size} bytes")
        }
        
        println("==========================")
    }
    
    private suspend fun alertUser(violation: SecurityViolation) {
        // In a real implementation, this would show a user notification
        println("🚨 SECURITY ALERT: ${violation.description}")
        println("   Device: ${violation.deviceAddress}")
        println("   Action: ${violation.recommendedAction}")
    }
    
    private suspend fun terminateConnection(deviceAddress: String, reason: String) {
        // In a real implementation, this would terminate the actual connection
        println("🔌 TERMINATING CONNECTION: $deviceAddress")
        println("   Reason: $reason")
    }
    
    companion object {
        private const val MAX_VIOLATIONS_BEFORE_BLOCK = 5
    }
}