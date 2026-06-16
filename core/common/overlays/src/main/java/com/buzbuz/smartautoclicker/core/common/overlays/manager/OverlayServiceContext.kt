/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.core.common.overlays.manager

import android.content.Context
import javax.inject.Inject
import javax.inject.Singleton

/** Accessibility-service context used to attach Throwlet/SAC overlay windows. */
@Singleton
class OverlayServiceContext @Inject constructor() {
    @Volatile
    private var context: Context? = null

    fun attach(context: Context) {
        this.context = context
    }

    fun detach(context: Context) {
        if (this.context === context) {
            this.context = null
        }
    }

    fun contextOrNull(): Context? = context
}
