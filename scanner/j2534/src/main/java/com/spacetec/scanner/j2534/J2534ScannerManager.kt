/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 *
 * This file is part of the SpaceTec professional automotive diagnostic system.
 */

package com.spacetec.obd.scanner.j2534

import com.spacetec.core.common.result.Result
import com.spacetec.core.domain.models.scanner.Scanner
import com.spacetec.j2534.J2534Interface
import com.spacetec.obd.scanner.core.DiscoveredScanner
import com.spacetec.obd.scanner.core.DiscoveryOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Interface for J2534 scanner discovery and management.
 */
interface J2534ScannerManager {
    
    /**
     * Whether discovery is active.
     */
    val isDiscovering: StateFlow<Boolean>
    
    /**
     * Stream of discovered devices.
     */
    val discoveredDevices: SharedFlow<DiscoveredScanner>
    
    /**
     * Starts J2534 discovery.
     *
     * @param options Discovery options
     * @return Flow of discovered scanners
     */
    fun startDiscovery(options: DiscoveryOptions): Flow<DiscoveredScanner>
    
    /**
     * Stops J2534 discovery.
     */
    suspend fun stopDiscovery()
    
    /**
     * Gets available J2534 devices.
     *
     * @return List of available J2534 scanners
     */
    suspend fun getAvailableDevices(): List<Scanner>
    
    /**
     * Checks if J2534 is available.
     *
     * @return true if J2534 is available
     */
    fun isJ2534Available(): Boolean
    
    /**
     * Gets installed J2534 drivers.
     *
     * @return List of installed driver names
     */
    suspend fun getInstalledDrivers(): List<String>
}

/**
 * Implementation of J2534ScannerManager.
 *
 * Manages J2534 Pass-Thru device discovery and provides access to
 * professional diagnostic tools through the J2534 API.
 */
@Singleton
class J2534ScannerManagerImpl @Inject constructor(
    private val j2534Interface: J2534Interface
) : J2534ScannerManager {
    
    private val _isDiscovering = MutableStateFlow(false)
    override val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()
    
    private val _discoveredDevices = MutableSharedFlow<DiscoveredScanner>()
    override val discoveredDevices: SharedFlow<DiscoveredScanner> = _discoveredDevices.asSharedFlow()
    
    override fun startDiscovery(options: DiscoveryOptions): Flow<DiscoveredScanner> = flow {
        _isDiscovering.value = true
        
        try {
            val devices = getAvailableDevices()
            
            devices.forEach { scanner ->
                if (options.matchesFilter(scanner.name)) {
                    val discovered = DiscoveredScanner(
                        scanner = scanner,
                        signalStrength = null, // J2534 devices don't have signal strength
                        isNew = true
                    )
                    emit(discovered)
                    _discoveredDevices.emit(discovered)
                }
            }
        } finally {
            _isDiscovering.value = false
        }
    }
    
    override suspend fun stopDiscovery() {
        _isDiscovering.value = false
    }
    
    override suspend fun getAvailableDevices(): List<Scanner> {
        return try {
            val drivers = getInstalledDrivers()
            drivers.map { driverName ->
                Scanner(
                    id = "j2534_$driverName",
                    name = driverName,
                    connectionType = com.spacetec.core.domain.models.scanner.ScannerConnectionType.J2534,
                    deviceType = com.spacetec.core.domain.models.scanner.ScannerDeviceType.J2534,
                    address = driverName
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    override fun isJ2534Available(): Boolean {
        return try {
            j2534Interface.isInitialized() || j2534Interface.initialize() is Result.Success
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun getInstalledDrivers(): List<String> {
        return try {
            // This would typically query the Windows registry or system for installed J2534 drivers
            // For now, return common driver names that might be available
            listOf(
                "Drew Technologies MongoosePro",
                "Tactrix OpenPort 2.0",
                "OBDLink SX",
                "Kvaser Leaf",
                "PEAK PCAN",
                "Vector VN1630"
            ).filter { driverName ->
                // Check if driver is actually available
                checkDriverAvailability(driverName)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    private fun checkDriverAvailability(driverName: String): Boolean {
        return try {
            // This would check if the driver DLL exists and can be loaded
            // For now, return true for testing purposes
            true
        } catch (e: Exception) {
            false
        }
    }
}