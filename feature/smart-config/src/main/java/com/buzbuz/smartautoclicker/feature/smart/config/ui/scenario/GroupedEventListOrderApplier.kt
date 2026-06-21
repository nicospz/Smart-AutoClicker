/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.feature.smart.config.ui.scenario

import com.buzbuz.smartautoclicker.core.domain.model.event.EventGroup
import com.buzbuz.smartautoclicker.core.domain.model.event.ImageEvent
import com.buzbuz.smartautoclicker.core.domain.model.event.TriggerEvent
import com.buzbuz.smartautoclicker.feature.smart.config.ui.scenario.imageevents.ImageEventListItem
import com.buzbuz.smartautoclicker.feature.smart.config.ui.scenario.triggerevents.TriggerEventListItem

internal object GroupedEventListOrderApplier {

    fun applyImageListOrder(
        items: List<ImageEventListItem>,
        events: List<ImageEvent>,
        groups: List<EventGroup>,
    ): Pair<List<ImageEvent>, List<EventGroup>> {
        val eventsById = events.associateBy { it.id.databaseId }.toMutableMap()
        val groupsById = groups.associateBy { it.id.databaseId }.toMutableMap()
        processImageLevel(items, startIndex = 0, parentDepth = -1, eventsById, groupsById)
        return eventsById.values.toList() to groupsById.values.toList()
    }

    fun applyTriggerListOrder(
        items: List<TriggerEventListItem>,
        events: List<TriggerEvent>,
        groups: List<EventGroup>,
    ): Pair<List<TriggerEvent>, List<EventGroup>> {
        val eventsById = events.associateBy { it.id.databaseId }.toMutableMap()
        val groupsById = groups.associateBy { it.id.databaseId }.toMutableMap()
        processTriggerLevel(items, startIndex = 0, parentDepth = -1, eventsById, groupsById)
        return eventsById.values.toList() to groupsById.values.toList()
    }

    private fun processImageLevel(
        items: List<ImageEventListItem>,
        startIndex: Int,
        parentDepth: Int,
        eventsById: MutableMap<Long, ImageEvent>,
        groupsById: MutableMap<Long, EventGroup>,
    ): Int {
        var index = startIndex
        var siblingPriority = 0
        val targetDepth = parentDepth + 1

        while (index < items.size) {
            when (val item = items[index]) {
                is ImageEventListItem.AddGroupAction -> index++
                is ImageEventListItem.EventItem -> {
                    if (item.nestingDepth < targetDepth) return index
                    if (item.nestingDepth > targetDepth) {
                        index++
                        continue
                    }
                    eventsById[item.uiEvent.event.id.databaseId] =
                        eventsById.getValue(item.uiEvent.event.id.databaseId).copy(priority = siblingPriority++)
                    index++
                }
                is ImageEventListItem.GroupHeader -> {
                    if (item.depth < targetDepth) return index
                    if (item.depth > targetDepth) {
                        index++
                        continue
                    }
                    groupsById[item.group.id.databaseId] =
                        groupsById.getValue(item.group.id.databaseId).copy(priority = siblingPriority++)
                    index++
                    if (item.expanded) {
                        index = processImageLevel(items, index, targetDepth, eventsById, groupsById)
                    }
                }
            }
        }
        return index
    }

    private fun processTriggerLevel(
        items: List<TriggerEventListItem>,
        startIndex: Int,
        parentDepth: Int,
        eventsById: MutableMap<Long, TriggerEvent>,
        groupsById: MutableMap<Long, EventGroup>,
    ): Int {
        var index = startIndex
        var siblingPriority = 0
        val targetDepth = parentDepth + 1

        while (index < items.size) {
            when (val item = items[index]) {
                is TriggerEventListItem.AddGroupAction -> index++
                is TriggerEventListItem.EventItem -> {
                    if (item.nestingDepth < targetDepth) return index
                    if (item.nestingDepth > targetDepth) {
                        index++
                        continue
                    }
                    eventsById[item.uiEvent.event.id.databaseId] =
                        eventsById.getValue(item.uiEvent.event.id.databaseId).copy(priority = siblingPriority++)
                    index++
                }
                is TriggerEventListItem.GroupHeader -> {
                    if (item.depth < targetDepth) return index
                    if (item.depth > targetDepth) {
                        index++
                        continue
                    }
                    groupsById[item.group.id.databaseId] =
                        groupsById.getValue(item.group.id.databaseId).copy(priority = siblingPriority++)
                    index++
                    if (item.expanded) {
                        index = processTriggerLevel(items, index, targetDepth, eventsById, groupsById)
                    }
                }
            }
        }
        return index
    }
}

internal object GroupedListReorder {

    fun canReorderImageItems(from: ImageEventListItem, to: ImageEventListItem): Boolean =
        listDepth(from) == listDepth(to) && from !is ImageEventListItem.AddGroupAction &&
            to !is ImageEventListItem.AddGroupAction &&
            (listDepth(from) > 0 || from is ImageEventListItem.EventItem || from is ImageEventListItem.GroupHeader)

    fun canReorderTriggerItems(from: TriggerEventListItem, to: TriggerEventListItem): Boolean =
        listDepth(from) == listDepth(to) && from !is TriggerEventListItem.AddGroupAction &&
            to !is TriggerEventListItem.AddGroupAction &&
            (listDepth(from) > 0 || from is TriggerEventListItem.EventItem || from is TriggerEventListItem.GroupHeader)

    fun imageSubtreeSize(items: List<ImageEventListItem>, headerIndex: Int): Int {
        val header = items[headerIndex] as ImageEventListItem.GroupHeader
        return 1 + countImageUntilSiblingDepth(items, headerIndex + 1, header.depth)
    }

    fun triggerSubtreeSize(items: List<TriggerEventListItem>, headerIndex: Int): Int {
        val header = items[headerIndex] as TriggerEventListItem.GroupHeader
        return 1 + countTriggerUntilSiblingDepth(items, headerIndex + 1, header.depth)
    }

    private fun listDepth(item: ImageEventListItem): Int = when (item) {
        is ImageEventListItem.EventItem -> item.nestingDepth
        is ImageEventListItem.GroupHeader -> item.depth
        is ImageEventListItem.AddGroupAction -> -1
    }

    private fun listDepth(item: TriggerEventListItem): Int = when (item) {
        is TriggerEventListItem.EventItem -> item.nestingDepth
        is TriggerEventListItem.GroupHeader -> item.depth
        is TriggerEventListItem.AddGroupAction -> -1
    }

    private fun countImageUntilSiblingDepth(items: List<ImageEventListItem>, start: Int, headerDepth: Int): Int {
        var count = 0
        var index = start
        while (index < items.size) {
            when (val item = items[index]) {
                is ImageEventListItem.AddGroupAction -> break
                is ImageEventListItem.EventItem -> {
                    if (item.nestingDepth <= headerDepth) break
                    count++
                }
                is ImageEventListItem.GroupHeader -> {
                    if (item.depth <= headerDepth) break
                    count++
                }
            }
            index++
        }
        return count
    }

    private fun countTriggerUntilSiblingDepth(items: List<TriggerEventListItem>, start: Int, headerDepth: Int): Int {
        var count = 0
        var index = start
        while (index < items.size) {
            when (val item = items[index]) {
                is TriggerEventListItem.AddGroupAction -> break
                is TriggerEventListItem.EventItem -> {
                    if (item.nestingDepth <= headerDepth) break
                    count++
                }
                is TriggerEventListItem.GroupHeader -> {
                    if (item.depth <= headerDepth) break
                    count++
                }
            }
            index++
        }
        return count
    }
}
