/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 */

package com.spacetec.obd.scanner.j2534

import com.spacetec.core.common.result.Result
import com.spacetec.j2534.constants.J2534Constants
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for J2534Connection.
 */
class J2534ConnectionTest {
    
    @Test
    fun testConnectionCreation() {
        val connection = J2534Connection.create()
        assertNotNull(connection)
        assertEquals("J2534", connection.connectionType.name)
        assertFalse(connection.isConnected)
    }
    
    @Test
    fun testProtocolConstants() {
        assertEquals(0x00000005, J2534Constants.CAN)
        assertEquals(0x00000006, J2534Constants.ISO15765)
        assertEquals(0x00000004, J2534Constants.ISO14230)
        assertEquals(0x00000003, J2534Constants.ISO9141)
    }
    
    @Test
    fun testErrorCodeToString() {
        assertEquals("No Error", J2534Constants.errorCodeToString(J2534Constants.STATUS_NOERROR))
        assertEquals("Timeout", J2534Constants.errorCodeToString(J2534Constants.ERR_TIMEOUT))
        assertEquals("Device Not Connected", J2534Constants.errorCodeToString(J2534Constants.ERR_DEVICE_NOT_CONNECTED))
    }
    
    @Test
    fun testProtocolIdToString() {
        assertEquals("CAN", J2534Constants.protocolIdToString(J2534Constants.CAN))
        assertEquals("ISO 15765 (CAN with TP)", J2534Constants.protocolIdToString(J2534Constants.ISO15765))
        assertEquals("ISO 14230 (KWP2000)", J2534Constants.protocolIdToString(J2534Constants.ISO14230))
    }
    
    @Test
    fun testDefaultDataRates() {
        assertEquals(500000, J2534Constants.getDefaultDataRate(J2534Constants.CAN))
        assertEquals(500000, J2534Constants.getDefaultDataRate(J2534Constants.ISO15765))
        assertEquals(10400, J2534Constants.getDefaultDataRate(J2534Constants.ISO14230))
        assertEquals(10400, J2534Constants.getDefaultDataRate(J2534Constants.ISO9141))
    }
    
    @Test
    fun testConnectionWithoutDevice() = runTest {
        val connection = J2534Connection.create()
        
        // Should fail to connect without a real device
        val result = connection.connect("NonExistentDevice")
        assertTrue(result is Result.Error)
        assertFalse(connection.isConnected)
    }
    
    @Test
    fun testHexMessageCreation() {
        val connection = J2534Connection.create()
        val message = connection.createMessageFromHex("7E0 02 01 00")
        
        assertNotNull(message)
        assertEquals(J2534Constants.ISO15765, message.protocolId)
        assertTrue(message.validate())
    }
    
    @Test
    fun testDiscoverDevices() = runTest {
        val result = J2534Connection.discoverDevices()
        
        // Should return a list (may be empty if no devices installed)
        assertTrue(result is Result.Success)
        val devices = (result as Result.Success).data
        assertNotNull(devices)
        // In test environment, we expect the mock device list
        assertTrue(devices.isNotEmpty())
    }
}