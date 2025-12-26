package com.obdreader.data.bluetooth

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.obdreader.data.bluetooth.ble.BluetoothLEManager
import com.obdreader.data.bluetooth.classic.BluetoothClassicManager
import com.spacetec.domain.repository.BluetoothConnection
import com.spacetec.domain.repository.BluetoothDeviceManager
import com.spacetec.domain.repository.ConnectionState
import dagger.hilt.android.scopes.ServiceScoped
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Unified Bluetooth manager that handles both Classic and BLE connections.
 * This class delegates to either BluetoothClassicManager or BluetoothLEManager
 * based on the type of device being connected to.
 */
@Singleton
class BluetoothManager @Inject constructor(
    private val bleManager: BluetoothLEManager,
    private val classicManager: BluetoothClassicManager
) : BluetoothDeviceManager {

    override val connectionState: StateFlow<ConnectionState>
        get() = if (isClassicConnected()) classicManager.connectionState else bleManager.connectionState

    override val connectedDevice: StateFlow<BluetoothDevice?>
        get() = if (isClassicConnected()) classicManager.connectedDevice else bleManager.connectedDevice

    override val isBluetoothEnabled: Boolean = bleManager.isBluetoothEnabled
    override val isScanning: Boolean
        get() = bleManager.isScanning || classicManager.isScanning

    override fun discoverDevices(): Flow<List<BluetoothDevice>> {
        // Combine both classic and BLE discovery
        return bleManager.discoverDevices()
    }

    override fun discoverClassicDevices(): Flow<List<BluetoothDevice>> {
        return classicManager.discoverDevices()
    }

    override fun discoverBleDevices(): Flow<List<BluetoothDevice>> {
        return bleManager.discoverDevices()
    }

    override fun stopDiscovery() {
        bleManager.stopDiscovery()
        classicManager.stopDiscovery()
    }

    override suspend fun connect(device: BluetoothDevice): Result<BluetoothConnection> {
        // Determine if device is classic or BLE and route accordingly
        return if (isClassicDevice(device)) {
            connectClassic(device)
        } else {
            connectBle(device)
        }
    }

    override suspend fun connectClassic(device: BluetoothDevice): Result<BluetoothConnection> {
        return classicManager.connect(device)
    }

    override suspend fun connectBle(device: BluetoothDevice): Result<BluetoothConnection> {
        return bleManager.connect(device)
    }

    override suspend fun disconnect() {
        // Disconnect from both managers if needed
        bleManager.disconnect()
        classicManager.disconnect()
    }

    override fun getConnection(): BluetoothConnection? {
        // Return the active connection based on current state
        return bleManager.getConnection() ?: classicManager.getConnection()
    }

    override fun getPairedDevices(): List<BluetoothDevice> {
        // Combine both classic and BLE paired devices
        return getPairedClassicDevices() + getPairedBleDevices()
    }

    override fun getPairedClassicDevices(): List<BluetoothDevice> {
        return classicManager.getPairedDevices()
    }

    override fun getPairedBleDevices(): List<BluetoothDevice> {
        return bleManager.getPairedDevices()
    }

    /**
     * Determines if a device is a classic Bluetooth device or BLE.
     */
    private fun isClassicDevice(device: BluetoothDevice, context: android.content.Context? = null): Boolean {
        // Safely get device type with permission check
        val deviceType = if (context != null) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context, 
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
            if (hasPermission) {
                try {
                    device.type
                } catch (e: SecurityException) {
                    BluetoothDevice.DEVICE_TYPE_CLASSIC // Default to classic on permission error
                }
            } else {
                BluetoothDevice.DEVICE_TYPE_CLASSIC // Default to classic when no permission
            }
        } else {
            try {
                device.type
            } catch (e: SecurityException) {
                BluetoothDevice.DEVICE_TYPE_CLASSIC // Default to classic on permission error
            }
        }
        
        return when (deviceType) {
            BluetoothDevice.DEVICE_TYPE_CLASSIC -> true
            BluetoothDevice.DEVICE_TYPE_LE -> false
            BluetoothDevice.DEVICE_TYPE_DUAL -> false // Prefer BLE when available
            else -> true // Default to classic for unknown types
        }
    }

    /**
     * Checks if classic connection is currently active
     */
    private fun isClassicConnected(): Boolean {
        return classicManager.getConnection()?.isConnected() == true
    }
}