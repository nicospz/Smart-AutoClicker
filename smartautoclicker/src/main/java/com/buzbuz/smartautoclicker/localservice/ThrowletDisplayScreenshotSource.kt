/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.localservice

import android.graphics.Bitmap
import com.buzbuz.smartautoclicker.core.display.recorder.AccessibilityScreenshotProvider
import com.buzbuz.smartautoclicker.core.display.recorder.DisplayRecorder
import com.buzbuz.smartautoclicker.feature.throwlet.ThrowletScreenshotSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

internal class ThrowletDisplayScreenshotSource(
    private val displayRecorder: DisplayRecorder,
    private val accessibilityScreenshotProvider: AccessibilityScreenshotProvider,
    private val useDisplayRecorder: () -> Boolean,
) : ThrowletScreenshotSource {
    override fun captureBlocking(): Bitmap? = runBlocking(Dispatchers.IO) {
        if (useDisplayRecorder()) {
            displayRecorder.acquireLatestBitmap()?.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            null
        } ?: withTimeoutOrNull(ACCESSIBILITY_SCREENSHOT_TIMEOUT_MS) {
            accessibilityScreenshotProvider.takeScreenshot()
        }
    }
}

private const val ACCESSIBILITY_SCREENSHOT_TIMEOUT_MS = 5_000L
