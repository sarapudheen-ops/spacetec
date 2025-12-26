package com.spacetec.core.database.repository;

import com.spacetec.core.database.dao.DiagnosticSessionDao;
import com.spacetec.core.database.dao.SessionDTCDao;
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
public final class DiagnosticSessionRepository_Factory implements Factory<DiagnosticSessionRepository> {
  private final Provider<DiagnosticSessionDao> sessionDaoProvider;

  private final Provider<SessionDTCDao> sessionDTCDaoProvider;

  public DiagnosticSessionRepository_Factory(Provider<DiagnosticSessionDao> sessionDaoProvider,
      Provider<SessionDTCDao> sessionDTCDaoProvider) {
    this.sessionDaoProvider = sessionDaoProvider;
    this.sessionDTCDaoProvider = sessionDTCDaoProvider;
  }

  @Override
  public DiagnosticSessionRepository get() {
    return newInstance(sessionDaoProvider.get(), sessionDTCDaoProvider.get());
  }

  public static DiagnosticSessionRepository_Factory create(
      Provider<DiagnosticSessionDao> sessionDaoProvider,
      Provider<SessionDTCDao> sessionDTCDaoProvider) {
    return new DiagnosticSessionRepository_Factory(sessionDaoProvider, sessionDTCDaoProvider);
  }

  public static DiagnosticSessionRepository newInstance(DiagnosticSessionDao sessionDao,
      SessionDTCDao sessionDTCDao) {
    return new DiagnosticSessionRepository(sessionDao, sessionDTCDao);
  }
}
