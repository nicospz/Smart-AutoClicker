/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.feature.smart.config.ui.throwlet

import android.content.Context
import android.util.Log
import com.buzbuz.smartautoclicker.core.common.overlays.manager.OverlayManager
import com.buzbuz.smartautoclicker.core.common.overlays.manager.OverlayServiceContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThrowletOverlayContextProvider @Inject constructor(
    private val overlayServiceContext: OverlayServiceContext,
    private val overlayManager: OverlayManager,
) {
    fun overlayContext(): Context? {
        val stackHidden = overlayManager.isStackHidden()
        val bottom = overlayManager.getBackStackBottom()
        val top = overlayManager.getBackStackTop()
        Log.i(
            TAG,
            "resolve overlay context stackHidden=$stackHidden " +
                "bottom=${bottom?.javaClass?.simpleName ?: "<none>"} " +
                "top=${top?.javaClass?.simpleName ?: "<none>"}",
        )
        overlayServiceContext.contextOrNull()?.let { serviceContext ->
            Log.i(TAG, "using service context=${serviceContext.javaClass.name}")
            return serviceContext
        }
        val rootContext = bottom?.let { runCatching { it.context }.getOrNull() }
        if (rootContext != null) {
            Log.i(TAG, "using back-stack bottom context=${rootContext.javaClass.name}")
            return rootContext
        }
        val topContext = top?.let { runCatching { it.context }.getOrNull() }
        if (topContext != null) {
            Log.w(TAG, "using back-stack top context=${topContext.javaClass.name}")
            return topContext
        }
        Log.e(TAG, "no overlay context; enable SAC accessibility service and start a smart scenario")
        return null
    }

    companion object {
        private const val TAG = "ThrowletOverlayCtx"
    }
}
