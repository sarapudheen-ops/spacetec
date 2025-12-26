package com.obdreader.di

import com.obdreader.data.bluetooth.BluetoothManager
import com.spacetec.domain.repository.BluetoothDeviceManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BluetoothModule {

    @Singleton
    @Binds
    abstract fun bindBluetoothDeviceManager(bluetoothManager: BluetoothManager): BluetoothDeviceManager
}