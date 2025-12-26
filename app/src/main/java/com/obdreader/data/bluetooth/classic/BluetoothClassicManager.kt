package com.obdreader.data.bluetooth.classic

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.spacetec.domain.repository.BluetoothConnection
import com.spacetec.domain.repository.BluetoothDeviceManager
import com.spacetec.domain.repository.ConnectionState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bluetooth Classic manager for handling classic Bluetooth OBD adapters.
 * This manager handles device discovery, connection, and communication for
 * classic Bluetooth devices using SPP/RFCOMM.
 */
@Singleton
class BluetoothClassicManager @Inject constructor(
    @ApplicationContext private val context: Context
) : BluetoothDeviceManager {

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private var currentConnection: BluetoothClassicConnection? = null

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _connectedDevice = MutableStateFlow<BluetoothDevice?>(null)
    override val connectedDevice: StateFlow<BluetoothDevice?> = _connectedDevice.asStateFlow()

    override val isBluetoothEnabled: Boolean
        get() = bluetoothAdapter?.isEnabled == true

    override val isScanning: Boolean = false // We'll implement this as needed

    // Channel for discovered devices
    private val discoveryChannel = Channel<List<BluetoothDevice>>(
        capacity = 100,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    override fun discoverDevices(): Flow<List<BluetoothDevice>> = discoverClassicDevicesFlow()

    /**
     * Starts classic Bluetooth device discovery and returns a flow of discovered devices.
     */
    @SuppressLint("MissingPermission")
    private fun discoverClassicDevicesFlow(): Flow<List<BluetoothDevice>> = callbackFlow {
        if (!hasBluetoothPermissions()) {
            close(SecurityException("Bluetooth permissions not granted"))
            return@callbackFlow
        }

        if (!isBluetoothEnabled) {
            close(Exception("Bluetooth is disabled"))
            return@callbackFlow
        }

        val receiver = object : BroadcastReceiver() {
            @SuppressLint("MissingPermission")
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }
                        device?.let { discoveredDevice ->
                            trySend(listOf(discoveredDevice))
                        }
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        // Discovery finished
                        close()
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }

        // Start discovery
        if (bluetoothAdapter?.startDiscovery() == true) {
            // Keep the flow alive until closed
        } else {
            close(Exception("Failed to start discovery"))
        }

        awaitClose {
            try {
                context.unregisterReceiver(receiver)
                bluetoothAdapter?.cancelDiscovery()
            } catch (e: Exception) {
                Timber.e(e, "Error unregistering receiver")
            }
        }
    }.flowOn(Dispatchers.IO)

    override fun discoverClassicDevices(): Flow<List<BluetoothDevice>> = discoverClassicDevicesFlow()

    override fun discoverBleDevices(): Flow<List<BluetoothDevice>> {
        // Classic manager doesn't discover BLE devices
        return kotlinx.coroutines.flow.emptyFlow() // Empty flow
    }

    override fun stopDiscovery() {
        // Cancel classic discovery
        try {
            bluetoothAdapter?.cancelDiscovery()
        } catch (e: SecurityException) {
            Timber.e(e, "Failed to cancel discovery - permissions issue")
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun connect(device: BluetoothDevice): Result<BluetoothConnection> {
        return connectClassic(device)
    }

    @SuppressLint("MissingPermission")
    override suspend fun connectClassic(device: BluetoothDevice): Result<BluetoothConnection> {
        // First disconnect any existing connection
        disconnect()

        // Check permissions
        if (!hasBluetoothPermissions()) {
            return Result.failure(SecurityException("Bluetooth permissions not granted"))
        }

        // Check if Bluetooth is enabled
        if (!isBluetoothEnabled) {
            return Result.failure(Exception("Bluetooth is disabled"))
        }

        _connectionState.value = ConnectionState.Connecting

        try {
            // Create a new classic connection instance
            val connection = BluetoothClassicConnection(context)
            connection.setDevice(device)
            
            // Store the connection
            currentConnection = connection

            // Connect to the device
            val result = connection.connect()
            
            if (result.isSuccess) {
                _connectedDevice.value = device
                _connectionState.value = ConnectionState.Connected

                // Monitor connection state changes
                monitorConnectionState(connection)

                return Result.success(connection)
            } else {
                _connectionState.value = ConnectionState.Error(result.exceptionOrNull()?.message ?: "Connection failed")
                result.exceptionOrNull()?.let { Timber.e(it, "Classic Bluetooth connection failed") }
                return Result.failure(result.exceptionOrNull() ?: Exception("Connection failed"))
            }
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.Error(e.message ?: "Connection failed")
            Timber.e(e, "Error during classic Bluetooth connection")
            return Result.failure(e)
        }
    }

    override suspend fun connectBle(device: BluetoothDevice): Result<BluetoothConnection> {
        // Classic manager cannot connect to BLE devices
        return Result.failure(Exception("Classic manager cannot connect to BLE devices"))
    }

    private fun monitorConnectionState(connection: BluetoothClassicConnection) {
        // This is a simplified monitoring - in a real implementation you'd want to
        // properly observe the connection state changes
        kotlinx.coroutines.GlobalScope.launch {
            while (connection.isConnected()) {
                delay(1000) // Check every second
                if (!connection.isConnected()) {
                    _connectionState.value = ConnectionState.Disconnected
                    _connectedDevice.value = null
                }
            }
        }
    }

    override suspend fun disconnect() {
        currentConnection?.disconnect()
        currentConnection = null
        _connectedDevice.value = null
        _connectionState.value = ConnectionState.Disconnected
    }

    override fun getConnection(): BluetoothConnection? = currentConnection

    override fun getPairedDevices(): List<BluetoothDevice> = getPairedClassicDevices()

    override fun getPairedClassicDevices(): List<BluetoothDevice> {
        if (!hasBluetoothPermissions()) {
            return emptyList()
        }
        
        return try {
            bluetoothAdapter?.bondedDevices
                ?.filter { device -> device.type == BluetoothDevice.DEVICE_TYPE_CLASSIC }
                ?.toList() ?: emptyList()
        } catch (e: SecurityException) {
            Timber.e(e, "Failed to get paired devices - permissions issue")
            emptyList()
        }
    }

    override fun getPairedBleDevices(): List<BluetoothDevice> {
        // Classic manager doesn't handle BLE devices
        return emptyList()
    }

    /**
     * Checks if the app has required Bluetooth permissions.
     */
    private fun hasBluetoothPermissions(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH
            ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_ADMIN
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Starts classic Bluetooth device discovery.
     */
    @SuppressLint("MissingPermission")
    fun startClassicDiscovery(): Result<Unit> {
        if (!hasBluetoothPermissions()) {
            return Result.failure(SecurityException("Bluetooth permissions not granted"))
        }

        if (!isBluetoothEnabled) {
            return Result.failure(Exception("Bluetooth is disabled"))
        }

        try {
            // Cancel any ongoing discovery
            if (bluetoothAdapter?.isDiscovering == true) {
                bluetoothAdapter?.cancelDiscovery()
            }

            // Start discovery
            val success = bluetoothAdapter?.startDiscovery() ?: false
            return if (success) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to start discovery"))
            }
        } catch (e: SecurityException) {
            Timber.e(e, "Failed to start discovery - permissions issue")
            return Result.failure(e)
        }
    }
}