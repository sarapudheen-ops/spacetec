/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 *
 * This file is part of the SpaceTec professional automotive diagnostic system.
 */

package com.spacetec.obd.scanner.core

import com.spacetec.core.common.result.Result
import com.spacetec.core.domain.models.scanner.ScannerConnectionType
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*

/**
 * Simple JUnit test to verify basic connection functionality.
 * This serves as a baseline before running property-based tests.
 */
class SimpleConnectionTest {
    
    @Test
    fun testMockConnectionBasicFunctionality() = runBlocking {
        val mockConnection = MockScannerConnection(ScannerConnectionType.BLUETOOTH_CLASSIC)
        
        try {
            // Test initial state
            assertFalse("Connection should start disconnected", mockConnection.isConnected)
            assertTrue("Initial state should be Disconnected", 
                mockConnection.connectionState.value is ConnectionState.Disconnected)
            
            // Test successful connection
            val connectResult = mockConnection.connect("AA:BB:CC:DD:EE:FF")
            assertTrue("Connection should succeed", connectResult is Result.Success)
            assertTrue("Connection should be established", mockConnection.isConnected)
            assertTrue("State should be Connected", 
                mockConnection.connectionState.value is ConnectionState.Connected)
            
            // Test basic communication
            val sendResult = mockConnection.sendCommand("ATZ")
            assertTrue("Send command should succeed", sendResult is Result.Success)
            
            val readResult = mockConnection.read(1000)
            assertTrue("Read should succeed", readResult is Result.Success)
            
            // Test disconnection
            mockConnection.disconnect()
            assertFalse("Connection should be disconnected", mockConnection.isConnected)
            assertTrue("State should be Disconnected", 
                mockConnection.connectionState.value is ConnectionState.Disconnected)
            
        } finally {
            mockConnection.release()
        }
    }
    
    @Test
    fun testConnectionFailure() = runBlocking {
        val mockConnection = MockScannerConnection(ScannerConnectionType.WIFI)
        
        try {
            // Configure to fail connection
            mockConnection.shouldFailConnection = true
            
            val connectResult = mockConnection.connect("192.168.1.100")
            assertTrue("Connection should fail", connectResult is Result.Error)
            assertFalse("Connection should not be established", mockConnection.isConnected)
            assertTrue("State should be Error", 
                mockConnection.connectionState.value is ConnectionState.Error)
            
        } finally {
            mockConnection.release()
        }
    }
    
    @Test
    fun testConnectionStatistics() = runBlocking {
        val mockConnection = MockScannerConnection(ScannerConnectionType.USB)
        
        try {
            mockConnection.connect("USB001")
            
            // Send some data
            mockConnection.sendCommand("AT")
            mockConnection.read(1000)
            
            val stats = mockConnection.getStatistics()
            assertTrue("Should have sent data", stats.bytesSent > 0)
            assertTrue("Should have received data", stats.bytesReceived > 0)
            assertTrue("Should have sent commands", stats.commandsSent > 0)
            assertTrue("Should have received responses", stats.responsesReceived > 0)
            
        } finally {
            mockConnection.release()
        }
    }
}