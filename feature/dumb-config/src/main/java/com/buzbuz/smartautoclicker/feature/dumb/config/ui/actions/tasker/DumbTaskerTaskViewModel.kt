/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.feature.dumb.config.ui.actions.tasker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buzbuz.smartautoclicker.core.dumb.domain.model.DumbAction
import com.buzbuz.smartautoclicker.core.tasker.TaskerClient
import com.buzbuz.smartautoclicker.core.tasker.TaskerVariable
import com.buzbuz.smartautoclicker.core.tasker.toJsonString
import com.buzbuz.smartautoclicker.core.tasker.toTaskerVariables
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.dinglisch.android.tasker.TaskerIntent
import javax.inject.Inject

class DumbTaskerTaskViewModel @Inject constructor(
    private val taskerClient: TaskerClient,
) : ViewModel() {

    private val _action = MutableStateFlow<DumbAction.DumbTaskerTask?>(null)
    val action: StateFlow<DumbAction.DumbTaskerTask?> = _action.asStateFlow()

    private val _variables = MutableStateFlow<List<TaskerVariable>>(emptyList())
    val variables: StateFlow<List<TaskerVariable>> = _variables.asStateFlow()

    private val _taskerStatus = MutableStateFlow(TaskerIntent.Status.NOT_INSTALLED)
    val taskerStatus: StateFlow<TaskerIntent.Status> = _taskerStatus.asStateFlow()

    fun setEditedAction(action: DumbAction.DumbTaskerTask) {
        _action.value = action
        _variables.value = action.variablesJson.toTaskerVariables()
            .ifEmpty { listOf(TaskerVariable(name = "", value = "")) }
        viewModelScope.launch {
            _taskerStatus.value = taskerClient.getStatus()
        }
    }

    fun setName(name: String) {
        _action.value = _action.value?.copy(name = name)
    }

    fun setTaskName(taskName: String) {
        _action.value = _action.value?.copy(taskName = taskName)
    }

    fun setWaitForCompletion(waitForCompletion: Boolean) {
        _action.value = _action.value?.copy(waitForCompletion = waitForCompletion)
    }

    fun setVariable(index: Int, name: String, value: String) {
        val updated = _variables.value.toMutableList()
        if (index !in updated.indices) return
        updated[index] = TaskerVariable(name = name, value = value)
        persistVariables(updated)
    }

    fun addVariable() {
        _variables.value = _variables.value + TaskerVariable(name = "", value = "")
    }

    fun getExternalAccessIntent() = taskerClient.getExternalAccessIntent()

    private fun persistVariables(variables: List<TaskerVariable>) {
        _action.value = _action.value?.copy(variablesJson = variables.toJsonString())
    }
}
