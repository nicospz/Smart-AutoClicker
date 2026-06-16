/*
 * Copyright (C) 2026 Nicolas Espinoza
 *
 * SAC overlay for Throwlet buddy-crop region selection via the frame broker.
 */
package com.buzbuz.smartautoclicker.feature.smart.config.ui.throwlet

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.buzbuz.smartautoclicker.core.ui.views.conditionselector.ConditionSelectorView
import com.buzbuz.smartautoclicker.feature.smart.config.R
import com.buzbuz.smartautoclicker.feature.smart.config.databinding.OverlayValidationMenuBinding
import com.buzbuz.smartautoclicker.core.common.overlays.menu.OverlayMenu

class ThrowletCropPickerMenu(
    private val screenshot: Bitmap,
    private val defaultArea: Rect?,
    private val onResult: (Rect?, Bitmap?) -> Unit,
    private val onOverlayFailed: () -> Unit,
) : OverlayMenu(theme = R.style.ScenarioConfigTheme) {

    private lateinit var viewBinding: OverlayValidationMenuBinding
    private lateinit var selectorView: ConditionSelectorView
    private var finished = false
    private var displayed = false

    override fun animateOverlayView(): Boolean = false

    override fun onCreateMenu(layoutInflater: LayoutInflater): ViewGroup {
        Log.i(TAG, "onCreateMenu lifecycle=${lifecycle.currentState}")
        selectorView = ConditionSelectorView(context, displayConfigManager, ::onSelectorValidityChanged)
        viewBinding = OverlayValidationMenuBinding.inflate(layoutInflater)
        return viewBinding.root
    }

    override fun onCreateOverlayView(): View {
        Log.i(TAG, "onCreateOverlayView")
        return selectorView
    }

    override fun onStart() {
        Log.i(TAG, "onStart lifecycle=${lifecycle.currentState} screenshot=${screenshot.width}x${screenshot.height}")
        super.onStart()
        viewBinding.btnConfirm.setImageResource(R.drawable.ic_confirm)
        setMenuVisibility(View.VISIBLE)
        setOverlayViewVisibility(true)
        selectorView.hide = false
        defaultArea?.let { area ->
            Log.i(TAG, "default crop area=$area")
        }
        selectorView.showCapture(screenshot, defaultArea)
        displayed = true
        Log.i(TAG, "onStart complete lifecycle=${lifecycle.currentState} displayed=$displayed")
    }

    override fun onMenuItemClicked(viewId: Int) {
        Log.i(TAG, "onMenuItemClicked viewId=$viewId")
        when (viewId) {
            R.id.btn_confirm -> onConfirm()
            R.id.btn_cancel -> onCancel()
        }
    }

    override fun onDestroy() {
        Log.w(
            TAG,
            "onDestroy finished=$finished displayed=$displayed lifecycle=${lifecycle.currentState}",
        )
        if (!finished) {
            finished = true
            if (displayed) {
                Log.i(TAG, "onDestroy treating as user/system cancel")
                onResult(null, null)
            } else {
                Log.e(TAG, "onDestroy overlay failed before display")
                onOverlayFailed()
            }
        }
        super.onDestroy()
    }

    private fun onSelectorValidityChanged(isValid: Boolean) {
        Log.d(TAG, "selector valid=$isValid")
        setMenuItemViewEnabled(viewBinding.btnConfirm, isValid, isValid)
    }

    private fun onConfirm() {
        if (finished) return
        try {
            val selection = selectorView.getSelection()
            finished = true
            Log.i(TAG, "onConfirm selection=${selection.first}")
            onResult(selection.first, selection.second)
            back()
        } catch (ex: IllegalStateException) {
            Log.e(TAG, "onConfirm selection failed", ex)
        }
    }

    private fun onCancel() {
        if (finished) return
        finished = true
        Log.i(TAG, "onCancel")
        onResult(null, null)
        back()
    }

    companion object {
        private const val TAG = "ThrowletCropPickerMenu"
    }
}
