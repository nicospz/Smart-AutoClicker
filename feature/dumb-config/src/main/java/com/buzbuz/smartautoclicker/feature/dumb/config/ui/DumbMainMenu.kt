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
package com.buzbuz.smartautoclicker.feature.dumb.config.ui

import android.content.Intent
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle

import com.buzbuz.smartautoclicker.core.base.identifier.Identifier
import com.buzbuz.smartautoclicker.core.base.isStopScenarioKey
import com.buzbuz.smartautoclicker.core.common.overlays.base.viewModels
import com.buzbuz.smartautoclicker.core.common.overlays.menu.OverlayMenu
import com.buzbuz.smartautoclicker.core.ui.utils.AnimatedStatesImageButtonController
import com.buzbuz.smartautoclicker.core.ui.utils.bindHoldActionMenuItem
import com.buzbuz.smartautoclicker.feature.dumb.config.R
import com.buzbuz.smartautoclicker.feature.dumb.config.databinding.OverlayDumbActionsPanelBinding
import com.buzbuz.smartautoclicker.feature.dumb.config.databinding.OverlayDumbMainMenuBinding
import com.buzbuz.smartautoclicker.feature.dumb.config.di.DumbConfigViewModelsEntryPoint
import com.buzbuz.smartautoclicker.feature.dumb.config.ui.brief.DumbScenarioBriefMenu
import com.buzbuz.smartautoclicker.feature.dumb.config.ui.scenario.DumbScenarioDialog

import kotlinx.coroutines.launch

class DumbMainMenu(
    private val dumbScenarioId: Identifier,
    private val onStopClicked: () -> Unit,
) : OverlayMenu(theme = R.style.AppTheme, holdActionMenuEnabled = true) {

    /** The view model for this menu. */
    private val viewModel: DumbMainMenuModel by viewModels(
        entryPoint = DumbConfigViewModelsEntryPoint::class.java,
        creator = { dumbMainMenuModel() },
    )

    /** View binding for the content of the overlay. */
    private lateinit var viewBinding: OverlayDumbMainMenuBinding
    /** View binding for the detached hold-action panel. */
    private lateinit var actionsPanelBinding: OverlayDumbActionsPanelBinding
    /** Controls the animations of the play/pause button. */
    private lateinit var playPauseButtonController: AnimatedStatesImageButtonController

    /**
     * Tells if this service has handled onKeyEvent with ACTION_DOWN for a key in order to return
     * the correct value when ACTION_UP is received.
     */
    private var keyDownHandled: Boolean = false

    override fun onCreate() {
        super.onCreate()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.isPlaying.collect(::updateMenuPlayingState) }
                launch { viewModel.canPlay.collect(::updatePlayPauseButtonEnabledState) }
            }
        }
    }

    override fun onCreateMenu(layoutInflater: LayoutInflater): ViewGroup {
        playPauseButtonController = AnimatedStatesImageButtonController(
            context = context,
            state1StaticRes = R.drawable.ic_play_arrow,
            state2StaticRes = R.drawable.ic_pause,
            state1to2AnimationRes = R.drawable.anim_play_pause,
            state2to1AnimationRes = R.drawable.anim_pause_play,
        )

        viewBinding = OverlayDumbMainMenuBinding.inflate(layoutInflater).apply {
            playPauseButtonController.attachView(btnHub)
        }

        return viewBinding.root
    }

    override fun onCreateHoldActionPanel(layoutInflater: LayoutInflater): ViewGroup {
        actionsPanelBinding = OverlayDumbActionsPanelBinding.inflate(layoutInflater)
        actionsPanelBinding.btnStop.bindHoldActionMenuItem(
            iconRes = R.drawable.ic_stop,
            labelRes = com.buzbuz.smartautoclicker.core.ui.R.string.menu_hold_action_stop,
            contentDescriptionRes = com.buzbuz.smartautoclicker.core.ui.R.string.content_desc_stop_clicker,
        )
        actionsPanelBinding.btnShowActions.bindHoldActionMenuItem(
            iconRes = R.drawable.ic_show_path,
            labelRes = R.string.menu_hold_action_preview,
            contentDescriptionRes = R.string.content_desc_show_actions,
        )
        actionsPanelBinding.btnActionList.bindHoldActionMenuItem(
            iconRes = R.drawable.ic_settings_filled,
            labelRes = R.string.menu_hold_action_configure,
            contentDescriptionRes = R.string.content_desc_open_action_list,
        )
        actionsPanelBinding.btnFavoriteScenarios.bindHoldActionMenuItem(
            iconRes = R.drawable.ic_favorite_outline,
            labelRes = R.string.menu_hold_action_favorites,
            contentDescriptionRes = R.string.content_desc_open_favorite_scenarios,
        )
        return actionsPanelBinding.root
    }

    override fun onDestroy() {
        super.onDestroy()
        playPauseButtonController.detachView()
        viewModel.stopEdition()
    }

    override fun onKeyEvent(keyEvent: KeyEvent): Boolean {
        if (!keyEvent.isStopScenarioKey()) return false

        when (keyEvent.action) {
            KeyEvent.ACTION_DOWN -> {
                if (viewModel.stopScenarioPlay()) {
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

    /** Refresh the play menu item according to the scenario state. */
    private fun updatePlayPauseButtonEnabledState(canStartDetection: Boolean) =
        setMenuItemViewEnabled(viewBinding.btnHub, canStartDetection)

    private fun updateMenuPlayingState(isPlaying: Boolean) {
        val currentState = viewBinding.btnHub.tag
        if (currentState == isPlaying) return

        viewBinding.btnHub.tag = isPlaying
        if (isPlaying) {
            setHoldActionMenuInteractionEnabled(false)
            setHoldActionLipVisible(false)
            if (currentState == null) {
                playPauseButtonController.toState2(false)
            } else {
                playPauseButtonController.toState2(true)
            }
            setMenuItemVisibility(actionsPanelBinding.btnStop, true)
            setMenuItemVisibility(actionsPanelBinding.btnShowActions, false)
            setMenuItemVisibility(actionsPanelBinding.btnActionList, false)
            setMenuItemVisibility(actionsPanelBinding.btnFavoriteScenarios, false)
        } else {
            setHoldActionMenuInteractionEnabled(true)
            setHoldActionLipVisible(true)
            if (currentState == null) {
                playPauseButtonController.toState1(false)
            } else {
                playPauseButtonController.toState1(true)
            }
            setMenuItemVisibility(actionsPanelBinding.btnStop, true)
            setMenuItemVisibility(actionsPanelBinding.btnShowActions, true)
            setMenuItemVisibility(actionsPanelBinding.btnActionList, true)
            setMenuItemVisibility(actionsPanelBinding.btnFavoriteScenarios, true)
        }
    }

    private fun setHoldActionLipVisible(visible: Boolean) {
        viewBinding.holdActionCorner.visibility = if (visible) View.VISIBLE else View.INVISIBLE
    }

    override fun onHubQuickTap() {
        onPlayPauseClicked()
    }

    override fun onMenuItemClicked(viewId: Int) {
        when (viewId) {
            R.id.btn_stop -> onStopClicked()
            R.id.btn_show_actions -> onShowBriefClicked()
            R.id.btn_action_list -> onDumbScenarioConfigClicked()
            R.id.btn_favorite_scenarios -> onFavoriteScenariosClicked()
        }
    }

    private fun onPlayPauseClicked() {
        viewModel.toggleScenarioPlay()
    }

    private fun onFavoriteScenariosClicked() {
        context.sendBroadcast(
            Intent(ACTION_SHOW_FAVORITE_SCENARIOS)
                .setPackage(context.packageName)
        )
    }

    private fun onShowBriefClicked() {
        viewModel.startEdition(dumbScenarioId) {
            overlayManager.navigateTo(
                context = context,
                newOverlay = DumbScenarioBriefMenu(
                    onConfigSaved = viewModel::saveEditions
                ),
                hideCurrent = true,
            )
        }
    }

    private fun onDumbScenarioConfigClicked() {
        viewModel.startEdition(dumbScenarioId) {
            overlayManager.navigateTo(
                context = context,
                newOverlay = DumbScenarioDialog(
                    onConfigSaved = viewModel::saveEditions,
                    onConfigDiscarded = viewModel::stopEdition,
                ),
                hideCurrent = true,
            )
        }
    }

}

private const val ACTION_SHOW_FAVORITE_SCENARIOS = "com.buzbuz.smartautoclicker.action.SHOW_FAVORITE_SCENARIOS"
