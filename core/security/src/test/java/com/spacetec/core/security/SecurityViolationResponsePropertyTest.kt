/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 *
 * This file is part of the SpaceTec professional automotive diagnostic system.
 */

package com.spacetec.obd.core.security

import com.spacetec.obd.core.security.scanner.SecurityViolation
import com.spacetec.obd.core.security.scanner.ViolationSeverity
import com.spacetec.obd.core.security.scanner.ViolationType
import com.spacetec.obd.core.security.violation.SecurityViolationHandler
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.longs.shouldBeLessThanOrEqual
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.byte
import io.kotest.property.arbitrary.byteArray
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.orNull
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * **Feature: scanner-connection-system, Property 19: Security Violation Response**
 * **Validates: Requirements 10.4, 10.5**
 *
 * Property-based tests for security violation response.
 * Tests that security violations are properly detected, logged, and
 * appropriate response actions are taken based on violation severity.
 */
class SecurityViolationResponsePropertyTest : StringSpec({
    
    "Property 19.1: Security violations should be properly recorded and tracked" {
        checkAll(100, securityViolationArb()) { violation ->
            runBlocking {
                val handler = SecurityViolationHandler()

                // Start collecting before emitting to avoid missing the event
                // Ensure the collector starts immediately to avoid deadlock with SharedFlow emit
                // (MutableSharedFlow with replay=0 has no buffer; emit can suspend until a collector is ready)
                val emittedViolationDeferred = async(start = CoroutineStart.UNDISPATCHED) {
                    handler.violations.first()
                }
                
                // Handle the violation
                handler.handleViolation(violation)
                
                // Violation should be recorded in history
                val history = handler.getDeviceViolationHistory(violation.deviceAddress)
                history shouldContain violation
                
                // Violation count should be incremented
                val count = handler.getDeviceViolationCount(violation.deviceAddress)
                count.shouldBeGreaterThanOrEqual(1)
                
                // Violation should be emitted to flow
                val emittedViolation = withTimeout(1000) { emittedViolationDeferred.await() }
                emittedViolation shouldBe violation
            }
        }
    }
    
    "Property 19.2: Critical violations should result in immediate device blocking" {
        checkAll(100, criticalViolationArb()) { violation ->
            runBlocking {
                val handler = SecurityViolationHandler()
                
                // Handle critical violation
                handler.handleViolation(violation)
                
                // Device should be blocked for critical violations of certain types
                if (shouldBlockForViolationType(violation.type)) {
                    handler.isDeviceBlocked(violation.deviceAddress) shouldBe true
                    handler.getBlockedDevices() shouldContain violation.deviceAddress
                }
                
                // Violation should still be recorded
                val history = handler.getDeviceViolationHistory(violation.deviceAddress)
                history shouldContain violation
            }
        }
    }
    
    "Property 19.3: Repeated violations should trigger device blocking" {
        checkAll(100, deviceAddressArb()) { deviceAddress ->
            runBlocking {
                val handler = SecurityViolationHandler()
                
                // Generate multiple violations for the same device
                repeat(6) { i ->
                    val violation = SecurityViolation(
                        type = ViolationType.AUTHENTICATION_FAILURE,
                        severity = ViolationSeverity.MEDIUM,
                        description = "Repeated authentication failure $i",
                        deviceAddress = deviceAddress,
                        recommendedAction = "Monitor device"
                    )
                    handler.handleViolation(violation)
                }
                
                // Device should be blocked after too many violations
                handler.isDeviceBlocked(deviceAddress) shouldBe true
                handler.getDeviceViolationCount(deviceAddress).shouldBeGreaterThanOrEqual(5)
            }
        }
    }
    
    "Property 19.4: Device unblocking should clear violation history" {
        checkAll(100, deviceAddressArb()) { deviceAddress ->
            runBlocking {
                val handler = SecurityViolationHandler()
                
                // Block device first
                handler.blockDevice(deviceAddress, "Test blocking")
                handler.isDeviceBlocked(deviceAddress) shouldBe true
                
                // Unblock device
                handler.unblockDevice(deviceAddress)
                
                // Device should no longer be blocked
                handler.isDeviceBlocked(deviceAddress) shouldBe false
                handler.getBlockedDevices() shouldBe emptySet()
                
                // Violation count should be reset
                handler.getDeviceViolationCount(deviceAddress) shouldBe 0
            }
        }
    }
    
    "Property 19.5: Recent violations should be retrievable within time window" {
        checkAll(100, violationTimeWindowArb()) { (violations, timeWindow) ->
            runBlocking {
                val handler = SecurityViolationHandler()
                val currentTime = System.currentTimeMillis()
                
                // Handle violations with different timestamps
                violations.forEach { violation ->
                    handler.handleViolation(violation)
                }
                
                // Get recent violations
                val recentViolations = handler.getRecentViolations(timeWindow)
                
                // Should only include violations within time window
                recentViolations.forEach { violation ->
                    val age = currentTime - violation.timestamp
                    age.shouldBeLessThanOrEqual(timeWindow)
                }
                
                // Should include all recent violations
                val recentFromInput = violations.filter { 
                    currentTime - it.timestamp <= timeWindow 
                }
                recentFromInput.forEach { expectedViolation ->
                    recentViolations shouldContain expectedViolation
                }
            }
        }
    }
    
    "Property 19.6: Violation history clearing should reset all tracking" {
        checkAll(100, multipleViolationsArb()) { violations ->
            runBlocking {
                val handler = SecurityViolationHandler()
                
                // Handle multiple violations
                violations.forEach { violation ->
                    handler.handleViolation(violation)
                }
                
                // Verify violations are recorded
                violations.forEach { violation ->
                    handler.getDeviceViolationCount(violation.deviceAddress).shouldBeGreaterThanOrEqual(1)
                }
                
                // Clear violation history
                handler.clearViolationHistory()
                
                // All violation counts should be reset
                violations.forEach { violation ->
                    handler.getDeviceViolationCount(violation.deviceAddress) shouldBe 0
                }
                
                // Recent violations should be empty
                handler.getRecentViolations(Long.MAX_VALUE).shouldBe(emptyList())
            }
        }
    }
    
    "Property 19.7: Violation severity should determine response appropriateness" {
        checkAll(100, securityViolationArb()) { violation ->
            runBlocking {
                val handler = SecurityViolationHandler()
                
                // Handle violation
                handler.handleViolation(violation)
                
                // Violation should always be recorded regardless of severity
                val history = handler.getDeviceViolationHistory(violation.deviceAddress)
                history shouldContain violation
                
                // Response should be appropriate to severity
                when (violation.severity) {
                    ViolationSeverity.CRITICAL -> {
                        // Critical violations should have immediate impact
                        if (shouldBlockForViolationType(violation.type)) {
                            handler.isDeviceBlocked(violation.deviceAddress) shouldBe true
                        }
                    }
                    ViolationSeverity.HIGH -> {
                        // High severity violations should be tracked
                        handler.getDeviceViolationCount(violation.deviceAddress).shouldBeGreaterThanOrEqual(1)
                    }
                    ViolationSeverity.MEDIUM, ViolationSeverity.LOW -> {
                        // Medium/low violations should be logged but not immediately block
                        handler.getDeviceViolationCount(violation.deviceAddress).shouldBeGreaterThanOrEqual(1)
                    }
                }
            }
        }
    }
})

// ═══════════════════════════════════════════════════════════════════════════
// PROPERTY GENERATORS
// ═══════════════════════════════════════════════════════════════════════════

private fun securityViolationArb() = arbitrary {
    SecurityViolation(
        type = Arb.choice(
            Arb.constant(ViolationType.AUTHENTICATION_FAILURE),
            Arb.constant(ViolationType.DATA_TAMPERING),
            Arb.constant(ViolationType.UNAUTHORIZED_ACCESS),
            Arb.constant(ViolationType.ENCRYPTION_FAILURE),
            Arb.constant(ViolationType.CERTIFICATE_INVALID),
            Arb.constant(ViolationType.REPLAY_ATTACK),
            Arb.constant(ViolationType.INJECTION_ATTACK),
            Arb.constant(ViolationType.UNKNOWN_DEVICE)
        ).bind(),
        severity = Arb.choice(
            Arb.constant(ViolationSeverity.LOW),
            Arb.constant(ViolationSeverity.MEDIUM),
            Arb.constant(ViolationSeverity.HIGH),
            Arb.constant(ViolationSeverity.CRITICAL)
        ).bind(),
        description = Arb.string(10..100).bind(),
        deviceAddress = deviceAddressArb().bind(),
        timestamp = Arb.long(System.currentTimeMillis() - 3600000, System.currentTimeMillis()).bind(),
        evidence = Arb.byteArray(Arb.int(1..100), Arb.byte()).orNull().bind(),
        recommendedAction = Arb.choice(
            Arb.constant("Monitor device"),
            Arb.constant("Terminate connection"),
            Arb.constant("Block device"),
            Arb.constant("Alert user")
        ).bind()
    )
}

private fun criticalViolationArb() = arbitrary {
    securityViolationArb().bind().copy(severity = ViolationSeverity.CRITICAL)
}

private fun deviceAddressArb() = arbitrary {
    Arb.choice(
        Arb.constant("AA:BB:CC:DD:EE:FF"),
        Arb.constant("192.168.1.100"),
        Arb.constant("device-123"),
        Arb.string(10..20)
    ).bind()
}

private fun violationTimeWindowArb() = arbitrary {
    val currentTime = System.currentTimeMillis()
    val timeWindow = Arb.long(1000, 3600000).bind() // 1 second to 1 hour
    
    val violations = (1..5).map {
        val timestamp = Arb.long(currentTime - timeWindow * 2, currentTime).bind()
        securityViolationArb().bind().copy(timestamp = timestamp)
    }
    
    Pair(violations, timeWindow)
}

private fun multipleViolationsArb() = arbitrary {
    (1..10).map { securityViolationArb().bind() }
}

private fun shouldBlockForViolationType(type: ViolationType): Boolean {
    return when (type) {
        ViolationType.INJECTION_ATTACK -> true
        ViolationType.UNAUTHORIZED_ACCESS -> true
        ViolationType.UNKNOWN_DEVICE -> false
        else -> false
    }
}