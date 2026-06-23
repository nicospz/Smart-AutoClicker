/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.feature.dumb.config.ui.actions.tasker

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
import com.buzbuz.smartautoclicker.core.dumb.domain.model.DumbAction
import com.buzbuz.smartautoclicker.core.ui.bindings.dialogs.DialogNavigationButton
import com.buzbuz.smartautoclicker.core.ui.bindings.dialogs.setButtonEnabledState
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.enableEasyOverwriteOnFocus
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setChecked
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setDescription
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setLabel
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setOnClickListener
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setOnTextChangedListener
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setText
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setTitle
import com.buzbuz.smartautoclicker.core.ui.databinding.IncludeFieldSwitchBinding
import com.buzbuz.smartautoclicker.feature.dumb.config.R
import com.buzbuz.smartautoclicker.feature.dumb.config.databinding.DialogConfigDumbActionTaskerTaskBinding
import com.buzbuz.smartautoclicker.feature.dumb.config.databinding.ItemTaskerVariableBinding
import com.buzbuz.smartautoclicker.feature.dumb.config.di.DumbConfigViewModelsEntryPoint
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch
import net.dinglisch.android.tasker.TaskerIntent

class DumbTaskerTaskDialog(
    private val dumbTaskerTask: DumbAction.DumbTaskerTask,
    private val onConfirmClicked: (DumbAction.DumbTaskerTask) -> Unit,
    private val onDeleteClicked: (DumbAction.DumbTaskerTask) -> Unit,
    private val onDismissClicked: () -> Unit,
) : OverlayDialog(R.style.AppTheme) {

    private val viewModel: DumbTaskerTaskViewModel by viewModels(
        entryPoint = DumbConfigViewModelsEntryPoint::class.java,
        creator = { dumbTaskerTaskViewModel() },
    )

    private lateinit var viewBinding: DialogConfigDumbActionTaskerTaskBinding
    private lateinit var waitSwitchBinding: IncludeFieldSwitchBinding
    private var variableRowCount = 0

    override fun onCreateView(): ViewGroup {
        viewModel.setEditedAction(dumbTaskerTask)

        viewBinding = DialogConfigDumbActionTaskerTaskBinding.inflate(LayoutInflater.from(context)).apply {
            layoutTopBar.apply {
                dialogTitle.setText(R.string.dialog_title_tasker_task)
                buttonDismiss.setDebouncedOnClickListener { onDismissButtonClicked() }
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
                setLabel(R.string.input_field_label_name)
                setText(dumbTaskerTask.name)
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
            updateTaskSelection(dumbTaskerTask.taskName)

            waitSwitchBinding = fieldWaitForCompletion.apply {
                setTitle(context.getString(R.string.field_tasker_wait_for_completion))
                setDescription(context.getString(R.string.field_tasker_wait_for_completion_desc))
                setChecked(dumbTaskerTask.waitForCompletion)
                toggleSwitch.setOnCheckedChangeListener { _, isChecked ->
                    viewModel.setWaitForCompletion(isChecked)
                }
            }

            buttonAddVariable.setOnClickListener {
                viewModel.addVariable()
            }
        }

        rebuildVariableRows(viewModel.variables.value)
        variableRowCount = viewModel.variables.value.size

        return viewBinding.root
    }

    override fun onDialogCreated(dialog: BottomSheetDialog) {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.action.collect(::updateActionState) }
                launch { viewModel.taskerStatus.collect(::updateTaskerStatus) }
                launch { viewModel.variables.collect(::updateVariables) }
            }
        }
    }

    private fun updateActionState(action: DumbAction.DumbTaskerTask?) {
        action ?: return
        updateTaskSelection(action.taskName)
        waitSwitchBinding.setChecked(action.waitForCompletion)
        viewBinding.layoutTopBar.setButtonEnabledState(DialogNavigationButton.SAVE, action.isValid())
    }

    private fun updateTaskSelection(taskName: String?) {
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
            newOverlay = DumbTaskerTaskPickerDialog(
                onTaskSelected = { taskName ->
                    viewModel.setTaskName(taskName)
                },
            ),
            hideCurrent = false,
        )
    }

    private fun onSaveButtonClicked() {
        viewModel.action.value?.let { action ->
            onConfirmClicked(action)
            back()
        }
    }

    private fun onDeleteButtonClicked() {
        viewModel.action.value?.let { action ->
            onDeleteClicked(action)
            back()
        }
    }

    private fun onDismissButtonClicked() {
        onDismissClicked()
        back()
    }
}
