package com.buzbuz.smartautoclicker.feature.dumb.config.ui.actions.precision

import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import com.buzbuz.smartautoclicker.core.common.actions.precision.PrecisionTextMode
import com.buzbuz.smartautoclicker.core.common.overlays.base.viewModels
import com.buzbuz.smartautoclicker.core.common.overlays.dialog.OverlayDialog
import com.buzbuz.smartautoclicker.core.dumb.domain.model.DumbAction
import com.buzbuz.smartautoclicker.feature.dumb.config.R
import com.buzbuz.smartautoclicker.feature.dumb.config.di.DumbConfigViewModelsEntryPoint
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textview.MaterialTextView
import kotlinx.coroutines.launch

class DumbPrecisionTextDialog(
    private val dumbPrecisionText: DumbAction.DumbPrecisionText,
    private val onConfirmClicked: (DumbAction.DumbPrecisionText) -> Unit,
    private val onDeleteClicked: (DumbAction.DumbPrecisionText) -> Unit,
    private val onDismissClicked: () -> Unit,
) : OverlayDialog(R.style.AppTheme) {

    private val viewModel: DumbPrecisionTextViewModel by viewModels(
        entryPoint = DumbConfigViewModelsEntryPoint::class.java,
        creator = { dumbPrecisionTextViewModel() },
    )

    private lateinit var nameField: TextInputEditText
    private lateinit var textField: TextInputEditText
    private lateinit var repeatCountField: TextInputEditText
    private lateinit var repeatDelayField: TextInputEditText
    private lateinit var modeGroup: MaterialButtonToggleGroup
    private lateinit var saveButton: MaterialButton
    private lateinit var statusText: MaterialTextView

    override fun onCreateView(): ViewGroup {
        viewModel.setEditedAction(dumbPrecisionText)
        val root = LayoutInflater.from(context).inflate(R.layout.dialog_config_dumb_action_precision_text, null, false) as ViewGroup
        root.findViewById<MaterialTextView>(R.id.dialog_title).setText(R.string.item_title_dumb_precision_text)
        root.findViewById<MaterialButton>(R.id.button_dismiss).setOnClickListener { back() }
        root.findViewById<MaterialButton>(R.id.button_delete).apply {
            visibility = View.VISIBLE
            setOnClickListener { viewModel.action.value?.let(onDeleteClicked); closeWithoutDismiss() }
        }
        saveButton = root.findViewById<MaterialButton>(R.id.button_save).apply {
            visibility = View.VISIBLE
            setOnClickListener { commitFields(); viewModel.action.value?.let(onConfirmClicked); closeWithoutDismiss() }
        }

        nameField = root.findInputField(R.id.field_name).apply { setSingleLine(true) }
        root.findViewById<TextInputLayout>(R.id.field_name).hint = context.getString(R.string.input_field_label_name)
        textField = root.findInputField(R.id.field_text)
        root.findViewById<TextInputLayout>(R.id.field_text).hint = context.getString(R.string.input_field_label_precision_text)
        repeatCountField = root.findInputField(R.id.field_repeat_count).apply { inputType = InputType.TYPE_CLASS_NUMBER }
        root.findViewById<TextInputLayout>(R.id.field_repeat_count).hint = context.getString(R.string.item_desc_dumb_repeat_count, 1)
        repeatDelayField = root.findInputField(R.id.field_repeat_delay).apply { inputType = InputType.TYPE_CLASS_NUMBER }
        root.findViewById<TextInputLayout>(R.id.field_repeat_delay).hint = context.getString(R.string.input_field_label_repeat_delay)
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
        onDismissClicked()
        super.back()
    }

    private fun tryType() {
        commitFields()
        lifecycleScope.launch {
            statusText.text = context.getString(R.string.status_dumb_precision_text_typing)
            overlayManager.hideAll()
            val result = try { viewModel.tryType() } finally { overlayManager.restoreVisibility() }
            result.onSuccess { statusText.text = context.getString(R.string.status_dumb_precision_text_typed) }
                .onFailure { statusText.text = it.message ?: it.javaClass.simpleName }
        }
    }

    private fun commitFields() {
        viewModel.setName(nameField.text.toString())
        viewModel.setText(textField.text.toString())
        viewModel.setRepeatCount(repeatCountField.text.toString().toIntOrNull() ?: 1)
        viewModel.setRepeatDelay(repeatDelayField.text.toString().toLongOrNull() ?: 0L)
    }

    private fun updateAction(action: DumbAction.DumbPrecisionText?) {
        if (action == null) return
        if (!nameField.hasFocus()) nameField.setText(action.name)
        if (!textField.hasFocus()) textField.setText(action.text)
        if (!repeatCountField.hasFocus()) repeatCountField.setText(action.repeatCount.toString())
        if (!repeatDelayField.hasFocus()) repeatDelayField.setText(action.repeatDelayMs.toString())
        modeGroup.check(if (action.mode == PrecisionTextMode.SHELL_INPUT) R.id.button_mode_shell else R.id.button_mode_keys)
        statusText.text = context.getString(R.string.status_dumb_precision_text_ready, action.text.length, action.mode.name)
        saveButton.isEnabled = action.isValid()
    }

    private fun closeWithoutDismiss() { super.back() }
    private fun View.findInputField(layoutId: Int): TextInputEditText = findViewById<TextInputLayout>(layoutId).findViewById(R.id.text_field)
}

