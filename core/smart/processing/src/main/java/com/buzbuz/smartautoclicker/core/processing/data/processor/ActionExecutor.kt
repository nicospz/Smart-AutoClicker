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
package com.buzbuz.smartautoclicker.core.processing.data.processor

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent as AndroidIntent
import android.graphics.Path
import android.graphics.Point
import android.util.Log

import com.buzbuz.smartautoclicker.core.base.workarounds.UnblockGestureScheduler
import com.buzbuz.smartautoclicker.core.base.workarounds.buildUnblockGesture
import com.buzbuz.smartautoclicker.core.common.actions.AndroidActionExecutor
import com.buzbuz.smartautoclicker.core.common.actions.ThrowletCatchControllers
import com.buzbuz.smartautoclicker.core.common.actions.ThrowletCatchLane
import com.buzbuz.smartautoclicker.core.common.actions.ThrowletCatchMode
import com.buzbuz.smartautoclicker.core.common.actions.ThrowletCatchOperation
import com.buzbuz.smartautoclicker.core.common.actions.ThrowletCatchSession
import com.buzbuz.smartautoclicker.core.common.actions.precision.PrecisionGestureExecutor
import com.buzbuz.smartautoclicker.core.common.actions.precision.PrecisionGesturePayload
import com.buzbuz.smartautoclicker.core.common.actions.precision.PrecisionTextExecutor
import com.buzbuz.smartautoclicker.core.common.actions.gesture.buildSingleStroke
import com.buzbuz.smartautoclicker.core.common.actions.gesture.line
import com.buzbuz.smartautoclicker.core.common.actions.gesture.moveTo
import com.buzbuz.smartautoclicker.core.common.actions.model.ActionNotificationRequest
import com.buzbuz.smartautoclicker.core.common.actions.text.findCounterReferences
import com.buzbuz.smartautoclicker.core.common.actions.text.replaceCounterReferences
import com.buzbuz.smartautoclicker.core.common.actions.utils.getPauseDurationMs
import com.buzbuz.smartautoclicker.core.domain.model.CounterOperationValue
import com.buzbuz.smartautoclicker.core.domain.model.OR
import com.buzbuz.smartautoclicker.core.domain.model.action.Intent
import com.buzbuz.smartautoclicker.core.domain.model.action.Click
import com.buzbuz.smartautoclicker.core.domain.model.action.Pause
import com.buzbuz.smartautoclicker.core.domain.model.action.PrecisionGesture
import com.buzbuz.smartautoclicker.core.domain.model.action.PrecisionText
import com.buzbuz.smartautoclicker.core.domain.model.action.Swipe
import com.buzbuz.smartautoclicker.core.domain.model.action.ToggleEvent
import com.buzbuz.smartautoclicker.core.domain.model.action.toggleevent.EventToggle
import com.buzbuz.smartautoclicker.core.domain.model.action.ChangeCounter
import com.buzbuz.smartautoclicker.core.domain.model.action.Notification
import com.buzbuz.smartautoclicker.core.domain.model.action.SetText
import com.buzbuz.smartautoclicker.core.domain.model.action.StopScenario
import com.buzbuz.smartautoclicker.core.domain.model.action.SystemAction
import com.buzbuz.smartautoclicker.core.domain.model.action.TaskerTask
import com.buzbuz.smartautoclicker.core.domain.model.action.ThrowletCatch
import com.buzbuz.smartautoclicker.core.domain.model.action.intent.putDomainExtra
import com.buzbuz.smartautoclicker.core.domain.model.event.Event
import com.buzbuz.smartautoclicker.core.domain.model.event.ImageEvent
import com.buzbuz.smartautoclicker.core.processing.data.processor.state.ProcessingState
import com.buzbuz.smartautoclicker.core.tasker.TaskerRunRequest
import com.buzbuz.smartautoclicker.core.tasker.TaskerClient
import com.buzbuz.smartautoclicker.core.tasker.toTaskerVariables

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * Execute the actions of an event.
 *
 * @param androidExecutor the executor for the actions requiring an interaction with Android.
 * @param processingState the state of the current processing (counters, enabled events...).
 * @param randomize true to randomize the actions values a bit (positions, timers...), false to be precise.
 */
internal class ActionExecutor(
    private val androidExecutor: AndroidActionExecutor,
    private val processingState: ProcessingState,
    randomize: Boolean,
    unblockWorkaroundEnabled: Boolean = false,
    private val onStopRequested: () -> Unit = {},
    private val precisionGestureExecutor: PrecisionGestureExecutor? = null,
    private val precisionTextExecutor: PrecisionTextExecutor? = null,
    private val taskerClient: TaskerClient? = null,
) {

    init { androidExecutor.resetState() }

    private val random: Random? =
        if (randomize) Random(System.currentTimeMillis()) else null

    private val unblockGestureScheduler: UnblockGestureScheduler? =
        if (unblockWorkaroundEnabled) UnblockGestureScheduler()
        else null


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

    suspend fun executeActions(event: Event, results: ConditionsResults? = null) {
        event.actions.forEach { action ->
            val shouldStop = when (action) {
                is Click -> {
                    executeClick(event, action, results)
                    false
                }
                is Swipe -> {
                    executeSwipe(action, results)
                    false
                }
                is Pause -> {
                    executePause(action)
                    false
                }
                is Intent -> {
                    executeIntent(action)
                    false
                }
                is ToggleEvent -> {
                    executeToggleEvent(action)
                    false
                }
                is ChangeCounter -> {
                    executeChangeCounter(action)
                    false
                }
                is Notification -> {
                    executeNotification(event, action)
                    false
                }
                is SystemAction -> {
                    executeSystemAction(action)
                    false
                }
                is SetText -> {
                    executeSetText(action)
                    false
                }
                is StopScenario -> executeStopScenario()
                is PrecisionGesture -> {
                    executePrecisionGesture(action, results)
                    false
                }
                is PrecisionText -> {
                    executePrecisionText(action)
                    false
                }
                is ThrowletCatch -> {
                    executeThrowletCatch(action)
                    false
                }
                is TaskerTask -> {
                    executeTaskerTask(action)
                    false
                }
            }

            if (shouldStop) return
        }
    }

    private suspend fun executeClick(event: Event, click: Click, results: ConditionsResults?) {
        val offsetDx = results?.offsetRepeatDx ?: 0
        val offsetDy = results?.offsetRepeatDy ?: 0

        val clickPath = when (click.positionType) {
            Click.PositionType.USER_SELECTED -> {
                click.position?.let { position ->
                    Path().apply {
                        moveTo(
                            Point(position.x + offsetDx, position.y + offsetDy),
                            random,
                        )
                    }
                }
            }

            Click.PositionType.ON_DETECTED_CONDITION ->
                getOnConditionClickPath(event, click, results)
        } ?: return

        val clickGesture = GestureDescription.Builder().buildSingleStroke(
            path = clickPath,
            durationMs = click.pressDuration!!,
            random = random,
        )

        withContext(Dispatchers.Main) {
            androidExecutor.dispatchGesture(clickGesture)
        }

        if (click.waitAfterClickMs > 0) {
            delay(click.waitAfterClickMs.getPauseDurationMs(random))
        }
    }

    private fun getOnConditionClickPath(event: Event, click: Click, results: ConditionsResults?): Path? {
        if (event !is ImageEvent) return null

        val result = when {
            event.conditionOperator == OR -> results?.getFirstImageDetectedResult()
            click.clickOnConditionId != null -> results?.getImageConditionResult(click.clickOnConditionId!!.databaseId)
            else -> null
        }

        val detectedPosition = result?.position
        if (detectedPosition == null) {
            Log.w(TAG, "Click is invalid, target condition has no detected position")
            return null
        }

        return Path().apply {
            moveTo(
                position = Point(
                    detectedPosition.x + (click.clickOffset?.x ?: 0),
                    detectedPosition.y + (click.clickOffset?.y ?: 0),
                ),
                random = random,
            )
        }
    }

    /**
     * Execute the provided swipe.
     * @param swipe the swipe to be executed.
     */
    private suspend fun executeSwipe(swipe: Swipe, results: ConditionsResults?) {
        val offsetDx = results?.offsetRepeatDx ?: 0
        val offsetDy = results?.offsetRepeatDy ?: 0
        val from = swipe.from ?: return
        val to = swipe.to ?: return

        val swipeGesture = GestureDescription.Builder().buildSingleStroke(
            path = Path().apply {
                line(
                    Point(from.x + offsetDx, from.y + offsetDy),
                    Point(to.x + offsetDx, to.y + offsetDy),
                    random,
                )
            },
            durationMs = swipe.swipeDuration!!,
            random = random,
        )

        withContext(Dispatchers.Main) {
            androidExecutor.dispatchGesture(swipeGesture)
        }
    }

    /**
     * Execute the provided pause.
     * @param pause the pause to be executed.
     */
    private suspend fun executePause(pause: Pause) {
        delay(pause.pauseDuration!!.getPauseDurationMs(random))
    }

    /**
     * Execute the provided intent.
     * @param intent the intent to be executed.
     */
    private suspend fun executeIntent(intent: Intent) {
        val androidIntent = AndroidIntent().apply {
            action = intent.intentAction!!
            flags = intent.flags!!

            intent.componentName?.let {
                component = intent.componentName
            }

            intent.extras?.forEach { putDomainExtra(it) }
        }

        if (intent.isBroadcast) {
            withContext(Dispatchers.Main) {
                androidExecutor.sendBroadcast(androidIntent)
            }
            delay(INTENT_BROADCAST_DELAY)
        } else {
            withContext(Dispatchers.Main) {
                androidExecutor.startActivity(androidIntent)
            }
            delay(INTENT_START_ACTIVITY_DELAY)
        }
    }

    /**
     * Execute the provided toggle event.
     * @param toggleEvent the toggleEvent to be executed.
     */
    private fun executeToggleEvent(toggleEvent: ToggleEvent) {
        if (toggleEvent.toggleAll) {
            when (toggleEvent.toggleAllType) {
                ToggleEvent.ToggleType.ENABLE -> processingState.enableAll()
                ToggleEvent.ToggleType.DISABLE -> processingState.disableAll()
                ToggleEvent.ToggleType.TOGGLE -> processingState.toggleAll()
                null -> Unit
            }

            return
        }

        toggleEvent.eventToggles.forEach { eventToggle ->
            when (eventToggle.toggleType) {
                ToggleEvent.ToggleType.ENABLE -> applyEventToggleEnable(eventToggle)
                ToggleEvent.ToggleType.DISABLE -> applyEventToggleDisable(eventToggle)
                ToggleEvent.ToggleType.TOGGLE -> applyEventToggleInvert(eventToggle)
            }
        }
    }

    private fun applyEventToggleEnable(eventToggle: EventToggle) {
        eventToggle.eventNamePrefix?.let { processingState.enableEventsWithNamePrefix(it) }
            ?: processingState.enableEvent(eventToggle.targetEventId!!.databaseId)
    }

    private fun applyEventToggleDisable(eventToggle: EventToggle) {
        eventToggle.eventNamePrefix?.let { processingState.disableEventsWithNamePrefix(it) }
            ?: processingState.disableEvent(eventToggle.targetEventId!!.databaseId)
    }

    private fun applyEventToggleInvert(eventToggle: EventToggle) {
        eventToggle.eventNamePrefix?.let { processingState.toggleEventsWithNamePrefix(it) }
            ?: processingState.toggleEvent(eventToggle.targetEventId!!.databaseId)
    }

    /**
     * Execute the provided change counter.
     * @param changeCounter the changeCounter action to be executed.
     */
    private fun executeChangeCounter(changeCounter: ChangeCounter) {
        val oldValue = processingState.getCounterValue(changeCounter.counterName) ?: return

        val operandValue = when (val operationValue = changeCounter.operationValue) {
            is CounterOperationValue.Counter -> processingState.getCounterValue(operationValue.value) ?: 0
            is CounterOperationValue.Number -> operationValue.value
        }

        processingState.setCounterValue(
            counterName = changeCounter.counterName,
            value = when (changeCounter.operation) {
                ChangeCounter.OperationType.ADD -> oldValue + operandValue
                ChangeCounter.OperationType.MINUS -> oldValue - operandValue
                ChangeCounter.OperationType.SET -> operandValue
            }
        )
    }

    private fun executeNotification(event: Event, notification: Notification) {
        val message = when (notification.messageType) {
            Notification.MessageType.TEXT -> notification.messageText
            Notification.MessageType.COUNTER_VALUE -> {
                val counterValue = processingState.getCounterValue(notification.messageCounterName) ?: return
                notification.messageCounterName + " = " + counterValue
            }
        }

        androidExecutor.postNotification(
            ActionNotificationRequest(
                actionId = notification.id.databaseId,
                title = notification.name ?: "Klick'r",
                message = message,
                eventId = event.id.databaseId,
                groupName = event.name,
                importance = notification.channelImportance,
            )
        )
    }

    private suspend fun executeSystemAction(action: SystemAction) {
        val globalAction = when (action.type) {
            SystemAction.Type.BACK -> AccessibilityService.GLOBAL_ACTION_BACK
            SystemAction.Type.HOME -> AccessibilityService.GLOBAL_ACTION_HOME
            SystemAction.Type.RECENT_APPS -> AccessibilityService.GLOBAL_ACTION_RECENTS
        }

        withContext(Dispatchers.Main) {
            androidExecutor.performGlobalAction(globalAction)
        }
    }

    private suspend fun executeThrowletCatch(action: ThrowletCatch) {
        val operation = when (action.operation) {
            ThrowletCatch.Operation.TOGGLE -> ThrowletCatchOperation.TOGGLE
            ThrowletCatch.Operation.HIDE -> ThrowletCatchOperation.HIDE
            ThrowletCatch.Operation.SHOW -> ThrowletCatchOperation.SHOW
        }
        val session = ThrowletCatchSession(
            mode = when (action.mode) {
                ThrowletCatch.Mode.CATCH -> ThrowletCatchMode.CATCH
                ThrowletCatch.Mode.BUDDY -> ThrowletCatchMode.BUDDY
            },
            lane = action.resolveThrowletCatchLane(),
            pokemonNameOverride = action.pokemonNameOverride,
        )

        Log.i(
            THROWLET_CATCH_TAG,
            "executeThrowletCatch action=${action.name} operation=$operation mode=${session.mode} lane=${session.lane} override=${session.pokemonNameOverride ?: "<none>"} eventId=${action.eventId}",
        )

        val controller = ThrowletCatchControllers.instance
        if (controller != null) {
            Log.i(THROWLET_CATCH_TAG, "executeThrowletCatch: invoking direct controller")
            withContext(Dispatchers.Main) {
                controller.execute(operation, session)
            }
            delay(INTENT_BROADCAST_DELAY)
            return
        }

        val broadcastAction = when (operation) {
            ThrowletCatchOperation.TOGGLE -> THROWLET_OVERLAY_TOGGLE_ACTION
            ThrowletCatchOperation.HIDE -> THROWLET_OVERLAY_HIDE_ACTION
            ThrowletCatchOperation.SHOW -> THROWLET_OVERLAY_SHOW_ACTION
        }

        Log.w(THROWLET_CATCH_TAG, "executeThrowletCatch: no controller, broadcasting $broadcastAction")
        withContext(Dispatchers.Main) {
            androidExecutor.sendBroadcast(AndroidIntent(broadcastAction))
        }
        delay(INTENT_BROADCAST_DELAY)
    }

    private suspend fun executeTaskerTask(action: TaskerTask) {
        val client = taskerClient
        if (client == null) {
            Log.w(TAG, "TaskerClient not available, skipping task ${action.taskName}")
            return
        }

        val taskName = action.taskName ?: return
        client.runTask(
            TaskerRunRequest(
                taskName = taskName,
                variables = action.variablesJson.toTaskerVariables(),
                waitForCompletion = action.waitForCompletion,
            )
        )
    }

    private fun ThrowletCatch.resolveThrowletCatchLane(): ThrowletCatchLane =
        when (lane) {
            ThrowletCatch.Lane.FULL -> ThrowletCatchLane.FULL
            ThrowletCatch.Lane.TOP -> ThrowletCatchLane.TOP
            ThrowletCatch.Lane.BOTTOM -> ThrowletCatchLane.BOTTOM
        }

    private suspend fun executeSetText(action: SetText) {
        val counters = buildMap {
            action.text.findCounterReferences().forEach { counterName ->
                processingState.getCounterValue(counterName)?.let { counterValue ->
                    put(counterName, counterValue)
                }
            }
        }

        withContext(Dispatchers.Main) {
            androidExecutor.writeTextOnFocusedItem(
                text = action.text.replaceCounterReferences(counters),
                validate = action.validateInput,
            )
        }
    }

    private fun executeStopScenario(): Boolean {
        onStopRequested()
        return true
    }

    private suspend fun executePrecisionGesture(action: PrecisionGesture, results: ConditionsResults?) {
        val payload = action.payloadHex ?: return
        val executor = precisionGestureExecutor ?: return
        val offsetDx = results?.offsetRepeatDx ?: 0
        val offsetDy = when (results?.offsetRepeatDy ?: 0) {
            SPLIT_SCREEN_Y_OFFSET_PX -> SPLIT_SCREEN_PRECISION_GESTURE_Y_OFFSET_EV
            else -> results?.offsetRepeatDy ?: 0
        }
        val translatedPayload = if (offsetDx == 0 && offsetDy == 0) {
            payload
        } else {
            PrecisionGesturePayload.translatePayload(payload, offsetDx, offsetDy)
        }

        runCatching { executor.play(translatedPayload) }
            .onFailure { Log.w(TAG, "Precision gesture playback failed", it) }
    }

    private suspend fun executePrecisionText(action: PrecisionText) {
        val executor = precisionTextExecutor ?: return
        val counters = buildMap {
            action.text.findCounterReferences().forEach { counterName ->
                processingState.getCounterValue(counterName)?.let { counterValue ->
                    put(counterName, counterValue)
                }
            }
        }

        runCatching { executor.typeText(action.text.replaceCounterReferences(counters), action.mode) }
            .onFailure { Log.w(TAG, "Precision text input failed", it) }
    }
}

/** Tag for logs. */
private const val TAG = "ActionExecutor"
private const val THROWLET_CATCH_TAG = "SacThrowletCatch"
/** Waiting delay after a start activity to avoid overflowing the system. */
private const val INTENT_START_ACTIVITY_DELAY = 1000L
/** Waiting delay after a broadcast to avoid overflowing the system. */
private const val INTENT_BROADCAST_DELAY = 100L
private const val THROWLET_OVERLAY_TOGGLE_ACTION = "com.buzbuz.smartautoclicker.action.TOGGLE_THROWLET_OVERLAY"
private const val THROWLET_OVERLAY_HIDE_ACTION = "com.buzbuz.smartautoclicker.action.HIDE_THROWLET_OVERLAY"
private const val THROWLET_OVERLAY_SHOW_ACTION = "com.buzbuz.smartautoclicker.action.SHOW_THROWLET_OVERLAY"
