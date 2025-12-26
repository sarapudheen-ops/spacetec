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
import com.spacetec.core.domain.models.scanner.Scanner
import com.spacetec.core.domain.models.scanner.ScannerConnectionType
import com.spacetec.core.domain.models.scanner.ScannerDeviceType
import com.spacetec.obd.scanner.core.DiscoveredScanner
import com.spacetec.obd.scanner.usb.drivers.USBDriverFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Discovered USB scanner information.
 */
data class DiscoveredUSBScanner(
    val device: UsbDevice,
    val driverType: USBDriverType,
    val hasPermission: Boolean,
    val isOBDAdapter: Boolean,
    val recommendedBaudRate: Int
) {
    /**
     * Device name (path).
     */
    val deviceName: String get() = device.deviceName
    
    /**
     * Product name if available.
     */
    val productName: String? get() = device.productName
    
    /**
     * Manufacturer name if available.
     */
    val manufacturerName: String? get() = device.manufacturerName
    
    /**
     * Vendor ID.
     */
    val vendorId: Int get() = device.vendorId
    
    /**
     * Product ID.
     */
    val productId: Int get() = device.productId
    
    /**
     * Human-readable display name.
     */
    val displayName: String
        get() = productName ?: manufacturerName ?: "USB Device (${vendorId.toString(16)}:${productId.toString(16)})"
    
    /**
     * Driver name.
     */
    val driverName: String
        get() = USBDriverFactory.getDriverName(driverType)
    
    /**
     * Converts to generic DiscoveredScanner.
     */
    fun toDiscoveredScanner(): DiscoveredScanner {
        val scanner = Scanner(
            id = deviceName,
            name = displayName,
            connectionType = ScannerConnectionType.USB,
            deviceType = ScannerDeviceType.ELM327, // Default, will be detected on connect
            address = deviceName,
            isPaired = hasPermission,
            metadata = mapOf(
                "vendorId" to vendorId.toString(),
                "productId" to productId.toString(),
                "driverType" to driverType.name,
                "isOBDAdapter" to isOBDAdapter.toString(),
                "recommendedBaudRate" to recommendedBaudRate.toString()
            )
        )
        
        return DiscoveredScanner(
            scanner = scanner,
            signalStrength = null,
            isNew = true
        )
    }
}

/**
 * USB scanner discovery service.
 *
 * Discovers and monitors USB OBD adapters connected to the device.
 * Unlike Bluetooth or WiFi, USB discovery is instant since devices
 * are directly enumerated from the USB manager.
 *
 * ## Features
 *
 * - Instant device enumeration
 * - Hot-plug monitoring
 * - Automatic driver detection
 * - Permission state tracking
 * - OBD adapter filtering
 *
 * ## Usage Example
 *
 * ```kotlin
 * val discovery = USBDiscovery(context)
 *
 * // Get current devices
 * val devices = discovery.getDiscoveredScanners()
 *
 * // Monitor for changes
 * discovery.discoveredScanners.collect { scanners ->
 *     println("Found ${scanners.size} USB scanners")
 * }
 *
 * // Start monitoring
 * discovery.startMonitoring()
 * ```
 *
 * @param context Android context
 * @param permissionHandler USB permission handler
 * @param deviceMonitor USB device monitor
 * @param dispatcher Coroutine dispatcher
 *
 * @author SpaceTec Development Team
 * @since 1.0.0
 */
@Singleton
class USBDiscovery @Inject constructor(
    private val context: Context,
    private val permissionHandler: USBPermissionHandler,
    private val deviceMonitor: USBDeviceMonitor,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    
    private val usbManager: UsbManager by lazy {
        context.getSystemService(Context.USB_SERVICE) as UsbManager
    }
    
    private val scope = CoroutineScope(dispatcher + SupervisorJob())
    
    private val _discoveredScanners = MutableStateFlow<List<DiscoveredUSBScanner>>(emptyList())
    
    /**
     * Flow of discovered USB scanners.
     */
    val discoveredScanners: StateFlow<List<DiscoveredUSBScanner>> = _discoveredScanners.asStateFlow()
    
    private val _scannerEvents = MutableSharedFlow<USBScannerEvent>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    
    /**
     * Flow of scanner events (attach/detach).
     */
    val scannerEvents: SharedFlow<USBScannerEvent> = _scannerEvents.asSharedFlow()
    
    private val _isMonitoring = MutableStateFlow(false)
    
    /**
     * Whether monitoring is active.
     */
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()
    
    private var monitoringJob: Job? = null
    
    /**
     * Gets the current list of discovered USB scanners.
     *
     * @param obdOnly If true, only returns OBD adapters
     * @return List of discovered scanners
     */
    fun getDiscoveredScanners(obdOnly: Boolean = false): List<DiscoveredUSBScanner> {
        val devices = usbManager.deviceList.values
        
        return devices.mapNotNull { device ->
            val driverType = USBDriverFactory.detectDriverType(device)
            val isOBDAdapter = USBDriverFactory.isOBDAdapter(device)
            
            if (obdOnly && !isOBDAdapter) {
                return@mapNotNull null
            }
            
            DiscoveredUSBScanner(
                device = device,
                driverType = driverType,
                hasPermission = permissionHandler.hasPermission(device),
                isOBDAdapter = isOBDAdapter,
                recommendedBaudRate = USBDriverFactory.getRecommendedBaudRate(device)
            )
        }
    }
    
    /**
     * Gets discovered scanners as generic DiscoveredScanner objects.
     *
     * @param obdOnly If true, only returns OBD adapters
     * @return Flow of discovered scanners
     */
    fun getDiscoveredScannersFlow(obdOnly: Boolean = false): Flow<List<DiscoveredScanner>> {
        return discoveredScanners.map { scanners ->
            scanners
                .filter { !obdOnly || it.isOBDAdapter }
                .map { it.toDiscoveredScanner() }
        }
    }
    
    /**
     * Refreshes the list of discovered scanners.
     */
    fun refresh() {
        _discoveredScanners.value = getDiscoveredScanners()
    }
    
    /**
     * Starts monitoring for USB device changes.
     */
    fun startMonitoring() {
        if (_isMonitoring.value) return
        
        _isMonitoring.value = true
        
        // Initial scan
        refresh()
        
        // Monitor for device events
        monitoringJob = scope.launch {
            deviceMonitor.deviceEvents().collect { event ->
                when (event) {
                    is USBDeviceEvent.Attached -> {
                        refresh()
                        val scanner = createDiscoveredScanner(event.device)
                        _scannerEvents.emit(USBScannerEvent.Attached(scanner))
                    }
                    is USBDeviceEvent.Detached -> {
                        val scanner = createDiscoveredScanner(event.device)
                        _scannerEvents.emit(USBScannerEvent.Detached(scanner))
                        refresh()
                    }
                    is USBDeviceEvent.PermissionGranted -> {
                        refresh()
                        val scanner = createDiscoveredScanner(event.device)
                        _scannerEvents.emit(USBScannerEvent.PermissionChanged(scanner, true))
                    }
                    is USBDeviceEvent.PermissionDenied -> {
                        refresh()
                        val scanner = createDiscoveredScanner(event.device)
                        _scannerEvents.emit(USBScannerEvent.PermissionChanged(scanner, false))
                    }
                }
            }
        }
    }
    
    /**
     * Stops monitoring for USB device changes.
     */
    fun stopMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
        _isMonitoring.value = false
    }
    
    /**
     * Requests permission for a discovered scanner.
     *
     * @param scanner Scanner to request permission for
     * @return Permission result
     */
    suspend fun requestPermission(scanner: DiscoveredUSBScanner): USBPermissionResult {
        return permissionHandler.requestPermission(scanner.device)
    }
    
    /**
     * Requests permission for a device by name.
     *
     * @param deviceName Device name
     * @return Permission result or null if device not found
     */
    suspend fun requestPermission(deviceName: String): USBPermissionResult? {
        val device = usbManager.deviceList[deviceName] ?: return null
        return permissionHandler.requestPermission(device)
    }
    
    /**
     * Gets a scanner by device name.
     *
     * @param deviceName Device name
     * @return Discovered scanner or null if not found
     */
    fun getScanner(deviceName: String): DiscoveredUSBScanner? {
        val device = usbManager.deviceList[deviceName] ?: return null
        return createDiscoveredScanner(device)
    }
    
    /**
     * Gets a scanner by vendor/product ID.
     *
     * @param vendorId Vendor ID
     * @param productId Product ID
     * @return Discovered scanner or null if not found
     */
    fun getScanner(vendorId: Int, productId: Int): DiscoveredUSBScanner? {
        val device = usbManager.deviceList.values.find { 
            it.vendorId == vendorId && it.productId == productId 
        } ?: return null
        return createDiscoveredScanner(device)
    }
    
    /**
     * Creates a DiscoveredUSBScanner from a UsbDevice.
     */
    private fun createDiscoveredScanner(device: UsbDevice): DiscoveredUSBScanner {
        return DiscoveredUSBScanner(
            device = device,
            driverType = USBDriverFactory.detectDriverType(device),
            hasPermission = permissionHandler.hasPermission(device),
            isOBDAdapter = USBDriverFactory.isOBDAdapter(device),
            recommendedBaudRate = USBDriverFactory.getRecommendedBaudRate(device)
        )
    }
    
    /**
     * Releases resources.
     */
    fun release() {
        stopMonitoring()
        scope.cancel()
    }
    
    companion object {
        /**
         * Creates a USBDiscovery instance.
         */
        fun create(context: Context): USBDiscovery {
            val permissionHandler = USBPermissionHandler(context)
            val deviceMonitor = USBDeviceMonitor(context)
            return USBDiscovery(context, permissionHandler, deviceMonitor)
        }
    }
}

/**
 * USB scanner events.
 */
sealed class USBScannerEvent {
    /**
     * A scanner was attached.
     */
    data class Attached(val scanner: DiscoveredUSBScanner) : USBScannerEvent()
    
    /**
     * A scanner was detached.
     */
    data class Detached(val scanner: DiscoveredUSBScanner) : USBScannerEvent()
    
    /**
     * Permission state changed for a scanner.
     */
    data class PermissionChanged(
        val scanner: DiscoveredUSBScanner,
        val granted: Boolean
    ) : USBScannerEvent()
}
