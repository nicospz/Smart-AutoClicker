/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.feature.smart.config.ui.scenario.imageevents

import com.buzbuz.smartautoclicker.core.base.interfaces.sortedByPriority
import com.buzbuz.smartautoclicker.core.domain.model.event.EventGroup
import com.buzbuz.smartautoclicker.core.domain.model.event.ImageEvent
import com.buzbuz.smartautoclicker.core.domain.model.event.RootListEntry
import com.buzbuz.smartautoclicker.core.domain.model.event.childrenOf
import com.buzbuz.smartautoclicker.core.domain.model.event.rootListEntries
import com.buzbuz.smartautoclicker.feature.smart.config.ui.common.model.event.UiImageEvent
import com.buzbuz.smartautoclicker.feature.smart.config.ui.common.model.event.toUiImageEvent

sealed class ImageEventListItem {

    abstract val stableId: Long

    data class AddGroupAction(val id: Long = ADD_GROUP_ID) : ImageEventListItem() {
        override val stableId: Long = id
    }

    data class GroupHeader(
        val group: EventGroup,
        val expanded: Boolean,
        val depth: Int,
        val inError: Boolean,
    ) : ImageEventListItem() {
        override val stableId: Long = -group.id.databaseId
    }

    data class EventItem(
        val uiEvent: UiImageEvent,
        val nestingDepth: Int,
        val canReorder: Boolean,
    ) : ImageEventListItem() {
        override val stableId: Long = uiEvent.event.id.databaseId
    }

    companion object {
        const val ADD_GROUP_ID = Long.MIN_VALUE

        fun buildList(
            events: List<ImageEvent>,
            eventValidityById: Map<Long, Boolean> = emptyMap(),
            groups: List<EventGroup>,
            groupValidityById: Map<Long, Boolean> = emptyMap(),
            expandedGroupIds: Set<Long>,
            searchQuery: String = "",
        ): List<ImageEventListItem> = buildList {
            val searchActive = searchQuery.isNotBlank()
            val visibleEvents = if (searchActive) {
                events.filter { it.name.contains(searchQuery, ignoreCase = true) }
            } else {
                events
            }
            val visibleGroups = if (searchActive) {
                groups.filter { it.id.databaseId in groups.ancestorIdsFor(visibleEvents) }
            } else {
                groups
            }
            val expandedIds = if (searchActive) {
                visibleGroups.map { it.id.databaseId }.toSet()
            } else {
                expandedGroupIds
            }

            if (!searchActive) add(AddGroupAction())

            val ungrouped = visibleEvents.filter { it.groupId == null }.sortedByPriority().toList()
            rootListEntries(ungrouped, visibleGroups, eventPriority = ImageEvent::priority).forEach { entry ->
                when (entry) {
                    is RootListEntry.UngroupedEvent -> {
                        add(
                            EventItem(
                                entry.event.toUiImageEvent(
                                    inError = !(eventValidityById[entry.event.id.databaseId] ?: entry.event.isComplete()),
                                ),
                                nestingDepth = 0,
                                canReorder = !searchActive,
                            ),
                        )
                    }
                    is RootListEntry.RootGroup -> {
                        appendGroupAtDepth(
                            events = visibleEvents,
                            eventValidityById = eventValidityById,
                            groups = visibleGroups,
                            groupValidityById = groupValidityById,
                            expandedGroupIds = expandedIds,
                            group = entry.group,
                            depth = 0,
                            canReorder = !searchActive,
                        )
                    }
                }
            }
        }

        private fun MutableList<ImageEventListItem>.appendGroupAtDepth(
            events: List<ImageEvent>,
            eventValidityById: Map<Long, Boolean>,
            groups: List<EventGroup>,
            groupValidityById: Map<Long, Boolean>,
            expandedGroupIds: Set<Long>,
            group: EventGroup,
            depth: Int,
            canReorder: Boolean,
        ) {
            val expanded = expandedGroupIds.contains(group.id.databaseId)
            add(
                GroupHeader(
                    group = group,
                    expanded = expanded,
                    depth = depth,
                    inError = !(groupValidityById[group.id.databaseId] ?: group.isComplete()),
                ),
            )
            if (expanded) {
                events.filter { it.groupId == group.id }.sortedByPriority().forEach { event ->
                    add(
                        EventItem(
                            event.toUiImageEvent(
                                inError = !(eventValidityById[event.id.databaseId] ?: event.isComplete()),
                            ),
                            nestingDepth = depth + 1,
                            canReorder = canReorder,
                        ),
                    )
                }
                groups.childrenOf(group.id).forEach { childGroup ->
                    appendGroupAtDepth(
                        events = events,
                        eventValidityById = eventValidityById,
                        groups = groups,
                        groupValidityById = groupValidityById,
                        expandedGroupIds = expandedGroupIds,
                        group = childGroup,
                        depth = depth + 1,
                        canReorder = canReorder,
                    )
                }
            }
        }

        private fun List<EventGroup>.ancestorIdsFor(events: List<ImageEvent>): Set<Long> {
            val groupsById = associateBy { it.id.databaseId }
            return buildSet {
                events.forEach { event ->
                    var groupId = event.groupId?.databaseId
                    while (groupId != null) {
                        add(groupId)
                        groupId = groupsById[groupId]?.parentGroupId?.databaseId
                    }
                }
            }
        }
    }
}
