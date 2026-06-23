/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.feature.smart.config.ui.action.tasker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buzbuz.smartautoclicker.core.tasker.TaskerClient
import com.buzbuz.smartautoclicker.core.tasker.TaskerTaskItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.dinglisch.android.tasker.TaskerIntent
import javax.inject.Inject

class TaskerTaskPickerViewModel @Inject constructor(
    private val taskerClient: TaskerClient,
) : ViewModel() {

    private val _tasks = MutableStateFlow<List<TaskerTaskItem>>(emptyList())
    val tasks: StateFlow<List<TaskerTaskItem>> = _tasks.asStateFlow()

    private val _status = MutableStateFlow(TaskerIntent.Status.NOT_INSTALLED)
    val status: StateFlow<TaskerIntent.Status> = _status.asStateFlow()

    fun loadTasks() {
        viewModelScope.launch {
            _status.value = taskerClient.getStatus()
            _tasks.value = taskerClient.queryTasks()
        }
    }
}
