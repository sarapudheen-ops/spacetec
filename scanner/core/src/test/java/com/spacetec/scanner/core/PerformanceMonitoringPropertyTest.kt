/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 *
 * This file is part of the SpaceTec professional automotive diagnostic system.
 */

package com.spacetec.obd.scanner.core

import com.spacetec.core.common.result.Result
import com.spacetec.core.domain.models.scanner.ScannerConnectionType
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.delay

/**
 * **Feature: scanner-connection-system, Property 12: Performance Monitoring and Alerting**
 * 
 * Property-based test for performance monitoring and alerting mechanisms.
 * 
 * **Validates: Requirements 6.3, 7.1, 7.2, 7.5**
 * 
 * This test verifies that:
 * 1. Connection statistics are accurately tracked (bytes sent/received, response times, errors)
 * 2. Performance metrics are continuously updated during active connections
 * 3. Response time statistics (min, max, average) are correctly calculated
 * 4. Error rates are accurately computed
 * 5. Statistics can be reset without affecting connection state
 */
class PerformanceMonitoringPropertyTest : StringSpec({
    
    "Property 12: Performance Monitoring - Connection statistics should accurately track all communication metrics" {
        checkAll(
            iterations = 100,
            Arb.enum<ScannerConnectionType>(),
            Arb.string(5..20), // address
            Arb.int(1..10) // number of commands to send
        ) { connectionType, address, commandCount ->
            
            val mockConnection = MockScannerConnection(connectionType)
            val config = ConnectionConfig.DEFAULT
            
            try {
                // Establish connection
                val connectResult = mockConnection.connect(address, config)
                connectResult.shouldBeInstanceOf<Result.Success<ConnectionInfo>>()
                
                // Get initial statistics
                val initialStats = mockConnection.getStatistics()
                initialStats.bytesSent shouldBe 0
                initialStats.bytesReceived shouldBe 0
                initialStats.commandsSent shouldBe 0
                initialStats.responsesReceived shouldBe 0
                initialStats.errors shouldBe 0
                
                // Send multiple commands and track expected values
                var expectedBytesSent = 0L
                var expectedBytesReceived = 0L
                
                for (i in 1..commandCount) {
                    val command = "AT$i"
                    val commandBytes = "$command\r".toByteArray()
                    expectedBytesSent += commandBytes.size
                    
                    val sendResult = mockConnection.sendCommand(command)
                    sendResult.shouldBeInstanceOf<Result.Success<Unit>>()
                    
                    val readResult = mockConnection.read(1000L)
                    readResult.shouldBeInstanceOf<Result.Success<ByteArray>>()
                    
                    if (readResult is Result.Success) {
                        expectedBytesReceived += readResult.data.size
                    }
                }
                
                // Verify statistics are accurately tracked
                val finalStats = mockConnection.getStatistics()
                
                // Verify byte counts
                finalStats.bytesSent shouldBe expectedBytesSent
                finalStats.bytesReceived shouldBe expectedBytesReceived
                finalStats.totalBytes shouldBe (expectedBytesSent + expectedBytesReceived)
                
                // Verify command/response counts
                finalStats.commandsSent shouldBe commandCount
                finalStats.responsesReceived shouldBe commandCount
                
                // Verify no errors occurred
                finalStats.errors shouldBe 0
                finalStats.errorRate shouldBe 0f
                finalStats.successRate shouldBe 100f
                
                // Verify response time statistics are populated
                if (commandCount > 0) {
                    finalStats.averageResponseTime shouldNotBe 0L
                    finalStats.minResponseTime shouldNotBe Long.MAX_VALUE
                    finalStats.maxResponseTime shouldNotBe 0L
                    
                    // Min should be <= average <= max
                    finalStats.minResponseTime shouldBe (finalStats.minResponseTime <= finalStats.averageResponseTime)
                    finalStats.averageResponseTime shouldBe (finalStats.averageResponseTime <= finalStats.maxResponseTime)
                }
                
            } finally {
                mockConnection.release()
            }
        }
    }
    
    "Property 12: Response time statistics should be correctly calculated across multiple operations" {
        checkAll(
            iterations = 50,
            Arb.string(5..15), // address
            Arb.list(Arb.long(10..500), 3..10) // list of simulated response times
        ) { address, responseTimes ->
            
            val mockConnection = MockScannerConnection()
            
            try {
                mockConnection.connect(address)
                
                // Simulate operations with known response times
                for (responseTime in responseTimes) {
                    mockConnection.readDelay = responseTime
                    mockConnection.sendCommand("AT")
                    delay(responseTime) // Simulate the delay
                    mockConnection.read(1000L)
                }
                
                val stats = mockConnection.getStatistics()
                
                // Verify min/max are within expected range
                val expectedMin = responseTimes.minOrNull() ?: 0L
                val expectedMax = responseTimes.maxOrNull() ?: 0L
                
                // Response times should be tracked (allowing for some measurement variance)
                stats.minResponseTime shouldBe (stats.minResponseTime <= expectedMin + 100)
                stats.maxResponseTime shouldBe (stats.maxResponseTime >= expectedMax - 100)
                
                // Average should be between min and max
                stats.averageResponseTime shouldBe (stats.averageResponseTime in stats.minResponseTime..stats.maxResponseTime)
                
            } finally {
                mockConnection.release()
            }
        }
    }
    
    "Property 12: Error rate should be accurately calculated" {
        checkAll(
            iterations = 50,
            Arb.string(5..15), // address
            Arb.int(5..20), // total operations
            Arb.int(0..5) // number of errors
        ) { address, totalOps, errorCount ->
            
            val mockConnection = MockScannerConnection()
            
            try {
                mockConnection.connect(address)
                
                val actualErrors = errorCount.coerceAtMost(totalOps)
                
                // Perform operations with some failures
                for (i in 1..totalOps) {
                    if (i <= actualErrors) {
                        // Simulate error
                        mockConnection.shouldFailWrite = true
                        mockConnection.sendCommand("AT")
                        mockConnection.shouldFailWrite = false
                    } else {
                        // Successful operation
                        mockConnection.sendCommand("AT")
                        mockConnection.read(1000L)
                    }
                }
                
                val stats = mockConnection.getStatistics()
                
                // Verify error count
                stats.errors shouldBe actualErrors
                
                // Verify error rate calculation
                val expectedErrorRate = if (totalOps > 0) {
                    (actualErrors.toFloat() / totalOps) * 100
                } else 0f
                
                // Allow for small floating point differences
                val errorRateDiff = kotlin.math.abs(stats.errorRate - expectedErrorRate)
                errorRateDiff shouldBe (errorRateDiff < 1.0f)
                
                // Verify success rate
                val expectedSuccessRate = 100f - expectedErrorRate
                val successRateDiff = kotlin.math.abs(stats.successRate - expectedSuccessRate)
                successRateDiff shouldBe (successRateDiff < 1.0f)
                
            } finally {
                mockConnection.release()
            }
        }
    }
    
    "Property 12: Statistics reset should clear all metrics without affecting connection state" {
        checkAll(
            iterations = 50,
            Arb.enum<ScannerConnectionType>(),
            Arb.string(5..15) // address
        ) { connectionType, address ->
            
            val mockConnection = MockScannerConnection(connectionType)
            
            try {
                // Establish connection and perform some operations
                mockConnection.connect(address)
                mockConnection.isConnected shouldBe true
                
                // Generate some statistics
                for (i in 1..5) {
                    mockConnection.sendCommand("AT$i")
                    mockConnection.read(1000L)
                }
                
                // Verify statistics are populated
                val statsBeforeReset = mockConnection.getStatistics()
                statsBeforeReset.bytesSent shouldNotBe 0L
                statsBeforeReset.bytesReceived shouldNotBe 0L
                statsBeforeReset.commandsSent shouldNotBe 0
                
                // Reset statistics
                mockConnection.resetStatistics()
                
                // Verify statistics are cleared
                val statsAfterReset = mockConnection.getStatistics()
                statsAfterReset.bytesSent shouldBe 0L
                statsAfterReset.bytesReceived shouldBe 0L
                statsAfterReset.commandsSent shouldBe 0
                statsAfterReset.responsesReceived shouldBe 0
                statsAfterReset.errors shouldBe 0
                statsAfterReset.minResponseTime shouldBe Long.MAX_VALUE
                statsAfterReset.maxResponseTime shouldBe 0L
                
                // Verify connection state is unaffected
                mockConnection.isConnected shouldBe true
                mockConnection.connectionState.value.shouldBeInstanceOf<ConnectionState.Connected>()
                
                // Verify connection still works after reset
                val sendResult = mockConnection.sendCommand("ATZ")
                sendResult.shouldBeInstanceOf<Result.Success<Unit>>()
                
            } finally {
                mockConnection.release()
            }
        }
    }
    
    "Property 12: Connection uptime should be tracked accurately" {
        checkAll(
            iterations = 30,
            Arb.string(5..15), // address
            Arb.long(100..500) // connection duration in ms
        ) { address, duration ->
            
            val mockConnection = MockScannerConnection()
            
            try {
                // Record start time
                val startTime = System.currentTimeMillis()
                
                // Establish connection
                mockConnection.connect(address)
                
                // Wait for specified duration
                delay(duration)
                
                // Get statistics
                val stats = mockConnection.getStatistics()
                val actualDuration = System.currentTimeMillis() - startTime
                
                // Verify uptime is tracked (allowing for some measurement variance)
                val uptimeDiff = kotlin.math.abs(stats.connectionUptime - actualDuration)
                uptimeDiff shouldBe (uptimeDiff < 100) // Within 100ms tolerance
                
                // Verify last activity time is recent
                val timeSinceActivity = System.currentTimeMillis() - stats.lastActivityTime
                timeSinceActivity shouldBe (timeSinceActivity < 1000) // Within 1 second
                
            } finally {
                mockConnection.release()
            }
        }
    }
    
    "Property 12: Throughput calculation should be accurate" {
        checkAll(
            iterations = 30,
            Arb.string(5..15), // address
            Arb.int(1..10) // number of operations
        ) { address, operationCount ->
            
            val mockConnection = MockScannerConnection()
            
            try {
                mockConnection.connect(address)
                
                // Perform operations
                for (i in 1..operationCount) {
                    mockConnection.sendCommand("AT$i")
                    mockConnection.read(1000L)
                }
                
                // Small delay to ensure uptime is non-zero
                delay(10)
                
                val stats = mockConnection.getStatistics()
                
                // Verify throughput is calculated
                if (stats.connectionUptime > 0) {
                    val expectedThroughput = (stats.totalBytes.toFloat() / stats.connectionUptime) * 1000
                    
                    // Allow for some variance in calculation
                    val throughputDiff = kotlin.math.abs(stats.throughputBps - expectedThroughput)
                    throughputDiff shouldBe (throughputDiff < expectedThroughput * 0.1f) // Within 10%
                }
                
            } finally {
                mockConnection.release()
            }
        }
    }
})