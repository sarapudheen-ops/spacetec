package com.obdreader.data.bluetooth.ble;

import android.bluetooth.BluetoothAdapter;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava"
})
public final class BLEScanner_Factory implements Factory<BLEScanner> {
  private final Provider<BluetoothAdapter> bluetoothAdapterProvider;

  public BLEScanner_Factory(Provider<BluetoothAdapter> bluetoothAdapterProvider) {
    this.bluetoothAdapterProvider = bluetoothAdapterProvider;
  }

  @Override
  public BLEScanner get() {
    return newInstance(bluetoothAdapterProvider.get());
  }

  public static BLEScanner_Factory create(Provider<BluetoothAdapter> bluetoothAdapterProvider) {
    return new BLEScanner_Factory(bluetoothAdapterProvider);
  }

  public static BLEScanner newInstance(BluetoothAdapter bluetoothAdapter) {
    return new BLEScanner(bluetoothAdapter);
  }
}
