package com.buzbuz.smartautoclicker.feature.smart.config.ui.action.precision

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textview.MaterialTextView
import androidx.lifecycle.lifecycleScope
import com.buzbuz.smartautoclicker.core.common.actions.precision.PrecisionTextMode
import com.buzbuz.smartautoclicker.core.common.overlays.base.viewModels
import com.buzbuz.smartautoclicker.core.common.overlays.dialog.OverlayDialog
import com.buzbuz.smartautoclicker.core.domain.model.action.PrecisionText
import com.buzbuz.smartautoclicker.feature.smart.config.R
import com.buzbuz.smartautoclicker.feature.smart.config.di.ScenarioConfigViewModelsEntryPoint
import com.buzbuz.smartautoclicker.feature.smart.config.ui.action.OnActionConfigCompleteListener
import com.buzbuz.smartautoclicker.feature.smart.config.ui.common.dialogs.showCloseWithoutSavingDialog
import kotlinx.coroutines.launch

class PrecisionTextDialog(
    private val listener: OnActionConfigCompleteListener,
) : OverlayDialog(R.style.ScenarioConfigTheme) {

    private val viewModel: PrecisionTextViewModel by viewModels(
        entryPoint = ScenarioConfigViewModelsEntryPoint::class.java,
        creator = { precisionTextViewModel() },
    )

    private lateinit var nameField: TextInputEditText
    private lateinit var textField: TextInputEditText
    private lateinit var modeGroup: MaterialButtonToggleGroup
    private lateinit var saveButton: MaterialButton
    private lateinit var statusText: MaterialTextView

    override fun onCreateView(): ViewGroup {
        val root = LayoutInflater.from(context).inflate(R.layout.dialog_config_action_precision_text, null, false) as ViewGroup
        root.findViewById<MaterialTextView>(R.id.dialog_title).setText(R.string.dialog_title_precision_text)
        root.findViewById<MaterialButton>(R.id.button_dismiss).setOnClickListener { back() }
        root.findViewById<MaterialButton>(R.id.button_delete).apply {
            visibility = View.VISIBLE
            setOnClickListener { listener.onDeleteClicked(); closeWithoutDismiss() }
        }
        saveButton = root.findViewById<MaterialButton>(R.id.button_save).apply {
            visibility = View.VISIBLE
            setOnClickListener { commitFields(); listener.onConfirmClicked(); closeWithoutDismiss() }
        }

        nameField = root.findInputField(R.id.field_name).apply { setSingleLine(true) }
        root.findViewById<TextInputLayout>(R.id.field_name).hint = context.getString(R.string.generic_name)
        textField = root.findInputField(R.id.field_text)
        root.findViewById<TextInputLayout>(R.id.field_text).hint = context.getString(R.string.input_field_label_precision_text)
        statusText = root.findViewById(R.id.text_status)
        modeGroup = root.findViewById<MaterialButtonToggleGroup>(R.id.toggle_mode).apply {
            addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (isChecked) viewModel.setMode(if (checkedId == R.id.button_mode_shell) PrecisionTextMode.SHELL_INPUT else PrecisionTextMode.KEY_EVENTS)
            }
        }
        root.findViewById<MaterialButton>(R.id.button_try).setOnClickListener { tryType() }
        return root
    }

    override fun onDialogCreated(dialog: com.google.android.material.bottomsheet.BottomSheetDialog) {
        lifecycleScope.launch { viewModel.action.collect(::updateAction) }
    }

    override fun back() {
        commitFields()
        if (viewModel.hasUnsavedModifications.value) {
            context.showCloseWithoutSavingDialog { listener.onDismissClicked(); super.back() }
            return
        }
        listener.onDismissClicked()
        super.back()
    }

    private fun tryType() {
        commitFields()
        lifecycleScope.launch {
            statusText.text = context.getString(R.string.status_precision_text_typing)
            overlayManager.hideAll()
            val result = try { viewModel.tryType() } finally { overlayManager.restoreVisibility() }
            result.onSuccess { statusText.text = context.getString(R.string.status_precision_text_typed) }
                .onFailure { statusText.text = it.message ?: it.javaClass.simpleName }
        }
    }

    private fun commitFields() {
        viewModel.setName(nameField.text.toString())
        viewModel.setText(textField.text.toString())
    }

    private fun updateAction(action: PrecisionText?) {
        if (action == null) return
        if (!nameField.hasFocus()) nameField.setText(action.name.orEmpty())
        if (!textField.hasFocus()) textField.setText(action.text)
        modeGroup.check(if (action.mode == PrecisionTextMode.SHELL_INPUT) R.id.button_mode_shell else R.id.button_mode_keys)
        statusText.text = context.getString(R.string.status_precision_text_ready, action.text.length, action.mode.name)
        saveButton.isEnabled = action.isComplete()
    }

    private fun closeWithoutDismiss() { super.back() }
    private fun View.findInputField(layoutId: Int): TextInputEditText = findViewById<TextInputLayout>(layoutId).findViewById(R.id.text_field)
}

