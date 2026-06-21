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
import com.buzbuz.smartautoclicker.core.base.interfaces.sortedByPriority

/** Sibling groups under [parentId] (null = root), sorted by priority. */
fun List<EventGroup>.childrenOf(parentId: Identifier?): List<EventGroup> =
    filter { it.parentGroupId == parentId }.sortedByPriority().toList()

/** Depth-first visit matching the scenario editor nested list order. */
fun List<EventGroup>.visitInListOrder(onGroup: (EventGroup) -> Unit) {
    visitInListOrderAtDepth(parentId = null, onGroup = onGroup)
}

/** Depth-first visit with nesting depth (0 = root groups). */
fun List<EventGroup>.visitInListOrderWithDepth(onGroup: (EventGroup, depth: Int) -> Unit) {
    visitInListOrderAtDepth(parentId = null, depth = 0, onGroup = onGroup)
}

private fun List<EventGroup>.visitInListOrderAtDepth(
    parentId: Identifier?,
    depth: Int = 0,
    onGroup: (EventGroup, Int) -> Unit,
) {
    childrenOf(parentId).forEach { group ->
        onGroup(group, depth)
        visitInListOrderAtDepth(group.id, depth + 1, onGroup)
    }
}

private fun List<EventGroup>.visitInListOrderAtDepth(
    parentId: Identifier?,
    onGroup: (EventGroup) -> Unit,
) {
    visitInListOrderAtDepth(parentId = parentId, depth = 0) { group, _ -> onGroup(group) }
}

fun List<EventGroup>.sortedForGroupListProcessing(): List<EventGroup> = buildList {
    visitInListOrder { add(it) }
}

/** Root-level list entry: ungrouped event or root group, merged by shared priority. */
sealed interface RootListEntry<out E> {
    data class UngroupedEvent<E>(val event: E) : RootListEntry<E>
    data class RootGroup(val group: EventGroup) : RootListEntry<Nothing>
}

/**
 * Merge ungrouped events and root groups into a single list ordered by shared [priority].
 * At equal priority, ungrouped events sort before groups.
 */
fun <E> rootListEntries(
    ungroupedEvents: List<E>,
    groups: List<EventGroup>,
    eventPriority: (E) -> Int,
): List<RootListEntry<E>> {
    val entries = buildList {
        ungroupedEvents.forEach { event ->
            add(eventPriority(event) to RootListEntry.UngroupedEvent(event))
        }
        groups.childrenOf(parentId = null).forEach { group ->
            add(group.priority to RootListEntry.RootGroup(group))
        }
    }
    return entries.sortedWith(
        compareBy<Pair<Int, RootListEntry<E>>> { it.first }.thenBy { entry ->
            when (entry.second) {
                is RootListEntry.UngroupedEvent<*> -> 0
                is RootListEntry.RootGroup -> 1
            }
        },
    ).map { it.second }
}

@JvmName("appendImageEventsInGroup")
private fun MutableList<ImageEvent>.appendEventsInGroup(
    group: EventGroup,
    allEvents: List<ImageEvent>,
    groups: List<EventGroup>,
) {
    addAll(allEvents.filter { it.groupId == group.id }.sortedByPriority())
    groups.childrenOf(group.id).forEach { appendEventsInGroup(it, allEvents, groups) }
}

@JvmName("appendTriggerEventsInGroup")
private fun MutableList<TriggerEvent>.appendEventsInGroup(
    group: EventGroup,
    allEvents: List<TriggerEvent>,
    groups: List<EventGroup>,
) {
    addAll(allEvents.filter { it.groupId == group.id }.sortedByPriority())
    groups.childrenOf(group.id).forEach { appendEventsInGroup(it, allEvents, groups) }
}

/**
 * Order events for processing to match the scenario editor list:
 * root ungrouped events and root groups interleaved by shared priority, then events
 * within each group during a depth-first group walk.
 */
@JvmName("sortedImageEventsForGroupListProcessing")
fun List<ImageEvent>.sortedForGroupListProcessing(groups: List<EventGroup>): List<ImageEvent> = buildList {
    val ungrouped = filter { it.groupId == null }.sortedByPriority().toList()
    rootListEntries(ungrouped, groups, eventPriority = ImageEvent::priority).forEach { entry ->
        when (entry) {
            is RootListEntry.UngroupedEvent -> add(entry.event)
            is RootListEntry.RootGroup -> appendEventsInGroup(entry.group, this@sortedForGroupListProcessing, groups)
        }
    }
}

@JvmName("sortedTriggerEventsForGroupListProcessing")
fun List<TriggerEvent>.sortedForGroupListProcessing(groups: List<EventGroup>): List<TriggerEvent> = buildList {
    val ungrouped = filter { it.groupId == null }.sortedByPriority().toList()
    rootListEntries(ungrouped, groups, eventPriority = TriggerEvent::priority).forEach { entry ->
        when (entry) {
            is RootListEntry.UngroupedEvent -> add(entry.event)
            is RootListEntry.RootGroup -> appendEventsInGroup(entry.group, this@sortedForGroupListProcessing, groups)
        }
    }
}

/** All descendant group database ids of [groupId], excluding [groupId] itself. */
fun List<EventGroup>.descendantIds(groupId: Identifier): Set<Long> {
    val result = mutableSetOf<Long>()
    fun collect(parentId: Identifier) {
        childrenOf(parentId).forEach { child ->
            result.add(child.id.databaseId)
            collect(child.id)
        }
    }
    collect(groupId)
    return result
}

/** True if assigning [newParentId] as parent of [groupId] would create a cycle. */
fun wouldCreateCycle(
    groupId: Identifier,
    newParentId: Identifier?,
    allGroups: List<EventGroup>,
): Boolean {
    if (newParentId == null) return false
    if (groupId == newParentId) return true
    return newParentId.databaseId in allGroups.descendantIds(groupId)
}

/** Display name including ancestor groups, e.g. "Full screen › Catch screen". */
fun List<EventGroup>.hierarchicalName(groupId: Identifier, separator: String = " › "): String {
    val byId = associateBy { it.id }
    val parts = mutableListOf<String>()
    var current: EventGroup? = byId[groupId]
    while (current != null) {
        parts.add(0, current.name)
        current = current.parentGroupId?.let { byId[it] }
    }
    return parts.joinToString(separator)
}
