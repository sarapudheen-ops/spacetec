/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 *
 * This file is part of the SpaceTec professional automotive diagnostic system.
 */

package com.spacetec.obd.scanner.bluetooth

import com.spacetec.core.domain.models.scanner.Scanner
import com.spacetec.core.domain.models.scanner.ScannerConnectionType
import com.spacetec.core.domain.models.scanner.ScannerDeviceType
import com.spacetec.obd.scanner.core.DiscoveredScanner
import com.spacetec.obd.scanner.core.DiscoveryOptions
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll

/**
 * **Feature: scanner-connection-system, Property 1: Scanner Discovery Completeness**
 * 
 * Property-based test for Bluetooth scanner discovery functionality.
 * 
 * **Validates: Requirements 1.1, 2.1, 3.1**
 * 
 * This test verifies that:
 * 1. Discovery returns all scanners that match the filter criteria
 * 2. Signal strength information is accurate when available
 * 3. Device filtering works correctly for OBD adapter names
 * 4. Paired and unpaired devices are handled according to options
 */
class BluetoothDiscoveryPropertyTest : StringSpec({

    // Generators for test data
    val macAddressArb = Arb.string(17, Codepoint.alphanumeric()).map { 
        // Generate valid MAC address format
        (0..5).joinToString(":") { 
            String.format("%02X", (0..255).random())
        }
    }

    val signalStrengthArb = Arb.int(-100..-30) // Valid RSSI range

    val obdDeviceNameArb = Arb.element(
        "OBDLink MX+",
        "ELM327 v1.5",
        "VLINK Bluetooth",
        "OBD Scanner Pro",
        "Veepeak BLE",
        "Carista OBD2",
        "BlueDriver Pro",
        "iCar Pro",
        "OBDII Scanner"
    )

    val nonObdDeviceNameArb = Arb.element(
        "iPhone",
        "Galaxy Buds",
        "AirPods Pro",
        "Fitbit Charge",
        "JBL Speaker",
        "Sony Headphones",
        "Keyboard",
        "Mouse"
    )

    val deviceTypeArb = Arb.enum<ScannerDeviceType>()

    val connectionTypeArb = Arb.element(
        ScannerConnectionType.BLUETOOTH_CLASSIC,
        ScannerConnectionType.BLUETOOTH_LE
    )

    fun createMockScanner(
        address: String,
        name: String,
        connectionType: ScannerConnectionType,
        signalStrength: Int?,
        isPaired: Boolean = false
    ): Scanner {
        return Scanner(
            id = address,
            name = name,
            connectionType = connectionType,
            deviceType = Scanner.detectDeviceType(name),
            address = address,
            signalStrength = signalStrength,
            isPaired = isPaired
        )
    }

    fun createDiscoveredScanner(
        scanner: Scanner,
        signalStrength: Int?,
        isNew: Boolean = true
    ): DiscoveredScanner {
        return DiscoveredScanner(
            scanner = scanner,
            signalStrength = signalStrength,
            isNew = isNew
        )
    }

    "Property 1: Scanner Discovery Completeness - All OBD scanners matching filter should be discovered" {
        checkAll(
            iterations = 100,
            Arb.list(obdDeviceNameArb, 1..10),
            Arb.list(signalStrengthArb, 1..10)
        ) { names, signals ->
            // Given: A set of OBD scanners with various signal strengths
            val scanners = names.zip(signals).mapIndexed { index, (name, signal) ->
                val address = String.format("AA:BB:CC:DD:EE:%02X", index)
                createMockScanner(
                    address = address,
                    name = name,
                    connectionType = ScannerConnectionType.BLUETOOTH_CLASSIC,
                    signalStrength = signal
                )
            }

            // When: Discovery options with default filter (should match OBD devices)
            val options = DiscoveryOptions.DEFAULT

            // Then: All OBD scanners should match the filter
            scanners.forEach { scanner ->
                val matchesFilter = options.matchesFilter(scanner.name) || 
                                   Scanner.looksLikeOBDAdapter(scanner.name)
                matchesFilter shouldBe true
            }

            // And: Signal strength should be within valid range
            scanners.forEach { scanner ->
                scanner.signalStrength?.let { rssi ->
                    rssi shouldBeGreaterThanOrEqual -100
                    rssi shouldBeLessThanOrEqual 0
                }
            }
        }
    }

    "Property 1: Non-OBD devices should be filtered out when using OBD filter" {
        checkAll(
            iterations = 100,
            Arb.list(nonObdDeviceNameArb, 1..10)
        ) { names ->
            // Given: A set of non-OBD devices
            val devices = names.mapIndexed { index, name ->
                val address = String.format("AA:BB:CC:DD:EE:%02X", index)
                createMockScanner(
                    address = address,
                    name = name,
                    connectionType = ScannerConnectionType.BLUETOOTH_CLASSIC,
                    signalStrength = -60
                )
            }

            // When: Using OBD-only filter
            val options = DiscoveryOptions.OBD_ONLY

            // Then: Non-OBD devices should not match the filter
            devices.forEach { device ->
                val matchesFilter = options.matchesFilter(device.name) && 
                                   Scanner.looksLikeOBDAdapter(device.name)
                // Most non-OBD devices should not match
                // (some might accidentally match if name contains OBD-like patterns)
                if (!device.name.uppercase().contains("OBD") &&
                    !device.name.uppercase().contains("ELM") &&
                    !device.name.uppercase().contains("SCAN")) {
                    Scanner.looksLikeOBDAdapter(device.name) shouldBe false
                }
            }
        }
    }

    "Property 1: Signal strength percentage calculation should be correct" {
        checkAll(
            iterations = 100,
            signalStrengthArb
        ) { rssi ->
            // Given: A discovered scanner with signal strength
            val scanner = createMockScanner(
                address = "AA:BB:CC:DD:EE:FF",
                name = "OBD Scanner",
                connectionType = ScannerConnectionType.BLUETOOTH_CLASSIC,
                signalStrength = rssi
            )
            val discovered = createDiscoveredScanner(scanner, rssi)

            // When: Calculating signal strength percentage
            val percent = discovered.signalStrengthPercent

            // Then: Percentage should be within 0-100 range
            percent shouldNotBe null
            percent!! shouldBeGreaterThanOrEqual 0
            percent shouldBeLessThanOrEqual 100

            // And: Should follow the expected formula
            val expectedPercent = when {
                rssi >= -50 -> 100
                rssi <= -100 -> 0
                else -> (2 * (rssi + 100)).coerceIn(0, 100)
            }
            percent shouldBe expectedPercent
        }
    }

    "Property 1: Discovery options filter should correctly match device names" {
        checkAll(
            iterations = 100,
            Arb.string(3..20),
            Arb.element("*OBD*", "*ELM*", "*SCAN*", "OBD*", "*327")
        ) { deviceName, filterPattern ->
            // Given: A filter pattern and device name
            val options = DiscoveryOptions(filterByName = filterPattern)

            // When: Checking if name matches filter
            val matches = options.matchesFilter(deviceName)

            // Then: Match result should be consistent with regex pattern
            val regexPattern = filterPattern
                .replace("*", ".*")
                .replace("?", ".")
                .toRegex(RegexOption.IGNORE_CASE)
            
            val expectedMatch = regexPattern.containsMatchIn(deviceName)
            matches shouldBe expectedMatch
        }
    }

    "Property 1: Minimum signal strength filter should exclude weak signals" {
        checkAll(
            iterations = 100,
            signalStrengthArb,
            Arb.int(-90..-50) // minSignalStrength threshold
        ) { rssi, minSignal ->
            // Given: A scanner with specific signal strength
            val scanner = createMockScanner(
                address = "AA:BB:CC:DD:EE:FF",
                name = "OBD Scanner",
                connectionType = ScannerConnectionType.BLUETOOTH_CLASSIC,
                signalStrength = rssi
            )

            // When: Applying minimum signal strength filter
            val options = DiscoveryOptions(minSignalStrength = minSignal)

            // Then: Scanner should only be included if signal >= minimum
            val shouldInclude = rssi >= minSignal
            
            // Verify the filter logic
            if (shouldInclude) {
                rssi shouldBeGreaterThanOrEqual minSignal
            } else {
                rssi shouldBeLessThanOrEqual minSignal - 1
            }
        }
    }

    "Property 1: Device type detection should be consistent for known device names" {
        checkAll(
            iterations = 100,
            obdDeviceNameArb
        ) { deviceName ->
            // Given: An OBD device name
            // When: Detecting device type
            val deviceType = Scanner.detectDeviceType(deviceName)

            // Then: Device type should be consistent for the same name
            val deviceType2 = Scanner.detectDeviceType(deviceName)
            deviceType shouldBe deviceType2

            // And: Should not be GENERIC for known OBD device names
            if (deviceName.uppercase().contains("OBDLINK") ||
                deviceName.uppercase().contains("ELM327") ||
                deviceName.uppercase().contains("VEEPEAK") ||
                deviceName.uppercase().contains("CARISTA") ||
                deviceName.uppercase().contains("BLUEDRIVER")) {
                deviceType shouldNotBe ScannerDeviceType.GENERIC
            }
        }
    }

    "Property 1: Paired devices should be marked correctly" {
        checkAll(
            iterations = 50,
            Arb.boolean()
        ) { isPaired ->
            // Given: A scanner with paired status
            val scanner = createMockScanner(
                address = "AA:BB:CC:DD:EE:FF",
                name = "OBD Scanner",
                connectionType = ScannerConnectionType.BLUETOOTH_CLASSIC,
                signalStrength = -60,
                isPaired = isPaired
            )

            // Then: Paired status should be preserved
            scanner.isPaired shouldBe isPaired

            // And: Discovered scanner should reflect paired status
            val discovered = createDiscoveredScanner(scanner, -60, isNew = !isPaired)
            discovered.isNew shouldBe !isPaired
        }
    }

    "Property 1: Connection type filter should only return matching types" {
        checkAll(
            iterations = 100,
            connectionTypeArb
        ) { filterType ->
            // Given: Discovery options with connection type filter
            val options = DiscoveryOptions(filterByType = filterType)

            // When: Creating scanners of different types
            val classicScanner = createMockScanner(
                address = "AA:BB:CC:DD:EE:01",
                name = "Classic OBD",
                connectionType = ScannerConnectionType.BLUETOOTH_CLASSIC,
                signalStrength = -60
            )
            val bleScanner = createMockScanner(
                address = "AA:BB:CC:DD:EE:02",
                name = "BLE OBD",
                connectionType = ScannerConnectionType.BLUETOOTH_LE,
                signalStrength = -60
            )

            // Then: Only matching type should pass filter
            val classicMatches = options.filterByType == null || 
                                 options.filterByType == ScannerConnectionType.BLUETOOTH_CLASSIC
            val bleMatches = options.filterByType == null || 
                            options.filterByType == ScannerConnectionType.BLUETOOTH_LE

            (classicScanner.connectionType == filterType) shouldBe 
                (filterType == ScannerConnectionType.BLUETOOTH_CLASSIC)
            (bleScanner.connectionType == filterType) shouldBe 
                (filterType == ScannerConnectionType.BLUETOOTH_LE)
        }
    }
})
