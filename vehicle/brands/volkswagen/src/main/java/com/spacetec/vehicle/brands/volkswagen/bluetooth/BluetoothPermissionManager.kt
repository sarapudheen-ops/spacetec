/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 */
package com.spacetec.vehicle.brands.volkswagen.bluetooth

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Handles all Bluetooth permissions across different Android versions
 */
class BluetoothPermissionManager(private val activity: Activity) {

    private val context: Context = activity.applicationContext
    var callback: PermissionCallback? = null

    interface PermissionCallback {
        fun onAllPermissionsGranted()
        fun onPermissionsDenied(deniedPermissions: List<String>)
        fun onBluetoothEnabled()
        fun onBluetoothEnableFailed()
    }

    fun getRequiredPermissions(): Array<String> {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            permissions.add(Manifest.permission.BLUETOOTH)
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        return permissions.toTypedArray()
    }

    fun hasAllPermissions(): Boolean {
        return getRequiredPermissions().all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun requestPermissions() {
        val permissionsToRequest = getRequiredPermissions().filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isEmpty()) {
            callback?.onAllPermissionsGranted()
        } else {
            ActivityCompat.requestPermissions(
                activity,
                permissionsToRequest.toTypedArray(),
                REQUEST_BLUETOOTH_PERMISSIONS
            )
        }
    }

    fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            val deniedPermissions = permissions.filterIndexed { index, _ ->
                grantResults[index] != PackageManager.PERMISSION_GRANTED
            }

            if (deniedPermissions.isEmpty()) {
                callback?.onAllPermissionsGranted()
            } else {
                callback?.onPermissionsDenied(deniedPermissions)
            }
        }
    }

    fun isBluetoothEnabled(): Boolean {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        return adapter?.isEnabled == true
    }

    fun requestEnableBluetooth() {
        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
            == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.S
        ) {
            activity.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BLUETOOTH)
        }
    }

    fun isLocationEnabled(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return true

        return try {
            val locationMode = Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.LOCATION_MODE
            )
            locationMode != Settings.Secure.LOCATION_MODE_OFF
        } catch (e: Settings.SettingNotFoundException) {
            false
        }
    }

    fun requestEnableLocation() {
        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        activity.startActivityForResult(intent, REQUEST_ENABLE_LOCATION)
    }

    fun performPreConnectionCheck(): PreConnectionCheckResult {
        val adapter = BluetoothAdapter.getDefaultAdapter()
            ?: return PreConnectionCheckResult(
                ready = false,
                errorMessage = "Device does not support Bluetooth",
                errorCode = ErrorCode.NO_BLUETOOTH_ADAPTER
            )

        if (!hasAllPermissions()) {
            return PreConnectionCheckResult(
                ready = false,
                errorMessage = "Bluetooth permissions not granted",
                errorCode = ErrorCode.PERMISSIONS_NOT_GRANTED
            )
        }

        if (!isBluetoothEnabled()) {
            return PreConnectionCheckResult(
                ready = false,
                errorMessage = "Bluetooth is not enabled",
                errorCode = ErrorCode.BLUETOOTH_DISABLED
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S
        ) {
            if (!isLocationEnabled()) {
                return PreConnectionCheckResult(
                    ready = false,
                    errorMessage = "Location services required for Bluetooth scanning",
                    errorCode = ErrorCode.LOCATION_DISABLED
                )
            }
        }

        return PreConnectionCheckResult(ready = true)
    }

    enum class ErrorCode {
        NO_BLUETOOTH_ADAPTER,
        PERMISSIONS_NOT_GRANTED,
        BLUETOOTH_DISABLED,
        LOCATION_DISABLED
    }

    data class PreConnectionCheckResult(
        val ready: Boolean,
        val errorMessage: String? = null,
        val errorCode: ErrorCode? = null
    )

    companion object {
        const val REQUEST_BLUETOOTH_PERMISSIONS = 1001
        const val REQUEST_ENABLE_BLUETOOTH = 1002
        const val REQUEST_ENABLE_LOCATION = 1003
    }
}
