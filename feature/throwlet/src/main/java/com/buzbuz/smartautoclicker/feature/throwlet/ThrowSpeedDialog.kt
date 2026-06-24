package com.buzbuz.smartautoclicker.feature.throwlet

import android.app.AlertDialog
import android.content.Context
import android.os.Build
import android.text.InputType
import android.view.ContextThemeWrapper
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

object ThrowSpeedDialog {
    fun show(
        context: Context,
        initialTuning: ThrowGestureTuning,
        title: String = "Throw tuning",
        message: String = "Top/bottom/left/right shift the whole throw gesture in pixels.",
        useOverlayWindow: Boolean = true,
        onTuningConfirmed: (ThrowGestureTuning) -> Unit,
        onSaveAndTransform: ((ThrowGestureTuning) -> Unit)? = null,
    ) {
        val themedContext = ContextThemeWrapper(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
        val speedInput = EditText(themedContext).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = "1.2x"
            setSingleLine()
            setText(formatSpeed(initialTuning.speed))
            selectAll()
        }
        val powerInput = EditText(themedContext).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = "1.0x"
            setSingleLine()
            setText(formatMultiplier(initialTuning.power))
        }
        val topInput = offsetInput(themedContext, initialTuning.topOffset)
        val bottomInput = offsetInput(themedContext, initialTuning.bottomOffset)
        val leftInput = offsetInput(themedContext, initialTuning.leftOffset)
        val rightInput = offsetInput(themedContext, initialTuning.rightOffset)
        val container = LinearLayout(themedContext).apply {
            orientation = LinearLayout.VERTICAL
            val padding = dp(context, 24)
            setPadding(padding, dp(context, 8), padding, 0)
            addLabeledInput("Speed", speedInput)
            addLabeledInput("Power", powerInput)
            addLabeledInput("Top", topInput)
            addLabeledInput("Bottom", bottomInput)
            addLabeledInput("Left", leftInput)
            addLabeledInput("Right", rightInput)
        }
        val builder = AlertDialog.Builder(themedContext)
            .setTitle(title)
            .setMessage(message)
            .setView(container)
            .setPositiveButton("Save", null)
            .setNegativeButton(android.R.string.cancel, null)
        if (onSaveAndTransform != null) {
            builder.setNeutralButton("Save and transform", null)
        }
        val dialog = builder.create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val tuning = parseTuning(speedInput, powerInput, topInput, bottomInput, leftInput, rightInput)
                    ?: return@setOnClickListener
                onTuningConfirmed(tuning)
                dialog.dismiss()
            }
            if (onSaveAndTransform != null) {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                    val tuning = parseTuning(speedInput, powerInput, topInput, bottomInput, leftInput, rightInput)
                        ?: return@setOnClickListener
                    onSaveAndTransform(tuning)
                    dialog.dismiss()
                }
            }
            speedInput.requestFocus()
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        }

        if (useOverlayWindow) {
            dialog.window?.setType(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                },
            )
        }
        dialog.show()
    }

    fun formatSpeed(speed: Double): String = formatMultiplier(speed)

    fun formatOffsetSummary(tuning: ThrowGestureTuning): String =
        buildString {
            if (tuning.power != ThrowSpeedStore.DEFAULT_POWER) append(" p=${formatMultiplier(tuning.power)}")
            if (tuning.hasTranslation) append(" dx=${tuning.dx} dy=${tuning.dy}")
        }

    private fun formatMultiplier(value: Double): String =
        String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.') + "x"

    private fun LinearLayout.addLabeledInput(label: String, input: EditText) {
        addView(TextView(context).apply { text = label })
        addView(input)
    }

    private fun offsetInput(context: Context, value: Int): EditText =
        EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
            hint = "0"
            setSingleLine()
            setText(value.toString())
        }

    private fun EditText.offsetValue(): Int =
        text?.toString()?.trim()?.toIntOrNull() ?: 0

    private fun parseTuning(
        speedInput: EditText,
        powerInput: EditText,
        topInput: EditText,
        bottomInput: EditText,
        leftInput: EditText,
        rightInput: EditText,
    ): ThrowGestureTuning? {
        val speed = speedInput.text?.toString()?.toSpeedOrNull()
        if (speed == null) {
            speedInput.error = "Enter a positive number"
            return null
        }
        val power = powerInput.text?.toString()?.toSpeedOrNull()
        if (power == null) {
            powerInput.error = "Enter a positive number"
            return null
        }
        return ThrowGestureTuning(
            speed = speed,
            power = power,
            topOffset = topInput.offsetValue(),
            bottomOffset = bottomInput.offsetValue(),
            leftOffset = leftInput.offsetValue(),
            rightOffset = rightInput.offsetValue(),
        )
    }

    private fun String.toSpeedOrNull(): Double? {
        val parsed = trim()
            .removeSuffix("x")
            .removeSuffix("X")
            .toDoubleOrNull()
        return parsed?.takeIf { it.isFinite() && it > 0.0 }
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
