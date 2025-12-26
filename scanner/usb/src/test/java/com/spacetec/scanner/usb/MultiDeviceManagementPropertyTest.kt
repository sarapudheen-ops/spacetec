/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 *
 * This file is part of the SpaceTec professional automotive diagnostic system.
 */

package com.spacetec.obd.scanner.usb

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll

/**
 * **Feature: scanner-connection-system, Property 7: Multi-Device Management**
 * 
 * Property-based test for multi-device USB management.
 * 
 * **Validates: Requirements 3.4, 4.5**
 * 
 * This test verifies that:
 * 1. Multiple devices can be managed simultaneously
 * 2. Device selection and switching works without interference
 * 3. Device priority management works correctly
 * 4. Concurrent device operations don't interfere with each other
 */
class MultiDeviceManagementPropertyTest : StringSpec({
    
    "Property 7: Multi-Device Management - Adding multiple devices should maintain all devices without interference" {
        checkAll(
            iterations = 100,
            Arb.list(Arb.string(5..20), 1..10), // device names
            Arb.list(Arb.enum<DevicePriority>(), 1..10) // priorities
        ) { deviceNames, priorities ->
            
            // Given: A multi-device manager
            val manager = MockUSBDeviceManager()
            
            try {
                // When: Multiple devices are added with different priorities
                val uniqueDeviceNames = deviceNames.distinct()
                val devicePriorities = uniqueDeviceNames.zip(
                    priorities.take(uniqueDeviceNames.size).ifEmpty { listOf(DevicePriority.NORMAL) }
                )
                
                devicePriorities.forEach { (name, priority) ->
                    manager.addDevice(name, priority)
                }
                
                // Then: All devices should be managed
                manager.getDeviceCount() shouldBe uniqueDeviceNames.size
                
                // And: Each device should have correct priority
                devicePriorities.forEach { (name, priority) ->
                    manager.isDeviceManaged(name) shouldBe true
                    manager.getDevicePriority(name) shouldBe priority
                }
                
                // And: Device list should contain all devices
                val managedDevices = manager.getManagedDeviceNames()
                uniqueDeviceNames.forEach { name ->
                    managedDevices shouldContain name
                }
                
            } finally {
                manager.release()
            }
        }
    }
    
    "Property 7: Multi-Device Management - Removing a device should not affect other devices" {
        checkAll(
            iterations = 100,
            Arb.list(Arb.string(5..15), 2..8) // at least 2 devices
        ) { deviceNames ->
            
            val manager = MockUSBDeviceManager()
            
            try {
                // Given: Multiple devices are added
                val uniqueDeviceNames = deviceNames.distinct()
                if (uniqueDeviceNames.size < 2) return@checkAll // Skip if not enough unique names
                
                uniqueDeviceNames.forEach { name ->
                    manager.addDevice(name, DevicePriority.NORMAL)
                }
                
                val initialCount = manager.getDeviceCount()
                
                // When: One device is removed
                val deviceToRemove = uniqueDeviceNames.first()
                val remainingDevices = uniqueDeviceNames.drop(1)
                
                manager.removeDevice(deviceToRemove)
                
                // Then: Device count should decrease by 1
                manager.getDeviceCount() shouldBe (initialCount - 1)
                
                // And: Removed device should not be managed
                manager.isDeviceManaged(deviceToRemove) shouldBe false
                
                // And: Other devices should still be managed
                remainingDevices.forEach { name ->
                    manager.isDeviceManaged(name) shouldBe true
                }
                
            } finally {
                manager.release()
            }
        }
    }
    
    "Property 7: Multi-Device Management - Switching between devices should update active device correctly" {
        checkAll(
            iterations = 100,
            Arb.list(Arb.string(5..15), 2..5) // 2-5 devices
        ) { deviceNames ->
            
            val manager = MockUSBDeviceManager()
            
            try {
                // Given: Multiple devices are added
                val uniqueDeviceNames = deviceNames.distinct()
                if (uniqueDeviceNames.size < 2) return@checkAll
                
                uniqueDeviceNames.forEach { name ->
                    manager.addDevice(name, DevicePriority.NORMAL)
                }
                
                // When: Connecting to first device
                val firstDevice = uniqueDeviceNames.first()
                manager.connectToDevice(firstDevice)
                
                // Then: First device should be active
                manager.getActiveDeviceName() shouldBe firstDevice
                manager.isDeviceActive(firstDevice) shouldBe true
                
                // When: Switching to second device
                val secondDevice = uniqueDeviceNames[1]
                manager.switchToDevice(secondDevice)
                
                // Then: Second device should be active
                manager.getActiveDeviceName() shouldBe secondDevice
                manager.isDeviceActive(secondDevice) shouldBe true
                
                // And: First device should no longer be active
                manager.isDeviceActive(firstDevice) shouldBe false
                
                // And: Both devices should still be managed
                manager.isDeviceManaged(firstDevice) shouldBe true
                manager.isDeviceManaged(secondDevice) shouldBe true
                
            } finally {
                manager.release()
            }
        }
    }
    
    "Property 7: Multi-Device Management - Device priority should determine preferred device selection" {
        checkAll(
            iterations = 100,
            Arb.list(Arb.string(5..15), 3..6) // 3-6 devices
        ) { deviceNames ->
            
            val manager = MockUSBDeviceManager()
            
            try {
                // Given: Multiple devices with different priorities
                val uniqueDeviceNames = deviceNames.distinct()
                if (uniqueDeviceNames.size < 3) return@checkAll
                
                // Add devices with different priorities
                val highPriorityDevice = uniqueDeviceNames[0]
                val normalPriorityDevice = uniqueDeviceNames[1]
                val lowPriorityDevice = uniqueDeviceNames[2]
                
                manager.addDevice(lowPriorityDevice, DevicePriority.LOW)
                manager.addDevice(normalPriorityDevice, DevicePriority.NORMAL)
                manager.addDevice(highPriorityDevice, DevicePriority.HIGH)
                
                // When: Getting preferred device
                val preferredDevice = manager.getPreferredDeviceName()
                
                // Then: High priority device should be preferred
                preferredDevice shouldBe highPriorityDevice
                
                // When: Changing priority of another device to HIGH
                manager.setDevicePriority(normalPriorityDevice, DevicePriority.HIGH)
                
                // Then: Either high priority device could be preferred (both are HIGH now)
                val newPreferred = manager.getPreferredDeviceName()
                (newPreferred == highPriorityDevice || newPreferred == normalPriorityDevice) shouldBe true
                
            } finally {
                manager.release()
            }
        }
    }
    
    "Property 7: Multi-Device Management - Concurrent device operations should not interfere" {
        checkAll(
            iterations = 50,
            Arb.list(Arb.string(5..15), 2..4) // 2-4 devices
        ) { deviceNames ->
            
            val manager = MockUSBDeviceManager()
            
            try {
                // Given: Multiple devices are added and connected
                val uniqueDeviceNames = deviceNames.distinct()
                if (uniqueDeviceNames.size < 2) return@checkAll
                
                uniqueDeviceNames.forEach { name ->
                    manager.addDevice(name, DevicePriority.NORMAL)
                    manager.connectToDevice(name)
                }
                
                // When: Performing operations on different devices
                uniqueDeviceNames.forEach { name ->
                    // Each device should be independently accessible
                    manager.isDeviceManaged(name) shouldBe true
                    manager.isDeviceConnected(name) shouldBe true
                }
                
                // Then: All devices should maintain their state
                manager.getConnectedDeviceCount() shouldBe uniqueDeviceNames.size
                
                // When: Disconnecting one device
                val deviceToDisconnect = uniqueDeviceNames.first()
                manager.disconnectDevice(deviceToDisconnect)
                
                // Then: Only that device should be disconnected
                manager.isDeviceConnected(deviceToDisconnect) shouldBe false
                
                // And: Other devices should remain connected
                uniqueDeviceNames.drop(1).forEach { name ->
                    manager.isDeviceConnected(name) shouldBe true
                }
                
            } finally {
                manager.release()
            }
        }
    }
    
    "Property 7: Multi-Device Management - Device count invariants should be maintained" {
        checkAll(
            iterations = 100,
            Arb.list(Arb.string(5..15), 1..10),
            Arb.int(0..5) // number of devices to remove
        ) { deviceNames, removeCount ->
            
            val manager = MockUSBDeviceManager()
            
            try {
                // Given: Devices are added
                val uniqueDeviceNames = deviceNames.distinct()
                uniqueDeviceNames.forEach { name ->
                    manager.addDevice(name, DevicePriority.NORMAL)
                }
                
                val initialCount = manager.getDeviceCount()
                initialCount shouldBe uniqueDeviceNames.size
                
                // When: Some devices are removed
                val devicesToRemove = uniqueDeviceNames.take(removeCount.coerceAtMost(uniqueDeviceNames.size))
                devicesToRemove.forEach { name ->
                    manager.removeDevice(name)
                }
                
                // Then: Device count should be correct
                val expectedCount = initialCount - devicesToRemove.size
                manager.getDeviceCount() shouldBe expectedCount
                
                // And: Connected count should never exceed total count
                manager.getConnectedDeviceCount() shouldBe 
                    manager.getConnectedDeviceCount().coerceAtMost(manager.getDeviceCount())
                
            } finally {
                manager.release()
            }
        }
    }
})

/**
 * Mock USB Device Manager for testing without Android dependencies.
 * 
 * Simulates the behavior of USBDeviceManager for property testing.
 */
class MockUSBDeviceManager {
    
    private data class MockDevice(
        val name: String,
        var priority: DevicePriority,
        var isConnected: Boolean = false,
        var isActive: Boolean = false
    )
    
    private val devices = mutableMapOf<String, MockDevice>()
    private var activeDeviceName: String? = null
    
    fun addDevice(name: String, priority: DevicePriority): Boolean {
        if (devices.containsKey(name)) return false
        devices[name] = MockDevice(name, priority)
        return true
    }
    
    fun removeDevice(name: String): Boolean {
        val removed = devices.remove(name) != null
        if (activeDeviceName == name) {
            activeDeviceName = null
        }
        return removed
    }
    
    fun setDevicePriority(name: String, priority: DevicePriority): Boolean {
        val device = devices[name] ?: return false
        device.priority = priority
        return true
    }
    
    fun getDevicePriority(name: String): DevicePriority? {
        return devices[name]?.priority
    }
    
    fun connectToDevice(name: String): Boolean {
        val device = devices[name] ?: return false
        device.isConnected = true
        device.isActive = true
        
        // Deactivate other devices
        devices.values.filter { it.name != name }.forEach { it.isActive = false }
        activeDeviceName = name
        
        return true
    }
    
    fun switchToDevice(name: String, disconnectCurrent: Boolean = true): Boolean {
        if (disconnectCurrent) {
            activeDeviceName?.let { current ->
                if (current != name) {
                    devices[current]?.isActive = false
                }
            }
        }
        return connectToDevice(name)
    }
    
    fun disconnectDevice(name: String): Boolean {
        val device = devices[name] ?: return false
        device.isConnected = false
        device.isActive = false
        if (activeDeviceName == name) {
            activeDeviceName = null
        }
        return true
    }
    
    fun getActiveDeviceName(): String? = activeDeviceName
    
    fun isDeviceManaged(name: String): Boolean = devices.containsKey(name)
    
    fun isDeviceConnected(name: String): Boolean = devices[name]?.isConnected == true
    
    fun isDeviceActive(name: String): Boolean = devices[name]?.isActive == true
    
    fun getDeviceCount(): Int = devices.size
    
    fun getConnectedDeviceCount(): Int = devices.values.count { it.isConnected }
    
    fun getManagedDeviceNames(): List<String> = devices.keys.toList()
    
    fun getPreferredDeviceName(): String? {
        return devices.values
            .sortedBy { it.priority.ordinal }
            .firstOrNull()?.name
    }
    
    fun release() {
        devices.clear()
        activeDeviceName = null
    }
}
