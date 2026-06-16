/*
 * Copyright (C) 2026
 */
package com.buzbuz.smartautoclicker.core.ui.views.touchprobe

import android.graphics.PointF
import com.buzbuz.smartautoclicker.core.ui.views.gesturerecord.RecordedGesture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchProbeMarkersTest {

    @Test
    fun finishedClick_addsMarkerThatExpiresAfterLifetime() {
        val markers = TouchProbeMarkers(markerLifetimeMs = MARKER_LIFETIME_MS)
        val click = RecordedGesture.Click(PointF(10f, 20f), durationMs = 50L)

        markers.onGesture(click, isFinished = true, nowMs = 1_000L)

        assertEquals(1, markers.activeMarkers(nowMs = 1_000L).size)
        assertFalse(markers.hasContent(nowMs = 1_000L + MARKER_LIFETIME_MS))
        assertTrue(markers.pruneExpired(nowMs = 1_000L + MARKER_LIFETIME_MS))
        assertTrue(markers.activeMarkers(nowMs = 1_000L + MARKER_LIFETIME_MS).isEmpty())
    }

    @Test
    fun unfinishedGesture_keepsInProgressUntilFinished() {
        val markers = TouchProbeMarkers()
        val click = RecordedGesture.Click(PointF(1f, 2f), durationMs = 1L)

        markers.onGesture(click, isFinished = false, nowMs = 0L)

        assertEquals(click, markers.inProgressGesture)
        assertTrue(markers.hasContent(nowMs = 0L))
        assertTrue(markers.activeMarkers(nowMs = 0L).isEmpty())

        markers.onGesture(click, isFinished = true, nowMs = 10L)

        assertNull(markers.inProgressGesture)
        assertEquals(1, markers.activeMarkers(nowMs = 10L).size)
    }

    @Test
    fun clear_removesMarkersAndInProgressGesture() {
        val markers = TouchProbeMarkers()
        val swipe = RecordedGesture.Swipe(
            from = PointF(0f, 0f),
            to = PointF(100f, 100f),
            durationMs = 120L,
        )

        markers.onGesture(swipe, isFinished = false, nowMs = 0L)
        markers.onGesture(swipe, isFinished = true, nowMs = 5L)
        markers.clear()

        assertNull(markers.inProgressGesture)
        assertTrue(markers.activeMarkers(nowMs = 5L).isEmpty())
        assertFalse(markers.hasContent(nowMs = 5L))
    }

    @Test
    fun alphaFor_fadesDuringLastFadeWindow() {
        val marker = TouchProbeMarker.Click(
            position = PointF(0f, 0f),
            expiresAtMs = 1_000L,
        )

        assertEquals(255, marker.alphaFor(nowMs = 850L))
        assertTrue(marker.alphaFor(nowMs = 950L) in 1..254)
        assertEquals(0, marker.alphaFor(nowMs = 1_000L))
    }
}
