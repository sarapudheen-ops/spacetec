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
 * **Feature: scanner-connection-system, Property 8: Automatic Initialization by Scanner Type**
 * **Validates: Requirements 5.1, 5.4**
 * 
 * Property-based test for automatic scanner initialization.
 * 
 * This test verifies that for any scanner model, the system sends appropriate
 * initialization commands based on the scanner's capabilities and firmware version.
 */
class AutomaticInitializationPropertyTest : StringSpec({
    
    "Property 8: For any scanner model, the system should send appropriate initialization commands based on the scanner's capabilities and firmware version" {
        checkAll(
            iterations = 100,
            Arb.scannerTestData()
        ) { testData ->
            // Create mock connection that tracks commands
            val mockConnection = MockScannerConnection()
            
            // Create initializer
            val initializer = ScannerInitializer()
            
            // Initialize scanner
            val result = initializer.initializeScanner(
                connection = mockConnection,
                deviceName = testData.deviceName,
                config = InitializationConfig.DEFAULT
            )
            
            // Verify initialization succeeded
            result shouldBe Result.Success::class
            val initResult = (result as Result.Success).data
            
            // Verify appropriate commands were sent for the detected model
            val expectedModel = ScannerModel.detectFromName(testData.deviceName)
            initResult.scannerModel shouldBe expectedModel
            
            // Verify all expected commands were executed
            val executedCommands = mockConnection.getExecutedCommands()
            val expectedCommands = expectedModel.initCommands
            
            // All expected commands should have been executed
            for (expectedCommand in expectedCommands) {
                executedCommands.any { it.command == expectedCommand } shouldBe true
            }
            
            // Verify capabilities were detected based on scanner model
            initResult.detectedCapabilities.isNotEmpty() shouldBe true
            
            // Model-specific validation
            when (expectedModel) {
                ScannerModel.ELM327 -> {
                    executedCommands.any { it.command == "ATZ" } shouldBe true
                    executedCommands.any { it.command == "ATE0" } shouldBe true
                    executedCommands.any { it.command == "ATSP0" } shouldBe true
                    initResult.detectedCapabilities.contains(ScannerCapability.OBD_II) shouldBe true
                }
                ScannerModel.STN1110, ScannerModel.STN2120 -> {
                    executedCommands.any { it.command == "STDI" } shouldBe true
                    initResult.detectedCapabilities.contains(ScannerCapability.OBD_II) shouldBe true
                    initResult.detectedCapabilities.contains(ScannerCapability.CAN_BUS) shouldBe true
                }
                ScannerModel.OBDLINK_SX -> {
                    executedCommands.any { it.command == "STI" } shouldBe true
                    initResult.detectedCapabilities.contains(ScannerCapability.OBD_II) shouldBe true
                }
                else -> {
                    // Generic ELM or vGate - should have basic OBD-II
                    executedCommands.any { it.command == "ATZ" } shouldBe true
                    initResult.detectedCapabilities.contains(ScannerCapability.OBD_II) shouldBe true
                }
            }
            
            // Verify initialization was successful
            initResult.success shouldBe true
            initResult.isFullyInitialized shouldBe true
            
            // Verify timing is reasonable (should complete within 30 seconds)
            initResult.initializationTime shouldBe { it in 0..30000 }
        }
    }
    
    "Property 8a: Initialization should retry with alternative sequences when primary sequence fails" {
        checkAll(
            iterations = 50,
            Arb.scannerTestDataWithFailures()
        ) { testData ->
            // Create mock connection that fails some commands initially
            val mockConnection = MockScannerConnection().apply {
                setCommandFailurePattern(testData.failurePattern)
            }
            
            // Create initializer with alternative sequences enabled
            val initializer = ScannerInitializer()
            val config = InitializationConfig(
                enableAlternativeSequences = true,
                maxRetries = 2
            )
            
            // Initialize scanner
            val result = initializer.initializeScanner(
                connection = mockConnection,
                deviceName = testData.deviceName,
                config = config
            )
            
            // If any alternative sequence should work, initialization should succeed
            val expectedModel = ScannerModel.detectFromName(testData.deviceName)
            val hasWorkingAlternative = expectedModel.alternativeCommands.any { altCommands ->
                altCommands.none { command -> testData.failurePattern.contains(command) }
            }
            
            if (hasWorkingAlternative || testData.failurePattern.isEmpty()) {
                result shouldBe Result.Success::class
                val initResult = (result as Result.Success).data
                initResult.success shouldBe true
            }
            
            // Verify retry attempts were made
            val executedCommands = mockConnection.getExecutedCommands()
            val retryCount = executedCommands.count { it.command == "ATZ" }
            
            if (testData.failurePattern.contains("ATZ")) {
                // Should have tried multiple times or alternative sequences
                retryCount shouldBe { it >= 1 }
            }
        }
    }
    
    "Property 8b: Scanner capabilities should be correctly detected based on model and responses" {
        checkAll(
            iterations = 100,
            Arb.scannerModelWithCapabilities()
        ) { testData ->
            val mockConnection = MockScannerConnection().apply {
                setCapabilityResponses(testData.capabilityResponses)
            }
            
            val initializer = ScannerInitializer()
            val config = InitializationConfig(detectCapabilities = true)
            
            val result = initializer.initializeScanner(
                connection = mockConnection,
                deviceName = testData.model.displayName,
                config = config
            )
            
            result shouldBe Result.Success::class
            val initResult = (result as Result.Success).data
            
            // Should detect at least the model's base capabilities
            for (capability in testData.model.capabilities) {
                initResult.detectedCapabilities.contains(capability) shouldBe true
            }
            
            // Should detect additional capabilities based on responses
            if (testData.capabilityResponses.containsKey("0100")) {
                initResult.detectedCapabilities.contains(ScannerCapability.OBD_II) shouldBe true
            }
            
            if (testData.capabilityResponses.containsKey("ATDP") && 
                testData.capabilityResponses["ATDP"]?.contains("CAN") == true) {
                initResult.detectedCapabilities.contains(ScannerCapability.CAN_BUS) shouldBe true
            }
        }
    }
})

/**
 * Test data for scanner initialization testing.
 */
data class ScannerTestData(
    val deviceName: String,
    val expectedModel: ScannerModel,
    val shouldSucceed: Boolean = true
)

/**
 * Test data with command failure patterns.
 */
data class ScannerTestDataWithFailures(
    val deviceName: String,
    val failurePattern: Set<String>
)

/**
 * Test data for capability detection.
 */
data class ScannerModelWithCapabilities(
    val model: ScannerModel,
    val capabilityResponses: Map<String, String>
)

/**
 * Arbitrary generator for scanner test data.
 */
fun Arb.Companion.scannerTestData(): Arb<ScannerTestData> = arbitrary { rs ->
    val models = ScannerModel.entries
    val selectedModel = models.random(rs.random)
    
    val deviceName = when (selectedModel) {
        ScannerModel.ELM327 -> listOf("ELM327 v1.5", "OBDII ELM327", "ELM327 Bluetooth").random(rs.random)
        ScannerModel.STN1110 -> listOf("STN1110", "ScanTool STN1110", "STN11xx").random(rs.random)
        ScannerModel.STN2120 -> listOf("STN2120", "ScanTool STN2120", "STN21xx").random(rs.random)
        ScannerModel.OBDLINK_SX -> listOf("OBDLink SX", "OBDLINK", "ScanTool SX").random(rs.random)
        ScannerModel.VGATE_ICAR -> listOf("vGate iCar Pro", "VGATE", "iCar V2.1").random(rs.random)
        ScannerModel.GENERIC_ELM -> listOf("Generic OBD", "OBD2 Scanner", "Bluetooth OBD").random(rs.random)
    }
    
    ScannerTestData(
        deviceName = deviceName,
        expectedModel = selectedModel
    )
}

/**
 * Arbitrary generator for scanner test data with failures.
 */
fun Arb.Companion.scannerTestDataWithFailures(): Arb<ScannerTestDataWithFailures> = arbitrary { rs ->
    val deviceNames = listOf(
        "ELM327 v1.5", "STN1110", "STN2120", "OBDLink SX", "vGate iCar", "Generic OBD"
    )
    
    val allCommands = listOf("ATZ", "ATE0", "ATL0", "ATS0", "ATH1", "ATSP0", "STDI", "STI")
    val failureCount = rs.random.nextInt(0, 3) // 0-2 commands that fail
    val failingCommands = allCommands.shuffled(rs.random).take(failureCount).toSet()
    
    ScannerTestDataWithFailures(
        deviceName = deviceNames.random(rs.random),
        failurePattern = failingCommands
    )
}

/**
 * Arbitrary generator for scanner models with capability responses.
 */
fun Arb.Companion.scannerModelWithCapabilities(): Arb<ScannerModelWithCapabilities> = arbitrary { rs ->
    val model = ScannerModel.entries.random(rs.random)
    
    val responses = mutableMapOf<String, String>()
    
    // OBD-II capability response
    if (rs.random.nextBoolean()) {
        responses["0100"] = "41 00 BE 3E B8 11"
    }
    
    // Protocol detection response
    if (rs.random.nextBoolean()) {
        val protocols = listOf("ISO 15765-4 CAN", "SAE J1850 PWM", "ISO 9141-2", "ISO 14230-4")
        responses["ATDP"] = protocols.random(rs.random)
    }
    
    // STN-specific responses
    if (model == ScannerModel.STN1110 || model == ScannerModel.STN2120) {
        if (rs.random.nextBoolean()) {
            responses["STFCP"] = "OK"
        }
    }
    
    ScannerModelWithCapabilities(
        model = model,
        capabilityResponses = responses
    )
}

/**
 * Mock scanner connection for testing.
 */
class MockScannerConnection : ScannerConnection {
    
    private val executedCommands = mutableListOf<ExecutedCommand>()
    private val commandFailures = mutableSetOf<String>()
    private val capabilityResponses = mutableMapOf<String, String>()
    private val commandCounter = AtomicInteger(0)
    
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Connected(
        ConnectionInfo(remoteAddress = "TEST:ADDRESS")
    ))
    
    override val connectionId: String = "mock-connection"
    override val connectionType = com.spacetec.core.domain.models.scanner.ScannerConnectionType.BLUETOOTH_CLASSIC
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    override val incomingData = kotlinx.coroutines.flow.MutableSharedFlow<ByteArray>().asSharedFlow()
    override val isConnected: Boolean = true
    override val config = ConnectionConfig.DEFAULT
    
    data class ExecutedCommand(
        val command: String,
        val response: String,
        val success: Boolean,
        val executionOrder: Int
    )
    
    fun setCommandFailurePattern(failures: Set<String>) {
        commandFailures.clear()
        commandFailures.addAll(failures)
    }
    
    fun setCapabilityResponses(responses: Map<String, String>) {
        capabilityResponses.clear()
        capabilityResponses.putAll(responses)
    }
    
    fun getExecutedCommands(): List<ExecutedCommand> = executedCommands.toList()
    
    override suspend fun connect(address: String, config: ConnectionConfig): Result<ConnectionInfo> {
        return Result.Success(ConnectionInfo(remoteAddress = address))
    }
    
    override suspend fun disconnect(graceful: Boolean) {
        _connectionState.value = ConnectionState.Disconnected()
    }
    
    override suspend fun reconnect(): Result<ConnectionInfo> {
        return Result.Success(ConnectionInfo(remoteAddress = "TEST:ADDRESS"))
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
        delay(50) // Simulate command execution time
        
        val executionOrder = commandCounter.incrementAndGet()
        val shouldFail = commandFailures.contains(command)
        
        val response = when {
            shouldFail -> {
                executedCommands.add(ExecutedCommand(command, "ERROR", false, executionOrder))
                return Result.Error(Exception("Command failed: $command"))
            }
            
            // Capability test responses
            capabilityResponses.containsKey(command) -> {
                val response = capabilityResponses[command]!!
                executedCommands.add(ExecutedCommand(command, response, true, executionOrder))
                response
            }
            
            // Standard AT command responses
            command == "ATZ" -> {
                val responses = listOf(
                    "ELM327 v1.5",
                    "STN1110 v1.0",
                    "STN2120 v2.0",
                    "OBDLink SX v1.0"
                )
                val response = responses.random()
                executedCommands.add(ExecutedCommand(command, response, true, executionOrder))
                response
            }
            
            command in listOf("ATE0", "ATL0", "ATS0", "ATH1", "ATH0", "ATSP0", "ATSP3", "ATSP4", "ATSP5", "ATSP6") -> {
                executedCommands.add(ExecutedCommand(command, "OK", true, executionOrder))
                "OK"
            }
            
            command == "STDI" -> {
                val response = "STN1110 v1.0"
                executedCommands.add(ExecutedCommand(command, response, true, executionOrder))
                response
            }
            
            command == "STI" -> {
                val response = "STN Interface v1.0"
                executedCommands.add(ExecutedCommand(command, response, true, executionOrder))
                response
            }
            
            command == "STFAC" -> {
                executedCommands.add(ExecutedCommand(command, "OK", true, executionOrder))
                "OK"
            }
            
            command == "0100" -> {
                val response = "41 00 BE 3E B8 11"
                executedCommands.add(ExecutedCommand(command, response, true, executionOrder))
                response
            }
            
            command == "ATDP" -> {
                val response = "ISO 15765-4 CAN (11 bit ID, 500 kbaud)"
                executedCommands.add(ExecutedCommand(command, response, true, executionOrder))
                response
            }
            
            command == "STFCP" -> {
                executedCommands.add(ExecutedCommand(command, "OK", true, executionOrder))
                "OK"
            }
            
            else -> {
                executedCommands.add(ExecutedCommand(command, "OK", true, executionOrder))
                "OK"
            }
        }
        
        return Result.Success(response)
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