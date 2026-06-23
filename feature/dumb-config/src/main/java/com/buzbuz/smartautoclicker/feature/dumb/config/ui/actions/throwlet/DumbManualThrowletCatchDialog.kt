/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.feature.dumb.config.ui.actions.throwlet

import android.text.InputFilter
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.buzbuz.smartautoclicker.core.common.overlays.base.viewModels
import com.buzbuz.smartautoclicker.core.common.overlays.dialog.OverlayDialog
import com.buzbuz.smartautoclicker.core.dumb.domain.model.DumbAction
import com.buzbuz.smartautoclicker.core.ui.bindings.dialogs.DialogNavigationButton
import com.buzbuz.smartautoclicker.core.ui.bindings.dialogs.setButtonEnabledState
import com.buzbuz.smartautoclicker.core.ui.bindings.dropdown.setItems
import com.buzbuz.smartautoclicker.core.ui.bindings.dropdown.setSelectedItem
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.enableEasyOverwriteOnFocus
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setLabel
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setOnTextChangedListener
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setText
import com.buzbuz.smartautoclicker.feature.dumb.config.R
import com.buzbuz.smartautoclicker.feature.dumb.config.databinding.DialogConfigDumbActionManualThrowletCatchBinding
import com.buzbuz.smartautoclicker.feature.dumb.config.di.DumbConfigViewModelsEntryPoint
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch

class DumbManualThrowletCatchDialog(
    private val dumbManualThrowletCatch: DumbAction.DumbManualThrowletCatch,
    private val onConfirmClicked: (DumbAction.DumbManualThrowletCatch) -> Unit,
    private val onDeleteClicked: (DumbAction.DumbManualThrowletCatch) -> Unit,
    private val onDismissClicked: () -> Unit,
) : OverlayDialog(R.style.AppTheme) {

    private val viewModel: DumbManualThrowletCatchViewModel by viewModels(
        entryPoint = DumbConfigViewModelsEntryPoint::class.java,
        creator = { dumbManualThrowletCatchViewModel() },
    )

    private lateinit var viewBinding: DialogConfigDumbActionManualThrowletCatchBinding

    override fun onCreateView(): ViewGroup {
        viewModel.setEditedAction(dumbManualThrowletCatch)
        viewBinding = DialogConfigDumbActionManualThrowletCatchBinding.inflate(LayoutInflater.from(context)).apply {
            layoutTopBar.apply {
                dialogTitle.setText(R.string.dialog_title_dumb_manual_throwlet_catch)
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
                setText(dumbManualThrowletCatch.name)
                textField.filters = arrayOf<InputFilter>(
                    InputFilter.LengthFilter(context.resources.getInteger(R.integer.name_max_length))
                )
                setOnTextChangedListener { viewModel.setName(it.toString()) }
            }
            hideSoftInputOnFocusLoss(fieldName.textField)
            fieldName.enableEasyOverwriteOnFocus()

            fieldOperation.setItems(
                label = context.getString(R.string.field_manual_throwlet_operation_title),
                items = manualThrowletOperationItems,
                onItemSelected = viewModel::setOperation,
            )
            fieldLane.setItems(
                label = context.getString(R.string.field_manual_throwlet_lane_title),
                items = manualThrowletLaneItems,
                onItemSelected = viewModel::setLane,
            )
            fieldPokemon.apply {
                disabledTouchHandler.visibility = View.GONE
                textLayout.hint = context.getString(R.string.field_manual_throwlet_pokemon_title)
                textField.setAdapter(
                    ArrayAdapter(
                        context,
                        android.R.layout.simple_dropdown_item_1line,
                        viewModel.pokemonNames,
                    )
                )
                textField.threshold = 1
                textField.inputType = InputType.TYPE_CLASS_TEXT
                textField.setText(dumbManualThrowletCatch.pokemonNameOverride.orEmpty(), false)
                textField.setOnItemClickListener { _, _, _, _ ->
                    viewModel.setPokemonNameOverride(textField.text?.toString().orEmpty())
                }
                textField.setOnFocusChangeListener { _, hasFocus ->
                    if (!hasFocus) viewModel.setPokemonNameOverride(textField.text?.toString().orEmpty())
                }
            }
        }
        return viewBinding.root
    }

    override fun onDialogCreated(dialog: BottomSheetDialog) {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.action.collect(::updateActionState) }
            }
        }
    }

    private fun updateActionState(action: DumbAction.DumbManualThrowletCatch?) {
        action ?: return
        viewBinding.fieldOperation.setSelectedItem(action.operation.toManualThrowletOperationItem())
        viewBinding.fieldLane.setSelectedItem(action.lane.toManualThrowletLaneItem())
        if (!viewBinding.fieldPokemon.textField.hasFocus()) {
            viewBinding.fieldPokemon.textField.setText(action.pokemonNameOverride.orEmpty(), false)
        }
        val pokemonIsValid = viewModel.hasValidPokemonOverride()
        viewBinding.fieldPokemon.textLayout.error =
            if (pokemonIsValid) null else context.getString(R.string.field_manual_throwlet_pokemon_error)
        viewBinding.layoutTopBar.setButtonEnabledState(
            DialogNavigationButton.SAVE,
            action.isValid() && pokemonIsValid,
        )
    }

    private fun onSaveButtonClicked() {
        viewModel.setPokemonNameOverride(viewBinding.fieldPokemon.textField.text?.toString().orEmpty())
        val action = viewModel.action.value ?: return
        if (!action.isValid() || !viewModel.hasValidPokemonOverride()) return
        onConfirmClicked(action)
        back()
    }

    private fun onDeleteButtonClicked() {
        viewModel.action.value?.let(onDeleteClicked)
        back()
    }

    private fun onDismissButtonClicked() {
        onDismissClicked()
        back()
    }
}
