package com.buzbuz.smartautoclicker.feature.throwlet

object SplitLaneTransforms {
    fun normalizeForStorage(
        payload: RawGesturePayload,
        sourceLane: HelperLane,
        laneOffsetTouch: Int,
    ): RawGesturePayload = payload

    fun forReplayStored(
        payload: RawGesturePayload,
        sourceLane: HelperLane,
        targetLane: HelperLane,
        laneOffsetTouch: Int,
    ): RawGesturePayload {
        val dy = laneDelta(sourceLane, targetLane, laneOffsetTouch)
        return if (dy == 0) payload else payload.translatedY(dy)
    }

    /** @deprecated Use [forReplayStored] with [sourceLane]. */
    fun forReplayBottomNormalized(
        payload: RawGesturePayload,
        targetLane: HelperLane,
        laneOffsetTouch: Int,
    ): RawGesturePayload = forReplayStored(
        payload = payload,
        sourceLane = HelperLane.SPLIT_BOTTOM,
        targetLane = targetLane,
        laneOffsetTouch = laneOffsetTouch,
    )

    private fun laneDelta(
        sourceLane: HelperLane,
        targetLane: HelperLane,
        laneOffsetTouch: Int,
    ): Int = when {
        sourceLane == targetLane -> 0
        sourceLane == HelperLane.FULL || targetLane == HelperLane.FULL -> 0
        sourceLane == HelperLane.SPLIT_TOP && targetLane == HelperLane.SPLIT_BOTTOM -> laneOffsetTouch
        sourceLane == HelperLane.SPLIT_BOTTOM && targetLane == HelperLane.SPLIT_TOP -> -laneOffsetTouch
        else -> 0
    }
}
