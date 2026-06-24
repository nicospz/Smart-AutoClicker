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
package com.buzbuz.smartautoclicker.feature.smart.config.ui.mainmenu

import android.content.DialogInterface
import android.content.Intent
import android.util.Log
import android.util.Size
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.View

import androidx.core.content.ContextCompat

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.buzbuz.smartautoclicker.core.base.extensions.setLeftCompoundDrawable

import com.buzbuz.smartautoclicker.core.base.isStopScenarioKey
import com.buzbuz.smartautoclicker.core.common.overlays.base.viewModels
import com.buzbuz.smartautoclicker.core.common.overlays.manager.OverlayManager.Companion.showAsOverlay
import com.buzbuz.smartautoclicker.core.common.overlays.menu.OverlayMenu
import com.buzbuz.smartautoclicker.core.ui.utils.AnimatedStatesImageButtonController
import com.buzbuz.smartautoclicker.core.ui.utils.bindHoldActionMenuItem
import com.buzbuz.smartautoclicker.core.ui.utils.getDynamicColorsContext
import com.buzbuz.smartautoclicker.core.ui.utils.holdActionMenuItemIcon
import com.buzbuz.smartautoclicker.core.ui.views.touchprobe.TouchProbeOverlayView
import com.buzbuz.smartautoclicker.feature.smart.config.R
import com.buzbuz.smartautoclicker.feature.smart.config.databinding.OverlayMenuActionsPanelBinding
import com.buzbuz.smartautoclicker.feature.smart.config.databinding.OverlayMenuBinding
import com.buzbuz.smartautoclicker.feature.smart.config.di.ScenarioConfigViewModelsEntryPoint
import com.buzbuz.smartautoclicker.feature.smart.config.ui.common.starters.newRestartMediaProjectionStarterOverlay
import com.buzbuz.smartautoclicker.feature.smart.config.ui.mainmenu.debugging.LiveDebuggingActionsAdapter
import com.buzbuz.smartautoclicker.feature.smart.config.ui.mainmenu.debugging.LiveDebuggingUiState
import com.buzbuz.smartautoclicker.feature.smart.config.ui.mainmenu.debugging.LiveDebuggingViewModel
import com.buzbuz.smartautoclicker.feature.smart.config.ui.scenario.ScenarioDialog

import com.google.android.material.dialog.MaterialAlertDialogBuilder

import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * [OverlayMenu] implementation for displaying the main menu overlay.
 *
 * This is the menu displayed once the service is started via the [com.buzbuz.smartautoclicker.scenarios.ScenarioActivity]
 * once the user has selected a scenario to be used. It allows the user to start the detection on the currently loaded
 * scenario, as well as editing the attached list of events.
 *
 * When the debug view setting is enabled, a touch probe overlay can be toggled to preview taps and swipes without
 * forwarding them to the application below.
 */
class MainMenu(private val onStopClicked: () -> Unit) : OverlayMenu(
    theme = R.style.ScenarioConfigTheme,
    holdActionMenuEnabled = true,
) {

    /** The view model for this menu. */
    private val viewModel: MainMenuModel by viewModels(
        entryPoint = ScenarioConfigViewModelsEntryPoint::class.java,
        creator = { mainMenuViewModel() },
    )

    /** The view model for the live debugging. */
    private val debuggingViewModel: LiveDebuggingViewModel by viewModels(
        entryPoint = ScenarioConfigViewModelsEntryPoint::class.java,
        creator = { liveDebuggingViewModel() },
    )

    private var isHiddenForPaywall: Boolean = false

    /** View binding for the content of the overlay. */
    private lateinit var viewBinding: OverlayMenuBinding
    /** View binding for the detached hold-action panel. */
    private lateinit var actionsPanelBinding: OverlayMenuActionsPanelBinding
    /** Controls the animations of the play/pause button. */
    private lateinit var playPauseButtonController: AnimatedStatesImageButtonController
    /** Adapter upon actions being executed while in live debugging. */
    private val debugLiveActionsAdapter: LiveDebuggingActionsAdapter = LiveDebuggingActionsAdapter()

    /** The coroutine job for the observable used in debug mode. Null when not in debug mode. */
    private var debugObservableJob: Job? = null

    /** Tells if the touch probe overlay is currently active. */
    private var isTouchProbeActive: Boolean = false

    /**
     * Tells if this service has handled onKeyEvent with ACTION_DOWN for a key in order to return
     * the correct value when ACTION_UP is received.
     */
    private var keyDownHandled: Boolean = false

    override fun onCreateMenu(layoutInflater: LayoutInflater): ViewGroup {
        playPauseButtonController = AnimatedStatesImageButtonController(
            context = context,
            state1StaticRes = R.drawable.ic_play_arrow,
            state2StaticRes = R.drawable.ic_pause,
            state1to2AnimationRes = R.drawable.anim_play_pause,
            state2to1AnimationRes = R.drawable.anim_pause_play,
        )
        viewBinding = OverlayMenuBinding.inflate(layoutInflater)
        playPauseButtonController.attachView(viewBinding.btnHub)

        return viewBinding.root
    }

    override fun onCreateHoldActionPanel(layoutInflater: LayoutInflater): ViewGroup {
        actionsPanelBinding = OverlayMenuActionsPanelBinding.inflate(layoutInflater)
        actionsPanelBinding.btnStop.bindHoldActionMenuItem(
            iconRes = R.drawable.ic_stop,
            labelRes = com.buzbuz.smartautoclicker.core.ui.R.string.menu_hold_action_stop,
            contentDescriptionRes = com.buzbuz.smartautoclicker.core.ui.R.string.content_desc_stop_clicker,
        )
        actionsPanelBinding.btnClickList.bindHoldActionMenuItem(
            iconRes = R.drawable.ic_settings_filled,
            labelRes = R.string.menu_hold_action_events,
            contentDescriptionRes = R.string.content_desc_open_event_list,
        )
        actionsPanelBinding.btnFavoriteScenarios.bindHoldActionMenuItem(
            iconRes = R.drawable.ic_favorite_outline,
            labelRes = R.string.menu_hold_action_favorites,
            contentDescriptionRes = R.string.content_desc_open_favorite_scenarios,
        )
        actionsPanelBinding.btnTouchProbe.bindHoldActionMenuItem(
            iconRes = R.drawable.ic_click,
            labelRes = R.string.menu_hold_action_touch_probe,
            contentDescriptionRes = R.string.content_desc_touch_probe,
        )
        return actionsPanelBinding.root
    }

    override fun onCreate() {
        Log.i(TAG, "onCreate lifecycle=${lifecycle.currentState}")
        super.onCreate()

        // Ensure the debug view state is correct
        viewBinding.layoutDebug.visibility = View.GONE
        viewBinding.actionList.adapter = debugLiveActionsAdapter
        viewBinding.actionList.itemAnimator = null

        Log.i(
            TAG,
            "onCreate complete touchProbeEnabled=${viewModel.isTouchProbeFeatureEnabled()} " +
                "lifecycle=${lifecycle.currentState}",
        )

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                launch { viewModel.paywallIsVisible.collect(::updateVisibilityForPaywall) }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.isStartButtonEnabled.collect(::updatePlayPauseButtonEnabledState) }
                launch { viewModel.isMediaProjectionStarted.collect(::updateProjectionErrorBadge) }
                launch { viewModel.detectionState.collect(::updateDetectionState) }
                launch { viewModel.nativeLibError.collect(::showNativeLibErrorDialogIfNeeded) }
                launch { debuggingViewModel.isDebugging.collect(::updateDebugOverlayViewVisibility) }
            }
        }
    }

    override fun onStart() {
        Log.i(TAG, "onStart lifecycle=${lifecycle.currentState} touchProbeActive=$isTouchProbeActive")
        super.onStart()

        if (isTouchProbeActive) restoreTouchProbeOverlay()

        viewModel.monitorViews(
            playMenuButton = viewBinding.btnHub,
            configMenuButton = actionsPanelBinding.btnClickList,
        )

        // Start loading advertisement if needed
        viewModel.loadAdIfNeeded(context)
    }

    override fun onResume() {
        super.onResume()

        updateTouchProbeFeatureVisibility()
        Log.i(
            TAG,
            "onResume menuLayout=${viewBinding.root.visibility} menuBackground=${viewBinding.menuBackground.visibility} " +
                "size=${viewBinding.root.width}x${viewBinding.root.height} lifecycle=${lifecycle.currentState}",
        )
    }

    override fun onStop() {
        Log.i(TAG, "onStop lifecycle=${lifecycle.currentState}")
        super.onStop()
        viewModel.stopViewMonitoring()
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy lifecycle=${lifecycle.currentState}")
        super.onDestroy()
        playPauseButtonController.detachView()
    }

    override fun onKeyEvent(keyEvent: KeyEvent): Boolean {
        if (!keyEvent.isStopScenarioKey()) return false

        when (keyEvent.action) {
            KeyEvent.ACTION_DOWN -> {
                if (viewModel.stopDetection()) {
                    keyDownHandled = true
                    return true
                }
            }

            KeyEvent.ACTION_UP -> {
                if (keyDownHandled) {
                    keyDownHandled = false
                    return true
                }
            }
        }

        return false
    }

    override fun onHubQuickTap() {
        onPlayPauseClicked()
    }

    override fun onMenuItemClicked(viewId: Int) {
        when (viewId) {
            R.id.btn_click_list -> onConfigureClicked()
            R.id.btn_favorite_scenarios -> onFavoriteScenariosClicked()
            R.id.btn_stop -> onStopClicked()
            R.id.btn_touch_probe -> onTouchProbeClicked()
        }
    }

    override fun getWindowMaximumSize(backgroundView: ViewGroup): Size {
        val railSize = super.getWindowMaximumSize(backgroundView)
        val debugPanelWidth = context.resources.getDimensionPixelSize(R.dimen.overlay_debug_panel_width)
        val debugPanelHeight = context.resources.getDimensionPixelSize(R.dimen.overlay_debug_panel_height)
        return Size(
            railSize.width + debugPanelWidth,
            maxOf(railSize.height, debugPanelHeight),
        )
    }

    fun onMediaProjectionLost() {
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)) return

        overlayManager.navigateUpToRoot(context)
        viewModel.cancelScenarioChanges()
    }

    private fun onConfigureClicked() {
        Log.i(TAG, "onConfigureClicked")
        viewModel.stopDetection()

        if (viewModel.shouldRestartMediaProjection()) {
            Log.i(TAG, "onConfigureClicked requires MediaProjection restart")
            showRestartMediaProjectionScreen()
            return
        }

        viewModel.startScenarioEdition {
            Log.i(TAG, "onConfigureClicked opening scenario config dialog")
            showScenarioConfigDialog()
        }
    }

    private fun onPlayPauseClicked() {
        Log.i(TAG, "onPlayPauseClicked")
        if (viewModel.shouldRestartMediaProjection()) {
            Log.i(TAG, "onPlayPauseClicked requires MediaProjection restart")
            showRestartMediaProjectionScreen()
            return
        }

        viewModel.toggleDetection(context)
    }

    private fun onFavoriteScenariosClicked() {
        Log.i(TAG, "onFavoriteScenariosClicked")
        context.sendBroadcast(
            Intent(ACTION_SHOW_FAVORITE_SCENARIOS)
                .setPackage(context.packageName)
        )
    }

    private fun onTouchProbeClicked() {
        if (isTouchProbeActive) disableTouchProbe()
        else enableTouchProbe()
    }

    private fun enableTouchProbe() {
        Log.d(TAG, "enableTouchProbe")
        if (!attachScreenOverlayView(TouchProbeOverlayView(context))) {
            Log.e(TAG, "enableTouchProbe failed: attachScreenOverlayView returned false")
            return
        }

        isTouchProbeActive = true
        setOverlayViewVisibility(true)
        updateTouchProbeButtonState(isActive = true)
        Log.i(TAG, "enableTouchProbe success")
    }

    private fun disableTouchProbe() {
        Log.d(TAG, "disableTouchProbe")
        isTouchProbeActive = false
        (screenOverlayView as? TouchProbeOverlayView)?.clearMarkers()
        setOverlayViewVisibility(false)
        detachScreenOverlayView()
        updateTouchProbeButtonState(isActive = false)
    }

    private fun restoreTouchProbeOverlay() {
        Log.d(TAG, "restoreTouchProbeOverlay screenOverlayAttached=${screenOverlayView != null}")
        if (screenOverlayView == null && !attachScreenOverlayView(TouchProbeOverlayView(context))) {
            Log.e(TAG, "restoreTouchProbeOverlay failed: attachScreenOverlayView returned false")
            isTouchProbeActive = false
            updateTouchProbeButtonState(isActive = false)
            return
        }

        setOverlayViewVisibility(true)
        updateTouchProbeButtonState(isActive = true)
    }

    private fun updateTouchProbeFeatureVisibility() {
        val enabled = viewModel.isTouchProbeFeatureEnabled()
        Log.d(TAG, "updateTouchProbeFeatureVisibility enabled=$enabled touchProbeActive=$isTouchProbeActive")
        if (enabled) {
            setMenuItemVisibility(actionsPanelBinding.btnTouchProbe, true)
            refreshTouchProbeButtonState()
        } else {
            if (isTouchProbeActive) disableTouchProbe()
            setMenuItemVisibility(actionsPanelBinding.btnTouchProbe, false)
        }
    }

    override fun onMenuLayoutResizeAnimationsCompleted() {
        super.onMenuLayoutResizeAnimationsCompleted()
        refreshTouchProbeButtonState()
    }

    private fun refreshTouchProbeButtonState() {
        if (actionsPanelBinding.btnTouchProbe.visibility != View.VISIBLE) return
        updateTouchProbeButtonState(isActive = isTouchProbeActive)
    }

    private fun updateTouchProbeButtonState(isActive: Boolean) {
        setMenuItemViewEnabled(actionsPanelBinding.btnTouchProbe, enabled = isActive, clickable = true)
        val icon = actionsPanelBinding.btnTouchProbe.holdActionMenuItemIcon()
        if (isActive) {
            icon.setColorFilter(
                ContextCompat.getColor(context, com.buzbuz.smartautoclicker.core.ui.R.color.overlayViewPrimary),
            )
        } else {
            icon.clearColorFilter()
            icon.imageTintList = ContextCompat.getColorStateList(
                context,
                com.buzbuz.smartautoclicker.core.ui.R.color.overlayMenuButtons,
            )
        }
    }

    /** Refresh the play menu item according to the scenario state. */
    private fun updatePlayPauseButtonEnabledState(canStartDetection: Boolean) =
        setMenuItemViewEnabled(viewBinding.btnHub, canStartDetection)

    /** Refresh the menu layout according to the detection state. */
    private fun updateDetectionState(newState: UiState) {
        val currentState = viewBinding.btnHub.tag
        if (currentState == newState) return

        viewBinding.btnHub.tag = newState
        when (newState) {
            UiState.Idle -> {
                setHoldActionMenuInteractionEnabled(true)
                setHoldActionLipVisible(true)
                if (currentState == null) {
                    playPauseButtonController.toState1(false)
                } else {
                    playPauseButtonController.toState1(true)
                }
                setMenuItemVisibility(actionsPanelBinding.btnStop, true)
                setMenuItemVisibility(actionsPanelBinding.btnClickList, true)
                setMenuItemVisibility(actionsPanelBinding.btnFavoriteScenarios, true)
            }

            UiState.Detecting -> {
                setHoldActionMenuInteractionEnabled(false)
                setHoldActionLipVisible(false)
                if (currentState == null) {
                    playPauseButtonController.toState2(false)
                } else {
                    playPauseButtonController.toState2(true)
                }
                setMenuItemVisibility(actionsPanelBinding.btnStop, true)
                setMenuItemVisibility(actionsPanelBinding.btnClickList, false)
                setMenuItemVisibility(actionsPanelBinding.btnFavoriteScenarios, false)
            }
        }
    }

    private fun setHoldActionLipVisible(visible: Boolean) {
        viewBinding.holdActionCorner.visibility = if (visible) View.VISIBLE else View.INVISIBLE
    }

    private fun updateVisibilityForPaywall(isHidden: Boolean) {
        Log.d(TAG, "updateVisibilityForPaywall isHidden=$isHidden wasHiddenForPaywall=$isHiddenForPaywall")
        if (isHidden) {
            isHiddenForPaywall = true
            hide()
        } else if (isHiddenForPaywall) {
            isHiddenForPaywall = false
            show()
        }
    }

    private fun updateProjectionErrorBadge(isProjectionStarted: Boolean) {
        viewBinding.errorBadge.visibility = if (isProjectionStarted) View.GONE else View.VISIBLE
    }

    /**
     * Change the debug state of this UI.
     * @param isVisible true when the debug view should be shown, false to hide it.
     */
    private fun updateDebugOverlayViewVisibility(isVisible: Boolean) {
        if (isVisible && debugObservableJob == null) {
            viewBinding.layoutDebug.visibility = View.VISIBLE
            debugObservableJob = observeDebugValues()

        } else if (!isVisible && debugObservableJob != null) {
            debugObservableJob?.cancel()
            debugObservableJob = null

            updateLiveDebugUiState(null)
            viewBinding.layoutDebug.visibility = View.GONE
        }

        forceWindowResize()
    }

    /**
     * Observe the values for the debug and update the debug views.
     * @return the coroutine job for the observable. Can be cancelled to stop the observation.
     */
    private fun observeDebugValues() = lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.STARTED) {
            launch {
                debuggingViewModel.debugLastPositive.collect(::updateLiveDebugUiState)
            }
        }
    }

    private fun updateLiveDebugUiState(uiState: LiveDebuggingUiState?) {
        viewBinding.apply {
            debugEventName.apply {
                text = uiState?.eventName
                setLeftCompoundDrawable(uiState?.eventIcon)
            }

            debugEventFulfilledCount.apply {
                text = uiState?.eventFulfilledCount
                setLeftCompoundDrawable(if (uiState != null) R.drawable.ic_confirm else null)
            }

            debugEventConditionComputeTime.apply {
                text = uiState?.eventDuration
                setLeftCompoundDrawable(if (uiState != null) R.drawable.ic_duration else null)
            }

            debugLiveActionsAdapter.submitList(uiState?.actions)
        }
    }

    private fun showScenarioConfigDialog() =
        overlayManager.navigateTo(
            context = context,
            newOverlay = ScenarioDialog(
                onConfigDiscarded = {
                    viewModel.cancelScenarioChanges()
                    updateTouchProbeFeatureVisibility()
                },
                onConfigSaved = {
                    viewModel.saveScenarioChanges { success ->
                        updateTouchProbeFeatureVisibility()
                        if (!success) showScenarioSaveErrorDialog()
                    }
                },
            ),
            hideCurrent = true,
        )

    private fun showScenarioSaveErrorDialog() {
        MaterialAlertDialogBuilder(context.getDynamicColorsContext(R.style.AppTheme))
            .setTitle(R.string.dialog_overlay_title_warning)
            .setMessage(R.string.error_dialog_message_scenario_saving)
            .setPositiveButton(R.string.generic_modify) { _: DialogInterface, _: Int ->
                showScenarioConfigDialog()
            }
            .setNegativeButton(android.R.string.cancel) { _: DialogInterface, _: Int ->
                viewModel.cancelScenarioChanges()
            }
            .create()
            .showAsOverlay()
    }

    private fun showNativeLibErrorDialogIfNeeded(haveError: Boolean) {
        if (!haveError) return

        MaterialAlertDialogBuilder(context.getDynamicColorsContext(R.style.AppTheme))
            .setTitle(R.string.dialog_overlay_title_warning)
            .setMessage(R.string.error_dialog_message_error_native_lib)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                onStopClicked()
            }
            .create()
            .showAsOverlay()
    }

    private fun showRestartMediaProjectionScreen() {
        overlayManager.navigateTo(
            context = context,
            newOverlay = newRestartMediaProjectionStarterOverlay(context),
            hideCurrent = true,
        )
    }

    private companion object {
        private const val TAG = "MainMenu"
        private const val ACTION_SHOW_FAVORITE_SCENARIOS =
            "com.buzbuz.smartautoclicker.action.SHOW_FAVORITE_SCENARIOS"
    }
}
