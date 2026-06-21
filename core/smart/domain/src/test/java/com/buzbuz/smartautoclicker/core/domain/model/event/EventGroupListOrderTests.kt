/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.core.domain.model.event

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.buzbuz.smartautoclicker.core.base.identifier.Identifier
import com.buzbuz.smartautoclicker.core.domain.model.AND
import com.buzbuz.smartautoclicker.core.domain.utils.asIdentifier
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.Q])
class EventGroupListOrderTests {

    private val scenarioId = 1L.asIdentifier()
    private val groupAId = 10L.asIdentifier()
    private val groupBId = 20L.asIdentifier()

    private val groupA = EventGroup(
        id = groupAId,
        scenarioId = scenarioId,
        name = "Group A",
        eventType = GroupEventType.IMAGE,
        conditionOperator = AND,
        priority = 1,
        conditions = emptyList(),
        parentGroupId = null,
    )

    private val groupB = EventGroup(
        id = groupBId,
        scenarioId = scenarioId,
        name = "Group B",
        eventType = GroupEventType.IMAGE,
        conditionOperator = AND,
        priority = 0,
        conditions = emptyList(),
        parentGroupId = null,
    )

    @Test
    fun nestedGroups_visitInListOrder_depthFirstAmongSiblings() {
        val fullId = 100L.asIdentifier()
        val splitId = 200L.asIdentifier()
        val catchId = 300L.asIdentifier()
        val mapId = 400L.asIdentifier()

        val full = group(fullId, "Full", priority = 0, parent = null)
        val split = group(splitId, "Split", priority = 1, parent = null)
        val catch = group(catchId, "Catch", priority = 0, parent = fullId)
        val map = group(mapId, "Map", priority = 1, parent = fullId)
        val groups = listOf(split, map, catch, full)

        val visitOrder = buildList {
            groups.visitInListOrder { add(it.id.databaseId) }
        }

        assertEquals(listOf(100L, 300L, 400L, 200L), visitOrder)
    }

    @Test
    fun nestedGroups_imageEvents_sortedForGroupListProcessing_matchesDepthFirstOrder() {
        val fullId = 100L.asIdentifier()
        val catchId = 300L.asIdentifier()
        val full = group(fullId, "Full", priority = 0, parent = null)
        val catch = group(catchId, "Catch", priority = 0, parent = fullId)
        val groups = listOf(catch, full)

        val events = listOf(
            imageEvent(id = 1, priority = 0),
            imageEvent(id = 2, priority = 0, groupId = fullId),
            imageEvent(id = 3, priority = 0, groupId = catchId),
        )

        val ordered = events.sortedForGroupListProcessing(groups)
        assertEquals(listOf(1L, 2L, 3L), ordered.map { it.id.databaseId })
    }

    @Test
    fun wouldCreateCycle_detectsDescendantAsParent() {
        val parentId = 10L.asIdentifier()
        val childId = 20L.asIdentifier()
        val parent = group(parentId, "Parent", priority = 0, parent = null)
        val child = group(childId, "Child", priority = 0, parent = parentId)
        val groups = listOf(parent, child)

        assertEquals(true, wouldCreateCycle(parentId, childId, groups))
        assertEquals(false, wouldCreateCycle(childId, parentId, groups))
    }

    @Test
    fun hierarchicalName_includesAncestors() {
        val fullId = 100L.asIdentifier()
        val catchId = 300L.asIdentifier()
        val full = group(fullId, "Full screen", priority = 0, parent = null)
        val catch = group(catchId, "Catch screen", priority = 0, parent = fullId)
        val groups = listOf(full, catch)

        assertEquals("Full screen › Catch screen", groups.hierarchicalName(catchId))
    }

    private fun group(
        id: Identifier,
        name: String,
        priority: Int,
        parent: Identifier?,
    ) = EventGroup(
        id = id,
        scenarioId = scenarioId,
        name = name,
        eventType = GroupEventType.IMAGE,
        conditionOperator = AND,
        priority = priority,
        conditions = emptyList(),
        parentGroupId = parent,
    )

    @Test
    fun imageEvents_sortedForGroupListProcessing_interleavesRootEventsAndGroups() {
        val events = listOf(
            imageEvent(id = 1, priority = 0),
            imageEvent(id = 2, priority = 2),
            imageEvent(id = 3, priority = 4),
            imageEvent(id = 4, priority = 0, groupId = groupAId),
            imageEvent(id = 5, priority = 1, groupId = groupBId),
        )

        val ordered = events.sortedForGroupListProcessing(listOf(groupA, groupB))

        // groupB(p=0) -> 5, event1(p=0), groupA(p=1) -> 4, event2(p=2), event3(p=4)
        assertEquals(listOf(5L, 1L, 4L, 2L, 3L), ordered.map { it.id.databaseId })
    }

    @Test
    fun imageEvents_sortedForGroupListProcessing_matchesEditorListOrder() {
        val events = listOf(
            imageEvent(id = 1, priority = 5, groupId = groupAId),
            imageEvent(id = 2, priority = 1),
            imageEvent(id = 3, priority = 2, groupId = groupBId),
            imageEvent(id = 4, priority = 0, groupId = groupAId),
            imageEvent(id = 5, priority = 3),
        )

        val ordered = events.sortedForGroupListProcessing(listOf(groupA, groupB))

        // groupB(p=0) -> 3, event2(p=1), groupA(p=1) -> 4,1, event5(p=3)
        assertEquals(listOf(3L, 2L, 4L, 1L, 5L), ordered.map { it.id.databaseId })
    }

    private fun imageEvent(
        id: Long,
        priority: Int,
        groupId: Identifier? = null,
        enabledOnStart: Boolean = true,
    ) = ImageEvent(
        id = id.asIdentifier(),
        scenarioId = scenarioId,
        name = "event-$id",
        conditionOperator = AND,
        enabledOnStart = enabledOnStart,
        priority = priority,
        keepDetecting = false,
        groupId = groupId,
        conditions = listOf(
            com.buzbuz.smartautoclicker.core.domain.model.condition.ImageCondition(
                id = (id * 100).asIdentifier(),
                eventId = id.asIdentifier(),
                name = "cond-$id",
                priority = 0,
                path = "/tmp/$id.png",
                area = android.graphics.Rect(0, 0, 1, 1),
                threshold = 1,
                detectionType = com.buzbuz.smartautoclicker.core.domain.model.EXACT,
                shouldBeDetected = true,
            )
        ),
        actions = listOf(
            com.buzbuz.smartautoclicker.core.domain.model.action.Pause(
                id = (id * 1000).asIdentifier(),
                eventId = id.asIdentifier(),
                name = "pause",
                priority = 0,
                pauseDuration = 1,
            )
        ),
    )
}
