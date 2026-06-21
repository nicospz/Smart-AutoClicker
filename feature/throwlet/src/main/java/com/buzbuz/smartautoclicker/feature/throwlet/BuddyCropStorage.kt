package com.buzbuz.smartautoclicker.feature.throwlet

import kotlin.math.roundToInt

object BuddyCropStorage {
    const val SEARCH_PADDING_PX = 96
    const val DEFAULT_THRESHOLD = 85

    private val REFERENCE_SIZE = SizeI(1_440, 3_120)
    private val DEFAULT_CROP = RectI(480, 1_800, 960, 2_400)

    fun defaultCropRect(lane: HelperLane, size: SizeI, dividerPx: Int): RectI {
        val base = DEFAULT_CROP.scaled(REFERENCE_SIZE, size)
        return when (lane) {
            HelperLane.SPLIT_TOP -> base.translated(-dividerPx).clamped(size)
            else -> base.clamped(size)
        }
    }

    fun normalizeForStorage(
        captureLane: HelperLane,
        cropRect: RectI,
        dividerPx: Int,
    ): Pair<HelperLane, RectI> = when (captureLane) {
        HelperLane.FULL -> HelperLane.FULL to cropRect
        HelperLane.SPLIT_BOTTOM -> HelperLane.SPLIT_BOTTOM to cropRect
        HelperLane.SPLIT_TOP -> HelperLane.SPLIT_BOTTOM to cropRect.translated(dividerPx)
    }

    fun runtimeCropRect(
        entity: BuddyCropEntity,
        targetSize: SizeI,
        detectionLane: HelperLane,
        dividerPx: Int,
    ): RectI {
        val stored = RectI(entity.cropLeft, entity.cropTop, entity.cropRight, entity.cropBottom)
        val scaled = stored.scaled(SizeI(entity.sourceWidth, entity.sourceHeight), targetSize)
        return translateForDetectionLane(scaled, detectionLane, dividerPx).clamped(targetSize)
    }

    fun runtimeSearchRect(
        entity: BuddyCropEntity,
        targetSize: SizeI,
        detectionLane: HelperLane,
        dividerPx: Int,
    ): RectI {
        val stored = RectI(entity.cropLeft, entity.cropTop, entity.cropRight, entity.cropBottom)
        val inflated = stored.inflated(SEARCH_PADDING_PX, SizeI(entity.sourceWidth, entity.sourceHeight))
        val scaled = inflated.scaled(SizeI(entity.sourceWidth, entity.sourceHeight), targetSize)
        return translateForDetectionLane(scaled, detectionLane, dividerPx).clamped(targetSize)
    }

    private fun translateForDetectionLane(rect: RectI, detectionLane: HelperLane, dividerPx: Int): RectI =
        when (detectionLane) {
            HelperLane.SPLIT_TOP -> rect.translated(-dividerPx)
            HelperLane.SPLIT_BOTTOM, HelperLane.FULL -> rect
        }

    fun RectI.scaled(from: SizeI, to: SizeI): RectI = RectI(
        left = (left * to.width.toFloat() / from.width).roundToInt(),
        top = (top * to.height.toFloat() / from.height).roundToInt(),
        right = (right * to.width.toFloat() / from.width).roundToInt(),
        bottom = (bottom * to.height.toFloat() / from.height).roundToInt(),
    )

    fun RectI.translated(dy: Int): RectI = copy(top = top + dy, bottom = bottom + dy)

    fun RectI.inflated(padding: Int, size: SizeI): RectI = RectI(
        left = left - padding,
        top = top - padding,
        right = right + padding,
        bottom = bottom + padding,
    ).clamped(size)

    fun RectI.clamped(size: SizeI): RectI {
        val left = left.coerceIn(0, size.width - 1)
        val top = top.coerceIn(0, size.height - 1)
        val right = right.coerceIn(left + 1, size.width)
        val bottom = bottom.coerceIn(top + 1, size.height)
        return RectI(left, top, right, bottom)
    }
}
