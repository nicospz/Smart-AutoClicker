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
import com.buzbuz.smartautoclicker.core.database.entity.CompleteEventGroupEntity
import com.buzbuz.smartautoclicker.core.database.entity.EventGroupEntity
import com.buzbuz.smartautoclicker.core.database.entity.EventGroupType
import com.buzbuz.smartautoclicker.core.domain.model.condition.toDomainGroupCondition

internal fun EventGroup.toEntity(): EventGroupEntity =
    EventGroupEntity(
        id = id.databaseId,
        scenarioId = scenarioId.databaseId,
        name = name,
        eventType = eventType.toEntity(),
        conditionOperator = conditionOperator,
        priority = priority,
        parentGroupId = parentGroupId?.databaseId,
    )

internal fun CompleteEventGroupEntity.toDomain(cleanIds: Boolean = false): EventGroup =
    EventGroup(
        id = Identifier(id = eventGroup.id, asTemporary = cleanIds),
        scenarioId = Identifier(id = eventGroup.scenarioId, asTemporary = cleanIds),
        name = eventGroup.name,
        eventType = eventGroup.eventType.toDomain(),
        conditionOperator = eventGroup.conditionOperator,
        priority = eventGroup.priority,
        conditions = conditions.map { it.toDomainGroupCondition(cleanIds) },
        parentGroupId = eventGroup.parentGroupId?.let { Identifier(id = it, asTemporary = cleanIds) },
    )

private fun GroupEventType.toEntity(): EventGroupType =
    EventGroupType.valueOf(name)

private fun EventGroupType.toDomain(): GroupEventType =
    GroupEventType.valueOf(name)
