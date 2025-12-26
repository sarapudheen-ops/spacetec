package com.spacetec.core.database.di;

import com.spacetec.core.database.SpaceTecDatabase;
import com.spacetec.core.database.dao.DTCDao;
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
public final class DatabaseModule_ProvideDTCDaoFactory implements Factory<DTCDao> {
  private final Provider<SpaceTecDatabase> databaseProvider;

  public DatabaseModule_ProvideDTCDaoFactory(Provider<SpaceTecDatabase> databaseProvider) {
    this.databaseProvider = databaseProvider;
  }

  @Override
  public DTCDao get() {
    return provideDTCDao(databaseProvider.get());
  }

  public static DatabaseModule_ProvideDTCDaoFactory create(
      Provider<SpaceTecDatabase> databaseProvider) {
    return new DatabaseModule_ProvideDTCDaoFactory(databaseProvider);
  }

  public static DTCDao provideDTCDao(SpaceTecDatabase database) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideDTCDao(database));
  }
}
