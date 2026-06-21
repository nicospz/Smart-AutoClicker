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
package com.buzbuz.smartautoclicker.feature.smart.config.ui.condition.image

import android.graphics.Bitmap
import android.util.Log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import com.buzbuz.smartautoclicker.core.display.recorder.AccessibilityScreenshotProvider
import com.buzbuz.smartautoclicker.core.display.recorder.DisplayRecorder
import com.buzbuz.smartautoclicker.core.domain.model.scenario.ScreenCaptureMode
import com.buzbuz.smartautoclicker.feature.smart.config.domain.EditionRepository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

import javax.inject.Inject

class ImageConditionAreaSelectorViewModel @Inject constructor(
    private val displayRecorder: DisplayRecorder,
    private val accessibilityScreenshotProvider: AccessibilityScreenshotProvider,
    private val editionRepository: EditionRepository,
) : ViewModel()  {

    fun takeScreenshot(onSuccess: (Bitmap) -> Unit, onFailure: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            delay(SCREENSHOT_CAPTURE_DELAY_MS)
            val screenshot = takeScenarioScreenshot()

            withContext(Dispatchers.Main) {
                if (screenshot != null) onSuccess(screenshot)
                else {
                    Log.e(TAG, "Failed to acquire screenshot for area selector")
                    onFailure()
                }
            }
        }
    }

    private suspend fun takeScenarioScreenshot(): Bitmap? =
        when (editionRepository.editionState.getScenario()?.screenCaptureMode) {
            ScreenCaptureMode.ACCESSIBILITY_SCREENSHOT ->
                accessibilityScreenshotProvider.takeScreenshot()

            ScreenCaptureMode.MEDIA_PROJECTION, null -> withTimeoutOrNull(SCREENSHOT_TIMEOUT_MS) {
                repeat(SCREENSHOT_MAX_ATTEMPTS) { attempt ->
                    displayRecorder.acquireLatestBitmap()?.let { bitmap ->
                        Log.i(TAG, "Screenshot acquired on attempt ${attempt + 1}")
                        return@withTimeoutOrNull bitmap
                    }
                    delay(SCREENSHOT_RETRY_DELAY_MS)
                }
                null
            }
        }
}

private const val TAG = "ImageConditionAreaSelector"
private const val SCREENSHOT_CAPTURE_DELAY_MS = 300L
private const val SCREENSHOT_TIMEOUT_MS = 5_000L
private const val SCREENSHOT_RETRY_DELAY_MS = 50L
private const val SCREENSHOT_MAX_ATTEMPTS = 100
