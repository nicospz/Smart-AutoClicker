/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.feature.smart.config.ui.scenario.imageevents

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.buzbuz.smartautoclicker.core.domain.model.event.EventGroup
import com.buzbuz.smartautoclicker.core.domain.model.event.ImageEvent
import com.buzbuz.smartautoclicker.core.ui.databinding.ItemListHeaderBinding
import com.buzbuz.smartautoclicker.feature.smart.config.R
import com.buzbuz.smartautoclicker.feature.smart.config.databinding.ItemEventGroupHeaderBinding
import com.buzbuz.smartautoclicker.feature.smart.config.databinding.ItemImageEventBinding
import com.buzbuz.smartautoclicker.feature.smart.config.ui.common.bindings.bind
import com.buzbuz.smartautoclicker.feature.smart.config.ui.scenario.GroupedListReorder

class ImageEventGroupedListAdapter(
    private val onEventClicked: (ImageEvent) -> Unit,
    private val onEventIgnoreChanged: (ImageEvent, Boolean) -> Unit,
    private val onGroupClicked: (EventGroup) -> Unit,
    private val onGroupExpandClicked: (EventGroup) -> Unit,
    private val onAddGroupClicked: () -> Unit,
    private val onReorderFinished: (List<ImageEventListItem>) -> Unit,
    private val onEventViewBound: (Int, View?) -> Unit,
) : ListAdapter<ImageEventListItem, RecyclerView.ViewHolder>(DiffCallback) {

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is ImageEventListItem.AddGroupAction -> VIEW_TYPE_ADD_GROUP
        is ImageEventListItem.GroupHeader -> VIEW_TYPE_GROUP
        is ImageEventListItem.EventItem -> VIEW_TYPE_EVENT
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
                ItemImageEventBinding.inflate(LayoutInflater.from(parent.context), parent, false),
            )
            else -> throw IllegalArgumentException("Unknown view type $viewType")
        }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ImageEventListItem.AddGroupAction -> (holder as AddGroupViewHolder).bind(onAddGroupClicked)
            is ImageEventListItem.GroupHeader -> (holder as GroupViewHolder).bind(item, onGroupClicked, onGroupExpandClicked)
            is ImageEventListItem.EventItem -> {
                (holder as EventViewHolder).bind(item, onEventClicked, onEventIgnoreChanged)
                onEventViewBound(position, holder.itemView)
            }
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        onEventViewBound(holder.bindingAdapterPosition, null)
        super.onViewRecycled(holder)
    }

    fun moveItem(from: Int, to: Int) {
        val newList = currentList.toMutableList()
        val fromItem = newList[from]
        val blockSize = if (fromItem is ImageEventListItem.GroupHeader) {
            GroupedListReorder.imageSubtreeSize(newList, from)
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
            item: ImageEventListItem.GroupHeader,
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
        private val binding: ItemImageEventBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            item: ImageEventListItem.EventItem,
            onEventClicked: (ImageEvent) -> Unit,
            onEventIgnoreChanged: (ImageEvent, Boolean) -> Unit,
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
            binding.bind(item.uiEvent, item.canReorder, onEventClicked, onEventIgnoreChanged)
        }
    }

    companion object {
        private const val VIEW_TYPE_ADD_GROUP = 0
        private const val VIEW_TYPE_GROUP = 1
        private const val VIEW_TYPE_EVENT = 2

        private val DiffCallback = object : DiffUtil.ItemCallback<ImageEventListItem>() {
            override fun areItemsTheSame(oldItem: ImageEventListItem, newItem: ImageEventListItem): Boolean =
                oldItem.stableId == newItem.stableId

            override fun areContentsTheSame(oldItem: ImageEventListItem, newItem: ImageEventListItem): Boolean =
                oldItem == newItem
        }
    }
}

class ImageEventGroupedReorderTouchHelper(
    private val adapterProvider: () -> ImageEventGroupedListAdapter,
) : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {

    private var isDragging = false

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder,
    ): Boolean {
        val adapter = adapterProvider()
        if (adapter.currentList.none { it is ImageEventListItem.AddGroupAction }) return false
        val from = viewHolder.bindingAdapterPosition
        val to = target.bindingAdapterPosition
        if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false

        val fromItem = adapter.currentList.getOrNull(from) ?: return false
        val toItem = adapter.currentList.getOrNull(to) ?: return false
        if (!GroupedListReorder.canReorderImageItems(fromItem, toItem)) return false

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
