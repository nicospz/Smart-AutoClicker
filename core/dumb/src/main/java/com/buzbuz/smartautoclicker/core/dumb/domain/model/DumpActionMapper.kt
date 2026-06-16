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
package com.buzbuz.smartautoclicker.core.dumb.domain.model

import android.graphics.Point

import com.buzbuz.smartautoclicker.core.base.identifier.DATABASE_ID_INSERTION
import com.buzbuz.smartautoclicker.core.base.identifier.Identifier
import com.buzbuz.smartautoclicker.core.dumb.data.database.DumbActionEntity
import com.buzbuz.smartautoclicker.core.dumb.data.database.DumbActionType
import com.buzbuz.smartautoclicker.core.common.actions.precision.PrecisionTextMode

internal fun DumbActionEntity.toDomain(asDomain: Boolean = false): DumbAction = when (type) {
    DumbActionType.CLICK -> toDomainClick(asDomain)
    DumbActionType.SWIPE -> toDomainSwipe(asDomain)
    DumbActionType.PAUSE -> toDomainPause(asDomain)
    DumbActionType.PRECISION_GESTURE -> toDomainPrecisionGesture(asDomain)
    DumbActionType.PRECISION_TEXT -> toDomainPrecisionText(asDomain)
}
internal fun DumbAction.toEntity(scenarioDbId: Long = DATABASE_ID_INSERTION): DumbActionEntity = when (this) {
    is DumbAction.DumbClick -> toClickEntity(scenarioDbId)
    is DumbAction.DumbSwipe -> toSwipeEntity(scenarioDbId)
    is DumbAction.DumbPause -> toPauseEntity(scenarioDbId)
    is DumbAction.DumbPrecisionGesture -> toPrecisionGestureEntity(scenarioDbId)
    is DumbAction.DumbPrecisionText -> toPrecisionTextEntity(scenarioDbId)
}

private fun DumbActionEntity.toDomainClick(asDomain: Boolean): DumbAction.DumbClick =
    DumbAction.DumbClick(
        id = Identifier(id = id, asTemporary = asDomain),
        scenarioId = Identifier(id = dumbScenarioId, asTemporary = asDomain),
        name = name,
        priority = priority,
        position = Point(x!!, y!!),
        pressDurationMs = pressDuration!!,
        repeatCount = repeatCount!!,
        isRepeatInfinite = isRepeatInfinite!!,
        repeatDelayMs = repeatDelay!!,
    )

private fun DumbActionEntity.toDomainSwipe(asDomain: Boolean): DumbAction.DumbSwipe =
    DumbAction.DumbSwipe(
        id = Identifier(id = id, asTemporary = asDomain),
        scenarioId = Identifier(id = dumbScenarioId, asTemporary = asDomain),
        name = name,
        priority = priority,
        fromPosition = Point(fromX!!, fromY!!),
        toPosition = Point(toX!!, toY!!),
        swipeDurationMs = swipeDuration!!,
        repeatCount = repeatCount!!,
        isRepeatInfinite = isRepeatInfinite!!,
        repeatDelayMs = repeatDelay!!,
    )

private fun DumbActionEntity.toDomainPause(asDomain: Boolean): DumbAction.DumbPause =
    DumbAction.DumbPause(
        id = Identifier(id = id, asTemporary = asDomain),
        scenarioId = Identifier(id = dumbScenarioId, asTemporary = asDomain),
        name = name,
        priority = priority,
        pauseDurationMs = pauseDuration!!,
    )

private fun DumbActionEntity.toDomainPrecisionGesture(asDomain: Boolean): DumbAction.DumbPrecisionGesture =
    DumbAction.DumbPrecisionGesture(
        id = Identifier(id = id, asTemporary = asDomain),
        scenarioId = Identifier(id = dumbScenarioId, asTemporary = asDomain),
        name = name,
        priority = priority,
        repeatCount = repeatCount!!,
        isRepeatInfinite = isRepeatInfinite!!,
        repeatDelayMs = repeatDelay!!,
        payloadHex = precisionGesturePayloadHex,
        eventCount = precisionGestureEventCount,
        durationMs = precisionGestureDurationMs,
        helperMode = precisionGestureHelperMode,
    )

private fun DumbActionEntity.toDomainPrecisionText(asDomain: Boolean): DumbAction.DumbPrecisionText =
    DumbAction.DumbPrecisionText(
        id = Identifier(id = id, asTemporary = asDomain),
        scenarioId = Identifier(id = dumbScenarioId, asTemporary = asDomain),
        name = name,
        priority = priority,
        repeatCount = repeatCount!!,
        isRepeatInfinite = isRepeatInfinite!!,
        repeatDelayMs = repeatDelay!!,
        text = precisionTextValue ?: "",
        mode = precisionTextMode?.let { runCatching { PrecisionTextMode.valueOf(it) }.getOrNull() }
            ?: PrecisionTextMode.KEY_EVENTS,
    )

private fun DumbAction.DumbClick.toClickEntity(scenarioDbId: Long): DumbActionEntity {
    if (!isValid()) throw IllegalStateException("Can't transform to entity, Click is incomplete.")

    return DumbActionEntity(
        id = id.databaseId,
        dumbScenarioId = if (scenarioDbId != DATABASE_ID_INSERTION) scenarioDbId else scenarioId.databaseId,
        name = name,
        priority = priority,
        type = DumbActionType.CLICK,
        repeatCount = repeatCount,
        isRepeatInfinite = isRepeatInfinite,
        repeatDelay = repeatDelayMs,
        pressDuration = pressDurationMs,
        x = position.x,
        y = position.y,
    )
}

private fun DumbAction.DumbSwipe.toSwipeEntity(scenarioDbId: Long): DumbActionEntity {
    if (!isValid()) throw IllegalStateException("Can't transform to entity, Swipe is incomplete.")

    return DumbActionEntity(
        id = id.databaseId,
        dumbScenarioId = if (scenarioDbId != DATABASE_ID_INSERTION) scenarioDbId else scenarioId.databaseId,
        name = name,
        priority = priority,
        type = DumbActionType.SWIPE,
        repeatCount = repeatCount,
        isRepeatInfinite = isRepeatInfinite,
        repeatDelay = repeatDelayMs,
        swipeDuration = swipeDurationMs,
        fromX = fromPosition.x,
        fromY = fromPosition.y,
        toX = toPosition.x,
        toY = toPosition.y,
    )
}

private fun DumbAction.DumbPause.toPauseEntity(scenarioDbId: Long): DumbActionEntity {
    if (!isValid()) throw IllegalStateException("Can't transform to entity, Pause is incomplete.")

    return DumbActionEntity(
        id = id.databaseId,
        dumbScenarioId = if (scenarioDbId != DATABASE_ID_INSERTION) scenarioDbId else scenarioId.databaseId,
        name = name,
        priority = priority,
        type = DumbActionType.PAUSE,
        pauseDuration = pauseDurationMs,
    )
}

private fun DumbAction.DumbPrecisionGesture.toPrecisionGestureEntity(scenarioDbId: Long): DumbActionEntity {
    if (!isValid()) throw IllegalStateException("Can't transform to entity, Precision Gesture is incomplete.")

    return DumbActionEntity(
        id = id.databaseId,
        dumbScenarioId = if (scenarioDbId != DATABASE_ID_INSERTION) scenarioDbId else scenarioId.databaseId,
        name = name,
        priority = priority,
        type = DumbActionType.PRECISION_GESTURE,
        repeatCount = repeatCount,
        isRepeatInfinite = isRepeatInfinite,
        repeatDelay = repeatDelayMs,
        precisionGesturePayloadHex = payloadHex,
        precisionGestureEventCount = eventCount,
        precisionGestureDurationMs = durationMs,
        precisionGestureHelperMode = helperMode,
    )
}

private fun DumbAction.DumbPrecisionText.toPrecisionTextEntity(scenarioDbId: Long): DumbActionEntity {
    if (!isValid()) throw IllegalStateException("Can't transform to entity, Precision Text is incomplete.")

    return DumbActionEntity(
        id = id.databaseId,
        dumbScenarioId = if (scenarioDbId != DATABASE_ID_INSERTION) scenarioDbId else scenarioId.databaseId,
        name = name,
        priority = priority,
        type = DumbActionType.PRECISION_TEXT,
        repeatCount = repeatCount,
        isRepeatInfinite = isRepeatInfinite,
        repeatDelay = repeatDelayMs,
        precisionTextValue = text,
        precisionTextMode = mode.name,
    )
}
