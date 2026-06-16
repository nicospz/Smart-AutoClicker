/*
 * Copyright (C) 2026 Kevin Buzeau
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
package com.buzbuz.smartautoclicker.scenarios.favorite

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle

import com.buzbuz.smartautoclicker.R
import com.buzbuz.smartautoclicker.core.display.recorder.showMediaProjectionWarning

import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint

import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class FavoriteScenarioLauncherActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_SMART_SCENARIO_ID =
            "com.buzbuz.smartautoclicker.scenarios.favorite.EXTRA_SMART_SCENARIO_ID"

        fun getSmartProjectionIntent(context: Context, scenarioId: Long): Intent =
            Intent(context, FavoriteScenarioLauncherActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                .putExtra(EXTRA_SMART_SCENARIO_ID, scenarioId)
    }

    @Inject lateinit var pickerController: FavoriteScenarioPickerController

    private val viewModel: FavoriteScenarioLauncherViewModel by viewModels()

    private lateinit var projectionActivityResult: ActivityResultLauncher<Intent>

    private var dialogShown: Boolean = false
    private var requestedScenario: FavoriteScenarioItem.Smart? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)

        val smartScenarioId = intent.getLongExtra(EXTRA_SMART_SCENARIO_ID, -1L)
        if (smartScenarioId != -1L) {
            startSmartScenarioProjectionFlow(smartScenarioId)
            return
        }

        pickerController.show(
            onUnavailable = { startActivityFavoritesPicker() },
            onShown = { window.decorView.post { moveTaskToBack(true) } },
            onDismiss = { finish() },
        )
    }

    private fun startActivityFavoritesPicker() {
        projectionActivityResult = registerProjectionResult()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.favoriteScenarios.collect { scenarios ->
                    scenarios ?: return@collect
                    if (!dialogShown) showFavoritesActivityDialog(scenarios)
                }
            }
        }
    }

    private fun startSmartScenarioProjectionFlow(scenarioId: Long) {
        projectionActivityResult = registerProjectionResult()

        lifecycleScope.launch {
            val scenario = viewModel.getSmartScenario(scenarioId) ?: run {
                finish()
                return@launch
            }

            val item = FavoriteScenarioItem.Smart(scenario)
            viewModel.startPermissionFlowIfNeeded(
                activity = this@FavoriteScenarioLauncherActivity,
                onMandatoryDenied = ::finish,
                onAllGranted = {
                    viewModel.startTroubleshootingFlowIfNeeded(this@FavoriteScenarioLauncherActivity) {
                        requestedScenario = item
                        requestSmartScenarioProjection(item)
                    }
                },
            )
        }
    }

    private fun registerProjectionResult(): ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK) {
                finish()
                return@registerForActivityResult
            }

            requestedScenario?.let { startSmartScenario(result, it.scenario) } ?: finish()
        }

    private fun showFavoritesActivityDialog(scenarios: List<FavoriteScenarioItem>) {
        dialogShown = true

        if (scenarios.isEmpty()) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_title_favorite_scenarios)
                .setMessage(R.string.message_no_favorite_scenarios)
                .setPositiveButton(android.R.string.ok) { _, _ -> finish() }
                .setOnCancelListener { finish() }
                .show()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_title_favorite_scenarios)
            .setItems(scenarios.map { it.getDisplayName(this) }.toTypedArray()) { _, which ->
                startScenario(scenarios[which])
            }
            .setNegativeButton(android.R.string.cancel) { _: DialogInterface, _: Int -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun startScenario(item: FavoriteScenarioItem) {
        viewModel.startPermissionFlowIfNeeded(
            activity = this,
            onMandatoryDenied = ::finish,
            onAllGranted = {
                viewModel.startTroubleshootingFlowIfNeeded(this) {
                    when (item) {
                        is FavoriteScenarioItem.Dumb -> startDumbScenario(item)
                        is FavoriteScenarioItem.Smart -> requestSmartScenarioProjection(item)
                    }
                }
            },
        )
    }

    private fun startDumbScenario(item: FavoriteScenarioItem.Dumb) {
        if (viewModel.loadDumbScenario(item.scenario)) finish()
        else Toast.makeText(this, R.string.toast_denied_foreground_permission, Toast.LENGTH_SHORT).show()
    }

    private fun requestSmartScenarioProjection(item: FavoriteScenarioItem.Smart) {
        requestedScenario = item
        projectionActivityResult.showMediaProjectionWarning(
            context = this,
            forceEntireScreen = viewModel.isEntireScreenCaptureForced(),
            onError = { showUnsupportedDeviceDialog() },
        )
    }

    private fun startSmartScenario(result: ActivityResult, scenario: com.buzbuz.smartautoclicker.core.domain.model.scenario.Scenario) {
        if (viewModel.loadSmartScenario(result.resultCode, result.data!!, scenario)) finish()
        else Toast.makeText(this, R.string.toast_denied_foreground_permission, Toast.LENGTH_SHORT).show()
    }

    private fun showUnsupportedDeviceDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_overlay_title_warning)
            .setMessage(R.string.message_error_screen_capture_permission_dialog_not_found)
            .setPositiveButton(android.R.string.ok) { _: DialogInterface, _: Int -> finish() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun FavoriteScenarioItem.getDisplayName(context: Context): String =
        when (this) {
            is FavoriteScenarioItem.Dumb -> context.getString(R.string.item_favorite_scenario_dumb, name)
            is FavoriteScenarioItem.Smart -> context.getString(R.string.item_favorite_scenario_smart, name)
        }
}
