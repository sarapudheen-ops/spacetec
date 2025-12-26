// scanner/bluetooth/src/main/kotlin/com/spacetec/automotive/scanner/bluetooth/BluetoothScannerFactory.kt
package com.spacetec.obd.scanner.bluetooth

import android.content.Context
import com.spacetec.obd.scanner.core.Scanner
import com.spacetec.obd.scanner.core.ScannerDevice
import com.spacetec.obd.scanner.core.ScannerType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Factory for creating Bluetooth scanner instances.
 */
@Singleton
class BluetoothScannerFactory @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    /**
     * Creates a scanner for the given device.
     */
    fun createScanner(device: ScannerDevice): Scanner {
        require(device.type == ScannerType.BLUETOOTH || device.type == ScannerType.BLUETOOTH_LE) {
            "Device type must be Bluetooth"
        }
        
        return when (device.type) {
            ScannerType.BLUETOOTH -> BluetoothScanner(context, device)
            ScannerType.BLUETOOTH_LE -> createBleScanner(device)
            else -> throw IllegalArgumentException("Unsupported device type: ${device.type}")
        }
    }
    
    private fun createBleScanner(device: ScannerDevice): Scanner {
        // BLE implementation would go here
        // For now, fall back to classic Bluetooth
        return BluetoothScanner(context, device)
    }
}