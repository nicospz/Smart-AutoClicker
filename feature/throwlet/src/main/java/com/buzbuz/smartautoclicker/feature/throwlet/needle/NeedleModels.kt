package com.buzbuz.smartautoclicker.feature.throwlet.needle

import android.graphics.Rect
import kotlin.math.roundToInt

enum class NeedleLane { FULL, TOP, BOTTOM }

enum class CatchNeedleFeature(val manifestValue: String) {
    BERRY_MENU("catch_berry_menu"),
    POKEBALL_READY("catch_pokeball_ready");

    companion object {
        fun fromManifestValue(value: String): CatchNeedleFeature? =
            entries.firstOrNull { it.manifestValue == value }
    }
}

enum class BuddyNeedleFeature(val manifestValue: String) {
    CAMERA_BUTTON("buddy_camera_button"),
    BERRY_MENU("buddy_berry_menu"),
    NANAB_BERRY("buddy_nanab_berry");

    companion object {
        fun fromManifestValue(value: String): BuddyNeedleFeature? =
            entries.firstOrNull { it.manifestValue == value }
    }
}

data class CatchNeedle(
    val feature: CatchNeedleFeature,
    val lane: NeedleLane,
    val variantOrder: Int,
    val assetPath: String,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val cropLeft: Int,
    val cropTop: Int,
    val cropRight: Int,
    val cropBottom: Int,
    val searchLeft: Int,
    val searchTop: Int,
    val searchRight: Int,
    val searchBottom: Int,
    val tapX: Int,
    val tapY: Int,
    val thresholdPercent: Int,
) {
    fun expectedRect(screenWidth: Int, screenHeight: Int): Rect {
        val scaleX = screenWidth.toFloat() / sourceWidth.toFloat()
        val scaleY = screenHeight.toFloat() / sourceHeight.toFloat()
        return Rect(
            (cropLeft * scaleX).roundToInt(),
            (cropTop * scaleY).roundToInt(),
            (cropRight * scaleX).roundToInt(),
            (cropBottom * scaleY).roundToInt(),
        )
    }

    fun searchRect(screenWidth: Int, screenHeight: Int): Rect {
        val scaleX = screenWidth.toFloat() / sourceWidth.toFloat()
        val scaleY = screenHeight.toFloat() / sourceHeight.toFloat()
        return Rect(
            (searchLeft * scaleX).roundToInt(),
            (searchTop * scaleY).roundToInt(),
            (searchRight * scaleX).roundToInt(),
            (searchBottom * scaleY).roundToInt(),
        ).apply { intersect(0, 0, screenWidth, screenHeight) }
    }

    fun tapPoint(screenWidth: Int, screenHeight: Int): Pair<Int, Int> {
        val scaleX = screenWidth.toFloat() / sourceWidth.toFloat()
        val scaleY = screenHeight.toFloat() / sourceHeight.toFloat()
        return (tapX * scaleX).roundToInt().coerceIn(0, screenWidth.coerceAtLeast(1) - 1) to
            (tapY * scaleY).roundToInt().coerceIn(0, screenHeight.coerceAtLeast(1) - 1)
    }
}

data class LoadedCatchNeedle(
    val needle: CatchNeedle,
    val template: android.graphics.Bitmap,
)

data class NeedleMatch(
    val feature: CatchNeedleFeature,
    val lane: NeedleLane,
    val scorePercent: Int,
    val tapX: Int,
    val tapY: Int,
)

data class BuddyNeedle(
    val feature: BuddyNeedleFeature,
    val lane: NeedleLane,
    val variantOrder: Int,
    val assetPath: String,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val cropLeft: Int,
    val cropTop: Int,
    val cropRight: Int,
    val cropBottom: Int,
    val searchLeft: Int,
    val searchTop: Int,
    val searchRight: Int,
    val searchBottom: Int,
    val tapX: Int,
    val tapY: Int,
    val thresholdPercent: Int,
) {
    fun expectedRect(screenWidth: Int, screenHeight: Int): Rect {
        val scaleX = screenWidth.toFloat() / sourceWidth.toFloat()
        val scaleY = screenHeight.toFloat() / sourceHeight.toFloat()
        return Rect(
            (cropLeft * scaleX).roundToInt(),
            (cropTop * scaleY).roundToInt(),
            (cropRight * scaleX).roundToInt(),
            (cropBottom * scaleY).roundToInt(),
        )
    }

    fun searchRect(screenWidth: Int, screenHeight: Int): Rect {
        val scaleX = screenWidth.toFloat() / sourceWidth.toFloat()
        val scaleY = screenHeight.toFloat() / sourceHeight.toFloat()
        return Rect(
            (searchLeft * scaleX).roundToInt(),
            (searchTop * scaleY).roundToInt(),
            (searchRight * scaleX).roundToInt(),
            (searchBottom * scaleY).roundToInt(),
        ).apply { intersect(0, 0, screenWidth, screenHeight) }
    }

    fun tapPoint(screenWidth: Int, screenHeight: Int): Pair<Int, Int> {
        val scaleX = screenWidth.toFloat() / sourceWidth.toFloat()
        val scaleY = screenHeight.toFloat() / sourceHeight.toFloat()
        return (tapX * scaleX).roundToInt().coerceIn(0, screenWidth.coerceAtLeast(1) - 1) to
            (tapY * scaleY).roundToInt().coerceIn(0, screenHeight.coerceAtLeast(1) - 1)
    }
}

data class LoadedBuddyNeedle(
    val needle: BuddyNeedle,
    val template: android.graphics.Bitmap,
)

data class BuddyNeedleMatch(
    val feature: BuddyNeedleFeature,
    val lane: NeedleLane,
    val scorePercent: Int,
    val tapX: Int,
    val tapY: Int,
)
