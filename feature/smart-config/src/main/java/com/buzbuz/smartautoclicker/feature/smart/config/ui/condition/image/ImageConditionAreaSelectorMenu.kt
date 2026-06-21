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

import android.graphics.Rect
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast

import androidx.lifecycle.Lifecycle

import com.buzbuz.smartautoclicker.core.common.overlays.base.viewModels
import com.buzbuz.smartautoclicker.core.common.overlays.menu.OverlayMenu
import com.buzbuz.smartautoclicker.core.ui.views.conditionselector.ConditionSelectorView
import com.buzbuz.smartautoclicker.feature.smart.config.R
import com.buzbuz.smartautoclicker.feature.smart.config.databinding.OverlayAreaSelectorMenuBinding
import com.buzbuz.smartautoclicker.feature.smart.config.di.ScenarioConfigViewModelsEntryPoint

class ImageConditionAreaSelectorMenu(
    private val selectorState: SelectorUiState,
    private val onAreaSelected: (Rect) -> Unit,
) : OverlayMenu() {

    /** The view model for this dialog. */
    private val viewModel: ImageConditionAreaSelectorViewModel by viewModels(
        entryPoint = ScenarioConfigViewModelsEntryPoint::class.java,
        creator = { imageConditionAreaSelectorViewModel() },
    )

    /** The view binding for the overlay menu. */
    private lateinit var viewBinding: OverlayAreaSelectorMenuBinding
    /** The view displaying the screenshot and the selector for the search area. */
    private lateinit var selectorView: ConditionSelectorView
    /** True when a zoomable screenshot canvas is shown. */
    private var hasScreenshot = false

    override fun animateOverlayView(): Boolean = false

    override fun onCreateMenu(layoutInflater: LayoutInflater): ViewGroup {
        selectorView = ConditionSelectorView(context, displayConfigManager, ::onSelectorValidityChanged).apply {
            lockSelectionOnViewportChanges = true
        }
        viewBinding = OverlayAreaSelectorMenuBinding.inflate(layoutInflater)
        return viewBinding.root
    }

    override fun onCreateOverlayView(): View = selectorView

    override fun onStart() {
        super.onStart()

        selectorView.hide = true
        setMenuVisibility(View.VISIBLE)
        setOverlayViewVisibility(false)
        setMenuItemViewEnabled(viewBinding.btnConfirm, false)
        startAreaSelection(selectorState)
    }

    override fun onMenuItemClicked(viewId: Int) {
        when (viewId) {
            R.id.btn_confirm -> onConfirm()
            R.id.btn_cancel -> onCancel()
            R.id.btn_reset -> onReset()
        }
    }

    private fun startAreaSelection(selectorState: SelectorUiState) {
        setMenuVisibility(View.GONE)

        viewModel.takeScreenshot(
            onSuccess = { screenshot ->
                hasScreenshot = true
                selectorView.showCapture(
                    bitmap = screenshot,
                    defaultSelection = selectorState.initialArea,
                    minimalSelection = selectorState.minimalArea,
                )
                selectorView.hide = false
                onAreaSelectorReady()
                Log.i(TAG, "Zoomable area selector ready")
            },
            onFailure = {
                hasScreenshot = false
                selectorView.setSelection(selectorState.initialArea, selectorState.minimalArea)
                selectorView.hide = false
                onAreaSelectorReady(enableConfirm = true)
                Toast.makeText(
                    context,
                    R.string.error_detection_area_screenshot,
                    Toast.LENGTH_LONG,
                ).show()
                Log.w(TAG, "Screenshot unavailable, using live area selector without zoom")
            },
        )
    }

    private fun onAreaSelectorReady(enableConfirm: Boolean = false) {
        setOverlayViewVisibility(true)
        setMenuVisibility(View.VISIBLE)
        syncConfirmButtonState(forceEnable = enableConfirm)
        ensureMenuInteractive()
    }

    /** Sync confirm button with selector validity (handles missed callbacks during setup). */
    private fun syncConfirmButtonState(forceEnable: Boolean = false) {
        val isEnabled = forceEnable || selectorView.hasValidSelection()
        setMenuItemViewEnabled(viewBinding.btnConfirm, isEnabled, isEnabled)
    }

    /** hideAll() skips the automatic resume; menu clicks require the RESUMED state. */
    private fun ensureMenuInteractive() {
        if (lifecycle.currentState == Lifecycle.State.STARTED) {
            show()
        }
    }

    private fun onSelectorValidityChanged(isValid: Boolean) {
        if (!hasScreenshot) return
        setMenuItemViewEnabled(viewBinding.btnConfirm, isValid, isValid)
    }

    private fun onConfirm() {
        onAreaSelected(selectorView.getSelectedArea())
        back()
        overlayManager.restoreVisibility()
    }

    private fun onCancel() {
        back()
        overlayManager.restoreVisibility()
    }

    private fun onReset() {
        selectorView.resetSelection(selectorState.minimalArea, selectorState.minimalArea)
    }

    companion object {
        private const val TAG = "ImageConditionAreaSelectorMenu"
    }
}
