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

import android.os.Build

import androidx.test.ext.junit.runners.AndroidJUnit4

import com.buzbuz.smartautoclicker.core.database.entity.CompleteEventEntity
import com.buzbuz.smartautoclicker.core.database.entity.ImageEventDetectionMode as EntityImageEventDetectionMode
import com.buzbuz.smartautoclicker.core.database.entity.OffsetRepeatMatchMode as EntityOffsetRepeatMatchMode
import com.buzbuz.smartautoclicker.core.domain.model.action.ActionTestsData
import com.buzbuz.smartautoclicker.core.domain.model.condition.ConditionTestsData

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.Q])
class EventMapperTests {

    @Test
    fun imageEvent_toEntity() {
        assertEquals(
            EventTestsData.getNewImageEventEntity(scenarioId = EventTestsData.EVENT_SCENARIO_ID, priority = 0),
            EventTestsData.getNewImageEvent(scenarioId = EventTestsData.EVENT_SCENARIO_ID, priority = 0).toEntity()
        )
    }

    @Test
    fun imageEvent_toDomain() {
        val completeEvent = CompleteEventEntity(
            event = EventTestsData.getNewImageEventEntity(scenarioId = EventTestsData.EVENT_SCENARIO_ID, priority = 0),
            actions = emptyList(),
            conditions = emptyList(),
        )

        assertEquals(
            EventTestsData.getNewImageEvent(scenarioId = EventTestsData.EVENT_SCENARIO_ID, priority = 0),
            completeEvent.toDomainImageEvent()
        )
    }

    @Test
    fun imageEvent_toDomain_complete() {
        val imageEvent = CompleteEventEntity(
            event = EventTestsData.getNewImageEventEntity(scenarioId = EventTestsData.EVENT_SCENARIO_ID, priority = 0),
            actions = listOf(ActionTestsData.getNewPauseEntity(eventId = EventTestsData.EVENT_ID)),
            conditions = listOf(ConditionTestsData.getNewImageConditionEntity(eventId = EventTestsData.EVENT_ID)),
        ).toDomainImageEvent()

        assertEquals(
            EventTestsData.getNewImageEvent(
                scenarioId = EventTestsData.EVENT_SCENARIO_ID,
                priority = 0,
                actions = listOf(ActionTestsData.getNewPause(eventId = EventTestsData.EVENT_ID)),
                conditions = listOf(ConditionTestsData.getNewImageCondition(eventId = EventTestsData.EVENT_ID)),
            ),
            imageEvent,
        )
    }

    @Test
    fun imageEvent_toEntity_offsetRepeat() {
        assertEquals(
            EventTestsData.getNewImageEventEntity(
                scenarioId = EventTestsData.EVENT_SCENARIO_ID,
                priority = 0,
                imageDetectionMode = EntityImageEventDetectionMode.OFFSET_REPEAT,
                offsetRepeatCount = 3,
                offsetRepeatX = 10,
                offsetRepeatY = 120,
                offsetRepeatMatchMode = EntityOffsetRepeatMatchMode.ALL_MATCHES,
            ),
            EventTestsData.getNewImageEvent(
                scenarioId = EventTestsData.EVENT_SCENARIO_ID,
                priority = 0,
                detectionMode = ImageEventDetectionMode.OFFSET_REPEAT,
                offsetRepeatCount = 3,
                offsetRepeatX = 10,
                offsetRepeatY = 120,
                offsetRepeatMatchMode = OffsetRepeatMatchMode.ALL_MATCHES,
            ).toEntity()
        )
    }

    @Test
    fun imageEvent_toDomain_offsetRepeat() {
        val completeEvent = CompleteEventEntity(
            event = EventTestsData.getNewImageEventEntity(
                scenarioId = EventTestsData.EVENT_SCENARIO_ID,
                priority = 0,
                imageDetectionMode = EntityImageEventDetectionMode.OFFSET_REPEAT,
                offsetRepeatCount = 2,
                offsetRepeatX = 5,
                offsetRepeatY = 80,
                offsetRepeatMatchMode = EntityOffsetRepeatMatchMode.ALL_MATCHES,
            ),
            actions = emptyList(),
            conditions = emptyList(),
        )

        assertEquals(
            EventTestsData.getNewImageEvent(
                scenarioId = EventTestsData.EVENT_SCENARIO_ID,
                priority = 0,
                detectionMode = ImageEventDetectionMode.OFFSET_REPEAT,
                offsetRepeatCount = 2,
                offsetRepeatX = 5,
                offsetRepeatY = 80,
                offsetRepeatMatchMode = OffsetRepeatMatchMode.ALL_MATCHES,
            ),
            completeEvent.toDomainImageEvent(),
        )
    }

    @Test
    fun imageEvent_toEntity_splitScreen() {
        assertEquals(
            EventTestsData.getNewImageEventEntity(
                scenarioId = EventTestsData.EVENT_SCENARIO_ID,
                priority = 0,
                imageDetectionMode = EntityImageEventDetectionMode.SPLIT_SCREEN,
            ),
            EventTestsData.getNewImageEvent(
                scenarioId = EventTestsData.EVENT_SCENARIO_ID,
                priority = 0,
                detectionMode = ImageEventDetectionMode.SPLIT_SCREEN,
            ).toEntity()
        )
    }

    @Test
    fun imageEvent_toDomain_splitScreen() {
        val completeEvent = CompleteEventEntity(
            event = EventTestsData.getNewImageEventEntity(
                scenarioId = EventTestsData.EVENT_SCENARIO_ID,
                priority = 0,
                imageDetectionMode = EntityImageEventDetectionMode.SPLIT_SCREEN,
            ),
            actions = emptyList(),
            conditions = emptyList(),
        )

        assertEquals(
            EventTestsData.getNewImageEvent(
                scenarioId = EventTestsData.EVENT_SCENARIO_ID,
                priority = 0,
                detectionMode = ImageEventDetectionMode.SPLIT_SCREEN,
            ),
            completeEvent.toDomainImageEvent(),
        )
    }

    @Test
    fun triggerEvent_toEntity() {
        assertEquals(
            EventTestsData.getNewTriggerEventEntity(scenarioId = EventTestsData.EVENT_SCENARIO_ID),
            EventTestsData.getNewTriggerEvent(scenarioId = EventTestsData.EVENT_SCENARIO_ID).toEntity()
        )
    }

    @Test
    fun triggerEvent_toDomain() {
        val completeEvent = CompleteEventEntity(
            event = EventTestsData.getNewTriggerEventEntity(scenarioId = EventTestsData.EVENT_SCENARIO_ID),
            actions = emptyList(),
            conditions = emptyList(),
        )

        assertEquals(
            EventTestsData.getNewTriggerEvent(scenarioId = EventTestsData.EVENT_SCENARIO_ID),
            completeEvent.toDomainTriggerEvent()
        )
    }

    @Test
    fun triggerEvent_toDomain_complete() {
        val triggerEvent = CompleteEventEntity(
            event = EventTestsData.getNewTriggerEventEntity(scenarioId = EventTestsData.EVENT_SCENARIO_ID),
            actions = listOf(ActionTestsData.getNewPauseEntity(eventId = EventTestsData.EVENT_ID)),
            conditions = listOf(ConditionTestsData.getNewTimerReachedConditionEntity(eventId = EventTestsData.EVENT_ID)),
        ).toDomainTriggerEvent()

        assertEquals(
            EventTestsData.getNewTriggerEvent(
                scenarioId = EventTestsData.EVENT_SCENARIO_ID,
                actions = listOf(ActionTestsData.getNewPause(eventId = EventTestsData.EVENT_ID)),
                conditions = listOf(ConditionTestsData.getNewTimerReachedCondition(eventId = EventTestsData.EVENT_ID)),
            ),
            triggerEvent,
        )
    }
}