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

import android.content.Context
import android.graphics.Bitmap
import androidx.annotation.VisibleForTesting

import com.buzbuz.smartautoclicker.core.common.actions.AndroidActionExecutor
import com.buzbuz.smartautoclicker.core.domain.model.event.ImageEventDetectionMode.OFFSET_REPEAT
import com.buzbuz.smartautoclicker.core.domain.model.event.ImageEventDetectionMode.SPLIT_SCREEN
import com.buzbuz.smartautoclicker.core.common.actions.precision.PrecisionGestureExecutor
import com.buzbuz.smartautoclicker.core.common.actions.precision.PrecisionTextExecutor
import com.buzbuz.smartautoclicker.core.detection.ImageDetector
import com.buzbuz.smartautoclicker.core.domain.model.event.EventGroup
import com.buzbuz.smartautoclicker.core.domain.model.event.ImageEvent
import com.buzbuz.smartautoclicker.core.domain.model.event.TriggerEvent
import com.buzbuz.smartautoclicker.core.domain.model.event.RootListEntry
import com.buzbuz.smartautoclicker.core.domain.model.event.childrenOf
import com.buzbuz.smartautoclicker.core.domain.model.event.rootListEntries
import com.buzbuz.smartautoclicker.core.processing.data.processor.state.ProcessingState
import com.buzbuz.smartautoclicker.core.processing.data.scaling.ScalingManager
import com.buzbuz.smartautoclicker.core.processing.domain.EventType
import com.buzbuz.smartautoclicker.core.processing.domain.SmartProcessingListener

import kotlinx.coroutines.yield

/**
 * Process a screen image and tries to detect the list of [ImageEvent] on it.
 *
 * @param imageDetector the detector for images.
 * @param randomize true to randomize the actions values a bit to avoid being taken for a bot.
 * @param imageEvents the list of scenario events to be detected.
 * @param bitmapSupplier provides the conditions bitmaps.
 * @param androidExecutor execute the actions requiring an interaction with Android.
 * @param onStopRequested called when an end condition of the scenario have been reached or all events are disabled.
 * @param progressListener the object to notify for detection progress. Can be null if not required.
 */
internal class ScenarioProcessor(
    private val processingTag: String,
    private val scenarioName: String,
    private val imageDetector: ImageDetector,
    scalingManager: ScalingManager,
    randomize: Boolean,
    imageEvents: List<ImageEvent>,
    triggerEvents: List<TriggerEvent>,
    private val imageGroups: List<EventGroup> = emptyList(),
    private val triggerGroups: List<EventGroup> = emptyList(),
    private val bitmapSupplier: suspend (String, Int, Int) -> Bitmap?,
    androidExecutor: AndroidActionExecutor,
    precisionGestureExecutor: PrecisionGestureExecutor? = null,
    precisionTextExecutor: PrecisionTextExecutor? = null,
    unblockWorkaroundEnabled: Boolean = false,
    private val onStopRequested: () -> Unit,
    private val progressListener: SmartProcessingListener?,
) {

    /** Handle the processing state of the scenario. */
    @VisibleForTesting internal val processingState: ProcessingState = ProcessingState(
        catalogImageEvents = imageEvents,
        catalogTriggerEvents = triggerEvents,
        imageEvents = imageEvents,
        triggerEvents = triggerEvents,
        imageGroups = imageGroups,
        triggerGroups = triggerGroups,
        progressListener = progressListener,
    )
    /** Check conditions and tell if they are fulfilled. */
    private val conditionsVerifier = ConditionsVerifier(
        state = processingState,
        imageDetector = imageDetector,
        scalingManager = scalingManager,
        bitmapSupplier = bitmapSupplier,
        progressListener = progressListener,
    )
    /** Execute the detected event actions. */
    private val actionExecutor = ActionExecutor(
        androidExecutor = androidExecutor,
        processingState = processingState,
        randomize = randomize,
        unblockWorkaroundEnabled = unblockWorkaroundEnabled,
        onStopRequested = onStopRequested,
        precisionGestureExecutor = precisionGestureExecutor,
        precisionTextExecutor = precisionTextExecutor,
    )

    fun onScenarioStart(context: Context) {
        processingState.onProcessingStarted(context)
        ConditionProcessingLog.logScenarioCatalog(
            scenarioName = scenarioName,
            triggerEvents = processingState.getCatalogTriggerEvents(),
            imageEvents = processingState.getCatalogImageEvents(),
        )
    }

    fun onScenarioEnd() {
        processingState.onProcessingStopped()
    }

    /**
     * Find an event with the conditions fulfilled on the current image.
     *
     * @param screenFrame the bitmap containing the current screen display.
     *
     * @return the first Event with all conditions fulfilled, or null if none has been found.
     */
    suspend fun process(screenFrame: Bitmap) {
        // No more events enabled, there is nothing more to do. Stop the detection.
        if (processingState.areAllEventsDisabled()) {
            onStopRequested()
            return
        }

        // Handle all trigger events enabled during previous processing
        if (!processingState.areAllTriggerEventsDisabled()) {
            progressListener?.onEventsListProcessingStarted(EventType.Trigger)
            processingState.beginTriggerPhase()
            processTriggerEventsInListOrder()
            progressListener?.onEventsProcessingCompleted(EventType.Trigger)
        }

        // Reset any values that needs to be reset for each iteration
        // After the triggers to let them handle changes, before the image processing to start capturing values before
        processingState.clearIterationState()

        // Handle the image detection
        if (!processingState.areAllImageEventsDisabled()) {
            progressListener?.onEventsListProcessingStarted(EventType.Image)
            processingState.beginImagePhase()
            processImageEventsInListOrder(screenFrame)
            progressListener?.onEventsProcessingCompleted(EventType.Image)
        }

        // Loop is completed
        actionExecutor.onScenarioLoopFinished()

        return
    }

    private suspend fun evaluateGroupGate(group: EventGroup) {
        if (group.conditions.isEmpty()) {
            processingState.setGroupGateResult(group.id.databaseId, true)
            return
        }

        val results = conditionsVerifier.verifyConditions(
            operator = group.conditionOperator,
            conditions = group.conditions,
            eventContext = "group:${group.name}(id=${group.id.databaseId})",
        )
        processingState.setGroupGateResult(group.id.databaseId, results.fulfilled == true)
    }

    private suspend fun processTriggerEventsInListOrder() {
        val catalogOrder = processingState.getCatalogTriggerEvents()
        val ungrouped = catalogOrder.filter { it.groupId == null }.sortedBy { it.priority }

        for (entry in rootListEntries(ungrouped, triggerGroups, eventPriority = { it.priority })) {
            when (entry) {
                is RootListEntry.UngroupedEvent -> processTriggerEvent(entry.event)
                is RootListEntry.RootGroup -> processTriggerGroupNode(entry.group, catalogOrder)
            }
        }
    }

    private suspend fun processTriggerGroupNode(group: EventGroup, catalogOrder: List<TriggerEvent>) {
        evaluateGroupGate(group)
        if (!processingState.isGroupActive(group.id)) return

        for (triggerEvent in catalogOrder.filter { it.groupId == group.id }.sortedBy { it.priority }) {
            processTriggerEvent(triggerEvent)
        }

        for (childGroup in triggerGroups.childrenOf(group.id)) {
            processTriggerGroupNode(childGroup, catalogOrder)
        }
    }

    private suspend fun processTriggerEvent(triggerEvent: TriggerEvent) {
        if (!processingState.isGroupActive(triggerEvent.groupId)) return
        // Enabled state of the event might have changed during the loop
        if (!processingState.isEventEnabled(triggerEvent.id.databaseId)) return
        if (processingState.isEventOnCooldown(triggerEvent.id.databaseId)) return

        // No conditions ? This should not happen, skip this event
        if (triggerEvent.conditions.isEmpty()) return

        progressListener?.onEventProcessingStarted(triggerEvent)
        val results = conditionsVerifier.verifyConditions(
            operator = triggerEvent.conditionOperator,
            conditions = triggerEvent.conditions,
            eventContext = "trigger:${triggerEvent.name}(id=${triggerEvent.id.databaseId})",
        )

        progressListener?.onEventProcessingCompleted(triggerEvent, results.fulfilled == true, results.getAllTriggerConditionsResults())
        if (results.fulfilled == true) {
            actionExecutor.executeActions(triggerEvent, results)
            processingState.startEventCooldown(triggerEvent)
            progressListener?.onEventActionsExecuted(triggerEvent, results.getAllTriggerConditionsResults())
        }
    }

    private suspend fun processImageEventsInListOrder(screenFrame: Bitmap) {
        imageDetector.setScreenBitmap(screenFrame, processingTag)

        try {
            val catalogOrder = processingState.getCatalogImageEvents()
            val ungrouped = catalogOrder.filter { it.groupId == null }.sortedBy { it.priority }

            for (entry in rootListEntries(ungrouped, imageGroups, eventPriority = { it.priority })) {
                when (entry) {
                    is RootListEntry.UngroupedEvent -> if (!processImageEvent(entry.event)) return
                    is RootListEntry.RootGroup -> if (!processImageGroupNode(entry.group, catalogOrder)) return
                }
            }
        } finally {
            imageDetector.releaseScreenBitmap(screenFrame)
        }
    }

    /** @return false if image event processing should stop for this frame. */
    private suspend fun processImageGroupNode(group: EventGroup, catalogOrder: List<ImageEvent>): Boolean {
        evaluateGroupGate(group)
        if (!processingState.isGroupActive(group.id)) return true

        for (imageEvent in catalogOrder.filter { it.groupId == group.id }.sortedBy { it.priority }) {
            if (!processImageEvent(imageEvent)) return false
        }

        for (childGroup in imageGroups.childrenOf(group.id)) {
            if (!processImageGroupNode(childGroup, catalogOrder)) return false
        }

        return true
    }

    /** @return false if image event processing should stop for this frame. */
    private suspend fun processImageEvent(imageEvent: ImageEvent): Boolean {
        if (!processingState.isGroupActive(imageEvent.groupId)) return true
        if (!processingState.isEventEnabled(imageEvent.id.databaseId)) return true
        if (processingState.isEventOnCooldown(imageEvent.id.databaseId)) return true

        // No conditions ? This should not happen, skip this event
        if (imageEvent.conditions.isEmpty()) return true

        progressListener?.onEventProcessingStarted(imageEvent)
        val eventContext = "image:${imageEvent.name}(id=${imageEvent.id.databaseId})"
        val results = when (imageEvent.detectionMode) {
            OFFSET_REPEAT -> conditionsVerifier.verifyOffsetRepeatImageEvent(imageEvent, eventContext)
            SPLIT_SCREEN -> verifySplitScreenImageEvent(imageEvent, eventContext)
            else -> conditionsVerifier.verifyConditions(
                operator = imageEvent.conditionOperator,
                conditions = imageEvent.conditions,
                eventContext = eventContext,
            )
        }

        progressListener?.onEventProcessingCompleted(imageEvent, results.fulfilled == true, results.getAllImageConditionsResults())
        if (results.fulfilled == true) {
            if (imageEvent.detectionMode == OFFSET_REPEAT || imageEvent.detectionMode == SPLIT_SCREEN) {
                results.offsetRepeatMatches.forEach { match ->
                    actionExecutor.executeActions(
                        imageEvent,
                        results.forOffsetRepeatMatch(match),
                    )
                }
            } else {
                actionExecutor.executeActions(imageEvent, results)
            }
            processingState.startEventCooldown(imageEvent)
            progressListener?.onEventActionsExecuted(imageEvent, results.getAllImageConditionsResults())

            if (!imageEvent.keepDetecting) return false
        }

        yield()
        return true
    }

    private suspend fun verifySplitScreenImageEvent(event: ImageEvent, eventContext: String): ConditionsResults {
        if (SPLIT_SCREEN_Y_OFFSET_PX <= 0) {
            return conditionsVerifier.verifyConditions(
                operator = event.conditionOperator,
                conditions = event.conditions,
                eventContext = eventContext,
            )
        }
        return conditionsVerifier.verifyOffsetRepeatImageEvent(
            event.toSplitScreenOffsetRepeat(SPLIT_SCREEN_Y_OFFSET_PX),
            eventContext = "$eventContext splitScreenYOffsetPx=$SPLIT_SCREEN_Y_OFFSET_PX",
        )
    }
}
