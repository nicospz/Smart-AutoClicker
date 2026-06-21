/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.feature.smart.config.data.groups

import com.buzbuz.smartautoclicker.core.domain.model.condition.Condition
import com.buzbuz.smartautoclicker.core.domain.model.condition.ImageCondition
import com.buzbuz.smartautoclicker.core.domain.model.condition.TriggerCondition
import com.buzbuz.smartautoclicker.core.domain.model.event.EventGroup
import com.buzbuz.smartautoclicker.core.domain.model.event.GroupEventType
import com.buzbuz.smartautoclicker.core.domain.model.scenario.Scenario
import com.buzbuz.smartautoclicker.feature.smart.config.data.base.ListEditor

import kotlinx.coroutines.flow.StateFlow

internal class ImageEventGroupsEditor(
    parentItem: StateFlow<Scenario?>,
) : EventGroupsEditor<ImageCondition>(ImageCondition::class, parentItem)

internal class TriggerEventGroupsEditor(
    parentItem: StateFlow<Scenario?>,
) : EventGroupsEditor<TriggerCondition>(TriggerCondition::class, parentItem)

internal open class EventGroupsEditor<ChildCondition : Condition>(
    private val conditionClass: kotlin.reflect.KClass<out Condition>,
    parentItem: StateFlow<Scenario?>,
) : ListEditor<EventGroup, Scenario>(canBeEmpty = true, parentItem = parentItem) {

    val conditionsEditor: ListEditor<ChildCondition, EventGroup> = ListEditor(
        onListUpdated = ::onEditedGroupConditionsUpdated,
        canBeEmpty = false,
        parentItem = editedItem,
    )

    @Suppress("UNCHECKED_CAST")
    override fun startItemEdition(item: EventGroup) {
        super.startItemEdition(item)
        conditionsEditor.startEdition(
            (item.conditions as? List<ChildCondition>) ?: emptyList(),
        )
    }

    override fun stopItemEdition() {
        conditionsEditor.stopEdition()
        super.stopItemEdition()
    }

    private fun onEditedGroupConditionsUpdated(conditions: List<ChildCondition>) {
        val group = editedItem.value ?: return
        val updatedGroup = group.copy(conditions = conditions)
        updateEditedItem(updatedGroup)
        replaceEditedListItem(updatedGroup)
    }

    fun getEventType(): GroupEventType =
        when (conditionClass) {
            ImageCondition::class -> GroupEventType.IMAGE
            TriggerCondition::class -> GroupEventType.TRIGGER
            else -> throw IllegalStateException("Unsupported group condition type $conditionClass")
        }
}
