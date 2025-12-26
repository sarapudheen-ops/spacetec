/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 *
 * This file is part of the SpaceTec professional automotive diagnostic system.
 */

package com.spacetec.obd.scanner.usb.di

import android.content.Context
import android.hardware.usb.UsbManager
import com.spacetec.obd.scanner.usb.USBConnection
import com.spacetec.obd.scanner.usb.USBConnectionConfig
import com.spacetec.obd.scanner.usb.USBDeviceManager
import com.spacetec.obd.scanner.usb.USBDiscovery
import com.spacetec.obd.scanner.usb.USBPermissionHandler
import com.spacetec.obd.scanner.usb.drivers.USBDriverFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for USB connection dependencies.
 *
 * Provides dependency injection configuration for USB connection
 * implementations, including the USB manager, device discovery,
 * and driver factory.
 *
 * ## Provided Dependencies
 *
 * - [UsbManager]: System USB manager
 * - [USBDiscovery]: USB device discovery service
 * - [USBDeviceManager]: USB device management service
 * - [USBPermissionHandler]: USB permission handling service
 * - [USBDriverFactory]: USB driver factory
 * - [USBConnection]: USB connection factory
 * - [USBConnectionConfig]: USB connection configuration
 *
 * @author SpaceTec Development Team
 * @since 1.0.0
 */
@Module
@InstallIn(SingletonComponent::class)
object USBConnectionModule {

    /**
     * Provides the system USB manager.
     *
     * @param context Application context
     * @return USB manager
     */
    @Provides
    @Singleton
    fun provideUsbManager(
        @ApplicationContext context: Context
    ): UsbManager {
        return context.getSystemService(Context.USB_SERVICE) as UsbManager
    }

    /**
     * Provides USB device discovery service.
     *
     * @param context Application context
     * @param usbManager USB manager
     * @return USB discovery service
     */
    @Provides
    @Singleton
    fun provideUSBDiscovery(
        @ApplicationContext context: Context,
        usbManager: UsbManager
    ): USBDiscovery {
        return USBDiscovery(context, usbManager)
    }

    /**
     * Provides USB device manager.
     *
     * @param context Application context
     * @param usbManager USB manager
     * @return USB device manager
     */
    @Provides
    @Singleton
    fun provideUSBDeviceManager(
        @ApplicationContext context: Context,
        usbManager: UsbManager
    ): USBDeviceManager {
        return USBDeviceManager(context, usbManager)
    }

    /**
     * Provides USB permission handler.
     *
     * @param context Application context
     * @param usbManager USB manager
     * @return USB permission handler
     */
    @Provides
    @Singleton
    fun provideUSBPermissionHandler(
        @ApplicationContext context: Context,
        usbManager: UsbManager
    ): USBPermissionHandler {
        return USBPermissionHandler(context, usbManager)
    }

    /**
     * Provides USB driver factory.
     *
     * @return USB driver factory
     */
    @Provides
    @Singleton
    fun provideUSBDriverFactory(): USBDriverFactory {
        return USBDriverFactory
    }

    /**
     * Provides default USB connection configuration.
     *
     * @return Default USB connection configuration
     */
    @Provides
    fun provideUSBConnectionConfig(): USBConnectionConfig {
        return USBConnectionConfig.DEFAULT
    }

    /**
     * Provides a factory method for creating USB connections.
     *
     * This is not a singleton - each call creates a new connection instance.
     *
     * @param context Application context
     * @param config USB connection configuration
     * @return Factory function for creating USB connections
     */
    @Provides
    fun provideUSBConnectionFactory(
        @ApplicationContext context: Context,
        config: USBConnectionConfig
    ): () -> USBConnection {
        return {
            USBConnection(
                context = context,
                usbConfig = config
            )
        }
    }
}