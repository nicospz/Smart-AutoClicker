package com.buzbuz.smartautoclicker.feature.throwlet

object LaneGeometry {
    fun cropFor(lane: HelperLane, size: SizeI, screenshotDividerPx: Int? = null): RectI {
        val divider = (screenshotDividerPx ?: (size.height / 2)).coerceIn(1, size.height - 1)
        return when (lane) {
            HelperLane.FULL -> RectI(0, 0, size.width, size.height)
            HelperLane.SPLIT_TOP -> RectI(0, 0, size.width, divider)
            HelperLane.SPLIT_BOTTOM -> RectI(0, divider, size.width, size.height)
        }
    }

    fun dividerForBitmap(size: SizeI, profile: DisplayProfile, displayDividerPx: Int?): Int {
        val divider = displayDividerPx ?: profile.defaultScreenshotDividerPx()
        val scaledDivider = if (profile.displayHeight == size.height) {
            divider
        } else {
            (divider.toFloat() * size.height.toFloat() / profile.displayHeight.toFloat()).toInt()
        }
        return scaledDivider.coerceIn(1, size.height - 1)
    }

    fun badgeFor(lane: HelperLane): String = when (lane) {
        HelperLane.FULL -> "F"
        HelperLane.SPLIT_TOP -> "T"
        HelperLane.SPLIT_BOTTOM -> "B"
    }
}
