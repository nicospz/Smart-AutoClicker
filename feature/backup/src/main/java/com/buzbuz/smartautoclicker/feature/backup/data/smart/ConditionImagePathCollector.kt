/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.feature.backup.data.smart

import com.buzbuz.smartautoclicker.core.database.entity.CompleteScenario
import com.buzbuz.smartautoclicker.core.database.entity.ConditionType
import com.buzbuz.smartautoclicker.core.database.entity.EventType

fun collectConditionImagePaths(scenario: CompleteScenario): Set<String> =
    buildSet {
        scenario.events.forEach { completeEvent ->
            if (completeEvent.event.type == EventType.IMAGE_EVENT) {
                completeEvent.conditions.forEach { condition ->
                    if (condition.type == ConditionType.ON_IMAGE_DETECTED) {
                        condition.path?.let(::add)
                    }
                }
            }
        }
        scenario.eventGroups.forEach { completeGroup ->
            completeGroup.conditions.forEach { condition ->
                if (condition.type == ConditionType.ON_IMAGE_DETECTED) {
                    condition.path?.let(::add)
                }
            }
        }
    }
