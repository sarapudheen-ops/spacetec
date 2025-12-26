/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 */
package com.spacetec.vehicle.brands.volkswagen.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Manages Bluetooth device discovery and pairing
 * Optimized for finding OBD2/ELM327 adapters
 */
class BluetoothDeviceManager(context: Context) {

    private val appContext: Context = context.applicationContext
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val discoveredDevices = CopyOnWriteArrayList<BluetoothDevice>()
    private val discoveredAddresses = mutableSetOf<String>()
    private val mainHandler = Handler(Looper.getMainLooper())
    var callback: DeviceDiscoveryCallback? = null
    private var isScanning = false

    interface DeviceDiscoveryCallback {
        fun onDeviceFound(device: BluetoothDevice, isOBDAdapter: Boolean)
        fun onDiscoveryStarted()
        fun onDiscoveryFinished(allDevices: List<BluetoothDevice>, obdDevices: List<BluetoothDevice>)
        fun onError(message: String)
    }

    private val discoveryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    if (device != null && !discoveredAddresses.contains(device.address)) {
                        discoveredAddresses.add(device.address)
                        discoveredDevices.add(device)
                        val isOBD = isOBDAdapter(device)
                        Log.d(TAG, "Device found: ${getDeviceName(device)} [${device.address}] isOBD: $isOBD")
                        mainHandler.post { callback?.onDeviceFound(device, isOBD) }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> finishDiscovery()
            }
        }
    }

    private val bondReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == BluetoothDevice.ACTION_BOND_STATE_CHANGED) {
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
                Log.d(TAG, "Bond state changed: ${getDeviceName(device)} to ${bondStateToString(bondState)}")
            }
        }
    }

    fun getPairedDevices(): List<BluetoothDevice> {
        if (bluetoothAdapter == null || !hasBluetoothConnectPermission()) return emptyList()

        return try {
            bluetoothAdapter.bondedDevices?.toList() ?: emptyList()
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception getting bonded devices", e)
            emptyList()
        }
    }

    fun getPairedOBDDevices(): List<BluetoothDevice> = getPairedDevices().filter { isOBDAdapter(it) }

    fun startDiscovery() {
        if (bluetoothAdapter == null) {
            callback?.onError("Bluetooth adapter not available")
            return
        }

        if (!hasBluetoothScanPermission()) {
            callback?.onError("Missing Bluetooth scan permission")
            return
        }

        if (isScanning) stopDiscovery()

        discoveredDevices.clear()
        discoveredAddresses.clear()

        getPairedDevices().forEach { device ->
            if (!discoveredAddresses.contains(device.address)) {
                discoveredAddresses.add(device.address)
                discoveredDevices.add(device)
            }
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        appContext.registerReceiver(discoveryReceiver, filter)
        appContext.registerReceiver(bondReceiver, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED))

        try {
            isScanning = true
            bluetoothAdapter.startDiscovery()
            mainHandler.post { callback?.onDiscoveryStarted() }
            mainHandler.postDelayed({ stopDiscovery() }, SCAN_TIMEOUT)
            Log.d(TAG, "Bluetooth discovery started")
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception starting discovery", e)
            callback?.onError("Permission denied for Bluetooth discovery")
        }
    }

    fun stopDiscovery() {
        if (!isScanning) return

        try {
            if (bluetoothAdapter?.isDiscovering == true) {
                bluetoothAdapter.cancelDiscovery()
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception stopping discovery", e)
        }

        finishDiscovery()
    }

    private fun finishDiscovery() {
        if (!isScanning) return
        isScanning = false

        runCatching { appContext.unregisterReceiver(discoveryReceiver) }
        runCatching { appContext.unregisterReceiver(bondReceiver) }
        mainHandler.removeCallbacksAndMessages(null)

        val obdDevices = discoveredDevices.filter { isOBDAdapter(it) }
        Log.d(TAG, "Discovery finished. Total: ${discoveredDevices.size}, OBD: ${obdDevices.size}")

        mainHandler.post {
            callback?.onDiscoveryFinished(discoveredDevices.toList(), obdDevices)
        }
    }

    fun isOBDAdapter(device: BluetoothDevice): Boolean {
        val name = getDeviceName(device).uppercase()
        if (name.isEmpty()) return false

        if (OBD_ADAPTER_PATTERNS.any { name.contains(it) }) return true

        device.bluetoothClass?.deviceClass?.let { deviceClass ->
            if (deviceClass == 0x1F00) return true
        }

        return false
    }

    fun getDeviceName(device: BluetoothDevice?): String {
        if (device == null) return "Unknown Device"

        return try {
            if (hasBluetoothConnectPermission()) {
                device.name ?: device.address
            } else device.address
        } catch (e: SecurityException) {
            device.address
        }
    }

    fun pairDevice(device: BluetoothDevice): Boolean {
        return try {
            if (device.bondState == BluetoothDevice.BOND_BONDED) {
                Log.d(TAG, "Device already paired: ${getDeviceName(device)}")
                true
            } else {
                Log.d(TAG, "Initiating pairing with: ${getDeviceName(device)}")
                device.createBond()
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception pairing device", e)
            false
        }
    }

    fun isDevicePaired(device: BluetoothDevice): Boolean {
        return try {
            device.bondState == BluetoothDevice.BOND_BONDED
        } catch (e: SecurityException) {
            false
        }
    }

    fun getDeviceByAddress(address: String): BluetoothDevice? {
        return try {
            bluetoothAdapter?.getRemoteDevice(address)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Invalid Bluetooth address: $address")
            null
        }
    }

    private fun hasBluetoothConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) ==
                    PackageManager.PERMISSION_GRANTED
        } else true
    }

    private fun hasBluetoothScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_SCAN) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }

    private fun bondStateToString(state: Int): String = when (state) {
        BluetoothDevice.BOND_NONE -> "NONE"
        BluetoothDevice.BOND_BONDING -> "BONDING"
        BluetoothDevice.BOND_BONDED -> "BONDED"
        else -> "UNKNOWN"
    }

    fun cleanup() = stopDiscovery()

    companion object {
        private const val TAG = "BluetoothDeviceManager"
        private const val SCAN_TIMEOUT = 30000L

        private val OBD_ADAPTER_PATTERNS = arrayOf(
            "OBD", "ELM", "OBDII", "OBD2", "OBD-II", "OBD 2",
            "VGATE", "VEEPEAK", "BAFX", "KONNWEI", "ANCEL",
            "LAUNCH", "AUTEL", "FOXWELL", "INNOVA", "BLUEDRIVER",
            "SCAN", "TORQUE", "CAR", "AUTO", "DIAG"
        )
    }
}
