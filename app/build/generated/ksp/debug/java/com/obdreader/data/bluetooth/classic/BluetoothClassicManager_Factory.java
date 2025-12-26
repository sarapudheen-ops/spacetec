package com.obdreader.data.bluetooth.classic;

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
public final class BluetoothClassicManager_Factory implements Factory<BluetoothClassicManager> {
  private final Provider<Context> contextProvider;

  public BluetoothClassicManager_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public BluetoothClassicManager get() {
    return newInstance(contextProvider.get());
  }

  public static BluetoothClassicManager_Factory create(Provider<Context> contextProvider) {
    return new BluetoothClassicManager_Factory(contextProvider);
  }

  public static BluetoothClassicManager newInstance(Context context) {
    return new BluetoothClassicManager(context);
  }
}
