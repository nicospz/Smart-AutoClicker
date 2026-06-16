package com.buzbuz.smartautoclicker.feature.dumb.config.ui.actions.precision

import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textview.MaterialTextView
import androidx.lifecycle.lifecycleScope
import com.buzbuz.smartautoclicker.core.common.overlays.base.viewModels
import com.buzbuz.smartautoclicker.core.common.overlays.dialog.OverlayDialog
import com.buzbuz.smartautoclicker.core.dumb.domain.model.DumbAction
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.enableEasyOverwriteOnFocus
import com.buzbuz.smartautoclicker.core.ui.utils.formatDuration
import com.buzbuz.smartautoclicker.feature.dumb.config.R
import com.buzbuz.smartautoclicker.feature.dumb.config.di.DumbConfigViewModelsEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DumbPrecisionGestureDialog(
    private val dumbPrecisionGesture: DumbAction.DumbPrecisionGesture,
    private val onConfirmClicked: (DumbAction.DumbPrecisionGesture) -> Unit,
    private val onDeleteClicked: (DumbAction.DumbPrecisionGesture) -> Unit,
    private val onDismissClicked: () -> Unit,
) : OverlayDialog(R.style.AppTheme) {

    private val viewModel: DumbPrecisionGestureViewModel by viewModels(
        entryPoint = DumbConfigViewModelsEntryPoint::class.java,
        creator = { dumbPrecisionGestureViewModel() },
    )

    private lateinit var nameField: TextInputEditText
    private lateinit var repeatCountField: TextInputEditText
    private lateinit var repeatDelayField: TextInputEditText
    private lateinit var statusText: MaterialTextView
    private lateinit var saveButton: MaterialButton
    private lateinit var playButton: MaterialButton
    private lateinit var clearButton: MaterialButton

    override fun onCreateView(): ViewGroup {
        viewModel.setEditedGesture(dumbPrecisionGesture)

        val root = LayoutInflater.from(context)
            .inflate(R.layout.dialog_config_dumb_action_precision_gesture, null, false) as ViewGroup

        root.findViewById<MaterialTextView>(R.id.dialog_title)
            .setText(R.string.item_title_dumb_precision_gesture)

        root.findViewById<MaterialButton>(R.id.button_dismiss)
            .setOnClickListener { back() }

        root.findViewById<MaterialButton>(R.id.button_delete).apply {
            visibility = View.VISIBLE
            setOnClickListener {
                viewModel.gesture.value?.let(onDeleteClicked)
                closeWithoutDismiss()
            }
        }

        saveButton = root.findViewById<MaterialButton>(R.id.button_save).apply {
            visibility = View.VISIBLE
            setOnClickListener {
                commitTextFields()
                viewModel.gesture.value?.let(onConfirmClicked)
                closeWithoutDismiss()
            }
        }

        val nameLayout = root.findViewById<TextInputLayout>(R.id.field_name).apply {
            hint = context.getString(R.string.input_field_label_name)
        }
        nameField = nameLayout.findViewById<TextInputEditText>(R.id.text_field).apply {
            setSingleLine(true)
            setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) viewModel.setName(text.toString()) }
        }
        nameLayout.enableEasyOverwriteOnFocus()

        repeatCountField = root.findInputField(R.id.field_repeat_count).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) viewModel.setRepeatCount(text.toString().toIntOrNull() ?: 1)
            }
        }
        root.findViewById<TextInputLayout>(R.id.field_repeat_count).hint =
            context.getString(R.string.item_desc_dumb_repeat_count, 1)

        repeatDelayField = root.findInputField(R.id.field_repeat_delay).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) viewModel.setRepeatDelay(text.toString().toLongOrNull() ?: 0L)
            }
        }
        root.findViewById<TextInputLayout>(R.id.field_repeat_delay).hint =
            context.getString(R.string.input_field_label_repeat_delay)

        statusText = root.findViewById(R.id.text_status)
        root.findViewById<MaterialButton>(R.id.button_record).setOnClickListener { record() }
        playButton = root.findViewById<MaterialButton>(R.id.button_play).apply {
            setOnClickListener { play() }
        }
        clearButton = root.findViewById<MaterialButton>(R.id.button_clear).apply {
            setOnClickListener { viewModel.clear() }
        }

        return root
    }

    override fun onDialogCreated(dialog: com.google.android.material.bottomsheet.BottomSheetDialog) {
        lifecycleScope.launch {
            viewModel.gesture.collect(::updateGesture)
        }
    }

    override fun back() {
        onDismissClicked()
        super.back()
    }

    private fun record() {
        commitTextFields()
        lifecycleScope.launch {
            statusText.text = context.getString(R.string.status_dumb_precision_gesture_recording)
            overlayManager.hideAll()
            val result = try {
                viewModel.record()
            } finally {
                overlayManager.restoreVisibility()
            }
            result.onFailure { statusText.text = it.message ?: it.javaClass.simpleName }
        }
    }

    private fun play() {
        commitTextFields()
        lifecycleScope.launch {
            statusText.text = context.getString(R.string.status_dumb_precision_gesture_playing)
            overlayManager.hideAll()
            val result = try {
                delay(PRECISION_GESTURE_OVERLAY_HIDE_DELAY_MS)
                viewModel.play()
            } finally {
                overlayManager.restoreVisibility()
            }
            result
                .onSuccess { statusText.text = context.getString(R.string.status_dumb_precision_gesture_played) }
                .onFailure { statusText.text = it.message ?: it.javaClass.simpleName }
        }
    }

    private fun commitTextFields() {
        viewModel.setName(nameField.text.toString())
        viewModel.setRepeatCount(repeatCountField.text.toString().toIntOrNull() ?: 1)
        viewModel.setRepeatDelay(repeatDelayField.text.toString().toLongOrNull() ?: 0L)
    }

    private fun updateGesture(gesture: DumbAction.DumbPrecisionGesture?) {
        if (gesture == null) return
        if (!nameField.hasFocus()) nameField.setText(gesture.name)
        if (!repeatCountField.hasFocus()) repeatCountField.setText(gesture.repeatCount.toString())
        if (!repeatDelayField.hasFocus()) repeatDelayField.setText(gesture.repeatDelayMs.toString())
        val hasPayload = gesture.payloadHex != null
        statusText.text = if (hasPayload) {
            context.getString(
                R.string.status_dumb_precision_gesture_ready,
                gesture.eventCount ?: 0,
                formatDuration(gesture.durationMs ?: 0L),
            )
        } else {
            context.getString(R.string.status_dumb_precision_gesture_empty)
        }
        saveButton.isEnabled = gesture.isValid()
        playButton.isEnabled = hasPayload
        clearButton.isEnabled = hasPayload
    }

    private fun closeWithoutDismiss() {
        super.back()
    }

    private fun View.findInputField(layoutId: Int): TextInputEditText =
        findViewById<TextInputLayout>(layoutId).findViewById(R.id.text_field)
}

private const val PRECISION_GESTURE_OVERLAY_HIDE_DELAY_MS = 500L
