/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 *
 * This file is part of the SpaceTec professional automotive diagnostic system.
 */

package com.spacetec.obd.scanner.wifi

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import com.spacetec.core.domain.models.scanner.Scanner
import com.spacetec.core.domain.models.scanner.ScannerConnectionType
import com.spacetec.obd.scanner.core.DiscoveredScanner
import com.spacetec.obd.scanner.core.DiscoveryOptions
import com.spacetec.obd.scanner.core.WiFiScannerManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WiFi scanner discovery implementation.
 *
 * Provides network discovery for WiFi-enabled OBD-II scanners using:
 * - mDNS/Bonjour service discovery (NSD)
 * - IP range scanning for direct device detection
 * - Known device management and caching
 *
 * ## Features
 *
 * - mDNS service discovery for auto-configured devices
 * - IP range scanning with concurrent connections
 * - Known device caching and management
 * - Network availability checking
 *
 * ## Usage Example
 *
 * ```kotlin
 * val discovery = WiFiDiscovery(context)
 *
 * // Start discovery
 * discovery.startDiscovery().collect { scanner ->
 *     println("Found: ${scanner.scanner.name}")
 * }
 *
 * // Or scan specific IP range
 * discovery.scanIPRange("192.168.0", 1, 254, 35000).collect { scanner ->
 *     println("Found at: ${scanner.scanner.address}")
 * }
 * ```
 *
 * @param context Android context for accessing network services
 * @param dispatcher Coroutine dispatcher for I/O operations
 *
 * @author SpaceTec Development Team
 * @since 1.0.0
 */
@Singleton
class WiFiDiscovery @Inject constructor(
    private val context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : WiFiScannerManager {

    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    private val _isDiscovering = MutableStateFlow(false)
    override val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private val _discoveredDevices = MutableSharedFlow<DiscoveredScanner>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val discoveredDevices: SharedFlow<DiscoveredScanner> = _discoveredDevices.asSharedFlow()

    // Known devices cache
    private val knownDevices = ConcurrentHashMap<String, KnownWiFiDevice>()
    private val knownDevicesLock = Mutex()

    // NSD discovery
    private var nsdManager: NsdManager? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var discoveryJob: Job? = null

    // Discovery state
    private val discoveredAddresses = ConcurrentHashMap.newKeySet<String>()

    // ═══════════════════════════════════════════════════════════════════════
    // DISCOVERY IMPLEMENTATION
    // ═══════════════════════════════════════════════════════════════════════

    override fun startDiscovery(options: DiscoveryOptions): Flow<DiscoveredScanner> = flow {
        if (_isDiscovering.value) {
            return@flow
        }

        _isDiscovering.value = true
        discoveredAddresses.clear()

        try {
            // Start mDNS discovery
            startMdnsDiscovery()

            // Also emit known devices
            knownDevicesLock.withLock {
                knownDevices.values.forEach { known ->
                    val scanner = known.toScanner()
                    val discovered = DiscoveredScanner(
                        scanner = scanner,
                        signalStrength = null,
                        isNew = false,
                        discoveredAt = System.currentTimeMillis()
                    )
                    emit(discovered)
                    _discoveredDevices.emit(discovered)
                }
            }

            // Wait for discovery duration
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < options.scanDuration) {
                delay(100)
                
                // Check for new mDNS discoveries
                // (handled by NSD callbacks)
            }

        } finally {
            stopMdnsDiscovery()
            _isDiscovering.value = false
        }
    }

    override suspend fun stopDiscovery() {
        stopMdnsDiscovery()
        discoveryJob?.cancel()
        discoveryJob = null
        _isDiscovering.value = false
    }

    override suspend fun getPairedDevices(): List<Scanner> {
        return knownDevicesLock.withLock {
            knownDevices.values.map { it.toScanner() }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // WIFI SCANNER MANAGER
    // ═══════════════════════════════════════════════════════════════════════

    override fun isWiFiEnabled(): Boolean {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        return wifiManager?.isWifiEnabled == true
    }

    override fun scanIPRange(
        baseIp: String,
        startRange: Int,
        endRange: Int,
        port: Int
    ): Flow<DiscoveredScanner> = flow {
        if (!isWiFiEnabled()) {
            return@flow
        }

        val semaphore = Semaphore(WiFiConstants.MAX_CONCURRENT_IP_SCANS)
        val jobs = mutableListOf<Job>()

        withContext(dispatcher) {
            for (i in startRange..endRange) {
                val ip = "$baseIp.$i"
                
                jobs += scope.launch {
                    semaphore.acquire()
                    try {
                        val scanner = probeHost(ip, port)
                        if (scanner != null) {
                            val discovered = DiscoveredScanner(
                                scanner = scanner,
                                signalStrength = null,
                                isNew = !knownDevices.containsKey(scanner.address),
                                discoveredAt = System.currentTimeMillis()
                            )
                            
                            // Add to known devices
                            addKnownDevice(scanner)
                            
                            _discoveredDevices.emit(discovered)
                        }
                    } finally {
                        semaphore.release()
                    }
                }
            }

            // Wait for all probes to complete
            jobs.forEach { it.join() }
        }

        // Emit all discovered devices
        knownDevices.values
            .filter { it.address.startsWith(baseIp) }
            .forEach { known ->
                emit(DiscoveredScanner(
                    scanner = known.toScanner(),
                    signalStrength = null,
                    isNew = false,
                    discoveredAt = System.currentTimeMillis()
                ))
            }
    }

    /**
     * Probes a specific host to check if it's an OBD scanner.
     */
    private suspend fun probeHost(ip: String, port: Int): Scanner? {
        return withContext(dispatcher) {
            try {
                val socket = Socket()
                socket.soTimeout = WiFiConstants.IP_SCAN_TIMEOUT_PER_HOST.toInt()
                
                withTimeoutOrNull(WiFiConstants.IP_SCAN_TIMEOUT_PER_HOST) {
                    socket.connect(
                        InetSocketAddress(ip, port),
                        WiFiConstants.IP_SCAN_TIMEOUT_PER_HOST.toInt()
                    )
                }

                if (socket.isConnected) {
                    // Try to identify the device
                    val deviceName = identifyDevice(socket) ?: "WiFi OBD Scanner"
                    socket.close()

                    Scanner.fromWiFiDevice(
                        name = deviceName,
                        ipAddress = ip,
                        port = port
                    )
                } else {
                    socket.close()
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Attempts to identify a connected device by sending ATI command.
     */
    private fun identifyDevice(socket: Socket): String? {
        return try {
            socket.soTimeout = 1000
            val output = socket.getOutputStream()
            val input = socket.getInputStream()

            // Send ATI command to identify
            output.write("ATI\r".toByteArray())
            output.flush()

            // Read response
            val buffer = ByteArray(256)
            val bytesRead = input.read(buffer)
            
            if (bytesRead > 0) {
                val response = String(buffer, 0, bytesRead).trim()
                parseDeviceName(response)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parses device name from ATI response.
     */
    private fun parseDeviceName(response: String): String? {
        // Common patterns in ATI responses
        val lines = response.split("\n", "\r").filter { it.isNotBlank() }
        
        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.contains("ELM327", ignoreCase = true) -> return "ELM327 WiFi"
                trimmed.contains("OBDLink", ignoreCase = true) -> return trimmed
                trimmed.contains("STN", ignoreCase = true) -> return trimmed
                trimmed.contains("vGate", ignoreCase = true) -> return "vGate WiFi"
                trimmed.contains("Veepeak", ignoreCase = true) -> return "Veepeak WiFi"
            }
        }

        return lines.firstOrNull { it.isNotBlank() && !it.startsWith(">") }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MDNS DISCOVERY
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Starts mDNS/Bonjour service discovery.
     */
    private fun startMdnsDiscovery() {
        try {
            nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
            
            discoveryListener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) {
                    // Discovery started
                }

                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    // Resolve the service to get IP and port
                    resolveService(serviceInfo)
                }

                override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                    // Service no longer available
                }

                override fun onDiscoveryStopped(serviceType: String) {
                    // Discovery stopped
                }

                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    // Failed to start discovery
                }

                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                    // Failed to stop discovery
                }
            }

            nsdManager?.discoverServices(
                WiFiConstants.MDNS_SERVICE_TYPE,
                NsdManager.PROTOCOL_DNS_SD,
                discoveryListener
            )
        } catch (e: Exception) {
            // mDNS not available, fall back to IP scanning
        }
    }

    /**
     * Resolves an mDNS service to get connection details.
     */
    private fun resolveService(serviceInfo: NsdServiceInfo) {
        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                // Resolution failed
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val host = serviceInfo.host?.hostAddress ?: return
                val port = serviceInfo.port
                val name = serviceInfo.serviceName ?: "WiFi OBD Scanner"

                val address = "$host:$port"
                if (discoveredAddresses.add(address)) {
                    val scanner = Scanner.fromWiFiDevice(
                        name = name,
                        ipAddress = host,
                        port = port
                    )

                    scope.launch {
                        addKnownDevice(scanner)
                        
                        val discovered = DiscoveredScanner(
                            scanner = scanner,
                            signalStrength = null,
                            isNew = true,
                            discoveredAt = System.currentTimeMillis()
                        )
                        _discoveredDevices.emit(discovered)
                    }
                }
            }
        }

        try {
            nsdManager?.resolveService(serviceInfo, resolveListener)
        } catch (e: Exception) {
            // Resolution failed
        }
    }

    /**
     * Stops mDNS discovery.
     */
    private fun stopMdnsDiscovery() {
        try {
            discoveryListener?.let { listener ->
                nsdManager?.stopServiceDiscovery(listener)
            }
        } catch (e: Exception) {
            // Ignore errors during stop
        }
        discoveryListener = null
    }


    // ═══════════════════════════════════════════════════════════════════════
    // KNOWN DEVICE MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Adds a device to the known devices cache.
     */
    suspend fun addKnownDevice(scanner: Scanner) {
        knownDevicesLock.withLock {
            knownDevices[scanner.address] = KnownWiFiDevice(
                address = scanner.address,
                name = scanner.name,
                port = extractPort(scanner.address),
                lastSeen = System.currentTimeMillis(),
                deviceType = scanner.deviceType
            )
        }
    }

    /**
     * Removes a device from the known devices cache.
     */
    suspend fun removeKnownDevice(address: String) {
        knownDevicesLock.withLock {
            knownDevices.remove(address)
        }
    }

    /**
     * Gets all known devices.
     */
    suspend fun getKnownDevices(): List<KnownWiFiDevice> {
        return knownDevicesLock.withLock {
            knownDevices.values.toList()
        }
    }

    /**
     * Clears all known devices.
     */
    suspend fun clearKnownDevices() {
        knownDevicesLock.withLock {
            knownDevices.clear()
        }
    }

    /**
     * Checks if a device is known.
     */
    fun isKnownDevice(address: String): Boolean {
        return knownDevices.containsKey(address)
    }

    /**
     * Extracts port from address string.
     */
    private fun extractPort(address: String): Int {
        return address.split(":").getOrNull(1)?.toIntOrNull() 
            ?: WiFiConstants.DEFAULT_PORT
    }

    // ═══════════════════════════════════════════════════════════════════════
    // NETWORK UTILITIES
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Gets the current WiFi network's gateway IP.
     */
    @Suppress("DEPRECATION")
    fun getGatewayIp(): String? {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return null

        val dhcpInfo = wifiManager.dhcpInfo ?: return null
        val gateway = dhcpInfo.gateway

        return if (gateway != 0) {
            intToIp(gateway)
        } else {
            null
        }
    }

    /**
     * Gets the current device's IP address on WiFi.
     */
    @Suppress("DEPRECATION")
    fun getDeviceIp(): String? {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return null

        val wifiInfo = wifiManager.connectionInfo ?: return null
        val ip = wifiInfo.ipAddress

        return if (ip != 0) {
            intToIp(ip)
        } else {
            null
        }
    }

    /**
     * Gets the base IP for the current network (e.g., "192.168.0").
     */
    fun getNetworkBaseIp(): String? {
        val deviceIp = getDeviceIp() ?: return null
        val parts = deviceIp.split(".")
        return if (parts.size == 4) {
            "${parts[0]}.${parts[1]}.${parts[2]}"
        } else {
            null
        }
    }

    /**
     * Converts an integer IP to string format.
     */
    private fun intToIp(ip: Int): String {
        return "${ip and 0xFF}.${(ip shr 8) and 0xFF}.${(ip shr 16) and 0xFF}.${(ip shr 24) and 0xFF}"
    }

    /**
     * Scans the current network for OBD scanners.
     */
    fun scanCurrentNetwork(port: Int = WiFiConstants.DEFAULT_PORT): Flow<DiscoveredScanner> {
        val baseIp = getNetworkBaseIp()
        return if (baseIp != null) {
            scanIPRange(baseIp, 1, 254, port)
        } else {
            flow { /* No network available */ }
        }
    }

    /**
     * Probes a specific address to check if it's reachable.
     */
    suspend fun probeAddress(address: String): Boolean {
        return withContext(dispatcher) {
            try {
                val (host, port) = parseAddress(address)
                val socket = Socket()
                socket.soTimeout = WiFiConstants.IP_SCAN_TIMEOUT_PER_HOST.toInt()
                
                withTimeoutOrNull(WiFiConstants.IP_SCAN_TIMEOUT_PER_HOST) {
                    socket.connect(
                        InetSocketAddress(host, port),
                        WiFiConstants.IP_SCAN_TIMEOUT_PER_HOST.toInt()
                    )
                }

                val connected = socket.isConnected
                socket.close()
                connected
            } catch (e: Exception) {
                false
            }
        }
    }

    /**
     * Parses an address string into host and port.
     */
    private fun parseAddress(address: String): Pair<String, Int> {
        val parts = address.split(":")
        return when (parts.size) {
            1 -> Pair(parts[0], WiFiConstants.DEFAULT_PORT)
            2 -> Pair(parts[0], parts[1].toIntOrNull() ?: WiFiConstants.DEFAULT_PORT)
            else -> Pair(address, WiFiConstants.DEFAULT_PORT)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Releases all resources.
     */
    fun release() {
        stopMdnsDiscovery()
        discoveryJob?.cancel()
        scope.launch { clearKnownDevices() }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // COMPANION
    // ═══════════════════════════════════════════════════════════════════════

    companion object {
        /**
         * Common OBD scanner ports to try.
         */
        val COMMON_PORTS = listOf(35000, 23, 6667, 8080)

        /**
         * Creates a WiFiDiscovery instance.
         */
        fun create(context: Context): WiFiDiscovery {
            return WiFiDiscovery(context)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// KNOWN DEVICE DATA CLASS
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Represents a known WiFi OBD device.
 */
data class KnownWiFiDevice(
    val address: String,
    val name: String,
    val port: Int,
    val lastSeen: Long,
    val deviceType: com.spacetec.core.domain.models.scanner.ScannerDeviceType = 
        com.spacetec.core.domain.models.scanner.ScannerDeviceType.GENERIC
) {
    /**
     * Converts to a Scanner domain model.
     */
    fun toScanner(): Scanner {
        return Scanner.fromWiFiDevice(
            name = name,
            ipAddress = address.split(":").first(),
            port = port
        )
    }

    /**
     * Time since last seen in milliseconds.
     */
    val timeSinceLastSeen: Long
        get() = System.currentTimeMillis() - lastSeen

    /**
     * Whether the device was seen recently (within 5 minutes).
     */
    val isRecent: Boolean
        get() = timeSinceLastSeen < 5 * 60 * 1000
}
