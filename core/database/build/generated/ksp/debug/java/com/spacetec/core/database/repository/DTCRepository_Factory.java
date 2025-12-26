package com.spacetec.core.database.repository;

import com.spacetec.core.database.dao.DTCDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
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
public final class DTCRepository_Factory implements Factory<DTCRepository> {
  private final Provider<DTCDao> dtcDaoProvider;

  public DTCRepository_Factory(Provider<DTCDao> dtcDaoProvider) {
    this.dtcDaoProvider = dtcDaoProvider;
  }

  @Override
  public DTCRepository get() {
    return newInstance(dtcDaoProvider.get());
  }

  public static DTCRepository_Factory create(Provider<DTCDao> dtcDaoProvider) {
    return new DTCRepository_Factory(dtcDaoProvider);
  }

  public static DTCRepository newInstance(DTCDao dtcDao) {
    return new DTCRepository(dtcDao);
  }
}
