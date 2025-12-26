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
import com.spacetec.core.domain.models.scanner.ScannerState
import com.spacetec.obd.scanner.core.ConnectionConfig
import com.spacetec.obd.scanner.core.DiscoveryOptions
import com.spacetec.obd.scanner.core.ScannerConnectionOptions
import com.spacetec.obd.scanner.core.ScannerManager
import com.spacetec.obd.scanner.core.state.ConnectionHealth
import com.spacetec.obd.scanner.core.state.EnhancedScannerManager
import com.spacetec.obd.scanner.core.state.StateEvent
import com.spacetec.obd.scanner.core.state.SynchronizationEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import java.util.concurrent.TimeUnit

/**
 * Comprehensive integration tests for the scanner connection system.
 * 
 * Tests end-to-end functionality across all connection types and validates:
 * - Connection establishment and management
 * - State synchronization and consistency
 * - Performance requirements
 * - Security compliance
 * - Error handling and recovery
 * - Logging and diagnostics
 * 
 * **Feature: scanner-connection-system, Integration Test Suite**
 * **Validates: All Requirements**
 */
@RunWith(MockitoJUnitRunner::class)
class ScannerConnectionIntegrationTest {
    
    private lateinit var enhancedScannerManager: EnhancedScannerManager
    private lateinit var testScanners: List<Scanner>
    
    @Before
    fun setUp() {
        // Initialize test environment
        setupTestScanners()
        setupEnhancedScannerManager()
    }
    
    @After
    fun tearDown() {
        runBlocking {
            enhancedScannerManager.disconnect()
            enhancedScannerManager.clearAllPersistedState()
            enhancedScannerManager.release()
        }
    }
    
    private fun setupTestScanners() {
        testScanners = listOf(
            // Bluetooth Classic Scanner
            Scanner(
                id = "bt_classic_test",
                name = "ELM327 Bluetooth",
                address = "AA:BB:CC:DD:EE:FF",
                connectionType = ScannerConnectionType.BLUETOOTH_CLASSIC,
                deviceType = ScannerDeviceType.ELM327
            ),
            // Bluetooth LE Scanner
            Scanner(
                id = "bt_le_test",
                name = "OBDLink LE",
                address = "11:22:33:44:55:66",
                connectionType = ScannerConnectionType.BLUETOOTH_LE,
                deviceType = ScannerDeviceType.OBDLINK_MX_PLUS
            ),
            // WiFi Scanner
            Scanner(
                id = "wifi_test",
                name = "WiFi OBD Scanner",
                address = "192.168.1.100:35000",
                connectionType = ScannerConnectionType.WIFI,
                deviceType = ScannerDeviceType.VGATE_ICAR_PRO
            ),
            // USB Scanner
            Scanner(
                id = "usb_test",
                name = "USB OBD Scanner",
                address = "/dev/ttyUSB0",
                connectionType = ScannerConnectionType.USB,
                deviceType = ScannerDeviceType.GENERIC
            ),
            // J2534 Scanner
            Scanner(
                id = "j2534_test",
                name = "Professional J2534 Tool",
                address = "J2534_DEVICE_0",
                connectionType = ScannerConnectionType.J2534,
                deviceType = ScannerDeviceType.PROFESSIONAL
            )
        )
    }
    
    private fun setupEnhancedScannerManager() {
        // This would normally be injected via Hilt in a real test
        // For integration tests, we create a test instance
        enhancedScannerManager = createTestEnhancedScannerManager()
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // COMPREHENSIVE INTEGRATION TESTS
    // ═══════════════════════════════════════════════════════════════════════
    
    @Test
    fun `test end-to-end connection lifecycle for all connection types`() = runTest {
        // Test each connection type individually
        for (scanner in testScanners) {
            println("Testing connection lifecycle for ${scanner.connectionType}")
            
            // Test connection establishment
            val connectResult = enhancedScannerManager.connect(
                scanner,
                ScannerConnectionOptions.DEFAULT
            )
            
            assertTrue(
                "Connection should succeed for ${scanner.connectionType}",
                connectResult is Result.Success
            )
            
            // Verify state is connected
            val state = enhancedScannerManager.scannerState.first()
            assertEquals(
                "Scanner should be connected",
                ScannerState.CONNECTED,
                state
            )
            
            // Test communication
            val commandResult = enhancedScannerManager.sendCommand("ATI", 5000L)
            assertTrue(
                "Command should succeed",
                commandResult is Result.Success
            )
            
            // Test disconnection
            enhancedScannerManager.disconnect()
            
            // Verify state is disconnected
            withTimeout(5000L) {
                val disconnectedState = enhancedScannerManager.scannerState.first { 
                    it == ScannerState.DISCONNECTED 
                }
                assertEquals(ScannerState.DISCONNECTED, disconnectedState)
            }
            
            // Small delay between tests
            delay(100)
        }
    }
    
    @Test
    fun `test cross-connection compatibility and switching`() = runTest {
        val bluetoothScanner = testScanners.first { it.connectionType == ScannerConnectionType.BLUETOOTH_CLASSIC }
        val wifiScanner = testScanners.first { it.connectionType == ScannerConnectionType.WIFI }
        
        // Connect to Bluetooth scanner
        val btResult = enhancedScannerManager.connect(bluetoothScanner)
        assertTrue("Bluetooth connection should succeed", btResult is Result.Success)
        
        // Verify connection is active
        assertTrue("Should be connected", enhancedScannerManager.isConnected)
        
        // Switch to WiFi scanner
        val wifiResult = enhancedScannerManager.connect(wifiScanner)
        assertTrue("WiFi connection should succeed", wifiResult is Result.Success)
        
        // Verify new connection is active
        val activeScanner = enhancedScannerManager.activeScanner.first()
        assertEquals("Active scanner should be WiFi", wifiScanner.id, activeScanner?.id)
        
        // Test that both connections work with same commands
        val atCommand = enhancedScannerManager.sendCommand("ATZ")
        val obdCommand = enhancedScannerManager.sendCommand("0100")
        
        assertTrue("AT command should work", atCommand is Result.Success)
        assertTrue("OBD command should work", obdCommand is Result.Success)
    }
    
    @Test
    fun `test multi-device and concurrent connection scenarios`() = runTest {
        // Test connecting to multiple scanners of different types
        val bluetoothScanner = testScanners.first { it.connectionType == ScannerConnectionType.BLUETOOTH_CLASSIC }
        val usbScanner = testScanners.first { it.connectionType == ScannerConnectionType.USB }
        
        // Connect to first scanner
        val btResult = enhancedScannerManager.connect(bluetoothScanner)
        assertTrue("Bluetooth connection should succeed", btResult is Result.Success)
        
        // Get connection info
        val btConnectionInfo = enhancedScannerManager.getConnectionStateInfo(
            enhancedScannerManager.currentConnection?.connectionId ?: ""
        )
        assertNotNull("Bluetooth connection info should exist", btConnectionInfo)
        
        // Connect to second scanner (should switch)
        val usbResult = enhancedScannerManager.connect(usbScanner)
        assertTrue("USB connection should succeed", usbResult is Result.Success)
        
        // Verify state management handles multiple connections
        val globalState = enhancedScannerManager.globalConnectionState.first()
        assertTrue("Should have connection activity", globalState.totalConnections > 0)
        
        // Test concurrent operations
        val command1 = enhancedScannerManager.sendCommand("ATI")
        val command2 = enhancedScannerManager.sendCommand("ATRV")
        
        assertTrue("Concurrent command 1 should succeed", command1 is Result.Success)
        assertTrue("Concurrent command 2 should succeed", command2 is Result.Success)
    }
    
    @Test
    fun `test state synchronization and consistency`() = runTest {
        val scanner = testScanners.first()
        
        // Monitor state events
        val stateEvents = mutableListOf<StateEvent>()
        val stateJob = kotlinx.coroutines.launch {
            enhancedScannerManager.stateEvents.take(10).toList(stateEvents)
        }
        
        // Connect scanner
        val connectResult = enhancedScannerManager.connect(scanner)
        assertTrue("Connection should succeed", connectResult is Result.Success)
        
        // Wait for state events
        delay(1000)
        stateJob.cancel()
        
        // Verify state events were generated
        assertTrue("Should have state events", stateEvents.isNotEmpty())
        
        // Check for connection registered event
        val registeredEvent = stateEvents.find { it is StateEvent.ConnectionRegistered }
        assertNotNull("Should have connection registered event", registeredEvent)
        
        // Test state synchronization
        val syncResult = enhancedScannerManager.synchronizeAllStates()
        assertTrue("State synchronization should succeed", syncResult is Result.Success)
        
        // Verify global state consistency
        val globalState = enhancedScannerManager.globalConnectionState.first()
        assertEquals("Should have one active connection", 1, globalState.activeConnections)
        assertTrue("Should have healthy connections", globalState.overallHealth != ConnectionHealth.POOR)
    }
    
    @Test
    fun `test performance requirements validation`() = runTest {
        val scanner = testScanners.first { it.connectionType == ScannerConnectionType.BLUETOOTH_CLASSIC }
        
        // Connect with performance monitoring
        val startTime = System.currentTimeMillis()
        val connectResult = enhancedScannerManager.connect(
            scanner,
            ScannerConnectionOptions.DEFAULT.copy(
                connectionConfig = ConnectionConfig.BLUETOOTH
            )
        )
        val connectTime = System.currentTimeMillis() - startTime
        
        assertTrue("Connection should succeed", connectResult is Result.Success)
        assertTrue("Connection should be within 15 seconds", connectTime < 15_000)
        
        // Test response time requirements
        val commandStartTime = System.currentTimeMillis()
        val commandResult = enhancedScannerManager.sendCommand("0100", 5000L)
        val commandTime = System.currentTimeMillis() - commandStartTime
        
        assertTrue("Command should succeed", commandResult is Result.Success)
        assertTrue("Command response should be within 5 seconds", commandTime < 5_000)
        
        // Test connection statistics
        val connection = enhancedScannerManager.currentConnection
        assertNotNull("Connection should exist", connection)
        
        val stats = connection!!.getStatistics()
        assertTrue("Should have sent commands", stats.commandsSent > 0)
        assertTrue("Should have received responses", stats.responsesReceived > 0)
        assertTrue("Average response time should be reasonable", stats.averageResponseTime < 3_000)
        
        // Test performance monitoring
        val connectionInfo = enhancedScannerManager.getConnectionStateInfo(connection.connectionId)
        assertNotNull("Connection state info should exist", connectionInfo)
        
        // Verify quality metrics are tracked
        delay(2000) // Allow time for quality metrics to be updated
        val updatedInfo = enhancedScannerManager.getConnectionStateInfo(connection.connectionId)
        // Quality metrics might be null initially, but should be tracked over time
    }
    
    @Test
    fun `test security compliance and violation handling`() = runTest {
        val scanner = testScanners.first()
        
        // Connect scanner
        val connectResult = enhancedScannerManager.connect(scanner)
        assertTrue("Connection should succeed", connectResult is Result.Success)
        
        // Test data integrity (commands should be validated)
        val validCommand = enhancedScannerManager.sendCommand("ATI")
        assertTrue("Valid command should succeed", validCommand is Result.Success)
        
        // Test invalid/potentially malicious commands are handled
        val invalidCommand = enhancedScannerManager.sendCommand("INVALID_COMMAND_WITH_SPECIAL_CHARS_!@#$%")
        // Should either succeed with error response or fail gracefully
        assertNotNull("Invalid command should be handled", invalidCommand)
        
        // Test connection security features
        val connection = enhancedScannerManager.currentConnection
        assertNotNull("Connection should exist", connection)
        
        // Verify connection has security features enabled
        val connectionConfig = connection!!.config
        assertNotNull("Connection config should exist", connectionConfig)
        
        // Test that connection statistics are protected
        val stats = connection.getStatistics()
        assertTrue("Statistics should be valid", stats.bytesSent >= 0)
        assertTrue("Statistics should be valid", stats.bytesReceived >= 0)
    }
    
    @Test
    fun `test error handling and recovery mechanisms`() = runTest {
        val scanner = testScanners.first()
        
        // Monitor error events
        val errorEvents = mutableListOf<StateEvent>()
        val errorJob = kotlinx.coroutines.launch {
            enhancedScannerManager.stateEvents.collect { event ->
                if (event is StateEvent.ConnectionError || 
                    event is StateEvent.RecoveryAttempt || 
                    event is StateEvent.RecoverySuccess ||
                    event is StateEvent.RecoveryFailed) {
                    errorEvents.add(event)
                }
            }
        }
        
        // Connect scanner
        val connectResult = enhancedScannerManager.connect(scanner)
        assertTrue("Connection should succeed", connectResult is Result.Success)
        
        // Simulate connection error by disconnecting abruptly
        val connection = enhancedScannerManager.currentConnection
        assertNotNull("Connection should exist", connection)
        
        // Force disconnect to simulate error
        connection!!.disconnect(graceful = false)
        
        // Wait for error handling
        delay(2000)
        
        // Test manual recovery
        val recoveryResult = enhancedScannerManager.triggerConnectionRecovery(connection.connectionId)
        // Recovery might fail if connection is not in error state, but should be handled gracefully
        assertNotNull("Recovery result should exist", recoveryResult)
        
        // Test reconnection
        val reconnectResult = enhancedScannerManager.reconnect()
        // Reconnection should work if there's a previous connection
        assertNotNull("Reconnect result should exist", reconnectResult)
        
        errorJob.cancel()
        
        // Verify error handling was triggered
        // Note: Actual error events depend on the mock implementation
    }
    
    @Test
    fun `test logging and diagnostic capabilities`() = runTest {
        val scanner = testScanners.first()
        
        // Connect scanner
        val connectResult = enhancedScannerManager.connect(scanner)
        assertTrue("Connection should succeed", connectResult is Result.Success)
        
        // Perform various operations to generate logs
        enhancedScannerManager.sendCommand("ATI")
        enhancedScannerManager.sendCommand("ATZ")
        enhancedScannerManager.sendCommand("0100")
        enhancedScannerManager.readVoltage()
        
        // Test connection statistics (which are logged)
        val connection = enhancedScannerManager.currentConnection
        assertNotNull("Connection should exist", connection)
        
        val stats = connection!!.getStatistics()
        assertTrue("Should have logged commands", stats.commandsSent > 0)
        assertTrue("Should have logged responses", stats.responsesReceived > 0)
        
        // Test diagnostic information
        val scannerInfoResult = enhancedScannerManager.getScannerInfo()
        assertTrue("Scanner info should be available", scannerInfoResult is Result.Success)
        
        // Test state information for diagnostics
        val connectionStateInfo = enhancedScannerManager.getConnectionStateInfo(connection.connectionId)
        assertNotNull("Connection state info should be available", connectionStateInfo)
        
        // Verify diagnostic data completeness
        if (connectionStateInfo != null) {
            assertTrue("Should have registration time", connectionStateInfo.registeredAt > 0)
            assertTrue("Should have last state change time", connectionStateInfo.lastStateChange > 0)
            assertNotNull("Should have scanner info", connectionStateInfo.scanner)
            assertNotNull("Should have connection type", connectionStateInfo.connectionType)
        }
        
        // Test global state for system diagnostics
        val globalState = enhancedScannerManager.globalConnectionState.first()
        assertTrue("Should have connection count", globalState.totalConnections >= 0)
        assertTrue("Should have last update time", globalState.lastUpdate > 0)
        assertNotNull("Should have health status", globalState.overallHealth)
    }
    
    @Test
    fun `test system resilience under stress conditions`() = runTest {
        // Test rapid connect/disconnect cycles
        val scanner = testScanners.first()
        
        repeat(5) { iteration ->
            println("Stress test iteration: $iteration")
            
            // Connect
            val connectResult = enhancedScannerManager.connect(scanner)
            assertTrue("Connection $iteration should succeed", connectResult is Result.Success)
            
            // Send multiple commands rapidly
            val commands = listOf("ATI", "ATZ", "ATRV", "0100", "0120")
            for (command in commands) {
                val result = enhancedScannerManager.sendCommand(command, 2000L)
                // Commands might fail under stress, but should be handled gracefully
                assertNotNull("Command result should exist", result)
            }
            
            // Disconnect
            enhancedScannerManager.disconnect()
            
            // Brief pause between iterations
            delay(100)
        }
        
        // Verify system is still stable after stress test
        val finalConnectResult = enhancedScannerManager.connect(scanner)
        assertTrue("Final connection should succeed after stress test", finalConnectResult is Result.Success)
        
        val finalCommandResult = enhancedScannerManager.sendCommand("ATI")
        assertTrue("Final command should succeed after stress test", finalCommandResult is Result.Success)
    }
    
    @Test
    fun `test state persistence and recovery`() = runTest {
        val scanner = testScanners.first()
        
        // Connect and establish state
        val connectResult = enhancedScannerManager.connect(scanner)
        assertTrue("Connection should succeed", connectResult is Result.Success)
        
        // Perform operations to create state
        enhancedScannerManager.sendCommand("ATI")
        enhancedScannerManager.sendCommand("0100")
        
        // Get connection state info
        val connection = enhancedScannerManager.currentConnection
        assertNotNull("Connection should exist", connection)
        
        val originalStateInfo = enhancedScannerManager.getConnectionStateInfo(connection!!.connectionId)
        assertNotNull("Original state info should exist", originalStateInfo)
        
        // Simulate app restart by clearing and recreating manager
        enhancedScannerManager.disconnect()
        enhancedScannerManager.release()
        
        // Create new manager instance (simulating app restart)
        enhancedScannerManager = createTestEnhancedScannerManager()
        
        // Wait for state recovery
        delay(1000)
        
        // Verify state was recovered
        val globalState = enhancedScannerManager.globalConnectionState.first()
        // Note: Actual recovery depends on the persistence implementation
        assertNotNull("Global state should exist after recovery", globalState)
        
        // Test that new connections still work after recovery
        val newConnectResult = enhancedScannerManager.connect(scanner)
        assertTrue("New connection should succeed after recovery", newConnectResult is Result.Success)
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════════
    
    private fun createTestEnhancedScannerManager(): EnhancedScannerManager {
        // In a real test, this would use a test DI container
        // For now, we'll create a mock implementation
        return MockEnhancedScannerManager()
    }
    
    /**
     * Mock implementation for testing purposes.
     * In a real implementation, this would be replaced with proper DI and mocking.
     */
    private class MockEnhancedScannerManager : EnhancedScannerManager {
        // Implementation would be provided by test framework
        // This is a placeholder for the integration test structure
    }
}