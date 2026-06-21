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
package com.buzbuz.smartautoclicker.feature.smart.config.data

import com.buzbuz.smartautoclicker.core.domain.model.action.Action
import com.buzbuz.smartautoclicker.core.domain.model.condition.Condition
import com.buzbuz.smartautoclicker.core.domain.model.condition.ImageCondition
import com.buzbuz.smartautoclicker.core.domain.model.condition.TriggerCondition
import com.buzbuz.smartautoclicker.core.domain.model.event.Event
import com.buzbuz.smartautoclicker.core.domain.model.event.EventGroup
import com.buzbuz.smartautoclicker.core.domain.model.event.ImageEvent
import com.buzbuz.smartautoclicker.core.domain.model.event.TriggerEvent
import com.buzbuz.smartautoclicker.core.domain.model.scenario.Scenario
import com.buzbuz.smartautoclicker.feature.smart.config.data.events.EventsEditor
import com.buzbuz.smartautoclicker.feature.smart.config.data.events.ImageEventsEditor
import com.buzbuz.smartautoclicker.feature.smart.config.data.events.TriggerEventsEditor
import com.buzbuz.smartautoclicker.feature.smart.config.data.groups.EventGroupsEditor
import com.buzbuz.smartautoclicker.feature.smart.config.data.groups.ImageEventGroupsEditor
import com.buzbuz.smartautoclicker.feature.smart.config.data.groups.TriggerEventGroupsEditor
import com.buzbuz.smartautoclicker.feature.smart.config.domain.model.EditedElementState
import com.buzbuz.smartautoclicker.feature.smart.config.domain.model.EditedListState

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

@OptIn(ExperimentalCoroutinesApi::class)
internal class ScenarioEditor {

    private val referenceScenario: MutableStateFlow<Scenario?> = MutableStateFlow(null)
    private val _editedScenario: MutableStateFlow<Scenario?> = MutableStateFlow(null)
    private val _currentEventEditor: MutableStateFlow<EventsEditor<Event, Condition>?> = MutableStateFlow(null)
    private val _currentEventGroupEditor: MutableStateFlow<EventGroupsEditor<out Condition>?> = MutableStateFlow(null)

    val editedScenario: StateFlow<Scenario?> = _editedScenario
    val editedScenarioState: Flow<EditedElementState<Scenario>> = combine(referenceScenario, _editedScenario) { ref, edit ->
        val hasChanged =
            if (ref == null || edit == null) false
            else ref != edit

        val canBeSaved = edit != null && edit.name.isNotEmpty()

        EditedElementState(edit, hasChanged, canBeSaved)
    }

    private val imageEventsEditor = ImageEventsEditor(::deleteAllReferencesToEvent, editedScenario)
    private val triggerEventsEditor = TriggerEventsEditor(::deleteAllReferencesToEvent, editedScenario)
    private val imageEventGroupsEditor = ImageEventGroupsEditor(editedScenario)
    private val triggerEventGroupsEditor = TriggerEventGroupsEditor(editedScenario)

    val currentEventEditor: StateFlow<EventsEditor<Event, Condition>?> = _currentEventEditor
    val currentEventGroupEditor: StateFlow<EventGroupsEditor<out Condition>?> = _currentEventGroupEditor

    val allEditedEvents: Flow<List<Event>> =
        combine(imageEventsEditor.allEditedItems, triggerEventsEditor.allEditedItems) { imageEvent, triggerEvents ->
            buildList {
                addAll(imageEvent)
                addAll(triggerEvents)
            }
        }

    val allEditedEventGroups: Flow<List<EventGroup>> =
        combine(imageEventGroupsEditor.allEditedItems, triggerEventGroupsEditor.allEditedItems) { imageGroups, triggerGroups ->
            buildList {
                addAll(imageGroups)
                addAll(triggerGroups)
            }
        }

    val editedEvent: Flow<Event?> = currentEventEditor.flatMapLatest { eventsEditor ->
        eventsEditor?.editedItem ?: flowOf(null)
    }

    val editedEventGroup: Flow<EventGroup?> = currentEventGroupEditor.flatMapLatest { groupEditor ->
        groupEditor?.editedItem ?: flowOf(null)
    }

    val editedImageEventListState: Flow<EditedListState<ImageEvent>> = imageEventsEditor.listState
    val editedImageEventState: Flow<EditedElementState<ImageEvent>> = imageEventsEditor.editedItemState

    val editedTriggerEventListState: Flow<EditedListState<TriggerEvent>> = triggerEventsEditor.listState
    val editedTriggerEventState: Flow<EditedElementState<TriggerEvent>> = triggerEventsEditor.editedItemState

    val editedImageEventGroupsListState: Flow<EditedListState<EventGroup>> = imageEventGroupsEditor.listState
    val editedTriggerEventGroupsListState: Flow<EditedListState<EventGroup>> = triggerEventGroupsEditor.listState
    val editedImageEventGroupState: Flow<EditedElementState<EventGroup>> = imageEventGroupsEditor.editedItemState
    val editedTriggerEventGroupState: Flow<EditedElementState<EventGroup>> = triggerEventGroupsEditor.editedItemState

    fun startEdition(
        scenario: Scenario,
        imageEvents: List<ImageEvent>,
        triggerEvents: List<TriggerEvent>,
        imageEventGroups: List<EventGroup>,
        triggerEventGroups: List<EventGroup>,
    ) {
        referenceScenario.value = scenario
        _editedScenario.value = scenario

        imageEventsEditor.startEdition(imageEvents)
        triggerEventsEditor.startEdition(triggerEvents)
        imageEventGroupsEditor.startEdition(imageEventGroups)
        triggerEventGroupsEditor.startEdition(triggerEventGroups)
    }

    @Suppress("UNCHECKED_CAST")
    fun startEventEdition(event: Event) {
        stopEventGroupEdition()
        _currentEventEditor.value = when (event) {
            is ImageEvent -> imageEventsEditor
            is TriggerEvent -> triggerEventsEditor
        } as EventsEditor<Event, Condition>

        currentEventEditor.value?.startItemEdition(event)
    }

    fun updateEditedEvent(event: Event) =
        currentEventEditor.value?.updateEditedItem(event)

    fun updateActionsOrder(actions: List<Action>) =
        currentEventEditor.value?.actionsEditor?.updateList(actions)

    fun updateImageConditionsOrder(imageConditions: List<ImageCondition>) =
        currentEventEditor.value?.conditionsEditor?.updateList(imageConditions)

    fun upsertEditedEvent() =
        currentEventEditor.value?.upsertEditedItem()

    fun deleteEditedEvent() =
        currentEventEditor.value?.deleteEditedItem()

    fun stopEventEdition() {
        currentEventEditor.value?.stopItemEdition()
        _currentEventEditor.value = null
    }

    fun startEventGroupEdition(group: EventGroup) {
        stopEventEdition()
        val editor = when (group.eventType) {
            com.buzbuz.smartautoclicker.core.domain.model.event.GroupEventType.IMAGE -> imageEventGroupsEditor
            com.buzbuz.smartautoclicker.core.domain.model.event.GroupEventType.TRIGGER -> triggerEventGroupsEditor
        }
        _currentEventGroupEditor.value = editor
        val groupToEdit = editor.editedList.value?.find { it.id == group.id } ?: group
        editor.startItemEdition(groupToEdit)
    }

    fun updateEditedEventGroup(group: EventGroup) =
        currentEventGroupEditor.value?.updateEditedItem(group)

    fun upsertEditedEventGroup() =
        currentEventGroupEditor.value?.upsertEditedItem()

    fun deleteEditedEventGroup() {
        val group = currentEventGroupEditor.value?.editedItem?.value ?: return
        unassignEventsFromGroup(group.id)
        promoteChildGroups(group)
        currentEventGroupEditor.value?.deleteEditedItem()
    }

    private fun promoteChildGroups(deletedGroup: EventGroup) {
        val editor = currentEventGroupEditor.value ?: return
        val groups = editor.editedList.value ?: return
        editor.updateList(
            groups.map { group ->
                if (group.parentGroupId == deletedGroup.id) {
                    group.copy(parentGroupId = deletedGroup.parentGroupId)
                } else {
                    group
                }
            },
        )
    }

    fun stopEventGroupEdition() {
        currentEventGroupEditor.value?.stopItemEdition()
        _currentEventGroupEditor.value = null
    }

    @Suppress("UNCHECKED_CAST")
    fun startGroupConditionEdition(condition: Condition) {
        when (val editor = currentEventGroupEditor.value) {
            is ImageEventGroupsEditor ->
                editor.conditionsEditor.startItemEdition(condition as ImageCondition)
            is TriggerEventGroupsEditor ->
                editor.conditionsEditor.startItemEdition(condition as TriggerCondition)
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun updateEditedGroupCondition(condition: Condition) {
        when (val editor = currentEventGroupEditor.value) {
            is ImageEventGroupsEditor -> editor.conditionsEditor.updateEditedItem(condition as ImageCondition)
            is TriggerEventGroupsEditor -> editor.conditionsEditor.updateEditedItem(condition as TriggerCondition)
        }
    }

    fun upsertEditedGroupCondition() =
        currentEventGroupEditor.value?.conditionsEditor?.upsertEditedItem()

    fun deleteEditedGroupCondition() =
        currentEventGroupEditor.value?.conditionsEditor?.deleteEditedItem()

    fun stopGroupConditionEdition() =
        currentEventGroupEditor.value?.conditionsEditor?.stopItemEdition()

    fun updateImageGroupConditionsOrder(conditions: List<ImageCondition>) =
        (currentEventGroupEditor.value as? ImageEventGroupsEditor)?.conditionsEditor?.updateList(conditions)

    fun updateTriggerGroupConditionsOrder(conditions: List<TriggerCondition>) =
        (currentEventGroupEditor.value as? TriggerEventGroupsEditor)?.conditionsEditor?.updateList(conditions)

    fun updateImageEventGroupsOrder(newGroups: List<EventGroup>) {
        imageEventGroupsEditor.updateList(newGroups)
    }

    fun updateTriggerEventGroupsOrder(newGroups: List<EventGroup>) {
        triggerEventGroupsEditor.updateList(newGroups)
    }

    fun getAllEditedEventGroups(): List<EventGroup> = buildList {
        addAll(imageEventGroupsEditor.getAllEditedItems())
        addAll(triggerEventGroupsEditor.getAllEditedItems())
    }

    private fun unassignEventsFromGroup(groupId: com.buzbuz.smartautoclicker.core.base.identifier.Identifier) {
        imageEventsEditor.editedList.value?.let { events ->
            imageEventsEditor.updateList(events.map { event ->
                if (event.groupId == groupId) event.copy(groupId = null) else event
            })
        }
        triggerEventsEditor.editedList.value?.let { events ->
            triggerEventsEditor.updateList(events.map { event ->
                if (event.groupId == groupId) event.copy(groupId = null) else event
            })
        }
    }

    fun stopEdition() {
        imageEventsEditor.stopEdition()
        triggerEventsEditor.stopEdition()
        imageEventGroupsEditor.stopEdition()
        triggerEventGroupsEditor.stopEdition()

        referenceScenario.value = null
        _editedScenario.value = null
    }

    fun updateEditedScenario(item: Scenario) {
        _editedScenario.value ?: return
        _editedScenario.value = item
    }

    fun updateImageEventsOrder(newEvents: List<ImageEvent>) {
        imageEventsEditor.updateList(newEvents)
    }

    fun updateTriggerEventsOrder(newEvents: List<TriggerEvent>) {
        triggerEventsEditor.updateList(newEvents)
    }

    fun updateImageEventsAndGroupsOrder(events: List<ImageEvent>, groups: List<EventGroup>) {
        imageEventsEditor.updateList(events)
        imageEventGroupsEditor.updateList(groups)
    }

    fun updateTriggerEventsAndGroupsOrder(events: List<TriggerEvent>, groups: List<EventGroup>) {
        triggerEventsEditor.updateList(events)
        triggerEventGroupsEditor.updateList(groups)
    }

    fun getEditedImageEvents(): List<ImageEvent> =
        imageEventsEditor.editedList.value ?: emptyList()

    fun getEditedImageEventGroups(): List<EventGroup> =
        imageEventGroupsEditor.editedList.value ?: emptyList()

    fun getEditedTriggerEvents(): List<TriggerEvent> =
        triggerEventsEditor.editedList.value ?: emptyList()

    fun getEditedTriggerEventGroups(): List<EventGroup> =
        triggerEventGroupsEditor.editedList.value ?: emptyList()

    fun getEditedImageRootListCount(): Int =
        getEditedImageEvents().count { it.groupId == null } +
            getEditedImageEventGroups().count { it.parentGroupId == null }

    fun getEditedTriggerRootListCount(): Int =
        getEditedTriggerEvents().count { it.groupId == null } +
            getEditedTriggerEventGroups().count { it.parentGroupId == null }

    fun getAllEditedEvents(): List<Event> = buildList {
        imageEventsEditor.editedList.value?.let { addAll(it) }
        triggerEventsEditor.editedList.value?.let { addAll(it) }
    }

    fun getEditedEvent(): Event? =
        currentEventEditor.value?.editedItem?.value

    fun getEditedImageEventsCount(): Int =
        imageEventsEditor.editedList.value?.size ?: 0

    private fun deleteAllReferencesToEvent(event: Event) {
        imageEventsEditor.deleteAllEventToggleReferencing(event)
        triggerEventsEditor.deleteAllEventToggleReferencing(event)
    }
}