/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 *
 * This file is part of the SpaceTec professional automotive diagnostic system.
 */

package com.spacetec.obd.scanner.bluetooth.di

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Build
import com.spacetec.obd.scanner.bluetooth.BluetoothDiscovery
import com.spacetec.obd.scanner.bluetooth.classic.BluetoothClassicConfig
import com.spacetec.obd.scanner.bluetooth.classic.BluetoothClassicConnection
import com.spacetec.obd.scanner.bluetooth.ble.BluetoothLEConfig
import com.spacetec.obd.scanner.bluetooth.ble.BluetoothLEConnection
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for Bluetooth connection dependencies.
 *
 * Provides dependency injection configuration for Bluetooth Classic and
 * Bluetooth LE connection implementations, including the Bluetooth adapter
 * and discovery services.
 *
 * ## Provided Dependencies
 *
 * - [BluetoothAdapter]: System Bluetooth adapter
 * - [BluetoothDiscovery]: Bluetooth device discovery service
 * - [BluetoothClassicConnection]: Bluetooth Classic connection factory
 * - [BluetoothLEConnection]: Bluetooth LE connection factory
 * - Configuration objects for both connection types
 *
 * @author SpaceTec Development Team
 * @since 1.0.0
 */
@Module
@InstallIn(SingletonComponent::class)
object BluetoothConnectionModule {

    /**
     * Provides the system Bluetooth adapter.
     *
     * @param context Application context
     * @return Bluetooth adapter or null if not available
     */
    @Provides
    @Singleton
    fun provideBluetoothAdapter(
        @ApplicationContext context: Context
    ): BluetoothAdapter? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            bluetoothManager?.adapter
        } else {
            @Suppress("DEPRECATION")
            BluetoothAdapter.getDefaultAdapter()
        }
    }

    /**
     * Provides Bluetooth device discovery service.
     *
     * @param context Application context
     * @param bluetoothAdapter Bluetooth adapter
     * @return Bluetooth discovery service
     */
    @Provides
    @Singleton
    fun provideBluetoothDiscovery(
        @ApplicationContext context: Context,
        bluetoothAdapter: BluetoothAdapter?
    ): BluetoothDiscovery {
        return BluetoothDiscovery(context, bluetoothAdapter)
    }

    /**
     * Provides default Bluetooth Classic configuration.
     *
     * @return Default Bluetooth Classic configuration
     */
    @Provides
    fun provideBluetoothClassicConfig(): BluetoothClassicConfig {
        return BluetoothClassicConfig.DEFAULT
    }

    /**
     * Provides default Bluetooth LE configuration.
     *
     * @return Default Bluetooth LE configuration
     */
    @Provides
    fun provideBluetoothLEConfig(): BluetoothLEConfig {
        return BluetoothLEConfig.DEFAULT
    }

    /**
     * Provides a factory method for creating Bluetooth Classic connections.
     *
     * This is not a singleton - each call creates a new connection instance.
     *
     * @param context Application context
     * @param bluetoothAdapter Bluetooth adapter
     * @param config Bluetooth Classic configuration
     * @return Factory function for creating Bluetooth Classic connections
     */
    @Provides
    fun provideBluetoothClassicConnectionFactory(
        @ApplicationContext context: Context,
        bluetoothAdapter: BluetoothAdapter?,
        config: BluetoothClassicConfig
    ): () -> BluetoothClassicConnection {
        return {
            BluetoothClassicConnection(
                context = context,
                bluetoothAdapter = bluetoothAdapter,
                classicConfig = config
            )
        }
    }

    /**
     * Provides a factory method for creating Bluetooth LE connections.
     *
     * This is not a singleton - each call creates a new connection instance.
     *
     * @param context Application context
     * @param bluetoothAdapter Bluetooth adapter
     * @param config Bluetooth LE configuration
     * @return Factory function for creating Bluetooth LE connections
     */
    @Provides
    fun provideBluetoothLEConnectionFactory(
        @ApplicationContext context: Context,
        bluetoothAdapter: BluetoothAdapter?,
        config: BluetoothLEConfig
    ): () -> BluetoothLEConnection {
        return {
            BluetoothLEConnection(
                context = context,
                bluetoothAdapter = bluetoothAdapter,
                leConfig = config
            )
        }
    }
}