package com.buzbuz.smartautoclicker.feature.smart.config.ui.action.precision

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
import com.buzbuz.smartautoclicker.core.domain.model.action.PrecisionGesture
import com.buzbuz.smartautoclicker.core.ui.utils.formatDuration
import com.buzbuz.smartautoclicker.feature.smart.config.R
import com.buzbuz.smartautoclicker.feature.smart.config.di.ScenarioConfigViewModelsEntryPoint
import com.buzbuz.smartautoclicker.feature.smart.config.ui.action.OnActionConfigCompleteListener
import com.buzbuz.smartautoclicker.feature.smart.config.ui.common.dialogs.showCloseWithoutSavingDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PrecisionGestureDialog(
    private val listener: OnActionConfigCompleteListener,
) : OverlayDialog(R.style.ScenarioConfigTheme) {

    private val viewModel: PrecisionGestureViewModel by viewModels(
        entryPoint = ScenarioConfigViewModelsEntryPoint::class.java,
        creator = { precisionGestureViewModel() },
    )

    private lateinit var nameField: TextInputEditText
    private lateinit var statusText: MaterialTextView
    private lateinit var saveButton: MaterialButton
    private lateinit var playButton: MaterialButton
    private lateinit var clearButton: MaterialButton

    override fun onCreateView(): ViewGroup {
        val root = LayoutInflater.from(context)
            .inflate(R.layout.dialog_config_action_precision_gesture, null, false) as ViewGroup

        root.findViewById<MaterialTextView>(R.id.dialog_title)
            .setText(R.string.dialog_title_precision_gesture)

        root.findViewById<MaterialButton>(R.id.button_dismiss)
            .setOnClickListener { back() }

        root.findViewById<MaterialButton>(R.id.button_delete).apply {
            visibility = View.VISIBLE
            setOnClickListener {
                listener.onDeleteClicked()
                closeWithoutDismiss()
            }
        }

        saveButton = root.findViewById<MaterialButton>(R.id.button_save).apply {
            visibility = View.VISIBLE
            setOnClickListener {
                viewModel.setName(nameField.text.toString())
                listener.onConfirmClicked()
                closeWithoutDismiss()
            }
        }

        val nameLayout = root.findViewById<TextInputLayout>(R.id.field_name).apply {
            hint = context.getString(R.string.generic_name)
        }
        nameField = nameLayout.findViewById<TextInputEditText>(R.id.text_field).apply {
            setSingleLine(true)
            setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) viewModel.setName(text.toString())
            }
        }

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
            viewModel.gesture.collect { updateGesture(it) }
        }
    }

    override fun back() {
        if (viewModel.hasUnsavedModifications.value) {
            context.showCloseWithoutSavingDialog {
                listener.onDismissClicked()
                super.back()
            }
            return
        }

        listener.onDismissClicked()
        super.back()
    }

    private fun record() {
        viewModel.setName(nameField.text.toString())
        lifecycleScope.launch {
            statusText.text = context.getString(R.string.status_precision_gesture_recording)
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
        lifecycleScope.launch {
            statusText.text = context.getString(R.string.status_precision_gesture_playing)
            overlayManager.hideAll()
            val result = try {
                delay(PRECISION_GESTURE_OVERLAY_HIDE_DELAY_MS)
                viewModel.play()
            } finally {
                overlayManager.restoreVisibility()
            }
            result
                .onSuccess { statusText.text = context.getString(R.string.status_precision_gesture_played) }
                .onFailure { statusText.text = it.message ?: it.javaClass.simpleName }
        }
    }

    private fun closeWithoutDismiss() {
        super.back()
    }

    private fun updateGesture(gesture: PrecisionGesture?) {
        if (gesture == null) return
        if (!nameField.hasFocus()) nameField.setText(gesture.name.orEmpty())
        val hasPayload = gesture.payloadHex != null
        statusText.text = if (hasPayload) {
            context.getString(
                R.string.status_precision_gesture_ready,
                gesture.eventCount ?: 0,
                formatDuration(gesture.durationMs ?: 0L),
            )
        } else {
            context.getString(R.string.status_precision_gesture_empty)
        }
        saveButton.isEnabled = gesture.isComplete()
        playButton.isEnabled = hasPayload
        clearButton.isEnabled = hasPayload
    }
}

private const val PRECISION_GESTURE_OVERLAY_HIDE_DELAY_MS = 500L
