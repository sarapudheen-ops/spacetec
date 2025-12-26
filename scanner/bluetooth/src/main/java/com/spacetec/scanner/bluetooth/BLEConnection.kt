package com.spacetec.scanner.bluetooth

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import com.spacetec.obd.scanner.core.BaseScannerConnection
import com.spacetec.obd.scanner.core.ConnectionConfig
import com.spacetec.obd.scanner.core.ConnectionInfo
import com.spacetec.obd.scanner.core.ScannerDeviceType
import com.spacetec.core.common.exceptions.CommunicationException
import com.spacetec.core.common.exceptions.ConnectionException
import com.spacetec.core.domain.models.scanner.ScannerConnectionType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.UUID
import kotlin.math.min

class BLEConnection(
    private val context: Context,
    dispatcher: CoroutineDispatcher = Dispatchers.IO
) : BaseScannerConnection(dispatcher) {
    
    override val connectionType: ScannerConnectionType = ScannerConnectionType.BLUETOOTH_LE
    
    // BLE-specific UUIDs
    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
        val WRITE_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb")
        val NOTIFY_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")
        
        // Client Characteristic Configuration Descriptor UUID for notifications
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
    
    private var bluetoothGatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var notifyCharacteristic: BluetoothGattCharacteristic? = null
    
    // Buffer for received data
    private val receiveBuffer = mutableListOf<Byte>()
    private val bufferMutex = Mutex()
    
    // GATT callback for handling BLE events
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Timber.d("BLE device connected")
                    // Discover services after connection
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Timber.d("BLE device disconnected")
                    bluetoothGatt = null
                    writeCharacteristic = null
                    notifyCharacteristic = null
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Timber.d("GATT services discovered")
                
                // Find the service and characteristics
                val service = gatt.getService(SERVICE_UUID)
                if (service != null) {
                    writeCharacteristic = service.getCharacteristic(WRITE_CHARACTERISTIC_UUID)
                    notifyCharacteristic = service.getCharacteristic(NOTIFY_CHARACTERISTIC_UUID)
                    
                    if (notifyCharacteristic != null) {
                        // Enable notifications
                        gatt.setCharacteristicNotification(notifyCharacteristic, true)
                        
                        // Get the descriptor for notifications
                        val descriptor = notifyCharacteristic?.getDescriptor(CCCD_UUID)
                        if (descriptor != null) {
                            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            gatt.writeDescriptor(descriptor)
                        }
                    }
                }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == NOTIFY_CHARACTERISTIC_UUID) {
                val data = characteristic.value
                if (data != null) {
                    // Add received data to buffer
                    bufferMutex.withLock {
                        receiveBuffer.addAll(data.toList())
                    }
                    // Notify any waiting readers
                    dataReceivedNotifier()
                }
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            // Handle write completion
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            Timber.d("Notification enabled: ${status == BluetoothGatt.GATT_SUCCESS}")
        }
    }
    
    override suspend fun doConnect(address: String, config: ConnectionConfig): ConnectionInfo {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            throw ConnectionException("Bluetooth is not available or enabled")
        }
        
        val device = bluetoothAdapter.getRemoteDevice(address)
        
        // Connect to the device
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
        
        // Wait for connection to be established
        var attempts = 0
        val maxAttempts = 20 // 20 * 500ms = 10 seconds timeout
        while (attempts < maxAttempts) {
            if (bluetoothGatt?.connectionState == BluetoothProfile.STATE_CONNECTED) {
                break
            }
            delay(500)
            attempts++
        }
        
        if (bluetoothGatt?.connectionState != BluetoothProfile.STATE_CONNECTED) {
            throw ConnectionException("Failed to connect to BLE device within timeout")
        }
        
        // Wait for services to be discovered
        attempts = 0
        while (attempts < maxAttempts && (writeCharacteristic == null || notifyCharacteristic == null)) {
            delay(500)
            attempts++
        }
        
        if (writeCharacteristic == null || notifyCharacteristic == null) {
            throw ConnectionException("Failed to discover required BLE characteristics")
        }
        
        return ConnectionInfo(
            address = address,
            name = device.name ?: address,
            type = ScannerDeviceType.BLUETOOTH_LE,
            rssi = 0 // RSSI would be obtained from scan results
        )
    }
    
    override suspend fun doDisconnect(graceful: Boolean) {
        try {
            bluetoothGatt?.let { gatt ->
                gatt.disconnect()
                gatt.close()
            }
        } finally {
            bluetoothGatt = null
            writeCharacteristic = null
            notifyCharacteristic = null
            bufferMutex.withLock {
                receiveBuffer.clear()
            }
        }
    }
    
    override suspend fun doWrite(data: ByteArray): Int {
        val characteristic = writeCharacteristic ?: throw CommunicationException("Write characteristic not available")
        val gatt = bluetoothGatt ?: throw CommunicationException("BLE GATT not connected")
        
        // Set the value to write
        characteristic.value = data
        
        // Write the characteristic
        val writeSuccess = gatt.writeCharacteristic(characteristic)
        
        if (!writeSuccess) {
            throw CommunicationException("Failed to write to BLE characteristic")
        }
        
        // Wait for write completion (simplified approach)
        delay(50) // Small delay to allow write to complete
        
        return data.size
    }
    
    override suspend fun doRead(buffer: ByteArray, timeout: Long): Int {
        val startTime = System.currentTimeMillis()
        
        while (System.currentTimeMillis() - startTime < timeout) {
            val data = bufferMutex.withLock {
                if (receiveBuffer.isNotEmpty()) {
                    val size = minOf(buffer.size, receiveBuffer.size)
                    for (i in 0 until size) {
                        buffer[i] = receiveBuffer.removeAt(0)
                    }
                    size
                } else {
                    -1 // No data available yet
                }
            }
            
            if (data != -1) {
                return data
            }
            
            delay(10) // Small delay to prevent busy waiting
        }
        
        return 0 // Timeout - no data received
    }
    
    override suspend fun doAvailable(): Int {
        return bufferMutex.withLock { receiveBuffer.size }
    }
    
    override suspend fun doClearBuffers() {
        bufferMutex.withLock {
            receiveBuffer.clear()
        }
    }
}