/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 *
 * This file is part of the SpaceTec professional automotive diagnostic system.
 */

package com.spacetec.testing.mocks

import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Comprehensive mock connection system for testing all connection types.
 *
 * Provides configurable behavior patterns, error injection, performance simulation,
 * and security scenario testing capabilities.
 *
 * ## Features
 * - Configurable response patterns
 * - Error injection and failure simulation
 * - Performance characteristic simulation
 * - Security scenario testing
 * - Network condition simulation
 * - Realistic timing simulation
 *
 * @author SpaceTec Development Team
 * @since 1.0.0
 */
class MockConnectionSystem {
    
    companion object {
        /**
         * Creates a mock connection for the specified type.
         */
        fun createMockConnection(
            type: ScannerConnectionType,
            config: MockConnectionConfig = MockConnectionConfig()
        ): MockConnection {
            return when (type) {
                ScannerConnectionType.BLUETOOTH_CLASSIC -> MockBluetoothClassicConnection(config)
                ScannerConnectionType.BLUETOOTH_LE -> MockBluetoothLEConnection(config)
                ScannerConnectionType.WIFI -> MockWiFiConnection(config)
                ScannerConnectionType.USB -> MockUSBConnection(config)
            }
        }
    }
}

/**
 * Configuration for mock connection behavior.
 */
data class MockConnectionConfig(
    val responsePatterns: Map<String, String> = defaultResponsePatterns,
    val errorInjection: ErrorInjectionConfig = ErrorInjectionConfig(),
    val performanceProfile: PerformanceProfile = PerformanceProfile(),
    val securityProfile: SecurityProfile = SecurityProfile(),
    val networkConditions: NetworkConditions = NetworkConditions()
) {
    companion object {
        val defaultResponsePatterns = mapOf(
            "ATZ" to "ELM327 v1.5\r>",
            "ATI" to "ELM327 v1.5\r>",
            "ATE0" to "OK\r>",
            "ATL0" to "OK\r>",
            "ATS0" to "OK\r>",
            "ATH1" to "OK\r>",
            "ATSP0" to "OK\r>",
            "0100" to "41 00 BE 3E B8 11\r>",
            "010C" to "41 0C 1A F8\r>",
            "010D" to "41 0D 4B\r>",
            "03" to "43 01 P0171\r>",
            "04" to "44\r>"
        )
    }
}

/**
 * Error injection configuration.
 */
data class ErrorInjectionConfig(
    val connectionFailureRate: Double = 0.0,
    val communicationErrorRate: Double = 0.0,
    val timeoutRate: Double = 0.0,
    val disconnectionRate: Double = 0.0,
    val corruptionRate: Double = 0.0,
    val failurePatterns: List<FailurePattern> = emptyList()
)

/**
 * Specific failure pattern to inject.
 */
data class FailurePattern(
    val trigger: String,
    val failureType: FailureType,
    val count: Int = 1
)

enum class FailureType {
    CONNECTION_FAILURE,
    COMMUNICATION_ERROR,
    TIMEOUT,
    DISCONNECTION,
    DATA_CORRUPTION,
    SECURITY_VIOLATION
}

/**
 * Performance characteristics to simulate.
 */
data class PerformanceProfile(
    val baseLatency: Long = 50L,
    val latencyVariation: Long = 20L,
    val throughputBps: Long = 10_000L,
    val connectionTime: Long = 1000L,
    val disconnectionDetectionTime: Long = 2000L,
    val responseTimeProfile: ResponseTimeProfile = ResponseTimeProfile()
)

/**
 * Response time characteristics for different operations.
 */
data class ResponseTimeProfile(
    val initCommands: Long = 100L,
    val obdCommands: Long = 50L,
    val dtcCommands: Long = 200L,
    val protocolDetection: Long = 500L
)

/**
 * Security scenario configuration.
 */
data class SecurityProfile(
    val enableAuthentication: Boolean = false,
    val enableEncryption: Boolean = false,
    val certificateValidation: Boolean = false,
    val securityViolations: List<SecurityViolation> = emptyList()
)

/**
 * Security violation to simulate.
 */
data class SecurityViolation(
    val type: SecurityViolationType,
    val trigger: String,
    val response: String
)

enum class SecurityViolationType {
    AUTHENTICATION_FAILURE,
    CERTIFICATE_INVALID,
    DATA_TAMPERING,
    UNAUTHORIZED_ACCESS
}

/**
 * Network condition simulation.
 */
data class NetworkConditions(
    val signalStrength: Int = -50,
    val packetLoss: Double = 0.0,
    val jitter: Long = 0L,
    val bandwidth: Long = 1_000_000L,
    val mtu: Int = 512
)

/**
 * Simple mock connection interface for testing.
 */
interface MockConnection {
    val connectionId: String
    val connectionType: ScannerConnectionType
    val isConnected: Boolean
    
    suspend fun connect(address: String): Boolean
    suspend fun disconnect()
    suspend fun write(data: ByteArray): Int
    suspend fun read(timeout: Long): ByteArray
    suspend fun sendAndReceive(command: String, timeout: Long = 5000L): String
    fun getStatistics(): ConnectionStatistics
    fun release()
}

/**
 * Connection statistics for testing.
 */
data class ConnectionStatistics(
    val bytesSent: Long = 0,
    val bytesReceived: Long = 0,
    val commandsSent: Int = 0,
    val responsesReceived: Int = 0,
    val errors: Int = 0
)

/**
 * Base mock connection implementation.
 */
abstract class BaseMockConnection(
    protected val mockConfig: MockConnectionConfig
) : MockConnection {
    
    override val connectionId: String = java.util.UUID.randomUUID().toString()
    protected var connected = false
    protected var commandCount = 0
    protected var bytesSent = 0L
    protected var bytesReceived = 0L
    
    override val isConnected: Boolean get() = connected
    
    override suspend fun connect(address: String): Boolean {
        delay(mockConfig.performanceProfile.connectionTime)
        
        if (shouldInjectError(FailureType.CONNECTION_FAILURE)) {
            return false
        }
        
        connected = true
        return true
    }
    
    override suspend fun disconnect() {
        connected = false
    }
    
    override suspend fun write(data: ByteArray): Int {
        if (!connected) throw RuntimeException("Not connected")
        
        delay(mockConfig.performanceProfile.baseLatency)
        
        if (shouldInjectError(FailureType.COMMUNICATION_ERROR)) {
            throw RuntimeException("Communication error")
        }
        
        commandCount++
        bytesSent += data.size
        return data.size
    }
    
    override suspend fun read(timeout: Long): ByteArray {
        if (!connected) throw RuntimeException("Not connected")
        
        delay(mockConfig.performanceProfile.baseLatency)
        
        if (shouldInjectError(FailureType.TIMEOUT)) {
            throw RuntimeException("Timeout")
        }
        
        val response = getResponseForLastCommand()
        val responseBytes = response.toByteArray()
        bytesReceived += responseBytes.size
        return responseBytes
    }
    
    override suspend fun sendAndReceive(command: String, timeout: Long): String {
        write("$command\r".toByteArray())
        val response = read(timeout)
        return String(response)
    }
    
    override fun getStatistics(): ConnectionStatistics {
        return ConnectionStatistics(
            bytesSent = bytesSent,
            bytesReceived = bytesReceived,
            commandsSent = commandCount,
            responsesReceived = commandCount,
            errors = 0
        )
    }
    
    override fun release() {
        connected = false
    }
    
    private fun shouldInjectError(failureType: FailureType): Boolean {
        val rate = when (failureType) {
            FailureType.CONNECTION_FAILURE -> mockConfig.errorInjection.connectionFailureRate
            FailureType.COMMUNICATION_ERROR -> mockConfig.errorInjection.communicationErrorRate
            FailureType.TIMEOUT -> mockConfig.errorInjection.timeoutRate
            FailureType.DISCONNECTION -> mockConfig.errorInjection.disconnectionRate
            FailureType.DATA_CORRUPTION -> mockConfig.errorInjection.corruptionRate
            FailureType.SECURITY_VIOLATION -> 0.0
        }
        return Random.nextDouble() < rate
    }
    
    private fun getResponseForLastCommand(): String {
        // Simple response mapping
        return mockConfig.responsePatterns.values.firstOrNull() ?: "OK\r>"
    }
}

/**
 * Mock Bluetooth Classic connection.
 */
class MockBluetoothClassicConnection(
    config: MockConnectionConfig
) : BaseMockConnection(config) {
    override val connectionType = ScannerConnectionType.BLUETOOTH_CLASSIC
}

/**
 * Mock Bluetooth LE connection.
 */
class MockBluetoothLEConnection(
    config: MockConnectionConfig
) : BaseMockConnection(config) {
    override val connectionType = ScannerConnectionType.BLUETOOTH_LE
}

/**
 * Mock WiFi connection.
 */
class MockWiFiConnection(
    config: MockConnectionConfig
) : BaseMockConnection(config) {
    override val connectionType = ScannerConnectionType.WIFI
}

/**
 * Mock USB connection.
 */
class MockUSBConnection(
    config: MockConnectionConfig
) : BaseMockConnection(config) {
    override val connectionType = ScannerConnectionType.USB
}

/**
 * Mock connection factory for testing.
 */
class MockConnectionFactory(
    private val defaultConfig: MockConnectionConfig = MockConnectionConfig(),
    private val connectionConfigs: Map<ScannerConnectionType, MockConnectionConfig> = emptyMap()
) {
    
    fun createConnection(type: ScannerConnectionType): MockConnection {
        val config = connectionConfigs[type] ?: defaultConfig
        return MockConnectionSystem.createMockConnection(type, config)
    }
    
    fun createConnectionForAddress(address: String): MockConnection {
        val type = detectConnectionTypeFromAddress(address)
        return createConnection(type)
    }
    
    /**
     * Detects connection type from address format.
     */
    private fun detectConnectionTypeFromAddress(address: String): ScannerConnectionType {
        return when {
            // Bluetooth MAC address format
            address.matches(Regex("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$")) -> {
                ScannerConnectionType.BLUETOOTH_CLASSIC
            }
            // IP address or hostname
            address.contains(".") && (
                address.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+(:\\d+)?")) ||
                address.contains(".local") ||
                address.contains(".com")
            ) -> {
                ScannerConnectionType.WIFI
            }
            // USB device path
            address.startsWith("/dev/tty") || 
            address.startsWith("COM") || 
            address.contains("USB") -> {
                ScannerConnectionType.USB
            }
            // Default to Bluetooth Classic
            else -> ScannerConnectionType.BLUETOOTH_CLASSIC
        }
    }
}

/**
 * Scenario builder for creating test configurations.
 */
class ScenarioBuilder {
    
    private var responsePatterns = MockConnectionConfig.defaultResponsePatterns.toMutableMap()
    private var errorInjection = ErrorInjectionConfig()
    private var performanceProfile = PerformanceProfile()
    private var securityProfile = SecurityProfile()
    private var networkConditions = NetworkConditions()
    
    fun withResponse(command: String, response: String): ScenarioBuilder {
        responsePatterns[command] = response
        return this
    }
    
    fun withLatency(baseLatency: Long, variation: Long = 0L): ScenarioBuilder {
        performanceProfile = performanceProfile.copy(
            baseLatency = baseLatency,
            latencyVariation = variation
        )
        return this
    }
    
    fun build(): MockConnectionConfig {
        return MockConnectionConfig(
            responsePatterns = responsePatterns.toMap(),
            errorInjection = errorInjection,
            performanceProfile = performanceProfile,
            securityProfile = securityProfile,
            networkConditions = networkConditions
        )
    }
    
    companion object {
        fun create(): ScenarioBuilder = ScenarioBuilder()
    }
}

/**
 * Test scenario configuration helper.
 */
object TestScenarioConfiguration {
    
    fun connectionEstablishmentTiming(
        connectionType: ScannerConnectionType,
        targetTime: Long = 1000L
    ): MockConnectionConfig {
        return MockConnectionConfig(
            performanceProfile = PerformanceProfile(
                connectionTime = targetTime,
                baseLatency = 10L,
                latencyVariation = 5L
            ),
            errorInjection = ErrorInjectionConfig(
                connectionFailureRate = 0.0 // No failures for timing tests
            )
        )
    }
    
    fun forConnectionType(type: ScannerConnectionType): MockConnectionConfig {
        return when (type) {
            ScannerConnectionType.BLUETOOTH_CLASSIC -> bluetoothClassicScenario()
            ScannerConnectionType.BLUETOOTH_LE -> bluetoothLEScenario()
            ScannerConnectionType.WIFI -> wifiScenario()
            ScannerConnectionType.USB -> usbScenario()
        }
    }
    
    private fun bluetoothClassicScenario(): MockConnectionConfig {
        return MockConnectionConfig(
            performanceProfile = PerformanceProfile(
                connectionTime = 2000L, // Bluetooth pairing takes time
                baseLatency = 80L,
                latencyVariation = 30L
            ),
            networkConditions = NetworkConditions(
                signalStrength = -50,
                mtu = 512
            ),
            responsePatterns = MockConnectionConfig.defaultResponsePatterns
        )
    }
    
    private fun bluetoothLEScenario(): MockConnectionConfig {
        return MockConnectionConfig(
            performanceProfile = PerformanceProfile(
                connectionTime = 1500L, // BLE connection + service discovery
                baseLatency = 60L,
                latencyVariation = 20L
            ),
            networkConditions = NetworkConditions(
                signalStrength = -45,
                mtu = 244 // Typical BLE MTU after negotiation
            )
        )
    }
    
    private fun wifiScenario(): MockConnectionConfig {
        return MockConnectionConfig(
            performanceProfile = PerformanceProfile(
                connectionTime = 800L, // TCP connection
                baseLatency = 30L,
                latencyVariation = 15L,
                disconnectionDetectionTime = 2000L
            ),
            networkConditions = NetworkConditions(
                bandwidth = 1_000_000L, // 1 Mbps
                mtu = 1500,
                packetLoss = 0.01,
                jitter = 10L
            )
        )
    }
    
    private fun usbScenario(): MockConnectionConfig {
        return MockConnectionConfig(
            performanceProfile = PerformanceProfile(
                connectionTime = 500L, // USB enumeration + driver loading
                baseLatency = 10L, // Very low latency
                latencyVariation = 5L
            ),
            networkConditions = NetworkConditions(
                mtu = 8192 // Large USB buffer
            )
        )
    }
}