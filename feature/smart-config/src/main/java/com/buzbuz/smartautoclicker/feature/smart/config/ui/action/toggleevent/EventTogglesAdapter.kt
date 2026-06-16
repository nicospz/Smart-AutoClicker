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
package com.buzbuz.smartautoclicker.feature.smart.config.ui.action.toggleevent

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup

import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

import com.buzbuz.smartautoclicker.core.base.identifier.Identifier
import com.buzbuz.smartautoclicker.core.domain.model.action.ToggleEvent
import com.buzbuz.smartautoclicker.core.ui.bindings.buttons.MultiStateButtonConfig
import com.buzbuz.smartautoclicker.core.ui.bindings.buttons.setChecked
import com.buzbuz.smartautoclicker.core.ui.bindings.buttons.setOnCheckedListener
import com.buzbuz.smartautoclicker.core.ui.bindings.buttons.setup
import com.buzbuz.smartautoclicker.feature.smart.config.R
import com.buzbuz.smartautoclicker.feature.smart.config.databinding.ItemAddPrefixToggleBinding
import com.buzbuz.smartautoclicker.feature.smart.config.databinding.ItemEventPrefixToggleBinding
import com.buzbuz.smartautoclicker.feature.smart.config.databinding.ItemEventToggleBinding
import com.buzbuz.smartautoclicker.core.ui.databinding.ItemListHeaderBinding


class EventToggleAdapter(
    private val onEventToggleStateChanged: (Identifier, ToggleEvent.ToggleType?) -> Unit,
    private val onPrefixToggleStateChanged: (Identifier, ToggleEvent.ToggleType?) -> Unit,
    private val onPrefixToggleTextChanged: (Identifier, String) -> Unit,
    private val onAddPrefixToggleClicked: () -> Unit,
    private val onRemovePrefixToggleClicked: (Identifier) -> Unit,
) : ListAdapter<EventTogglesListItem, RecyclerView.ViewHolder>(ItemEventToggleDiffUtilCallback) {

    override fun getItemViewType(position: Int): Int =
        when (getItem(position)) {
            is EventTogglesListItem.Header -> R.layout.item_list_header
            is EventTogglesListItem.PrefixItem -> R.layout.item_event_prefix_toggle
            is EventTogglesListItem.AddPrefixButton -> R.layout.item_add_prefix_toggle
            is EventTogglesListItem.Item -> R.layout.item_event_toggle
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        when (viewType) {
            R.layout.item_list_header ->
                HeaderViewHolder(
                    viewBinding = ItemListHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false),
                )

            R.layout.item_event_prefix_toggle ->
                PrefixItemViewHolder(
                    viewBinding = ItemEventPrefixToggleBinding.inflate(LayoutInflater.from(parent.context), parent, false),
                    onPrefixToggleStateChanged = onPrefixToggleStateChanged,
                    onPrefixToggleTextChanged = onPrefixToggleTextChanged,
                    onRemovePrefixToggleClicked = onRemovePrefixToggleClicked,
                )

            R.layout.item_add_prefix_toggle ->
                AddPrefixButtonViewHolder(
                    viewBinding = ItemAddPrefixToggleBinding.inflate(LayoutInflater.from(parent.context), parent, false),
                    onAddPrefixToggleClicked = onAddPrefixToggleClicked,
                )

            R.layout.item_event_toggle ->
                ItemViewHolder(
                    viewBinding = ItemEventToggleBinding.inflate(LayoutInflater.from(parent.context), parent, false),
                    onEventToggleStateChanged = onEventToggleStateChanged,
                )

            else -> throw IllegalArgumentException("Unsupported view type $viewType")
        }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is HeaderViewHolder -> holder.onBind(getItem(position) as EventTogglesListItem.Header)
            is PrefixItemViewHolder -> holder.onBind(getItem(position) as EventTogglesListItem.PrefixItem)
            is AddPrefixButtonViewHolder -> holder.onBind()
            is ItemViewHolder -> holder.onBind(getItem(position) as EventTogglesListItem.Item)
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        when (holder) {
            is ItemViewHolder -> holder.onUnbind()
            is PrefixItemViewHolder -> holder.onUnbind()
        }
    }
}

/** DiffUtil Callback comparing two ActionItem when updating the [EventToggleAdapter] list. */
object ItemEventToggleDiffUtilCallback: DiffUtil.ItemCallback<EventTogglesListItem>() {
    override fun areItemsTheSame(oldItem: EventTogglesListItem, newItem: EventTogglesListItem): Boolean =
          when {
              oldItem is EventTogglesListItem.Header && newItem is EventTogglesListItem.Header ->
                  oldItem.title == newItem.title
              oldItem is EventTogglesListItem.PrefixItem && newItem is EventTogglesListItem.PrefixItem ->
                  oldItem.prefixToggleId == newItem.prefixToggleId
              oldItem is EventTogglesListItem.AddPrefixButton && newItem is EventTogglesListItem.AddPrefixButton ->
                  true
              oldItem is EventTogglesListItem.Item && newItem is EventTogglesListItem.Item ->
                  oldItem.eventId == newItem.eventId
              else -> false
          }

    override fun areContentsTheSame(oldItem: EventTogglesListItem, newItem: EventTogglesListItem): Boolean =
        oldItem == newItem
}


/**
 * View holder displaying an action in the [EventToggleAdapter].
 * @param viewBinding the view binding for this item.
 */
class HeaderViewHolder(
    private val viewBinding: ItemListHeaderBinding,
) : RecyclerView.ViewHolder(viewBinding.root) {

    fun onBind(item: EventTogglesListItem.Header) {
        viewBinding.textHeader.text = item.title
    }
}

class PrefixItemViewHolder(
    private val viewBinding: ItemEventPrefixToggleBinding,
    private val onPrefixToggleStateChanged: (Identifier, ToggleEvent.ToggleType?) -> Unit,
    private val onPrefixToggleTextChanged: (Identifier, String) -> Unit,
    private val onRemovePrefixToggleClicked: (Identifier) -> Unit,
) : RecyclerView.ViewHolder(viewBinding.root) {

    private var boundPrefixToggleId: Identifier? = null
    private var textWatcher: TextWatcher? = null

    init {
        viewBinding.toggleTypeButton.setup(TOGGLE_BUTTONS_CONFIG)
    }

    fun onBind(item: EventTogglesListItem.PrefixItem) {
        boundPrefixToggleId = item.prefixToggleId

        viewBinding.inputPrefix.removeTextChangedListener(textWatcher)
        if (viewBinding.inputPrefix.text?.toString() != item.prefix) {
            viewBinding.inputPrefix.setText(item.prefix)
        }

        textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                onPrefixToggleTextChanged(item.prefixToggleId, s?.toString() ?: "")
            }
        }
        viewBinding.inputPrefix.addTextChangedListener(textWatcher)

        viewBinding.toggleTypeButton.setChecked(item.toggleState.toButtonIndex())
        viewBinding.toggleTypeButton.setOnCheckedListener { newChecked ->
            onPrefixToggleStateChanged(item.prefixToggleId, newChecked.toToggleType())
        }

        viewBinding.buttonRemove.setOnClickListener {
            onRemovePrefixToggleClicked(item.prefixToggleId)
        }
    }

    fun onUnbind() {
        viewBinding.inputPrefix.removeTextChangedListener(textWatcher)
        textWatcher = null
        viewBinding.toggleTypeButton.setOnCheckedListener(null)
        viewBinding.buttonRemove.setOnClickListener(null)
        boundPrefixToggleId = null
    }
}

class AddPrefixButtonViewHolder(
    private val viewBinding: ItemAddPrefixToggleBinding,
    private val onAddPrefixToggleClicked: () -> Unit,
) : RecyclerView.ViewHolder(viewBinding.root) {

    fun onBind() {
        viewBinding.buttonAddPrefix.setOnClickListener { onAddPrefixToggleClicked() }
    }
}

/**
 * View holder displaying an action in the [EventToggleAdapter].
 * @param viewBinding the view binding for this item.
 */
class ItemViewHolder(
    private val viewBinding: ItemEventToggleBinding,
    private val onEventToggleStateChanged: (Identifier, ToggleEvent.ToggleType?) -> Unit,
) : RecyclerView.ViewHolder(viewBinding.root) {

    init {
        viewBinding.toggleTypeButton.setup(TOGGLE_BUTTONS_CONFIG)
    }

    fun onBind(item: EventTogglesListItem.Item) {
        viewBinding.apply {
            eventName.text = item.eventName
            textActionsCount.text = item.actionsCount.toString()
            textConditionCount.text = item.conditionsCount.toString()

            toggleTypeButton.setChecked(item.toggleState.toButtonIndex())

            toggleTypeButton.setOnCheckedListener { newChecked ->
                onEventToggleStateChanged(item.eventId, newChecked.toToggleType())
            }
        }
    }

    fun onUnbind() {
        viewBinding.toggleTypeButton.setOnCheckedListener(null)
    }
}

private val TOGGLE_BUTTONS_CONFIG = MultiStateButtonConfig(
    icons = listOf(R.drawable.ic_confirm, R.drawable.ic_invert, R.drawable.ic_cancel),
    selectionRequired = false,
    singleSelection = true,
)

private fun ToggleEvent.ToggleType?.toButtonIndex(): Int? = when (this) {
    ToggleEvent.ToggleType.ENABLE -> BUTTON_ENABLE_EVENT
    ToggleEvent.ToggleType.TOGGLE -> BUTTON_TOGGLE_EVENT
    ToggleEvent.ToggleType.DISABLE -> BUTTON_DISABLE_EVENT
    null -> null
}

private fun Int?.toToggleType(): ToggleEvent.ToggleType? = when (this) {
    BUTTON_ENABLE_EVENT -> ToggleEvent.ToggleType.ENABLE
    BUTTON_TOGGLE_EVENT -> ToggleEvent.ToggleType.TOGGLE
    BUTTON_DISABLE_EVENT -> ToggleEvent.ToggleType.DISABLE
    else -> null
}
