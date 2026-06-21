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
package com.buzbuz.smartautoclicker.core.domain.model.event

import androidx.annotation.CallSuper
import com.buzbuz.smartautoclicker.core.base.identifier.Identifier
import com.buzbuz.smartautoclicker.core.base.interfaces.Completable
import com.buzbuz.smartautoclicker.core.base.interfaces.Identifiable
import com.buzbuz.smartautoclicker.core.base.interfaces.Prioritizable
import com.buzbuz.smartautoclicker.core.base.interfaces.areComplete
import com.buzbuz.smartautoclicker.core.domain.model.event.ImageEventDetectionMode.OFFSET_REPEAT
import com.buzbuz.smartautoclicker.core.domain.model.event.ImageEventDetectionMode.SPLIT_SCREEN
import com.buzbuz.smartautoclicker.core.domain.model.event.ImageEventDetectionMode.STANDARD
import com.buzbuz.smartautoclicker.core.domain.model.event.OffsetRepeatMatchMode
import com.buzbuz.smartautoclicker.core.domain.model.AND
import com.buzbuz.smartautoclicker.core.domain.model.WHOLE_SCREEN
import com.buzbuz.smartautoclicker.core.domain.model.action.Action
import com.buzbuz.smartautoclicker.core.domain.model.condition.ImageCondition
import com.buzbuz.smartautoclicker.core.domain.model.ConditionOperator
import com.buzbuz.smartautoclicker.core.domain.model.action.Click
import com.buzbuz.smartautoclicker.core.domain.model.condition.Condition
import com.buzbuz.smartautoclicker.core.domain.model.condition.TriggerCondition

sealed class Event: Identifiable, Completable {

    /** The unique identifier of the scenario for this event. */
    abstract val scenarioId: Identifier
    /** The name of the event. */
    abstract val name: String
    /** The operator to apply between the conditions in the [conditions] list. */
    @ConditionOperator abstract val conditionOperator: Int
    /** Tells if the event should be evaluated with the scenario, or if it should be enabled by an action. */
    abstract val enabledOnStart: Boolean
    /** Tells if this event should be ignored for scenario runs. */
    abstract val ignored: Boolean
    /** Time in milliseconds this event should be ignored after its actions have been executed. */
    abstract val cooldownMs: Long
    /** The list of action to execute when the [conditions] have been fulfilled. */
    abstract val actions: List<Action>
    /** The list of conditions to fulfill to execute the [actions].  */
    abstract val conditions: List<Condition>
    /** Optional group this event belongs to. */
    abstract val groupId: Identifier?

    @Suppress("UNCHECKED_CAST")
    fun copyBase(
        id: Identifier = this.id,
        scenarioId: Identifier = this.scenarioId,
        name: String = this.name,
        conditionOperator: Int = this.conditionOperator,
        enabledOnStart: Boolean = this.enabledOnStart,
        ignored: Boolean = this.ignored,
        cooldownMs: Long = this.cooldownMs,
        actions: List<Action> = this.actions,
        conditions: List<Condition> = this.conditions,
        groupId: Identifier? = this.groupId,
    ): Event =
        when (this) {
            is ImageEvent -> copy(id = id, scenarioId = scenarioId, name = name, conditionOperator = conditionOperator,
                enabledOnStart = enabledOnStart, ignored = ignored, cooldownMs = cooldownMs, actions = actions,
                conditions = conditions as List<ImageCondition>, groupId = groupId)
            is TriggerEvent -> copy(id = id, scenarioId = scenarioId, name = name, conditionOperator = conditionOperator,
                enabledOnStart = enabledOnStart, ignored = ignored, cooldownMs = cooldownMs, actions = actions,
                conditions = conditions as List<TriggerCondition>, groupId = groupId)
        }

    @CallSuper
    override fun isComplete(): Boolean =
        name.isNotEmpty() && actions.isNotEmpty() && actions.areComplete() &&
                conditions.isNotEmpty() && conditions.areComplete()
}

/**
 * Event of a scenario.
 *
 * @param priority the execution priority of the event in the scenario.
 */
data class ImageEvent(
    override val id: Identifier,
    override val scenarioId: Identifier,
    override val name: String,
    @param:ConditionOperator override val conditionOperator: Int,
    override val actions: List<Action> = emptyList(),
    override val conditions: List<ImageCondition> =  emptyList(),
    override val enabledOnStart: Boolean = true,
    override var priority: Int,
    val keepDetecting: Boolean,
    val detectionMode: ImageEventDetectionMode = STANDARD,
    val offsetRepeatCount: Int = 0,
    val offsetRepeatX: Int = 0,
    val offsetRepeatY: Int = 0,
    val offsetRepeatMatchMode: OffsetRepeatMatchMode = OffsetRepeatMatchMode.FIRST_MATCH,
    override val cooldownMs: Long = 0,
    override val groupId: Identifier? = null,
    override val ignored: Boolean = false,
): Event(), Prioritizable {

    /** Tells if this event is complete and valid for save. */
    override fun isComplete(): Boolean {
        if (!super.isComplete()) return false

        actions.forEach { action ->
            if (conditionOperator == AND && action is Click && !action.isClickOnConditionValid()) return false
        }

        return when (detectionMode) {
            STANDARD -> true
            OFFSET_REPEAT -> isOffsetRepeatComplete()
            SPLIT_SCREEN -> isSplitScreenComplete()
        }
    }

    private fun isSplitScreenComplete(): Boolean =
        conditions.none { it.detectionType == WHOLE_SCREEN }

    /** Maps this event to offset-repeat parameters for split-screen processing. */
    fun toSplitScreenOffsetRepeat(deviceYOffsetPx: Int): ImageEvent =
        copy(
            detectionMode = OFFSET_REPEAT,
            offsetRepeatCount = 1,
            offsetRepeatX = 0,
            offsetRepeatY = deviceYOffsetPx,
            offsetRepeatMatchMode = OffsetRepeatMatchMode.ALL_MATCHES,
        )

    private fun isOffsetRepeatComplete(): Boolean {
        if (offsetRepeatCount < 1) return false
        if (offsetRepeatX == 0 && offsetRepeatY == 0) return false
        if (conditions.any { it.detectionType == WHOLE_SCREEN }) return false
        return true
    }

}


data class TriggerEvent(
    override val id: Identifier,
    override val scenarioId: Identifier,
    override val name: String,
    @param:ConditionOperator override val conditionOperator: Int,
    override val actions: List<Action> = emptyList(),
    override val conditions: List<TriggerCondition> =  emptyList(),
    override val enabledOnStart: Boolean = true,
    override var priority: Int = 0,
    override val cooldownMs: Long = 0,
    override val groupId: Identifier? = null,
    override val ignored: Boolean = false,
) : Event(), Prioritizable {

    override fun isComplete(): Boolean {
        if (!super.isComplete()) return false

        actions.forEach { action ->
            if (!action.isValidForTrigger()) return false
        }

        return true
    }

    private fun Action.isValidForTrigger(): Boolean {
        if (!isComplete()) return false

        return when (this) {
            is Click -> positionType == Click.PositionType.USER_SELECTED
                    && clickOnConditionId == null

            else -> true
        }
    }
}
