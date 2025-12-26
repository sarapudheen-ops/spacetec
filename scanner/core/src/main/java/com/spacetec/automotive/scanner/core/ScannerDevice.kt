// scanner/core/src/main/kotlin/com/spacetec/automotive/scanner/core/ScannerDevice.kt
package com.spacetec.obd.scanner.core

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/**
 * Represents a discovered scanner device that can be connected to.
 * 
 * @property id Unique identifier (MAC address for Bluetooth, IP for WiFi, etc.)
 * @property name Device name (may be user-assigned or from device)
 * @property type Type of scanner connection
 * @property address Connection address (MAC, IP, USB path)
 * @property signalStrength Signal strength for wireless devices (RSSI for Bluetooth)
 * @property isPaired Whether the device is paired (for Bluetooth)
 * @property isKnown Whether this device was previously connected
 * @property lastConnected Timestamp of last successful connection
 * @property metadata Additional device-specific metadata
 */
@Parcelize
@Serializable
data class ScannerDevice(
    val id: String,
    val name: String,
    val type: ScannerType,
    val address: String,
    val signalStrength: Int = 0,
    val isPaired: Boolean = false,
    val isKnown: Boolean = false,
    val lastConnected: Long = 0,
    val metadata: Map<String, String> = emptyMap()
) : Parcelable {
    
    /**
     * Display name with signal indicator for wireless devices.
     */
    val displayName: String
        get() = when {
            type.isWireless && signalStrength != 0 -> "$name ${getSignalBars()}"
            else -> name
        }
    
    /**
     * Whether this device appears to be an OBD-II adapter based on name.
     */
    val looksLikeObdAdapter: Boolean
        get() {
            val nameLower = name.lowercase()
            return OBD_ADAPTER_KEYWORDS.any { nameLower.contains(it) }
        }
    
    /**
     * Gets signal strength as visual bars.
     */
    fun getSignalBars(): String = when {
        signalStrength >= -50 -> "▂▄▆█"
        signalStrength >= -60 -> "▂▄▆_"
        signalStrength >= -70 -> "▂▄__"
        signalStrength >= -80 -> "▂___"
        else -> "____"
    }
    
    /**
     * Gets signal strength as percentage.
     */
    fun getSignalPercentage(): Int = when {
        signalStrength >= -50 -> 100
        signalStrength >= -60 -> 80
        signalStrength >= -70 -> 60
        signalStrength >= -80 -> 40
        signalStrength >= -90 -> 20
        else -> 0
    }
    
    /**
     * Gets human-readable last connected time.
     */
    fun getLastConnectedDisplay(): String {
        if (lastConnected == 0L) return "Never"
        
        val now = System.currentTimeMillis()
        val diff = now - lastConnected
        
        return when {
            diff < 60_000 -> "Just now"
            diff < 3600_000 -> "${diff / 60_000} min ago"
            diff < 86400_000 -> "${diff / 3600_000} hours ago"
            diff < 604800_000 -> "${diff / 86400_000} days ago"
            else -> "Over a week ago"
        }
    }
    
    companion object {
        private val OBD_ADAPTER_KEYWORDS = listOf(
            "obd", "obdii", "obd2", "obd-ii",
            "elm", "elm327",
            "vgate", "veepeak", "bafx",
            "scan", "scanner", "diag",
            "car", "auto", "vehicle",
            "bluetooth", "wifi", "wireless"
        )
        
        /**
         * Sorts devices by relevance for OBD-II use.
         */
        fun sortByRelevance(devices: List<ScannerDevice>): List<ScannerDevice> {
            return devices.sortedWith(
                compareByDescending<ScannerDevice> { it.isKnown }
                    .thenByDescending { it.isPaired }
                    .thenByDescending { it.looksLikeObdAdapter }
                    .thenByDescending { it.signalStrength }
                    .thenBy { it.name }
            )
        }
    }
}

/**
 * Result of a device scan operation.
 */
data class ScanResult(
    val devices: List<ScannerDevice>,
    val isComplete: Boolean = false,
    val scanDurationMs: Long = 0,
    val errorMessage: String? = null
) {
    val deviceCount: Int get() = devices.size
    val hasDevices: Boolean get() = devices.isNotEmpty()
    val hasError: Boolean get() = errorMessage != null
    
    /**
     * Gets devices sorted by relevance.
     */
    fun getSortedDevices(): List<ScannerDevice> = 
        ScannerDevice.sortByRelevance(devices)
    
    /**
     * Gets only devices that look like OBD adapters.
     */
    fun getObdDevices(): List<ScannerDevice> =
        devices.filter { it.looksLikeObdAdapter }
}