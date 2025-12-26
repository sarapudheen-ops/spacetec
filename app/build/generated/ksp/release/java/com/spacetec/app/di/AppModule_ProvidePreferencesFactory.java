package com.spacetec.app.di;

import android.content.Context;
import com.spacetec.core.datastore.SpaceTecPreferences;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
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
public final class AppModule_ProvidePreferencesFactory implements Factory<SpaceTecPreferences> {
  private final Provider<Context> contextProvider;

  public AppModule_ProvidePreferencesFactory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public SpaceTecPreferences get() {
    return providePreferences(contextProvider.get());
  }

  public static AppModule_ProvidePreferencesFactory create(Provider<Context> contextProvider) {
    return new AppModule_ProvidePreferencesFactory(contextProvider);
  }

  public static SpaceTecPreferences providePreferences(Context context) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.providePreferences(context));
  }
}
