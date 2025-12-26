// scanner/bluetooth/src/main/kotlin/com/spacetec/automotive/scanner/bluetooth/di/BluetoothScannerModule.kt
package com.spacetec.obd.scanner.bluetooth.di

import android.content.Context
import com.spacetec.obd.scanner.bluetooth.BluetoothScannerDiscovery
import com.spacetec.obd.scanner.bluetooth.BluetoothScannerFactory
import com.spacetec.obd.scanner.core.ScannerDiscovery
import com.spacetec.obd.scanner.core.ScannerType
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import dagger.multibindings.StringKey
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Qualifier for Bluetooth scanner discovery.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BluetoothDiscovery

/**
 * Hilt module for Bluetooth scanner dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object BluetoothScannerModule {
    
    @Provides
    @Singleton
    @BluetoothDiscovery
    fun provideBluetoothScannerDiscovery(
        @ApplicationContext context: Context
    ): ScannerDiscovery = BluetoothScannerDiscovery(context)
    
    @Provides
    @Singleton
    fun provideBluetoothScannerFactory(
        @ApplicationContext context: Context
    ): BluetoothScannerFactory = BluetoothScannerFactory(context)
}

/**
 * Bindings module for scanner discovery multibinding.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class BluetoothScannerBindingsModule {
    
    @Binds
    @IntoMap
    @StringKey("bluetooth")
    abstract fun bindBluetoothDiscovery(
        @BluetoothDiscovery discovery: ScannerDiscovery
    ): ScannerDiscovery
}