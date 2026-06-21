/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.feature.smart.config.ui.scenario.triggerevents

import com.buzbuz.smartautoclicker.core.base.interfaces.sortedByPriority
import com.buzbuz.smartautoclicker.core.domain.model.event.EventGroup
import com.buzbuz.smartautoclicker.core.domain.model.event.RootListEntry
import com.buzbuz.smartautoclicker.core.domain.model.event.TriggerEvent
import com.buzbuz.smartautoclicker.core.domain.model.event.childrenOf
import com.buzbuz.smartautoclicker.core.domain.model.event.rootListEntries
import com.buzbuz.smartautoclicker.feature.smart.config.ui.common.model.event.UiTriggerEvent
import com.buzbuz.smartautoclicker.feature.smart.config.ui.common.model.event.toUiTriggerEvent

sealed class TriggerEventListItem {

    abstract val stableId: Long

    data class AddGroupAction(val id: Long = ADD_GROUP_ID) : TriggerEventListItem() {
        override val stableId: Long = id
    }

    data class GroupHeader(
        val group: EventGroup,
        val expanded: Boolean,
        val depth: Int,
        val inError: Boolean,
    ) : TriggerEventListItem() {
        override val stableId: Long = -group.id.databaseId
    }

    data class EventItem(
        val uiEvent: UiTriggerEvent,
        val nestingDepth: Int,
        val canReorder: Boolean,
    ) : TriggerEventListItem() {
        override val stableId: Long = uiEvent.event.id.databaseId
    }

    companion object {
        const val ADD_GROUP_ID = Long.MIN_VALUE

        fun buildList(
            events: List<TriggerEvent>,
            groups: List<EventGroup>,
            expandedGroupIds: Set<Long>,
            searchQuery: String = "",
        ): List<TriggerEventListItem> = buildList {
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
            rootListEntries(ungrouped, visibleGroups, eventPriority = TriggerEvent::priority).forEach { entry ->
                when (entry) {
                    is RootListEntry.UngroupedEvent -> {
                        add(
                            EventItem(
                                entry.event.toUiTriggerEvent(inError = !entry.event.isComplete()),
                                nestingDepth = 0,
                                canReorder = !searchActive,
                            ),
                        )
                    }
                    is RootListEntry.RootGroup -> {
                        appendGroupAtDepth(
                            events = visibleEvents,
                            groups = visibleGroups,
                            expandedGroupIds = expandedIds,
                            group = entry.group,
                            depth = 0,
                            canReorder = !searchActive,
                        )
                    }
                }
            }
        }

        private fun MutableList<TriggerEventListItem>.appendGroupAtDepth(
            events: List<TriggerEvent>,
            groups: List<EventGroup>,
            expandedGroupIds: Set<Long>,
            group: EventGroup,
            depth: Int,
            canReorder: Boolean,
        ) {
            val expanded = expandedGroupIds.contains(group.id.databaseId)
            add(GroupHeader(group, expanded, depth, inError = !group.isComplete()))
            if (expanded) {
                events.filter { it.groupId == group.id }.sortedByPriority().forEach { event ->
                    add(
                        EventItem(
                            event.toUiTriggerEvent(inError = !event.isComplete()),
                            nestingDepth = depth + 1,
                            canReorder = canReorder,
                        ),
                    )
                }
                groups.childrenOf(group.id).forEach { childGroup ->
                    appendGroupAtDepth(
                        events = events,
                        groups = groups,
                        expandedGroupIds = expandedGroupIds,
                        group = childGroup,
                        depth = depth + 1,
                        canReorder = canReorder,
                    )
                }
            }
        }

        private fun List<EventGroup>.ancestorIdsFor(events: List<TriggerEvent>): Set<Long> {
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
