/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.feature.smart.config.ui.action.tasker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buzbuz.smartautoclicker.core.domain.model.action.TaskerTask
import com.buzbuz.smartautoclicker.core.tasker.TaskerClient
import com.buzbuz.smartautoclicker.core.tasker.TaskerVariable
import com.buzbuz.smartautoclicker.core.tasker.toJsonString
import com.buzbuz.smartautoclicker.core.tasker.toTaskerVariables
import com.buzbuz.smartautoclicker.feature.smart.config.domain.EditionRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import net.dinglisch.android.tasker.TaskerIntent
import javax.inject.Inject

class TaskerTaskViewModel @Inject constructor(
    private val editionRepository: EditionRepository,
    private val taskerClient: TaskerClient,
) : ViewModel() {

    private val configuredTaskerTask = editionRepository.editionState.editedActionState
        .mapNotNull { action -> action.value }
        .filterIsInstance<TaskerTask>()

    private val editedActionHasChanged: StateFlow<Boolean> =
        editionRepository.editionState.editedActionState
            .map { it.hasChanged }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _variables = MutableStateFlow<List<TaskerVariable>>(emptyList())
    val variables: StateFlow<List<TaskerVariable>> = _variables

    private val _taskerStatus = MutableStateFlow(TaskerIntent.Status.NOT_INSTALLED)
    val taskerStatus: StateFlow<TaskerIntent.Status> = _taskerStatus

    init {
        viewModelScope.launch {
            configuredTaskerTask.take(1).collect { action ->
                _variables.value = action.variablesJson.toTaskerVariables()
                    .ifEmpty { listOf(TaskerVariable(name = "", value = "")) }
            }
            _taskerStatus.value = taskerClient.getStatus()
        }
    }

    @OptIn(FlowPreview::class)
    val isEditingAction: Flow<Boolean> = editionRepository.isEditingAction
        .distinctUntilChanged()
        .debounce(1000)

    val name: Flow<String?> = configuredTaskerTask.map { it.name }.take(1)
    val nameError: Flow<Boolean> = configuredTaskerTask
        .map { it.name?.isEmpty() ?: true }
        .distinctUntilChanged()
    val taskName: Flow<String?> = configuredTaskerTask
        .map { it.taskName }
        .distinctUntilChanged()
    val waitForCompletion: Flow<Boolean> = configuredTaskerTask.map { it.waitForCompletion }

    val isValidAction: Flow<Boolean> = editionRepository.editionState.editedActionState
        .map { it.canBeSaved }

    fun hasUnsavedModifications(): Boolean = editedActionHasChanged.value

    fun setName(name: String) = updateAction { it.copy(name = "" + name) }

    fun setTaskName(taskName: String) = updateAction { it.copy(taskName = taskName) }

    fun setWaitForCompletion(waitForCompletion: Boolean) =
        updateAction { it.copy(waitForCompletion = waitForCompletion) }

    fun setVariable(index: Int, name: String, value: String) {
        val updated = _variables.value.toMutableList()
        if (index !in updated.indices) return
        updated[index] = TaskerVariable(name = name, value = value)
        persistVariables(updated)
    }

    fun addVariable() {
        val updated = _variables.value + TaskerVariable(name = "", value = "")
        _variables.value = updated
    }

    fun removeVariable(index: Int) {
        val updated = _variables.value.toMutableList()
        if (index !in updated.indices) return
        updated.removeAt(index)
        if (updated.isEmpty()) updated += TaskerVariable(name = "", value = "")
        _variables.value = updated
        persistVariables(updated)
    }

    fun getExternalAccessIntent() = taskerClient.getExternalAccessIntent()

    private fun persistVariables(variables: List<TaskerVariable>) =
        updateAction { it.copy(variablesJson = variables.toJsonString()) }

    private fun updateAction(closure: (TaskerTask) -> TaskerTask) {
        editionRepository.editionState.getEditedAction<TaskerTask>()?.let { action ->
            editionRepository.updateEditedAction(closure(action))
        }
    }
}
