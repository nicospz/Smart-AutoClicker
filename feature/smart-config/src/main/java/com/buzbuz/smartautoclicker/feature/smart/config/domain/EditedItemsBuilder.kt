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
package com.buzbuz.smartautoclicker.feature.smart.config.domain

import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect

import com.buzbuz.smartautoclicker.core.base.identifier.Identifier
import com.buzbuz.smartautoclicker.core.base.identifier.IdentifierCreator
import com.buzbuz.smartautoclicker.core.bitmaps.BitmapRepository
import com.buzbuz.smartautoclicker.core.bitmaps.CONDITION_FILE_PREFIX
import com.buzbuz.smartautoclicker.core.domain.IRepository
import com.buzbuz.smartautoclicker.core.domain.model.CounterOperationValue
import com.buzbuz.smartautoclicker.core.domain.model.action.Action
import com.buzbuz.smartautoclicker.core.domain.model.action.ChangeCounter
import com.buzbuz.smartautoclicker.core.domain.model.action.Click
import com.buzbuz.smartautoclicker.core.domain.model.action.Click.PositionType
import com.buzbuz.smartautoclicker.core.domain.model.action.Intent
import com.buzbuz.smartautoclicker.core.domain.model.action.Notification
import com.buzbuz.smartautoclicker.core.domain.model.action.Pause
import com.buzbuz.smartautoclicker.core.domain.model.action.PrecisionGesture
import com.buzbuz.smartautoclicker.core.domain.model.action.PrecisionText
import com.buzbuz.smartautoclicker.core.domain.model.action.SetText
import com.buzbuz.smartautoclicker.core.domain.model.action.StopScenario
import com.buzbuz.smartautoclicker.core.domain.model.action.Swipe
import com.buzbuz.smartautoclicker.core.domain.model.action.SystemAction
import com.buzbuz.smartautoclicker.core.domain.model.action.ThrowletCatch
import com.buzbuz.smartautoclicker.core.domain.model.action.ToggleEvent
import com.buzbuz.smartautoclicker.core.domain.model.action.toggleevent.EventToggle
import com.buzbuz.smartautoclicker.core.domain.model.action.intent.IntentExtra
import com.buzbuz.smartautoclicker.core.domain.model.condition.ImageCondition
import com.buzbuz.smartautoclicker.core.domain.model.condition.TriggerCondition
import com.buzbuz.smartautoclicker.core.domain.model.event.EventGroup
import com.buzbuz.smartautoclicker.core.domain.model.event.GroupEventType
import com.buzbuz.smartautoclicker.core.domain.model.event.ImageEvent
import com.buzbuz.smartautoclicker.core.domain.model.event.TriggerEvent
import com.buzbuz.smartautoclicker.feature.smart.config.data.ScenarioEditor

class EditedItemsBuilder internal constructor(
    private val repository: IRepository,
    private val bitmapRepository: BitmapRepository,
    private val editor: ScenarioEditor,
) {

    private val defaultValues = EditionDefaultValues()
    private val eventsIdCreator = IdentifierCreator()
    private val conditionsIdCreator = IdentifierCreator()
    private val actionsIdCreator = IdentifierCreator()
    private val intentExtrasIdCreator = IdentifierCreator()
    private val eventTogglesIdCreator = IdentifierCreator()
    private val endConditionsIdCreator = IdentifierCreator()
    private val eventGroupsIdCreator = IdentifierCreator()

    /**
     * Map of original condition list ids to copy condition ids.
     * Will contain data only when creating an event from another one.
     */
    private val eventCopyConditionIdMap =  mutableMapOf<Identifier, Identifier>()

    /** Keep track of new images created during the edition session. */
    private val _newImageConditionsPaths: MutableList<String> = mutableListOf()
    internal val newImageConditionsPaths: List<String> = _newImageConditionsPaths

    internal fun resetBuilder() {
        eventsIdCreator.resetIdCount()
        conditionsIdCreator.resetIdCount()
        actionsIdCreator.resetIdCount()
        intentExtrasIdCreator.resetIdCount()
        endConditionsIdCreator.resetIdCount()
        eventGroupsIdCreator.resetIdCount()
        eventCopyConditionIdMap.clear()
        _newImageConditionsPaths.clear()
    }

    fun createNewImageEvent(context: Context): ImageEvent =
        ImageEvent(
            id = eventsIdCreator.generateNewIdentifier(),
            scenarioId = getEditedScenarioIdOrThrow(),
            name = defaultValues.eventName(context),
            conditionOperator = defaultValues.eventConditionOperator(),
            priority = getEditedImageRootListCountOrThrow(),
            conditions = mutableListOf(),
            actions = mutableListOf(),
            keepDetecting = false,
        )

    fun createNewTriggerEvent(context: Context): TriggerEvent =
        TriggerEvent(
            id = eventsIdCreator.generateNewIdentifier(),
            scenarioId = getEditedScenarioIdOrThrow(),
            name = defaultValues.eventName(context),
            conditionOperator = defaultValues.eventConditionOperator(),
            priority = getEditedTriggerRootListCountOrThrow(),
            conditions = mutableListOf(),
            actions = mutableListOf(),
        )

    fun createNewImageEventGroup(context: Context): EventGroup =
        createNewEventGroup(context, GroupEventType.IMAGE)

    fun createNewTriggerEventGroup(context: Context): EventGroup =
        createNewEventGroup(context, GroupEventType.TRIGGER)

    private fun createNewEventGroup(context: Context, eventType: GroupEventType): EventGroup {
        val groupId = eventGroupsIdCreator.generateNewIdentifier()
        return EventGroup(
            id = groupId,
            scenarioId = getEditedScenarioIdOrThrow(),
            name = defaultValues.eventGroupName(context),
            eventType = eventType,
            conditionOperator = defaultValues.eventConditionOperator(),
            priority = getEditedRootListCount(eventType),
            conditions = emptyList(),
        )
    }

    private fun getEditedRootListCount(eventType: GroupEventType): Int = when (eventType) {
        GroupEventType.IMAGE -> editor.getEditedImageRootListCount()
        GroupEventType.TRIGGER -> editor.getEditedTriggerRootListCount()
    }

    private fun getEditedImageRootListCountOrThrow(): Int = editor.getEditedImageRootListCount()

    private fun getEditedTriggerRootListCountOrThrow(): Int = editor.getEditedTriggerRootListCount()

    fun createNewImageEventFrom(from: ImageEvent, scenarioId: Identifier = getEditedScenarioIdOrThrow()): ImageEvent {
        val eventId = eventsIdCreator.generateNewIdentifier()

        return from.copy(
            id = eventId,
            scenarioId = scenarioId,
            name = "" + from.name,
            conditions = from.conditions.map { conditionOrig ->
                val conditionCopy = createNewImageConditionFrom(conditionOrig, eventId)
                eventCopyConditionIdMap[conditionOrig.id] = conditionCopy.id
                conditionCopy
            },
            actions = from.actions.map { createNewActionFrom(it, eventId) }
        ).also { eventCopyConditionIdMap.clear() }
    }

    fun createNewTriggerEventFrom(from: TriggerEvent, scenarioId: Identifier = getEditedScenarioIdOrThrow()): TriggerEvent {
        val eventId = eventsIdCreator.generateNewIdentifier()

        return from.copy(
            id = eventId,
            scenarioId = scenarioId,
            name = "" + from.name,
            conditions = from.conditions.map { conditionOrig ->
                val conditionCopy = createNewTriggerConditionFrom(conditionOrig, eventId)
                eventCopyConditionIdMap[conditionOrig.id] = conditionCopy.id
                conditionCopy
            },
            actions = from.actions.map { createNewActionFrom(it, eventId) }
        ).also { eventCopyConditionIdMap.clear() }
    }

    suspend fun createNewImageCondition(context: Context, area: Rect, bitmap: Bitmap): ImageCondition {
        val id = conditionsIdCreator.generateNewIdentifier()
        val newPath = bitmapRepository.saveImageConditionBitmap(
            bitmap = bitmap,
            prefix = CONDITION_FILE_PREFIX,
        )
        _newImageConditionsPaths.add(newPath)

        return ImageCondition(
            id = id,
            eventId = getEditedConditionOwnerIdOrThrow(),
            name = defaultValues.conditionName(context),
            area = area,
            threshold = defaultValues.conditionThreshold(context),
            detectionType = defaultValues.conditionDetectionType(),
            shouldBeDetected = defaultValues.conditionShouldBeDetected(),
            path = newPath,
            priority = 0,
        )
    }

    fun createNewImageConditionFrom(condition: ImageCondition, eventId: Identifier = getEditedConditionOwnerIdOrThrow()): ImageCondition =
        condition.copy(
            id = conditionsIdCreator.generateNewIdentifier(),
            eventId = eventId,
            name = "" + condition.name,
            path = "" + condition.path,
        )

    fun createNewOnBroadcastReceived(context: Context): TriggerCondition.OnBroadcastReceived =
        TriggerCondition.OnBroadcastReceived(
            id = conditionsIdCreator.generateNewIdentifier(),
            eventId = getEditedConditionOwnerIdOrThrow(),
            name = defaultValues.conditionName(context),
            intentAction = "",
        )

    fun createNewOnCounterReached(context: Context): TriggerCondition.OnCounterCountReached =
        TriggerCondition.OnCounterCountReached(
            id = conditionsIdCreator.generateNewIdentifier(),
            eventId = getEditedConditionOwnerIdOrThrow(),
            name = defaultValues.conditionName(context),
            counterName = "",
            comparisonOperation = defaultValues.counterComparisonOperation(),
            counterValue = CounterOperationValue.Number(0)
        )

    fun createNewOnTimerReached(context: Context): TriggerCondition.OnTimerReached =
        TriggerCondition.OnTimerReached(
            id = conditionsIdCreator.generateNewIdentifier(),
            eventId = getEditedConditionOwnerIdOrThrow(),
            name = defaultValues.conditionName(context),
            durationMs = 0,
            restartWhenReached = false,
        )

    fun createNewTriggerConditionFrom(condition: TriggerCondition, eventId: Identifier = getEditedConditionOwnerIdOrThrow()): TriggerCondition =
        when (condition) {
            is TriggerCondition.OnBroadcastReceived -> createNewOnBroadcastReceivedFrom(condition, eventId)
            is TriggerCondition.OnCounterCountReached -> createNewOnCounterReachedFrom(condition, eventId)
            is TriggerCondition.OnTimerReached -> createNewOnTimerReachedFrom(condition, eventId)
        }

    private fun createNewOnBroadcastReceivedFrom(condition: TriggerCondition.OnBroadcastReceived, eventId: Identifier) =
        condition.copy(
            id = conditionsIdCreator.generateNewIdentifier(),
            eventId = eventId,
            name = "" + condition.name,
            intentAction = "" + condition.intentAction,
        )

    private fun createNewOnCounterReachedFrom(condition: TriggerCondition.OnCounterCountReached, eventId: Identifier) =
        condition.copy(
            id = conditionsIdCreator.generateNewIdentifier(),
            eventId = eventId,
            name = "" + condition.name,
            counterName = "" + condition.counterName,
        )

    private fun createNewOnTimerReachedFrom(condition: TriggerCondition.OnTimerReached, eventId: Identifier) =
        condition.copy(
            id = conditionsIdCreator.generateNewIdentifier(),
            eventId = eventId,
            name = "" + condition.name,
        )

    fun createNewClick(context: Context): Click =
        Click(
            id = actionsIdCreator.generateNewIdentifier(),
            eventId = getEditedEventIdOrThrow(),
            name = defaultValues.clickName(context),
            pressDuration = defaultValues.clickPressDuration(context),
            waitAfterClickMs = defaultValues.clickWaitAfterDuration(context),
            positionType = defaultValues.clickPositionType(),
            priority = 0,
        )

    fun createNewSwipe(context: Context): Swipe =
        Swipe(
            id = actionsIdCreator.generateNewIdentifier(),
            eventId = getEditedEventIdOrThrow(),
            name = defaultValues.swipeName(context),
            swipeDuration = defaultValues.swipeDuration(context),
            priority = 0,
        )

    fun createNewPause(context: Context): Pause =
        Pause(
            id = actionsIdCreator.generateNewIdentifier(),
            eventId = getEditedEventIdOrThrow(),
            name = defaultValues.pauseName(context),
            pauseDuration = defaultValues.pauseDuration(context),
            priority = 0,
        )

    fun createNewPrecisionGesture(context: Context): PrecisionGesture =
        PrecisionGesture(
            id = actionsIdCreator.generateNewIdentifier(),
            eventId = getEditedEventIdOrThrow(),
            name = defaultValues.precisionGestureName(context),
            priority = 0,
        )

    fun createNewIntent(context: Context): Intent =
        Intent(
            id = actionsIdCreator.generateNewIdentifier(),
            eventId = getEditedEventIdOrThrow(),
            name = defaultValues.intentName(context),
            isBroadcast = false,
            isAdvanced = defaultValues.intentIsAdvanced(context),
            priority = 0,
        )

    fun createNewIntentExtra() : IntentExtra<Any> =
        IntentExtra(
            id = intentExtrasIdCreator.generateNewIdentifier(),
            actionId = getEditedActionIdOrThrow(),
            key = null,
            value = null,
        )

    fun createNewToggleEvent(context: Context): ToggleEvent =
        ToggleEvent(
            id = actionsIdCreator.generateNewIdentifier(),
            eventId = getEditedEventIdOrThrow(),
            name = defaultValues.toggleEventName(context),
            toggleAll = false,
            toggleAllType = null,
            eventToggles = emptyList(),
            priority = 0,
        )

    fun createNewEventToggle(
        id: Identifier = eventTogglesIdCreator.generateNewIdentifier(),
        targetEventId: Identifier? = null,
        eventNamePrefix: String? = null,
        toggleType: ToggleEvent.ToggleType = defaultValues.eventToggleType(),
    ) = EventToggle(
            id = id,
            actionId = getEditedActionIdOrThrow(),
            targetEventId = targetEventId,
            eventNamePrefix = eventNamePrefix,
            toggleType = toggleType,
        )

    fun createNewChangeCounter(context: Context): ChangeCounter =
        ChangeCounter(
            id = actionsIdCreator.generateNewIdentifier(),
            eventId = getEditedEventIdOrThrow(),
            name = defaultValues.changeCounterName(context),
            counterName = "",
            operation = ChangeCounter.OperationType.ADD,
            operationValue = CounterOperationValue.Number(0),
            priority = 0,
        )

    fun createNewNotification(context: Context): Notification =
        Notification(
            id = actionsIdCreator.generateNewIdentifier(),
            eventId = getEditedEventIdOrThrow(),
            name = defaultValues.notificationName(context),
            channelImportance = NotificationManager.IMPORTANCE_DEFAULT,
            messageType = Notification.MessageType.TEXT,
            messageText = "",
            messageCounterName = "",
            priority = 0,
        )

    fun createNewSystemAction(context: Context): SystemAction =
        SystemAction(
            id = actionsIdCreator.generateNewIdentifier(),
            eventId = getEditedEventIdOrThrow(),
            name = defaultValues.systemActionName(context),
            type = SystemAction.Type.BACK,
            priority = 0,
        )

    fun createNewSetText(context: Context): SetText =
        SetText(
            id = actionsIdCreator.generateNewIdentifier(),
            eventId = getEditedEventIdOrThrow(),
            name = defaultValues.setTextName(context),
            text = "",
            validateInput = false,
            priority = 0,
        )

    fun createNewPrecisionText(context: Context): PrecisionText =
        PrecisionText(
            id = actionsIdCreator.generateNewIdentifier(),
            eventId = getEditedEventIdOrThrow(),
            name = defaultValues.precisionTextName(context),
            priority = 0,
        )

    fun createNewStopScenario(context: Context): StopScenario =
        StopScenario(
            id = actionsIdCreator.generateNewIdentifier(),
            eventId = getEditedEventIdOrThrow(),
            name = defaultValues.stopScenarioName(context),
            priority = 0,
        )

    fun createNewThrowletCatch(context: Context): ThrowletCatch =
        ThrowletCatch(
            id = actionsIdCreator.generateNewIdentifier(),
            eventId = getEditedEventIdOrThrow(),
            name = defaultValues.throwletCatchName(context),
            operation = ThrowletCatch.Operation.TOGGLE,
            priority = 0,
        )

    fun createNewActionFrom(from: Action, eventId: Identifier = getEditedEventIdOrThrow()): Action = when (from) {
        is Click -> createNewClickFrom(from, eventId)
        is Swipe -> createNewSwipeFrom(from, eventId)
        is Pause -> createNewPauseFrom(from, eventId)
        is PrecisionGesture -> createNewPrecisionGestureFrom(from, eventId)
        is PrecisionText -> createNewPrecisionTextFrom(from, eventId)
        is Intent -> createNewIntentFrom(from, eventId)
        is ToggleEvent -> createNewToggleEventFrom(from, eventId)
        is ChangeCounter -> createNewChangeCounterFrom(from, eventId)
        is Notification -> createNewNotificationFrom(from, eventId)
        is SystemAction -> createNewSystemActionFrom(from, eventId)
        is SetText -> createNewSetTextFrom(from, eventId)
        is StopScenario -> createNewStopScenarioFrom(from, eventId)
        is ThrowletCatch -> createNewThrowletCatchFrom(from, eventId)
    }

    private fun createNewClickFrom(from: Click, eventId: Identifier): Click {
        val conditionId =
            if (from.positionType == PositionType.ON_DETECTED_CONDITION && from.clickOnConditionId != null)
                eventCopyConditionIdMap[from.clickOnConditionId]
            else null

        return from.copy(
            id = actionsIdCreator.generateNewIdentifier(),
            eventId = eventId,
            name = "" + from.name,
            clickOnConditionId = conditionId,
        )
    }

    private fun createNewSwipeFrom(from: Swipe, eventId: Identifier): Swipe =
        from.copy(
            id = actionsIdCreator.generateNewIdentifier(),
            eventId = eventId,
            name = "" + from.name,
        )

    private fun createNewPauseFrom(from: Pause, eventId: Identifier): Pause =
        from.copy(
            id = actionsIdCreator.generateNewIdentifier(),
            eventId = eventId,
            name = "" + from.name,
        )

    private fun createNewPrecisionGestureFrom(from: PrecisionGesture, eventId: Identifier): PrecisionGesture =
        from.copy(
            id = actionsIdCreator.generateNewIdentifier(),
            eventId = eventId,
            name = "" + from.name,
            payloadHex = from.payloadHex?.let { "" + it },
        )

    private fun createNewPrecisionTextFrom(from: PrecisionText, eventId: Identifier): PrecisionText =
        from.copy(
            id = actionsIdCreator.generateNewIdentifier(),
            eventId = eventId,
            name = "" + from.name,
            text = "" + from.text,
        )

    private fun createNewIntentFrom(from: Intent, eventId: Identifier): Intent {
        val actionId = actionsIdCreator.generateNewIdentifier()

        return from.copy(
            id = actionId,
            eventId = eventId,
            name = "" + from.name,
            intentAction = "" + from.intentAction,
            componentName = from.componentName?.clone(),
            extras = from.extras?.map { extra -> createNewIntentExtraFrom(extra, eventId) }
        )
    }

    private fun createNewIntentExtraFrom(from: IntentExtra<out Any>, actionId: Identifier = getEditedActionIdOrThrow()): IntentExtra<out Any> =
        from.copy(
            id = intentExtrasIdCreator.generateNewIdentifier(),
            actionId = actionId,
            key = "" + from.key,
        )

    private fun createNewToggleEventFrom(from: ToggleEvent, eventId: Identifier): ToggleEvent {
        val actionId = actionsIdCreator.generateNewIdentifier()

        val eventsToggles = from.eventToggles.mapNotNull { eventToggle ->
            // Check if the current edited scenario contains the event modified by the child event toggle.
            // Filter if not
            if (eventToggle.targetEventId == eventId || isEventIdValidInEditedScenario(eventId)) {
                createEventToggleFrom(eventToggle, actionId)
            } else null
        }

        return from.copy(
            id = actionId,
            eventId = eventId,
            name = "" + from.name,
            eventToggles = eventsToggles,
        )
    }

    private fun createEventToggleFrom(from: EventToggle, actionId: Identifier = getEditedActionIdOrThrow()): EventToggle =
        from.copy(
            id = eventTogglesIdCreator.generateNewIdentifier(),
            actionId = actionId,
        )

    private fun createNewChangeCounterFrom(from: ChangeCounter, eventId: Identifier): ChangeCounter {
        val actionId = actionsIdCreator.generateNewIdentifier()

        return from.copy(
            id = actionId,
            eventId = eventId,
            name = "" + from.name,
            counterName = "" + from.counterName,
        )
    }

    private fun createNewNotificationFrom(from: Notification, eventId: Identifier): Notification {
        val actionId = actionsIdCreator.generateNewIdentifier()

        return from.copy(
            id = actionId,
            eventId = eventId,
            name = "" + from.name,
            messageText = "" + from.messageText,
            messageCounterName = "" + from.messageCounterName,
        )
    }

    private fun createNewSystemActionFrom(from: SystemAction, eventId: Identifier): SystemAction {
        val actionId = actionsIdCreator.generateNewIdentifier()

        return from.copy(
            id = actionId,
            eventId = eventId,
            name = "" + from.name,
            type = from.type,
        )
    }

    private fun createNewSetTextFrom(from: SetText, eventId: Identifier): SetText {
        val actionId = actionsIdCreator.generateNewIdentifier()

        return from.copy(
            id = actionId,
            eventId = eventId,
            name = "" + from.name,
            text = from.text,
            validateInput = from.validateInput,
        )
    }

    private fun createNewStopScenarioFrom(from: StopScenario, eventId: Identifier): StopScenario =
        from.copy(
            id = actionsIdCreator.generateNewIdentifier(),
            eventId = eventId,
            name = "" + from.name,
        )

    private fun createNewThrowletCatchFrom(from: ThrowletCatch, eventId: Identifier): ThrowletCatch =
        from.copy(
            id = actionsIdCreator.generateNewIdentifier(),
            eventId = eventId,
            name = "" + from.name,
            operation = from.operation,
        )

    private fun isEventIdValidInEditedScenario(eventId: Identifier): Boolean =
        editor.getAllEditedEvents().find { eventId == it.id } != null

    private fun getEditedScenarioIdOrThrow(): Identifier =
        editor.editedScenario.value?.id
            ?: throw IllegalStateException("Can't create items without an edited scenario")

    private fun getEditedEventIdOrThrow(): Identifier =
        editor.currentEventEditor.value?.editedItem?.value?.id
            ?: throw IllegalStateException("Can't create items without an edited event")

    private fun getEditedConditionOwnerIdOrThrow(): Identifier =
        editor.currentEventGroupEditor.value?.editedItem?.value?.id
            ?: getEditedEventIdOrThrow()

    private fun getEditedActionIdOrThrow(): Identifier =
        editor.currentEventEditor.value?.actionsEditor?.editedItem?.value?.id
            ?: throw IllegalStateException("Can't create items without an edited action")
}
