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

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import com.buzbuz.smartautoclicker.core.display.recorder.AccessibilityScreenshotProvider
import com.buzbuz.smartautoclicker.core.display.recorder.DisplayRecorder
import com.buzbuz.smartautoclicker.core.domain.model.condition.ImageCondition
import com.buzbuz.smartautoclicker.core.domain.model.scenario.ScreenCaptureMode
import com.buzbuz.smartautoclicker.core.ui.monitoring.MonitoredViewsManager
import com.buzbuz.smartautoclicker.core.ui.monitoring.MonitoredViewType
import com.buzbuz.smartautoclicker.feature.smart.config.domain.EditionRepository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

class CaptureViewModel @Inject constructor(
    private val displayRecorder: DisplayRecorder,
    private val accessibilityScreenshotProvider: AccessibilityScreenshotProvider,
    private val editionRepository: EditionRepository,
    private val monitoredViewsManager: MonitoredViewsManager,
) : ViewModel()  {

    fun takeScreenshot(resultCallback: (Bitmap) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            delay(200L)

            val screenshot = takeScenarioScreenshot() ?: return@launch

            withContext(Dispatchers.Main) {
                resultCallback(screenshot)
                monitoredViewsManager.notifyClick(MonitoredViewType.CONDITION_CAPTURE_MENU_BUTTON_CAPTURE)
            }
        }
    }

    private suspend fun takeScenarioScreenshot(): Bitmap? =
        when (editionRepository.editionState.getScenario()?.screenCaptureMode) {
            ScreenCaptureMode.ACCESSIBILITY_SCREENSHOT ->
                accessibilityScreenshotProvider.takeScreenshot()

            ScreenCaptureMode.MEDIA_PROJECTION, null -> {
                var screenshot: Bitmap? = null
                withTimeoutOrNull(SCREENSHOT_TIMEOUT_MS) {
                    displayRecorder.takeScreenshot { screenshot = it }
                }
                screenshot
            }
        }

    /**
     * Create a new condition with the default values from configuration.
     *
     * @param context the Android Context.
     * @param area the area of the condition to create.
     * @param bitmap the image for the condition to create.
     */
    fun createImageCondition(context: Context, area: Rect, bitmap: Bitmap, completed: (ImageCondition) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val condition = editionRepository.editedItemsBuilder.createNewImageCondition(context, area, bitmap)
            withContext(Dispatchers.Main) { completed(condition) }
        }
    }
}

private const val SCREENSHOT_TIMEOUT_MS = 5_000L
