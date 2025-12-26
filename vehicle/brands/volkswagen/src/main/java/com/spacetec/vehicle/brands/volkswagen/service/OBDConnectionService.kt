/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 */
package com.spacetec.vehicle.brands.volkswagen.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.spacetec.vehicle.brands.volkswagen.bluetooth.BluetoothDeviceManager
import com.spacetec.vehicle.brands.volkswagen.elm327.ELM327Connection
import com.spacetec.vehicle.brands.volkswagen.elm327.ELM327ProtocolHandler
import com.spacetec.vehicle.brands.volkswagen.vw.VWProtocolManager
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Foreground service for maintaining OBD connection
 * Provides complete connection management for VW vehicles
 */
class OBDConnectionService : Service() {

    private lateinit var deviceManager: BluetoothDeviceManager
    private lateinit var connection: ELM327Connection
    private var protocolHandler: ELM327ProtocolHandler? = null
    private var vwManager: VWProtocolManager? = null

    private val executor = Executors.newSingleThreadExecutor()
    private val scheduledExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var monitoringTask: ScheduledFuture<*>? = null
    private var monitoredPIDs = mutableListOf<String>()
    private var monitoringInterval = 500L

    private val state = AtomicReference(ConnectionState.DISCONNECTED)

    var connectedDeviceName: String = ""
        private set
    var adapterVersion: String = ""
        private set
    var detectedProtocol: String = ""
        private set
    var vehicleVIN: String = ""
        private set
    var isVWVehicle: Boolean = false
        private set

    var connectionCallback: ConnectionCallback? = null
    var dataCallback: DataCallback? = null

    private val binder = LocalBinder()

    enum class ConnectionState {
        DISCONNECTED, DISCOVERING, CONNECTING, INITIALIZING, CONNECTED, ERROR
    }

    interface ConnectionCallback {
        fun onStateChanged(state: ConnectionState, message: String)
        fun onDeviceFound(device: BluetoothDevice, isOBD: Boolean)
        fun onDiscoveryComplete(devices: List<BluetoothDevice>)
        fun onConnected(adapterInfo: String, protocol: String, vin: String?)
        fun onDisconnected(reason: String)
        fun onError(error: String, e: Exception?)
    }

    interface DataCallback {
        fun onPIDData(pid: String, value: Double, unit: String)
        fun onDTCsRead(codes: List<String>)
        fun onMonitoringData(readings: List<PIDReading>)
    }

    data class PIDReading(
        val pid: String,
        val value: Double,
        val unit: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    inner class LocalBinder : Binder() {
        fun getService(): OBDConnectionService = this@OBDConnectionService
    }

    override fun onCreate() {
        super.onCreate()
        deviceManager = BluetoothDeviceManager(this)
        connection = ELM327Connection()
        createNotificationChannel()
        setupDeviceManagerCallback()
        setupConnectionCallback()
        Log.d(TAG, "OBDConnectionService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification("Ready to connect"))
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        stopMonitoring()
        disconnect()
        deviceManager.cleanup()
        executor.shutdown()
        scheduledExecutor.shutdown()
        Log.d(TAG, "OBDConnectionService destroyed")
    }

    fun getState(): ConnectionState = state.get()
    fun isConnected(): Boolean = state.get() == ConnectionState.CONNECTED

    fun startDiscovery() {
        setState(ConnectionState.DISCOVERING, "Searching for OBD adapters...")
        deviceManager.startDiscovery()
    }

    fun stopDiscovery() {
        deviceManager.stopDiscovery()
        if (state.get() == ConnectionState.DISCOVERING) {
            setState(ConnectionState.DISCONNECTED, "Discovery stopped")
        }
    }

    fun getPairedOBDDevices(): List<BluetoothDevice> = deviceManager.getPairedOBDDevices()
    fun getAllPairedDevices(): List<BluetoothDevice> = deviceManager.getPairedDevices()

    fun connect(device: BluetoothDevice) {
        if (state.get() == ConnectionState.CONNECTING || state.get() == ConnectionState.INITIALIZING) {
            Log.w(TAG, "Connection already in progress")
            return
        }

        deviceManager.stopDiscovery()
        connectedDeviceName = deviceManager.getDeviceName(device)
        setState(ConnectionState.CONNECTING, "Connecting to $connectedDeviceName")
        connection.connect(device)
    }

    fun connect(deviceAddress: String) {
        val device = deviceManager.getDeviceByAddress(deviceAddress)
        if (device != null) {
            connect(device)
        } else {
            notifyError("Invalid device address: $deviceAddress", null)
        }
    }

    fun disconnect() {
        stopMonitoring()
        executor.execute {
            connection.disconnect()
            adapterVersion = ""
            detectedProtocol = ""
            vehicleVIN = ""
            isVWVehicle = false
            setState(ConnectionState.DISCONNECTED, "Disconnected")
            updateNotification("Disconnected")
            mainHandler.post { connectionCallback?.onDisconnected("User requested") }
        }
    }

    fun readPID(pid: String) {
        if (state.get() != ConnectionState.CONNECTED) {
            notifyError("Not connected", null)
            return
        }

        executor.execute {
            try {
                val result = vwManager?.readPID(pid)
                if (result?.success == true) {
                    mainHandler.post { dataCallback?.onPIDData(pid, result.value, result.unit) }
                } else {
                    Log.w(TAG, "PID read failed: $pid - ${result?.errorMessage}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read PID: $pid", e)
                notifyError("Failed to read PID: $pid", e)
            }
        }
    }

    fun readPIDs(pids: List<String>) {
        if (state.get() != ConnectionState.CONNECTED) {
            notifyError("Not connected", null)
            return
        }

        executor.execute {
            val readings = mutableListOf<PIDReading>()

            for (pid in pids) {
                try {
                    val result = vwManager?.readPID(pid)
                    if (result?.success == true) {
                        readings.add(PIDReading(pid, result.value, result.unit))
                        mainHandler.post { dataCallback?.onPIDData(pid, result.value, result.unit) }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to read PID: $pid", e)
                }
            }

            if (readings.isNotEmpty()) {
                mainHandler.post { dataCallback?.onMonitoringData(readings) }
            }
        }
    }

    fun startMonitoring(pids: List<String>, intervalMs: Long) {
        if (state.get() != ConnectionState.CONNECTED) {
            notifyError("Not connected", null)
            return
        }

        stopMonitoring()
        monitoredPIDs = pids.toMutableList()
        monitoringInterval = intervalMs

        monitoringTask = scheduledExecutor.scheduleAtFixedRate({
            if (state.get() == ConnectionState.CONNECTED) {
                readPIDs(monitoredPIDs)
            } else {
                stopMonitoring()
            }
        }, 0, intervalMs, TimeUnit.MILLISECONDS)

        Log.d(TAG, "Started monitoring ${pids.size} PIDs every ${intervalMs}ms")
    }

    fun stopMonitoring() {
        monitoringTask?.cancel(false)
        monitoringTask = null
        Log.d(TAG, "Stopped PID monitoring")
    }

    fun isMonitoring(): Boolean = monitoringTask?.isCancelled == false

    fun readDTCs() {
        if (state.get() != ConnectionState.CONNECTED) {
            notifyError("Not connected", null)
            return
        }

        executor.execute {
            try {
                val result = vwManager?.readDTCs()
                if (result?.success == true) {
                    mainHandler.post { dataCallback?.onDTCsRead(result.codes) }
                } else {
                    notifyError("Failed to read DTCs: ${result?.errorMessage}", null)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read DTCs", e)
                notifyError("Failed to read DTCs", e)
            }
        }
    }

    fun clearDTCs(callback: ((Boolean, String) -> Unit)?) {
        if (state.get() != ConnectionState.CONNECTED) {
            notifyError("Not connected", null)
            callback?.invoke(false, "Not connected")
            return
        }

        executor.execute {
            try {
                val success = vwManager?.clearDTCs() ?: false
                mainHandler.post {
                    callback?.invoke(success, if (success) "DTCs cleared" else "Failed to clear DTCs")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear DTCs", e)
                mainHandler.post { callback?.invoke(false, e.message ?: "Unknown error") }
            }
        }
    }

    fun getSupportedPIDs(callback: ((List<String>?, String?) -> Unit)?) {
        if (state.get() != ConnectionState.CONNECTED) {
            notifyError("Not connected", null)
            callback?.invoke(null, "Not connected")
            return
        }

        executor.execute {
            try {
                val pids = vwManager?.getSupportedPIDs()
                mainHandler.post { callback?.invoke(pids, null) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get supported PIDs", e)
                mainHandler.post { callback?.invoke(null, e.message) }
            }
        }
    }

    fun sendRawCommand(command: String, callback: ((String?, String?) -> Unit)?) {
        if (state.get() != ConnectionState.CONNECTED) {
            notifyError("Not connected", null)
            callback?.invoke(null, "Not connected")
            return
        }

        executor.execute {
            try {
                val response = protocolHandler?.sendOBDCommand(command)
                mainHandler.post {
                    callback?.invoke(
                        response?.cleanResponse,
                        if (response?.success == true) null else response?.errorType?.toString()
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send raw command", e)
                mainHandler.post { callback?.invoke(null, e.message) }
            }
        }
    }

    private fun setupDeviceManagerCallback() {
        deviceManager.callback = object : BluetoothDeviceManager.DeviceDiscoveryCallback {
            override fun onDeviceFound(device: BluetoothDevice, isOBDAdapter: Boolean) {
                mainHandler.post { connectionCallback?.onDeviceFound(device, isOBDAdapter) }
            }

            override fun onDiscoveryStarted() {
                setState(ConnectionState.DISCOVERING, "Searching for devices...")
            }

            override fun onDiscoveryFinished(allDevices: List<BluetoothDevice>, obdDevices: List<BluetoothDevice>) {
                if (state.get() == ConnectionState.DISCOVERING) {
                    setState(ConnectionState.DISCONNECTED, "Found ${obdDevices.size} OBD adapters")
                }
                mainHandler.post { connectionCallback?.onDiscoveryComplete(obdDevices) }
            }

            override fun onError(message: String) {
                notifyError("Discovery error: $message", null)
            }
        }
    }

    private fun setupConnectionCallback() {
        connection.callback = object : ELM327Connection.ConnectionCallback {
            override fun onConnecting(status: String) {
                setState(ConnectionState.CONNECTING, status)
                updateNotification("Connecting: $status")
            }

            override fun onConnected() {
                setState(ConnectionState.INITIALIZING, "Initializing adapter...")
                updateNotification("Initializing...")
                executor.execute { initializeConnection() }
            }

            override fun onDisconnected() {
                setState(ConnectionState.DISCONNECTED, "Disconnected")
                updateNotification("Disconnected")
                mainHandler.post { connectionCallback?.onDisconnected("Connection lost") }
            }

            override fun onConnectionFailed(reason: String, e: Exception?) {
                setState(ConnectionState.ERROR, reason)
                updateNotification("Connection failed")
                notifyError(reason, e)
            }
        }
    }

    private fun initializeConnection() {
        try {
            protocolHandler = ELM327ProtocolHandler(connection)

            setState(ConnectionState.INITIALIZING, "Configuring adapter...")
            val elmResult = protocolHandler?.initialize() ?: run {
                setState(ConnectionState.ERROR, "Protocol handler not initialized")
                return
            }

            if (!elmResult.success) {
                setState(ConnectionState.ERROR, elmResult.errorMessage ?: "Unknown error")
                notifyError("Adapter initialization failed: ${elmResult.errorMessage}", elmResult.exception)
                connection.disconnect()
                return
            }

            adapterVersion = elmResult.adapterVersion ?: ""
            detectedProtocol = elmResult.detectedProtocol ?: ""

            Log.i(TAG, "ELM327 initialized - Version: $adapterVersion, Protocol: $detectedProtocol")

            setState(ConnectionState.INITIALIZING, "Detecting vehicle...")
            protocolHandler?.let { handler ->
                vwManager = VWProtocolManager(connection, handler)
            } ?: run {
                setState(ConnectionState.ERROR, "Protocol handler not available")
                return
            }

            val vwResult = vwManager?.initializeVWCommunication() ?: run {
                Log.w(TAG, "VW manager not initialized")
                setState(ConnectionState.ERROR, "VW communication initialization failed")
                return
            }

            if (!vwResult.success) {
                Log.w(TAG, "VW-specific initialization failed: ${vwResult.errorMessage}")
            } else {
                vehicleVIN = vwResult.vin ?: ""
                isVWVehicle = vwResult.isVWVehicle
                Log.i(TAG, "VW initialized - VIN: $vehicleVIN, isVW: $isVWVehicle")
            }

            setState(ConnectionState.CONNECTED, "Connected to $connectedDeviceName")
            updateNotification("Connected to $connectedDeviceName")

            mainHandler.post {
                connectionCallback?.onConnected(adapterVersion, detectedProtocol, vehicleVIN)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Initialization failed", e)
            setState(ConnectionState.ERROR, "Initialization failed")
            notifyError("Initialization failed: ${e.message}", e)
            connection.disconnect()
        }
    }

    private fun setState(newState: ConnectionState, message: String) {
        val oldState = state.getAndSet(newState)
        if (oldState != newState) {
            Log.d(TAG, "State changed: $oldState -> $newState ($message)")
            mainHandler.post { connectionCallback?.onStateChanged(newState, message) }
        }
    }

    private fun notifyError(error: String, e: Exception?) {
        Log.e(TAG, "Error: $error", e)
        mainHandler.post { connectionCallback?.onError(error, e) }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "OBD Connection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows OBD connection status"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VW Diagnostics")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, createNotification(text))
    }

    companion object {
        private const val TAG = "OBDConnectionService"
        private const val CHANNEL_ID = "OBDConnectionChannel"
        private const val NOTIFICATION_ID = 1001
    }
}