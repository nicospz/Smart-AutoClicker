/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.sync

import com.buzbuz.smartautoclicker.feature.sync.data.SettingsSyncLocalPort
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class SacSyncBindingsModule {

    @Binds
    abstract fun bindSettingsSyncLocalPort(impl: SettingsSyncLocalPortImpl): SettingsSyncLocalPort
}
