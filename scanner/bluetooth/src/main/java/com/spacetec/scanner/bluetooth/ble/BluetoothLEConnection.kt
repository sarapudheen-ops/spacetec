/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 *
 * This file is part of the SpaceTec professional automotive diagnostic system.
 */

package com.spacetec.obd.scanner.bluetooth.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.spacetec.core.common.exceptions.CommunicationException
import com.spacetec.core.common.exceptions.ConnectionException
import com.spacetec.core.domain.models.scanner.ScannerConnectionType
import com.spacetec.obd.scanner.core.BaseScannerConnection
import com.spacetec.obd.scanner.core.ConnectionConfig
import com.spacetec.obd.scanner.core.ConnectionInfo
import com.spacetec.obd.scanner.core.ConnectionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

/**
 * Bluetooth Low Energy (BLE) GATT connection implementation.
 *
 * Provides BLE GATT client functionality for connecting to OBD-II adapters
 * that use Bluetooth Low Energy, such as Veepeak BLE and similar devices.
 *
 * ## Features
 *
 * - GATT client with service and characteristic discovery
 * - Notification/indication handling for asynchronous data
 * - MTU negotiation for optimal data transfer
 * - Data fragmentation handling for large payloads
 * - Automatic reconnection on connection loss
 * - Thread-safe operations
 *
 * ## BLE OBD Adapter Protocol
 *
 * Most BLE OBD adapters use a custom GATT service with:
 * - A write characteristic for sending commands
 * - A notify characteristic for receiving responses
 *
 * Common UUIDs:
 * - Service: 0000fff0-0000-1000-8000-00805f9b34fb
 * - Write: 0000fff2-0000-1000-8000-00805f9b34fb
 * - Notify: 0000fff1-0000-1000-8000-00805f9b34fb
 *
 * @param context Android context for accessing Bluetooth services
 * @param bleConfig BLE-specific configuration
 * @param dispatcher Coroutine dispatcher for I/O operations
 *
 * @author SpaceTec Development Team
 * @since 1.0.0
 */
class BluetoothLEConnection @Inject constructor(
    private val context: Context,
    private var bleConfig: BluetoothLEConfig = BluetoothLEConfig.DEFAULT,
    dispatcher: CoroutineDispatcher = Dispatchers.IO
) : BaseScannerConnection(dispatcher) {

    override val connectionType: ScannerConnectionType = ScannerConnectionType.BLUETOOTH_LE

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        } else {
            @Suppress("DEPRECATION")
            BluetoothAdapter.getDefaultAdapter()
        }
    }

    private var gatt: BluetoothGatt? = null
    private var device: BluetoothDevice? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var notifyCharacteristic: BluetoothGattCharacteristic? = null

    private val gattLock = Mutex()
    private val writeLock = Mutex()
    
    // FIX: Use AtomicReference to reset CompletableDeferred for each connection attempt
    private val connectionDeferred = AtomicReference<CompletableDeferred<Boolean>?>(null)
    private val servicesDiscoveredDeferred = AtomicReference<CompletableDeferred<Boolean>?>(null)
    private val mtuNegotiatedDeferred = AtomicReference<CompletableDeferred<Int>?>(null)
    private val writeDeferred = AtomicReference<CompletableDeferred<Boolean>?>(null)

    private val dataChannel = Channel<ByteArray>(Channel.BUFFERED)

    private var currentMtu = BluetoothLEConstants.DEFAULT_MTU
    private val isNotificationsEnabled = AtomicBoolean(false)
    private val connectionAttempt = AtomicInteger(0)

    /**
     * Creates fresh deferreds for a new connection attempt.
     */
    private fun resetDeferreds() {
        connectionDeferred.set(CompletableDeferred())
        servicesDiscoveredDeferred.set(CompletableDeferred())
        mtuNegotiatedDeferred.set(CompletableDeferred())
    }

    /**
     * Cancels all pending deferreds.
     */
    private fun cancelDeferreds() {
        connectionDeferred.getAndSet(null)?.cancel()
        servicesDiscoveredDeferred.getAndSet(null)?.cancel()
        mtuNegotiatedDeferred.getAndSet(null)?.cancel()
    }


    // ═══════════════════════════════════════════════════════════════════════
    // GATT CALLBACK
    // ═══════════════════════════════════════════════════════════════════════

    private val gattCallback = object : BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    scope.launch {
                        // Complete the connection deferred
                        connectionDeferred.get()?.complete(status == BluetoothGatt.GATT_SUCCESS)
                        
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            // Discover services on the GATT callback thread
                            // This ensures proper sequencing
                            gatt.discoverServices()
                        }
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    scope.launch {
                        // Complete the connection deferred with false
                        connectionDeferred.get()?.complete(false)
                        handleDisconnection()
                    }
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // Find service and characteristics synchronously in callback
                val service = findOBDService(gatt)
                if (service != null) {
                    writeCharacteristic = findWriteCharacteristic(service)
                    notifyCharacteristic = findNotifyCharacteristic(service)

                    if (writeCharacteristic != null && notifyCharacteristic != null) {
                        // Enable notifications before completing
                        enableNotificationsSync(gatt, notifyCharacteristic!!)
                        servicesDiscoveredDeferred.get()?.complete(true)
                    } else {
                        servicesDiscoveredDeferred.get()?.complete(false)
                    }
                } else {
                    servicesDiscoveredDeferred.get()?.complete(false)
                }
            } else {
                servicesDiscoveredDeferred.get()?.complete(false)
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                currentMtu = mtu
            }
            mtuNegotiatedDeferred.get()?.complete(if (status == BluetoothGatt.GATT_SUCCESS) mtu else currentMtu)
        }

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == notifyCharacteristic?.uuid) {
                val data = characteristic.value
                if (data != null && data.isNotEmpty()) {
                    scope.launch {
                        dataChannel.send(data)
                        processIncomingData(data)
                    }
                }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid == notifyCharacteristic?.uuid) {
                if (value.isNotEmpty()) {
                    scope.launch {
                        dataChannel.send(value)
                        processIncomingData(value)
                    }
                }
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            writeDeferred.get()?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (descriptor.uuid == BluetoothLEConstants.CLIENT_CHARACTERISTIC_CONFIG_UUID) {
                isNotificationsEnabled.set(status == BluetoothGatt.GATT_SUCCESS)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // CONNECTION IMPLEMENTATION
    // ═══════════════════════════════════════════════════════════════════════

    @SuppressLint("MissingPermission")
    override suspend fun doConnect(
        address: String,
        config: ConnectionConfig
    ): ConnectionInfo = withContext(dispatcher) {

        val adapter = bluetoothAdapter
            ?: throw ConnectionException("Bluetooth not available on this device")

        if (!adapter.isEnabled) {
            throw ConnectionException("Bluetooth is not enabled")
        }

        if (!BluetoothAdapter.checkBluetoothAddress(address)) {
            throw ConnectionException("Invalid Bluetooth address: $address")
        }

        if (!hasBluetoothPermissions()) {
            throw ConnectionException("Bluetooth permissions not granted")
        }

        device = adapter.getRemoteDevice(address)
        val btDevice = device ?: throw ConnectionException("Device not found: $address")

        var lastException: Exception? = null
        var attempt = 0

        while (attempt < BluetoothLEConstants.MAX_CONNECTION_RETRIES) {
            attempt++
            connectionAttempt.set(attempt)

            try {
                // CRITICAL: Reset deferreds for new connection
                resetDeferreds()

                // Connect to GATT server
                gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    btDevice.connectGatt(
                        context,
                        false,
                        gattCallback,
                        BluetoothDevice.TRANSPORT_LE
                    )
                } else {
                    btDevice.connectGatt(context, false, gattCallback)
                }

                // Wait for connection with timeout
                val connected = withTimeoutOrNull(config.connectionTimeout) {
                    connectionDeferred.get()?.await() ?: false
                } ?: false

                if (!connected) {
                    throw ConnectionException("GATT connection timeout")
                }

                // Wait for service discovery
                val servicesFound = withTimeoutOrNull(bleConfig.serviceDiscoveryTimeout) {
                    servicesDiscoveredDeferred.get()?.await() ?: false
                } ?: false

                if (!servicesFound) {
                    throw ConnectionException("Service discovery failed - OBD service not found")
                }

                // Negotiate MTU
                if (bleConfig.requestMtu > BluetoothLEConstants.DEFAULT_MTU) {
                    gatt?.requestMtu(bleConfig.requestMtu)
                    withTimeoutOrNull(BluetoothLEConstants.MTU_NEGOTIATION_TIMEOUT) {
                        mtuNegotiatedDeferred.get()?.await()
                    }
                }

                return@withContext ConnectionInfo(
                    connectedAt = System.currentTimeMillis(),
                    remoteAddress = address,
                    mtu = currentMtu,
                    connectionType = ScannerConnectionType.BLUETOOTH_LE
                )

            } catch (e: CancellationException) {
                cleanupGatt()
                throw e
            } catch (e: Exception) {
                lastException = e
                cleanupGatt()

                if (attempt < BluetoothLEConstants.MAX_CONNECTION_RETRIES) {
                    delay(BluetoothLEConstants.CONNECTION_RETRY_DELAY * attempt)
                }
            }
        }

        throw ConnectionException(
            "Failed to connect after $attempt attempts: ${lastException?.message}",
            lastException
        )
    }

    @SuppressLint("MissingPermission")
    override suspend fun doDisconnect(graceful: Boolean) = withContext(dispatcher) {
        gattLock.withLock {
            cancelDeferreds()

            if (graceful) {
                // Disable notifications before disconnecting
                notifyCharacteristic?.let { char ->
                    gatt?.let { g -> disableNotificationsSync(g, char) }
                }
                delay(100)
            }

            cleanupGatt()
        }
        device = null
    }

    @SuppressLint("MissingPermission")
    private fun cleanupGatt() {
        try {
            gatt?.disconnect()
        } catch (_: Exception) {}

        try {
            gatt?.close()
        } catch (_: Exception) {}

        gatt = null
        writeCharacteristic = null
        notifyCharacteristic = null
        isNotificationsEnabled.set(false)
    }


    // ═══════════════════════════════════════════════════════════════════════
    // DATA TRANSFER
    // ═══════════════════════════════════════════════════════════════════════

    @SuppressLint("MissingPermission")
    override suspend fun doWrite(data: ByteArray): Int = writeLock.withLock {
        val characteristic = writeCharacteristic
            ?: throw CommunicationException("Write characteristic not available")

        val gattConnection = gatt
            ?: throw CommunicationException("GATT connection not available")

        // Fragment data if larger than MTU
        val maxPayload = currentMtu - BluetoothLEConstants.ATT_HEADER_SIZE
        val chunks = data.toList().chunked(maxPayload).map { it.toByteArray() }

        var totalWritten = 0

        for ((index, chunk) in chunks.withIndex()) {
            // Create fresh deferred for this write
            val currentWriteDeferred = CompletableDeferred<Boolean>()
            writeDeferred.set(currentWriteDeferred)

            // Set characteristic value and write
            val writeResult = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gattConnection.writeCharacteristic(
                    characteristic,
                    chunk,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                )
            } else {
                @Suppress("DEPRECATION")
                characteristic.value = chunk
                @Suppress("DEPRECATION")
                if (gattConnection.writeCharacteristic(characteristic)) {
                    BluetoothGatt.GATT_SUCCESS
                } else {
                    BluetoothGatt.GATT_FAILURE
                }
            }

            if (writeResult != BluetoothGatt.GATT_SUCCESS) {
                throw CommunicationException("Write initiation failed: $writeResult")
            }

            // Wait for write confirmation
            val success = withTimeoutOrNull(config.writeTimeout) {
                currentWriteDeferred.await()
            } ?: throw CommunicationException("Write confirmation timeout")

            if (!success) {
                throw CommunicationException("Write failed for chunk ${index + 1}/${chunks.size}")
            }

            totalWritten += chunk.size

            // Flow control delay between chunks
            if (index < chunks.size - 1) {
                delay(BluetoothLEConstants.CHUNK_WRITE_DELAY)
            }
        }

        return totalWritten
    }

    override suspend fun doRead(buffer: ByteArray, timeout: Long): Int {
        val data = withTimeoutOrNull(timeout) {
            dataChannel.receive()
        } ?: return 0

        val bytesToCopy = minOf(data.size, buffer.size)
        System.arraycopy(data, 0, buffer, 0, bytesToCopy)
        return bytesToCopy
    }

    override suspend fun doAvailable(): Int {
        return if (dataChannel.isEmpty) 0 else 1
    }

    override suspend fun doClearBuffers() {
        // Drain the data channel
        while (!dataChannel.isEmpty) {
            dataChannel.tryReceive()
        }

        responseLock.withLock {
            responseBuffer.clear()
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SERVICE AND CHARACTERISTIC DISCOVERY
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Finds the OBD service from discovered services.
     */
    private fun findOBDService(gatt: BluetoothGatt): BluetoothGattService? {
        // Try known OBD service UUIDs
        for (uuid in bleConfig.serviceUUIDs) {
            val service = gatt.getService(uuid)
            if (service != null) {
                return service
            }
        }

        // Fallback: look for any service with write and notify characteristics
        for (service in gatt.services) {
            val hasWrite = service.characteristics.any { char ->
                (char.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 ||
                (char.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
            }
            val hasNotify = service.characteristics.any { char ->
                (char.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0 ||
                (char.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
            }
            if (hasWrite && hasNotify) {
                return service
            }
        }

        return null
    }

    /**
     * Finds the write characteristic from the service.
     */
    private fun findWriteCharacteristic(service: BluetoothGattService): BluetoothGattCharacteristic? {
        // Try known write characteristic UUIDs
        for (uuid in bleConfig.writeCharacteristicUUIDs) {
            val char = service.getCharacteristic(uuid)
            if (char != null) {
                return char
            }
        }

        // Fallback: find any writable characteristic
        return service.characteristics.firstOrNull { char ->
            (char.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 ||
            (char.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
        }
    }

    /**
     * Finds the notify characteristic from the service.
     */
    private fun findNotifyCharacteristic(service: BluetoothGattService): BluetoothGattCharacteristic? {
        // Try known notify characteristic UUIDs
        for (uuid in bleConfig.notifyCharacteristicUUIDs) {
            val char = service.getCharacteristic(uuid)
            if (char != null) {
                return char
            }
        }

        // Fallback: find any notifiable characteristic
        return service.characteristics.firstOrNull { char ->
            (char.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0 ||
            (char.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // NOTIFICATION MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════════

    @SuppressLint("MissingPermission")
    @SuppressLint("MissingPermission")
    private fun enableNotificationsSync(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        gatt.setCharacteristicNotification(characteristic, true)

        characteristic.getDescriptor(BluetoothLEConstants.CLIENT_CHARACTERISTIC_CONFIG_UUID)?.let { descriptor ->
            val value = if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) {
                BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            } else {
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(descriptor, value)
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = value
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(descriptor)
            }
        }
    }

    private fun enableNotifications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        gatt.setCharacteristicNotification(characteristic, true)

        val descriptor = characteristic.getDescriptor(BluetoothLEConstants.CLIENT_CHARACTERISTIC_CONFIG_UUID)
        if (descriptor != null) {
            val value = if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) {
                BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            } else {
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(descriptor, value)
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = value
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(descriptor)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun disableNotificationsSync(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        gatt.setCharacteristicNotification(characteristic, false)

        characteristic.getDescriptor(BluetoothLEConstants.CLIENT_CHARACTERISTIC_CONFIG_UUID)?.let { descriptor ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(descriptor)
            }
        }
    }

    private fun disableNotifications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        gatt.setCharacteristicNotification(characteristic, false)

        val descriptor = characteristic.getDescriptor(BluetoothLEConstants.CLIENT_CHARACTERISTIC_CONFIG_UUID)
        if (descriptor != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(descriptor)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ERROR HANDLING
    // ═══════════════════════════════════════════════════════════════════════

    private suspend fun handleDisconnection() {
        if (!isConnected) return

        _connectionState.value = ConnectionState.Error(
            ConnectionException("BLE connection lost"),
            isRecoverable = true
        )

        if (config.autoReconnect) {
            scope.launch {
                reconnect()
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // UTILITIES
    // ═══════════════════════════════════════════════════════════════════════

    private fun hasBluetoothPermissions(): Boolean {
        return BluetoothLEConstants.BLUETOOTH_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Gets the current MTU.
     */
    fun getCurrentMtu(): Int = currentMtu

    /**
     * Gets the connection attempt number.
     */
    fun getConnectionAttempt(): Int = connectionAttempt.get()

    /**
     * Updates the BLE configuration.
     */
    fun updateConfig(config: BluetoothLEConfig) {
        this.bleConfig = config
    }

    companion object {
        fun create(context: Context): BluetoothLEConnection {
            return BluetoothLEConnection(context)
        }

        fun create(context: Context, config: BluetoothLEConfig): BluetoothLEConnection {
            return BluetoothLEConnection(context, config)
        }

        @SuppressLint("MissingPermission")
        fun isBLEAvailable(context: Context): Boolean {
            return context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
        }
    }
}