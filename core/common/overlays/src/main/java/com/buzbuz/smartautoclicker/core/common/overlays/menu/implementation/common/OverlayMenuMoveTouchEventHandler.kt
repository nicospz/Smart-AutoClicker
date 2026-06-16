/*
 * Copyright (C) 2023 Kevin Buzeau
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
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager

internal class OverlayMenuMoveTouchEventHandler(
    private val onMenuMoved: (Point) -> Unit,
) {

    /** The initial position of the overlay menu when pressing the move menu item. */
    private var moveInitialViewPosition: Point = Point(0, 0)
    /** The initial position of the touch event that as initiated the move of the overlay menu. */
    private var moveInitialTouchPosition: Point = Point(0, 0)

    private var isMoving: Boolean = false

    fun onTouchEvent(viewToMove: View, touchView: View, event: MotionEvent, canMove: Boolean): Boolean {
        if (!canMove) {
            cancelMove()
            return false
        }

        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                onDownEvent(viewToMove, event)
                isMoving = false
                false
            }

            MotionEvent.ACTION_MOVE -> {
                if (!isMoving && !hasMovedBeyondTouchSlop(touchView, event)) return false

                isMoving = true
                if (isMoving) {
                    onMoveEvent(event)
                    true
                } else false
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                val wasMoving = isMoving
                cancelMove()
                wasMoving
            }

            else -> isMoving
        }
    }

    private fun onDownEvent(viewToMove: View, event: MotionEvent) {
        val layoutParams = (viewToMove.layoutParams as WindowManager.LayoutParams)
        moveInitialViewPosition = Point(layoutParams.x, layoutParams.y)
        moveInitialTouchPosition = Point(event.rawX.toInt(), event.rawY.toInt())
    }

    private fun onMoveEvent(event: MotionEvent) {
        onMenuMoved(
            Point(
                moveInitialViewPosition.x + (event.rawX.toInt() - moveInitialTouchPosition.x),
                moveInitialViewPosition.y + (event.rawY.toInt() - moveInitialTouchPosition.y),
            )
        )
    }

    private fun hasMovedBeyondTouchSlop(touchView: View, event: MotionEvent): Boolean {
        val touchSlop = ViewConfiguration.get(touchView.context).scaledTouchSlop
        val deltaX = event.rawX.toInt() - moveInitialTouchPosition.x
        val deltaY = event.rawY.toInt() - moveInitialTouchPosition.y

        return (deltaX * deltaX) + (deltaY * deltaY) > touchSlop * touchSlop
    }

    private fun cancelMove() {
        isMoving = false
    }
}
