/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.feature.smart.config.ui.eventgroup

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buzbuz.smartautoclicker.core.bitmaps.BitmapRepository
import com.buzbuz.smartautoclicker.core.domain.model.ConditionOperator
import com.buzbuz.smartautoclicker.core.domain.model.condition.ImageCondition
import com.buzbuz.smartautoclicker.core.domain.model.condition.TriggerCondition
import com.buzbuz.smartautoclicker.core.domain.model.event.EventGroup
import com.buzbuz.smartautoclicker.core.domain.model.event.GroupEventType
import com.buzbuz.smartautoclicker.core.domain.model.event.descendantIds
import com.buzbuz.smartautoclicker.core.domain.model.event.hierarchicalName
import com.buzbuz.smartautoclicker.core.domain.model.event.visitInListOrder
import com.buzbuz.smartautoclicker.feature.smart.config.R
import com.buzbuz.smartautoclicker.feature.smart.config.domain.EditionRepository
import com.buzbuz.smartautoclicker.feature.smart.config.ui.common.model.condition.UiImageCondition
import com.buzbuz.smartautoclicker.feature.smart.config.ui.common.model.condition.getIconRes
import com.buzbuz.smartautoclicker.feature.smart.config.ui.common.model.condition.toUiImageCondition
import com.buzbuz.smartautoclicker.feature.smart.config.ui.event.EventChildrenItem
import com.buzbuz.smartautoclicker.feature.smart.config.ui.event.EventGroupDropdownItem
import com.buzbuz.smartautoclicker.feature.smart.config.utils.getImageConditionBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import javax.inject.Inject

@OptIn(FlowPreview::class)
class EventGroupDialogViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bitmapRepository: BitmapRepository,
    private val editionRepository: EditionRepository,
) : ViewModel() {

    private val configuredGroup = editionRepository.editionState.editedEventGroupState
        .mapNotNull { it.value }

    private val editedGroupHasChanged: StateFlow<Boolean> =
        editionRepository.editionState.editedEventGroupState
            .map { it.hasChanged }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val groupCanBeSaved: Flow<Boolean> = editionRepository.editionState.editedEventGroupState
        .map { it.canBeSaved }

    val isEditingEventGroup: Flow<Boolean> = editionRepository.isEditingEventGroup
        .distinctUntilChanged()
        .debounce(1000)

    val groupName: Flow<String?> = configuredGroup
        .map { it.name }
        .take(1)

    val groupNameError: Flow<Boolean> = configuredGroup
        .map { it.name.isEmpty() }

    val parentGroupDropdownItems: Flow<List<EventGroupDropdownItem>> = combine(
        editionRepository.editionState.editedEventGroupState,
        editionRepository.editionState.editedImageEventGroupsState,
        editionRepository.editionState.editedTriggerEventGroupsState,
    ) { groupState, imageGroupsState, triggerGroupsState ->
        val group = groupState.value ?: return@combine emptyList()
        val allGroups = when (group.eventType) {
            GroupEventType.IMAGE -> imageGroupsState.value ?: emptyList()
            GroupEventType.TRIGGER -> triggerGroupsState.value ?: emptyList()
        }
        buildParentGroupDropdownItems(group, allGroups)
    }

    val isParentGroupDropdownVisible: Flow<Boolean> = parentGroupDropdownItems.map { it.size > 1 }

    val selectedParentGroup: Flow<EventGroupDropdownItem> = combine(
        configuredGroup,
        parentGroupDropdownItems,
    ) { group, items ->
        items.find { it.groupId == group.parentGroupId } ?: items.first()
    }.distinctUntilChanged()

    val imageConditions: Flow<List<UiImageCondition>> =
        combine(
            configuredGroup.filter { it.eventType == GroupEventType.IMAGE },
            editionRepository.editionState.editedEventImageConditionsState,
        ) { group, imageConditionsState ->
            val conditions: List<ImageCondition> = imageConditionsState.value
                ?: group.conditions.filterIsInstance<ImageCondition>()
            conditions.mapIndexed { index, imageCondition ->
                val inError = if (imageConditionsState.value != null) {
                    imageConditionsState.itemValidity.getOrElse(index) { false }.not()
                } else {
                    imageCondition.isComplete().not()
                }
                imageCondition.toUiImageCondition(
                    context = context,
                    shortThreshold = true,
                    inError = inError,
                )
            }
        }

    val triggerConditionsDescription: Flow<List<EventChildrenItem>> =
        combine(
            configuredGroup.filter { it.eventType == GroupEventType.TRIGGER },
            editionRepository.editionState.editedEventTriggerConditionsState,
        ) { group, conditionsListState ->
            val conditions: List<TriggerCondition> = conditionsListState.value
                ?: group.conditions.filterIsInstance<TriggerCondition>()
            conditions.map { condition ->
                EventChildrenItem(
                    iconRes = condition.getIconRes(),
                    isInError = !condition.isComplete(),
                )
            }
        }

    val conditionOperator: Flow<Int> = configuredGroup
        .map { group -> group.conditionOperator }

    fun isConfiguringScreenGroup(): Boolean =
        editionRepository.editionState.getEditedEventGroup()?.eventType == GroupEventType.IMAGE

    fun hasUnsavedModifications(): Boolean =
        editedGroupHasChanged.value

    fun getConditionBitmap(condition: ImageCondition, onBitmapLoaded: (Bitmap?) -> Unit): Job =
        getImageConditionBitmap(bitmapRepository, condition, onBitmapLoaded)

    fun setGroupName(newName: String) {
        updateEditedGroup { oldValue -> oldValue.copy(name = newName) }
    }

    fun setParentGroup(item: EventGroupDropdownItem) {
        updateEditedGroup { oldValue -> oldValue.copy(parentGroupId = item.groupId) }
    }

    fun setConditionOperator(@ConditionOperator operator: Int) {
        updateEditedGroup { oldValue -> oldValue.copy(conditionOperator = operator) }
    }

    private fun buildParentGroupDropdownItems(
        editedGroup: EventGroup,
        allGroups: List<EventGroup>,
    ): List<EventGroupDropdownItem> {
        val excludedIds = allGroups.descendantIds(editedGroup.id) + editedGroup.id.databaseId
        return buildList {
            add(
                EventGroupDropdownItem(
                    groupId = null,
                    label = context.getString(R.string.dropdown_parent_group_none),
                ),
            )
            allGroups
                .filter { it.id.databaseId !in excludedIds }
                .visitInListOrder { group ->
                    add(
                        EventGroupDropdownItem(
                            groupId = group.id,
                            label = allGroups.hierarchicalName(group.id),
                        ),
                    )
                }
        }
    }

    private fun updateEditedGroup(block: (EventGroup) -> EventGroup) {
        val group = editionRepository.editionState.getEditedEventGroup() ?: return
        editionRepository.updateEditedEventGroup(block(group))
    }
}
