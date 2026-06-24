/*
 * Copyright (C) 2024 Kevin Buzeau
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.buzbuz.smartautoclicker.core.common.overlays.menu.implementation.common

import android.graphics.Point
import android.graphics.Rect
import android.view.MotionEvent
import android.util.Size
import android.view.Gravity
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewGroup
import android.view.WindowManager
import com.buzbuz.smartautoclicker.core.base.extensions.safeAddView
import com.buzbuz.smartautoclicker.core.base.extensions.safeUpdateViewLayout
import com.buzbuz.smartautoclicker.core.common.overlays.R

internal class OverlayHoldActionPanelWindow(
    private val windowManager: WindowManager,
    private val panelLayout: ViewGroup,
    private val panelLayoutParams: WindowManager.LayoutParams,
    private val displaySizeProvider: () -> Point,
    private val anchorPositionProvider: () -> Point,
    private val anchorSizeProvider: () -> Size,
    private val panelGapPx: Int,
    private val onDismissRequested: () -> Unit,
) {

    private val panelContainer: View? =
        panelLayout.findViewById(R.id.hold_action_panel_container)
    private val menuItemsContainer: ViewGroup? =
        panelLayout.findViewById(R.id.menu_items)

    private var isAttached: Boolean = false

    init {
        panelLayout.findViewById<View>(R.id.hold_action_dismiss_scrim)?.setOnClickListener {
            onDismissRequested()
        }
        panelLayout.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                onDismissRequested()
                true
            } else {
                false
            }
        }
    }

    val isVisible: Boolean
        get() = isAttached && panelLayout.visibility == View.VISIBLE

    fun show() {
        panelLayoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT
        panelLayoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT
        panelLayout.visibility = View.VISIBLE

        updatePositionParams()
        if (!isAttached) {
            panelLayoutParams.gravity = Gravity.TOP or Gravity.START
            if (!windowManager.safeAddView(panelLayout, panelLayoutParams)) return
            isAttached = true
        } else {
            windowManager.safeUpdateViewLayout(panelLayout, panelLayoutParams)
        }

        updatePosition()
    }

    fun hide() {
        if (!isAttached) return
        panelLayout.visibility = View.GONE
        runCatching { windowManager.removeView(panelLayout) }
        isAttached = false
    }

    fun destroy() {
        if (!isAttached) return
        windowManager.removeView(panelLayout)
        isAttached = false
    }

    fun updatePosition() {
        if (!isAttached) return
        updatePositionParams()
        windowManager.safeUpdateViewLayout(panelLayout, panelLayoutParams)
    }

    fun getItemBoundsOnScreen(itemView: View): Rect? {
        if (!isVisible || itemView.visibility != View.VISIBLE || !itemView.isEnabled) return null
        layoutPanelForMeasurement()

        val width = itemView.width.takeIf { it > 0 } ?: itemView.measuredWidth
        val height = itemView.height.takeIf { it > 0 } ?: itemView.measuredHeight
        if (width <= 0 || height <= 0) return null

        val itemOrigin = itemView.positionRelativeTo(panelLayout)
        val left = panelLayoutParams.x + itemOrigin.x
        val top = panelLayoutParams.y + itemOrigin.y
        return Rect(left, top, left + width, top + height)
    }

    private fun updatePositionParams() {
        val panelSize = layoutPanelForMeasurement()
        val panelWidth = panelSize.width
        val panelHeight = panelSize.height
        if (panelWidth <= 0 || panelHeight <= 0) {
            panelLayout.post { updatePosition() }
            return
        }

        val displaySize = displaySizeProvider()
        val anchorPosition = anchorPositionProvider()
        val anchorSize = anchorSizeProvider()

        val railCenterX = anchorPosition.x + (anchorSize.width / 2)
        val openOnRight = railCenterX < displaySize.x / 2
        val panelX = if (openOnRight) {
            anchorPosition.x + anchorSize.width + panelGapPx
        } else {
            anchorPosition.x - panelWidth - panelGapPx
        }
        val panelY = anchorPosition.y + (anchorSize.height / 2) - getFirstVisibleItemCenterYInPanel(panelHeight)

        panelLayoutParams.x = panelX.coerceIn(0, (displaySize.x - panelWidth).coerceAtLeast(0))
        panelLayoutParams.y = panelY.coerceIn(0, (displaySize.y - panelHeight).coerceAtLeast(0))
    }

    private fun getFirstVisibleItemCenterYInPanel(panelHeight: Int): Int {
        val itemsContainer = menuItemsContainer ?: return panelHeight / 2
        for (index in 0 until itemsContainer.childCount) {
            val child = itemsContainer.getChildAt(index)
            if (child.visibility != View.VISIBLE) continue

            val childHeight = child.height.takeIf { it > 0 } ?: child.measuredHeight
            if (childHeight <= 0) continue

            val childOrigin = child.positionRelativeTo(panelLayout)
            return childOrigin.y + (childHeight / 2)
        }

        return panelHeight / 2
    }

    private fun layoutPanelForMeasurement(): Size {
        val container = panelContainer ?: panelLayout
        panelLayout.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)
        val panelWidth = container.measuredWidth.coerceAtLeast(0)
        val panelHeight = container.measuredHeight.coerceAtLeast(0)

        panelLayout.layout(0, 0, panelLayout.measuredWidth, panelLayout.measuredHeight)
        container.x = 0f
        container.y = 0f

        return Size(panelWidth, panelHeight)
    }

    private fun View.positionRelativeTo(ancestor: View): Point {
        var left = 0
        var top = 0
        var current: View? = this

        while (current != null && current != ancestor) {
            left += current.left
            top += current.top
            current = current.parent as? View
        }

        return Point(left, top)
    }
}
