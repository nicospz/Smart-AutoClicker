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

import android.app.Activity
import android.graphics.Rect
import android.os.Build
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.buzbuz.smartautoclicker.core.common.overlays.R
import com.buzbuz.smartautoclicker.core.common.overlays.testutils.mockSimpleRawEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.Q])
class OverlayHoldActionMenuControllerTests {

    private lateinit var activityController: ActivityController<Activity>
    private lateinit var root: FrameLayout
    private lateinit var hub: ImageButton
    private lateinit var menuItems: FrameLayout
    private lateinit var stopButton: ImageButton
    private lateinit var configButton: ImageButton

    private var quickTapCount = 0
    private var selectedViewId = 0
    private var menuVisible = false
    private var holdMenuEnabled = true

    private lateinit var controller: OverlayHoldActionMenuController

    @Before
    fun setUp() {
        activityController = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = activityController.get()

        root = FrameLayout(activity)
        hub = ImageButton(activity).apply {
            id = R.id.btn_hub
            layoutParams = FrameLayout.LayoutParams(100, 100)
        }
        menuItems = FrameLayout(activity).apply {
            id = R.id.menu_items
            layoutParams = FrameLayout.LayoutParams(100, 220).apply {
                topMargin = 110
            }
            visibility = View.GONE
        }
        stopButton = ImageButton(activity).apply {
            id = STOP_ID
            layoutParams = FrameLayout.LayoutParams(100, 100)
        }
        configButton = ImageButton(activity).apply {
            id = CONFIG_ID
            layoutParams = FrameLayout.LayoutParams(100, 100).apply {
                topMargin = 110
            }
        }

        menuItems.addView(stopButton)
        menuItems.addView(configButton)
        root.addView(hub)
        root.addView(menuItems)
        activity.setContentView(root)
        root.measure(
            View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, 400, 400)

        quickTapCount = 0
        selectedViewId = 0
        menuVisible = false
        holdMenuEnabled = true

        controller = OverlayHoldActionMenuController(
            hubView = hub,
            menuItemsContainer = menuItems,
            holdDelayMs = 50L,
            onQuickTap = { quickTapCount++ },
            onItemSelected = { selectedViewId = it },
            onMenuVisibilityChanged = { menuVisible = it },
            isInteractionBlocked = { false },
            isHoldMenuEnabled = { holdMenuEnabled },
            menuItemBoundsOnScreenProvider = { item ->
                when (item.id) {
                    STOP_ID -> Rect(0, 110, 100, 210)
                    CONFIG_ID -> Rect(0, 220, 100, 320)
                    else -> null
                }
            },
        )
    }

    @Test
    fun quickTap_triggersOnQuickTap() {
        controller.onHubTouch(mockSimpleRawEvent(MotionEvent.ACTION_DOWN, 50f, 50f))
        controller.onHubTouch(mockSimpleRawEvent(MotionEvent.ACTION_UP, 50f, 50f))

        assertEquals(1, quickTapCount)
        assertFalse(menuVisible)
    }

    @Test
    fun holdWithoutMove_opensMenu() {
        controller.onHubTouch(mockSimpleRawEvent(MotionEvent.ACTION_DOWN, 50f, 50f))
        shadowOf(Looper.getMainLooper()).idleFor(60, TimeUnit.MILLISECONDS)

        assertTrue(menuVisible)
        assertTrue(controller.isMenuOpen())
    }

    @Test
    fun moveBeyondSlopBeforeHold_requestsDragTakeover() {
        controller.onHubTouch(mockSimpleRawEvent(MotionEvent.ACTION_DOWN, 50f, 50f))
        val handled = controller.onHubTouch(mockSimpleRawEvent(MotionEvent.ACTION_MOVE, 200f, 200f))

        assertFalse(handled)
        assertTrue(controller.isDragTakeoverRequested())
        assertFalse(menuVisible)
    }

    @Test
    fun holdHoverAndRelease_selectsItem() {
        controller.onHubTouch(mockSimpleRawEvent(MotionEvent.ACTION_DOWN, 50f, 50f))
        shadowOf(Looper.getMainLooper()).idleFor(60, TimeUnit.MILLISECONDS)
        controller.onHubTouch(mockSimpleRawEvent(MotionEvent.ACTION_MOVE, 50f, 160f))
        controller.onHubTouch(mockSimpleRawEvent(MotionEvent.ACTION_UP, 50f, 160f))

        assertEquals(STOP_ID, selectedViewId)
        assertFalse(menuVisible)
    }

    @Test
    fun holdOpenThenMoveWithoutRelease_selectsHoveredItemOnRelease() {
        controller.onHubTouch(mockSimpleRawEvent(MotionEvent.ACTION_DOWN, 50f, 50f))
        shadowOf(Looper.getMainLooper()).idleFor(60, TimeUnit.MILLISECONDS)

        assertTrue(controller.isMenuOpen())
        controller.onHubTouch(mockSimpleRawEvent(MotionEvent.ACTION_MOVE, 50f, 270f))
        controller.onHubTouch(mockSimpleRawEvent(MotionEvent.ACTION_UP, 50f, 270f))

        assertEquals(CONFIG_ID, selectedViewId)
        assertFalse(menuVisible)
    }

    @Test
    fun releasedOpen_itemClick_selectsAndCloses() {
        controller.openMenu()
        assertTrue(menuVisible)

        controller.onMenuItemClicked(CONFIG_ID)

        assertEquals(CONFIG_ID, selectedViewId)
        assertFalse(menuVisible)
    }

    @Test
    fun outsideTouch_closesReleasedOpenMenu() {
        controller.openMenu()
        controller.onOutsideTouch()

        assertFalse(menuVisible)
        assertFalse(controller.isMenuOpen())
    }

    @Test
    fun outsideTouch_closesHoldOpenMenu() {
        controller.onHubTouch(mockSimpleRawEvent(MotionEvent.ACTION_DOWN, 50f, 50f))
        shadowOf(Looper.getMainLooper()).idleFor(60, TimeUnit.MILLISECONDS)

        controller.onOutsideTouch()

        assertFalse(menuVisible)
        assertFalse(controller.isMenuOpen())
    }

    @Test
    fun outsideTouch_cancelsPendingHold() {
        controller.onHubTouch(mockSimpleRawEvent(MotionEvent.ACTION_DOWN, 50f, 50f))

        controller.onOutsideTouch()

        shadowOf(Looper.getMainLooper()).idleFor(60, TimeUnit.MILLISECONDS)
        assertFalse(menuVisible)
        assertFalse(controller.isMenuOpen())
    }

    @Test
    fun holdDisabled_quickTap_triggersOnQuickTap() {
        holdMenuEnabled = false
        controller.onHubTouch(mockSimpleRawEvent(MotionEvent.ACTION_DOWN, 50f, 50f))
        controller.onHubTouch(mockSimpleRawEvent(MotionEvent.ACTION_UP, 50f, 50f))

        assertEquals(1, quickTapCount)
        assertFalse(menuVisible)
    }

    @Test
    fun holdDisabled_moveBeyondSlop_requestsDragTakeover() {
        holdMenuEnabled = false
        controller.onHubTouch(mockSimpleRawEvent(MotionEvent.ACTION_DOWN, 50f, 50f))
        val handled = controller.onHubTouch(mockSimpleRawEvent(MotionEvent.ACTION_MOVE, 200f, 200f))

        assertFalse(handled)
        assertTrue(controller.isDragTakeoverRequested())
        assertFalse(menuVisible)
    }

    @Test
    fun holdDisabled_holdDoesNotOpenMenu() {
        holdMenuEnabled = false
        controller.onHubTouch(mockSimpleRawEvent(MotionEvent.ACTION_DOWN, 50f, 50f))
        shadowOf(Looper.getMainLooper()).idleFor(60, TimeUnit.MILLISECONDS)

        assertFalse(menuVisible)
        assertFalse(controller.isMenuOpen())
    }

    private companion object {
        private const val STOP_ID = 0x1001
        private const val CONFIG_ID = 0x1002
    }
}
