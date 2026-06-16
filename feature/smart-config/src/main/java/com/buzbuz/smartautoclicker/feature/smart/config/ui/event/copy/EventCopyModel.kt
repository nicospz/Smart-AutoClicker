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
package com.buzbuz.smartautoclicker.feature.smart.config.ui.event.copy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import com.buzbuz.smartautoclicker.core.base.identifier.Identifier
import com.buzbuz.smartautoclicker.core.domain.IRepository
import com.buzbuz.smartautoclicker.core.domain.model.action.ToggleEvent
import com.buzbuz.smartautoclicker.core.domain.model.event.ImageEvent
import com.buzbuz.smartautoclicker.core.domain.model.event.Event
import com.buzbuz.smartautoclicker.core.domain.model.event.TriggerEvent
import com.buzbuz.smartautoclicker.feature.smart.config.domain.EditionRepository
import com.buzbuz.smartautoclicker.feature.smart.config.ui.common.model.action.getIconRes
import com.buzbuz.smartautoclicker.feature.smart.config.ui.common.model.event.UiEvent
import com.buzbuz.smartautoclicker.feature.smart.config.ui.common.model.event.UiImageEvent
import com.buzbuz.smartautoclicker.feature.smart.config.ui.common.model.event.UiTriggerEvent
import com.buzbuz.smartautoclicker.feature.smart.config.ui.common.model.event.toUiImageEvent
import com.buzbuz.smartautoclicker.feature.smart.config.ui.common.model.event.toUiTriggerEvent

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/** View model for the [EventCopyDialog]. */
@OptIn(ExperimentalCoroutinesApi::class)
class EventCopyModel @Inject constructor(
    private val editionRepository: EditionRepository,
    private val repository: IRepository,
) : ViewModel() {

    private val requestTriggerEvents: MutableStateFlow<Boolean?> = MutableStateFlow(null)
    private val searchQuery = MutableStateFlow<String?>(null)

    private val requestedCopyItems: Flow<List<Event>> = requestTriggerEvents
        .flatMapLatest { isRequestingTriggerEvents ->
            if (isRequestingTriggerEvents == true) editionRepository.editionState.triggerEventsForCopy
            else editionRepository.editionState.imageEventsForCopy
        }

    private val allCopyItems: Flow<List<EventCopyItem>> =
        combine(
            editionRepository.editionState.scenarioState,
            requestedCopyItems,
            repository.scenarios,
        ) { scenarioState, events, scenarios ->
            val editedScenario = scenarioState.value ?: return@combine emptyList()
            val editedScenarioId = editedScenario.id

            val scenarioNames = scenarios.associate { scenario -> scenario.id to scenario.name }
                .toMutableMap()
                .apply { put(editedScenarioId, editedScenario.name) }

            val eventsByScenario = events.groupBy { event -> event.scenarioId }
            val orderedScenarioIds = buildList {
                if (eventsByScenario.containsKey(editedScenarioId)) add(editedScenarioId)
                addAll(
                    eventsByScenario.keys
                        .filter { scenarioId -> scenarioId != editedScenarioId }
                        .sortedBy { scenarioId -> scenarioNames[scenarioId].orEmpty() },
                )
            }

            buildList {
                orderedScenarioIds.forEach { scenarioId ->
                    val scenarioEvents = eventsByScenario[scenarioId] ?: return@forEach
                    add(
                        EventCopyItem.Header(
                            scenarioId = scenarioId,
                            title = scenarioNames[scenarioId].orEmpty(),
                        ),
                    )
                    addAll(scenarioEvents.toCopyItems().sortedBy { eventItem -> eventItem.name })
                }
            }
        }

    val eventList: Flow<List<EventCopyItem>?> = allCopyItems.combine(searchQuery) { allItems, query ->
            if (query.isNullOrEmpty()) allItems
            else allItems
                .filterIsInstance<EventCopyItem.EventItem>()
                .filter { item -> item.name.contains(query, true) }
        }
    fun setCopyListType(triggerEvents: Boolean) {
        viewModelScope.launch {
            requestTriggerEvents.emit(triggerEvents)
        }
    }

    fun updateSearchQuery(query: String?) {
        viewModelScope.launch {
            searchQuery.emit(query)
        }
    }

    fun eventCopyShouldWarnUser(event: Event): Boolean =
        !event.isFromEditedScenario() && event.actions.find { action ->
            action is ToggleEvent && !action.toggleAll
        } != null

    private fun Event.isFromEditedScenario(): Boolean =
        editionRepository.editionState.getScenario()?.id == scenarioId

    private fun List<Event>.toCopyItems(): List<EventCopyItem.EventItem> = map { event ->
        when (event) {
            is ImageEvent -> EventCopyItem.EventItem.Image(
                name = event.name,
                uiEvent = event.toUiImageEvent(inError = !event.isComplete()),
                actionsIcons = event.actions.map { it.getIconRes() },
            )

            is TriggerEvent -> EventCopyItem.EventItem.Trigger(
                name = event.name,
                uiEvent = event.toUiTriggerEvent(inError = !event.isComplete()),
            )
        }
    }

    /** Types of items in the event copy list. */
    sealed class EventCopyItem {

        /**
         * Header item, delimiting sections by scenario.
         * @param scenarioId the scenario identifier for this section.
         * @param title the scenario name displayed in the header.
         */
        data class Header(
            val scenarioId: Identifier,
            val title: String,
        ) : EventCopyItem()

        sealed class EventItem : EventCopyItem() {

            abstract val name: String
            abstract val uiEvent: UiEvent

            data class Image (
                override val name: String,
                override val uiEvent: UiImageEvent,
                val actionsIcons: List<Int>,
            ) : EventItem()

            data class Trigger (
                override val name: String,
                override val uiEvent: UiTriggerEvent,
            ) : EventItem()
        }

    }
}
