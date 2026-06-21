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
import com.buzbuz.smartautoclicker.core.common.actions.precision.isClipboardPaste
import com.buzbuz.smartautoclicker.core.common.overlays.base.viewModels
import com.buzbuz.smartautoclicker.core.common.overlays.dialog.OverlayDialog
import com.buzbuz.smartautoclicker.core.domain.model.action.PrecisionText
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setChecked
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setDescription
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setOnClickListener
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setTitle
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setupDescriptions
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.enableEasyOverwriteOnFocus
import com.buzbuz.smartautoclicker.core.ui.databinding.IncludeFieldSwitchBinding
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
    private lateinit var replaceExistingField: IncludeFieldSwitchBinding
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

        val nameLayout = root.findViewById<TextInputLayout>(R.id.field_name).apply {
            hint = context.getString(R.string.generic_name)
        }
        nameField = nameLayout.findViewById<TextInputEditText>(R.id.text_field).apply { setSingleLine(true) }
        nameLayout.enableEasyOverwriteOnFocus()
        textField = root.findInputField(R.id.field_text)
        root.findViewById<TextInputLayout>(R.id.field_text).hint = context.getString(R.string.input_field_label_precision_text)
        statusText = root.findViewById(R.id.text_status)
        modeGroup = root.findViewById<MaterialButtonToggleGroup>(R.id.toggle_mode).apply {
            addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (isChecked) viewModel.setMode(checkedId.toPrecisionTextMode(replaceExistingField.toggleSwitch.isChecked))
            }
        }
        replaceExistingField = IncludeFieldSwitchBinding.bind(root.findViewById(R.id.field_replace_existing_text)).apply {
            setTitle(context.getString(R.string.field_precision_text_replace_existing_title))
            setupDescriptions(
                listOf(
                    context.getString(R.string.field_precision_text_replace_existing_disabled),
                    context.getString(R.string.field_precision_text_replace_existing_enabled),
                )
            )
            setOnClickListener { viewModel.setReplaceExistingText(toggleSwitch.isChecked) }
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
        replaceExistingField.updateReplaceExistingText(action.mode)
        modeGroup.check(action.mode.toButtonId())
        statusText.text = context.getString(R.string.status_precision_text_ready, action.text.length, action.mode.name)
        saveButton.isEnabled = action.isComplete()
    }

    private fun closeWithoutDismiss() { super.back() }
    private fun View.findInputField(layoutId: Int): TextInputEditText = findViewById<TextInputLayout>(layoutId).findViewById(R.id.text_field)
    private fun Int.toPrecisionTextMode(replaceExistingText: Boolean): PrecisionTextMode = when (this) {
        R.id.button_mode_shell -> PrecisionTextMode.SHELL_INPUT
        R.id.button_mode_paste -> if (replaceExistingText) PrecisionTextMode.CLIPBOARD_PASTE_REPLACE else PrecisionTextMode.CLIPBOARD_PASTE
        else -> PrecisionTextMode.KEY_EVENTS
    }
    private fun PrecisionTextMode.toButtonId(): Int = when (this) {
        PrecisionTextMode.KEY_EVENTS -> R.id.button_mode_keys
        PrecisionTextMode.SHELL_INPUT -> R.id.button_mode_shell
        PrecisionTextMode.CLIPBOARD_PASTE,
        PrecisionTextMode.CLIPBOARD_PASTE_REPLACE -> R.id.button_mode_paste
    }
    private fun IncludeFieldSwitchBinding.updateReplaceExistingText(mode: PrecisionTextMode) {
        root.visibility = if (mode.isClipboardPaste()) View.VISIBLE else View.GONE
        setChecked(mode == PrecisionTextMode.CLIPBOARD_PASTE_REPLACE)
        setDescription(if (mode == PrecisionTextMode.CLIPBOARD_PASTE_REPLACE) 1 else 0)
    }
}
