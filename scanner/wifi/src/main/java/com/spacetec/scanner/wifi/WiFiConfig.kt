/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 *
 * This file is part of the SpaceTec professional automotive diagnostic system.
 */

package com.spacetec.obd.scanner.wifi

import com.spacetec.obd.scanner.core.ConnectionConfig

/**
 * WiFi connection constants.
 */
object WiFiConstants {

    /**
     * Default port for WiFi OBD adapters.
     */
    const val DEFAULT_PORT = 35000

    /**
     * Alternative port used by some adapters.
     */
    const val ALTERNATIVE_PORT = 23

    /**
     * Default socket connection timeout in milliseconds.
     */
    const val SOCKET_CONNECT_TIMEOUT = 5_000L

    /**
     * Default socket read timeout in milliseconds.
     */
    const val SOCKET_READ_TIMEOUT = 3_000L

    /**
     * Default socket write timeout in milliseconds.
     */
    const val SOCKET_WRITE_TIMEOUT = 3_000L

    /**
     * Maximum connection retry attempts.
     */
    const val MAX_CONNECTION_RETRIES = 3

    /**
     * Delay between connection retries in milliseconds.
     */
    const val CONNECTION_RETRY_DELAY = 500L

    /**
     * Read buffer size.
     */
    const val READ_BUFFER_SIZE = 4096

    /**
     * Default MTU for WiFi connections.
     */
    const val DEFAULT_MTU = 1500

    /**
     * Background read interval in milliseconds.
     */
    const val BACKGROUND_READ_INTERVAL = 10L

    /**
     * Disconnection detection timeout in milliseconds.
     * Per requirement 2.3: detect disconnection within 5 seconds.
     */
    const val DISCONNECTION_DETECTION_TIMEOUT = 5_000L

    /**
     * Keep-alive interval in milliseconds.
     */
    const val KEEP_ALIVE_INTERVAL = 30_000L

    /**
     * Socket linger timeout in seconds.
     */
    const val SOCKET_LINGER_TIMEOUT = 0

    /**
     * Minimum response time threshold for timeout adjustment (ms).
     */
    const val MIN_RESPONSE_TIME_THRESHOLD = 100L

    /**
     * Maximum response time threshold for timeout adjustment (ms).
     */
    const val MAX_RESPONSE_TIME_THRESHOLD = 5_000L

    /**
     * Response time history size for averaging.
     */
    const val RESPONSE_TIME_HISTORY_SIZE = 10

    /**
     * Timeout adjustment factor when network is slow.
     */
    const val SLOW_NETWORK_TIMEOUT_FACTOR = 1.5f

    /**
     * Timeout adjustment factor when network is fast.
     */
    const val FAST_NETWORK_TIMEOUT_FACTOR = 0.8f

    /**
     * mDNS service type for OBD scanners.
     */
    const val MDNS_SERVICE_TYPE = "_obd._tcp"

    /**
     * mDNS discovery timeout in milliseconds.
     */
    const val MDNS_DISCOVERY_TIMEOUT = 10_000L

    /**
     * IP range scan timeout per host in milliseconds.
     */
    const val IP_SCAN_TIMEOUT_PER_HOST = 500L

    /**
     * Maximum concurrent IP scan connections.
     */
    const val MAX_CONCURRENT_IP_SCANS = 20
}

/**
 * WiFi TCP connection configuration.
 *
 * @property baseConfig Base connection configuration
 * @property port Port number to connect to
 * @property enableKeepAlive Whether to enable TCP keep-alive
 * @property keepAliveInterval Keep-alive interval in milliseconds
 * @property enableNoDelay Whether to enable TCP_NODELAY (disable Nagle's algorithm)
 * @property socketLingerTimeout Socket linger timeout in seconds (0 = disabled)
 * @property enableDynamicTimeout Whether to adjust timeouts based on network conditions
 * @property minDynamicTimeout Minimum timeout when using dynamic adjustment
 * @property maxDynamicTimeout Maximum timeout when using dynamic adjustment
 */
data class WiFiConfig(
    val baseConfig: ConnectionConfig = ConnectionConfig.WIFI,
    val port: Int = WiFiConstants.DEFAULT_PORT,
    val enableKeepAlive: Boolean = true,
    val keepAliveInterval: Long = WiFiConstants.KEEP_ALIVE_INTERVAL,
    val enableNoDelay: Boolean = true,
    val socketLingerTimeout: Int = WiFiConstants.SOCKET_LINGER_TIMEOUT,
    val enableDynamicTimeout: Boolean = true,
    val minDynamicTimeout: Long = 1_000L,
    val maxDynamicTimeout: Long = 30_000L
) {
    companion object {
        /**
         * Default configuration.
         */
        val DEFAULT = WiFiConfig()

        /**
         * Configuration for fast local network.
         */
        val FAST_NETWORK = WiFiConfig(
            baseConfig = ConnectionConfig.WIFI.copy(
                connectionTimeout = 3_000L,
                readTimeout = 2_000L
            ),
            enableDynamicTimeout = true,
            minDynamicTimeout = 500L,
            maxDynamicTimeout = 10_000L
        )

        /**
         * Configuration for slow/unreliable network.
         */
        val SLOW_NETWORK = WiFiConfig(
            baseConfig = ConnectionConfig.WIFI.copy(
                connectionTimeout = 15_000L,
                readTimeout = 10_000L,
                maxReconnectAttempts = 5
            ),
            enableDynamicTimeout = true,
            minDynamicTimeout = 2_000L,
            maxDynamicTimeout = 60_000L
        )

        /**
         * Configuration for professional diagnostic tools.
         */
        val PROFESSIONAL = WiFiConfig(
            baseConfig = ConnectionConfig.WIFI.copy(
                connectionTimeout = 10_000L,
                readTimeout = 5_000L,
                autoReconnect = true,
                maxReconnectAttempts = 5
            ),
            enableKeepAlive = true,
            enableNoDelay = true,
            enableDynamicTimeout = true
        )
    }
}

/**
 * WiFi connection error types.
 */
enum class WiFiError {
    /**
     * WiFi is not available on the device.
     */
    WIFI_NOT_AVAILABLE,

    /**
     * WiFi is disabled.
     */
    WIFI_DISABLED,

    /**
     * Not connected to any WiFi network.
     */
    NOT_CONNECTED_TO_NETWORK,

    /**
     * Invalid IP address format.
     */
    INVALID_ADDRESS,

    /**
     * Invalid port number.
     */
    INVALID_PORT,

    /**
     * Host not found / DNS resolution failed.
     */
    HOST_NOT_FOUND,

    /**
     * Connection refused by host.
     */
    CONNECTION_REFUSED,

    /**
     * Connection timed out.
     */
    CONNECTION_TIMEOUT,

    /**
     * Network unreachable.
     */
    NETWORK_UNREACHABLE,

    /**
     * Socket creation failed.
     */
    SOCKET_CREATION_FAILED,

    /**
     * Connection lost during communication.
     */
    CONNECTION_LOST,

    /**
     * Read operation failed.
     */
    READ_FAILED,

    /**
     * Write operation failed.
     */
    WRITE_FAILED,

    /**
     * Unknown error.
     */
    UNKNOWN
}

/**
 * Network quality assessment.
 */
enum class NetworkQuality {
    /**
     * Excellent network quality (< 50ms average response).
     */
    EXCELLENT,

    /**
     * Good network quality (50-200ms average response).
     */
    GOOD,

    /**
     * Fair network quality (200-500ms average response).
     */
    FAIR,

    /**
     * Poor network quality (500-1000ms average response).
     */
    POOR,

    /**
     * Very poor network quality (> 1000ms average response).
     */
    VERY_POOR,

    /**
     * Unknown quality (not enough data).
     */
    UNKNOWN;

    companion object {
        /**
         * Determines network quality from average response time.
         */
        fun fromResponseTime(avgResponseTimeMs: Long): NetworkQuality {
            return when {
                avgResponseTimeMs < 50 -> EXCELLENT
                avgResponseTimeMs < 200 -> GOOD
                avgResponseTimeMs < 500 -> FAIR
                avgResponseTimeMs < 1000 -> POOR
                else -> VERY_POOR
            }
        }
    }
}

/**
 * Network condition monitoring data.
 */
data class NetworkCondition(
    val quality: NetworkQuality = NetworkQuality.UNKNOWN,
    val averageResponseTime: Long = 0,
    val minResponseTime: Long = Long.MAX_VALUE,
    val maxResponseTime: Long = 0,
    val packetLossRate: Float = 0f,
    val jitter: Long = 0,
    val recommendedTimeout: Long = WiFiConstants.SOCKET_READ_TIMEOUT,
    val lastUpdated: Long = System.currentTimeMillis()
) {
    /**
     * Whether the network condition is acceptable for diagnostics.
     */
    val isAcceptable: Boolean
        get() = quality != NetworkQuality.VERY_POOR && packetLossRate < 0.1f

    /**
     * Suggested action based on network condition.
     */
    val suggestedAction: String
        get() = when {
            quality == NetworkQuality.VERY_POOR -> "Network quality is very poor. Consider moving closer to the scanner or checking WiFi signal."
            quality == NetworkQuality.POOR -> "Network quality is poor. Some operations may be slow."
            packetLossRate > 0.05f -> "High packet loss detected. Check for WiFi interference."
            jitter > 200 -> "High network jitter detected. Connection may be unstable."
            else -> "Network conditions are acceptable."
        }
}
