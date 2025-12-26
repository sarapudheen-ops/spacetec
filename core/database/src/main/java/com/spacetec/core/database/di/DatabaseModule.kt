package com.spacetec.core.database.di

import android.content.Context
import androidx.room.Room
import com.spacetec.core.database.SpaceTecDatabase
import com.spacetec.core.database.dao.DTCDao
import com.spacetec.core.database.dao.DiagnosticSessionDao
import com.spacetec.core.database.dao.SessionDTCDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Provides
    @Singleton
    fun provideSpaceTecDatabase(@ApplicationContext context: Context): SpaceTecDatabase {
        return SpaceTecDatabase.getDatabase(context)
    }
    
    @Provides
    fun provideDTCDao(database: SpaceTecDatabase): DTCDao = database.dtcDao()
    
    @Provides
    fun provideDiagnosticSessionDao(database: SpaceTecDatabase): DiagnosticSessionDao = 
        database.diagnosticSessionDao()
    
    @Provides
    fun provideSessionDTCDao(database: SpaceTecDatabase): SessionDTCDao = database.sessionDTCDao()
}
