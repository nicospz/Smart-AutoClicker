/*
 * Copyright (C) 2026
 */
package com.buzbuz.smartautoclicker.core.ui.views.touchprobe

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.buzbuz.smartautoclicker.core.ui.R
import com.buzbuz.smartautoclicker.core.ui.views.gesturerecord.GestureRecorder
import com.buzbuz.smartautoclicker.core.ui.views.gesturerecord.RecordedGesture

/**
 * Full-screen transparent overlay that captures touches and briefly visualizes tap and swipe positions.
 */
class TouchProbeOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val markers = TouchProbeMarkers()
    private val density = resources.displayMetrics.density
    private val outerRadiusPx = OUTER_RADIUS_DP * density
    private val innerRadiusPx = INNER_RADIUS_DP * density

    private val primaryColor = ContextCompat.getColor(context, R.color.overlayViewPrimary)
    private val backgroundColor = primaryColor and 0x00FFFFFF or BACKGROUND_ALPHA shl 24

    private val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = OUTER_STROKE_DP * density
        color = primaryColor
    }
    private val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = primaryColor
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = LINE_STROKE_DP * density
        color = primaryColor
    }
    private val gradientBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var invalidateLoopScheduled = false

    private val frameCallback = Choreographer.FrameCallback {
        invalidateLoopScheduled = false
        val nowMs = System.currentTimeMillis()
        markers.pruneExpired(nowMs)
        invalidate()
        if (markers.hasContent(nowMs)) {
            scheduleInvalidateLoop()
        }
    }

    private val gestureRecorder = GestureRecorder { gesture, isFinished ->
        val nowMs = System.currentTimeMillis()
        markers.onGesture(gesture, isFinished, nowMs)
        invalidate()
        if (markers.hasContent(nowMs)) {
            scheduleInvalidateLoop()
        }
    }

    fun clearMarkers() {
        markers.clear()
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        invalidateLoopScheduled = false
        invalidate()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        event ?: return true
        gestureRecorder.processEvent(event)
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val nowMs = System.currentTimeMillis()
        markers.activeMarkers(nowMs).forEach { marker ->
            when (marker) {
                is TouchProbeMarker.Click -> drawClick(canvas, marker.position, marker.alphaFor(nowMs))
                is TouchProbeMarker.Swipe -> drawSwipe(
                    canvas = canvas,
                    from = marker.from,
                    to = marker.to,
                    alpha = marker.alphaFor(nowMs),
                )
            }
        }

        markers.inProgressGesture?.let { gesture ->
            when (gesture) {
                is RecordedGesture.Click -> drawClick(canvas, gesture.position, 255)
                is RecordedGesture.Swipe -> drawSwipe(canvas, gesture.from, gesture.to, 255)
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        invalidateLoopScheduled = false
    }

    private fun scheduleInvalidateLoop() {
        if (invalidateLoopScheduled) return
        invalidateLoopScheduled = true
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private fun drawClick(canvas: Canvas, position: PointF, alpha: Int) {
        if (alpha <= 0) return

        gradientBackgroundPaint.shader = RadialGradient(
            position.x,
            position.y,
            outerRadiusPx * 1.75f,
            intArrayOf(withAlpha(backgroundColor, alpha), withAlpha(backgroundColor, 0)),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )

        outerPaint.alpha = alpha
        innerPaint.alpha = alpha
        gradientBackgroundPaint.alpha = alpha

        canvas.drawCircle(position.x, position.y, outerRadiusPx * 2f, gradientBackgroundPaint)
        canvas.drawCircle(position.x, position.y, outerRadiusPx, outerPaint)
        canvas.drawCircle(position.x, position.y, innerRadiusPx, innerPaint)
    }

    private fun drawSwipe(canvas: Canvas, from: PointF, to: PointF, alpha: Int) {
        if (alpha <= 0) return

        drawClick(canvas, from, alpha)
        drawClick(canvas, to, alpha)

        linePaint.alpha = alpha
        canvas.drawLine(from.x, from.y, to.x, to.y, linePaint)
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        color and 0x00FFFFFF or (alpha shl 24)
}

private const val OUTER_RADIUS_DP = 24f
private const val INNER_RADIUS_DP = 8f
private const val OUTER_STROKE_DP = 3f
private const val LINE_STROKE_DP = 4f
private const val BACKGROUND_ALPHA = 0x40
