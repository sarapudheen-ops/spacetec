package com.spacetec.domain.repository

import android.bluetooth.BluetoothDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface for Bluetooth connection implementations.
 */
interface BluetoothConnection {
    suspend fun send(data: ByteArray): Result<Unit>
    fun receive(): Flow<ByteArray>
    suspend fun connect(): Result<Unit>
    suspend fun disconnect()
    fun isConnected(): Boolean
}

/**
 * Connection states for Bluetooth devices.
 */
sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Scanning : ConnectionState()
    object Connecting : ConnectionState()
    object Initializing : ConnectionState()
    object Connected : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

/**
 * Interface for managing Bluetooth device connections.
 */
interface BluetoothDeviceManager {
    val connectionState: StateFlow<ConnectionState>
    val connectedDevice: StateFlow<BluetoothDevice?>
    val isBluetoothEnabled: Boolean
    val isScanning: Boolean

    fun discoverDevices(): Flow<List<BluetoothDevice>>
    fun discoverClassicDevices(): Flow<List<BluetoothDevice>> // For classic Bluetooth devices
    fun discoverBleDevices(): Flow<List<BluetoothDevice>>     // For BLE devices
    fun stopDiscovery()
    suspend fun connect(device: BluetoothDevice): Result<BluetoothConnection>
    suspend fun connectClassic(device: BluetoothDevice): Result<BluetoothConnection> // For classic connections
    suspend fun connectBle(device: BluetoothDevice): Result<BluetoothConnection>     // For BLE connections
    suspend fun disconnect()
    fun getConnection(): BluetoothConnection?
    fun getPairedDevices(): List<BluetoothDevice>
    fun getPairedClassicDevices(): List<BluetoothDevice> // For classic devices
    fun getPairedBleDevices(): List<BluetoothDevice>     // For BLE devices
}