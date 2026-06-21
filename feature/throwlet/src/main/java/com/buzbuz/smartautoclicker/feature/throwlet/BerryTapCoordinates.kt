package com.buzbuz.smartautoclicker.feature.throwlet

import kotlin.math.roundToInt

/**
 * Reference berry tap coordinates from a 1440x3120 capture. Scaled to the active frame size.
 */
internal object BerryTapCoordinates {
    private const val REF_WIDTH = 1440
    private const val REF_HEIGHT = 3120

    fun menuTap(screenWidth: Int, screenHeight: Int, lane: HelperLane): Pair<Int, Int> =
        scale(screenWidth, screenHeight, referenceMenuTap(lane))

    fun berryTap(screenWidth: Int, screenHeight: Int, lane: HelperLane, berry: BerryAction): Pair<Int, Int>? {
        val reference = referenceBerryTap(lane, berry) ?: return null
        return scale(screenWidth, screenHeight, reference)
    }

    fun confirmTap(screenWidth: Int, screenHeight: Int, lane: HelperLane): Pair<Int, Int> =
        scale(screenWidth, screenHeight, referenceConfirmTap(lane))

    private fun referenceMenuTap(lane: HelperLane): Pair<Int, Int> = when (lane) {
        HelperLane.SPLIT_TOP -> 180 to 1310
        HelperLane.SPLIT_BOTTOM -> 180 to 2780
        HelperLane.FULL -> 180 to 2800
    }

    private fun referenceConfirmTap(lane: HelperLane): Pair<Int, Int> = when (lane) {
        HelperLane.SPLIT_TOP -> 740 to 1460
        HelperLane.SPLIT_BOTTOM -> 740 to 3020
        HelperLane.FULL -> 740 to 2800
    }

    private fun referenceBerryTap(lane: HelperLane, berry: BerryAction): Pair<Int, Int>? = when (lane) {
        HelperLane.SPLIT_TOP -> when (berry) {
            BerryAction.PINAP -> 1180 to 890
            BerryAction.GOLDEN_RAZZ -> 260 to 1310
            BerryAction.SILVER_PINAP -> 700 to 1310
            BerryAction.NONE -> null
        }
        HelperLane.SPLIT_BOTTOM -> when (berry) {
            BerryAction.PINAP -> 1180 to 2370
            BerryAction.GOLDEN_RAZZ -> 260 to 2780
            BerryAction.SILVER_PINAP -> 700 to 2780
            BerryAction.NONE -> null
        }
        HelperLane.FULL -> when (berry) {
            BerryAction.PINAP -> 1150 to 2400
            BerryAction.GOLDEN_RAZZ -> 250 to 2800
            BerryAction.SILVER_PINAP -> 718 to 2800
            BerryAction.NONE -> null
        }
    }

    private fun scale(screenWidth: Int, screenHeight: Int, point: Pair<Int, Int>): Pair<Int, Int> {
        val scaleX = screenWidth.toFloat() / REF_WIDTH.toFloat()
        val scaleY = screenHeight.toFloat() / REF_HEIGHT.toFloat()
        val x = (point.first * scaleX).roundToInt().coerceIn(0, screenWidth.coerceAtLeast(1) - 1)
        val y = (point.second * scaleY).roundToInt().coerceIn(0, screenHeight.coerceAtLeast(1) - 1)
        return x to y
    }
}
