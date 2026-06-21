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
package com.buzbuz.smartautoclicker.core.processing.data.processor.state

import com.buzbuz.smartautoclicker.core.base.identifier.Identifier
import com.buzbuz.smartautoclicker.core.domain.model.event.EventGroup
import com.buzbuz.smartautoclicker.core.domain.model.event.GroupEventType

internal interface IGroupsState {
    fun beginImagePhase()
    fun beginTriggerPhase()
    fun isGroupActive(groupId: Identifier?): Boolean
    fun setGroupGateResult(groupId: Long, active: Boolean)
}

/**
 * Tracks per-frame gate evaluation for event groups.
 *
 * Groups with no conditions are always active. Groups with conditions are evaluated once per phase.
 * Ancestor group gates must also be open for nested groups.
 */
internal class GroupsState(
    imageGroups: List<EventGroup>,
    triggerGroups: List<EventGroup>,
) : IGroupsState {

    private val imageGroupsById: Map<Long, EventGroup> =
        imageGroups.associateBy { it.id.databaseId }
    private val triggerGroupsById: Map<Long, EventGroup> =
        triggerGroups.associateBy { it.id.databaseId }

    private var currentPhase: GroupEventType? = null
    private val gateResults: MutableMap<Long, Boolean> = mutableMapOf()

    override fun beginImagePhase() {
        currentPhase = GroupEventType.IMAGE
        gateResults.clear()
    }

    override fun beginTriggerPhase() {
        currentPhase = GroupEventType.TRIGGER
        gateResults.clear()
    }

    override fun isGroupActive(groupId: Identifier?): Boolean {
        if (groupId == null) return true

        var currentId: Long? = groupId.databaseId
        while (currentId != null) {
            val group = imageGroupsById[currentId] ?: triggerGroupsById[currentId] ?: return true
            if (group.conditions.isNotEmpty() && gateResults[currentId] != true) return false
            currentId = group.parentGroupId?.databaseId
        }
        return true
    }

    override fun setGroupGateResult(groupId: Long, active: Boolean) {
        gateResults[groupId] = active
    }

    fun getGroups(phase: GroupEventType): Collection<EventGroup> =
        when (phase) {
            GroupEventType.IMAGE -> imageGroupsById.values
            GroupEventType.TRIGGER -> triggerGroupsById.values
        }

    fun getGroupsForCurrentPhase(): Collection<EventGroup> =
        when (currentPhase) {
            GroupEventType.IMAGE -> imageGroupsById.values
            GroupEventType.TRIGGER -> triggerGroupsById.values
            null -> emptyList()
        }
}
