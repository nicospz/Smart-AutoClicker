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
package com.buzbuz.smartautoclicker.core.base.extensions

import android.os.Build
import android.util.Log
import android.view.View
import android.view.WindowManager

import java.lang.reflect.Field


fun WindowManager.safeAddView(view: View?, params: WindowManager.LayoutParams?): Boolean {
    if (view == null || params == null) {
        Log.e(TAG, "safeAddView skipped: view=$view params=$params")
        return false
    }

    return try {
        params.preferSmoothOverlayFrameRate()
        addView(view, params)
        Log.i(TAG, "safeAddView ok: view=${view.javaClass.simpleName} size=${params.width}x${params.height}")
        true
    } catch (ex: WindowManager.BadTokenException) {
        Log.e(TAG, "Can't add view ${view.javaClass.simpleName} to window manager, permission is denied!", ex)
        false
    } catch (ex: Exception) {
        Log.e(TAG, "Can't add view ${view.javaClass.simpleName} to window manager", ex)
        false
    }
}

fun WindowManager.safeUpdateViewLayout(view: View, params: WindowManager.LayoutParams?): Boolean {
    return try {
        params?.preferSmoothOverlayFrameRate()
        updateViewLayout(view, params)
        true
    } catch (ex: IllegalArgumentException) {
        false
    }
}

/**
 * Ask SurfaceFlinger to keep Smart Auto Clicker overlays at an interactive frame rate.
 *
 * Accessibility overlay windows are layered on top of the foreground app. When that app is a 60 FPS game, leaving the
 * overlay without a frame-rate preference can make menu animations and drag updates feel noticeably sluggish on some
 * devices/emulators. Request 60 Hz for the overlay surfaces and, on Android 15+, opt in to the platform touch boost
 * so dragging the floating menu is composited promptly.
 */
fun WindowManager.LayoutParams.preferSmoothOverlayFrameRate() {
    preferredRefreshRate = OVERLAY_REFRESH_RATE_FPS

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        setFrameRateBoostOnTouchEnabled(true)
    }
}

fun WindowManager.LayoutParams.disableMoveAnimations() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        setCanPlayMoveAnimation(false)
    } else {
        val wp = WindowManager.LayoutParams()
        val className = "android.view.WindowManager\$LayoutParams"
        try {
            val layoutParamsClass = Class.forName(className)
            val noAnimFlagField: Field = layoutParamsClass.getField("PRIVATE_FLAG_NO_MOVE_ANIMATION")
            layoutParamsClass.getField("privateFlags").apply {
                setInt(wp, getInt(wp) or noAnimFlagField.getInt(wp))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Can't disable move animations !")
        }
    }
}

private const val TAG = "WindowManagerExt"
private const val OVERLAY_REFRESH_RATE_FPS = 60f
