/*
 * Copyright (C) 2026 Nicolas Espinoza
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

import com.buzbuz.smartautoclicker.core.base.identifier.Identifier
import com.buzbuz.smartautoclicker.core.base.interfaces.Completable
import com.buzbuz.smartautoclicker.core.base.interfaces.Identifiable
import com.buzbuz.smartautoclicker.core.base.interfaces.Prioritizable
import com.buzbuz.smartautoclicker.core.base.interfaces.areComplete
import com.buzbuz.smartautoclicker.core.domain.model.ConditionOperator
import com.buzbuz.smartautoclicker.core.domain.model.condition.Condition

/** Scope of an event group: screen (image) events or trigger events. */
enum class GroupEventType {
    IMAGE,
    TRIGGER,
}

/**
 * A group of events sharing a gate condition.
 *
 * Child events are evaluated only when the group gate passes, independently of their enabled/disabled state.
 */
data class EventGroup(
    override val id: Identifier,
    val scenarioId: Identifier,
    val name: String,
    val eventType: GroupEventType,
    @param:ConditionOperator val conditionOperator: Int,
    override var priority: Int,
    val conditions: List<Condition>,
    val parentGroupId: Identifier? = null,
) : Identifiable, Prioritizable, Completable {

    override fun isComplete(): Boolean =
        name.isNotEmpty() && conditions.isNotEmpty() && conditions.areComplete()
}
