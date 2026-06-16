/*
 * Copyright (C) 2024 Kevin Buzeau
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
package com.buzbuz.smartautoclicker.feature.smart.config.ui.condition.image

import android.content.Context
import android.graphics.Rect
import android.text.Editable
import android.text.InputType
import android.view.LayoutInflater

import androidx.appcompat.app.AlertDialog

import com.buzbuz.smartautoclicker.core.common.overlays.manager.OverlayManager.Companion.showAsOverlay
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setLabel
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setOnTextChangedListener
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setText
import com.buzbuz.smartautoclicker.core.ui.databinding.IncludeFieldTextInputBinding
import com.buzbuz.smartautoclicker.feature.smart.config.R
import com.buzbuz.smartautoclicker.feature.smart.config.databinding.DialogDetectionAreaCoordinatesBinding

import com.google.android.material.dialog.MaterialAlertDialogBuilder

internal fun Context.showDetectionAreaCoordinatesDialog(
    initialArea: Rect,
    onAreaSelected: (Rect) -> Unit,
) {
    val viewBinding = DialogDetectionAreaCoordinatesBinding.inflate(LayoutInflater.from(this)).apply {
        fieldLeft.setupCoordinateField(R.string.field_detection_area_coord_left, initialArea.left)
        fieldTop.setupCoordinateField(R.string.field_detection_area_coord_top, initialArea.top)
        fieldRight.setupCoordinateField(R.string.field_detection_area_coord_right, initialArea.right)
        fieldBottom.setupCoordinateField(R.string.field_detection_area_coord_bottom, initialArea.bottom)
    }

    val dialog = MaterialAlertDialogBuilder(this)
        .setTitle(R.string.dialog_title_detection_area_coordinates)
        .setView(viewBinding.root)
        .setPositiveButton(android.R.string.ok, null)
        .setNegativeButton(android.R.string.cancel, null)
        .create()

    val updatePositiveButtonState = {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = viewBinding.getEditedArea() != null
    }

    viewBinding.fieldLeft.setOnTextChangedListener { updatePositiveButtonState() }
    viewBinding.fieldTop.setOnTextChangedListener { updatePositiveButtonState() }
    viewBinding.fieldRight.setOnTextChangedListener { updatePositiveButtonState() }
    viewBinding.fieldBottom.setOnTextChangedListener { updatePositiveButtonState() }

    dialog.setOnShowListener {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            viewBinding.getEditedArea()?.let { area ->
                onAreaSelected(area)
                dialog.dismiss()
            }
        }
        updatePositiveButtonState()
    }

    dialog.showAsOverlay()
    viewBinding.fieldLeft.textField.requestFocus()
}

private fun IncludeFieldTextInputBinding.setupCoordinateField(label: Int, value: Int) {
    setLabel(label)
    setText(value.toString(), InputType.TYPE_CLASS_NUMBER)
}

private fun DialogDetectionAreaCoordinatesBinding.getEditedArea(): Rect? {
    val left = fieldLeft.textField.text.getEditedValue() ?: return null
    val top = fieldTop.textField.text.getEditedValue() ?: return null
    val right = fieldRight.textField.text.getEditedValue() ?: return null
    val bottom = fieldBottom.textField.text.getEditedValue() ?: return null

    if (left >= right || top >= bottom) return null

    return Rect(left, top, right, bottom)
}

private fun Editable?.getEditedValue(): Int? =
    try {
        this?.toString()?.toInt()
    } catch (nfEx: NumberFormatException) {
        null
    }
