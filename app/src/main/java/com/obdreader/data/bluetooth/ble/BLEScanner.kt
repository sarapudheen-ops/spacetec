package com.obdreader.data.bluetooth.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.ParcelUuid
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Handles BLE device scanning with filtering for OBD adapters
 */
@Singleton
class BLEScanner @Inject constructor(
    private val bluetoothAdapter: BluetoothAdapter
) {
    private val discoveredDevices = ConcurrentHashMap<String, BLEDevice>()
    private var currentCallback: ScanCallback? = null
    
    val isScanning: Boolean
        get() = currentCallback != null
    
    private val scanner: BluetoothLeScanner?
        get() = bluetoothAdapter.bluetoothLeScanner
    
    /**
     * Scan for BLE devices as a Flow
     */
    @SuppressLint("MissingPermission")
    fun scanForDevices(
        filterOBDOnly: Boolean = true,
        scanDurationMs: Long = BLEConfig.SCAN_TIMEOUT_MS
    ): Flow<List<BLEDevice>> = callbackFlow {
        val bleScanner = scanner ?: throw BLEException.BluetoothDisabledException()
        
        discoveredDevices.clear()
        
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = BLEDevice.fromScanResult(result)
                
                if (!filterOBDOnly || device.isLikelyOBDAdapter) {
                    discoveredDevices[device.address] = device
                    trySend(discoveredDevices.values.toList().sortedByDescending { it.rssi })
                }
            }
            
            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { result ->
                    val device = BLEDevice.fromScanResult(result)
                    if (!filterOBDOnly || device.isLikelyOBDAdapter) {
                        discoveredDevices[device.address] = device
                    }
                }
                trySend(discoveredDevices.values.toList().sortedByDescending { it.rssi })
            }
            
            override fun onScanFailed(errorCode: Int) {
                close(BLEException.ScanFailedException(errorCode))
            }
        }
        
        currentCallback = callback
        
        val scanFilters = if (filterOBDOnly) buildScanFilters() else emptyList()
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
            .setReportDelay(0)
            .build()
        
        bleScanner.startScan(scanFilters, scanSettings, callback)
        
        // Auto-stop after duration
        kotlinx.coroutines.delay(scanDurationMs)
        
        awaitClose {
            stopScan()
        }
    }
    
    /**
     * Single-shot scan that returns discovered devices
     */
    @SuppressLint("MissingPermission")
    suspend fun scanOnce(
        durationMs: Long = BLEConfig.SCAN_TIMEOUT_MS,
        filterOBDOnly: Boolean = true
    ): List<BLEDevice> = suspendCancellableCoroutine { continuation ->
        val bleScanner = scanner ?: run {
            continuation.resumeWithException(BLEException.BluetoothDisabledException())
            return@suspendCancellableCoroutine
        }
        
        discoveredDevices.clear()
        
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = BLEDevice.fromScanResult(result)
                if (!filterOBDOnly || device.isLikelyOBDAdapter) {
                    discoveredDevices[device.address] = device
                }
            }
            
            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { result ->
                    val device = BLEDevice.fromScanResult(result)
                    if (!filterOBDOnly || device.isLikelyOBDAdapter) {
                        discoveredDevices[device.address] = device
                    }
                }
            }
            
            override fun onScanFailed(errorCode: Int) {
                if (continuation.isActive) {
                    continuation.resumeWithException(BLEException.ScanFailedException(errorCode))
                }
            }
        }
        
        currentCallback = callback
        
        val scanFilters = if (filterOBDOnly) buildScanFilters() else emptyList()
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .build()
        
        bleScanner.startScan(scanFilters, scanSettings, callback)
        
        // Schedule stop
        kotlinx.coroutines.GlobalScope.launch {
            kotlinx.coroutines.delay(durationMs)
            stopScan()
            if (continuation.isActive) {
                continuation.resume(discoveredDevices.values.toList().sortedByDescending { it.rssi })
            }
        }
        
        continuation.invokeOnCancellation {
            stopScan()
        }
    }
    
    /**
     * Stop ongoing scan
     */
    @SuppressLint("MissingPermission")
    fun stopScan() {
        currentCallback?.let { callback ->
            try {
                scanner?.stopScan(callback)
            } catch (e: Exception) {
                // Ignore stop errors
            }
            currentCallback = null
        }
    }
    
    /**
     * Get cached discovered devices
     */
    fun getDiscoveredDevices(): List<BLEDevice> {
        return discoveredDevices.values.toList().sortedByDescending { it.rssi }
    }
    
    /**
     * Clear discovered devices cache
     */
    fun clearCache() {
        discoveredDevices.clear()
    }
    
    private fun buildScanFilters(): List<ScanFilter> {
        val filters = mutableListOf<ScanFilter>()
        
        // Add filters for known OBD service UUIDs
        filters.add(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(BLEConfig.GATT_SERVICE_UUID))
                .build()
        )
        
        BLEConfig.ALT_SERVICE_UUIDS.forEach { uuid ->
            filters.add(
                ScanFilter.Builder()
                    .setServiceUuid(ParcelUuid(uuid))
                    .build()
            )
        }
        
        return filters
    }
}
