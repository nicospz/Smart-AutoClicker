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
package com.buzbuz.smartautoclicker.core.dumb.engine

import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log

import com.buzbuz.smartautoclicker.core.base.workarounds.UnblockGestureScheduler
import com.buzbuz.smartautoclicker.core.base.workarounds.buildUnblockGesture
import com.buzbuz.smartautoclicker.core.common.actions.AndroidActionExecutor
import com.buzbuz.smartautoclicker.core.common.actions.ThrowletCatchControllers
import com.buzbuz.smartautoclicker.core.common.actions.ThrowletCatchMode
import com.buzbuz.smartautoclicker.core.common.actions.ThrowletCatchSession
import com.buzbuz.smartautoclicker.core.common.actions.precision.PrecisionGestureExecutor
import com.buzbuz.smartautoclicker.core.common.actions.precision.PrecisionTextExecutor
import com.buzbuz.smartautoclicker.core.common.actions.gesture.buildSingleStroke
import com.buzbuz.smartautoclicker.core.common.actions.gesture.line
import com.buzbuz.smartautoclicker.core.common.actions.gesture.moveTo
import com.buzbuz.smartautoclicker.core.common.actions.utils.getPauseDurationMs
import com.buzbuz.smartautoclicker.core.dumb.domain.model.DumbAction
import com.buzbuz.smartautoclicker.core.dumb.domain.model.Repeatable
import com.buzbuz.smartautoclicker.core.tasker.TaskerClient
import com.buzbuz.smartautoclicker.core.tasker.TaskerRunRequest
import com.buzbuz.smartautoclicker.core.tasker.toTaskerVariables

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class DumbActionExecutor @Inject constructor(
    private val androidExecutor: AndroidActionExecutor,
    private val precisionGestureExecutor: PrecisionGestureExecutor,
    private val precisionTextExecutor: PrecisionTextExecutor,
    private val taskerClient: TaskerClient,
) {

    private val random: Random = Random(System.currentTimeMillis())
    private var randomize: Boolean = false

    private var unblockWorkaroundEnabled: Boolean = false

    private val unblockGestureScheduler: UnblockGestureScheduler? =
        if (unblockWorkaroundEnabled) UnblockGestureScheduler()
        else null

    fun setUnblockWorkaround(isEnabled: Boolean) {
        unblockWorkaroundEnabled = isEnabled
    }

    suspend fun onScenarioLoopFinished() {
        if (unblockGestureScheduler?.shouldTrigger() == true) {
            withContext(Dispatchers.Main) {
                Log.i(TAG, "Injecting unblock gesture")
                androidExecutor.dispatchGesture(
                    GestureDescription.Builder().buildUnblockGesture()
                )
            }
        }
    }

    suspend fun executeDumbAction(action: DumbAction, randomize: Boolean) {
        this.randomize = randomize
        when (action) {
            is DumbAction.DumbClick -> executeDumbClick(action)
            is DumbAction.DumbSwipe -> executeDumbSwipe(action)
            is DumbAction.DumbPause -> executeDumbPause(action)
            is DumbAction.DumbPrecisionGesture -> executeDumbPrecisionGesture(action)
            is DumbAction.DumbPrecisionText -> executeDumbPrecisionText(action)
            is DumbAction.DumbTaskerTask -> executeDumbTaskerTask(action)
            is DumbAction.DumbManualThrowletCatch -> executeDumbManualThrowletCatch(action)
        }
    }

    private suspend fun executeDumbClick(dumbClick: DumbAction.DumbClick) {
        val clickGesture = GestureDescription.Builder().buildSingleStroke(
            path = Path().apply { moveTo(dumbClick.position, random) },
            durationMs = dumbClick.pressDurationMs,
            random = random,
        )

        executeRepeatableGesture(clickGesture, dumbClick)
    }

    private suspend fun executeDumbSwipe(dumbSwipe: DumbAction.DumbSwipe) {
        val swipeGesture = GestureDescription.Builder().buildSingleStroke(
            path = Path().apply {
                line(
                    from = dumbSwipe.fromPosition,
                    to = dumbSwipe.toPosition,
                    random = random,
                )
            },
            durationMs = dumbSwipe.swipeDurationMs,
            random = random,
        )

        executeRepeatableGesture(swipeGesture, dumbSwipe)
    }

    private suspend fun executeDumbPause(dumbPause: DumbAction.DumbPause) {
        delay(dumbPause.pauseDurationMs.getPauseDurationMs(random))
    }

    private suspend fun executeRepeatableGesture(gesture: GestureDescription, repeatable: Repeatable) {
        repeatable.repeat {
            withContext(Dispatchers.Main) {
                androidExecutor.dispatchGesture(gesture)
            }
        }
    }

    private suspend fun executeDumbPrecisionGesture(precisionGesture: DumbAction.DumbPrecisionGesture) {
        val payload = precisionGesture.payloadHex ?: return

        precisionGesture.repeat {
            runCatching { precisionGestureExecutor.play(payload) }
                .onFailure { Log.w(TAG, "Precision gesture playback failed", it) }
        }
    }

    private suspend fun executeDumbPrecisionText(precisionText: DumbAction.DumbPrecisionText) {
        precisionText.repeat {
            runCatching { precisionTextExecutor.typeText(precisionText.text, precisionText.mode) }
                .onFailure { Log.w(TAG, "Precision text input failed", it) }
        }
    }

    private suspend fun executeDumbTaskerTask(taskerTask: DumbAction.DumbTaskerTask) {
        val taskName = taskerTask.taskName ?: return
        taskerClient.runTask(
            TaskerRunRequest(
                taskName = taskName,
                variables = taskerTask.variablesJson.toTaskerVariables(),
                waitForCompletion = taskerTask.waitForCompletion,
            )
        )
    }

    private suspend fun executeDumbManualThrowletCatch(action: DumbAction.DumbManualThrowletCatch) {
        val controller = ThrowletCatchControllers.instance
        if (controller == null) {
            Log.w(TAG, "Manual Throwlet Catch ignored: controller unavailable")
            return
        }

        withContext(Dispatchers.Main) {
            controller.execute(
                action.operation,
                ThrowletCatchSession(
                    mode = ThrowletCatchMode.CATCH,
                    lane = action.lane,
                    pokemonNameOverride = action.pokemonNameOverride,
                ),
            )
        }
    }
}

private const val TAG = "DumbActionExecutor"
