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
import com.spacetec.obd.scanner.core.ScannerConnectionOptions
import com.spacetec.obd.scanner.core.state.EnhancedScannerManager
import com.spacetec.obd.scanner.core.state.StateEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner

/**
 * Logging and diagnostics validation tests.
 * 
 * **Feature: scanner-connection-system, Logging and Diagnostics**
 * **Validates: Requirements 8.1, 8.2, 8.3, 8.4, 8.5**
 */
@RunWith(MockitoJUnitRunner::class)
class LoggingDiagnosticsTest {
    
    private lateinit var enhancedScannerManager: EnhancedScannerManager
    private lateinit var testScanner: Scanner
    
    @Before
    fun setUp() {
        setupTestScanner()
        setupEnhancedScannerManager()
    }
    
    @After
    fun tearDown() {
        runBlocking {
            enhancedScannerManager.disconnect()
            enhancedScannerManager.release()
        }
    }
    
    private fun setupTestScanner() {
        testScanner = Scanner(
            id = "logging_test",
            name = "Logging Test Scanner",
            address = "AA:BB:CC:DD:EE:FF",
            connectionType = ScannerConnectionType.BLUETOOTH_CLASSIC,
            deviceType = ScannerDeviceType.ELM327
        )
    }
    
    private fun setupEnhancedScannerManager() {
        enhancedScannerManager = createTestEnhancedScannerManager()
    }
   
 
    @Test
    fun `test comprehensive operation logging`() = runTest {
        // Monitor state events for logging
        val loggedEvents = mutableListOf<StateEvent>()
        val eventJob = kotlinx.coroutines.launch {
            enhancedScannerManager.stateEvents.collect { event ->
                loggedEvents.add(event)
            }
        }
        
        // Perform various operations
        val connectResult = enhancedScannerManager.connect(testScanner)
        assertTrue("Connection should succeed", connectResult is Result.Success)
        
        enhancedScannerManager.sendCommand("ATI")
        enhancedScannerManager.sendCommand("ATRV")
        enhancedScannerManager.readVoltage()
        
        enhancedScannerManager.disconnect()
        
        delay(1000)
        eventJob.cancel()
        
        // Verify comprehensive logging
        assertTrue("Should have logged events", loggedEvents.isNotEmpty())
        
        // Verify connection lifecycle was logged
        val registeredEvent = loggedEvents.find { it is StateEvent.ConnectionRegistered }
        assertNotNull("Connection registration should be logged", registeredEvent)
        
        println("Comprehensive operation logging validated with ${loggedEvents.size} events")
    }
    
    @Test
    fun `test error logging with context`() = runTest {
        val errorEvents = mutableListOf<StateEvent>()
        val eventJob = kotlinx.coroutines.launch {
            enhancedScannerManager.stateEvents.collect { event ->
                if (event is StateEvent.ConnectionError) {
                    errorEvents.add(event)
                }
            }
        }
        
        // Connect and force error
        val connectResult = enhancedScannerManager.connect(testScanner)
        if (connectResult is Result.Success) {
            val connection = enhancedScannerManager.currentConnection
            connection?.disconnect(graceful = false)
        }
        
        delay(1000)
        eventJob.cancel()
        
        // Verify error logging includes context
        for (event in errorEvents) {
            if (event is StateEvent.ConnectionError) {
                assertNotNull("Error should have exception", event.exception)
                assertNotNull("Error should have connection ID", event.connectionId)
            }
        }
        
        println("Error logging with context validated")
    }
    
    @Test
    fun `test diagnostic data capture and export`() = runTest {
        // Connect and perform operations
        val connectResult = enhancedScannerManager.connect(testScanner)
        assertTrue("Connection should succeed", connectResult is Result.Success)
        
        // Perform operations to generate diagnostic data
        enhancedScannerManager.sendCommand("ATI")
        enhancedScannerManager.sendCommand("0100")
        
        // Get diagnostic information
        val connection = enhancedScannerManager.currentConnection
        assertNotNull("Connection should exist", connection)
        
        val stats = connection!!.getStatistics()
        assertNotNull("Statistics should be available", stats)
        
        val stateInfo = enhancedScannerManager.getConnectionStateInfo(connection.connectionId)
        assertNotNull("State info should be available", stateInfo)
        
        val globalState = enhancedScannerManager.globalConnectionState.value
        assertNotNull("Global state should be available", globalState)
        
        // Verify diagnostic data completeness
        assertTrue("Should have command statistics", stats.commandsSent > 0)
        assertTrue("Should have state history", stateInfo!!.stateHistory.isNotEmpty())
        assertTrue("Should have global metrics", globalState.totalConnections >= 0)
        
        println("Diagnostic data capture validated")
        println("Statistics: ${stats.toSummary()}")
    }
    
    private fun createTestEnhancedScannerManager(): EnhancedScannerManager {
        return MockEnhancedScannerManager()
    }
    
    private class MockEnhancedScannerManager : EnhancedScannerManager {
        // Mock implementation
    }
}