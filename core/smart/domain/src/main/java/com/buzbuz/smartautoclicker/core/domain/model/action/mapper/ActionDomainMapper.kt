package com.buzbuz.smartautoclicker.core.domain.model.action.mapper

import android.content.ComponentName
import android.graphics.Point
import com.buzbuz.smartautoclicker.core.base.identifier.Identifier
import com.buzbuz.smartautoclicker.core.database.entity.ActionType
import com.buzbuz.smartautoclicker.core.database.entity.ChangeCounterOperationType
import com.buzbuz.smartautoclicker.core.database.entity.ClickPositionType
import com.buzbuz.smartautoclicker.core.database.entity.CompleteActionEntity
import com.buzbuz.smartautoclicker.core.database.entity.EventToggleType
import com.buzbuz.smartautoclicker.core.database.entity.NotificationMessageType
import com.buzbuz.smartautoclicker.core.database.entity.SystemActionType
import com.buzbuz.smartautoclicker.core.database.entity.ThrowletCatchLaneType
import com.buzbuz.smartautoclicker.core.database.entity.ThrowletCatchModeType
import com.buzbuz.smartautoclicker.core.database.entity.ThrowletCatchOperationType
import com.buzbuz.smartautoclicker.core.domain.model.CounterOperationValue
import com.buzbuz.smartautoclicker.core.domain.model.action.Action
import com.buzbuz.smartautoclicker.core.domain.model.action.ChangeCounter
import com.buzbuz.smartautoclicker.core.domain.model.action.Click
import com.buzbuz.smartautoclicker.core.domain.model.action.Intent
import com.buzbuz.smartautoclicker.core.domain.model.action.Notification
import com.buzbuz.smartautoclicker.core.domain.model.action.Pause
import com.buzbuz.smartautoclicker.core.domain.model.action.PrecisionGesture
import com.buzbuz.smartautoclicker.core.domain.model.action.PrecisionText
import com.buzbuz.smartautoclicker.core.domain.model.action.SetText
import com.buzbuz.smartautoclicker.core.domain.model.action.StopScenario
import com.buzbuz.smartautoclicker.core.domain.model.action.Swipe
import com.buzbuz.smartautoclicker.core.domain.model.action.SystemAction
import com.buzbuz.smartautoclicker.core.domain.model.action.TaskerTask
import com.buzbuz.smartautoclicker.core.domain.model.action.ThrowletCatch
import com.buzbuz.smartautoclicker.core.domain.model.action.ToggleEvent
import com.buzbuz.smartautoclicker.core.common.actions.precision.PrecisionTextMode
import com.buzbuz.smartautoclicker.core.domain.model.action.intent.toDomainIntentExtra
import com.buzbuz.smartautoclicker.core.domain.model.action.toggleevent.toDomain

/** Convert an Action entity into a Domain Action. */
internal fun CompleteActionEntity.toDomain(cleanIds: Boolean = false): Action = when (action.type) {
    ActionType.CLICK -> toDomainClick(cleanIds)
    ActionType.SWIPE -> toDomainSwipe(cleanIds)
    ActionType.PAUSE -> toDomainPause(cleanIds)
    ActionType.INTENT -> toDomainIntent(cleanIds)
    ActionType.TOGGLE_EVENT -> toDomainToggleEvent(cleanIds)
    ActionType.CHANGE_COUNTER -> toDomainChangeCounter(cleanIds)
    ActionType.NOTIFICATION -> toDomainNotification(cleanIds)
    ActionType.SYSTEM -> toDomainSystem(cleanIds)
    ActionType.TEXT -> toDomainSetText(cleanIds)
    ActionType.STOP_SCENARIO -> toDomainStopScenario(cleanIds)
    ActionType.PRECISION_GESTURE -> toDomainPrecisionGesture(cleanIds)
    ActionType.PRECISION_TEXT -> toDomainPrecisionText(cleanIds)
    ActionType.THROWLET_CATCH -> toDomainThrowletCatch(cleanIds)
    ActionType.TASKER_TASK -> toDomainTaskerTask(cleanIds)
}

private fun CompleteActionEntity.toDomainClick(cleanIds: Boolean = false) = Click(
    id = Identifier(id = action.id, asTemporary = cleanIds),
    eventId = Identifier(id = action.eventId, asTemporary = cleanIds),
    name = action.name,
    priority = action.priority,
    pressDuration = action.pressDuration!!,
    positionType = action.clickPositionType!!.toDomain(),
    position = getPositionIfValid(action.x, action.y),
    clickOnConditionId = action.clickOnConditionId?.let { Identifier(id = it, asTemporary = cleanIds) },
    clickOffset =
        if (action.clickOffsetX != null && action.clickOffsetY != null) Point(action.clickOffsetX!!, action.clickOffsetY!!)
        else null,
    waitAfterClickMs = action.clickWaitAfterMs,
)

private fun CompleteActionEntity.toDomainSwipe(cleanIds: Boolean = false) = Swipe(
    id = Identifier(id = action.id, asTemporary = cleanIds),
    eventId = Identifier(id = action.eventId, asTemporary = cleanIds),
    name = action.name,
    priority = action.priority,
    swipeDuration = action.swipeDuration!!,
    from = getPositionIfValid(action.fromX, action.fromY),
    to = getPositionIfValid(action.toX, action.toY),
)

private fun CompleteActionEntity.toDomainPause(cleanIds: Boolean = false) = Pause(
    id = Identifier(id = action.id, asTemporary = cleanIds),
    eventId = Identifier(id = action.eventId, asTemporary = cleanIds),
    name = action.name,
    priority = action.priority,
    pauseDuration = action.pauseDuration!!,
)

private fun CompleteActionEntity.toDomainIntent(cleanIds: Boolean = false) = Intent(
    id = Identifier(id = action.id, asTemporary = cleanIds),
    eventId = Identifier(id = action.eventId, asTemporary = cleanIds),
    name = action.name,
    priority = action.priority,
    isAdvanced = action.isAdvanced,
    isBroadcast = action.isBroadcast ?: false,
    intentAction = action.intentAction,
    componentName = action.componentName.toComponentName(),
    flags = action.flags,
    extras = intentExtras.map { it.toDomainIntentExtra(cleanIds) }.toMutableList(),
)

private fun CompleteActionEntity.toDomainToggleEvent(cleanIds: Boolean = false) = ToggleEvent(
    id = Identifier(id = action.id, asTemporary = cleanIds),
    eventId = Identifier(id = action.eventId, asTemporary = cleanIds),
    name = action.name,
    priority = action.priority,
    toggleAll = action.toggleAll == true,
    toggleAllType = action.toggleAllType?.toDomain(),
    eventToggles = eventsToggle.map { it.toDomain(cleanIds) }.toMutableList(),
)

private fun CompleteActionEntity.toDomainChangeCounter(cleanIds: Boolean = false) = ChangeCounter(
    id = Identifier(id = action.id, asTemporary = cleanIds),
    eventId = Identifier(id = action.eventId, asTemporary = cleanIds),
    name = action.name,
    priority = action.priority,
    counterName = action.counterName!!,
    operation = action.counterOperation!!.toDomain(),
    operationValue = CounterOperationValue.getCounterOperationValue(
        type = action.counterOperationValueType,
        numberValue = action.counterOperationValue,
        counterName = action.counterOperationCounterName,
    ),
)

private fun CompleteActionEntity.toDomainNotification(cleanIds: Boolean = false) = Notification(
    id = Identifier(id = action.id, asTemporary = cleanIds),
    eventId = Identifier(id = action.eventId, asTemporary = cleanIds),
    name = action.name,
    priority = action.priority,
    channelImportance = action.notificationImportance!!,
    messageType = action.notificationMessageType!!.toDomain(),
    messageText = action.notificationMessageText!!,
    messageCounterName = action.notificationMessageCounterName!!,
)

private fun CompleteActionEntity.toDomainSystem(cleanIds: Boolean = false) = SystemAction(
    id = Identifier(id = action.id, asTemporary = cleanIds),
    eventId = Identifier(id = action.eventId, asTemporary = cleanIds),
    name = action.name,
    priority = action.priority,
    type = action.systemActionType?.toDomain()!!,
)

private fun CompleteActionEntity.toDomainSetText(cleanIds: Boolean = false) = SetText(
    id = Identifier(id = action.id, asTemporary = cleanIds),
    eventId = Identifier(id = action.eventId, asTemporary = cleanIds),
    name = action.name,
    priority = action.priority,
    text = action.textValue ?: "",
    validateInput = action.textValidateInput ?: false,
)

private fun CompleteActionEntity.toDomainStopScenario(cleanIds: Boolean = false) = StopScenario(
    id = Identifier(id = action.id, asTemporary = cleanIds),
    eventId = Identifier(id = action.eventId, asTemporary = cleanIds),
    name = action.name,
    priority = action.priority,
)

private fun CompleteActionEntity.toDomainPrecisionGesture(cleanIds: Boolean = false) = PrecisionGesture(
    id = Identifier(id = action.id, asTemporary = cleanIds),
    eventId = Identifier(id = action.eventId, asTemporary = cleanIds),
    name = action.name,
    priority = action.priority,
    payloadHex = action.precisionGesturePayloadHex,
    eventCount = action.precisionGestureEventCount,
    durationMs = action.precisionGestureDurationMs,
    helperMode = action.precisionGestureHelperMode,
)

private fun CompleteActionEntity.toDomainPrecisionText(cleanIds: Boolean = false) = PrecisionText(
    id = Identifier(id = action.id, asTemporary = cleanIds),
    eventId = Identifier(id = action.eventId, asTemporary = cleanIds),
    name = action.name,
    priority = action.priority,
    text = action.precisionTextValue ?: "",
    mode = action.precisionTextMode?.let { runCatching { PrecisionTextMode.valueOf(it) }.getOrNull() }
        ?: PrecisionTextMode.KEY_EVENTS,
)

private fun CompleteActionEntity.toDomainThrowletCatch(cleanIds: Boolean = false) = ThrowletCatch(
    id = Identifier(id = action.id, asTemporary = cleanIds),
    eventId = Identifier(id = action.eventId, asTemporary = cleanIds),
    name = action.name,
    priority = action.priority,
    operation = action.throwletCatchOperation?.toDomain()!!,
    mode = action.throwletCatchMode?.toDomain() ?: ThrowletCatch.Mode.CATCH,
    lane = action.throwletCatchLane?.toDomain() ?: ThrowletCatch.Lane.FULL,
    pokemonNameOverride = action.throwletCatchPokemonNameOverride?.takeIf { it.isNotBlank() },
)

private fun CompleteActionEntity.toDomainTaskerTask(cleanIds: Boolean = false) = TaskerTask(
    id = Identifier(id = action.id, asTemporary = cleanIds),
    eventId = Identifier(id = action.eventId, asTemporary = cleanIds),
    name = action.name,
    priority = action.priority,
    taskName = action.taskerTaskName,
    waitForCompletion = action.taskerWaitForCompletion == true,
    variablesJson = action.taskerVariablesJson,
)

private fun ClickPositionType.toDomain(): Click.PositionType =
    Click.PositionType.valueOf(name)

private fun EventToggleType.toDomain(): ToggleEvent.ToggleType =
    ToggleEvent.ToggleType.valueOf(name)

private fun ChangeCounterOperationType.toDomain(): ChangeCounter.OperationType =
    ChangeCounter.OperationType.valueOf(name)

private fun NotificationMessageType.toDomain(): Notification.MessageType =
    Notification.MessageType.valueOf(name)

private fun SystemActionType.toDomain(): SystemAction.Type =
    SystemAction.Type.valueOf(name)

private fun ThrowletCatchOperationType.toDomain(): ThrowletCatch.Operation =
    ThrowletCatch.Operation.valueOf(name)

private fun ThrowletCatchModeType.toDomain(): ThrowletCatch.Mode =
    ThrowletCatch.Mode.valueOf(name)

private fun ThrowletCatchLaneType.toDomain(): ThrowletCatch.Lane =
    ThrowletCatch.Lane.valueOf(name)

private fun String?.toComponentName(): ComponentName? = this?.let {
    ComponentName.unflattenFromString(it)
}

private fun getPositionIfValid(x: Int?, y: Int?): Point? =
    if (x != null && y != null) Point(x, y) else null
