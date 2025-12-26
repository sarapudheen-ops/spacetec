/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 *
 * This file is part of the SpaceTec professional automotive diagnostic system.
 */

package com.spacetec.obd.core.security.di

import com.spacetec.obd.core.security.encryption.AESEncryption
import com.spacetec.obd.core.security.encryption.KeyStoreManager
import com.spacetec.obd.core.security.integrity.DataIntegrityManager
import com.spacetec.obd.core.security.scanner.ScannerSecurityManager
import com.spacetec.obd.core.security.scanner.ScannerSecurityManagerImpl
import com.spacetec.obd.core.security.violation.SecurityViolationHandler
import com.spacetec.obd.core.security.wireless.WirelessEncryptionManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dependency injection module for security components.
 *
 * Provides security-related dependencies including encryption,
 * integrity checking, and violation handling.
 *
 * @author SpaceTec Development Team
 * @since 1.0.0
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SecurityModule {
    
    @Binds
    @Singleton
    abstract fun bindScannerSecurityManager(
        impl: ScannerSecurityManagerImpl
    ): ScannerSecurityManager
}

/**
 * Provides concrete security implementations.
 */
@Module
@InstallIn(SingletonComponent::class)
object SecurityProviderModule {
    
    // All implementations are already annotated with @Singleton and @Inject
    // so Hilt will automatically provide them
}