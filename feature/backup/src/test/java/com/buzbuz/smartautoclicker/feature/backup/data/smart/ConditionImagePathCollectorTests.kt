/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.feature.backup.data.smart

import com.buzbuz.smartautoclicker.core.database.entity.CompleteEventGroupEntity
import com.buzbuz.smartautoclicker.core.database.entity.CompleteEventEntity
import com.buzbuz.smartautoclicker.core.database.entity.CompleteScenario
import com.buzbuz.smartautoclicker.core.database.entity.ConditionEntity
import com.buzbuz.smartautoclicker.core.database.entity.ConditionType
import com.buzbuz.smartautoclicker.core.database.entity.EventEntity
import com.buzbuz.smartautoclicker.core.database.entity.EventGroupEntity
import com.buzbuz.smartautoclicker.core.database.entity.EventGroupType
import com.buzbuz.smartautoclicker.core.database.entity.EventType
import com.buzbuz.smartautoclicker.core.database.entity.ScenarioEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConditionImagePathCollectorTests {

    @Test
    fun collectConditionImagePaths_includesEventAndGroupGateConditions() {
        val scenario = CompleteScenario(
            scenario = ScenarioEntity(
                id = 1L,
                name = "Test",
                detectionQuality = 1200,
            ),
            events = listOf(
                CompleteEventEntity(
                    event = EventEntity(
                        id = 10L,
                        scenarioId = 1L,
                        name = "Image",
                        conditionOperator = 1,
                        priority = 0,
                        type = EventType.IMAGE_EVENT,
                    ),
                    conditions = listOf(
                        imageCondition(id = 100L, eventId = 10L, path = "Condition_event.png"),
                    ),
                    actions = emptyList(),
                ),
            ),
            eventGroups = listOf(
                CompleteEventGroupEntity(
                    eventGroup = EventGroupEntity(
                        id = 20L,
                        scenarioId = 1L,
                        name = "Gate",
                        eventType = EventGroupType.IMAGE,
                        conditionOperator = 1,
                        priority = 0,
                    ),
                    conditions = listOf(
                        imageCondition(
                            id = 200L,
                            eventGroupId = 20L,
                            path = "Condition_group.png",
                        ),
                    ),
                ),
            ),
        )

        val paths = collectConditionImagePaths(scenario)
        assertEquals(setOf("Condition_event.png", "Condition_group.png"), paths)
        assertTrue(paths.contains("Condition_group.png"))
    }

    private fun imageCondition(
        id: Long,
        eventId: Long? = null,
        eventGroupId: Long? = null,
        path: String,
    ) = ConditionEntity(
        id = id,
        eventId = eventId,
        eventGroupId = eventGroupId,
        name = "cond",
        type = ConditionType.ON_IMAGE_DETECTED,
        priority = 0,
        path = path,
        areaLeft = 0,
        areaTop = 0,
        areaRight = 10,
        areaBottom = 10,
    )
}
