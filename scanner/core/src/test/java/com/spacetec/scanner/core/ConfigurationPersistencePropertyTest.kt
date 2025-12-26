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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * **Feature: scanner-connection-system, Property 10: Configuration Persistence**
 * **Validates: Requirements 5.5**
 * 
 * Property-based test for configuration persistence.
 * 
 * This test verifies that for any successfully detected scanner capabilities, 
 * the configuration should be stored and reused for future connections to the same scanner.
 */
class ConfigurationPersistencePropertyTest : StringSpec({
    
    "Property 10: For any successfully detected scanner capabilities, the configuration should be stored and reused for future connections to the same scanner" {
        checkAll(
            iterations = 100,
            Arb.scannerConfigurationData()
        ) { testData ->
            // Create mock storage
            val mockStorage = MockConfigurationStorage()
            val configManager = ScannerConfigurationManager(mockStorage)
            
            // Save initial configuration
            val saveResult = configManager.saveConfiguration(
                deviceName = testData.deviceName,
                deviceAddress = testData.deviceAddress,
                connectionType = testData.connectionType,
                initializationResult = testData.initializationResult,
                protocolResult = testData.protocolResult
            )
            
            // Verify save succeeded
            saveResult shouldBe Result.Success::class
            val scannerId = (saveResult as Result.Success).data
            scannerId shouldNotBe null
            
            // Load the configuration back
            val loadResult = configManager.loadConfiguration(scannerId)
            loadResult shouldBe Result.Success::class
            
            val loadedConfig = (loadResult as Result.Success).data
            loadedConfig shouldNotBe null
            
            // Verify all key data was persisted correctly
            loadedConfig!!.deviceName shouldBe testData.deviceName
            loadedConfig.deviceAddress shouldBe testData.deviceAddress
            loadedConfig.connectionType shouldBe testData.connectionType.name
            loadedConfig.scannerModel shouldBe testData.initializationResult.scannerModel.name
            loadedConfig.detectedCapabilities shouldBe testData.initializationResult.detectedCapabilities.map { it.name }.toSet()
            
            // Verify protocol information if available
            testData.protocolResult?.let { protocolResult ->
                loadedConfig.protocolType shouldBe protocolResult.detectedProtocol?.name
                loadedConfig.protocolSettings["confidence"] shouldBe protocolResult.confidence.toString()
            }
            
            // Verify firmware/hardware info if available
            testData.initializationResult.firmwareVersion?.let { version ->
                loadedConfig.firmwareVersion shouldBe version
            }
            
            testData.initializationResult.hardwareVersion?.let { version ->
                loadedConfig.hardwareVersion shouldBe version
            }
            
            // Verify configuration can be found by address
            val findResult = configManager.findConfigurationsByAddress(testData.deviceAddress)
            findResult shouldBe Result.Success::class
            
            val foundConfigs = (findResult as Result.Success).data
            foundConfigs.size shouldBe { it >= 1 }
            foundConfigs.any { it.scannerId == scannerId } shouldBe true
        }
    }
    
    "Property 10a: Configuration should be reusable for subsequent connections to the same scanner" {
        checkAll(
            iterations = 50,
            Arb.scannerConfigurationData()
        ) { testData ->
            val mockStorage = MockConfigurationStorage()
            val configManager = ScannerConfigurationManager(mockStorage)
            
            // Save initial configuration
            val saveResult = configManager.saveConfiguration(
                deviceName = testData.deviceName,
                deviceAddress = testData.deviceAddress,
                connectionType = testData.connectionType,
                initializationResult = testData.initializationResult,
                protocolResult = testData.protocolResult
            )
            
            val scannerId = (saveResult as Result.Success).data
            
            // Find most recent configuration for the same device
            val findResult = configManager.findMostRecentConfiguration(
                deviceAddress = testData.deviceAddress,
                connectionType = testData.connectionType
            )
            
            findResult shouldBe Result.Success::class
            val foundConfig = (findResult as Result.Success).data
            foundConfig shouldNotBe null
            foundConfig!!.scannerId shouldBe scannerId
            
            // Simulate usage updates
            val connectionTime = 2500L
            val success = true
            
            val updateResult = configManager.updateConfigurationUsage(
                scannerId = scannerId,
                connectionTime = connectionTime,
                success = success
            )
            
            updateResult shouldBe Result.Success::class
            
            // Verify usage statistics were updated
            val updatedLoadResult = configManager.loadConfiguration(scannerId)
            val updatedConfig = (updatedLoadResult as Result.Success).data!!
            
            updatedConfig.useCount shouldBe 1
            updatedConfig.averageConnectionTime shouldBe connectionTime
            updatedConfig.successRate shouldBe 1.0f
            updatedConfig.lastUsed shouldBe { it > foundConfig.lastUsed }
        }
    }
    
    "Property 10b: Configuration validation should prevent invalid data from being stored" {
        checkAll(
            iterations = 30,
            Arb.invalidConfigurationData()
        ) { testData ->
            val mockStorage = MockConfigurationStorage()
            val configManager = ScannerConfigurationManager(mockStorage)
            
            // Attempt to save invalid configuration
            val saveResult = configManager.saveConfiguration(
                deviceName = testData.deviceName,
                deviceAddress = testData.deviceAddress,
                connectionType = testData.connectionType,
                initializationResult = testData.initializationResult,
                protocolResult = testData.protocolResult
            )
            
            // Should fail validation for clearly invalid data
            if (testData.shouldFailValidation) {
                saveResult shouldBe Result.Error::class
            } else {
                // Should succeed for marginally valid data
                saveResult shouldBe Result.Success::class
            }
        }
    }
    
    "Property 10c: Configuration synchronization should handle duplicates and conflicts" {
        checkAll(
            iterations = 20,
            Arb.duplicateConfigurationData()
        ) { testData ->
            val mockStorage = MockConfigurationStorage()
            val configManager = ScannerConfigurationManager(mockStorage)
            
            // Save multiple configurations for the same device
            val savedIds = mutableListOf<String>()
            
            for (configData in testData.configurations) {
                val saveResult = configManager.saveConfiguration(
                    deviceName = configData.deviceName,
                    deviceAddress = configData.deviceAddress,
                    connectionType = configData.connectionType,
                    initializationResult = configData.initializationResult,
                    protocolResult = configData.protocolResult
                )
                
                if (saveResult is Result.Success) {
                    savedIds.add(saveResult.data)
                }
            }
            
            // Perform synchronization
            val syncResult = configManager.synchronizeConfigurations()
            syncResult shouldBe Result.Success::class
            
            // Verify duplicates were removed (should keep most recent)
            val allConfigsResult = configManager.loadAllConfigurations()
            allConfigsResult shouldBe Result.Success::class
            
            val allConfigs = (allConfigsResult as Result.Success).data
            
            // Group by device address and connection type
            val deviceGroups = allConfigs.values.groupBy { "${it.deviceAddress}:${it.connectionType}" }
            
            // Each device should have only one configuration after sync
            for ((_, configs) in deviceGroups) {
                configs.size shouldBe 1
            }
        }
    }
})

/**
 * Test data for scanner configuration.
 */
data class ScannerConfigurationData(
    val deviceName: String,
    val deviceAddress: String,
    val connectionType: ScannerConnectionType,
    val initializationResult: InitializationResult,
    val protocolResult: ProtocolDetectionResult?
)

/**
 * Test data for invalid configuration.
 */
data class InvalidConfigurationData(
    val deviceName: String,
    val deviceAddress: String,
    val connectionType: ScannerConnectionType,
    val initializationResult: InitializationResult,
    val protocolResult: ProtocolDetectionResult?,
    val shouldFailValidation: Boolean
)

/**
 * Test data for duplicate configurations.
 */
data class DuplicateConfigurationData(
    val configurations: List<ScannerConfigurationData>
)

/**
 * Arbitrary generator for scanner configuration data.
 */
fun Arb.Companion.scannerConfigurationData(): Arb<ScannerConfigurationData> = arbitrary { rs ->
    val deviceNames = listOf(
        "ELM327 v1.5", "STN1110", "OBDLink SX", "vGate iCar Pro", "BlueDriver"
    )
    
    val addresses = listOf(
        "AA:BB:CC:DD:EE:FF", "11:22:33:44:55:66", "192.168.1.100", "/dev/ttyUSB0"
    )
    
    val connectionTypes = ScannerConnectionType.entries
    
    val deviceName = deviceNames.random(rs.random)
    val deviceAddress = addresses.random(rs.random)
    val connectionType = connectionTypes.random(rs.random)
    
    val scannerModel = ScannerModel.detectFromName(deviceName)
    val capabilities = scannerModel.capabilities.toMutableSet()
    
    // Add some random additional capabilities
    if (rs.random.nextBoolean()) {
        capabilities.add(ScannerCapability.ENHANCED_DIAGNOSTICS)
    }
    
    val initializationResult = InitializationResult(
        success = true,
        scannerModel = scannerModel,
        detectedCapabilities = capabilities,
        firmwareVersion = if (rs.random.nextBoolean()) "v${rs.random.nextInt(1, 5)}.${rs.random.nextInt(0, 10)}" else null,
        hardwareVersion = if (rs.random.nextBoolean()) "hw${rs.random.nextInt(1, 3)}.${rs.random.nextInt(0, 5)}" else null,
        serialNumber = if (rs.random.nextBoolean()) "SN${rs.random.nextInt(100000, 999999)}" else null,
        voltage = if (rs.random.nextBoolean()) rs.random.nextFloat() * 2 + 11 else null,
        initializationTime = rs.random.nextLong(500, 5000),
        commandsExecuted = scannerModel.initCommands
    )
    
    val protocolResult = if (rs.random.nextBoolean()) {
        ProtocolDetectionResult(
            success = true,
            detectedProtocol = ProtocolType.entries.random(rs.random),
            detectionTime = rs.random.nextLong(1000, 10000),
            testedProtocols = ProtocolType.entries.shuffled(rs.random).take(rs.random.nextInt(2, 6)),
            confidence = rs.random.nextFloat() * 0.5f + 0.5f, // 0.5 to 1.0
            vehicleInfo = VehicleInfo(
                make = listOf("Toyota", "Ford", "BMW").random(rs.random),
                year = rs.random.nextInt(2000, 2024)
            )
        )
    } else null
    
    ScannerConfigurationData(
        deviceName = deviceName,
        deviceAddress = deviceAddress,
        connectionType = connectionType,
        initializationResult = initializationResult,
        protocolResult = protocolResult
    )
}

/**
 * Arbitrary generator for invalid configuration data.
 */
fun Arb.Companion.invalidConfigurationData(): Arb<InvalidConfigurationData> = arbitrary { rs ->
    val validData = scannerConfigurationData().sample(rs).value
    
    // Create various types of invalid data
    val invalidVariants = listOf(
        // Empty device name
        validData.copy(deviceName = "") to true,
        // Empty device address  
        validData.copy(deviceAddress = "") to true,
        // Failed initialization
        validData.copy(
            initializationResult = validData.initializationResult.copy(success = false)
        ) to false, // This might still be valid to store for debugging
        // Very old configuration (warning but not invalid)
        validData to false
    )
    
    val (invalidData, shouldFail) = invalidVariants.random(rs.random)
    
    InvalidConfigurationData(
        deviceName = invalidData.deviceName,
        deviceAddress = invalidData.deviceAddress,
        connectionType = invalidData.connectionType,
        initializationResult = invalidData.initializationResult,
        protocolResult = invalidData.protocolResult,
        shouldFailValidation = shouldFail
    )
}

/**
 * Arbitrary generator for duplicate configuration data.
 */
fun Arb.Companion.duplicateConfigurationData(): Arb<DuplicateConfigurationData> = arbitrary { rs ->
    val baseConfig = scannerConfigurationData().sample(rs).value
    
    // Create 2-4 configurations for the same device with slight variations
    val configCount = rs.random.nextInt(2, 5)
    val configurations = mutableListOf<ScannerConfigurationData>()
    
    repeat(configCount) { index ->
        val config = baseConfig.copy(
            // Same device address and connection type (duplicates)
            deviceAddress = baseConfig.deviceAddress,
            connectionType = baseConfig.connectionType,
            // Slight variations in other fields
            deviceName = if (index == 0) baseConfig.deviceName else "${baseConfig.deviceName} v${index + 1}",
            initializationResult = baseConfig.initializationResult.copy(
                initializationTime = baseConfig.initializationResult.initializationTime + index * 100
            )
        )
        configurations.add(config)
    }
    
    DuplicateConfigurationData(configurations = configurations)
}

/**
 * Mock configuration storage for testing.
 */
class MockConfigurationStorage : ConfigurationStorage {
    
    private val storage = ConcurrentHashMap<String, ScannerConfiguration>()
    
    override suspend fun save(scannerId: String, configuration: ScannerConfiguration): Result<Unit> {
        return try {
            storage[scannerId] = configuration
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(com.spacetec.core.common.exceptions.ConfigurationException("Save failed: ${e.message}", e))
        }
    }
    
    override suspend fun load(scannerId: String): Result<ScannerConfiguration?> {
        return try {
            val config = storage[scannerId]
            Result.Success(config)
        } catch (e: Exception) {
            Result.Error(com.spacetec.core.common.exceptions.ConfigurationException("Load failed: ${e.message}", e))
        }
    }
    
    override suspend fun loadAll(): Result<Map<String, ScannerConfiguration>> {
        return try {
            Result.Success(storage.toMap())
        } catch (e: Exception) {
            Result.Error(com.spacetec.core.common.exceptions.ConfigurationException("Load all failed: ${e.message}", e))
        }
    }
    
    override suspend fun delete(scannerId: String): Result<Unit> {
        return try {
            storage.remove(scannerId)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(com.spacetec.core.common.exceptions.ConfigurationException("Delete failed: ${e.message}", e))
        }
    }
    
    override suspend fun exists(scannerId: String): Result<Boolean> {
        return try {
            Result.Success(storage.containsKey(scannerId))
        } catch (e: Exception) {
            Result.Error(com.spacetec.core.common.exceptions.ConfigurationException("Exists check failed: ${e.message}", e))
        }
    }
    
    override suspend fun clear(): Result<Unit> {
        return try {
            storage.clear()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(com.spacetec.core.common.exceptions.ConfigurationException("Clear failed: ${e.message}", e))
        }
    }
}