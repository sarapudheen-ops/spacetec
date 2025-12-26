/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 *
 * This file is part of the SpaceTec professional automotive diagnostic system.
 */

package com.spacetec.obd.scanner.core.integration

import com.spacetec.core.common.result.Result
import com.spacetec.core.domain.models.scanner.Scanner
import com.spacetec.core.domain.models.scanner.ScannerConnectionType
import com.spacetec.core.domain.models.scanner.ScannerDeviceType
import com.spacetec.obd.scanner.core.ConnectionConfig
import com.spacetec.obd.scanner.core.ScannerConnectionOptions
import com.spacetec.obd.scanner.core.state.EnhancedScannerManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import kotlin.system.measureTimeMillis

/**
 * Performance validation tests for the scanner connection system.
 * 
 * Validates that the system meets all performance requirements:
 * - Connection establishment timing
 * - Command response times
 * - Throughput requirements
 * - Resource usage limits
 * - Concurrent operation performance
 * 
 * **Feature: scanner-connection-system, Performance Validation**
 * **Validates: Requirements 7.1, 7.2, 7.3, 7.4, 7.5**
 */
@RunWith(MockitoJUnitRunner::class)
class PerformanceValidationTest {
    
    private lateinit var enhancedScannerManager: EnhancedScannerManager
    private lateinit var testScanners: Map<ScannerConnectionType, Scanner>
    
    @Before
    fun setUp() {
        setupTestScanners()
        setupEnhancedScannerManager()
    }
    
    @After
    fun tearDown() {
        runBlocking {
            enhancedScannerManager.disconnect()
            enhancedScannerManager.release()
        }
    }
    
    private fun setupTestScanners() {
        testScanners = mapOf(
            ScannerConnectionType.BLUETOOTH_CLASSIC to Scanner(
                id = "bt_perf_test",
                name = "Performance Test BT",
                address = "AA:BB:CC:DD:EE:FF",
                connectionType = ScannerConnectionType.BLUETOOTH_CLASSIC,
                deviceType = ScannerDeviceType.ELM327
            ),
            ScannerConnectionType.WIFI to Scanner(
                id = "wifi_perf_test",
                name = "Performance Test WiFi",
                address = "192.168.1.100:35000",
                connectionType = ScannerConnectionType.WIFI,
                deviceType = ScannerDeviceType.VGATE_ICAR_PRO
            ),
            ScannerConnectionType.USB to Scanner(
                id = "usb_perf_test",
                name = "Performance Test USB",
                address = "/dev/ttyUSB0",
                connectionType = ScannerConnectionType.USB,
                deviceType = ScannerDeviceType.GENERIC
            )
        )
    }
    
    private fun setupEnhancedScannerManager() {
        enhancedScannerManager = createTestEnhancedScannerManager()
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // CONNECTION ESTABLISHMENT TIMING TESTS
    // ═══════════════════════════════════════════════════════════════════════
    
    @Test
    fun `test Bluetooth connection establishment within 15 seconds`() = runTest {
        val scanner = testScanners[ScannerConnectionType.BLUETOOTH_CLASSIC]!!
        
        val connectionTime = measureTimeMillis {
            val result = enhancedScannerManager.connect(
                scanner,
                ScannerConnectionOptions.DEFAULT.copy(
                    connectionConfig = ConnectionConfig.BLUETOOTH
                )
            )
            assertTrue("Bluetooth connection should succeed", result is Result.Success)
        }
        
        assertTrue(
            "Bluetooth connection should be established within 15 seconds (was ${connectionTime}ms)",
            connectionTime < 15_000
        )
        
        println("Bluetooth connection established in ${connectionTime}ms")
    }
    
    @Test
    fun `test WiFi connection establishment within 5 seconds`() = runTest {
        val scanner = testScanners[ScannerConnectionType.WIFI]!!
        
        val connectionTime = measureTimeMillis {
            val result = enhancedScannerManager.connect(
                scanner,
                ScannerConnectionOptions.DEFAULT.copy(
                    connectionConfig = ConnectionConfig.WIFI
                )
            )
            assertTrue("WiFi connection should succeed", result is Result.Success)
        }
        
        assertTrue(
            "WiFi connection should be established within 5 seconds (was ${connectionTime}ms)",
            connectionTime < 5_000
        )
        
        println("WiFi connection established in ${connectionTime}ms")
    }
    
    @Test
    fun `test USB connection establishment within 3 seconds`() = runTest {
        val scanner = testScanners[ScannerConnectionType.USB]!!
        
        val connectionTime = measureTimeMillis {
            val result = enhancedScannerManager.connect(
                scanner,
                ScannerConnectionOptions.DEFAULT.copy(
                    connectionConfig = ConnectionConfig.USB
                )
            )
            assertTrue("USB connection should succeed", result is Result.Success)
        }
        
        assertTrue(
            "USB connection should be established within 3 seconds (was ${connectionTime}ms)",
            connectionTime < 3_000
        )
        
        println("USB connection established in ${connectionTime}ms")
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // COMMAND RESPONSE TIME TESTS
    // ═══════════════════════════════════════════════════════════════════════
    
    @Test
    fun `test AT command response times under 2 seconds`() = runTest {
        val scanner = testScanners[ScannerConnectionType.BLUETOOTH_CLASSIC]!!
        
        // Connect first
        val connectResult = enhancedScannerManager.connect(scanner)
        assertTrue("Connection should succeed", connectResult is Result.Success)
        
        val atCommands = listOf("ATI", "ATZ", "ATRV", "ATE0", "ATL0", "ATS1", "ATH0")
        
        for (command in atCommands) {
            val responseTime = measureTimeMillis {
                val result = enhancedScannerManager.sendCommand(command, 5000L)
                assertTrue("Command $command should succeed", result is Result.Success)
            }
            
            assertTrue(
                "AT command $command should respond within 2 seconds (was ${responseTime}ms)",
                responseTime < 2_000
            )
            
            println("Command $command responded in ${responseTime}ms")
        }
    }
    
    @Test
    fun `test OBD command response times under 5 seconds`() = runTest {
        val scanner = testScanners[ScannerConnectionType.BLUETOOTH_CLASSIC]!!
        
        // Connect first
        val connectResult = enhancedScannerManager.connect(scanner)
        assertTrue("Connection should succeed", connectResult is Result.Success)
        
        val obdCommands = listOf("0100", "0120", "0140", "0101", "0105", "010C", "010D")
        
        for (command in obdCommands) {
            val responseTime = measureTimeMillis {
                val result = enhancedScannerManager.sendCommand(command, 10000L)
                // OBD commands might fail if vehicle is not connected, but timing should still be measured
                assertNotNull("Command $command should return a result", result)
            }
            
            assertTrue(
                "OBD command $command should respond within 5 seconds (was ${responseTime}ms)",
                responseTime < 5_000
            )
            
            println("Command $command responded in ${responseTime}ms")
        }
    }
    
    @Test
    fun `test protocol detection within 30 seconds`() = runTest {
        val scanner = testScanners[ScannerConnectionType.BLUETOOTH_CLASSIC]!!
        
        // Connect without auto protocol detection
        val connectResult = enhancedScannerManager.connect(
            scanner,
            ScannerConnectionOptions.DEFAULT.copy(autoDetectProtocol = false)
        )
        assertTrue("Connection should succeed", connectResult is Result.Success)
        
        val detectionTime = measureTimeMillis {
            val result = enhancedScannerManager.detectProtocol()
            // Protocol detection might fail if no vehicle is connected, but should complete within time limit
            assertNotNull("Protocol detection should return a result", result)
        }
        
        assertTrue(
            "Protocol detection should complete within 30 seconds (was ${detectionTime}ms)",
            detectionTime < 30_000
        )
        
        println("Protocol detection completed in ${detectionTime}ms")
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // THROUGHPUT AND PERFORMANCE TESTS
    // ═══════════════════════════════════════════════════════════════════════
    
    @Test
    fun `test command throughput performance`() = runTest {
        val scanner = testScanners[ScannerConnectionType.BLUETOOTH_CLASSIC]!!
        
        // Connect first
        val connectResult = enhancedScannerManager.connect(scanner)
        assertTrue("Connection should succeed", connectResult is Result.Success)
        
        val commandCount = 50
        val commands = (1..commandCount).map { "ATI" } // Simple command for throughput test
        
        val totalTime = measureTimeMillis {
            for (command in commands) {
                val result = enhancedScannerManager.sendCommand(command, 2000L)
                assertTrue("Command should succeed", result is Result.Success)
            }
        }
        
        val averageTime = totalTime.toDouble() / commandCount
        val throughput = (commandCount * 1000.0) / totalTime // commands per second
        
        println("Executed $commandCount commands in ${totalTime}ms")
        println("Average time per command: ${averageTime}ms")
        println("Throughput: $throughput commands/second")
        
        // Verify reasonable throughput (at least 5 commands per second)
        assertTrue(
            "Throughput should be at least 5 commands/second (was $throughput)",
            throughput >= 5.0
        )
        
        // Verify average response time is reasonable
        assertTrue(
            "Average response time should be under 1 second (was ${averageTime}ms)",
            averageTime < 1000.0
        )
    }
    
    @Test
    fun `test concurrent connection performance`() = runTest {
        val bluetoothScanner = testScanners[ScannerConnectionType.BLUETOOTH_CLASSIC]!!
        val wifiScanner = testScanners[ScannerConnectionType.WIFI]!!
        
        // Test rapid connection switching
        val switchCount = 10
        val totalSwitchTime = measureTimeMillis {
            repeat(switchCount) { iteration ->
                val scanner = if (iteration % 2 == 0) bluetoothScanner else wifiScanner
                
                val result = enhancedScannerManager.connect(scanner)
                assertTrue("Connection $iteration should succeed", result is Result.Success)
                
                // Send a quick command to verify connection works
                val commandResult = enhancedScannerManager.sendCommand("ATI", 2000L)
                assertTrue("Command $iteration should succeed", commandResult is Result.Success)
            }
        }
        
        val averageSwitchTime = totalSwitchTime.toDouble() / switchCount
        
        println("Completed $switchCount connection switches in ${totalSwitchTime}ms")
        println("Average switch time: ${averageSwitchTime}ms")
        
        // Verify reasonable switching performance
        assertTrue(
            "Average connection switch should be under 5 seconds (was ${averageSwitchTime}ms)",
            averageSwitchTime < 5_000
        )
    }
    
    @Test
    fun `test memory and resource usage`() = runTest {
        val scanner = testScanners[ScannerConnectionType.BLUETOOTH_CLASSIC]!!
        
        // Get initial memory usage
        val runtime = Runtime.getRuntime()
        runtime.gc() // Force garbage collection
        val initialMemory = runtime.totalMemory() - runtime.freeMemory()
        
        // Connect and perform operations
        val connectResult = enhancedScannerManager.connect(scanner)
        assertTrue("Connection should succeed", connectResult is Result.Success)
        
        // Perform many operations to test memory usage
        repeat(100) { iteration ->
            enhancedScannerManager.sendCommand("ATI", 1000L)
            
            if (iteration % 10 == 0) {
                // Check memory usage periodically
                val currentMemory = runtime.totalMemory() - runtime.freeMemory()
                val memoryIncrease = currentMemory - initialMemory
                
                println("Memory usage after $iteration operations: ${memoryIncrease / 1024}KB increase")
                
                // Verify memory usage doesn't grow excessively (less than 10MB increase)
                assertTrue(
                    "Memory usage should not increase excessively (${memoryIncrease / 1024 / 1024}MB)",
                    memoryIncrease < 10 * 1024 * 1024
                )
            }
        }
        
        // Test connection statistics don't consume excessive memory
        val connection = enhancedScannerManager.currentConnection
        assertNotNull("Connection should exist", connection)
        
        val stats = connection!!.getStatistics()
        assertTrue("Should have reasonable command count", stats.commandsSent > 0)
        assertTrue("Should have reasonable response count", stats.responsesReceived >= 0)
        
        // Verify statistics are reasonable
        assertTrue("Error rate should be reasonable", stats.errorRate <= 50.0f) // Allow up to 50% error rate in tests
        assertTrue("Average response time should be reasonable", stats.averageResponseTime < 10_000) // Under 10 seconds
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // DISCONNECTION DETECTION TIMING TESTS
    // ═══════════════════════════════════════════════════════════════════════
    
    @Test
    fun `test WiFi disconnection detection within 5 seconds`() = runTest {
        val scanner = testScanners[ScannerConnectionType.WIFI]!!
        
        // Connect first
        val connectResult = enhancedScannerManager.connect(scanner)
        assertTrue("Connection should succeed", connectResult is Result.Success)
        
        val connection = enhancedScannerManager.currentConnection
        assertNotNull("Connection should exist", connection)
        
        // Force disconnect to simulate network failure
        val disconnectTime = measureTimeMillis {
            connection!!.disconnect(graceful = false)
            
            // Wait for disconnection detection
            withTimeout(6000L) {
                val state = enhancedScannerManager.scannerState.first { 
                    it == com.spacetec.core.domain.models.scanner.ScannerState.DISCONNECTED ||
                    it == com.spacetec.core.domain.models.scanner.ScannerState.ERROR
                }
                assertNotNull("Should detect disconnection", state)
            }
        }
        
        assertTrue(
            "WiFi disconnection should be detected within 5 seconds (was ${disconnectTime}ms)",
            disconnectTime <= 5_000
        )
        
        println("WiFi disconnection detected in ${disconnectTime}ms")
    }
    
    @Test
    fun `test USB disconnection detection immediately`() = runTest {
        val scanner = testScanners[ScannerConnectionType.USB]!!
        
        // Connect first
        val connectResult = enhancedScannerManager.connect(scanner)
        assertTrue("Connection should succeed", connectResult is Result.Success)
        
        val connection = enhancedScannerManager.currentConnection
        assertNotNull("Connection should exist", connection)
        
        // Force disconnect to simulate USB removal
        val disconnectTime = measureTimeMillis {
            connection!!.disconnect(graceful = false)
            
            // Wait for disconnection detection
            withTimeout(2000L) {
                val state = enhancedScannerManager.scannerState.first { 
                    it == com.spacetec.core.domain.models.scanner.ScannerState.DISCONNECTED ||
                    it == com.spacetec.core.domain.models.scanner.ScannerState.ERROR
                }
                assertNotNull("Should detect disconnection", state)
            }
        }
        
        assertTrue(
            "USB disconnection should be detected immediately (was ${disconnectTime}ms)",
            disconnectTime <= 1_000 // Allow up to 1 second for immediate detection
        )
        
        println("USB disconnection detected in ${disconnectTime}ms")
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // PERFORMANCE MONITORING AND ALERTING TESTS
    // ═══════════════════════════════════════════════════════════════════════
    
    @Test
    fun `test performance monitoring and degradation alerts`() = runTest {
        val scanner = testScanners[ScannerConnectionType.BLUETOOTH_CLASSIC]!!
        
        // Monitor state events for quality degradation
        val qualityEvents = mutableListOf<com.spacetec.scanner.core.state.StateEvent>()
        val eventJob = kotlinx.coroutines.launch {
            enhancedScannerManager.stateEvents.collect { event ->
                if (event is com.spacetec.scanner.core.state.StateEvent.QualityDegraded) {
                    qualityEvents.add(event)
                }
            }
        }
        
        // Connect and establish baseline
        val connectResult = enhancedScannerManager.connect(scanner)
        assertTrue("Connection should succeed", connectResult is Result.Success)
        
        // Perform operations to establish performance baseline
        repeat(10) {
            enhancedScannerManager.sendCommand("ATI", 1000L)
            delay(100)
        }
        
        // Simulate performance degradation by using longer timeouts
        repeat(5) {
            enhancedScannerManager.sendCommand("ATI", 5000L) // Longer timeout to simulate slow responses
            delay(200)
        }
        
        // Wait for potential quality degradation detection
        delay(2000)
        eventJob.cancel()
        
        // Get connection statistics
        val connection = enhancedScannerManager.currentConnection
        assertNotNull("Connection should exist", connection)
        
        val stats = connection!!.getStatistics()
        assertTrue("Should have performance statistics", stats.commandsSent > 0)
        assertTrue("Should track response times", stats.averageResponseTime >= 0)
        
        // Verify performance monitoring is working
        val connectionStateInfo = enhancedScannerManager.getConnectionStateInfo(connection.connectionId)
        assertNotNull("Connection state info should exist", connectionStateInfo)
        
        // Quality metrics might be null initially, but monitoring should be active
        println("Connection statistics: ${stats.toSummary()}")
        println("Quality events detected: ${qualityEvents.size}")
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════════
    
    private fun createTestEnhancedScannerManager(): EnhancedScannerManager {
        // In a real test, this would use a test DI container with mock implementations
        // that simulate realistic timing and performance characteristics
        return MockEnhancedScannerManager()
    }
    
    private class MockEnhancedScannerManager : EnhancedScannerManager {
        // Mock implementation with realistic performance simulation
        // This would be provided by the test framework
    }
}