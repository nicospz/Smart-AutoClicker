package com.buzbuz.smartautoclicker.feature.throwlet

import android.content.Context
import android.graphics.Insets
import android.os.Build
import android.view.InputDevice
import android.view.WindowInsets
import android.view.WindowManager
import kotlin.math.roundToInt

data class DisplayProfile(
    val displayWidth: Int,
    val displayHeight: Int,
    val touchMaxX: Int,
    val touchMaxY: Int,
    val statusBarInsetPx: Int,
) {
    val playableHeightPx: Int get() = (displayHeight - statusBarInsetPx).coerceAtLeast(1)

    /** Split-line Y in screenshot pixels (top lane is above this line). */
    fun defaultScreenshotDividerPx(): Int =
        (playableHeightPx.toFloat() / 2f).roundToInt().coerceAtLeast(1)

    /** Distance between matching points in top vs bottom lanes (equals split-line Y on Samsung Ultra). */
    fun laneOffsetScreenshotPx(dividerPx: Int): Int =
        dividerPx.coerceIn(1, displayHeight - 1)

    fun defaultLaneOffsetScreenshotPx(): Int = defaultScreenshotDividerPx()

    fun defaultTouchLaneOffset(): Int =
        laneOffsetTouchFromDivider(defaultScreenshotDividerPx())

    fun laneOffsetTouchFromDivider(dividerPx: Int): Int =
        screenDeltaToTouch(laneOffsetScreenshotPx(dividerPx)).coerceAtLeast(1)

    fun screenDeltaToTouch(screenDelta: Int): Int =
        (screenDelta.toFloat() * TouchCoordinateSpace.TOUCH_COORDINATE_MAX.toFloat() / displayHeight.toFloat()).roundToInt()

    fun touchDeltaToScreen(touchDelta: Int): Int =
        (touchDelta.toFloat() * displayHeight.toFloat() / TouchCoordinateSpace.TOUCH_COORDINATE_MAX.toFloat()).roundToInt()

    fun screenXFromTouch(touchX: Int): Int =
        (touchX.toFloat() * displayWidth.toFloat() / TouchCoordinateSpace.TOUCH_COORDINATE_MAX.toFloat()).roundToInt()

    fun screenYFromTouch(touchY: Int): Int =
        (touchY.toFloat() * displayHeight.toFloat() / TouchCoordinateSpace.TOUCH_COORDINATE_MAX.toFloat()).roundToInt()

    fun touchXFromScreen(screenX: Int): Int =
        (screenX.toFloat() * TouchCoordinateSpace.TOUCH_COORDINATE_MAX.toFloat() / displayWidth.toFloat()).roundToInt()

    fun touchYFromScreen(screenY: Int): Int =
        (screenY.toFloat() * TouchCoordinateSpace.TOUCH_COORDINATE_MAX.toFloat() / displayHeight.toFloat()).roundToInt()
}

object TouchCoordinateSpace {
    /**
     * Evdev touch coordinate height used for screen↔touch scaling on Samsung Ultra devices.
     * SmartPoGo hardcodes 4096 here; it is separate from [DisplayProfile.touchMaxY], which is
     * whatever Android's InputDevice API reports and is often closer to display height (e.g. ~3119).
     */
    const val TOUCH_COORDINATE_MAX = 4096

    private const val FALLBACK_TOUCH_MAX = TOUCH_COORDINATE_MAX

    fun profile(context: Context): DisplayProfile {
        val metrics = context.resources.displayMetrics
        val touchRange = largestTouchRange()
        return DisplayProfile(
            displayWidth = metrics.widthPixels.coerceAtLeast(1),
            displayHeight = metrics.heightPixels.coerceAtLeast(1),
            touchMaxX = touchRange?.first ?: FALLBACK_TOUCH_MAX,
            touchMaxY = touchRange?.second ?: FALLBACK_TOUCH_MAX,
            statusBarInsetPx = statusBarInsetPx(context),
        )
    }

    private fun largestTouchRange(): Pair<Int, Int>? {
        var bestX = 0
        var bestY = 0
        InputDevice.getDeviceIds().forEach { deviceId ->
            val device = InputDevice.getDevice(deviceId) ?: return@forEach
            if (!device.supportsSource(InputDevice.SOURCE_TOUCHSCREEN)) return@forEach
            val xRange = device.getMotionRange(InputDevice.MOTION_RANGE_X, InputDevice.SOURCE_TOUCHSCREEN)
            val yRange = device.getMotionRange(InputDevice.MOTION_RANGE_Y, InputDevice.SOURCE_TOUCHSCREEN)
            if (xRange != null && yRange != null) {
                bestX = maxOf(bestX, xRange.max.roundToInt())
                bestY = maxOf(bestY, yRange.max.roundToInt())
            }
        }
        return if (bestX > 0 && bestY > 0) bestX to bestY else null
    }

    private fun statusBarInsetPx(context: Context): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val insets: Insets? = context.getSystemService(WindowManager::class.java)
                ?.currentWindowMetrics
                ?.windowInsets
                ?.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars())
            if (insets != null) return insets.top.coerceAtLeast(0)
        }
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) context.resources.getDimensionPixelSize(resourceId) else 0
    }
}
