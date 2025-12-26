package com.obdreader.data.bluetooth.classic

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.spacetec.domain.repository.BluetoothConnection
import com.spacetec.domain.repository.ConnectionState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.lang.reflect.Method
import java.util.UUID
import javax.inject.Inject

/**
 * Bluetooth Classic (SPP) connection implementation for OBD adapters.
 * 
 * This class handles connections to Bluetooth OBD adapters that use the Serial Port Profile (SPP)
 * over RFCOMM, which is the most common type of Bluetooth OBD adapter (like ELM327).
 */
class BluetoothClassicConnection @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : BluetoothConnection {

    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var device: BluetoothDevice? = null

    private var _isConnected = false

    private val receiveChannel = Channel<ByteArray>(capacity = 100, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override fun receive(): Flow<ByteArray> = receiveChannel.receiveAsFlow().buffer(capacity = 100)

    override suspend fun send(data: ByteArray): Result<Unit> = withContext(dispatcher) {
        val stream = outputStream
        if (stream == null) {
            return@withContext Result.failure(Exception("Output stream not available"))
        }

        try {
            stream.write(data)
            stream.flush()
            Result.success(Unit)
        } catch (e: IOException) {
            Timber.e(e, "Failed to send data")
            Result.failure(e)
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun connect(): Result<Unit> = withContext(dispatcher) {
        try {
            // Check if device is set
            val btDevice = device ?: return@withContext Result.failure(Exception("No device selected"))

            // Check Bluetooth permissions
            if (!hasBluetoothPermissions()) {
                return@withContext Result.failure(SecurityException("Bluetooth permissions not granted"))
            }

            // Get Bluetooth adapter
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val bluetoothAdapter = bluetoothManager?.adapter
                ?: return@withContext Result.failure(Exception("Bluetooth adapter not available"))

            if (!bluetoothAdapter.isEnabled) {
                return@withContext Result.failure(Exception("Bluetooth is disabled"))
            }

            // Cancel discovery to improve connection speed
            if (bluetoothAdapter.isDiscovering) {
                bluetoothAdapter.cancelDiscovery()
                delay(100)
            }

            // Attempt to create and connect socket

            // Try different socket creation methods for compatibility
            socket = createCompatibleSocket(btDevice)

            // Connect with timeout
            withTimeout(CONNECTION_TIMEOUT) {
                socket?.connect()
            }

            // Get input/output streams
            inputStream = socket?.inputStream
            outputStream = socket?.outputStream

            if (inputStream == null || outputStream == null) {
                return@withContext Result.failure(IOException("Failed to get I/O streams"))
            }

            // Start background reader
            startBackgroundReader()

            _isConnected = true
            Result.success(Unit)

        } catch (e: CancellationException) {
            Result.failure(e)
        } catch (e: Exception) {
            Timber.e(e, "Bluetooth connection failed")
            disconnect()
            Result.failure(e)
        }
    }

    override suspend fun disconnect() {
        try {
            // Stop background reader
            receiveChannel.close()

            // Close streams
            inputStream?.close()
            outputStream?.close()
            socket?.close()

            inputStream = null
            outputStream = null
            socket = null
            device = null

            _isConnected = false
        } catch (e: Exception) {
            Timber.e(e, "Error during disconnect")
        }
    }

    override fun isConnected(): Boolean {
        return _isConnected && socket?.isConnected == true
    }

    /**
     * Sets the Bluetooth device to connect to.
     */
    fun setDevice(btDevice: BluetoothDevice) {
        this.device = btDevice
    }

    /**
     * Sets the device by MAC address.
     */
    @SuppressLint("MissingPermission")
    fun setDeviceByAddress(address: String) {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val bluetoothAdapter = bluetoothManager?.adapter
        if (bluetoothAdapter != null) {
            this.device = bluetoothAdapter.getRemoteDevice(address)
        }
    }

    /**
     * Creates a Bluetooth socket using various methods for maximum compatibility.
     */
    @SuppressLint("MissingPermission")
    private fun createCompatibleSocket(device: BluetoothDevice): BluetoothSocket? {
        // Method 1: Standard SPP UUID
        try {
            return device.createRfcommSocketToServiceRecord(SPP_UUID)
        } catch (e: Exception) {
            Timber.d("Standard socket creation failed: ${e.message}")
        }

        // Method 2: Insecure socket
        try {
            return device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
        } catch (e: Exception) {
            Timber.d("Insecure socket creation failed: ${e.message}")
        }

        // Method 3: Reflection-based socket creation (for older devices)
        try {
            val method: Method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
            return method.invoke(device, 1) as BluetoothSocket
        } catch (e: Exception) {
            Timber.d("Reflection-based socket creation failed: ${e.message}")
        }

        return null
    }

    /**
     * Starts a background coroutine to read data from the Bluetooth stream.
     */
    private fun startBackgroundReader() {
        kotlinx.coroutines.GlobalScope.launch {
            val buffer = ByteArray(1024)
            try {
                while (isConnected()) {
                    val stream = inputStream
                    if (stream != null && stream.available() > 0) {
                        val bytesRead = stream.read(buffer)
                        if (bytesRead > 0) {
                            val data = buffer.copyOfRange(0, bytesRead)
                            receiveChannel.trySend(data)
                        }
                    } else {
                        delay(10) // Small delay to prevent busy waiting
                    }
                }
            } catch (e: IOException) {
                Timber.e(e, "Background reader error")
                _isConnected = false
            }
        }
    }

    /**
     * Checks if the app has required Bluetooth permissions.
     */
    private fun hasBluetoothPermissions(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
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

    companion object {
        private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val CONNECTION_TIMEOUT = 10_000L // 10 seconds
    }
}