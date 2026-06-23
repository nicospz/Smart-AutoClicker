/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.feature.dumb.config.ui.actions.tasker

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.buzbuz.smartautoclicker.core.tasker.TaskerTaskItem
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setDescription
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setOnClickListener
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setTitle
import com.buzbuz.smartautoclicker.feature.dumb.config.databinding.ItemTaskerTaskBinding

class DumbTaskerTaskPickerAdapter(
    private val onTaskSelected: (String) -> Unit,
) : ListAdapter<TaskerTaskItem, DumbTaskerTaskPickerAdapter.TaskViewHolder>(TaskDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder =
        TaskViewHolder(
            ItemTaskerTaskBinding.inflate(LayoutInflater.from(parent.context), parent, false),
            onTaskSelected,
        )

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class TaskViewHolder(
        private val binding: ItemTaskerTaskBinding,
        private val onTaskSelected: (String) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(task: TaskerTaskItem) {
            binding.fieldSelector.apply {
                setTitle(task.name)
                val projectName = task.projectName
                if (projectName.isNullOrBlank()) {
                    setDescription(null)
                } else {
                    setDescription(projectName)
                }
                setOnClickListener { onTaskSelected(task.name) }
            }
        }
    }
}

private class TaskDiffCallback : DiffUtil.ItemCallback<TaskerTaskItem>() {
    override fun areItemsTheSame(oldItem: TaskerTaskItem, newItem: TaskerTaskItem): Boolean =
        oldItem.name == newItem.name

    override fun areContentsTheSame(oldItem: TaskerTaskItem, newItem: TaskerTaskItem): Boolean =
        oldItem == newItem
}
