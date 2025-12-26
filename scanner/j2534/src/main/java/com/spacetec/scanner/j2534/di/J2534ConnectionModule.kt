/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 *
 * This file is part of the SpaceTec professional automotive diagnostic system.
 */

package com.spacetec.obd.scanner.j2534.di

import com.spacetec.j2534.J2534Interface
import com.spacetec.obd.scanner.j2534.J2534Connection
import com.spacetec.obd.scanner.j2534.J2534ScannerManager
import com.spacetec.obd.scanner.j2534.J2534ScannerManagerImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for J2534 connection dependencies.
 *
 * Provides dependency injection configuration for J2534 Pass-Thru
 * connection implementations, including the J2534 interface,
 * connection factory, and scanner manager.
 *
 * ## Provided Dependencies
 *
 * - [J2534Interface]: J2534 native interface
 * - [J2534Connection]: J2534 connection factory
 * - [J2534ScannerManager]: J2534 scanner discovery and management
 *
 * @author SpaceTec Development Team
 * @since 1.0.0
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class J2534ConnectionModule {

    /**
     * Binds J2534ScannerManager implementation.
     */
    @Binds
    @Singleton
    abstract fun bindJ2534ScannerManager(
        j2534ScannerManagerImpl: J2534ScannerManagerImpl
    ): J2534ScannerManager

    companion object {
        /**
         * Provides J2534 native interface.
         *
         * The interface is a singleton that manages the native J2534 library
         * and provides access to J2534 API functions.
         *
         * @return J2534 interface instance
         */
        @Provides
        @Singleton
        fun provideJ2534Interface(): J2534Interface {
            return J2534Interface.create()
        }

        /**
         * Provides a factory method for creating J2534 connections.
         *
         * This is not a singleton - each call creates a new connection instance.
         *
         * @return Factory function for creating J2534 connections
         */
        @Provides
        fun provideJ2534ConnectionFactory(): () -> J2534Connection {
            return {
                J2534Connection.create()
            }
        }
    }
}