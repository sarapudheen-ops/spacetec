package com.spacetec.features.connection;

import com.spacetec.domain.repository.BluetoothDeviceManager;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
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
public final class ConnectionViewModel_Factory implements Factory<ConnectionViewModel> {
  private final Provider<BluetoothDeviceManager> bluetoothManagerProvider;

  public ConnectionViewModel_Factory(Provider<BluetoothDeviceManager> bluetoothManagerProvider) {
    this.bluetoothManagerProvider = bluetoothManagerProvider;
  }

  @Override
  public ConnectionViewModel get() {
    return newInstance(bluetoothManagerProvider.get());
  }

  public static ConnectionViewModel_Factory create(
      Provider<BluetoothDeviceManager> bluetoothManagerProvider) {
    return new ConnectionViewModel_Factory(bluetoothManagerProvider);
  }

  public static ConnectionViewModel newInstance(BluetoothDeviceManager bluetoothManager) {
    return new ConnectionViewModel(bluetoothManager);
  }
}
