/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.feature.smart.config.ui.action.tasker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.buzbuz.smartautoclicker.core.common.overlays.base.viewModels
import com.buzbuz.smartautoclicker.core.common.overlays.dialog.OverlayDialog
import com.buzbuz.smartautoclicker.core.ui.bindings.dialogs.DialogNavigationButton
import com.buzbuz.smartautoclicker.core.ui.bindings.dialogs.setButtonVisibility
import com.buzbuz.smartautoclicker.core.ui.bindings.lists.updateState
import com.buzbuz.smartautoclicker.feature.smart.config.R
import com.buzbuz.smartautoclicker.feature.smart.config.databinding.DialogBaseSelectionBinding
import com.buzbuz.smartautoclicker.feature.smart.config.di.ScenarioConfigViewModelsEntryPoint
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch
import net.dinglisch.android.tasker.TaskerIntent

class TaskerTaskPickerDialog(
    private val onTaskSelected: (String) -> Unit,
) : OverlayDialog(R.style.ScenarioConfigTheme) {

    private val viewModel: TaskerTaskPickerViewModel by viewModels(
        entryPoint = ScenarioConfigViewModelsEntryPoint::class.java,
        creator = { taskerTaskPickerViewModel() },
    )

    private lateinit var viewBinding: DialogBaseSelectionBinding
    private lateinit var adapter: TaskerTaskPickerAdapter

    override fun onCreateView(): ViewGroup {
        viewModel.loadTasks()

        adapter = TaskerTaskPickerAdapter { taskName ->
            debounceUserInteraction {
                onTaskSelected(taskName)
                back()
            }
        }

        viewBinding = DialogBaseSelectionBinding.inflate(LayoutInflater.from(context)).apply {
            layoutTopBar.apply {
                dialogTitle.setText(R.string.dialog_title_tasker_task_picker)
                setButtonVisibility(DialogNavigationButton.SAVE, View.GONE)
                setButtonVisibility(DialogNavigationButton.DELETE, View.GONE)
                buttonDismiss.setDebouncedOnClickListener { back() }
            }

            layoutLoadableList.list.apply {
                adapter = this@TaskerTaskPickerDialog.adapter
            }
        }

        return viewBinding.root
    }

    override fun onDialogCreated(dialog: BottomSheetDialog) {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.tasks.collect { tasks ->
                        viewBinding.layoutLoadableList.updateState(tasks)
                        adapter.submitList(tasks)
                    }
                }
                launch {
                    viewModel.status.collect { status ->
                        if (status != TaskerIntent.Status.OK) {
                            viewBinding.layoutLoadableList.apply {
                                loading.visibility = View.GONE
                                list.visibility = View.GONE
                                empty.visibility = View.VISIBLE
                                emptyText.setText(
                                    when (status) {
                                        TaskerIntent.Status.NOT_INSTALLED -> R.string.message_tasker_not_installed
                                        TaskerIntent.Status.NO_ACCESS -> R.string.message_tasker_no_access
                                        TaskerIntent.Status.OK -> R.string.message_tasker_no_tasks
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
