package com.buzbuz.smartautoclicker.feature.throwlet

import org.junit.Assert.assertEquals
import org.junit.Test

class GestureModesTest {
    @Test
    fun catchLanesMapToExpectedStorageModes() {
        assertEquals(GestureMode.CATCH_FULL, GestureModes.storageMode(HelperMode.CATCH, HelperLane.FULL))
        assertEquals(
            GestureMode.CATCH_SPLIT_BOTTOM_NORMALIZED,
            GestureModes.storageMode(HelperMode.CATCH, HelperLane.SPLIT_TOP),
        )
        assertEquals(
            GestureMode.CATCH_SPLIT_BOTTOM_NORMALIZED,
            GestureModes.storageMode(HelperMode.CATCH, HelperLane.SPLIT_BOTTOM),
        )
    }

    @Test
    fun buddyLanesMapToExpectedStorageModes() {
        assertEquals(GestureMode.BUDDY_FULL, GestureModes.storageMode(HelperMode.BUDDY, HelperLane.FULL))
        assertEquals(
            GestureMode.BUDDY_SPLIT_BOTTOM_NORMALIZED,
            GestureModes.storageMode(HelperMode.BUDDY, HelperLane.SPLIT_TOP),
        )
        assertEquals(
            GestureMode.BUDDY_SPLIT_BOTTOM_NORMALIZED,
            GestureModes.storageMode(HelperMode.BUDDY, HelperLane.SPLIT_BOTTOM),
        )
    }

    @Test
    fun replayTransform_usesSourceAndTargetLanes() {
        val payload = RawGesturePayload(
            durationMs = 100L,
            events = listOf(RawGestureEvent(0L, 0L, 0L, 0x03, 0x36, 1000)),
        )

        assertEquals(
            3060,
            SplitLaneTransforms.forReplayStored(
                payload = payload,
                sourceLane = HelperLane.SPLIT_TOP,
                targetLane = HelperLane.SPLIT_BOTTOM,
                laneOffsetTouch = 2064,
            ).events.first().value,
        )
        assertEquals(
            -1064,
            SplitLaneTransforms.forReplayStored(
                payload = payload,
                sourceLane = HelperLane.SPLIT_BOTTOM,
                targetLane = HelperLane.SPLIT_TOP,
                laneOffsetTouch = 2064,
            ).events.first().value,
        )
        assertEquals(
            1000,
            SplitLaneTransforms.forReplayStored(
                payload = payload,
                sourceLane = HelperLane.SPLIT_TOP,
                targetLane = HelperLane.SPLIT_TOP,
                laneOffsetTouch = 2064,
            ).events.first().value,
        )
    }
}
