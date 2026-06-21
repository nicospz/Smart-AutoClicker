package com.buzbuz.smartautoclicker.feature.throwlet

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

object OverlayToast {
    private val handler = Handler(Looper.getMainLooper())
    private var hideRunnable: Runnable? = null
    private var view: TextView? = null
    private var params: WindowManager.LayoutParams? = null

    fun show(context: Context, message: String, durationMs: Long = 1_000L) {
        val appContext = context.applicationContext
        val wm = appContext.getSystemService(WindowManager::class.java) ?: return
        handler.post {
            dismiss(wm)
            val density = appContext.resources.displayMetrics.density
            val toastView = TextView(appContext).apply {
                text = message
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                setPadding((16 * density).toInt(), (10 * density).toInt(), (16 * density).toInt(), (10 * density).toInt())
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 10 * density
                    setColor(Color.argb(210, 0, 0, 0))
                }
                alpha = 0f
                animate().alpha(1f).setDuration(120).start()
            }
            val layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
                y = (96 * density).toInt()
            }
            runCatching { wm.addView(toastView, layoutParams) }
                .onFailure { ThrowletLog.w("overlay toast add failed: ${it.message}") }
                .onSuccess {
                    view = toastView
                    params = layoutParams
                    val runnable = Runnable { dismiss(wm) }
                    hideRunnable = runnable
                    handler.postDelayed(runnable, durationMs)
                }
        }
    }

    private fun dismiss(wm: WindowManager) {
        hideRunnable?.let(handler::removeCallbacks)
        hideRunnable = null
        view?.let { runCatching { wm.removeView(it) } }
        view = null
        params = null
    }
}
