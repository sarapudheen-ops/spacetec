package com.obdreader.data.bluetooth.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.content.Context
import android.os.Build
import android.util.Log
import com.spacetec.domain.repository.BluetoothConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * BLE connection implementation for OBD communication
 */
class BLEConnection(
    private val context: Context,
    private val device: BluetoothDevice
) : BluetoothConnection {
    
    companion object {
        private const val TAG = "BLEConnection"
    }
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val gattCallback = BLEGattCallback()
    private var gatt: BluetoothGatt? = null
    
    private var txCharacteristic: BluetoothGattCharacteristic? = null
    private var rxCharacteristic: BluetoothGattCharacteristic? = null
    
    private val writeMutex = Mutex()
    private val writeQueue = ConcurrentLinkedQueue<WriteOperation>()
    private var pendingWriteCount = 0
    
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    private var connectionJob: Job? = null
    
    data class WriteOperation(
        val data: ByteArray,
        val result: Channel<Result<Unit>>
    )
    
    /**
     * Connect to the BLE device
     */
    @SuppressLint("MissingPermission")
    override suspend fun connect(): Result<Unit> {
        return try {
            Log.d(TAG, "Connecting to ${device.address}")
            
            // Connect to GATT server
            gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(
                    context,
                    false,
                    gattCallback,
                    BluetoothDevice.TRANSPORT_LE
                )
            } else {
                device.connectGatt(context, false, gattCallback)
            }
            
            // Wait for connection
            val connectionResult = withTimeout(BLEConfig.CONNECTION_TIMEOUT_MS) {
                gattCallback.connectionResultChannel.receive()
            }
            
            if (connectionResult.isFailure) {
                return connectionResult
            }
            
            // Discover services
            gatt?.discoverServices()
            val servicesResult = withTimeout(BLEConfig.SERVICE_DISCOVERY_TIMEOUT_MS) {
                gattCallback.serviceDiscoveryChannel.receive()
            }
            
            if (servicesResult.isFailure) {
                return Result.failure(servicesResult.exceptionOrNull() ?: Exception("Unknown error"))
            }
            
            // Find characteristics
            val setupResult = setupCharacteristics(servicesResult.getOrThrow())
            if (setupResult.isFailure) {
                return setupResult
            }
            
            // Request MTU
            requestMtu()
            
            // Enable notifications
            val notifyResult = enableNotifications()
            if (notifyResult.isFailure) {
                return notifyResult
            }
            
            gattCallback.setReady()
            _isConnected.value = true
            
            // Start write queue processor
            startWriteQueueProcessor()
            
            Log.d(TAG, "Connected successfully to ${device.address}")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Connection failed", e)
            disconnect()
            Result.failure(e)
        }
    }
    
    @SuppressLint("MissingPermission")
    private fun setupCharacteristics(gatt: BluetoothGatt): Result<Unit> {
        // Try primary service first
        var service = gatt.getService(BLEConfig.GATT_SERVICE_UUID)
        
        // Try alternative services
        if (service == null) {
            for (altUuid in BLEConfig.ALT_SERVICE_UUIDS) {
                service = gatt.getService(altUuid)
                if (service != null) break
            }
        }
        
        if (service == null) {
            return Result.failure(
                BLEException.ServiceNotFoundException(BLEConfig.GATT_SERVICE_UUID.toString())
            )
        }
        
        // Find TX characteristic
        txCharacteristic = service.getCharacteristic(BLEConfig.TX_CHAR_UUID)
            ?: findCharacteristicByProperty(service, BluetoothGattCharacteristic.PROPERTY_WRITE)
            ?: findCharacteristicByProperty(service, BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)
        
        if (txCharacteristic == null) {
            return Result.failure(
                BLEException.CharacteristicNotFoundException("TX (Write)")
            )
        }
        
        // Find RX characteristic
        rxCharacteristic = service.getCharacteristic(BLEConfig.RX_CHAR_UUID)
            ?: findCharacteristicByProperty(service, BluetoothGattCharacteristic.PROPERTY_NOTIFY)
            ?: findCharacteristicByProperty(service, BluetoothGattCharacteristic.PROPERTY_INDICATE)
        
        if (rxCharacteristic == null) {
            return Result.failure(
                BLEException.CharacteristicNotFoundException("RX (Notify)")
            )
        }
        
        Log.d(TAG, "Found TX: ${txCharacteristic?.uuid}, RX: ${rxCharacteristic?.uuid}")
        return Result.success(Unit)
    }
    
    private fun findCharacteristicByProperty(
        service: android.bluetooth.BluetoothGattService,
        property: Int
    ): BluetoothGattCharacteristic? {
        return service.characteristics.firstOrNull { char ->
            (char.properties and property) != 0
        }
    }
    
    @SuppressLint("MissingPermission")
    private suspend fun requestMtu() {
        val currentGatt = gatt ?: return
        
        try {
            currentGatt.requestMtu(BLEConfig.PREFERRED_MTU)
            withTimeoutOrNull(BLEConfig.MTU_REQUEST_TIMEOUT_MS) {
                gattCallback.mtuResultChannel.receive()
            }
            Log.d(TAG, "MTU negotiated: ${gattCallback.currentMtu}")
        } catch (e: Exception) {
            Log.w(TAG, "MTU negotiation failed, using default", e)
        }
    }
    
    @SuppressLint("MissingPermission")
    private suspend fun enableNotifications(): Result<Unit> {
        val currentGatt = gatt ?: return Result.failure(BLEException.DisconnectedException())
        val rxChar = rxCharacteristic ?: return Result.failure(
            BLEException.CharacteristicNotFoundException("RX")
        )
        
        // Enable local notifications
        if (!currentGatt.setCharacteristicNotification(rxChar, true)) {
            return Result.failure(
                BLEException.NotificationEnableFailedException(rxChar.uuid.toString(), -1)
            )
        }
        
        // Write to CCCD
        val descriptor = rxChar.getDescriptor(BLEConfig.CCCD_UUID)
        if (descriptor != null) {
            val value = if ((rxChar.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) {
                BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            } else {
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                currentGatt.writeDescriptor(descriptor, value)
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = value
                @Suppress("DEPRECATION")
                currentGatt.writeDescriptor(descriptor)
            }
            
            val result = withTimeout(BLEConfig.NOTIFICATION_TIMEOUT_MS) {
                gattCallback.descriptorWriteChannel.receive()
            }
            
            if (result.isFailure) {
                return result
            }
        }
        
        Log.d(TAG, "Notifications enabled for ${rxChar.uuid}")
        return Result.success(Unit)
    }
    
    /**
     * Send data to the BLE device
     */
    override suspend fun send(data: ByteArray): Result<Unit> {
        if (!_isConnected.value) {
            return Result.failure(BLEException.DisconnectedException())
        }
        
        // Fragment data if necessary
        val maxPayload = gattCallback.currentMtu - 3 // ATT header
        val fragments = data.toList().chunked(maxPayload).map { it.toByteArray() }
        
        for (fragment in fragments) {
            val result = sendFragment(fragment)
            if (result.isFailure) {
                return result
            }
        }
        
        return Result.success(Unit)
    }
    
    @SuppressLint("MissingPermission")
    private suspend fun sendFragment(data: ByteArray): Result<Unit> {
        val resultChannel = Channel<Result<Unit>>(Channel.CONFLATED)
        writeQueue.offer(WriteOperation(data, resultChannel))
        
        return withTimeout(BLEConfig.WRITE_TIMEOUT_MS * 2) {
            resultChannel.receive()
        }
    }
    
    private fun startWriteQueueProcessor() {
        connectionJob = scope.launch {
            while (isActive && _isConnected.value) {
                val operation = writeQueue.poll()
                if (operation != null) {
                    val result = performWrite(operation.data)
                    operation.result.trySend(result)
                } else {
                    delay(10)
                }
            }
        }
    }
    
    @SuppressLint("MissingPermission")
    private suspend fun performWrite(data: ByteArray): Result<Unit> = writeMutex.withLock {
        val currentGatt = gatt ?: return Result.failure(BLEException.DisconnectedException())
        val txChar = txCharacteristic ?: return Result.failure(
            BLEException.CharacteristicNotFoundException("TX")
        )
        
        try {
            // Determine write type
            val writeType = if ((txChar.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            } else {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val status = currentGatt.writeCharacteristic(txChar, data, writeType)
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    return Result.failure(BLEException.WriteFailedException(status))
                }
            } else {
                @Suppress("DEPRECATION")
                txChar.value = data
                @Suppress("DEPRECATION")
                txChar.writeType = writeType
                @Suppress("DEPRECATION")
                if (!currentGatt.writeCharacteristic(txChar)) {
                    return Result.failure(BLEException.WriteFailedException(-1))
                }
            }
            
            // Wait for write confirmation if not write-no-response
            if (writeType == BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) {
                val result = withTimeout(BLEConfig.WRITE_TIMEOUT_MS) {
                    gattCallback.writeResultChannel.receive()
                }
                return result
            }
            
            // Small delay for write-no-response
            delay(20)
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Write failed", e)
            Result.failure(e)
        }
    }
    
    /**
     * Receive data from the BLE device
     */
    override fun receive(): Flow<ByteArray> = gattCallback.receivedData
    
    /**
     * Disconnect from the BLE device
     */
    @SuppressLint("MissingPermission")
    override suspend fun disconnect() {
        Log.d(TAG, "Disconnecting from ${device.address}")
        
        connectionJob?.cancel()
        _isConnected.value = false
        
        try {
            gatt?.disconnect()
            delay(100)
            gatt?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error during disconnect", e)
        }
        
        gatt = null
        txCharacteristic = null
        rxCharacteristic = null
        gattCallback.reset()
        writeQueue.clear()
    }
    
    override fun isConnected(): Boolean = _isConnected.value
    
    /**
     * Get connection state flow
     */
    fun getConnectionState(): StateFlow<BLEConnectionState> = gattCallback.connectionState
    
    /**
     * Get current MTU
     */
    fun getCurrentMtu(): Int = gattCallback.currentMtu
    
    /**
     * Read RSSI
     */
    @SuppressLint("MissingPermission")
    fun readRssi(): Boolean = gatt?.readRemoteRssi() ?: false
}