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
package com.buzbuz.smartautoclicker.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

import com.buzbuz.smartautoclicker.core.base.interfaces.EntityWithId
import com.buzbuz.smartautoclicker.core.database.EVENT_GROUP_TABLE

import kotlinx.serialization.Serializable

/** Scope of an event group: screen (image) events or trigger events. */
enum class EventGroupType {
    IMAGE,
    TRIGGER,
}

/**
 * Entity defining a group of events sharing a gate condition.
 *
 * Events in the group are evaluated only when the group gate conditions are fulfilled, independently of their
 * individual enabled/disabled state.
 */
@Entity(
    tableName = EVENT_GROUP_TABLE,
    indices = [
        Index("scenario_id"),
        Index("parent_group_id"),
    ],
    foreignKeys = [
        ForeignKey(
            entity = ScenarioEntity::class,
            parentColumns = ["id"],
            childColumns = ["scenario_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = EventGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["parent_group_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
@Serializable
data class EventGroupEntity(
    @PrimaryKey(autoGenerate = true) override var id: Long,
    @ColumnInfo(name = "scenario_id") var scenarioId: Long,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "event_type") val eventType: EventGroupType,
    @ColumnInfo(name = "operator") val conditionOperator: Int,
    @ColumnInfo(name = "priority") var priority: Int,
    @ColumnInfo(name = "parent_group_id") var parentGroupId: Long? = null,
) : EntityWithId

@Serializable
data class CompleteEventGroupEntity(
    @Embedded val eventGroup: EventGroupEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "event_group_id",
    )
    val conditions: List<ConditionEntity>,
)
