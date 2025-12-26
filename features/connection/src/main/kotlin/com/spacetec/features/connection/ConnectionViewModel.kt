package com.spacetec.features.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spacetec.domain.repository.BluetoothDeviceManager
import com.spacetec.domain.repository.ConnectionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ConnectionViewModel @Inject constructor(
    private val bluetoothManager: BluetoothDeviceManager
) : ViewModel() {

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<String>>(emptyList())
    val discoveredDevices: StateFlow<List<String>> = _discoveredDevices.asStateFlow()

    init {
        // Observe connection state
        viewModelScope.launch {
            bluetoothManager.connectionState.collect { state ->
                _connectionState.value = state
            }
        }
    }

    private var scanJob: kotlinx.coroutines.Job? = null

    fun startBleScan() {
        if (_isScanning.value) return // Already scanning

        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _isScanning.value = true
            try {
                // Collect BLE devices
                bluetoothManager.discoverBleDevices().collect { devices ->
                    // Update discovered devices list - for UI purposes we'll just show count
                    _discoveredDevices.value = devices.map { it.address }
                }
            } catch (e: Exception) {
                // Handle error
                Timber.e(e, "Error during BLE scan")
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun startClassicBluetoothScan() {
        if (_isScanning.value) return // Already scanning

        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _isScanning.value = true
            try {
                // Collect classic Bluetooth devices
                bluetoothManager.discoverClassicDevices().collect { devices ->
                    // Update discovered devices list - for UI purposes we'll just show count
                    _discoveredDevices.value = devices.map { it.address }
                }
            } catch (e: Exception) {
                // Handle error
                Timber.e(e, "Error during classic Bluetooth scan")
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun startWiFiScan() {
        viewModelScope.launch {
            // TODO: Implement WiFi scan logic
        }
    }

    fun connectToDevice(deviceAddress: String) {
        viewModelScope.launch {
            // Find the device by address and connect
            val pairedDevices = bluetoothManager.getPairedDevices()
            val device = pairedDevices.find { it.address == deviceAddress }

            if (device != null) {
                bluetoothManager.connect(device)
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            bluetoothManager.disconnect()
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Clean up resources if needed
        scanJob?.cancel()
        viewModelScope.launch {
            bluetoothManager.stopDiscovery()
        }
    }
}