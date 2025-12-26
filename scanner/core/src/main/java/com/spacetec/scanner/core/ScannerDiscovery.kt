/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 *
 * This file is part of the SpaceTec professional automotive diagnostic system.
 */

package com.spacetec.obd.scanner.core

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.wifi.WifiManager
import androidx.core.content.ContextCompat
import com.spacetec.core.domain.models.scanner.ScannerConnectionType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/**
 * Information about a discovered scanner device.
 */
data class DiscoveredScanner(
    val address: String,
    val name: String,
    val connectionType: ScannerConnectionType,
    val signalStrength: Int? = null, // For Bluetooth devices
    val isPaired: Boolean = false,
    val lastSeen: Long = System.currentTimeMillis(),
    val manufacturer: String? = null,
    val model: String? = null
)

/**
 * Scanner discovery service that finds available OBD-II devices.
 *
 * This service provides:
 * - Bluetooth device discovery
 * - WiFi network scanning
 * - USB device detection
 * - J2534 device enumeration
 * - Device filtering and validation
 * - Observable discovery results
 *
 * @param context Android context for accessing system services
 * @param dispatcher Coroutine dispatcher for discovery operations
 *
 * @author SpaceTec Development Team
 * @since 1.0.0
 */
class ScannerDiscoveryService @Inject constructor(
    private val context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val _discoveredDevices = MutableStateFlow<List<DiscoveredScanner>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredScanner>> = _discoveredDevices.asStateFlow()

    private val _discoveryState = MutableStateFlow<DiscoveryState>(DiscoveryState.Idle)
    val discoveryState: StateFlow<DiscoveryState> = _discoveryState.asStateFlow()

    private val discoveredAddresses = ConcurrentHashMap<String, DiscoveredScanner>()
    private var discoveryJob: Job? = null

    /**
     * Discovery state.
     */
    sealed class DiscoveryState {
        object Idle : DiscoveryState()
        object Discovering : DiscoveryState()
        data class Error(val message: String) : DiscoveryState()
    }

    /**
     * Starts discovery for all supported connection types.
     *
     * @param timeoutMs Maximum time to spend discovering (0 = no timeout)
     * @param includeBluetooth Whether to include Bluetooth discovery
     * @param includeWiFi Whether to include WiFi discovery
     * @param includeUSB Whether to include USB discovery
     * @param includeJ2534 Whether to include J2534 discovery
     */
    fun startDiscovery(
        timeoutMs: Long = 0,
        includeBluetooth: Boolean = true,
        includeWiFi: Boolean = true,
        includeUSB: Boolean = true,
        includeJ2534: Boolean = true
    ) {
        if (discoveryState.value is DiscoveryState.Discovering) {
            return // Already discovering
        }

        discoveryJob?.cancel()
        discoveryJob = CoroutineScope(dispatcher).launch {
            _discoveryState.value = DiscoveryState.Discovering
            
            try {
                val allDevices = mutableListOf<DiscoveredScanner>()
                
                // Discover devices based on requested types
                if (includeBluetooth && hasBluetoothPermissions()) {
                    allDevices.addAll(discoverBluetoothDevices())
                }
                
                if (includeWiFi && hasWiFiPermissions()) {
                    allDevices.addAll(discoverWiFiDevices())
                }
                
                if (includeUSB && hasUSBPermissions()) {
                    allDevices.addAll(discoverUSBDevices())
                }
                
                if (includeJ2534) {
                    allDevices.addAll(discoverJ2534Devices())
                }
                
                // Update discovered devices
                discoveredAddresses.putAll(allDevices.associateBy { it.address })
                _discoveredDevices.value = discoveredAddresses.values.toList()
                
                _discoveryState.value = DiscoveryState.Idle
            } catch (e: Exception) {
                _discoveryState.value = DiscoveryState.Error(e.message ?: "Unknown error")
            }
        }

        // Apply timeout if specified
        if (timeoutMs > 0) {
            CoroutineScope(dispatcher).launch {
                delay(timeoutMs)
                if (discoveryState.value is DiscoveryState.Discovering) {
                    stopDiscovery()
                }
            }
        }
    }

    /**
     * Stops the discovery process.
     */
    fun stopDiscovery() {
        discoveryJob?.cancel()
        _discoveryState.value = DiscoveryState.Idle
    }

    /**
     * Discovers Bluetooth devices.
     */
    private suspend fun discoverBluetoothDevices(): List<DiscoveredScanner> = withContext(Dispatchers.IO) {
        val devices = mutableListOf<DiscoveredScanner>()
        
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter != null && bluetoothAdapter.isEnabled) {
            // Get already paired devices
            val bondedDevices = bluetoothAdapter.bondedDevices
            for (device in bondedDevices) {
                if (isOBDDevice(device)) {
                    devices.add(
                        DiscoveredScanner(
                            address = device.address,
                            name = device.name ?: device.address,
                            connectionType = getBluetoothConnectionType(device),
                            isPaired = true,
                            signalStrength = null // Signal strength not available for bonded devices
                        )
                    )
                }
            }
            
            // Start discovery if needed
            if (!bluetoothAdapter.isDiscovering) {
                bluetoothAdapter.startDiscovery()
            }
            
            // Wait for discovery to complete or timeout
            delay(10000) // 10 seconds for discovery
            
            // Cancel discovery
            bluetoothAdapter.cancelDiscovery()
        }
        
        devices
    }

    /**
     * Discovers WiFi devices.
     */
    private suspend fun discoverWiFiDevices(): List<DiscoveredScanner> = withContext(Dispatchers.IO) {
        val devices = mutableListOf<DiscoveredScanner>()
        
        // Look for common OBD WiFi adapter ports
        val commonPorts = listOf(35000, 35001, 35002, 35003)
        val networkPrefix = getNetworkPrefix()
        
        if (networkPrefix != null) {
            // Scan common IP ranges for OBD adapters
            for (i in 1..254) {
                val ip = "$networkPrefix.$i"
                
                for (port in commonPorts) {
                    val address = "$ip:$port"
                    if (isReachable(address)) {
                        devices.add(
                            DiscoveredScanner(
                                address = address,
                                name = "OBD WiFi Adapter at $address",
                                connectionType = ScannerConnectionType.WIFI,
                                isPaired = false
                            )
                        )
                        break // Found on one port, no need to check others
                    }
                }
            }
        }
        
        devices
    }

    /**
     * Discovers USB devices.
     */
    private suspend fun discoverUSBDevices(): List<DiscoveredScanner> = withContext(Dispatchers.IO) {
        val devices = mutableListOf<DiscoveredScanner>()
        
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
        val deviceList = usbManager?.deviceList
        
        deviceList?.forEach { (_, usbDevice) ->
            if (isOBDUSBDevice(usbDevice)) {
                devices.add(
                    DiscoveredScanner(
                        address = "${usbDevice.vendorId}:${usbDevice.productId}",
                        name = usbDevice.productName ?: "USB OBD Device",
                        connectionType = ScannerConnectionType.USB,
                        manufacturer = usbDevice.manufacturerName,
                        model = usbDevice.productName
                    )
                )
            }
        }
        
        devices
    }

    /**
     * Discovers J2534 devices.
     */
    private suspend fun discoverJ2534Devices(): List<DiscoveredScanner> = withContext(Dispatchers.IO) {
        val devices = mutableListOf<DiscoveredScanner>()
        
        // Get available J2534 devices
        val j2534Devices = getAvailableJ2534Devices()
        
        for (device in j2534Devices) {
            devices.add(
                DiscoveredScanner(
                    address = device,
                    name = device,
                    connectionType = ScannerConnectionType.J2534
                )
            )
        }
        
        devices
    }

    /**
     * Checks if a Bluetooth device is likely an OBD adapter.
     */
    private fun isOBDDevice(device: BluetoothDevice): Boolean {
        val name = device.name?.uppercase() ?: ""
        val deviceClass = device.bluetoothClass?.deviceClass
        
        // Common OBD adapter identifiers
        val obdKeywords = listOf(
            "OBD", "ELM", "OBD2", "OBDII", "VEEPEAK", "VGATE", 
            "OBDLINK", "BLUEDRIVER", "CARISTA", "LAUNCH"
        )
        
        return obdKeywords.any { keyword -> name.contains(keyword) } ||
               deviceClass == BluetoothDevice.MajorDeviceClass.UNCATEGORIZED
    }

    /**
     * Determines the Bluetooth connection type for a device.
     */
    private fun getBluetoothConnectionType(device: BluetoothDevice): ScannerConnectionType {
        // In a real implementation, we'd check the supported profiles
        // For now, assume Classic for OBD adapters
        return ScannerConnectionType.BLUETOOTH_CLASSIC
    }

    /**
     * Gets the network prefix for the current WiFi network.
     */
    private fun getNetworkPrefix(): String? {
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val connectionInfo = wifiManager?.connectionInfo
        
        if (connectionInfo != null) {
            val ipAddress = connectionInfo.ipAddress
            // Convert to dotted decimal format and extract prefix
            val ipString = java.lang.String.format(
                "%d.%d.%d",
                ipAddress and 0xFF,
                (ipAddress shr 8) and 0xFF,
                (ipAddress shr 16) and 0xFF
            )
            return ipString
        }
        
        return null
    }

    /**
     * Checks if an address is reachable.
     */
    private suspend fun isReachable(address: String): Boolean = withContext(Dispatchers.IO) {
        val (host, port) = try {
            val parts = address.split(":")
            if (parts.size == 2) {
                Pair(parts[0], parts[1].toInt())
            } else {
                return@withContext false
            }
        } catch (e: Exception) {
            return@withContext false
        }

        try {
            val socket = java.net.Socket()
            socket.connect(java.net.InetSocketAddress(host, port), 1000) // 1 second timeout
            socket.close()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks if a USB device is an OBD adapter.
     */
    private fun isOBDUSBDevice(usbDevice: UsbDevice): Boolean {
        // Common vendor IDs for OBD adapters
        val obdVendorIds = setOf(
            0x0403, // FTDI
            0x10C4, // Silicon Labs CP210x
            0x1A86, // CH340
            0x067B, // Prolific PL2303
            0xEA60, // OBDLink
            0x04D8  // Microchip
        )
        
        return usbDevice.vendorId in obdVendorIds
    }

    /**
     * Gets available J2534 devices.
     */
    private fun getAvailableJ2534Devices(): List<String> {
        // In a real implementation, this would enumerate J2534 drivers
        // For now, return an empty list
        return emptyList()
    }

    /**
     * Checks if Bluetooth permissions are granted.
     */
    private fun hasBluetoothPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH
        ) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_ADMIN
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Checks if WiFi permissions are granted.
     */
    private fun hasWiFiPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_WIFI_STATE
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Checks if USB permissions are granted.
     */
    private fun hasUSBPermissions(): Boolean {
        // USB permissions are typically handled differently
        // This is just a basic check
        return ContextCompat.checkSelfPermission(
            context,
            "android.permission.USB_PERMISSION"
        ) == PackageManager.PERMISSION_GRANTED ||
        context.packageManager.hasSystemFeature("android.hardware.usb.host")
    }

    /**
     * Filters discovered devices by connection type.
     */
    fun getDevicesByType(type: ScannerConnectionType): List<DiscoveredScanner> {
        return discoveredDevices.value.filter { it.connectionType == type }
    }

    /**
     * Gets a specific discovered device by address.
     */
    fun getDeviceByAddress(address: String): DiscoveredScanner? {
        return discoveredAddresses[address]
    }

    /**
     * Clears all discovered devices.
     */
    fun clearDiscoveredDevices() {
        discoveredAddresses.clear()
        _discoveredDevices.value = emptyList()
    }

    /**
     * Refreshes the discovery results.
     */
    fun refreshDiscovery(
        includeBluetooth: Boolean = true,
        includeWiFi: Boolean = true,
        includeUSB: Boolean = true,
        includeJ2534: Boolean = true
    ) {
        startDiscovery(
            timeoutMs = 0,
            includeBluetooth = includeBluetooth,
            includeWiFi = includeWiFi,
            includeUSB = includeUSB,
            includeJ2534 = includeJ2534
        )
    }

    /**
     * Gets statistics about discovered devices.
     */
    fun getDiscoveryStatistics(): DiscoveryStatistics {
        val devices = discoveredDevices.value
        val byType = devices.groupingBy { it.connectionType }.eachCount()
        
        return DiscoveryStatistics(
            totalDevices = devices.size,
            byConnectionType = byType,
            lastDiscoveryTime = devices.maxOfOrNull { it.lastSeen } ?: 0
        )
    }

    /**
     * Discovery statistics.
     */
    data class DiscoveryStatistics(
        val totalDevices: Int,
        val byConnectionType: Map<ScannerConnectionType, Int>,
        val lastDiscoveryTime: Long
    )

    companion object {
        /**
         * Creates a scanner discovery service.
         *
         * @param context Android context
         * @return Scanner discovery service
         */
        fun create(context: Context): ScannerDiscoveryService {
            return ScannerDiscoveryService(context)
        }
    }
}