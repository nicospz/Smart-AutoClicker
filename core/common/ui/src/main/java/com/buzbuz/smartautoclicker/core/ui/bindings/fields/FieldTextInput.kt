/*
 * Copyright (C) 2023 Kevin Buzeau
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.buzbuz.smartautoclicker.core.ui.bindings.fields

import android.text.Editable
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText

import androidx.annotation.StringRes

import com.buzbuz.smartautoclicker.core.ui.R
import com.buzbuz.smartautoclicker.core.ui.databinding.IncludeFieldTextInputBinding
import com.buzbuz.smartautoclicker.core.ui.utils.OnAfterTextChangedListener
import com.google.android.material.R as MaterialR
import com.google.android.material.textfield.TextInputLayout

fun IncludeFieldTextInputBinding.setLabel(@StringRes labelResId: Int) {
    root.setHint(labelResId)
}

fun IncludeFieldTextInputBinding.setText(text: String?, type: Int = InputType.TYPE_CLASS_TEXT) {
    textField.apply {
        inputType = type
        imeOptions = EditorInfo.IME_ACTION_DONE
        setText(text)
    }
    root.refreshClearIconAfterTextSet(textField)
}

fun IncludeFieldTextInputBinding.setError(isError: Boolean) {
    setError(R.string.input_field_error_required, isError)
}

fun IncludeFieldTextInputBinding.setError(@StringRes messageId: Int, isError: Boolean) {
    root.error = if (isError) root.context.getString(messageId) else null
}

fun IncludeFieldTextInputBinding.setOnTextChangedListener(listener: (Editable) -> Unit) {
    textField.addTextChangedListener(OnAfterTextChangedListener(listener))
}

/**
 * Selects all text on focus so it can be overwritten immediately, and shows the clear icon as soon
 * as the field is focused when it already contains text (including programmatically set values).
 *
 * Call after any other [View.setOnFocusChangeListener] setup so existing listeners are preserved.
 */
fun IncludeFieldTextInputBinding.enableEasyOverwriteOnFocus() {
    root.enableEasyOverwriteOnFocus()
}

fun TextInputLayout.enableEasyOverwriteOnFocus() {
    val editText = editText ?: return

    val previousListener = editText.onFocusChangeListener
    editText.setOnFocusChangeListener { view, hasFocus ->
        previousListener?.onFocusChange(view, hasFocus)

        if (hasFocus) {
            refreshClearIconVisibility(editText)
            view.post {
                editText.selectAll()
                refreshClearIconVisibility(editText)
            }
        } else {
            refreshClearIconVisibility(editText)
        }
    }

    editText.addTextChangedListener(OnAfterTextChangedListener {
        refreshClearIconAfterTextSet(editText)
    })
}

private fun TextInputLayout.refreshClearIconAfterTextSet(editText: EditText) {
    if (editText.hasFocus()) {
        post { refreshClearIconVisibility(editText) }
    }
}

private fun TextInputLayout.refreshClearIconVisibility(editText: EditText) {
    val shouldShow = editText.hasFocus() && !editText.text.isNullOrEmpty()
    isEndIconVisible = shouldShow
    if (!shouldShow) return

    // Material's clear-text delegate normally fades the icon in; toggling visibility alone can leave
    // the icon fully transparent until the user edits the text.
    findViewById<View>(MaterialR.id.text_input_end_icon)?.apply {
        alpha = 1f
        scaleX = 1f
        scaleY = 1f
    }
}
