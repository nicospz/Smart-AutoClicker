/*
 * Copyright (C) 2026 Kevin Buzeau
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
package com.buzbuz.smartautoclicker.core.display.recorder

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.Display
import androidx.annotation.RequiresApi

import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Provides screenshots from the running [AccessibilityService].
 *
 * This capture source doesn't own MediaProjection, which allows Smart Auto Clicker to run alongside another app that
 * already uses screen sharing. Android only exposes this API from the active accessibility service, so the service is
 * attached when it connects and detached when it is destroyed.
 */
@Singleton
class AccessibilityScreenshotProvider @Inject constructor() {

    private var service: AccessibilityService? = null
    private var lastScreenshotUptimeMs: Long = 0L

    fun attach(accessibilityService: AccessibilityService) {
        service = accessibilityService
    }

    fun detach(accessibilityService: AccessibilityService) {
        if (service === accessibilityService) service = null
    }

    suspend fun takeScreenshot(): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Log.w(TAG, "takeScreenshot: accessibility screenshot requires Android 11+")
            return null
        }

        val accessibilityService = service ?: run {
            Log.w(TAG, "takeScreenshot: accessibility service is not attached")
            return null
        }

        throttleScreenshotRequests()
        return accessibilityService.takeScreenshotCompat()
    }

    private suspend fun throttleScreenshotRequests() {
        val now = SystemClock.uptimeMillis()
        val nextAllowedTimestamp = lastScreenshotUptimeMs + MIN_SCREENSHOT_INTERVAL_MS
        if (now < nextAllowedTimestamp) delay(nextAllowedTimestamp - now)

        lastScreenshotUptimeMs = SystemClock.uptimeMillis()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun AccessibilityService.takeScreenshotCompat(): Bitmap? =
        suspendCancellableCoroutine { continuation ->
            try {
                takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    mainExecutor,
                    object : AccessibilityService.TakeScreenshotCallback {
                        override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                            val hardwareBuffer = screenshot.hardwareBuffer
                            val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, screenshot.colorSpace)
                                ?.copy(Bitmap.Config.ARGB_8888, false)
                            hardwareBuffer.close()

                            if (continuation.isActive) continuation.resume(bitmap)
                        }

                        override fun onFailure(errorCode: Int) {
                            Log.w(TAG, "takeScreenshot failed: errorCode=$errorCode")
                            if (continuation.isActive) continuation.resume(null)
                        }
                    },
                )
            } catch (securityException: SecurityException) {
                Log.e(TAG, "takeScreenshot: service is missing screenshot capability", securityException)
                if (continuation.isActive) continuation.resume(null)
            }
        }
}

private const val TAG = "A11yScreenshotProvider"
private const val MIN_SCREENSHOT_INTERVAL_MS = 100L
