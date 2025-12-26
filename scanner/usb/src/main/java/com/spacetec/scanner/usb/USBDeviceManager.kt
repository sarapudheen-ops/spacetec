/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 *
 * This file is part of the SpaceTec professional automotive diagnostic system.
 */

package com.spacetec.obd.scanner.usb

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.spacetec.core.common.result.Result
import com.spacetec.obd.scanner.core.ConnectionInfo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Device priority levels for multi-device management.
 */
enum class DevicePriority {
    /**
     * Highest priority - preferred device.
     */
    HIGH,
    
    /**
     * Normal priority - standard device.
     */
    NORMAL,
    
    /**
     * Low priority - fallback device.
     */
    LOW
}

/**
 * Managed USB device information.
 */
data class ManagedUSBDevice(
    val device: UsbDevice,
    val connection: USBConnection?,
    val priority: DevicePriority,
    val isActive: Boolean,
    val lastUsed: Long,
    val connectionCount: Int,
    val errorCount: Int
) {
    /**
     * Device name.
     */
    val deviceName: String get() = device.deviceName
    
    /**
     * Whether the device is currently connected.
     */
    val isConnected: Boolean get() = connection?.isConnected == true
    
    /**
     * Product name if available.
     */
    val productName: String? get() = device.productName
}

/**
 * Multi-device USB manager.
 *
 * Manages multiple USB devices simultaneously, providing device selection,
 * switching, and priority management capabilities.
 *
 * ## Features
 *
 * - Concurrent device management
 * - Device priority and preference management
 * - Automatic device selection
 * - Device switching without interference
 * - Connection pooling and reuse
 *
 * ## Usage Example
 *
 * ```kotlin
 * val manager = USBDeviceManager(context)
 *
 * // Add devices
 * manager.addDevice(device1, DevicePriority.HIGH)
 * manager.addDevice(device2, DevicePriority.NORMAL)
 *
 * // Connect to preferred device
 * val result = manager.connectToPreferred()
 *
 * // Switch to another device
 * manager.switchToDevice(device2.deviceName)
 *
 * // Get active connection
 * val connection = manager.getActiveConnection()
 * ```
 *
 * @param context Android context
 * @param permissionHandler USB permission handler
 * @param dispatcher Coroutine dispatcher
 *
 * @author SpaceTec Development Team
 * @since 1.0.0
 */
@Singleton
class USBDeviceManager @Inject constructor(
    private val context: Context,
    private val permissionHandler: USBPermissionHandler,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    
    private val usbManager: UsbManager by lazy {
        context.getSystemService(Context.USB_SERVICE) as UsbManager
    }
    
    private val scope = CoroutineScope(dispatcher + SupervisorJob())
    private val deviceMutex = Mutex()
    
    private val managedDevices = ConcurrentHashMap<String, ManagedUSBDevice>()
    private val connections = ConcurrentHashMap<String, USBConnection>()
    
    private val _activeDevice = MutableStateFlow<ManagedUSBDevice?>(null)
    
    /**
     * Currently active device.
     */
    val activeDevice: StateFlow<ManagedUSBDevice?> = _activeDevice.asStateFlow()
    
    private val _managedDevicesList = MutableStateFlow<List<ManagedUSBDevice>>(emptyList())
    
    /**
     * List of all managed devices.
     */
    val managedDevicesList: StateFlow<List<ManagedUSBDevice>> = _managedDevicesList.asStateFlow()
    
    /**
     * Adds a device to management.
     *
     * @param device USB device to add
     * @param priority Device priority
     * @return true if device was added
     */
    suspend fun addDevice(
        device: UsbDevice,
        priority: DevicePriority = DevicePriority.NORMAL
    ): Boolean = deviceMutex.withLock {
        if (managedDevices.containsKey(device.deviceName)) {
            return@withLock false
        }
        
        val managedDevice = ManagedUSBDevice(
            device = device,
            connection = null,
            priority = priority,
            isActive = false,
            lastUsed = 0,
            connectionCount = 0,
            errorCount = 0
        )
        
        managedDevices[device.deviceName] = managedDevice
        updateDevicesList()
        
        return@withLock true
    }
    
    /**
     * Removes a device from management.
     *
     * @param deviceName Device name to remove
     * @return true if device was removed
     */
    suspend fun removeDevice(deviceName: String): Boolean = deviceMutex.withLock {
        val device = managedDevices.remove(deviceName) ?: return@withLock false
        
        // Disconnect if connected
        connections.remove(deviceName)?.let { connection ->
            try {
                connection.disconnect()
                connection.release()
            } catch (_: Exception) {}
        }
        
        // Update active device if needed
        if (_activeDevice.value?.deviceName == deviceName) {
            _activeDevice.value = null
        }
        
        updateDevicesList()
        return@withLock true
    }
    
    /**
     * Sets the priority for a device.
     *
     * @param deviceName Device name
     * @param priority New priority
     * @return true if priority was set
     */
    suspend fun setDevicePriority(
        deviceName: String,
        priority: DevicePriority
    ): Boolean = deviceMutex.withLock {
        val device = managedDevices[deviceName] ?: return@withLock false
        
        managedDevices[deviceName] = device.copy(priority = priority)
        updateDevicesList()
        
        return@withLock true
    }
    
    /**
     * Connects to a specific device.
     *
     * @param deviceName Device name to connect to
     * @param config Connection configuration
     * @return Connection result
     */
    suspend fun connectToDevice(
        deviceName: String,
        config: USBConnectionConfig = USBConnectionConfig.DEFAULT
    ): Result<ConnectionInfo> = deviceMutex.withLock {
        val managedDevice = managedDevices[deviceName]
            ?: return@withLock Result.Error(Exception("Device not found: $deviceName"))
        
        // Check permission
        if (!permissionHandler.hasPermission(managedDevice.device)) {
            val permResult = permissionHandler.requestPermission(managedDevice.device)
            if (permResult !is USBPermissionResult.Granted) {
                return@withLock Result.Error(Exception("Permission not granted"))
            }
        }
        
        // Get or create connection
        val connection = connections.getOrPut(deviceName) {
            USBConnection(context, config)
        }
        
        // Connect
        val result = connection.connect(deviceName, config.baseConfig)
        
        if (result is Result.Success) {
            // Update managed device
            managedDevices[deviceName] = managedDevice.copy(
                connection = connection,
                isActive = true,
                lastUsed = System.currentTimeMillis(),
                connectionCount = managedDevice.connectionCount + 1
            )
            
            // Set as active
            _activeDevice.value = managedDevices[deviceName]
            
            // Deactivate other devices
            managedDevices.forEach { (name, device) ->
                if (name != deviceName && device.isActive) {
                    managedDevices[name] = device.copy(isActive = false)
                }
            }
            
            updateDevicesList()
        } else {
            // Update error count
            managedDevices[deviceName] = managedDevice.copy(
                errorCount = managedDevice.errorCount + 1
            )
            updateDevicesList()
        }
        
        return@withLock result
    }
    
    /**
     * Connects to the preferred (highest priority) device.
     *
     * @param config Connection configuration
     * @return Connection result
     */
    suspend fun connectToPreferred(
        config: USBConnectionConfig = USBConnectionConfig.DEFAULT
    ): Result<ConnectionInfo> {
        val preferredDevice = getPreferredDevice()
            ?: return Result.Error(Exception("No devices available"))
        
        return connectToDevice(preferredDevice.deviceName, config)
    }
    
    /**
     * Switches to a different device.
     *
     * @param deviceName Device name to switch to
     * @param disconnectCurrent Whether to disconnect the current device
     * @param config Connection configuration
     * @return Connection result
     */
    suspend fun switchToDevice(
        deviceName: String,
        disconnectCurrent: Boolean = true,
        config: USBConnectionConfig = USBConnectionConfig.DEFAULT
    ): Result<ConnectionInfo> = deviceMutex.withLock {
        // Disconnect current if requested
        if (disconnectCurrent) {
            _activeDevice.value?.let { current ->
                if (current.deviceName != deviceName) {
                    disconnectDeviceInternal(current.deviceName)
                }
            }
        }
        
        // Connect to new device (release lock first)
        return@withLock connectToDevice(deviceName, config)
    }
    
    /**
     * Disconnects a specific device.
     *
     * @param deviceName Device name to disconnect
     * @param graceful Whether to perform graceful disconnection
     */
    suspend fun disconnectDevice(
        deviceName: String,
        graceful: Boolean = true
    ) = deviceMutex.withLock {
        disconnectDeviceInternal(deviceName, graceful)
    }
    
    private suspend fun disconnectDeviceInternal(
        deviceName: String,
        graceful: Boolean = true
    ) {
        val connection = connections[deviceName] ?: return
        
        try {
            connection.disconnect(graceful)
        } catch (_: Exception) {}
        
        // Update managed device
        managedDevices[deviceName]?.let { device ->
            managedDevices[deviceName] = device.copy(
                isActive = false
            )
        }
        
        // Update active device if needed
        if (_activeDevice.value?.deviceName == deviceName) {
            _activeDevice.value = null
        }
        
        updateDevicesList()
    }
    
    /**
     * Disconnects all devices.
     *
     * @param graceful Whether to perform graceful disconnection
     */
    suspend fun disconnectAll(graceful: Boolean = true) = deviceMutex.withLock {
        connections.keys.toList().forEach { deviceName ->
            disconnectDeviceInternal(deviceName, graceful)
        }
    }
    
    /**
     * Gets the active connection.
     *
     * @return Active USB connection or null
     */
    fun getActiveConnection(): USBConnection? {
        return _activeDevice.value?.connection
    }
    
    /**
     * Gets a connection for a specific device.
     *
     * @param deviceName Device name
     * @return USB connection or null
     */
    fun getConnection(deviceName: String): USBConnection? {
        return connections[deviceName]
    }
    
    /**
     * Gets the preferred device based on priority and availability.
     *
     * @return Preferred device or null
     */
    fun getPreferredDevice(): ManagedUSBDevice? {
        return managedDevices.values
            .filter { usbManager.deviceList.containsKey(it.deviceName) }
            .sortedWith(compareBy(
                { it.priority.ordinal },
                { -it.lastUsed },
                { it.errorCount }
            ))
            .firstOrNull()
    }
    
    /**
     * Gets all connected devices.
     *
     * @return List of connected devices
     */
    fun getConnectedDevices(): List<ManagedUSBDevice> {
        return managedDevices.values.filter { it.isConnected }
    }
    
    /**
     * Gets all available (physically connected) devices.
     *
     * @return List of available devices
     */
    fun getAvailableDevices(): List<ManagedUSBDevice> {
        return managedDevices.values.filter { 
            usbManager.deviceList.containsKey(it.deviceName) 
        }
    }
    
    /**
     * Checks if a device is managed.
     *
     * @param deviceName Device name
     * @return true if device is managed
     */
    fun isDeviceManaged(deviceName: String): Boolean {
        return managedDevices.containsKey(deviceName)
    }
    
    /**
     * Checks if a device is connected.
     *
     * @param deviceName Device name
     * @return true if device is connected
     */
    fun isDeviceConnected(deviceName: String): Boolean {
        return managedDevices[deviceName]?.isConnected == true
    }
    
    /**
     * Gets device count.
     *
     * @return Number of managed devices
     */
    fun getDeviceCount(): Int = managedDevices.size
    
    /**
     * Gets connected device count.
     *
     * @return Number of connected devices
     */
    fun getConnectedDeviceCount(): Int = getConnectedDevices().size
    
    private fun updateDevicesList() {
        _managedDevicesList.value = managedDevices.values.toList()
    }
    
    /**
     * Releases all resources.
     */
    fun release() {
        scope.launch {
            disconnectAll(graceful = false)
            
            connections.values.forEach { connection ->
                try {
                    connection.release()
                } catch (_: Exception) {}
            }
            connections.clear()
            managedDevices.clear()
            
            _activeDevice.value = null
            updateDevicesList()
        }
        
        scope.cancel()
    }
    
    companion object {
        /**
         * Creates a USBDeviceManager instance.
         */
        fun create(context: Context): USBDeviceManager {
            val permissionHandler = USBPermissionHandler(context)
            return USBDeviceManager(context, permissionHandler)
        }
    }
}
