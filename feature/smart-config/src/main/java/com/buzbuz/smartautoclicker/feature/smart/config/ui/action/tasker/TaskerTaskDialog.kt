/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.feature.smart.config.ui.action.tasker

import android.text.InputFilter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.buzbuz.smartautoclicker.core.common.overlays.base.viewModels
import com.buzbuz.smartautoclicker.core.common.overlays.dialog.OverlayDialog
import com.buzbuz.smartautoclicker.core.ui.bindings.dialogs.DialogNavigationButton
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.enableEasyOverwriteOnFocus
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setError
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setLabel
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setOnTextChangedListener
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setText
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setTitle
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setDescription
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setOnClickListener
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setChecked
import com.buzbuz.smartautoclicker.core.ui.databinding.IncludeFieldSwitchBinding
import com.buzbuz.smartautoclicker.feature.smart.config.R
import com.buzbuz.smartautoclicker.feature.smart.config.databinding.DialogConfigActionTaskerTaskBinding
import com.buzbuz.smartautoclicker.feature.smart.config.databinding.ItemTaskerVariableBinding
import com.buzbuz.smartautoclicker.feature.smart.config.di.ScenarioConfigViewModelsEntryPoint
import com.buzbuz.smartautoclicker.feature.smart.config.ui.action.OnActionConfigCompleteListener
import com.buzbuz.smartautoclicker.feature.smart.config.ui.common.dialogs.showCloseWithoutSavingDialog
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch
import net.dinglisch.android.tasker.TaskerIntent

class TaskerTaskDialog(
    private val listener: OnActionConfigCompleteListener,
) : OverlayDialog(R.style.ScenarioConfigTheme) {

    private val viewModel: TaskerTaskViewModel by viewModels(
        entryPoint = ScenarioConfigViewModelsEntryPoint::class.java,
        creator = { taskerTaskViewModel() },
    )

    private lateinit var viewBinding: DialogConfigActionTaskerTaskBinding
    private lateinit var waitSwitchBinding: IncludeFieldSwitchBinding
    private var selectedTaskName: String? = null
    private var variableRowCount = 0

    override fun onCreateView(): ViewGroup {
        viewBinding = DialogConfigActionTaskerTaskBinding.inflate(LayoutInflater.from(context)).apply {
            layoutTopBar.apply {
                dialogTitle.setText(R.string.dialog_title_tasker_task)
                buttonDismiss.setDebouncedOnClickListener { back() }
                buttonSave.apply {
                    visibility = View.VISIBLE
                    setDebouncedOnClickListener { onSaveButtonClicked() }
                }
                buttonDelete.apply {
                    visibility = View.VISIBLE
                    setDebouncedOnClickListener { onDeleteButtonClicked() }
                }
            }

            fieldName.apply {
                setLabel(R.string.generic_name)
                textField.filters = arrayOf<InputFilter>(
                    InputFilter.LengthFilter(context.resources.getInteger(R.integer.name_max_length))
                )
                setOnTextChangedListener { viewModel.setName(it.toString()) }
            }
            hideSoftInputOnFocusLoss(fieldName.textField)
            fieldName.enableEasyOverwriteOnFocus()

            fieldTaskSelection.setOnClickListener {
                debounceUserInteraction { showTaskPickerDialog() }
            }

            waitSwitchBinding = fieldWaitForCompletion.apply {
                setTitle(context.getString(R.string.field_tasker_wait_for_completion))
                setDescription(context.getString(R.string.field_tasker_wait_for_completion_desc))
                toggleSwitch.setOnCheckedChangeListener { _, isChecked ->
                    viewModel.setWaitForCompletion(isChecked)
                }
            }

            buttonAddVariable.setOnClickListener {
                viewModel.addVariable()
            }
        }

        return viewBinding.root
    }

    override fun onDialogCreated(dialog: BottomSheetDialog) {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                launch { viewModel.isEditingAction.collect(::onActionEditingStateChanged) }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.name.collect(::updateName) }
                launch { viewModel.nameError.collect(viewBinding.fieldName::setError) }
                launch { viewModel.taskName.collect(::updateTaskSelection) }
                launch { viewModel.waitForCompletion.collect(::updateWaitForCompletion) }
                launch { viewModel.isValidAction.collect(::updateSaveButton) }
                launch { viewModel.taskerStatus.collect(::updateTaskerStatus) }
                launch {
                    viewModel.variables.collect { variables ->
                        if (variableRowCount == 0) {
                            variableRowCount = variables.size
                            rebuildVariableRows(variables)
                        } else {
                            updateVariables(variables)
                        }
                    }
                }
            }
        }
    }

    private fun updateName(name: String?) {
        viewBinding.fieldName.setText(name)
    }

    private fun updateTaskSelection(taskName: String?) {
        selectedTaskName = taskName
        viewBinding.fieldTaskSelection.apply {
            if (taskName.isNullOrBlank()) {
                setTitle(context.getString(R.string.field_tasker_task_selection_title))
                setDescription(context.getString(R.string.field_tasker_task_selection_desc))
            } else {
                setTitle(taskName)
                setDescription(context.getString(R.string.field_tasker_task_selection_selected))
            }
        }
    }

    private fun updateWaitForCompletion(wait: Boolean) {
        waitSwitchBinding.setChecked(wait)
    }

    private fun updateSaveButton(isValid: Boolean) {
        viewBinding.layoutTopBar.buttonSave.isEnabled = isValid
    }

    private fun updateTaskerStatus(status: TaskerIntent.Status) {
        viewBinding.textTaskerStatus.apply {
            isVisible = status != TaskerIntent.Status.OK
            text = when (status) {
                TaskerIntent.Status.NOT_INSTALLED -> context.getString(R.string.message_tasker_not_installed)
                TaskerIntent.Status.NO_ACCESS -> context.getString(R.string.message_tasker_no_access)
                TaskerIntent.Status.OK -> ""
            }
            setOnClickListener {
                if (status == TaskerIntent.Status.NO_ACCESS) {
                    context.startActivity(viewModel.getExternalAccessIntent())
                }
            }
        }
    }

    private fun updateVariables(variables: List<com.buzbuz.smartautoclicker.core.tasker.TaskerVariable>) {
        if (variables.size == variableRowCount) return
        variableRowCount = variables.size
        rebuildVariableRows(variables)
    }

    private fun rebuildVariableRows(variables: List<com.buzbuz.smartautoclicker.core.tasker.TaskerVariable>) {
        val container = viewBinding.variablesContainer
        container.removeAllViews()
        variables.forEachIndexed { index, variable ->
            val itemBinding = ItemTaskerVariableBinding.inflate(
                LayoutInflater.from(context),
                container,
                true,
            )
            itemBinding.fieldVariableName.apply {
                setLabel(R.string.field_tasker_variable_name)
                setText(variable.name)
                setOnTextChangedListener { newName ->
                    viewModel.setVariable(
                        index,
                        newName.toString(),
                        itemBinding.fieldVariableValue.textField.text?.toString().orEmpty(),
                    )
                }
            }
            itemBinding.fieldVariableValue.apply {
                setLabel(R.string.field_tasker_variable_value)
                setText(variable.value)
                setOnTextChangedListener { newValue ->
                    viewModel.setVariable(
                        index,
                        itemBinding.fieldVariableName.textField.text?.toString().orEmpty(),
                        newValue.toString(),
                    )
                }
            }
        }
    }

    private fun showTaskPickerDialog() {
        overlayManager.navigateTo(
            context = context,
            newOverlay = TaskerTaskPickerDialog(
                onTaskSelected = { taskName ->
                    viewModel.setTaskName(taskName)
                },
            ),
            hideCurrent = false,
        )
    }

    private fun onSaveButtonClicked() {
        listener.onConfirmClicked()
        back()
    }

    private fun onDeleteButtonClicked() {
        listener.onDeleteClicked()
        back()
    }

    private fun onActionEditingStateChanged(isEditing: Boolean) {
        if (!isEditing) back()
    }

    override fun back() {
        if (viewModel.hasUnsavedModifications()) {
            context.showCloseWithoutSavingDialog {
                listener.onDismissClicked()
                super.back()
            }
        } else {
            listener.onDismissClicked()
            super.back()
        }
    }
}
