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

import android.graphics.Typeface
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView

import androidx.core.content.ContextCompat

import com.buzbuz.smartautoclicker.core.ui.R as CoreUiR

internal class OverlayHoldActionMenuController(
    private val hubView: View,
    private val menuItemsContainer: ViewGroup,
    private val holdDelayMs: Long = HOLD_DELAY_MS,
    private val onQuickTap: () -> Unit,
    private val onItemSelected: (Int) -> Unit,
    private val onMenuVisibilityChanged: (Boolean) -> Unit,
    private val isInteractionBlocked: () -> Boolean,
    private val isHoldMenuEnabled: () -> Boolean = { true },
    private val menuItemBoundsOnScreenProvider: (View) -> Rect? = ::defaultMenuItemBoundsOnScreen,
) {

    private enum class State {
        Collapsed,
        PendingHold,
        HoldOpen,
        ReleasedOpen,
    }

    private val handler = Handler(Looper.getMainLooper())
    private val touchSlop = ViewConfiguration.get(hubView.context).scaledTouchSlop
    private val touchSlopSquared = touchSlop * touchSlop

    private var state = State.Collapsed
    private var initialRawX = 0f
    private var initialRawY = 0f
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var dragTakeoverRequested = false
    private var hoveredView: View? = null
    private var hoveredDefaultBackground: android.graphics.drawable.Drawable? = null

    private val holdActionHoverBackground by lazy(LazyThreadSafetyMode.NONE) {
        ContextCompat.getDrawable(hubView.context, CoreUiR.drawable.bg_overlay_hold_action_item_hover)
    }
    private val holdActionDefaultLabelColor by lazy(LazyThreadSafetyMode.NONE) {
        ContextCompat.getColor(hubView.context, CoreUiR.color.overlayMenuButtons)
    }
    private val holdActionHoverLabelColor by lazy(LazyThreadSafetyMode.NONE) {
        ContextCompat.getColor(hubView.context, CoreUiR.color.overlayViewPrimary)
    }

    private val holdRunnable = Runnable {
        if (state != State.PendingHold) return@Runnable
        openMenu(fromHold = true)
        state = State.HoldOpen
        updateHoverHighlight(lastRawX, lastRawY)
    }

    fun isMenuOpen(): Boolean = state == State.HoldOpen || state == State.ReleasedOpen

    fun isDragTakeoverRequested(): Boolean = dragTakeoverRequested

    fun clearDragTakeoverRequest() {
        dragTakeoverRequested = false
    }

    fun openMenu() {
        if (state == State.HoldOpen || state == State.ReleasedOpen) return
        onMenuVisibilityChanged(true)
        state = State.ReleasedOpen
    }

    fun closeMenu() {
        if (state == State.Collapsed) return
        cancelPendingHold()
        clearHoverHighlight()
        hubView.parent?.requestDisallowInterceptTouchEvent(false)
        onMenuVisibilityChanged(false)
        state = State.Collapsed
        dragTakeoverRequested = false
    }

    fun onHubTouch(event: MotionEvent): Boolean {
        if (isInteractionBlocked()) return false
        if (!isHoldMenuEnabled()) return onHubTouchTapAndDragOnly(event)

        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (state == State.ReleasedOpen) {
                    closeMenu()
                    return true
                }
                if (state != State.Collapsed) return true

                dragTakeoverRequested = false
                initialRawX = event.rawX
                initialRawY = event.rawY
                lastRawX = event.rawX
                lastRawY = event.rawY
                state = State.PendingHold
                handler.postDelayed(holdRunnable, holdDelayMs)
                true
            }

            MotionEvent.ACTION_MOVE -> {
                lastRawX = event.rawX
                lastRawY = event.rawY
                when (state) {
                    State.PendingHold -> {
                        if (hasMovedBeyondTouchSlop(event)) {
                            cancelPendingHold()
                            state = State.Collapsed
                            dragTakeoverRequested = true
                            false
                        } else {
                            true
                        }
                    }

                    State.HoldOpen -> {
                        updateHoverHighlight(event.rawX, event.rawY)
                        true
                    }

                    else -> state != State.Collapsed
                }
            }

            MotionEvent.ACTION_UP -> {
                when (state) {
                    State.PendingHold -> {
                        cancelPendingHold()
                        state = State.Collapsed
                        onQuickTap()
                        true
                    }

                    State.HoldOpen -> {
                        val selectedView = hoveredView
                        clearHoverHighlight()
                        if (selectedView != null) {
                            onItemSelected(selectedView.id)
                            closeMenu()
                        } else {
                            state = State.ReleasedOpen
                        }
                        true
                    }

                    else -> state != State.Collapsed
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                if (state == State.PendingHold) {
                    cancelPendingHold()
                    state = State.Collapsed
                } else if (state == State.HoldOpen) {
                    clearHoverHighlight()
                    state = State.ReleasedOpen
                }
                true
            }

            else -> state != State.Collapsed
        }
    }

    fun onMenuItemClicked(viewId: Int) {
        if (!isMenuOpen()) return
        onItemSelected(viewId)
        closeMenu()
    }

    fun onOutsideTouch() {
        if (state == State.PendingHold) {
            cancelPendingHold()
            state = State.Collapsed
            return
        }
        if (isMenuOpen()) closeMenu()
    }

    private fun onHubTouchTapAndDragOnly(event: MotionEvent): Boolean {
        if (state == State.HoldOpen || state == State.ReleasedOpen) {
            closeMenu()
        }

        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragTakeoverRequested = false
                initialRawX = event.rawX
                initialRawY = event.rawY
                lastRawX = event.rawX
                lastRawY = event.rawY
                state = State.PendingHold
                true
            }

            MotionEvent.ACTION_MOVE -> {
                lastRawX = event.rawX
                lastRawY = event.rawY
                if (state != State.PendingHold) return false
                if (hasMovedBeyondTouchSlop(event)) {
                    state = State.Collapsed
                    dragTakeoverRequested = true
                    false
                } else {
                    true
                }
            }

            MotionEvent.ACTION_UP -> {
                if (state != State.PendingHold) return false
                state = State.Collapsed
                onQuickTap()
                true
            }

            MotionEvent.ACTION_CANCEL -> {
                if (state == State.PendingHold) {
                    state = State.Collapsed
                }
                true
            }

            else -> false
        }
    }

    private fun openMenu(fromHold: Boolean) {
        onMenuVisibilityChanged(true)
        if (fromHold) {
            hubView.parent?.requestDisallowInterceptTouchEvent(true)
        }
    }

    private fun cancelPendingHold() {
        handler.removeCallbacks(holdRunnable)
    }

    private fun hasMovedBeyondTouchSlop(event: MotionEvent): Boolean {
        val deltaX = event.rawX - initialRawX
        val deltaY = event.rawY - initialRawY
        return (deltaX * deltaX) + (deltaY * deltaY) > touchSlopSquared
    }

    private fun updateHoverHighlight(rawX: Float, rawY: Float) {
        val target = findMenuItemAt(rawX, rawY)
        if (target == hoveredView) return

        clearHoverHighlight()
        hoveredView = target
        target?.let { applyHoverHighlight(it) }
    }

    private fun findMenuItemAt(rawX: Float, rawY: Float): View? {
        for (index in 0 until menuItemsContainer.childCount) {
            val child = menuItemsContainer.getChildAt(index)
            val bounds = menuItemBoundsOnScreenProvider(child) ?: continue
            if (bounds.contains(rawX.toInt(), rawY.toInt())) {
                return child
            }
        }
        return null
    }

    private fun applyHoverHighlight(view: View) {
        hoveredDefaultBackground = view.background
        view.background = holdActionHoverBackground?.constantState?.newDrawable()?.mutate()
        view.findViewById<TextView>(CoreUiR.id.hold_action_item_label)?.apply {
            setTextColor(holdActionHoverLabelColor)
            setTypeface(typeface, Typeface.BOLD)
        }
        view.findViewById<ImageView>(CoreUiR.id.hold_action_item_icon)?.apply {
            imageTintList = ContextCompat.getColorStateList(context, CoreUiR.color.overlayViewPrimary)
        }
    }

    private fun clearHoverHighlight() {
        hoveredView?.let { view ->
            view.background = hoveredDefaultBackground ?: ColorDrawable(android.graphics.Color.TRANSPARENT)
            hoveredDefaultBackground = null
            view.findViewById<TextView>(CoreUiR.id.hold_action_item_label)?.apply {
                setTextColor(holdActionDefaultLabelColor)
                setTypeface(typeface, Typeface.NORMAL)
            }
            view.findViewById<ImageView>(CoreUiR.id.hold_action_item_icon)?.apply {
                imageTintList = ContextCompat.getColorStateList(context, CoreUiR.color.overlayMenuButtons)
            }
        }
        hoveredView = null
    }

    private companion object {
        private const val HOLD_DELAY_MS = 300L

        private fun defaultMenuItemBoundsOnScreen(view: View): Rect? {
            if (view.visibility != View.VISIBLE || !view.isEnabled) return null

            val width = view.width
            val height = view.height
            if (width <= 0 || height <= 0) return null

            val location = IntArray(2)
            view.getLocationOnScreen(location)
            return Rect(location[0], location[1], location[0] + width, location[1] + height)
        }
    }
}
