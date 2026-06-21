package com.buzbuz.smartautoclicker.feature.throwlet

import android.content.Context
import android.os.Build
import com.buzbuz.smartautoclicker.feature.throwlet.data.ThrowletDatabase

class SplitCalibrationStore(
    private val db: ThrowletDatabase,
    private val context: Context,
) {
    private val profile: DisplayProfile
        get() = TouchCoordinateSpace.profile(context)

    fun defaultTouchOffset(display: DisplayProfile = profile): Int =
        SplitLaneCalibrationResolver.touchOffset(Build.MODEL, Build.DEVICE)
            ?: display.defaultTouchLaneOffset()

    suspend fun load(profileKey: String = "default"): SplitCalibration {
        val display = profile
        val stored = db.splitCalibrationDao().get(profileKey)
        val device = SplitLaneCalibrationResolver.resolve(Build.MODEL, Build.DEVICE)
        val defaultTouch = defaultTouchOffset(display)
        val screenshotDivider = normalizeDividerPx(
            stored?.topToBottomScreenshotDy ?: device?.screenObjectOffsetPx,
            display,
        )
        val touchOffset = stored?.topToBottomTouchDy?.takeIf { it > 0 } ?: defaultTouch
        return SplitCalibration(
            profileKey = profileKey,
            topToBottomTouchDy = touchOffset,
            topToBottomScreenshotDy = screenshotDivider,
            updatedAtMs = stored?.updatedAtMs ?: System.currentTimeMillis(),
        )
    }

    suspend fun save(
        touchOffset: Int,
        screenshotLaneDividerPx: Int,
        profileKey: String = "default",
    ) {
        val display = profile
        val dividerPx = screenshotLaneDividerPx.coerceIn(1, display.displayHeight - 1)
        val touchDy = touchOffset.coerceAtLeast(1)
        db.splitCalibrationDao().upsert(
            SplitCalibrationEntity(
                profileKey = profileKey,
                topToBottomTouchDy = touchDy,
                topToBottomScreenshotDy = dividerPx,
                updatedAtMs = System.currentTimeMillis(),
            ),
        )
        ThrowletLog.i(
            "calibration saved dividerPx=$dividerPx touchOffset=$touchDy display=${display.displayWidth}x${display.displayHeight} touchMax=${display.touchMaxY}",
        )
    }

    fun default(profileKey: String = "default", display: DisplayProfile = profile): SplitCalibration {
        val device = SplitLaneCalibrationResolver.resolve(Build.MODEL, Build.DEVICE)
        val dividerPx = SplitLaneCalibrationResolver.laneDividerPx(display.displayHeight, device?.screenObjectOffsetPx)
        val touchOffset = device?.touchOffset ?: display.defaultTouchLaneOffset()
        return SplitCalibration(
            profileKey = profileKey,
            topToBottomTouchDy = touchOffset,
            topToBottomScreenshotDy = dividerPx,
            updatedAtMs = System.currentTimeMillis(),
        )
    }

    fun touchOffsetAdjustmentFromStored(stored: SplitCalibration?, display: DisplayProfile = profile): Int {
        val base = defaultTouchOffset(display)
        val effective = stored?.topToBottomTouchDy ?: base
        return effective - base
    }

    fun defaultScreenshotDivider(display: DisplayProfile = profile): Int {
        val deviceDivider = SplitLaneCalibrationResolver.screenObjectOffsetPx(Build.MODEL, Build.DEVICE)
        return normalizeDividerPx(deviceDivider, display)
    }

    private fun normalizeDividerPx(storedDividerPx: Int?, display: DisplayProfile): Int {
        if (storedDividerPx == null) return display.defaultScreenshotDividerPx()
        // Older saves stored the lane offset here instead of the split-line Y coordinate.
        return if (storedDividerPx > display.displayHeight / 2) {
            display.displayHeight - storedDividerPx
        } else {
            storedDividerPx
        }
    }
}
