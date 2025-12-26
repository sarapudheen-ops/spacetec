package com.spacetec.app.di

import android.content.Context
import com.spacetec.core.datastore.SpaceTecPreferences
import com.spacetec.core.logging.SpaceTecLogger
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    @Provides
    @Singleton
    fun providePreferences(@ApplicationContext context: Context) = SpaceTecPreferences(context)
    
}
