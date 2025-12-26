/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 *
 * This file is part of the SpaceTec professional automotive diagnostic system.
 */

package com.spacetec.obd.scanner.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import com.spacetec.core.common.exceptions.ConnectionException
import com.spacetec.core.domain.models.scanner.Scanner
import com.spacetec.core.domain.models.scanner.ScannerConnectionType
import com.spacetec.obd.scanner.bluetooth.ble.BluetoothLEConstants
import com.spacetec.obd.scanner.bluetooth.classic.BluetoothClassicConstants
import com.spacetec.obd.scanner.core.BluetoothScannerManager
import com.spacetec.obd.scanner.core.DiscoveredScanner
import com.spacetec.obd.scanner.core.DiscoveryOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bluetooth scanner discovery implementation.
 *
 * Provides comprehensive Bluetooth device discovery for both Classic and BLE
 * OBD adapters with signal strength monitoring, device filtering, and caching.
 *
 * ## Features
 *
 * - Bluetooth Classic device discovery
 * - Bluetooth LE scanning with service UUID filtering
 * - Signal strength (RSSI) monitoring
 * - Device name filtering for OBD adapters
 * - Paired device enumeration
 * - Discovery result caching
 * - Automatic discovery timeout
 *
 * @param context Android application context
 *
 * @author SpaceTec Development Team
 * @since 1.0.0
 */
@Singleton
class BluetoothDiscoveryManager @Inject constructor(
    private val context: Context
) : BluetoothScannerManager {

    private val _isDiscovering = MutableStateFlow(false)
    override val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private val _discoveredDevices = MutableSharedFlow<DiscoveredScanner>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val discoveredDevices: SharedFlow<DiscoveredScanner> = _discoveredDevices.asSharedFlow()

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        } else {
            @Suppress("DEPRECATION")
            BluetoothAdapter.getDefaultAdapter()
        }
    }

    private val bleScanner: BluetoothLeScanner?
        get() = bluetoothAdapter?.bluetoothLeScanner

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var classicDiscoveryJob: Job? = null
    private var bleDiscoveryJob: Job? = null

    // Cache of discovered devices with their signal strengths
    private val discoveredCache = ConcurrentHashMap<String, DiscoveredScanner>()
    private val discoveredAddresses = mutableSetOf<String>()


    // ═══════════════════════════════════════════════════════════════════════
    // DISCOVERY IMPLEMENTATION
    // ═══════════════════════════════════════════════════════════════════════

    @SuppressLint("MissingPermission")
    override fun startDiscovery(options: DiscoveryOptions): Flow<DiscoveredScanner> = callbackFlow {
        val adapter = bluetoothAdapter

        if (adapter == null) {
            close(ConnectionException("Bluetooth not available"))
            return@callbackFlow
        }

        if (!adapter.isEnabled) {
            close(ConnectionException("Bluetooth is not enabled"))
            return@callbackFlow
        }

        if (!hasBluetoothPermissions()) {
            close(ConnectionException("Bluetooth permissions not granted"))
            return@callbackFlow
        }

        _isDiscovering.value = true
        discoveredAddresses.clear()

        // Emit paired devices first if requested
        if (options.includePaired) {
            emitPairedDevices(options) { discovered ->
                trySend(discovered)
                scope.launch { _discoveredDevices.emit(discovered) }
            }
        }

        // Start Classic Bluetooth discovery
        if (options.filterByType == null || options.filterByType == ScannerConnectionType.BLUETOOTH_CLASSIC) {
            startClassicDiscovery(options) { discovered ->
                trySend(discovered)
                scope.launch { _discoveredDevices.emit(discovered) }
            }
        }

        // Start BLE scanning
        if (options.filterByType == null || options.filterByType == ScannerConnectionType.BLUETOOTH_LE) {
            startBLEDiscovery(options) { discovered ->
                trySend(discovered)
                scope.launch { _discoveredDevices.emit(discovered) }
            }
        }

        // Auto-stop after scan duration
        scope.launch {
            delay(options.scanDuration)
            stopDiscovery()
        }

        awaitClose {
            scope.launch { stopDiscovery() }
        }
    }.flowOn(Dispatchers.IO)

    @SuppressLint("MissingPermission")
    private fun emitPairedDevices(
        options: DiscoveryOptions,
        onDiscovered: (DiscoveredScanner) -> Unit
    ) {
        try {
            bluetoothAdapter?.bondedDevices?.forEach { device ->
                val name = try { device.name } catch (e: SecurityException) { null }
                if (options.matchesFilter(name) || Scanner.looksLikeOBDAdapter(name)) {
                    val scanner = createScanner(device, null, isPaired = true)
                    val discovered = DiscoveredScanner(
                        scanner = scanner,
                        signalStrength = null,
                        isNew = false
                    )

                    if (discoveredAddresses.add(device.address)) {
                        discoveredCache[device.address] = discovered
                        onDiscovered(discovered)
                    }
                }
            }
        } catch (e: SecurityException) {
            // Permission issue, skip paired devices
        }
    }

    @SuppressLint("MissingPermission")
    private fun startClassicDiscovery(
        options: DiscoveryOptions,
        onDiscovered: (DiscoveredScanner) -> Unit
    ) {
        val adapter = bluetoothAdapter ?: return

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }

                        val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()

                        device?.let { handleClassicDeviceFound(it, rssi, options, onDiscovered) }
                    }

                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        // Discovery finished, but we might still be doing BLE scan
                    }
                }
            }
        }

        // Register receiver
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }

        // Cancel any ongoing discovery and start fresh
        try {
            if (adapter.isDiscovering) {
                adapter.cancelDiscovery()
            }
            adapter.startDiscovery()
        } catch (e: SecurityException) {
            // Permission denied
        }

        // Store job for cleanup
        classicDiscoveryJob = scope.launch {
            delay(options.scanDuration)
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {}
            try {
                adapter.cancelDiscovery()
            } catch (_: Exception) {}
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleClassicDeviceFound(
        device: BluetoothDevice,
        rssi: Int,
        options: DiscoveryOptions,
        onDiscovered: (DiscoveredScanner) -> Unit
    ) {
        try {
            val name = device.name

            // Check signal strength filter
            if (rssi != Short.MIN_VALUE.toInt() && rssi < options.minSignalStrength) {
                return
            }

            // Check name filter
            if (!options.matchesFilter(name) && !Scanner.looksLikeOBDAdapter(name)) {
                return
            }

            val isNew = discoveredAddresses.add(device.address)
            val isPaired = device.bondState == BluetoothDevice.BOND_BONDED

            val scanner = createScanner(device, rssi, isPaired = isPaired)
            val discovered = DiscoveredScanner(
                scanner = scanner,
                signalStrength = if (rssi != Short.MIN_VALUE.toInt()) rssi else null,
                isNew = isNew
            )

            // Update cache (may update signal strength for existing device)
            val existing = discoveredCache[device.address]
            if (existing == null || (rssi != Short.MIN_VALUE.toInt() && 
                (existing.signalStrength == null || rssi > existing.signalStrength))) {
                discoveredCache[device.address] = discovered
            }

            if (isNew || options.includePaired) {
                onDiscovered(discovered)
            }
        } catch (e: SecurityException) {
            // Skip device if we can't read its properties
        }
    }

    @SuppressLint("MissingPermission")
    private fun startBLEDiscovery(
        options: DiscoveryOptions,
        onDiscovered: (DiscoveredScanner) -> Unit
    ) {
        val scanner = bleScanner ?: return

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                handleBLEDeviceFound(result, options, onDiscovered)
            }

            override fun onBatchScanResults(results: List<ScanResult>) {
                results.forEach { result ->
                    handleBLEDeviceFound(result, options, onDiscovered)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                // BLE scan failed, continue with classic discovery
            }
        }

        // Build scan filters for OBD service UUIDs
        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(BluetoothLEConstants.OBD_SERVICE_UUID))
                .build(),
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(BluetoothLEConstants.OBD_SERVICE_UUID_ALT))
                .build()
        )

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()

        try {
            // Start with filters first, then without if no results
            scanner.startScan(filters, settings, callback)
        } catch (e: SecurityException) {
            // Permission denied
        }

        // Store job for cleanup
        bleDiscoveryJob = scope.launch {
            delay(options.scanDuration)
            try {
                scanner.stopScan(callback)
            } catch (_: Exception) {}
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleBLEDeviceFound(
        result: ScanResult,
        options: DiscoveryOptions,
        onDiscovered: (DiscoveredScanner) -> Unit
    ) {
        try {
            val device = result.device
            val name = result.scanRecord?.deviceName ?: device.name
            val rssi = result.rssi

            // Check signal strength filter
            if (rssi < options.minSignalStrength) {
                return
            }

            // Check name filter
            if (!options.matchesFilter(name) && !Scanner.looksLikeOBDAdapter(name)) {
                return
            }

            val isNew = discoveredAddresses.add(device.address)
            val isPaired = device.bondState == BluetoothDevice.BOND_BONDED

            val scanner = createBLEScanner(device, name, rssi, isPaired)
            val discovered = DiscoveredScanner(
                scanner = scanner,
                signalStrength = rssi,
                isNew = isNew
            )

            // Update cache
            val existing = discoveredCache[device.address]
            if (existing == null || rssi > (existing.signalStrength ?: Int.MIN_VALUE)) {
                discoveredCache[device.address] = discovered
            }

            if (isNew) {
                onDiscovered(discovered)
            }
        } catch (e: SecurityException) {
            // Skip device
        }
    }


    // ═══════════════════════════════════════════════════════════════════════
    // STOP DISCOVERY
    // ═══════════════════════════════════════════════════════════════════════

    @SuppressLint("MissingPermission")
    override suspend fun stopDiscovery() {
        try {
            bluetoothAdapter?.cancelDiscovery()
        } catch (_: SecurityException) {}

        classicDiscoveryJob?.cancel()
        classicDiscoveryJob = null

        bleDiscoveryJob?.cancel()
        bleDiscoveryJob = null

        _isDiscovering.value = false
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PAIRED DEVICES
    // ═══════════════════════════════════════════════════════════════════════

    @SuppressLint("MissingPermission")
    override suspend fun getPairedDevices(): List<Scanner> {
        if (!hasBluetoothPermissions()) {
            return emptyList()
        }

        return try {
            bluetoothAdapter?.bondedDevices
                ?.filter { device ->
                    val name = try { device.name } catch (e: SecurityException) { null }
                    Scanner.looksLikeOBDAdapter(name)
                }
                ?.map { device -> createScanner(device, null, isPaired = true) }
                ?: emptyList()
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // BLUETOOTH STATE
    // ═══════════════════════════════════════════════════════════════════════

    @SuppressLint("MissingPermission")
    override fun isBluetoothEnabled(): Boolean {
        return try {
            bluetoothAdapter?.isEnabled == true
        } catch (e: SecurityException) {
            false
        }
    }

    override suspend fun requestEnableBluetooth(): Boolean {
        // This would typically launch an intent for the user to enable Bluetooth
        // For now, just return current state
        return isBluetoothEnabled()
    }

    // ═══════════════════════════════════════════════════════════════════════
    // CACHE MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Gets all cached discovered devices.
     */
    fun getCachedDevices(): List<DiscoveredScanner> {
        return discoveredCache.values.toList()
    }

    /**
     * Gets a cached device by address.
     */
    fun getCachedDevice(address: String): DiscoveredScanner? {
        return discoveredCache[address]
    }

    /**
     * Clears the discovery cache.
     */
    fun clearCache() {
        discoveredCache.clear()
        discoveredAddresses.clear()
    }

    /**
     * Gets the best scanner (highest signal strength).
     */
    fun getBestScanner(): DiscoveredScanner? {
        return discoveredCache.values
            .filter { it.signalStrength != null }
            .maxByOrNull { it.signalStrength }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // UTILITIES
    // ═══════════════════════════════════════════════════════════════════════

    @SuppressLint("MissingPermission")
    private fun createScanner(
        device: BluetoothDevice,
        rssi: Int?,
        isPaired: Boolean
    ): Scanner {
        val name = try {
            device.name ?: "Unknown Device"
        } catch (e: SecurityException) {
            "Unknown Device"
        }

        val deviceType = Scanner.detectDeviceType(name)

        return Scanner(
            id = device.address,
            name = name,
            connectionType = ScannerConnectionType.BLUETOOTH_CLASSIC,
            deviceType = deviceType,
            address = device.address,
            signalStrength = rssi,
            isPaired = isPaired
        )
    }

    private fun createBLEScanner(
        device: BluetoothDevice,
        name: String?,
        rssi: Int,
        isPaired: Boolean
    ): Scanner {
        val displayName = name ?: "Unknown BLE Device"
        val deviceType = Scanner.detectDeviceType(displayName)

        return Scanner(
            id = device.address,
            name = displayName,
            connectionType = ScannerConnectionType.BLUETOOTH_LE,
            deviceType = deviceType,
            address = device.address,
            signalStrength = rssi,
            isPaired = isPaired
        )
    }

    private fun hasBluetoothPermissions(): Boolean {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            BluetoothClassicConstants.BLUETOOTH_PERMISSIONS
        } else {
            BluetoothClassicConstants.BLUETOOTH_PERMISSIONS
        }

        return permissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    companion object {
        /**
         * Checks if Bluetooth is available on the device.
         */
        fun isBluetoothAvailable(context: Context): Boolean {
            val bluetoothManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            } else {
                null
            }
            return bluetoothManager?.adapter != null ||
                   @Suppress("DEPRECATION") BluetoothAdapter.getDefaultAdapter() != null
        }

        /**
         * Checks if BLE is available on the device.
         */
        fun isBLEAvailable(context: Context): Boolean {
            return context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
        }
    }
}