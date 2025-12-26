package com.obdreader.data.bluetooth.ble;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class BluetoothLEManager_Factory implements Factory<BluetoothLEManager> {
  private final Provider<Context> contextProvider;

  public BluetoothLEManager_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public BluetoothLEManager get() {
    return newInstance(contextProvider.get());
  }

  public static BluetoothLEManager_Factory create(Provider<Context> contextProvider) {
    return new BluetoothLEManager_Factory(contextProvider);
  }

  public static BluetoothLEManager newInstance(Context context) {
    return new BluetoothLEManager(context);
  }
}
