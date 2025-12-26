package com.obdreader.data.bluetooth.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import com.spacetec.domain.repository.BluetoothConnection
import com.spacetec.domain.repository.BluetoothDeviceManager
import com.spacetec.domain.repository.ConnectionState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for BLE OBD adapter connections
 */
@Singleton
class BluetoothLEManager @Inject constructor(
    @ApplicationContext private val context: Context
) : BluetoothDeviceManager {
    
    private val bluetoothManager: BluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    
    private val bluetoothAdapter: BluetoothAdapter? =
        bluetoothManager.adapter
    
    private val scanner = bluetoothAdapter?.let { BLEScanner(it) }
    
    private var currentConnection: BLEConnection? = null
    
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    private val _connectedDevice = MutableStateFlow<BluetoothDevice?>(null)
    override val connectedDevice: StateFlow<BluetoothDevice?> = _connectedDevice.asStateFlow()
    
    override val isBluetoothEnabled: Boolean
        get() = bluetoothAdapter?.isEnabled == true
    
    override val isScanning: Boolean
        get() = scanner?.isScanning == true
    
    /**
     * Discover BLE devices
     */
    override fun discoverDevices(): Flow<List<BluetoothDevice>> {
        val bleScanner = scanner ?: throw BLEException.BluetoothDisabledException()
        
        return bleScanner.scanForDevices(filterOBDOnly = true).map { bleDevices ->
            bleDevices.map { it.device }
        }
    }
    
    /**
     * Discover BLE devices with additional info
     */
    fun discoverBLEDevices(filterOBDOnly: Boolean = true): Flow<List<BLEDevice>> {
        val bleScanner = scanner ?: throw BLEException.BluetoothDisabledException()
        return bleScanner.scanForDevices(filterOBDOnly = filterOBDOnly)
    }
    
    /**
     * Stop device discovery
     */
    override fun stopDiscovery() {
        scanner?.stopScan()
    }
    
    /**
     * Connect to a BLE device
     */
    @SuppressLint("MissingPermission")
    override suspend fun connect(device: BluetoothDevice): Result<BluetoothConnection> {
        // Disconnect existing connection
        disconnect()
        
        _connectionState.value = ConnectionState.Connecting
        
        return try {
            val connection = BLEConnection(context, device)
            val result = connection.connect()
            
            if (result.isSuccess) {
                currentConnection = connection
                _connectedDevice.value = device
                _connectionState.value = ConnectionState.Connected
                
                // Monitor connection state
                monitorConnectionState(connection)
                
                Result.success(connection)
            } else {
                _connectionState.value = ConnectionState.Error(
                    result.exceptionOrNull()?.message ?: "Connection failed"
                )
                result.map { connection }
            }
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.Error(e.message ?: "Connection failed")
            Result.failure(e)
        }
    }
    
    private fun monitorConnectionState(connection: BLEConnection) {
        kotlinx.coroutines.GlobalScope.launch {
            connection.getConnectionState().collect { bleState ->
                _connectionState.value = when (bleState) {
                    is BLEConnectionState.Disconnected -> ConnectionState.Disconnected
                    is BLEConnectionState.Scanning -> ConnectionState.Scanning
                    is BLEConnectionState.Connecting -> ConnectionState.Connecting
                    is BLEConnectionState.DiscoveringServices,
                    is BLEConnectionState.NegotiatingMTU,
                    is BLEConnectionState.EnablingNotifications -> ConnectionState.Initializing
                    is BLEConnectionState.Ready -> ConnectionState.Connected
                    is BLEConnectionState.Error -> ConnectionState.Error(bleState.exception.message ?: "Error")
                }
                
                if (bleState is BLEConnectionState.Disconnected) {
                    _connectedDevice.value = null
                }
            }
        }
    }
    
    /**
     * Disconnect from current device
     */
    override suspend fun disconnect() {
        currentConnection?.disconnect()
        currentConnection = null
        _connectedDevice.value = null
        _connectionState.value = ConnectionState.Disconnected
    }
    
    /**
     * Get current connection
     */
    override fun getConnection(): BluetoothConnection? = currentConnection
    
    /**
     * Get paired BLE devices
     */
    @SuppressLint("MissingPermission")
    override fun getPairedDevices(): List<BluetoothDevice> {
        return bluetoothAdapter?.bondedDevices
            ?.filter { it.type == BluetoothDevice.DEVICE_TYPE_LE || it.type == BluetoothDevice.DEVICE_TYPE_DUAL }
            ?.toList()
            ?: emptyList()
    }

    override fun discoverClassicDevices(): Flow<List<BluetoothDevice>> {
        // BLE manager doesn't discover classic devices
        return kotlinx.coroutines.flow.flowOf(emptyList())
    }

    override fun discoverBleDevices(): Flow<List<BluetoothDevice>> {
        return discoverDevices()
    }

    override suspend fun connectClassic(device: BluetoothDevice): Result<BluetoothConnection> {
        // BLE manager cannot connect to classic devices
        return Result.failure(Exception("BLE manager cannot connect to classic devices"))
    }

    override suspend fun connectBle(device: BluetoothDevice): Result<BluetoothConnection> {
        return connect(device)
    }

    override fun getPairedClassicDevices(): List<BluetoothDevice> {
        // BLE manager doesn't handle classic devices
        return emptyList()
    }

    override fun getPairedBleDevices(): List<BluetoothDevice> {
        return getPairedDevices()
    }
}
