package com.spacetec.core.database.di;

import com.spacetec.core.database.SpaceTecDatabase;
import com.spacetec.core.database.dao.SessionDTCDao;
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
public final class DatabaseModule_ProvideSessionDTCDaoFactory implements Factory<SessionDTCDao> {
  private final Provider<SpaceTecDatabase> databaseProvider;

  public DatabaseModule_ProvideSessionDTCDaoFactory(Provider<SpaceTecDatabase> databaseProvider) {
    this.databaseProvider = databaseProvider;
  }

  @Override
  public SessionDTCDao get() {
    return provideSessionDTCDao(databaseProvider.get());
  }

  public static DatabaseModule_ProvideSessionDTCDaoFactory create(
      Provider<SpaceTecDatabase> databaseProvider) {
    return new DatabaseModule_ProvideSessionDTCDaoFactory(databaseProvider);
  }

  public static SessionDTCDao provideSessionDTCDao(SpaceTecDatabase database) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideSessionDTCDao(database));
  }
}
