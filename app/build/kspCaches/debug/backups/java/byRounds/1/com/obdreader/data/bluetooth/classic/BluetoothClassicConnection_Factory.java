package com.obdreader.data.bluetooth.classic;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;
import kotlinx.coroutines.CoroutineDispatcher;

@ScopeMetadata
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
public final class BluetoothClassicConnection_Factory implements Factory<BluetoothClassicConnection> {
  private final Provider<Context> contextProvider;

  private final Provider<CoroutineDispatcher> dispatcherProvider;

  public BluetoothClassicConnection_Factory(Provider<Context> contextProvider,
      Provider<CoroutineDispatcher> dispatcherProvider) {
    this.contextProvider = contextProvider;
    this.dispatcherProvider = dispatcherProvider;
  }

  @Override
  public BluetoothClassicConnection get() {
    return newInstance(contextProvider.get(), dispatcherProvider.get());
  }

  public static BluetoothClassicConnection_Factory create(Provider<Context> contextProvider,
      Provider<CoroutineDispatcher> dispatcherProvider) {
    return new BluetoothClassicConnection_Factory(contextProvider, dispatcherProvider);
  }

  public static BluetoothClassicConnection newInstance(Context context,
      CoroutineDispatcher dispatcher) {
    return new BluetoothClassicConnection(context, dispatcher);
  }
}
