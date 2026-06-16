/*
 * Copyright (C) 2026 Nicolas Espinoza
 *
 * Interactive crop picker invoked by Throwlet over the frame broker.
 */
package com.buzbuz.smartautoclicker.core.display.recorder

import android.graphics.Bitmap
import android.graphics.Rect

data class ThrowletCropPickResult(
    val frameWidth: Int,
    val frameHeight: Int,
    val cropLeft: Int,
    val cropTop: Int,
    val cropRight: Int,
    val cropBottom: Int,
    val cropPng: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ThrowletCropPickResult) return false
        return frameWidth == other.frameWidth &&
            frameHeight == other.frameHeight &&
            cropLeft == other.cropLeft &&
            cropTop == other.cropTop &&
            cropRight == other.cropRight &&
            cropBottom == other.cropBottom &&
            cropPng.contentEquals(other.cropPng)
    }

    override fun hashCode(): Int {
        var result = frameWidth
        result = 31 * result + frameHeight
        result = 31 * result + cropLeft
        result = 31 * result + cropTop
        result = 31 * result + cropRight
        result = 31 * result + cropBottom
        result = 31 * result + cropPng.contentHashCode()
        return result
    }
}

interface ThrowletCropPicker {
    suspend fun pickCrop(frame: Bitmap, defaultArea: Rect?): ThrowletCropPickResult?
}
