/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.feature.smart.config.ui.scenario.triggerevents

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.buzbuz.smartautoclicker.core.domain.model.event.EventGroup
import com.buzbuz.smartautoclicker.core.domain.model.event.TriggerEvent
import com.buzbuz.smartautoclicker.core.ui.databinding.ItemListHeaderBinding
import com.buzbuz.smartautoclicker.feature.smart.config.R
import com.buzbuz.smartautoclicker.feature.smart.config.databinding.ItemEventGroupHeaderBinding
import com.buzbuz.smartautoclicker.feature.smart.config.databinding.ItemTriggerEventBinding
import com.buzbuz.smartautoclicker.feature.smart.config.ui.common.bindings.bind
import com.buzbuz.smartautoclicker.feature.smart.config.ui.scenario.GroupedListReorder

class TriggerEventGroupedListAdapter(
    private val onEventClicked: (TriggerEvent) -> Unit,
    private val onEventIgnoreChanged: (TriggerEvent, Boolean) -> Unit,
    private val onGroupClicked: (EventGroup) -> Unit,
    private val onGroupExpandClicked: (EventGroup) -> Unit,
    private val onAddGroupClicked: () -> Unit,
    private val onReorderFinished: (List<TriggerEventListItem>) -> Unit,
) : ListAdapter<TriggerEventListItem, RecyclerView.ViewHolder>(DiffCallback) {

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is TriggerEventListItem.AddGroupAction -> VIEW_TYPE_ADD_GROUP
        is TriggerEventListItem.GroupHeader -> VIEW_TYPE_GROUP
        is TriggerEventListItem.EventItem -> VIEW_TYPE_EVENT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        when (viewType) {
            VIEW_TYPE_ADD_GROUP -> AddGroupViewHolder(
                ItemListHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false),
            )
            VIEW_TYPE_GROUP -> GroupViewHolder(
                ItemEventGroupHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false),
            )
            VIEW_TYPE_EVENT -> EventViewHolder(
                ItemTriggerEventBinding.inflate(LayoutInflater.from(parent.context), parent, false),
            )
            else -> throw IllegalArgumentException("Unknown view type $viewType")
        }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is TriggerEventListItem.AddGroupAction -> (holder as AddGroupViewHolder).bind(onAddGroupClicked)
            is TriggerEventListItem.GroupHeader -> (holder as GroupViewHolder).bind(item, onGroupClicked, onGroupExpandClicked)
            is TriggerEventListItem.EventItem -> (holder as EventViewHolder).bind(
                item,
                onEventClicked,
                onEventIgnoreChanged,
            )
        }
    }

    fun moveItem(from: Int, to: Int) {
        val newList = currentList.toMutableList()
        val fromItem = newList[from]
        val blockSize = if (fromItem is TriggerEventListItem.GroupHeader) {
            GroupedListReorder.triggerSubtreeSize(newList, from)
        } else {
            1
        }
        val block = newList.subList(from, from + blockSize).toList()
        newList.subList(from, from + blockSize).clear()

        var targetIndex = to
        if (from < to) targetIndex -= blockSize - 1
        targetIndex = targetIndex.coerceIn(0, newList.size)

        newList.addAll(targetIndex, block)
        submitList(newList)
    }

    fun notifyMoveFinished() {
        onReorderFinished(currentList)
    }

    private class AddGroupViewHolder(
        private val binding: ItemListHeaderBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(onClick: () -> Unit) {
            binding.textHeader.text = binding.root.context.getString(R.string.button_add_event_group)
            binding.root.setOnClickListener { onClick() }
        }
    }

    private class GroupViewHolder(
        private val binding: ItemEventGroupHeaderBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            item: TriggerEventListItem.GroupHeader,
            onGroupClicked: (EventGroup) -> Unit,
            onExpandClicked: (EventGroup) -> Unit,
        ) = binding.apply {
            val indent = root.resources.getDimensionPixelSize(
                com.buzbuz.smartautoclicker.core.ui.R.dimen.margin_horizontal_default,
            ) * item.depth
            root.setPaddingRelative(
                indent,
                root.paddingTop,
                root.paddingEnd,
                root.paddingBottom,
            )
            textName.text = item.group.name
            textConditionsCount.text = item.group.conditions.size.toString()
            btnExpand.setImageResource(
                if (item.expanded) com.buzbuz.smartautoclicker.core.ui.R.drawable.ic_chevron_up
                else com.buzbuz.smartautoclicker.core.ui.R.drawable.ic_chevron_down,
            )
            btnExpand.setOnClickListener { onExpandClicked(item.group) }
            root.setOnClickListener { onGroupClicked(item.group) }
        }
    }

    private class EventViewHolder(
        private val binding: ItemTriggerEventBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            item: TriggerEventListItem.EventItem,
            onEventClicked: (TriggerEvent) -> Unit,
            onEventIgnoreChanged: (TriggerEvent, Boolean) -> Unit,
        ) {
            val nestedPadding = binding.root.resources.getDimensionPixelSize(
                com.buzbuz.smartautoclicker.core.ui.R.dimen.margin_horizontal_default,
            )
            binding.root.setPaddingRelative(
                nestedPadding * item.nestingDepth,
                binding.root.paddingTop,
                binding.root.paddingEnd,
                binding.root.paddingBottom,
            )
            binding.bind(item.uiEvent, onEventClicked, onEventIgnoreChanged)
        }
    }

    companion object {
        private const val VIEW_TYPE_ADD_GROUP = 0
        private const val VIEW_TYPE_GROUP = 1
        private const val VIEW_TYPE_EVENT = 2

        private val DiffCallback = object : DiffUtil.ItemCallback<TriggerEventListItem>() {
            override fun areItemsTheSame(oldItem: TriggerEventListItem, newItem: TriggerEventListItem): Boolean =
                oldItem.stableId == newItem.stableId

            override fun areContentsTheSame(oldItem: TriggerEventListItem, newItem: TriggerEventListItem): Boolean =
                oldItem == newItem
        }
    }
}

class TriggerEventGroupedReorderTouchHelper(
    private val adapterProvider: () -> TriggerEventGroupedListAdapter,
) : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {

    private var isDragging = false

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder,
    ): Boolean {
        val adapter = adapterProvider()
        if (adapter.currentList.none { it is TriggerEventListItem.AddGroupAction }) return false
        val from = viewHolder.bindingAdapterPosition
        val to = target.bindingAdapterPosition
        if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false

        val fromItem = adapter.currentList.getOrNull(from) ?: return false
        val toItem = adapter.currentList.getOrNull(to) ?: return false
        if (!GroupedListReorder.canReorderTriggerItems(fromItem, toItem)) return false

        isDragging = true
        adapter.moveItem(from, to)
        return true
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        if (isDragging) {
            adapterProvider().notifyMoveFinished()
            isDragging = false
        }
    }
}
