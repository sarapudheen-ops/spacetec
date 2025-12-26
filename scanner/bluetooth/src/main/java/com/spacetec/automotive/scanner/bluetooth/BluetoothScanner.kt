// scanner/bluetooth/src/main/kotlin/com/spacetec/automotive/scanner/bluetooth/BluetoothScanner.kt
package com.spacetec.obd.scanner.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.spacetec.obd.core.common.result.AppResult
import com.spacetec.obd.core.common.result.Result
import com.spacetec.obd.core.common.result.SpaceTecError
import com.spacetec.transport.contract.Protocol
import com.spacetec.transport.contract.ProtocolType
import com.spacetec.obd.scanner.core.ScannerDevice
import com.spacetec.obd.scanner.core.ScannerType
import com.spacetec.obd.scanner.core.elm327.Elm327Adapter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import javax.inject.Inject

/**
 * Bluetooth Classic (SPP) scanner implementation.
 * 
 * This class implements the ELM327 adapter interface for Bluetooth
 * OBD-II scanners that use the Serial Port Profile (SPP).
 */
class BluetoothScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val device: ScannerDevice
) : Elm327Adapter() {
    
    override val id: String = device.id
    override val name: String = device.name
    override val type: ScannerType = ScannerType.BLUETOOTH
    
    private val bluetoothManager: BluetoothManager? = 
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    
    private var bluetoothSocket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    
    private val readBuffer = ByteArray(1024)
    
    // ========================================================================
    // CONNECTION IMPLEMENTATION
    // ========================================================================
    
    @SuppressLint("MissingPermission")
    override suspend fun openConnection(): AppResult<Unit> = withContext(Dispatchers.IO) {
        if (!hasBluetoothPermissions()) {
            return@withContext Result.failure(
                SpaceTecError.ConnectionError.PermissionDenied(
                    message = "Bluetooth permissions not granted",
                    permission = getBluetoothPermission()
                )
            )
        }
        
        val adapter = bluetoothAdapter ?: return@withContext Result.failure(
            SpaceTecError.ConnectionError.BluetoothDisabled(
                message = "Bluetooth not available on this device"
            )
        )
        
        if (!adapter.isEnabled) {
            return@withContext Result.failure(
                SpaceTecError.ConnectionError.BluetoothDisabled()
            )
        }
        
        try {
            val btDevice = adapter.getRemoteDevice(device.address)
            
            // Cancel discovery to speed up connection
            adapter.cancelDiscovery()
            
            // Try to connect using SPP UUID
            bluetoothSocket = createSocket(btDevice)
            
            Timber.d("Connecting to ${device.name} (${device.address})...")
            bluetoothSocket?.connect()
            
            inputStream = bluetoothSocket?.inputStream
            outputStream = bluetoothSocket?.outputStream
            
            if (inputStream == null || outputStream == null) {
                throw IOException("Failed to get streams")
            }
            
            Timber.i("Bluetooth socket connected to ${device.name}")
            Result.success(Unit)
            
        } catch (e: IOException) {
            Timber.e(e, "Bluetooth connection failed")
            closeConnection()
            Result.failure(SpaceTecError.ConnectionError.ConnectionFailed(
                reason = e.message ?: "Connection failed"
            ))
        } catch (e: SecurityException) {
            Timber.e(e, "Bluetooth permission denied")
            Result.failure(SpaceTecError.ConnectionError.PermissionDenied(
                permission = "Bluetooth"
            ))
        }
    }
    
    @SuppressLint("MissingPermission")
    private fun createSocket(device: BluetoothDevice): BluetoothSocket {
        return try {
            // Standard SPP UUID
            device.createRfcommSocketToServiceRecord(SPP_UUID)
        } catch (e: Exception) {
            Timber.w(e, "Standard socket creation failed, trying fallback")
            // Fallback method using reflection
            try {
                val method = device.javaClass.getMethod(
                    "createRfcommSocket",
                    Int::class.javaPrimitiveType
                )
                method.invoke(device, 1) as BluetoothSocket
            } catch (e2: Exception) {
                Timber.e(e2, "Fallback socket creation failed")
                throw e
            }
        }
    }
    
    override suspend fun closeConnection() {
        withContext(Dispatchers.IO) {
            try {
                inputStream?.close()
                outputStream?.close()
                bluetoothSocket?.close()
            } catch (e: IOException) {
                Timber.w(e, "Error closing Bluetooth socket")
            } finally {
                inputStream = null
                outputStream = null
                bluetoothSocket = null
            }
        }
    }
    
    // ========================================================================
    // DATA TRANSMISSION
    // ========================================================================
    
    override suspend fun writeBytes(data: ByteArray): AppResult<Unit> = withContext(Dispatchers.IO) {
        val stream = outputStream ?: return@withContext Result.failure(
            SpaceTecError.ConnectionError.ConnectionLost()
        )
        
        try {
            stream.write(data)
            stream.flush()
            Result.success(Unit)
        } catch (e: IOException) {
            Timber.e(e, "Bluetooth write error")
            handleConnectionLost()
            Result.failure(SpaceTecError.ConnectionError.ConnectionLost())
        }
    }
    
    override suspend fun readBytes(timeout: Long): AppResult<ByteArray> = withContext(Dispatchers.IO) {
        val stream = inputStream ?: return@withContext Result.failure(
            SpaceTecError.ConnectionError.ConnectionLost()
        )
        
        try {
            val startTime = System.currentTimeMillis()
            
            while (System.currentTimeMillis() - startTime < timeout) {
                if (stream.available() > 0) {
                    val bytesRead = stream.read(readBuffer)
                    if (bytesRead > 0) {
                        return@withContext Result.success(readBuffer.copyOf(bytesRead))
                    }
                }
                kotlinx.coroutines.delay(10)
            }
            
            // Timeout - return empty array
            Result.success(byteArrayOf())
            
        } catch (e: IOException) {
            Timber.e(e, "Bluetooth read error")
            handleConnectionLost()
            Result.failure(SpaceTecError.ConnectionError.ConnectionLost())
        }
    }
    
    override fun observeBytes(): Flow<ByteArray> = flow {
        val stream = inputStream ?: return@flow
        val buffer = ByteArray(1024)
        
        while (isActive && bluetoothSocket?.isConnected == true) {
            try {
                if (stream.available() > 0) {
                    val bytesRead = stream.read(buffer)
                    if (bytesRead > 0) {
                        emit(buffer.copyOf(bytesRead))
                    }
                } else {
                    kotlinx.coroutines.delay(10)
                }
            } catch (e: IOException) {
                Timber.e(e, "Bluetooth stream error")
                break
            }
        }
    }.flowOn(Dispatchers.IO)
    
    // ========================================================================
    // PROTOCOL CREATION
    // ========================================================================
    
    // Protocol creation is handled by the protocol layer to avoid circular dependency
    // The scanner provides the transport (connection) and the protocol layer uses it
    
    // ========================================================================
    // CONNECTION STATE
    // ========================================================================
    
    private suspend fun handleConnectionLost() {
        val currentState = _connectionState.value
        if (currentState is com.spacetec.automotive.scanner.core.ScannerConnectionState.Connected) {
            _connectionState.value = com.spacetec.automotive.scanner.core.ScannerConnectionState.Error(
                message = "Connection lost",
                isRecoverable = true
            )
            
            // Attempt reconnection if enabled
            // This would be handled by a connection manager
        }
    }
    
    // ========================================================================
    // PERMISSIONS
    // ========================================================================
    
    private fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    private fun getBluetoothPermission(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_CONNECT
        } else {
            Manifest.permission.BLUETOOTH
        }
    }
    
    companion object {
        /** Standard Serial Port Profile UUID */
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}