/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.feature.throwlet.di

import android.content.Context
import com.buzbuz.smartautoclicker.feature.throwlet.data.GestureStore
import com.buzbuz.smartautoclicker.feature.throwlet.data.ThrowletDatabase
import com.buzbuz.smartautoclicker.feature.throwlet.sync.SupabaseSyncRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ThrowletModule {

    @Provides
    @Singleton
    fun provideThrowletDatabase(@ApplicationContext context: Context): ThrowletDatabase =
        ThrowletDatabase.get(context)

    @Provides
    @Singleton
    fun provideGestureStore(database: ThrowletDatabase): GestureStore =
        GestureStore(database)

    @Provides
    @Singleton
    fun provideSupabaseSyncRepository(
        @ApplicationContext context: Context,
        database: ThrowletDatabase,
    ): SupabaseSyncRepository = SupabaseSyncRepository(context, database)
}
