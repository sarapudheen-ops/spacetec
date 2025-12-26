/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 */

package com.spacetec.j2534

import com.spacetec.j2534.constants.J2534Constants
import org.junit.Test
import org.junit.Assert.*

/**
 * Integration tests for J2534 components.
 */
class J2534IntegrationTest {
    
    @Test
    fun testJ2534MessageCreation() {
        val data = byteArrayOf(0x7E, 0x0, 0x02, 0x01, 0x00)
        val message = J2534Message.createCanMessage(data, use29Bit = false)
        
        assertNotNull(message)
        assertEquals(J2534Constants.CAN, message.protocolId)
        assertEquals(data.size, message.dataSize)
        assertArrayEquals(data, message.messageData)
        assertTrue(message.validate())
        assertFalse(message.is29BitCan)
    }
    
    @Test
    fun testJ2534MessageFilter() {
        val mask = byteArrayOf(0xFF.toByte(), 0xF0.toByte())
        val pattern = byteArrayOf(0x7E.toByte(), 0x00.toByte())
        
        val filter = J2534MessageFilter.createPassFilter(J2534Constants.CAN, mask, pattern)
        
        assertNotNull(filter)
        assertTrue(filter.isPassFilter)
        assertFalse(filter.isBlockFilter)
        assertFalse(filter.isFlowControlFilter)
        assertTrue(filter.validate())
    }
    
    @Test
    fun testJ2534Config() {
        val config = J2534Config.dataRate(500000)
        
        assertEquals(J2534Constants.DATA_RATE, config.parameterId)
        assertEquals(500000, config.value)
        assertEquals("DATA_RATE", config.parameterName)
        assertEquals("DATA_RATE = 500000", config.toString())
    }
    
    @Test
    fun testJ2534DefaultConfigs() {
        val configs = J2534Config.defaultsForProtocol(J2534Constants.ISO15765)
        
        assertNotNull(configs)
        assertTrue(configs.isNotEmpty())
        
        // Should contain data rate
        val dataRateConfig = configs.find { it.parameterId == J2534Constants.DATA_RATE }
        assertNotNull(dataRateConfig)
        assertEquals(500000, dataRateConfig?.value)
        
        // Should contain ISO 15765 specific configs
        val bsConfig = configs.find { it.parameterId == J2534Constants.ISO15765_BS }
        assertNotNull(bsConfig)
    }
    
    @Test
    fun testJ2534Interface() {
        val interface = J2534Interface.create()
        assertNotNull(interface)
        assertFalse(interface.isInitialized())
    }
    
    @Test
    fun testJ2534Device() {
        val interface = J2534Interface.create()
        val device = J2534Device.create(interface, "TestDevice")
        
        assertNotNull(device)
        assertEquals("TestDevice", device.deviceName)
        assertFalse(device.isConnected())
    }
    
    @Test
    fun testHexMessageParsing() {
        val hexData = "7E0 02 01 00"
        val data = J2534Message.parseHexData(hexData)
        
        assertNotNull(data)
        assertEquals(4, data.size)
        assertEquals(0x7E.toByte(), data[0])
        assertEquals(0x00.toByte(), data[1])
        assertEquals(0x02.toByte(), data[2])
        assertEquals(0x01.toByte(), data[3])
    }
    
    @Test
    fun testMessageToHexString() {
        val data = byteArrayOf(0x7E.toByte(), 0x00.toByte(), 0x02.toByte(), 0x01.toByte(), 0x00.toByte())
        val message = J2534Message.createIso15765Message(data)
        
        val hexString = message.toHexString()
        assertNotNull(hexString)
        assertTrue(hexString.contains("ISO 15765"))
        assertTrue(hexString.contains("7E 00 02 01 00"))
    }
}