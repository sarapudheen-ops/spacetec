// scanner/core/src/main/kotlin/com/spacetec/automotive/scanner/core/ScannerDiscovery.kt
package com.spacetec.obd.scanner.core

import com.spacetec.obd.core.common.result.AppResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface for scanner device discovery.
 * 
 * Implementations handle the platform-specific logic for discovering
 * available scanner devices (Bluetooth scanning, WiFi network scanning,
 * USB device enumeration, etc.)
 */
interface ScannerDiscovery {
    
    /**
     * Type of discovery this implementation handles.
     */
    val scannerType: ScannerType
    
    /**
     * Current scanning state.
     */
    val isScanning: StateFlow<Boolean>
    
    /**
     * Discovered devices as a flow.
     */
    val discoveredDevices: StateFlow<List<ScannerDevice>>
    
    /**
     * Starts scanning for available devices.
     * 
     * @param config Scan configuration
     * @return Success or failure (e.g., permissions missing)
     */
    suspend fun startScan(config: ScanConfig = ScanConfig()): AppResult<Unit>
    
    /**
     * Stops the current scan.
     */
    suspend fun stopScan()
    
    /**
     * Scans for devices and returns results as a flow.
     * 
     * @param config Scan configuration
     * @param duration Maximum scan duration in milliseconds
     * @return Flow of discovered devices
     */
    fun scanAsFlow(
        config: ScanConfig = ScanConfig(),
        duration: Long = DEFAULT_SCAN_DURATION
    ): Flow<ScannerDevice>
    
    /**
     * Gets previously paired/known devices.
     */
    suspend fun getKnownDevices(): AppResult<List<ScannerDevice>>
    
    /**
     * Checks if the required permissions are granted.
     */
    fun hasRequiredPermissions(): Boolean
    
    /**
     * Gets the list of required permissions.
     */
    fun getRequiredPermissions(): List<String>
    
    /**
     * Checks if the scanner type is available on this device.
     */
    fun isAvailable(): Boolean
    
    /**
     * Checks if the scanner type is enabled (Bluetooth on, WiFi on, etc.)
     */
    fun isEnabled(): Boolean
    
    companion object {
        const val DEFAULT_SCAN_DURATION = 10_000L
        const val EXTENDED_SCAN_DURATION = 30_000L
    }
}

/**
 * Configuration for device scanning.
 */
data class ScanConfig(
    val filterByName: String? = null,
    val filterByAddress: String? = null,
    val onlyObdDevices: Boolean = true,
    val includeUnnamed: Boolean = false,
    val includePaired: Boolean = true,
    val scanMode: ScanMode = ScanMode.BALANCED,
    val signalThreshold: Int = -100
)

/**
 * Scanning mode options.
 */
enum class ScanMode {
    /** Low power, slower discovery */
    LOW_POWER,
    
    /** Balanced power and speed */
    BALANCED,
    
    /** High power, fastest discovery */
    LOW_LATENCY,
    
    /** Opportunistic, uses other apps' scans */
    OPPORTUNISTIC
}