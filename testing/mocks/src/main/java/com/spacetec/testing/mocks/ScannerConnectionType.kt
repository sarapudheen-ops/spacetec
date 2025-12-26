/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 *
 * This file is part of the SpaceTec professional automotive diagnostic system.
 */

package com.spacetec.testing.mocks

/**
 * Types of scanner connections for testing.
 */
enum class ScannerConnectionType(val displayName: String) {
    /**
     * Bluetooth Classic using SPP (Serial Port Profile) / RFCOMM.
     */
    BLUETOOTH_CLASSIC("Bluetooth"),
    
    /**
     * Bluetooth Low Energy using GATT.
     */
    BLUETOOTH_LE("Bluetooth LE"),
    
    /**
     * WiFi using TCP/IP socket connection.
     */
    WIFI("WiFi"),
    
    /**
     * USB wired connection using serial communication.
     */
    USB("USB");
    
    /**
     * Indicates whether this connection type is wireless.
     */
    val isWireless: Boolean
        get() = this != USB
    
    /**
     * Indicates whether this is a Bluetooth connection type.
     */
    val isBluetooth: Boolean
        get() = this == BLUETOOTH_CLASSIC || this == BLUETOOTH_LE
}