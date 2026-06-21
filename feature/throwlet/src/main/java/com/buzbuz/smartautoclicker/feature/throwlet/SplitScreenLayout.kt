package com.buzbuz.smartautoclicker.feature.throwlet

import android.content.Context
import kotlinx.coroutines.runBlocking

/** Split-screen geometry shared by overlays, gestures, and OCR crops. */
data class SplitScreenLayout(
    val dividerPx: Int,
    val laneOffsetTouch: Int,
) {
    val laneOffsetScreenPx: Int get() = dividerPx

    fun fullYForLane(localY: Int, lane: HelperLane): Int = when (lane) {
        HelperLane.SPLIT_TOP -> localY
        HelperLane.SPLIT_BOTTOM -> dividerPx + localY
        HelperLane.FULL -> localY
    }

    fun localYForLane(fullY: Int, lane: HelperLane): Int = when (lane) {
        HelperLane.SPLIT_TOP -> fullY
        HelperLane.SPLIT_BOTTOM -> fullY - dividerPx
        HelperLane.FULL -> fullY
    }

    fun defaultLocalActionY(mode: HelperMode): Int {
        val margin = when (mode) {
            HelperMode.CATCH -> 380
            HelperMode.BUDDY -> 320
        }
        return (dividerPx - margin).coerceAtLeast(96)
    }

    fun laneYRange(lane: HelperLane, viewHeight: Int, screenHeight: Int): IntRange = when (lane) {
        HelperLane.SPLIT_TOP -> 0..(dividerPx - viewHeight).coerceAtLeast(0)
        HelperLane.SPLIT_BOTTOM -> dividerPx..(screenHeight - viewHeight).coerceAtLeast(dividerPx)
        HelperLane.FULL -> 0..(screenHeight - viewHeight).coerceAtLeast(0)
    }
}

object SplitScreenLayouts {
    fun fromCalibration(context: Context, calibration: SplitCalibration): SplitScreenLayout {
        val profile = TouchCoordinateSpace.profile(context)
        val divider = calibration.topToBottomScreenshotDy ?: profile.defaultScreenshotDividerPx()
        return SplitScreenLayout(
            dividerPx = divider,
            laneOffsetTouch = calibration.topToBottomTouchDy,
        )
    }

    fun load(context: Context, calibrationStore: SplitCalibrationStore): SplitScreenLayout =
        runBlocking { fromCalibration(context, calibrationStore.load()) }
}
