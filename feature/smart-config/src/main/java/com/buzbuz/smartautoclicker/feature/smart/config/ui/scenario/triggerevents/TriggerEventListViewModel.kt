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
package com.buzbuz.smartautoclicker.feature.smart.config.ui.scenario.triggerevents

import android.content.Context
import androidx.lifecycle.ViewModel
import com.buzbuz.smartautoclicker.core.domain.model.event.EventGroup
import com.buzbuz.smartautoclicker.core.domain.model.event.TriggerEvent
import com.buzbuz.smartautoclicker.feature.smart.config.domain.EditionRepository
import com.buzbuz.smartautoclicker.feature.smart.config.ui.scenario.GroupedEventListOrderApplier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

class TriggerEventListViewModel @Inject constructor(
    private val editionRepository: EditionRepository,
) : ViewModel() {

    private val expandedGroupIds = MutableStateFlow<Set<Long>>(emptySet())
    private val searchQuery = MutableStateFlow("")

    val listItems = combine(
        editionRepository.editionState.editedTriggerEventsState,
        editionRepository.editionState.editedTriggerEventGroupsState,
        expandedGroupIds,
        searchQuery,
    ) { eventsState, groupsState, expanded, query ->
        TriggerEventListItem.buildList(
            events = eventsState.value ?: emptyList(),
            groups = groupsState.value ?: emptyList(),
            expandedGroupIds = expanded,
            searchQuery = query.trim(),
        )
    }

    val copyButtonIsVisible: Flow<Boolean> = editionRepository.editionState.canCopyTriggerEvents

    fun createNewEvent(context: Context, event: TriggerEvent? = null): TriggerEvent =
        with(editionRepository.editedItemsBuilder) {
            if (event == null) createNewTriggerEvent(context)
            else createNewTriggerEventFrom(from = event)
        }

    fun createNewGroup(context: Context): EventGroup =
        editionRepository.editedItemsBuilder.createNewTriggerEventGroup(context).also { group ->
            editionRepository.upsertNewEventGroup(group)
        }

    fun toggleGroupExpanded(group: EventGroup) {
        val id = group.id.databaseId
        expandedGroupIds.value = expandedGroupIds.value.let { current ->
            if (current.contains(id)) current - id else current + id
        }
    }

    fun startEventEdition(event: TriggerEvent) = editionRepository.startEventEdition(event)

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun setEventIgnored(event: TriggerEvent, ignored: Boolean) {
        val updatedEvents = editionRepository.getEditedTriggerEvents().map { editedEvent ->
            if (editedEvent.id == event.id) editedEvent.copy(ignored = ignored) else editedEvent
        }
        editionRepository.updateTriggerEventsAndGroupsOrder(
            events = updatedEvents,
            groups = editionRepository.getEditedTriggerEventGroups(),
        )
    }

    fun startGroupEdition(group: EventGroup) = editionRepository.startEventGroupEdition(group)

    fun saveEventEdition() = editionRepository.upsertEditedEvent()

    fun saveGroupEdition() = editionRepository.upsertEditedEventGroup()

    fun deleteEditedEvent() = editionRepository.deleteEditedEvent()

    fun deleteEditedGroup() = editionRepository.deleteEditedEventGroup()

    fun dismissEditedEvent() = editionRepository.stopEventEdition()

    fun dismissEditedGroup() = editionRepository.stopEventGroupEdition()

    fun updateListOrder(items: List<TriggerEventListItem>) {
        val events = editionRepository.getEditedTriggerEvents()
        val groups = editionRepository.getEditedTriggerEventGroups()
        val (updatedEvents, updatedGroups) = GroupedEventListOrderApplier.applyTriggerListOrder(items, events, groups)
        editionRepository.updateTriggerEventsAndGroupsOrder(updatedEvents, updatedGroups)
    }
}
