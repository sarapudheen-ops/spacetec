// scanner/bluetooth/src/main/kotlin/com/spacetec/automotive/scanner/bluetooth/BluetoothScannerDiscovery.kt
package com.spacetec.obd.scanner.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.spacetec.obd.core.common.result.AppResult
import com.spacetec.obd.core.common.result.Result
import com.spacetec.obd.core.common.result.SpaceTecError
import com.spacetec.obd.scanner.core.ScanConfig
import com.spacetec.obd.scanner.core.ScannerDevice
import com.spacetec.obd.scanner.core.ScannerDiscovery
import com.spacetec.obd.scanner.core.ScannerType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bluetooth device discovery implementation.
 * 
 * Discovers available Bluetooth OBD-II scanners using both
 * paired device enumeration and active Bluetooth discovery.
 */
@Singleton
class BluetoothScannerDiscovery @Inject constructor(
    @ApplicationContext private val context: Context
) : ScannerDiscovery {
    
    override val scannerType: ScannerType = ScannerType.BLUETOOTH
    
    private val _isScanning = MutableStateFlow(false)
    override val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()
    
    private val _discoveredDevices = MutableStateFlow<List<ScannerDevice>>(emptyList())
    override val discoveredDevices: StateFlow<List<ScannerDevice>> = 
        _discoveredDevices.asStateFlow()
    
    private val bluetoothManager: BluetoothManager? = 
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    
    private var discoveryReceiver: BroadcastReceiver? = null
    
    // ========================================================================
    // DISCOVERY OPERATIONS
    // ========================================================================
    
    @SuppressLint("MissingPermission")
    override suspend fun startScan(config: ScanConfig): AppResult<Unit> {
        if (!hasRequiredPermissions()) {
            return Result.failure(SpaceTecError.ConnectionError.PermissionDenied(
                message = "Bluetooth permissions not granted"
            ))
        }
        
        val adapter = bluetoothAdapter ?: return Result.failure(
            SpaceTecError.ConnectionError.BluetoothDisabled(
                message = "Bluetooth not available"
            )
        )
        
        if (!adapter.isEnabled) {
            return Result.failure(SpaceTecError.ConnectionError.BluetoothDisabled())
        }
        
        _isScanning.value = true
        _discoveredDevices.value = emptyList()
        
        // First, get paired devices
        if (config.includePaired) {
            try {
                val pairedDevices = adapter.bondedDevices?.mapNotNull { device ->
                    device.toScannerDevice(isPaired = true)
                }?.filter { device ->
                    matchesConfig(device, config)
                } ?: emptyList()
                
                _discoveredDevices.value = pairedDevices
                Timber.d("Found ${pairedDevices.size} paired devices")
            } catch (e: SecurityException) {
                Timber.e(e, "Cannot access paired devices")
            }
        }
        
        // Start active discovery
        try {
            registerDiscoveryReceiver()
            
            if (adapter.isDiscovering) {
                adapter.cancelDiscovery()
            }
            
            val started = adapter.startDiscovery()
            if (!started) {
                Timber.w("Failed to start Bluetooth discovery")
            } else {
                Timber.d("Bluetooth discovery started")
            }
            
            return Result.success(Unit)
        } catch (e: SecurityException) {
            _isScanning.value = false
            return Result.failure(SpaceTecError.ConnectionError.PermissionDenied())
        }
    }
    
    @SuppressLint("MissingPermission")
    override suspend fun stopScan() {
        _isScanning.value = false
        
        try {
            bluetoothAdapter?.cancelDiscovery()
        } catch (e: SecurityException) {
            Timber.w(e, "Cannot cancel discovery")
        }
        
        unregisterDiscoveryReceiver()
        Timber.d("Bluetooth discovery stopped")
    }
    
    override fun scanAsFlow(config: ScanConfig, duration: Long): Flow<ScannerDevice> = callbackFlow {
        val devices = mutableSetOf<String>()
        
        val receiver = object : BroadcastReceiver() {
            @SuppressLint("MissingPermission")
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(
                                BluetoothDevice.EXTRA_DEVICE,
                                BluetoothDevice::class.java
                            )
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }
                        
                        val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, 0).toInt()
                        
                        device?.let { btDevice ->
                            if (btDevice.address !in devices) {
                                devices.add(btDevice.address)
                                btDevice.toScannerDevice(rssi = rssi)?.let { scannerDevice ->
                                    if (matchesConfig(scannerDevice, config)) {
                                        trySend(scannerDevice)
                                    }
                                }
                            }
                        }
                    }
                    
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        Timber.d("Discovery finished")
                    }
                }
            }
        }
        
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        
        context.registerReceiver(receiver, filter)
        
        try {
            bluetoothAdapter?.startDiscovery()
        } catch (e: SecurityException) {
            Timber.e(e, "Cannot start discovery")
        }
        
        // Auto-stop after duration
        kotlinx.coroutines.delay(duration)
        
        awaitClose {
            try {
                bluetoothAdapter?.cancelDiscovery()
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {
                Timber.w(e, "Error stopping scan")
            }
        }
    }
    
    @SuppressLint("MissingPermission")
    override suspend fun getKnownDevices(): AppResult<List<ScannerDevice>> {
        if (!hasRequiredPermissions()) {
            return Result.failure(SpaceTecError.ConnectionError.PermissionDenied())
        }
        
        val adapter = bluetoothAdapter ?: return Result.success(emptyList())
        
        return try {
            val devices = adapter.bondedDevices?.mapNotNull { device ->
                device.toScannerDevice(isPaired = true)
            } ?: emptyList()
            
            Result.success(devices)
        } catch (e: SecurityException) {
            Result.failure(SpaceTecError.ConnectionError.PermissionDenied())
        }
    }
    
    // ========================================================================
    // RECEIVER MANAGEMENT
    // ========================================================================
    
    private fun registerDiscoveryReceiver() {
        if (discoveryReceiver != null) return
        
        discoveryReceiver = object : BroadcastReceiver() {
            @SuppressLint("MissingPermission")
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(
                                BluetoothDevice.EXTRA_DEVICE,
                                BluetoothDevice::class.java
                            )
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }
                        
                        val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, 0).toInt()
                        
                        device?.toScannerDevice(rssi = rssi)?.let { scannerDevice ->
                            addDiscoveredDevice(scannerDevice)
                        }
                    }
                    
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        _isScanning.value = false
                        Timber.d("Bluetooth discovery finished")
                    }
                }
            }
        }
        
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        
        context.registerReceiver(discoveryReceiver, filter)
    }
    
    private fun unregisterDiscoveryReceiver() {
        discoveryReceiver?.let { receiver ->
            try {
                context.unregisterReceiver(receiver)
            } catch (e: IllegalArgumentException) {
                Timber.w("Receiver not registered")
            }
        }
        discoveryReceiver = null
    }
    
    private fun addDiscoveredDevice(device: ScannerDevice) {
        val currentDevices = _discoveredDevices.value.toMutableList()
        
        // Update existing or add new
        val existingIndex = currentDevices.indexOfFirst { it.address == device.address }
        if (existingIndex >= 0) {
            currentDevices[existingIndex] = device
        } else {
            currentDevices.add(device)
        }
        
        _discoveredDevices.value = currentDevices.sortedByDescending { it.signalStrength }
        Timber.d("Discovered device: ${device.name} (${device.address})")
    }
    
    // ========================================================================
    // UTILITY METHODS
    // ========================================================================
    
    @SuppressLint("MissingPermission")
    private fun BluetoothDevice.toScannerDevice(
        isPaired: Boolean = false,
        rssi: Int = 0
    ): ScannerDevice? {
        val deviceName = try {
            name ?: return null
        } catch (e: SecurityException) {
            return null
        }
        
        return ScannerDevice(
            id = address,
            name = deviceName,
            type = ScannerType.BLUETOOTH,
            address = address,
            signalStrength = rssi,
            isPaired = isPaired || bondState == BluetoothDevice.BOND_BONDED,
            isKnown = bondState == BluetoothDevice.BOND_BONDED
        )
    }
    
    private fun matchesConfig(device: ScannerDevice, config: ScanConfig): Boolean {
        // Filter by name
        config.filterByName?.let { filter ->
            if (!device.name.contains(filter, ignoreCase = true)) {
                return false
            }
        }
        
        // Filter by address
        config.filterByAddress?.let { filter ->
            if (!device.address.contains(filter, ignoreCase = true)) {
                return false
            }
        }
        
        // Filter out unnamed devices
        if (!config.includeUnnamed && device.name.isBlank()) {
            return false
        }
        
        // Only OBD devices
        if (config.onlyObdDevices && !device.looksLikeObdAdapter) {
            return false
        }
        
        // Signal threshold
        if (device.signalStrength != 0 && device.signalStrength < config.signalThreshold) {
            return false
        }
        
        return true
    }
    
    // ========================================================================
    // PERMISSIONS & AVAILABILITY
    // ========================================================================
    
    override fun hasRequiredPermissions(): Boolean {
        return getRequiredPermissions().all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == 
                PackageManager.PERMISSION_GRANTED
        }
    }
    
    override fun getRequiredPermissions(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            listOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }
    
    override fun isAvailable(): Boolean {
        return bluetoothAdapter != null
    }
    
    @SuppressLint("MissingPermission")
    override fun isEnabled(): Boolean {
        return try {
            bluetoothAdapter?.isEnabled == true
        } catch (e: SecurityException) {
            false
        }
    }
}