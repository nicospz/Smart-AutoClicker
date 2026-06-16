/*
 * Copyright (C) 2026
 */
package com.buzbuz.smartautoclicker.core.ui.views.touchprobe

import android.graphics.PointF
import com.buzbuz.smartautoclicker.core.ui.views.gesturerecord.RecordedGesture

internal sealed class TouchProbeMarker {
    abstract val expiresAtMs: Long

    data class Click(
        val position: PointF,
        override val expiresAtMs: Long,
    ) : TouchProbeMarker()

    data class Swipe(
        val from: PointF,
        val to: PointF,
        override val expiresAtMs: Long,
    ) : TouchProbeMarker()
}

internal class TouchProbeMarkers(
    private val markerLifetimeMs: Long = MARKER_LIFETIME_MS,
) {

    private val markers = mutableListOf<TouchProbeMarker>()

    var inProgressGesture: RecordedGesture? = null
        private set

    fun onGesture(gesture: RecordedGesture?, isFinished: Boolean, nowMs: Long) {
        if (gesture == null) {
            inProgressGesture = null
            return
        }

        if (isFinished) {
            inProgressGesture = null
            addMarker(gesture, nowMs)
        } else {
            inProgressGesture = gesture
        }
    }

    fun clear() {
        markers.clear()
        inProgressGesture = null
    }

    fun pruneExpired(nowMs: Long): Boolean {
        val sizeBefore = markers.size
        markers.removeAll { it.expiresAtMs <= nowMs }
        return markers.size != sizeBefore
    }

    fun activeMarkers(nowMs: Long): List<TouchProbeMarker> =
        markers.filter { it.expiresAtMs > nowMs }

    fun hasContent(nowMs: Long): Boolean =
        activeMarkers(nowMs).isNotEmpty() || inProgressGesture != null

    private fun addMarker(gesture: RecordedGesture, nowMs: Long) {
        val expiresAtMs = nowMs + markerLifetimeMs
        when (gesture) {
            is RecordedGesture.Click -> markers.add(
                TouchProbeMarker.Click(
                    position = PointF(gesture.position.x, gesture.position.y),
                    expiresAtMs = expiresAtMs,
                ),
            )
            is RecordedGesture.Swipe -> markers.add(
                TouchProbeMarker.Swipe(
                    from = PointF(gesture.from.x, gesture.from.y),
                    to = PointF(gesture.to.x, gesture.to.y),
                    expiresAtMs = expiresAtMs,
                ),
            )
        }
    }
}

internal fun TouchProbeMarker.alphaFor(nowMs: Long): Int {
    val remainingMs = expiresAtMs - nowMs
    if (remainingMs <= 0) return 0
    if (remainingMs >= MARKER_FADE_WINDOW_MS) return 255
    return ((255 * remainingMs) / MARKER_FADE_WINDOW_MS).toInt().coerceIn(0, 255)
}

internal const val MARKER_LIFETIME_MS = 500L
private const val MARKER_FADE_WINDOW_MS = 100L
