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
            3064,
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

    @Test
    fun playbackSpeed_scalesTimingOnly() {
        val payload = RawGesturePayload(
            durationMs = 1_000L,
            events = listOf(
                RawGestureEvent(0L, 0L, 0L, 0x03, 0x35, 100),
                RawGestureEvent(1_000L, 0L, 0L, 0x03, 0x36, 200),
                RawGestureEvent(2_500L, 0L, 0L, 0x03, 0x35, 300),
            ),
        )

        val faster = payload.withPlaybackSpeed(2.0)

        assertEquals(500L, faster.durationMs)
        assertEquals(listOf(0L, 500L, 1_250L), faster.events.map { it.deltaUs })
        assertEquals(listOf(100, 200, 300), faster.events.map { it.value })
    }

    @Test
    fun translated_shiftsCoordinatesOnly() {
        val payload = RawGesturePayload(
            durationMs = 1_000L,
            events = listOf(
                RawGestureEvent(100L, 0L, 0L, 0x03, 0x35, 100),
                RawGestureEvent(200L, 0L, 0L, 0x03, 0x36, 200),
                RawGestureEvent(300L, 0L, 0L, 0x01, 0x00, 1),
            ),
        )

        val translated = payload.translated(dx = 12, dy = -8)

        assertEquals(1_000L, translated.durationMs)
        assertEquals(listOf(100L, 200L, 300L), translated.events.map { it.deltaUs })
        assertEquals(listOf(112, 192, 1), translated.events.map { it.value })
    }

    @Test
    fun powered_stretchesPathFromStartCoordinatesOnly() {
        val payload = RawGesturePayload(
            durationMs = 1_000L,
            events = listOf(
                RawGestureEvent(100L, 0L, 0L, 0x03, 0x35, 100),
                RawGestureEvent(200L, 0L, 0L, 0x03, 0x36, 300),
                RawGestureEvent(300L, 0L, 0L, 0x03, 0x35, 140),
                RawGestureEvent(400L, 0L, 0L, 0x03, 0x36, 260),
                RawGestureEvent(500L, 0L, 0L, 0x01, 0x00, 1),
            ),
        )

        val powered = payload.powered(1.5)

        assertEquals(1_000L, powered.durationMs)
        assertEquals(listOf(100L, 200L, 300L, 400L, 500L), powered.events.map { it.deltaUs })
        assertEquals(listOf(100, 300, 160, 240, 1), powered.events.map { it.value })
    }
}
