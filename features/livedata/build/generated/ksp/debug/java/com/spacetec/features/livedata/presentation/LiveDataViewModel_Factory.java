package com.spacetec.features.livedata.presentation;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

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
public final class LiveDataViewModel_Factory implements Factory<LiveDataViewModel> {
  @Override
  public LiveDataViewModel get() {
    return newInstance();
  }

  public static LiveDataViewModel_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static LiveDataViewModel newInstance() {
    return new LiveDataViewModel();
  }

  private static final class InstanceHolder {
    private static final LiveDataViewModel_Factory INSTANCE = new LiveDataViewModel_Factory();
  }
}
