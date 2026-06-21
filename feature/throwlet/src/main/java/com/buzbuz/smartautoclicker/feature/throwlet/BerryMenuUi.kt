package com.buzbuz.smartautoclicker.feature.throwlet

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import kotlin.math.max

object BerryMenuUi {
    private const val MENU_WIDTH_DP = 148
    private const val OPTION_HEIGHT_DP = 42

    fun show(
        context: Context,
        anchor: View,
        selected: BerryAction,
        onSelect: (BerryAction) -> Unit,
    ): PopupWindow {
        val menuView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(context, 4), 0, dp(context, 4))
            background = menuBackground(context)
        }
        BerryAction.entries.forEach { berry ->
            menuView.addView(optionRow(context, berry, berry == selected) {
                onSelect(berry)
            })
        }

        val popup = PopupWindow(
            menuView,
            dp(context, MENU_WIDTH_DP),
            android.view.WindowManager.LayoutParams.WRAP_CONTENT,
            true,
        ).apply {
            isOutsideTouchable = true
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = dp(context, 6).toFloat()
        }

        val anchorLocation = IntArray(2)
        anchor.getLocationOnScreen(anchorLocation)
        val metrics = context.resources.displayMetrics
        val gap = dp(context, 6)
        val menuWidth = dp(context, MENU_WIDTH_DP)
        val estimatedHeight = dp(context, OPTION_HEIGHT_DP * BerryAction.entries.size + 8)
        val preferredX = anchorLocation[0] + anchor.width + gap
        val x = if (preferredX + menuWidth <= metrics.widthPixels) {
            preferredX
        } else {
            max(0, anchorLocation[0] - menuWidth - gap)
        }
        val y = anchorLocation[1].coerceIn(0, max(0, metrics.heightPixels - estimatedHeight))
        popup.showAtLocation(anchor.rootView, Gravity.TOP or Gravity.START, x, y)
        return popup
    }

    private fun optionRow(
        context: Context,
        berry: BerryAction,
        selected: Boolean,
        onClick: () -> Unit,
    ): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isClickable = true
        isFocusable = true
        alpha = if (selected) 1f else 0.82f
        setPadding(dp(context, 10), 0, dp(context, 12), 0)
        if (selected) background = selectionBackground(context)
        contentDescription = "Select ${berry.label} berry"
        setOnClickListener { onClick() }
        addView(
            ImageView(context).apply {
                setImageResource(berry.iconRes)
                scaleType = ImageView.ScaleType.FIT_CENTER
            },
            LinearLayout.LayoutParams(dp(context, 28), dp(context, 28)),
        )
        addView(
            TextView(context).apply {
                text = berry.label
                textSize = 13f
                typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(context, 10), 0, 0, 0)
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f),
        )
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(context, OPTION_HEIGHT_DP),
        ).apply {
            setMargins(dp(context, 4), dp(context, 2), dp(context, 4), dp(context, 2))
        }
    }

    private fun menuBackground(context: Context): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(context, 8).toFloat()
        setColor(Color.argb(230, 18, 18, 18))
        setStroke(dp(context, 1), Color.argb(180, 255, 255, 255))
    }

    private fun selectionBackground(context: Context): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(context, 6).toFloat()
        setColor(Color.argb(90, 255, 255, 255))
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
