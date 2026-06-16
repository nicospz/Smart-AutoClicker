/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.feature.smart.config.di

import com.buzbuz.smartautoclicker.core.display.recorder.ThrowletCropPicker
import com.buzbuz.smartautoclicker.feature.smart.config.ui.throwlet.ThrowletCropPickerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ThrowletIntegrationModule {

    @Binds
    @Singleton
    abstract fun bindThrowletCropPicker(impl: ThrowletCropPickerImpl): ThrowletCropPicker
}
