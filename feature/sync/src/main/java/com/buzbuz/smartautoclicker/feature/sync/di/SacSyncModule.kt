/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.feature.sync.di

import com.buzbuz.smartautoclicker.feature.sync.domain.SacSyncEngine
import dagger.Module
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SacSyncWorkerEntryPoint {
    fun syncEngine(): SacSyncEngine
}

@Module
@InstallIn(SingletonComponent::class)
object SacSyncModule
