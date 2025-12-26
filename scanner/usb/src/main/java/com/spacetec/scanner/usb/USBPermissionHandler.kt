/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 *
 * This file is part of the SpaceTec professional automotive diagnostic system.
 */

package com.spacetec.obd.scanner.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Result of a USB permission request.
 */
sealed class USBPermissionResult {
    /**
     * Permission was granted.
     */
    data class Granted(val device: UsbDevice) : USBPermissionResult()
    
    /**
     * Permission was denied by user.
     */
    data class Denied(val device: UsbDevice) : USBPermissionResult()
    
    /**
     * Permission request timed out.
     */
    data class Timeout(val device: UsbDevice) : USBPermissionResult()
    
    /**
     * An error occurred during permission request.
     */
    data class Error(val device: UsbDevice, val exception: Throwable) : USBPermissionResult()
}

/**
 * Handles USB permission requests and management.
 *
 * Provides a coroutine-based API for requesting USB permissions and
 * tracking permission state changes.
 *
 * ## Usage Example
 *
 * ```kotlin
 * val handler = USBPermissionHandler(context)
 *
 * // Request permission for a device
 * val result = handler.requestPermission(device)
 * when (result) {
 *     is USBPermissionResult.Granted -> println("Permission granted")
 *     is USBPermissionResult.Denied -> println("Permission denied")
 *     is USBPermissionResult.Timeout -> println("Request timed out")
 *     is USBPermissionResult.Error -> println("Error: ${result.exception}")
 * }
 * ```
 *
 * @param context Android context
 *
 * @author SpaceTec Development Team
 * @since 1.0.0
 */
@Singleton
class USBPermissionHandler @Inject constructor(
    private val context: Context
) {
    
    private val usbManager: UsbManager by lazy {
        context.getSystemService(Context.USB_SERVICE) as UsbManager
    }
    
    private val pendingRequests = ConcurrentHashMap<String, (Boolean) -> Unit>()
    
    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_USB_PERMISSION) {
                val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                }
                
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                
                device?.let {
                    pendingRequests.remove(it.deviceName)?.invoke(granted)
                }
            }
        }
    }
    
    private var isReceiverRegistered = false
    
    /**
     * Checks if permission is granted for a device.
     *
     * @param device USB device
     * @return true if permission is granted
     */
    fun hasPermission(device: UsbDevice): Boolean {
        return usbManager.hasPermission(device)
    }
    
    /**
     * Requests permission for a USB device.
     *
     * This is a suspending function that waits for the user's response.
     *
     * @param device USB device to request permission for
     * @param timeout Timeout in milliseconds (default: 30 seconds)
     * @return Permission result
     */
    suspend fun requestPermission(
        device: UsbDevice,
        timeout: Long = USBConstants.PERMISSION_REQUEST_TIMEOUT
    ): USBPermissionResult {
        // Check if already granted
        if (hasPermission(device)) {
            return USBPermissionResult.Granted(device)
        }
        
        return try {
            withTimeout(timeout) {
                suspendCancellableCoroutine { continuation ->
                    // Register receiver if needed
                    ensureReceiverRegistered()
                    
                    // Store callback
                    pendingRequests[device.deviceName] = { granted ->
                        if (continuation.isActive) {
                            if (granted) {
                                continuation.resume(USBPermissionResult.Granted(device))
                            } else {
                                continuation.resume(USBPermissionResult.Denied(device))
                            }
                        }
                    }
                    
                    // Request permission
                    val permissionIntent = PendingIntent.getBroadcast(
                        context,
                        0,
                        Intent(ACTION_USB_PERMISSION),
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                        } else {
                            PendingIntent.FLAG_UPDATE_CURRENT
                        }
                    )
                    
                    usbManager.requestPermission(device, permissionIntent)
                    
                    // Handle cancellation
                    continuation.invokeOnCancellation {
                        pendingRequests.remove(device.deviceName)
                    }
                }
            }
        } catch (e: CancellationException) {
            pendingRequests.remove(device.deviceName)
            throw e
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            pendingRequests.remove(device.deviceName)
            USBPermissionResult.Timeout(device)
        } catch (e: Exception) {
            pendingRequests.remove(device.deviceName)
            USBPermissionResult.Error(device, e)
        }
    }
    
    /**
     * Requests permission for multiple devices.
     *
     * @param devices List of USB devices
     * @param timeout Timeout per device in milliseconds
     * @return Map of device to permission result
     */
    suspend fun requestPermissions(
        devices: List<UsbDevice>,
        timeout: Long = USBConstants.PERMISSION_REQUEST_TIMEOUT
    ): Map<UsbDevice, USBPermissionResult> {
        return devices.associateWith { device ->
            requestPermission(device, timeout)
        }
    }
    
    /**
     * Ensures the permission receiver is registered.
     */
    @Synchronized
    private fun ensureReceiverRegistered() {
        if (!isReceiverRegistered) {
            val filter = IntentFilter(ACTION_USB_PERMISSION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(permissionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(permissionReceiver, filter)
            }
            isReceiverRegistered = true
        }
    }
    
    /**
     * Unregisters the permission receiver.
     *
     * Call this when the handler is no longer needed.
     */
    @Synchronized
    fun unregister() {
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(permissionReceiver)
            } catch (_: Exception) {}
            isReceiverRegistered = false
        }
        pendingRequests.clear()
    }
    
    /**
     * Gets all devices that have permission granted.
     *
     * @return List of devices with permission
     */
    fun getDevicesWithPermission(): List<UsbDevice> {
        return usbManager.deviceList.values.filter { hasPermission(it) }
    }
    
    /**
     * Gets all devices that need permission.
     *
     * @return List of devices without permission
     */
    fun getDevicesNeedingPermission(): List<UsbDevice> {
        return usbManager.deviceList.values.filter { !hasPermission(it) }
    }
    
    companion object {
        /**
         * Action for USB permission broadcast.
         */
        const val ACTION_USB_PERMISSION = "com.spacetec.scanner.usb.USB_PERMISSION"
    }
}

/**
 * USB device event types.
 */
sealed class USBDeviceEvent {
    /**
     * A USB device was attached.
     */
    data class Attached(val device: UsbDevice) : USBDeviceEvent()
    
    /**
     * A USB device was detached.
     */
    data class Detached(val device: UsbDevice) : USBDeviceEvent()
    
    /**
     * Permission was granted for a device.
     */
    data class PermissionGranted(val device: UsbDevice) : USBDeviceEvent()
    
    /**
     * Permission was denied for a device.
     */
    data class PermissionDenied(val device: UsbDevice) : USBDeviceEvent()
}

/**
 * Monitors USB device attach/detach events.
 *
 * Provides a Flow-based API for observing USB device events.
 *
 * @param context Android context
 *
 * @author SpaceTec Development Team
 * @since 1.0.0
 */
@Singleton
class USBDeviceMonitor @Inject constructor(
    private val context: Context
) {
    
    private val usbManager: UsbManager by lazy {
        context.getSystemService(Context.USB_SERVICE) as UsbManager
    }
    
    /**
     * Flow of USB device events.
     *
     * Emits events when devices are attached, detached, or permission changes.
     */
    fun deviceEvents(): Flow<USBDeviceEvent> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                }
                
                device?.let {
                    when (intent.action) {
                        UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                            trySend(USBDeviceEvent.Attached(it))
                        }
                        UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                            trySend(USBDeviceEvent.Detached(it))
                        }
                        USBPermissionHandler.ACTION_USB_PERMISSION -> {
                            val granted = intent.getBooleanExtra(
                                UsbManager.EXTRA_PERMISSION_GRANTED, false
                            )
                            if (granted) {
                                trySend(USBDeviceEvent.PermissionGranted(it))
                            } else {
                                trySend(USBDeviceEvent.PermissionDenied(it))
                            }
                        }
                    }
                }
            }
        }
        
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(USBPermissionHandler.ACTION_USB_PERMISSION)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        
        awaitClose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {}
        }
    }
    
    /**
     * Gets the current list of connected USB devices.
     *
     * @return List of connected devices
     */
    fun getConnectedDevices(): List<UsbDevice> {
        return usbManager.deviceList.values.toList()
    }
    
    /**
     * Gets the current list of connected OBD adapters.
     *
     * @return List of connected OBD adapters
     */
    fun getConnectedOBDAdapters(): List<UsbDevice> {
        return getConnectedDevices().filter { device ->
            com.spacetec.scanner.usb.drivers.USBDriverFactory.isOBDAdapter(device)
        }
    }
    
    /**
     * Checks if a specific device is connected.
     *
     * @param deviceName Device name to check
     * @return true if device is connected
     */
    fun isDeviceConnected(deviceName: String): Boolean {
        return usbManager.deviceList.containsKey(deviceName)
    }
    
    /**
     * Checks if a device with specific vendor/product ID is connected.
     *
     * @param vendorId Vendor ID
     * @param productId Product ID
     * @return true if matching device is connected
     */
    fun isDeviceConnected(vendorId: Int, productId: Int): Boolean {
        return usbManager.deviceList.values.any { device ->
            device.vendorId == vendorId && device.productId == productId
        }
    }
}
