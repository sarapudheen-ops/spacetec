package com.obdreader.data.bluetooth.ble

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * Wrapper for BLE device with additional scan information
 */
data class BLEDevice(
    val device: BluetoothDevice,
    val name: String?,
    val address: String,
    val rssi: Int,
    val isConnectable: Boolean,
    val serviceUuids: List<String>,
    val manufacturerData: Map<Int, ByteArray>,
    val lastSeen: Long = System.currentTimeMillis()
) {
    val displayName: String
        get() = name ?: "Unknown Device"
    
    val signalStrength: SignalStrength
        get() = when {
            rssi >= -50 -> SignalStrength.EXCELLENT
            rssi >= -60 -> SignalStrength.GOOD
            rssi >= -70 -> SignalStrength.FAIR
            rssi >= -80 -> SignalStrength.WEAK
            else -> SignalStrength.POOR
        }
    
    val isLikelyOBDAdapter: Boolean
        get() = name?.let { n ->
            OBD_DEVICE_NAMES.any { pattern -> 
                n.uppercase().contains(pattern.uppercase()) 
            }
        } ?: false || serviceUuids.any { uuid ->
            uuid.uppercase().startsWith("0000FFF0") ||
            uuid.uppercase().startsWith("0000FFE0")
        }
    
    companion object {
        private val OBD_DEVICE_NAMES = listOf(
            "OBD", "ELM", "V-LINK", "VLINK", "VEEPEAK", 
            "VGATE", "KONNWEI", "OBDII", "OBD2", "SCAN",
            "CARISTA", "BAFX", "BLUEDRIVER", "FIXD"
        )
        
        fun fromScanResult(result: ScanResult, context: android.content.Context? = null): BLEDevice {
            val scanRecord = result.scanRecord
            
            // Safely get device name with permission check
            val deviceName = if (context != null) {
                val hasPermission = ContextCompat.checkSelfPermission(
                    context, 
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
                if (hasPermission) {
                    try {
                        result.device.name
                    } catch (e: SecurityException) {
                        null
                    }
                } else {
                    null
                }
            } else {
                try {
                    result.device.name
                } catch (e: SecurityException) {
                    null
                }
            }
            
            return BLEDevice(
                device = result.device,
                name = deviceName ?: scanRecord?.deviceName,
                address = result.device.address,
                rssi = result.rssi,
                isConnectable = result.isConnectable,
                serviceUuids = scanRecord?.serviceUuids?.map { it.uuid.toString() } ?: emptyList(),
                manufacturerData = scanRecord?.manufacturerSpecificData?.let { data ->
                    (0 until data.size()).associate { i ->
                        data.keyAt(i) to data.valueAt(i)
                    }
                } ?: emptyMap()
            )
        }
    }
    
    enum class SignalStrength(val description: String, val bars: Int) {
        EXCELLENT("Excellent", 4),
        GOOD("Good", 3),
        FAIR("Fair", 2),
        WEAK("Weak", 1),
        POOR("Poor", 0)
    }
}
