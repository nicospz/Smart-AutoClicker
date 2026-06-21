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
package com.buzbuz.smartautoclicker.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

import com.buzbuz.smartautoclicker.core.database.EVENT_GROUP_TABLE
import com.buzbuz.smartautoclicker.core.database.entity.CompleteEventGroupEntity
import com.buzbuz.smartautoclicker.core.database.entity.EventGroupEntity
import com.buzbuz.smartautoclicker.core.database.entity.EventGroupType

/** Allows to access and edit event groups in the database. */
@Dao
abstract class EventGroupDao {

    @Query("SELECT * FROM $EVENT_GROUP_TABLE WHERE scenario_id=:scenarioId AND event_type=:eventType ORDER BY priority")
    abstract suspend fun getEventGroups(scenarioId: Long, eventType: EventGroupType): List<EventGroupEntity>

    @Query("SELECT * FROM $EVENT_GROUP_TABLE WHERE scenario_id=:scenarioId ORDER BY priority")
    abstract suspend fun getAllEventGroups(scenarioId: Long): List<EventGroupEntity>

    @Transaction
    @Query(
        """
        SELECT * FROM $EVENT_GROUP_TABLE
        WHERE scenario_id=:scenarioId AND event_type=:eventType
        ORDER BY priority
        """
    )
    abstract suspend fun getCompleteEventGroups(
        scenarioId: Long,
        eventType: EventGroupType,
    ): List<CompleteEventGroupEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun addEventGroups(groups: List<EventGroupEntity>): List<Long>

    @Update
    abstract suspend fun updateEventGroups(groups: List<EventGroupEntity>)

    @Delete
    abstract suspend fun deleteEventGroups(groups: List<EventGroupEntity>)
}
