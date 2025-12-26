package com.obdreader.data.bluetooth.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.util.Log
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * GATT callback handler with coroutine-based result delivery
 */
class BLEGattCallback : BluetoothGattCallback() {
    
    companion object {
        private const val TAG = "BLEGattCallback"
    }
    
    // Connection state
    private val _connectionState = MutableStateFlow<BLEConnectionState>(BLEConnectionState.Disconnected)
    val connectionState: StateFlow<BLEConnectionState> = _connectionState.asStateFlow()
    
    // Received data
    private val _receivedData = MutableSharedFlow<ByteArray>(extraBufferCapacity = 100)
    val receivedData: SharedFlow<ByteArray> = _receivedData.asSharedFlow()
    
    // Operation result channels
    val connectionResultChannel = Channel<Result<Unit>>(Channel.CONFLATED)
    val serviceDiscoveryChannel = Channel<Result<BluetoothGatt>>(Channel.CONFLATED)
    val mtuResultChannel = Channel<Result<Int>>(Channel.CONFLATED)
    val writeResultChannel = Channel<Result<Unit>>(Channel.CONFLATED)
    val readResultChannel = Channel<Result<ByteArray>>(Channel.CONFLATED)
    val descriptorWriteChannel = Channel<Result<Unit>>(Channel.CONFLATED)
    
    // Current GATT reference
    var gatt: BluetoothGatt? = null
        private set
    
    // Negotiated MTU
    var currentMtu: Int = BLEConfig.MIN_MTU
        private set
    
    @SuppressLint("MissingPermission")
    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        Log.d(TAG, "onConnectionStateChange: status=$status, newState=$newState")
        
        this.gatt = gatt
        
        when (newState) {
            BluetoothProfile.STATE_CONNECTED -> {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    _connectionState.value = BLEConnectionState.DiscoveringServices
                    connectionResultChannel.trySend(Result.success(Unit))
                } else {
                    val error = BLEException.ConnectionFailedException(status)
                    _connectionState.value = BLEConnectionState.Error(error)
                    connectionResultChannel.trySend(Result.failure(error))
                }
            }
            BluetoothProfile.STATE_DISCONNECTED -> {
                _connectionState.value = BLEConnectionState.Disconnected
                currentMtu = BLEConfig.MIN_MTU
                
                // Notify pending operations
                val error = BLEException.DisconnectedException()
                connectionResultChannel.trySend(Result.failure(error))
                serviceDiscoveryChannel.trySend(Result.failure(error))
                mtuResultChannel.trySend(Result.failure(error))
                writeResultChannel.trySend(Result.failure(error))
                readResultChannel.trySend(Result.failure(error))
                
                gatt.close()
                this.gatt = null
            }
        }
    }
    
    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        Log.d(TAG, "onServicesDiscovered: status=$status")
        
        if (status == BluetoothGatt.GATT_SUCCESS) {
            _connectionState.value = BLEConnectionState.NegotiatingMTU
            serviceDiscoveryChannel.trySend(Result.success(gatt))
        } else {
            val error = BLEException.ServiceDiscoveryFailedException(status)
            _connectionState.value = BLEConnectionState.Error(error)
            serviceDiscoveryChannel.trySend(Result.failure(error))
        }
    }
    
    override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
        Log.d(TAG, "onMtuChanged: mtu=$mtu, status=$status")
        
        if (status == BluetoothGatt.GATT_SUCCESS) {
            currentMtu = mtu
            _connectionState.value = BLEConnectionState.EnablingNotifications
            mtuResultChannel.trySend(Result.success(mtu))
        } else {
            // Use default MTU on failure
            currentMtu = BLEConfig.MIN_MTU
            mtuResultChannel.trySend(Result.failure(
                BLEException.MTUNegotiationFailedException(BLEConfig.PREFERRED_MTU, status)
            ))
        }
    }
    
    override fun onCharacteristicWrite(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        status: Int
    ) {
        Log.d(TAG, "onCharacteristicWrite: ${characteristic.uuid}, status=$status")
        
        if (status == BluetoothGatt.GATT_SUCCESS) {
            writeResultChannel.trySend(Result.success(Unit))
        } else {
            writeResultChannel.trySend(Result.failure(BLEException.WriteFailedException(status)))
        }
    }
    
    override fun onCharacteristicRead(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        status: Int
    ) {
        Log.d(TAG, "onCharacteristicRead: ${characteristic.uuid}, status=$status")
        
        if (status == BluetoothGatt.GATT_SUCCESS) {
            readResultChannel.trySend(Result.success(characteristic.value ?: ByteArray(0)))
        } else {
            readResultChannel.trySend(Result.failure(BLEException.ReadFailedException(status)))
        }
    }
    
    @Deprecated("Deprecated in API 33")
    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ) {
        Log.d(TAG, "onCharacteristicChanged: ${characteristic.uuid}")
        characteristic.value?.let { data ->
            _receivedData.tryEmit(data)
        }
    }
    
    // API 33+ version
    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ) {
        Log.d(TAG, "onCharacteristicChanged (API 33+): ${characteristic.uuid}")
        _receivedData.tryEmit(value)
    }
    
    override fun onDescriptorWrite(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        status: Int
    ) {
        Log.d(TAG, "onDescriptorWrite: ${descriptor.uuid}, status=$status")
        
        if (status == BluetoothGatt.GATT_SUCCESS) {
            descriptorWriteChannel.trySend(Result.success(Unit))
        } else {
            descriptorWriteChannel.trySend(Result.failure(
                BLEException.NotificationEnableFailedException(
                    descriptor.characteristic.uuid.toString(),
                    status
                )
            ))
        }
    }
    
    override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
        Log.d(TAG, "onReadRemoteRssi: rssi=$rssi, status=$status")
    }
    
    fun setReady() {
        _connectionState.value = BLEConnectionState.Ready
    }
    
    fun setError(error: BLEException) {
        _connectionState.value = BLEConnectionState.Error(error)
    }
    
    fun reset() {
        _connectionState.value = BLEConnectionState.Disconnected
        currentMtu = BLEConfig.MIN_MTU
        gatt = null
    }
}
