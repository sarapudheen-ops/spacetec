/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 *
 * This file is part of the SpaceTec professional automotive diagnostic system.
 */

package com.spacetec.obd.scanner.core

import android.content.Context
import com.spacetec.core.domain.models.scanner.ScannerConnectionType
import com.spacetec.protocol.core.ProtocolType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.*
import kotlinx.serialization.json.Json

/**
 * Configuration for scanner connections with comprehensive settings.
 *
 * @property connectionTimeoutMs Connection timeout in milliseconds
 * @property readTimeoutMs Read timeout in milliseconds
 * @property writeTimeoutMs Write timeout in milliseconds
 * @property autoReconnect Whether to automatically reconnect on disconnection
 * @property maxReconnectAttempts Maximum number of reconnection attempts
 * @property reconnectDelayMs Delay between reconnection attempts
 * @property maxReconnectDelayMs Maximum delay between reconnection attempts
 * @property bufferSize Size of read/write buffers
 * @property keepAliveIntervalMs Interval for keep-alive messages (0 = disabled)
 * @property flushAfterWrite Whether to flush after each write operation
 * @property protocolType Preferred protocol type (AUTO for auto-detection)
 * @property enableProtocolDetection Whether to enable protocol auto-detection
 * @property enableEcho Whether to enable echo for commands
 * @property enableHeaders Whether to enable headers in responses
 * @property enableLineFeeds Whether to enable line feeds
 * @property enableSpaces Whether to enable spaces in responses
 * @property adaptiveTiming Adaptive timing mode (0=off, 1=on, 2=auto)
 * @property canBaudRate CAN bus baud rate in kbps
 * @property kwpBaudRate KWP baud rate in baud
 * @property isoBaudRate ISO 9141 baud rate in baud
 * @property enableDynamicTimeouts Whether to enable dynamic timeout adjustment
 */
@Serializable
data class ScannerConfig(
    val connectionTimeoutMs: Long = 10_000L,
    val readTimeoutMs: Long = 5_000L,
    val writeTimeoutMs: Long = 5_000L,
    val autoReconnect: Boolean = true,
    val maxReconnectAttempts: Int = 3,
    val reconnectDelayMs: Long = 1_000L,
    val maxReconnectDelayMs: Long = 10_000L,
    val bufferSize: Int = 4096,
    val keepAliveIntervalMs: Long = 0L,
    val flushAfterWrite: Boolean = true,
    val protocolType: ProtocolType = ProtocolType.AUTO,
    val enableProtocolDetection: Boolean = true,
    val enableEcho: Boolean = false,
    val enableHeaders: Boolean = false,
    val enableLineFeeds: Boolean = false,
    val enableSpaces: Boolean = false,
    val adaptiveTiming: Int = 1, // 0=off, 1=on, 2=auto
    val canBaudRate: Int = 500, // kbps
    val kwpBaudRate: Int = 10400, // baud
    val isoBaudRate: Int = 10400, // baud
    val enableDynamicTimeouts: Boolean = true
) {
    companion object {
        val DEFAULT = ScannerConfig()
        
        val BLUETOOTH = ScannerConfig(
            connectionTimeoutMs = 15_000L,
            readTimeoutMs = 5_000L,
            autoReconnect = true,
            maxReconnectAttempts = 3,
            bufferSize = 4096
        )
        
        val WIFI = ScannerConfig(
            connectionTimeoutMs = 5_000L,
            readTimeoutMs = 3_000L,
            autoReconnect = true,
            maxReconnectAttempts = 5,
            bufferSize = 8192,
            keepAliveIntervalMs = 30_000L
        )
        
        val USB = ScannerConfig(
            connectionTimeoutMs = 3_000L,
            readTimeoutMs = 2_000L,
            autoReconnect = false,
            bufferSize = 8192
        )
        
        val J2534 = ScannerConfig(
            connectionTimeoutMs = 10_000L,
            readTimeoutMs = 10_000L,
            writeTimeoutMs = 10_000L,
            autoReconnect = false,
            bufferSize = 16384
        )
    }
}

/**
 * Scanner configuration manager for managing and persisting scanner settings.
 *
 * This manager provides:
 * - Configuration storage and retrieval
 * - Default configuration profiles
 * - Configuration validation
 * - Automatic configuration optimization
 * - Configuration persistence across app sessions
 *
 * @param context Android context for file operations
 * @param dispatcher Coroutine dispatcher for I/O operations
 *
 * @author SpaceTec Development Team
 * @since 1.0.0
 */
class ScannerConfigurationManager(
    private val context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val configDir = File(context.filesDir, "scanner_configs")
    private val configs = ConcurrentHashMap<String, ScannerConfig>()
    private val json = Json { ignoreUnknownKeys = true }

    init {
        configDir.mkdirs()
    }

    /**
     * Gets a configuration by ID.
     *
     * @param configId Configuration ID
     * @return Configuration or null if not found
     */
    fun getConfiguration(configId: String): ScannerConfig? {
        return configs[configId] ?: loadConfiguration(configId)
    }

    /**
     * Gets the default configuration for a connection type.
     *
     * @param connectionType Type of connection
     * @return Default configuration for the connection type
     */
    fun getDefaultConfiguration(connectionType: ScannerConnectionType): ScannerConfig {
        return when (connectionType) {
            ScannerConnectionType.BLUETOOTH_CLASSIC,
            ScannerConnectionType.BLUETOOTH_LE -> ScannerConfig.BLUETOOTH
            ScannerConnectionType.WIFI -> ScannerConfig.WIFI
            ScannerConnectionType.USB -> ScannerConfig.USB
            ScannerConnectionType.J2534 -> ScannerConfig.J2534
            else -> ScannerConfig.DEFAULT
        }
    }

    /**
     * Saves a configuration.
     *
     * @param configId Configuration ID
     * @param config Configuration to save
     */
    suspend fun saveConfiguration(configId: String, config: ScannerConfig) = withContext(dispatcher) {
        configs[configId] = config
        val configFile = File(configDir, "$configId.json")
        configFile.writeText(json.encodeToString(ScannerConfig.serializer(), config))
    }

    /**
     * Loads a configuration from persistent storage.
     *
     * @param configId Configuration ID
     * @return Loaded configuration or null if not found
     */
    private fun loadConfiguration(configId: String): ScannerConfig? {
        val configFile = File(configDir, "$configId.json")
        if (!configFile.exists()) return null

        return try {
            val configJson = configFile.readText()
            val config = json.decodeFromString(ScannerConfig.serializer(), configJson)
            configs[configId] = config
            config
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Deletes a configuration.
     *
     * @param configId Configuration ID to delete
     */
    suspend fun deleteConfiguration(configId: String) = withContext(dispatcher) {
        configs.remove(configId)
        val configFile = File(configDir, "$configId.json")
        configFile.delete()
    }

    /**
     * Gets all saved configuration IDs.
     *
     * @return List of configuration IDs
     */
    fun getAllConfigurationIds(): List<String> {
        val storedIds = configDir.listFiles()?.mapNotNull { file ->
            if (file.extension == "json") {
                file.nameWithoutExtension
            } else null
        } ?: emptyList()

        return (configs.keys + storedIds).distinct()
    }

    /**
     * Validates a configuration.
     *
     * @param config Configuration to validate
     * @return True if configuration is valid, false otherwise
     */
    fun validateConfiguration(config: ScannerConfig): Boolean {
        return config.connectionTimeoutMs > 0 &&
               config.readTimeoutMs > 0 &&
               config.writeTimeoutMs > 0 &&
               config.maxReconnectAttempts >= 0 &&
               config.reconnectDelayMs >= 0 &&
               config.maxReconnectDelayMs >= config.reconnectDelayMs &&
               config.bufferSize > 0 &&
               config.canBaudRate in 10..1000 &&
               config.kwpBaudRate in 9600..115200 &&
               config.isoBaudRate in 9600..115200
    }

    /**
     * Creates an optimized configuration based on device and network characteristics.
     *
     * @param connectionType Type of connection
     * @param networkQuality Estimated network quality (0.0-1.0)
     * @param deviceCapabilities Device capabilities
     * @return Optimized configuration
     */
    fun createOptimizedConfiguration(
        connectionType: ScannerConnectionType,
        networkQuality: Float = 1.0f,
        deviceCapabilities: DeviceCapabilities = DeviceCapabilities()
    ): ScannerConfig {
        val baseConfig = getDefaultConfiguration(connectionType)
        
        return when (connectionType) {
            ScannerConnectionType.WIFI -> {
                // Adjust timeouts based on network quality
                val timeoutMultiplier = when {
                    networkQuality > 0.8f -> 1.0f
                    networkQuality > 0.6f -> 1.5f
                    networkQuality > 0.4f -> 2.0f
                    else -> 3.0f
                }
                
                baseConfig.copy(
                    readTimeoutMs = (baseConfig.readTimeoutMs * timeoutMultiplier).toLong(),
                    writeTimeoutMs = (baseConfig.writeTimeoutMs * timeoutMultiplier).toLong(),
                    keepAliveIntervalMs = if (networkQuality > 0.5f) 30_000L else 15_000L
                )
            }
            ScannerConnectionType.BLUETOOTH_CLASSIC,
            ScannerConnectionType.BLUETOOTH_LE -> {
                // Bluetooth may need longer timeouts for discovery
                baseConfig.copy(
                    connectionTimeoutMs = if (deviceCapabilities.isLowPowerMode) 20_000L else 15_000L,
                    readTimeoutMs = if (deviceCapabilities.isLowPowerMode) 8_000L else 5_000L
                )
            }
            else -> baseConfig
        }
    }

    /**
     * Migrates old configuration format to new format.
     *
     * @param oldConfig Old configuration format
     * @return Migrated configuration
     */
    fun migrateConfiguration(oldConfig: Map<String, Any>): ScannerConfig {
        return ScannerConfig(
            connectionTimeoutMs = (oldConfig["connectionTimeoutMs"] as? Number)?.toLong() ?: 10_000L,
            readTimeoutMs = (oldConfig["readTimeoutMs"] as? Number)?.toLong() ?: 5_000L,
            writeTimeoutMs = (oldConfig["writeTimeoutMs"] as? Number)?.toLong() ?: 5_000L,
            autoReconnect = (oldConfig["autoReconnect"] as? Boolean) ?: true,
            maxReconnectAttempts = (oldConfig["maxReconnectAttempts"] as? Number)?.toInt() ?: 3,
            reconnectDelayMs = (oldConfig["reconnectDelayMs"] as? Number)?.toLong() ?: 1_000L,
            maxReconnectDelayMs = (oldConfig["maxReconnectDelayMs"] as? Number)?.toLong() ?: 10_000L,
            bufferSize = (oldConfig["bufferSize"] as? Number)?.toInt() ?: 4096,
            keepAliveIntervalMs = (oldConfig["keepAliveIntervalMs"] as? Number)?.toLong() ?: 0L,
            flushAfterWrite = (oldConfig["flushAfterWrite"] as? Boolean) ?: true,
            protocolType = try {
                val protocolStr = oldConfig["protocolType"] as? String
                protocolStr?.let { ProtocolType.valueOf(it) } ?: ProtocolType.AUTO
            } catch (e: Exception) {
                ProtocolType.AUTO
            },
            enableProtocolDetection = (oldConfig["enableProtocolDetection"] as? Boolean) ?: true,
            enableEcho = (oldConfig["enableEcho"] as? Boolean) ?: false,
            enableHeaders = (oldConfig["enableHeaders"] as? Boolean) ?: false,
            enableLineFeeds = (oldConfig["enableLineFeeds"] as? Boolean) ?: false,
            enableSpaces = (oldConfig["enableSpaces"] as? Boolean) ?: false,
            adaptiveTiming = (oldConfig["adaptiveTiming"] as? Number)?.toInt() ?: 1,
            canBaudRate = (oldConfig["canBaudRate"] as? Number)?.toInt() ?: 500,
            kwpBaudRate = (oldConfig["kwpBaudRate"] as? Number)?.toInt() ?: 10400,
            isoBaudRate = (oldConfig["isoBaudRate"] as? Number)?.toInt() ?: 10400,
            enableDynamicTimeouts = (oldConfig["enableDynamicTimeouts"] as? Boolean) ?: true
        )
    }

    /**
     * Clears all cached configurations (does not delete persisted files).
     */
    fun clearCache() {
        configs.clear()
    }

    /**
     * Gets statistics about configuration usage.
     *
     * @return Configuration statistics
     */
    fun getStatistics(): ConfigurationStatistics {
        val allIds = getAllConfigurationIds()
        val loadedCount = configs.size
        val fileCount = configDir.listFiles()?.size ?: 0

        return ConfigurationStatistics(
            totalConfigurations = allIds.size,
            loadedConfigurations = loadedCount,
            fileConfigurations = fileCount,
            cachedConfigurations = configs.keys.toList()
        )
    }

    companion object {
        /**
         * Creates a scanner configuration manager.
         *
         * @param context Android context
         * @return Scanner configuration manager
         */
        fun create(context: Context): ScannerConfigurationManager {
            return ScannerConfigurationManager(context)
        }
    }
}

/**
 * Device capabilities for configuration optimization.
 */
data class DeviceCapabilities(
    val isLowPowerMode: Boolean = false,
    val availableMemory: Long = Runtime.getRuntime().maxMemory(),
    val isOnBattery: Boolean = false,
    val networkType: String = "unknown"
)

/**
 * Configuration statistics.
 */
data class ConfigurationStatistics(
    val totalConfigurations: Int,
    val loadedConfigurations: Int,
    val fileConfigurations: Int,
    val cachedConfigurations: List<String>
)