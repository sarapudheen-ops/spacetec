/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 *
 * This file is part of the SpaceTec professional automotive diagnostic system.
 */

package com.spacetec.obd.scanner.wifi.di

import android.content.Context
import com.spacetec.obd.scanner.wifi.WiFiConfig
import com.spacetec.obd.scanner.wifi.WiFiConnection
import com.spacetec.obd.scanner.wifi.WiFiDiscovery
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for WiFi connection dependencies.
 *
 * Provides dependency injection configuration for WiFi connection
 * implementations, including the WiFi discovery service and
 * connection configuration.
 *
 * ## Provided Dependencies
 *
 * - [WiFiDiscovery]: WiFi scanner discovery service
 * - [WiFiConnection]: WiFi connection factory
 * - [WiFiConfig]: WiFi connection configuration
 *
 * @author SpaceTec Development Team
 * @since 1.0.0
 */
@Module
@InstallIn(SingletonComponent::class)
object WiFiConnectionModule {

    /**
     * Provides WiFi scanner discovery service.
     *
     * @param context Application context
     * @return WiFi discovery service
     */
    @Provides
    @Singleton
    fun provideWiFiDiscovery(
        @ApplicationContext context: Context
    ): WiFiDiscovery {
        return WiFiDiscovery(context)
    }

    /**
     * Provides default WiFi configuration.
     *
     * @return Default WiFi configuration
     */
    @Provides
    fun provideWiFiConfig(): WiFiConfig {
        return WiFiConfig.DEFAULT
    }

    /**
     * Provides a factory method for creating WiFi connections.
     *
     * This is not a singleton - each call creates a new connection instance.
     *
     * @param context Application context
     * @param config WiFi configuration
     * @return Factory function for creating WiFi connections
     */
    @Provides
    fun provideWiFiConnectionFactory(
        @ApplicationContext context: Context,
        config: WiFiConfig
    ): () -> WiFiConnection {
        return {
            WiFiConnection(
                context = context,
                wifiConfig = config
            )
        }
    }
}