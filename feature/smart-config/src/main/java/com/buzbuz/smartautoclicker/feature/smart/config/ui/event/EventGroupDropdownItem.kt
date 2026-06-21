/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.feature.smart.config.ui.event

import com.buzbuz.smartautoclicker.core.base.identifier.Identifier
import com.buzbuz.smartautoclicker.core.ui.bindings.dropdown.DropdownItem
import com.buzbuz.smartautoclicker.feature.smart.config.R

/** Dropdown entry for assigning an event to a group, or to none. */
data class EventGroupDropdownItem(
    val groupId: Identifier?,
    private val label: String,
) : DropdownItem(
    title = R.string.dropdown_event_group_none,
    titleText = label,
)
