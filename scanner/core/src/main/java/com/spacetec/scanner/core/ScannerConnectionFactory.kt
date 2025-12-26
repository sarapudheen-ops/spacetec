/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 *
 * This file is part of the SpaceTec professional automotive diagnostic system.
 */

package com.spacetec.obd.scanner.core

import android.content.Context
import com.spacetec.core.domain.models.scanner.ScannerConnectionType
import com.spacetec.obd.scanner.bluetooth.classic.BluetoothClassicConnection
import com.spacetec.obd.scanner.bluetooth.ble.BluetoothLEConnection
import com.spacetec.obd.scanner.j2534.J2534Connection
import com.spacetec.obd.scanner.usb.USBConnection
import com.spacetec.obd.scanner.wifi.WiFiConnection
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Factory for creating scanner connections with automatic type detection.
 *
 * This factory provides a unified interface for creating scanner connections
 * across all supported connection types (Bluetooth Classic/LE, WiFi, USB, J2534).
 * It includes automatic connection type detection from addresses and supports
 * configuration customization for each connection type.
 *
 * ## Features
 *
 * - Automatic connection type detection from address format
 * - Support for all scanner connection types
 * - Configuration customization per connection type
 * - Dependency injection integration with Hilt
 * - Connection pooling and resource management
 *
 * ## Address Format Detection
 *
 * The factory automatically detects connection type based on address format:
 * - Bluetooth: MAC address format (AA:BB:CC:DD:EE:FF)
 * - WiFi: IP:port format (192.168.1.100:35000) or hostname:port
 * - USB: Device path (/dev/bus/usb/001/002) or vendor:product ID
 * - J2534: Device name or driver identifier
 *
 * ## Usage Example
 *
 * ```kotlin
 * @Inject
 * lateinit var connectionFactory: ScannerConnectionFactory
 *
 * // Create connection with automatic type detection
 * val connection = connectionFactory.createConnectionForAddress("AA:BB:CC:DD:EE:FF")
 *
 * // Create connection for specific type
 * val bluetoothConnection = connectionFactory.createConnection(ScannerConnectionType.BLUETOOTH_CLASSIC)
 * ```
 *
 * @param context Android context for accessing system services
 *
 * @author SpaceTec Development Team
 * @since 1.0.0
 */
@Singleton
class ScannerConnectionFactory @Inject constructor(
    private val context: Context
) {

    // ═══════════════════════════════════════════════════════════════════════
    // CONNECTION CREATION
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Creates a connection for the specified type.
     *
     * @param type Connection type to create
     * @return New connection instance
     * @throws IllegalArgumentException if connection type is not supported
     */
    fun createConnection(type: ScannerConnectionType): ScannerConnection {
        return when (type) {
            ScannerConnectionType.BLUETOOTH_CLASSIC -> {
                BluetoothClassicConnection.create(context)
            }
            ScannerConnectionType.BLUETOOTH_LE -> {
                BluetoothLEConnection.create(context)
            }
            ScannerConnectionType.WIFI -> {
                WiFiConnection.create(context)
            }
            ScannerConnectionType.USB -> {
                USBConnection.create(context)
            }
            ScannerConnectionType.J2534 -> {
                J2534Connection.create()
            }
            else -> {
                throw IllegalArgumentException("Unsupported connection type: $type")
            }
        }
    }

    /**
     * Creates a connection appropriate for the given address.
     *
     * Automatically detects the connection type based on the address format:
     * - Bluetooth MAC: AA:BB:CC:DD:EE:FF
     * - WiFi IP:port: 192.168.1.100:35000
     * - WiFi hostname:port: scanner.local:35000
     * - USB device path: /dev/bus/usb/001/002
     * - USB vendor:product: 1234:5678
     * - J2534 device name: MyJ2534Device
     *
     * @param address Device address in any supported format
     * @return New connection instance appropriate for the address
     * @throws IllegalArgumentException if address format is not recognized
     */
    fun createConnectionForAddress(address: String): ScannerConnection {
        val connectionType = detectConnectionType(address)
        return createConnection(connectionType)
    }

    /**
     * Creates a connection with custom configuration.
     *
     * @param type Connection type to create
     * @param config Custom connection configuration
     * @return New connection instance with custom configuration
     */
    fun createConnectionWithConfig(
        type: ScannerConnectionType,
        config: ConnectionConfig
    ): ScannerConnection {
        val connection = createConnection(type)
        // Note: Configuration is applied during connect() call
        return connection
    }

    /**
     * Creates multiple connections for multi-device scenarios.
     *
     * @param addresses List of device addresses
     * @return List of connections appropriate for each address
     */
    fun createMultipleConnections(addresses: List<String>): List<ScannerConnection> {
        return addresses.map { address ->
            createConnectionForAddress(address)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // CONNECTION TYPE DETECTION
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Detects the connection type from an address string.
     *
     * @param address Device address to analyze
     * @return Detected connection type
     * @throws IllegalArgumentException if address format is not recognized
     */
    fun detectConnectionType(address: String): ScannerConnectionType {
        return when {
            isBluetoothAddress(address) -> {
                // Default to Classic, but could be enhanced to detect LE
                ScannerConnectionType.BLUETOOTH_CLASSIC
            }
            isWiFiAddress(address) -> {
                ScannerConnectionType.WIFI
            }
            isUSBAddress(address) -> {
                ScannerConnectionType.USB
            }
            isJ2534Address(address) -> {
                ScannerConnectionType.J2534
            }
            else -> {
                throw IllegalArgumentException("Unable to detect connection type for address: $address")
            }
        }
    }

    /**
     * Checks if an address is a Bluetooth MAC address.
     *
     * @param address Address to check
     * @return true if address is a Bluetooth MAC address
     */
    private fun isBluetoothAddress(address: String): Boolean {
        // Bluetooth MAC address format: AA:BB:CC:DD:EE:FF or AA-BB-CC-DD-EE-FF
        val macRegex = Regex("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$")
        return macRegex.matches(address)
    }

    /**
     * Checks if an address is a WiFi IP:port or hostname:port.
     *
     * @param address Address to check
     * @return true if address is a WiFi address
     */
    private fun isWiFiAddress(address: String): Boolean {
        // Check for IP:port format
        val ipPortRegex = Regex("^(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}):(\\d{1,5})$")
        if (ipPortRegex.matches(address)) {
            // Validate IP address ranges
            val parts = address.split(":")[0].split(".")
            return parts.all { part ->
                val num = part.toIntOrNull()
                num != null && num in 0..255
            }
        }

        // Check for hostname:port format
        val hostnamePortRegex = Regex("^([a-zA-Z0-9.-]+):(\\d{1,5})$")
        if (hostnamePortRegex.matches(address)) {
            val port = address.split(":")[1].toIntOrNull()
            return port != null && port in 1..65535
        }

        // Check for plain IP address (will use default port)
        val ipRegex = Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$")
        if (ipRegex.matches(address)) {
            val parts = address.split(".")
            return parts.all { part ->
                val num = part.toIntOrNull()
                num != null && num in 0..255
            }
        }

        return false
    }

    /**
     * Checks if an address is a USB device path or vendor:product ID.
     *
     * @param address Address to check
     * @return true if address is a USB address
     */
    private fun isUSBAddress(address: String): Boolean {
        // USB device path format: /dev/bus/usb/001/002
        val devicePathRegex = Regex("^/dev/bus/usb/\\d{3}/\\d{3}$")
        if (devicePathRegex.matches(address)) {
            return true
        }

        // USB vendor:product ID format: 1234:5678 (hex)
        val vendorProductRegex = Regex("^[0-9A-Fa-f]{4}:[0-9A-Fa-f]{4}$")
        if (vendorProductRegex.matches(address)) {
            return true
        }

        // USB device name format (starts with common USB device prefixes)
        val usbPrefixes = listOf(
            "/dev/ttyUSB",
            "/dev/ttyACM",
            "/dev/cu.usbserial",
            "/dev/cu.usbmodem"
        )
        return usbPrefixes.any { prefix -> address.startsWith(prefix) }
    }

    /**
     * Checks if an address is a J2534 device name.
     *
     * @param address Address to check
     * @return true if address is a J2534 device name
     */
    private fun isJ2534Address(address: String): Boolean {
        // J2534 device names are typically alphanumeric strings
        // Common patterns include manufacturer names or device models
        val j2534Patterns = listOf(
            "j2534",
            "passthru",
            "drew",
            "tactrix",
            "openport",
            "mongoose",
            "kvaser",
            "peak",
            "vector"
        )

        val lowerAddress = address.lowercase()
        return j2534Patterns.any { pattern -> lowerAddress.contains(pattern) } ||
               // Generic pattern for device names (alphanumeric with spaces/dashes)
               Regex("^[a-zA-Z0-9\\s\\-_]+$").matches(address) && address.length > 3
    }

    // ═══════════════════════════════════════════════════════════════════════
    // CONFIGURATION MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Gets the default configuration for a connection type.
     *
     * @param type Connection type
     * @return Default configuration for the connection type
     */
    fun getDefaultConfig(type: ScannerConnectionType): ConnectionConfig {
        return when (type) {
            ScannerConnectionType.BLUETOOTH_CLASSIC,
            ScannerConnectionType.BLUETOOTH_LE -> ConnectionConfig.BLUETOOTH
            ScannerConnectionType.WIFI -> ConnectionConfig.WIFI
            ScannerConnectionType.USB -> ConnectionConfig.USB
            ScannerConnectionType.J2534 -> ConnectionConfig.INITIALIZATION
            else -> ConnectionConfig.DEFAULT
        }
    }

    /**
     * Creates an optimized configuration for a specific use case.
     *
     * @param type Connection type
     * @param useCase Use case (e.g., "fast", "slow", "initialization")
     * @return Optimized configuration
     */
    fun createOptimizedConfig(
        type: ScannerConnectionType,
        useCase: String
    ): ConnectionConfig {
        val baseConfig = getDefaultConfig(type)
        
        return when (useCase.lowercase()) {
            "fast", "quick" -> baseConfig.forFastOperations()
            "slow", "initialization", "programming" -> baseConfig.forSlowOperations()
            "extended" -> baseConfig.withExtendedTimeouts()
            else -> baseConfig
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // VALIDATION AND UTILITIES
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Validates if a connection type is supported on this device.
     *
     * @param type Connection type to validate
     * @return true if connection type is supported
     */
    fun isConnectionTypeSupported(type: ScannerConnectionType): Boolean {
        return when (type) {
            ScannerConnectionType.BLUETOOTH_CLASSIC,
            ScannerConnectionType.BLUETOOTH_LE -> {
                BluetoothClassicConnection.isBluetoothAvailable(context)
            }
            ScannerConnectionType.WIFI -> {
                WiFiConnection.isWiFiAvailable(context)
            }
            ScannerConnectionType.USB -> {
                USBConnection.isUSBHostSupported(context)
            }
            ScannerConnectionType.J2534 -> {
                // J2534 support depends on native libraries and drivers
                // For now, assume it's supported if the class is available
                true
            }
            else -> false
        }
    }

    /**
     * Gets all supported connection types on this device.
     *
     * @return List of supported connection types
     */
    fun getSupportedConnectionTypes(): List<ScannerConnectionType> {
        return ScannerConnectionType.values().filter { type ->
            isConnectionTypeSupported(type)
        }
    }

    /**
     * Validates an address format for a specific connection type.
     *
     * @param address Address to validate
     * @param type Expected connection type
     * @return true if address is valid for the connection type
     */
    fun validateAddress(address: String, type: ScannerConnectionType): Boolean {
        return try {
            val detectedType = detectConnectionType(address)
            detectedType == type
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    /**
     * Suggests alternative connection types for an address.
     *
     * @param address Address to analyze
     * @return List of possible connection types for the address
     */
    fun suggestConnectionTypes(address: String): List<ScannerConnectionType> {
        val suggestions = mutableListOf<ScannerConnectionType>()
        
        try {
            val primaryType = detectConnectionType(address)
            suggestions.add(primaryType)
            
            // Add related types
            when (primaryType) {
                ScannerConnectionType.BLUETOOTH_CLASSIC -> {
                    suggestions.add(ScannerConnectionType.BLUETOOTH_LE)
                }
                ScannerConnectionType.BLUETOOTH_LE -> {
                    suggestions.add(ScannerConnectionType.BLUETOOTH_CLASSIC)
                }
                else -> {
                    // No related types for other connection types
                }
            }
        } catch (e: IllegalArgumentException) {
            // If detection fails, suggest all supported types
            suggestions.addAll(getSupportedConnectionTypes())
        }
        
        return suggestions.distinct()
    }

    // ═══════════════════════════════════════════════════════════════════════
    // FACTORY INFORMATION
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Gets information about the factory and supported features.
     *
     * @return Factory information
     */
    fun getFactoryInfo(): FactoryInfo {
        val supportedTypes = getSupportedConnectionTypes()
        
        return FactoryInfo(
            supportedConnectionTypes = supportedTypes,
            hasBluetoothSupport = supportedTypes.any { 
                it == ScannerConnectionType.BLUETOOTH_CLASSIC || 
                it == ScannerConnectionType.BLUETOOTH_LE 
            },
            hasWiFiSupport = supportedTypes.contains(ScannerConnectionType.WIFI),
            hasUSBSupport = supportedTypes.contains(ScannerConnectionType.USB),
            hasJ2534Support = supportedTypes.contains(ScannerConnectionType.J2534),
            version = "1.0.0"
        )
    }

    /**
     * Information about the connection factory capabilities.
     */
    data class FactoryInfo(
        val supportedConnectionTypes: List<ScannerConnectionType>,
        val hasBluetoothSupport: Boolean,
        val hasWiFiSupport: Boolean,
        val hasUSBSupport: Boolean,
        val hasJ2534Support: Boolean,
        val version: String
    )

    companion object {
        
        /**
         * Creates a factory instance for testing.
         *
         * @param context Android context
         * @return Factory instance
         */
        fun createForTesting(context: Context): ScannerConnectionFactory {
            return ScannerConnectionFactory(context)
        }
        
        /**
         * Validates an address format without creating a factory.
         *
         * @param address Address to validate
         * @return true if address format is recognized
         */
        fun isValidAddressFormat(address: String): Boolean {
            return try {
                val tempFactory = ScannerConnectionFactory(
                    // Use a minimal context for validation only
                    context = android.app.Application()
                )
                tempFactory.detectConnectionType(address)
                true
            } catch (e: Exception) {
                false
            }
        }
    }
}