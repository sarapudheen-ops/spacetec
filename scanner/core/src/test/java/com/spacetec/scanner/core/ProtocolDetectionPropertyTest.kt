/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 *
 * This file is part of the SpaceTec professional automotive diagnostic system.
 */

package com.spacetec.obd.scanner.core

import com.spacetec.core.common.result.Result
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger

/**
 * **Feature: scanner-connection-system, Property 9: Protocol Detection Within Timeout**
 * **Validates: Requirements 5.2, 5.3**
 * 
 * Property-based test for protocol detection within timeout.
 * 
 * This test verifies that for any vehicle with a supported protocol, automatic 
 * protocol detection should succeed within the configured timeout.
 */
class ProtocolDetectionPropertyTest : StringSpec({
    
    "Property 9: For any vehicle with a supported protocol, automatic protocol detection should succeed within the configured timeout" {
        checkAll(
            iterations = 100,
            Arb.vehicleWithProtocol()
        ) { testData ->
            // Create mock connection that supports the vehicle's protocol
            val mockConnection = MockProtocolConnection().apply {
                setSupportedProtocol(testData.supportedProtocol)
                setVehicleResponses(testData.vehicleResponses)
            }
            
            // Create protocol detection engine
            val mockProtocolDetector = MockProtocolDetector()
            val detectionEngine = ProtocolDetectionEngine(mockProtocolDetector)
            
            // Configure detection with reasonable timeout
            val config = ProtocolDetectionConfig(
                totalTimeout = 30_000L,
                protocolTimeout = 5_000L,
                enableVehicleOptimization = true
            )
            
            // Perform detection
            val startTime = System.currentTimeMillis()
            val result = detectionEngine.detectProtocol(
                connection = mockConnection,
                vehicleInfo = testData.vehicleInfo,
                config = config
            )
            val detectionTime = System.currentTimeMillis() - startTime
            
            // Verify detection succeeded
            result shouldBe Result.Success::class
            val detectionResult = (result as Result.Success).data
            
            // Verify protocol was detected correctly
            detectionResult.success shouldBe true
            detectionResult.detectedProtocol shouldBe testData.supportedProtocol
            
            // Verify detection completed within timeout
            detectionTime shouldBe { it <= config.totalTimeout }
            detectionResult.detectionTime shouldBe { it <= config.totalTimeout }
            
            // Verify confidence is reasonable for correct detection
            detectionResult.confidence shouldBe { it >= 0.5f }
            
            // Verify vehicle optimization was used if applicable
            if (testData.vehicleInfo != null) {
                // Should have tested fewer protocols due to optimization
                detectionResult.testedProtocols.size shouldBe { it <= ProtocolType.entries.size }
            }
        }
    }
    
    "Property 9a: Protocol detection should use fallback strategies when primary detection fails" {
        checkAll(
            iterations = 50,
            Arb.vehicleWithDifficultProtocol()
        ) { testData ->
            // Create mock connection that only responds to fallback attempts
            val mockConnection = MockProtocolConnection().apply {
                setSupportedProtocol(testData.supportedProtocol)
                setFailPrimaryAttempts(true)
                setVehicleResponses(testData.vehicleResponses)
            }
            
            val mockProtocolDetector = MockProtocolDetector()
            val detectionEngine = ProtocolDetectionEngine(mockProtocolDetector)
            
            val config = ProtocolDetectionConfig(
                totalTimeout = 45_000L,
                protocolTimeout = 5_000L,
                enableFallbackStrategies = true,
                enableVehicleOptimization = true
            )
            
            val result = detectionEngine.detectProtocol(
                connection = mockConnection,
                vehicleInfo = testData.vehicleInfo,
                config = config
            )
            
            // Should still succeed using fallback strategies
            result shouldBe Result.Success::class
            val detectionResult = (result as Result.Success).data
            
            detectionResult.success shouldBe true
            detectionResult.detectedProtocol shouldBe testData.supportedProtocol
            detectionResult.fallbackUsed shouldBe true
            
            // Should have tested more protocols due to fallback
            detectionResult.testedProtocols.size shouldBe { it >= 2 }
        }
    }
    
    "Property 9b: Protocol detection should timeout gracefully when no protocol is supported" {
        checkAll(
            iterations = 30,
            Arb.vehicleWithNoSupportedProtocol()
        ) { testData ->
            // Create mock connection that doesn't support any protocol
            val mockConnection = MockProtocolConnection().apply {
                setSupportedProtocol(null) // No supported protocol
                setVehicleResponses(testData.vehicleResponses)
            }
            
            val mockProtocolDetector = MockProtocolDetector()
            val detectionEngine = ProtocolDetectionEngine(mockProtocolDetector)
            
            val config = ProtocolDetectionConfig(
                totalTimeout = 10_000L, // Shorter timeout for failure case
                protocolTimeout = 2_000L,
                enableFallbackStrategies = true
            )
            
            val startTime = System.currentTimeMillis()
            val result = detectionEngine.detectProtocol(
                connection = mockConnection,
                vehicleInfo = testData.vehicleInfo,
                config = config
            )
            val detectionTime = System.currentTimeMillis() - startTime
            
            // Should fail but within timeout
            result shouldBe Result.Error::class
            detectionTime shouldBe { it <= config.totalTimeout + 1000 } // Allow small margin
            
            // Should have attempted multiple protocols
            val testedCount = mockConnection.getTestedProtocols().size
            testedCount shouldBe { it >= 3 } // Should have tried several protocols
        }
    }
    
    "Property 9c: Protocol detection should optimize order based on vehicle information" {
        checkAll(
            iterations = 50,
            Arb.vehicleWithOptimizationHints()
        ) { testData ->
            val mockConnection = MockProtocolConnection().apply {
                setSupportedProtocol(testData.expectedFirstProtocol)
                setVehicleResponses(testData.vehicleResponses)
            }
            
            val mockProtocolDetector = MockProtocolDetector()
            val detectionEngine = ProtocolDetectionEngine(mockProtocolDetector)
            
            val config = ProtocolDetectionConfig(
                enableVehicleOptimization = true,
                stopOnFirstMatch = true
            )
            
            val result = detectionEngine.detectProtocol(
                connection = mockConnection,
                vehicleInfo = testData.vehicleInfo,
                config = config
            )
            
            result shouldBe Result.Success::class
            val detectionResult = (result as Result.Success).data
            
            // Should have found the protocol quickly due to optimization
            detectionResult.success shouldBe true
            detectionResult.detectedProtocol shouldBe testData.expectedFirstProtocol
            
            // Should have tested fewer protocols due to optimization
            detectionResult.testedProtocols.size shouldBe { it <= 3 }
            
            // First tested protocol should be the optimized one
            val testedProtocols = mockConnection.getTestedProtocols()
            if (testedProtocols.isNotEmpty()) {
                testedProtocols.first() shouldBe testData.expectedFirstProtocol
            }
        }
    }
})

/**
 * Test data for vehicle with supported protocol.
 */
data class VehicleWithProtocol(
    val vehicleInfo: VehicleInfo?,
    val supportedProtocol: ProtocolType,
    val vehicleResponses: Map<String, String>
)

/**
 * Test data for vehicle with difficult protocol detection.
 */
data class VehicleWithDifficultProtocol(
    val vehicleInfo: VehicleInfo?,
    val supportedProtocol: ProtocolType,
    val vehicleResponses: Map<String, String>
)

/**
 * Test data for vehicle with no supported protocol.
 */
data class VehicleWithNoSupportedProtocol(
    val vehicleInfo: VehicleInfo?,
    val vehicleResponses: Map<String, String>
)

/**
 * Test data for vehicle with optimization hints.
 */
data class VehicleWithOptimizationHints(
    val vehicleInfo: VehicleInfo,
    val expectedFirstProtocol: ProtocolType,
    val vehicleResponses: Map<String, String>
)

/**
 * Arbitrary generator for vehicle with supported protocol.
 */
fun Arb.Companion.vehicleWithProtocol(): Arb<VehicleWithProtocol> = arbitrary { rs ->
    val protocols = ProtocolType.entries
    val supportedProtocol = protocols.random(rs.random)
    
    val vehicleInfo = if (rs.random.nextBoolean()) {
        VehicleInfo(
            make = listOf("Toyota", "Ford", "BMW", "Mercedes", "Audi", "Honda").random(rs.random),
            year = rs.random.nextInt(2000, 2024),
            model = "Test Model",
            region = listOf("USA", "Europe", "Asia").random(rs.random)
        )
    } else null
    
    val responses = mutableMapOf<String, String>()
    
    // Add protocol-specific responses
    when (supportedProtocol) {
        ProtocolType.ISO_15765_4_CAN_11BIT_500K,
        ProtocolType.ISO_15765_4_CAN_29BIT_500K -> {
            responses["0100"] = "41 00 BE 3E B8 11"
            responses["ATDP"] = "ISO 15765-4 CAN (11 bit ID, 500 kbaud)"
        }
        ProtocolType.SAE_J1850_VPW -> {
            responses["0100"] = "41 00 BE 3E B8 11"
            responses["ATDP"] = "SAE J1850 VPW"
        }
        ProtocolType.ISO_9141_2 -> {
            responses["0100"] = "41 00 BE 3E B8 11"
            responses["ATDP"] = "ISO 9141-2"
        }
        else -> {
            responses["0100"] = "41 00 BE 3E B8 11"
            responses["ATDP"] = supportedProtocol.displayName
        }
    }
    
    VehicleWithProtocol(
        vehicleInfo = vehicleInfo,
        supportedProtocol = supportedProtocol,
        vehicleResponses = responses
    )
}

/**
 * Arbitrary generator for vehicle with difficult protocol.
 */
fun Arb.Companion.vehicleWithDifficultProtocol(): Arb<VehicleWithDifficultProtocol> = arbitrary { rs ->
    val protocols = listOf(
        ProtocolType.ISO_14230_4_KWP_SLOW,
        ProtocolType.ISO_9141_2,
        ProtocolType.SAE_J1850_PWM
    )
    val supportedProtocol = protocols.random(rs.random)
    
    val vehicleInfo = VehicleInfo(
        make = listOf("Volvo", "Saab", "Peugeot", "Citroen").random(rs.random),
        year = rs.random.nextInt(1996, 2008), // Older vehicles
        model = "Legacy Model"
    )
    
    val responses = mapOf(
        "0100" to "41 00 BE 3E B8 11",
        "ATDP" to supportedProtocol.displayName
    )
    
    VehicleWithDifficultProtocol(
        vehicleInfo = vehicleInfo,
        supportedProtocol = supportedProtocol,
        vehicleResponses = responses
    )
}

/**
 * Arbitrary generator for vehicle with no supported protocol.
 */
fun Arb.Companion.vehicleWithNoSupportedProtocol(): Arb<VehicleWithNoSupportedProtocol> = arbitrary { rs ->
    val vehicleInfo = if (rs.random.nextBoolean()) {
        VehicleInfo(
            make = "Unknown",
            year = rs.random.nextInt(1990, 2000), // Very old vehicle
            model = "Unsupported Model"
        )
    } else null
    
    val responses = mapOf(
        "0100" to "NO DATA",
        "ATDP" to "UNKNOWN PROTOCOL"
    )
    
    VehicleWithNoSupportedProtocol(
        vehicleInfo = vehicleInfo,
        vehicleResponses = responses
    )
}

/**
 * Arbitrary generator for vehicle with optimization hints.
 */
fun Arb.Companion.vehicleWithOptimizationHints(): Arb<VehicleWithOptimizationHints> = arbitrary { rs ->
    val scenarios = listOf(
        // Modern vehicle should try CAN first
        Triple(
            VehicleInfo(make = "Toyota", year = 2015, model = "Camry"),
            ProtocolType.ISO_15765_4_CAN_11BIT_500K,
            mapOf("0100" to "41 00 BE 3E B8 11", "ATDP" to "ISO 15765-4 CAN")
        ),
        // GM vehicle should try VPW
        Triple(
            VehicleInfo(make = "Chevrolet", year = 2005, model = "Silverado"),
            ProtocolType.SAE_J1850_VPW,
            mapOf("0100" to "41 00 BE 3E B8 11", "ATDP" to "SAE J1850 VPW")
        ),
        // European vehicle should try KWP
        Triple(
            VehicleInfo(make = "BMW", year = 2003, model = "3 Series"),
            ProtocolType.ISO_14230_4_KWP_FAST,
            mapOf("0100" to "41 00 BE 3E B8 11", "ATDP" to "ISO 14230-4 KWP")
        ),
        // Asian vehicle should try ISO 9141
        Triple(
            VehicleInfo(make = "Honda", year = 2000, model = "Civic"),
            ProtocolType.ISO_9141_2,
            mapOf("0100" to "41 00 BE 3E B8 11", "ATDP" to "ISO 9141-2")
        )
    )
    
    val scenario = scenarios.random(rs.random)
    
    VehicleWithOptimizationHints(
        vehicleInfo = scenario.first,
        expectedFirstProtocol = scenario.second,
        vehicleResponses = scenario.third
    )
}

/**
 * Mock protocol connection for testing.
 */
class MockProtocolConnection : ScannerConnection {
    
    private var supportedProtocol: ProtocolType? = null
    private var vehicleResponses: Map<String, String> = emptyMap()
    private var failPrimaryAttempts = false
    private val testedProtocols = mutableListOf<ProtocolType>()
    private var attemptCount = 0
    
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Connected(
        ConnectionInfo(remoteAddress = "TEST:PROTOCOL")
    ))
    
    override val connectionId: String = "mock-protocol-connection"
    override val connectionType = com.spacetec.core.domain.models.scanner.ScannerConnectionType.BLUETOOTH_CLASSIC
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    override val incomingData = kotlinx.coroutines.flow.MutableSharedFlow<ByteArray>().asSharedFlow()
    override val isConnected: Boolean = true
    override val config = ConnectionConfig.DEFAULT
    
    fun setSupportedProtocol(protocol: ProtocolType?) {
        supportedProtocol = protocol
    }
    
    fun setVehicleResponses(responses: Map<String, String>) {
        vehicleResponses = responses
    }
    
    fun setFailPrimaryAttempts(fail: Boolean) {
        failPrimaryAttempts = fail
    }
    
    fun getTestedProtocols(): List<ProtocolType> = testedProtocols.toList()
    
    override suspend fun connect(address: String, config: ConnectionConfig): Result<ConnectionInfo> {
        return Result.Success(ConnectionInfo(remoteAddress = address))
    }
    
    override suspend fun disconnect(graceful: Boolean) {
        _connectionState.value = ConnectionState.Disconnected()
    }
    
    override suspend fun reconnect(): Result<ConnectionInfo> {
        return Result.Success(ConnectionInfo(remoteAddress = "TEST:PROTOCOL"))
    }
    
    override suspend fun write(data: ByteArray): Result<Int> {
        return Result.Success(data.size)
    }
    
    override suspend fun sendCommand(command: String): Result<Unit> {
        return Result.Success(Unit)
    }
    
    override suspend fun read(timeout: Long): Result<ByteArray> {
        return Result.Success("OK>".toByteArray())
    }
    
    override suspend fun readUntil(terminator: String, timeout: Long): Result<String> {
        return Result.Success("OK")
    }
    
    override suspend fun sendAndReceive(
        command: String,
        timeout: Long,
        terminator: String
    ): Result<String> {
        delay(100) // Simulate protocol detection time
        
        // Handle protocol configuration commands
        when {
            command.startsWith("ATSP") -> {
                val protocolCode = command.substring(4)
                val protocol = when (protocolCode) {
                    "0" -> null // Auto detect
                    "3" -> ProtocolType.ISO_9141_2
                    "4" -> ProtocolType.ISO_14230_4_KWP_SLOW
                    "5" -> ProtocolType.ISO_14230_4_KWP_FAST
                    "6" -> ProtocolType.ISO_15765_4_CAN_11BIT_500K
                    "7" -> ProtocolType.ISO_15765_4_CAN_29BIT_500K
                    "8" -> ProtocolType.ISO_15765_4_CAN_11BIT_250K
                    "9" -> ProtocolType.ISO_15765_4_CAN_29BIT_250K
                    "A" -> ProtocolType.SAE_J1850_PWM
                    "B" -> ProtocolType.SAE_J1850_VPW
                    else -> null
                }
                
                protocol?.let { testedProtocols.add(it) }
                
                return Result.Success("OK")
            }
            
            command == "0100" -> {
                // OBD Mode 01 PID 00 - test if protocol works
                attemptCount++
                
                return if (supportedProtocol != null) {
                    // If we're failing primary attempts, fail the first few tries
                    if (failPrimaryAttempts && attemptCount <= 3) {
                        Result.Error(Exception("NO DATA"))
                    } else {
                        Result.Success(vehicleResponses["0100"] ?: "41 00 BE 3E B8 11")
                    }
                } else {
                    Result.Error(Exception("NO DATA"))
                }
            }
            
            vehicleResponses.containsKey(command) -> {
                return Result.Success(vehicleResponses[command]!!)
            }
            
            else -> {
                return Result.Success("OK")
            }
        }
    }
    
    override suspend fun sendAndReceiveBytes(
        data: ByteArray,
        timeout: Long,
        expectedLength: Int
    ): Result<ByteArray> {
        return Result.Success("OK".toByteArray())
    }
    
    override suspend fun clearBuffers() {}
    
    override suspend fun available(): Int = 0
    
    override fun getStatistics(): ConnectionStatistics = ConnectionStatistics()
    
    override fun resetStatistics() {}
    
    override fun release() {}
}

/**
 * Mock protocol detector for testing.
 */
class MockProtocolDetector : com.spacetec.protocol.core.ProtocolDetector {
    
    override suspend fun detectProtocol(
        connection: ScannerConnection,
        config: com.spacetec.protocol.core.DetectionConfig
    ): com.spacetec.protocol.core.ProtocolType {
        // Simulate protocol detection by trying to send OBD command
        val result = connection.sendAndReceive("0100", 5000L)
        
        return if (result is Result.Success) {
            // Return first protocol that would work
            com.spacetec.protocol.core.ProtocolType.ISO_15765_4_CAN_11BIT_500K
        } else {
            throw com.spacetec.core.common.exceptions.ProtocolException("No protocol detected")
        }
    }
    
    override fun detectProtocolWithProgress(
        connection: ScannerConnection,
        config: com.spacetec.protocol.core.DetectionConfig
    ): kotlinx.coroutines.flow.Flow<com.spacetec.protocol.core.DetectionProgress> {
        return kotlinx.coroutines.flow.flow {
            emit(com.spacetec.protocol.core.DetectionProgress.Started)
            
            try {
                val protocol = detectProtocol(connection, config)
                emit(com.spacetec.protocol.core.DetectionProgress.Detected(protocol, 1000L))
            } catch (e: Exception) {
                emit(com.spacetec.protocol.core.DetectionProgress.Failed(
                    com.spacetec.protocol.core.ProtocolError.fromException(e),
                    emptyList()
                ))
            }
        }
    }
    
    override suspend fun testProtocol(
        connection: ScannerConnection,
        protocol: com.spacetec.protocol.core.ProtocolType
    ): Boolean {
        val result = connection.sendAndReceive("0100", 5000L)
        return result is Result.Success
    }
    
    override fun getDetectionOrder(
        vehicleYear: Int?,
        vehicleMake: String?,
        config: com.spacetec.protocol.core.DetectionConfig
    ): List<com.spacetec.protocol.core.ProtocolType> {
        return com.spacetec.protocol.core.ProtocolType.entries
    }
    
    override fun cancelDetection() {
        // Mock implementation
    }
}