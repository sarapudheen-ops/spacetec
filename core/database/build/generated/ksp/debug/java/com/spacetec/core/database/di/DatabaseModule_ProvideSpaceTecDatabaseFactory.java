package com.spacetec.core.database.di;

import android.content.Context;
import com.spacetec.core.database.SpaceTecDatabase;
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
public final class DatabaseModule_ProvideSpaceTecDatabaseFactory implements Factory<SpaceTecDatabase> {
  private final Provider<Context> contextProvider;

  public DatabaseModule_ProvideSpaceTecDatabaseFactory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public SpaceTecDatabase get() {
    return provideSpaceTecDatabase(contextProvider.get());
  }

  public static DatabaseModule_ProvideSpaceTecDatabaseFactory create(
      Provider<Context> contextProvider) {
    return new DatabaseModule_ProvideSpaceTecDatabaseFactory(contextProvider);
  }

  public static SpaceTecDatabase provideSpaceTecDatabase(Context context) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideSpaceTecDatabase(context));
  }
}
