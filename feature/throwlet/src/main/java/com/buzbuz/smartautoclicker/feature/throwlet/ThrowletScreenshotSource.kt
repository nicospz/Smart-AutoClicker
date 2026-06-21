/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.feature.throwlet

import android.graphics.Bitmap

/** Supplies the latest screen frame for Throwlet OCR and buddy-crop matching. */
fun interface ThrowletScreenshotSource {
    fun captureBlocking(): Bitmap?
}
