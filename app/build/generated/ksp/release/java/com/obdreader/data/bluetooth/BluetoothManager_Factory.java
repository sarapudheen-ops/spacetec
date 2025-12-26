package com.obdreader.data.bluetooth;

import com.obdreader.data.bluetooth.ble.BluetoothLEManager;
import com.obdreader.data.bluetooth.classic.BluetoothClassicManager;
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
public final class BluetoothManager_Factory implements Factory<BluetoothManager> {
  private final Provider<BluetoothLEManager> bleManagerProvider;

  private final Provider<BluetoothClassicManager> classicManagerProvider;

  public BluetoothManager_Factory(Provider<BluetoothLEManager> bleManagerProvider,
      Provider<BluetoothClassicManager> classicManagerProvider) {
    this.bleManagerProvider = bleManagerProvider;
    this.classicManagerProvider = classicManagerProvider;
  }

  @Override
  public BluetoothManager get() {
    return newInstance(bleManagerProvider.get(), classicManagerProvider.get());
  }

  public static BluetoothManager_Factory create(Provider<BluetoothLEManager> bleManagerProvider,
      Provider<BluetoothClassicManager> classicManagerProvider) {
    return new BluetoothManager_Factory(bleManagerProvider, classicManagerProvider);
  }

  public static BluetoothManager newInstance(BluetoothLEManager bleManager,
      BluetoothClassicManager classicManager) {
    return new BluetoothManager(bleManager, classicManager);
  }
}
