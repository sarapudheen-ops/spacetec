package com.spacetec.core.database.di;

import com.spacetec.core.database.SpaceTecDatabase;
import com.spacetec.core.database.dao.DiagnosticSessionDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
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
public final class DatabaseModule_ProvideDiagnosticSessionDaoFactory implements Factory<DiagnosticSessionDao> {
  private final Provider<SpaceTecDatabase> databaseProvider;

  public DatabaseModule_ProvideDiagnosticSessionDaoFactory(
      Provider<SpaceTecDatabase> databaseProvider) {
    this.databaseProvider = databaseProvider;
  }

  @Override
  public DiagnosticSessionDao get() {
    return provideDiagnosticSessionDao(databaseProvider.get());
  }

  public static DatabaseModule_ProvideDiagnosticSessionDaoFactory create(
      Provider<SpaceTecDatabase> databaseProvider) {
    return new DatabaseModule_ProvideDiagnosticSessionDaoFactory(databaseProvider);
  }

  public static DiagnosticSessionDao provideDiagnosticSessionDao(SpaceTecDatabase database) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideDiagnosticSessionDao(database));
  }
}
