/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.localservice

import android.graphics.Bitmap
import com.buzbuz.smartautoclicker.core.display.recorder.DisplayRecorder
import com.buzbuz.smartautoclicker.feature.throwlet.ThrowletScreenshotSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

internal class ThrowletDisplayScreenshotSource(
    private val displayRecorder: DisplayRecorder,
) : ThrowletScreenshotSource {
    override fun captureBlocking(): Bitmap? = runBlocking(Dispatchers.IO) {
        displayRecorder.acquireLatestBitmap()?.copy(Bitmap.Config.ARGB_8888, false)
    }
}
