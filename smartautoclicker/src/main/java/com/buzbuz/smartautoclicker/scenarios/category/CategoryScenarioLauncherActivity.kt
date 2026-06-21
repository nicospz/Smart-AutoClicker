/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.scenarios.category

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle

import com.buzbuz.smartautoclicker.R
import com.buzbuz.smartautoclicker.core.display.recorder.showMediaProjectionWarning
import com.buzbuz.smartautoclicker.core.domain.model.scenario.ScreenCaptureMode
import com.buzbuz.smartautoclicker.scenarios.favorite.FavoriteScenarioItem
import com.buzbuz.smartautoclicker.scenarios.named.NamedScenarioSacIntents

import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint

import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class CategoryScenarioLauncherActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_SMART_SCENARIO_ID =
            "com.buzbuz.smartautoclicker.scenarios.category.EXTRA_SMART_SCENARIO_ID"

        fun getSmartProjectionIntent(context: Context, scenarioId: Long): Intent =
            Intent(context, CategoryScenarioLauncherActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                .putExtra(EXTRA_SMART_SCENARIO_ID, scenarioId)

        fun getCategoryPickerIntent(context: Context, category: String): Intent =
            CategorySacIntents.showCategoryScenariosActivity(context, category)
    }

    @Inject lateinit var pickerController: CategoryScenarioPickerController

    private val viewModel: CategoryScenarioLauncherViewModel by viewModels()

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

        val scenarioName = intent.getStringExtra(NamedScenarioSacIntents.EXTRA_SCENARIO_NAME)
        if (!scenarioName.isNullOrBlank()) {
            startSmartScenarioProjectionFlowByName(scenarioName)
            return
        }

        val category = intent.getStringExtra(CategorySacIntents.EXTRA_CATEGORY)
        if (category == null) {
            finish()
            return
        }

        pickerController.show(
            category = category,
            onUnavailable = { startActivityCategoryPicker(category) },
            onShown = { window.decorView.post { moveTaskToBack(true) } },
            onDismiss = { finish() },
        )
    }

    private fun startActivityCategoryPicker(category: String) {
        projectionActivityResult = registerProjectionResult()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.getCategoryScenarios(category).collect { scenarios ->
                    scenarios ?: return@collect
                    if (!dialogShown) showCategoryActivityDialog(category, scenarios)
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

            launchSmartScenarioProjection(FavoriteScenarioItem.Smart(scenario))
        }
    }

    private fun startSmartScenarioProjectionFlowByName(scenarioName: String) {
        projectionActivityResult = registerProjectionResult()

        lifecycleScope.launch {
            val scenario = viewModel.getSmartScenarioByName(scenarioName) ?: run {
                Toast.makeText(
                    this@CategoryScenarioLauncherActivity,
                    getString(R.string.message_scenario_not_found, scenarioName),
                    Toast.LENGTH_LONG,
                ).show()
                finish()
                return@launch
            }

            launchSmartScenarioProjection(FavoriteScenarioItem.Smart(scenario))
        }
    }

    private fun launchSmartScenarioProjection(item: FavoriteScenarioItem.Smart) {
        viewModel.startPermissionFlowIfNeeded(
            activity = this@CategoryScenarioLauncherActivity,
            onMandatoryDenied = ::finish,
            onAllGranted = {
                viewModel.startTroubleshootingFlowIfNeeded(this@CategoryScenarioLauncherActivity) {
                    requestSmartScenarioStart(item)
                }
            },
        )
    }

    private fun registerProjectionResult(): ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK) {
                finish()
                return@registerForActivityResult
            }

            requestedScenario?.let { startSmartScenario(result.resultCode, result.data!!, it.scenario) } ?: finish()
        }

    private fun showCategoryActivityDialog(category: String, scenarios: List<FavoriteScenarioItem>) {
        dialogShown = true
        val title = getCategoryDialogTitle(category)

        if (scenarios.isEmpty()) {
            MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(R.string.message_no_category_scenarios)
                .setPositiveButton(android.R.string.ok) { _, _ -> finish() }
                .setOnCancelListener { finish() }
                .show()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(title)
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
                        is FavoriteScenarioItem.Smart -> requestSmartScenarioStart(item)
                    }
                }
            },
        )
    }

    private fun startDumbScenario(item: FavoriteScenarioItem.Dumb) {
        if (viewModel.loadDumbScenario(item.scenario)) finish()
        else Toast.makeText(this, R.string.toast_denied_foreground_permission, Toast.LENGTH_SHORT).show()
    }

    private fun requestSmartScenarioStart(item: FavoriteScenarioItem.Smart) {
        when (item.scenario.screenCaptureMode) {
            ScreenCaptureMode.MEDIA_PROJECTION -> {
                requestedScenario = item
                projectionActivityResult.showMediaProjectionWarning(
                    context = this,
                    forceEntireScreen = viewModel.isEntireScreenCaptureForced(),
                    onError = { showUnsupportedDeviceDialog() },
                )
            }
            ScreenCaptureMode.ACCESSIBILITY_SCREENSHOT ->
                startSmartScenario(RESULT_OK, null, item.scenario)
        }
    }

    private fun startSmartScenario(
        resultCode: Int,
        data: Intent?,
        scenario: com.buzbuz.smartautoclicker.core.domain.model.scenario.Scenario,
    ) {
        if (viewModel.loadSmartScenario(resultCode, data, scenario)) finish()
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

    private fun getCategoryDialogTitle(category: String): String =
        if (category.isBlank()) getString(R.string.dialog_title_category_scenarios_uncategorized)
        else getString(R.string.dialog_title_category_scenarios, category)
}
