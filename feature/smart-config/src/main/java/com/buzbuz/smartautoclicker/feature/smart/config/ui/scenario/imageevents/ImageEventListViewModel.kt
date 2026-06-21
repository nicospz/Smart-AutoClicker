/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.feature.smart.config.ui.scenario.imageevents

import android.content.Context
import android.view.View
import androidx.lifecycle.ViewModel
import com.buzbuz.smartautoclicker.core.domain.model.event.EventGroup
import com.buzbuz.smartautoclicker.core.domain.model.event.ImageEvent
import com.buzbuz.smartautoclicker.core.ui.monitoring.MonitoredViewType
import com.buzbuz.smartautoclicker.core.ui.monitoring.MonitoredViewsManager
import com.buzbuz.smartautoclicker.feature.smart.config.domain.EditionRepository
import com.buzbuz.smartautoclicker.feature.smart.config.ui.scenario.GroupedEventListOrderApplier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

class ImageEventListViewModel @Inject constructor(
    private val editionRepository: EditionRepository,
    private val monitoredViewsManager: MonitoredViewsManager,
) : ViewModel() {

    private val expandedGroupIds = MutableStateFlow<Set<Long>>(emptySet())
    private val searchQuery = MutableStateFlow("")

    val listItems = combine(
        editionRepository.editionState.editedImageEventsState,
        editionRepository.editionState.editedImageEventGroupsState,
        expandedGroupIds,
        searchQuery,
    ) { eventsState, groupsState, expanded, query ->
        ImageEventListItem.buildList(
            events = eventsState.value ?: emptyList(),
            groups = groupsState.value ?: emptyList(),
            expandedGroupIds = expanded,
            searchQuery = query.trim(),
        )
    }

    val copyButtonIsVisible = editionRepository.editionState.canCopyImageEvents

    fun createNewEvent(context: Context, event: ImageEvent? = null): ImageEvent =
        with(editionRepository.editedItemsBuilder) {
            if (event == null) createNewImageEvent(context)
            else createNewImageEventFrom(event)
        }

    fun createNewGroup(context: Context): EventGroup =
        editionRepository.editedItemsBuilder.createNewImageEventGroup(context).also { group ->
            editionRepository.upsertNewEventGroup(group)
        }

    fun toggleGroupExpanded(group: EventGroup) {
        val id = group.id.databaseId
        expandedGroupIds.value = expandedGroupIds.value.let { current ->
            if (current.contains(id)) current - id else current + id
        }
    }

    fun startEventEdition(event: ImageEvent) = editionRepository.startEventEdition(event)

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun setEventIgnored(event: ImageEvent, ignored: Boolean) {
        val updatedEvents = editionRepository.getEditedImageEvents().map { editedEvent ->
            if (editedEvent.id == event.id) editedEvent.copy(ignored = ignored) else editedEvent
        }
        editionRepository.updateImageEventsAndGroupsOrder(
            events = updatedEvents,
            groups = editionRepository.getEditedImageEventGroups(),
        )
    }

    fun startGroupEdition(group: EventGroup) = editionRepository.startEventGroupEdition(group)

    fun saveEventEdition() = editionRepository.upsertEditedEvent()

    fun saveGroupEdition() = editionRepository.upsertEditedEventGroup()

    fun deleteEditedEvent() = editionRepository.deleteEditedEvent()

    fun deleteEditedGroup() = editionRepository.deleteEditedEventGroup()

    fun dismissEditedEvent() = editionRepository.stopEventEdition()

    fun dismissEditedGroup() = editionRepository.stopEventGroupEdition()

    fun updateListOrder(items: List<ImageEventListItem>) {
        val events = editionRepository.getEditedImageEvents()
        val groups = editionRepository.getEditedImageEventGroups()
        val (updatedEvents, updatedGroups) = GroupedEventListOrderApplier.applyImageListOrder(items, events, groups)
        editionRepository.updateImageEventsAndGroupsOrder(updatedEvents, updatedGroups)
    }

    fun monitorFirstEventView(view: View) {
        monitoredViewsManager.attach(MonitoredViewType.SCENARIO_DIALOG_ITEM_FIRST_EVENT, view)
    }

    fun stopViewMonitoring() {
        monitoredViewsManager.detach(MonitoredViewType.SCENARIO_DIALOG_ITEM_FIRST_EVENT)
    }
}
