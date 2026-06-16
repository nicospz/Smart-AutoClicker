/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.scenarios.favorite

import android.Manifest
import android.content.Context
import android.widget.Toast

import androidx.core.content.PermissionChecker

import com.buzbuz.smartautoclicker.R
import com.buzbuz.smartautoclicker.core.common.overlays.manager.OverlayManager.Companion.showAsOverlay
import com.buzbuz.smartautoclicker.core.common.overlays.manager.OverlayServiceContext
import com.buzbuz.smartautoclicker.core.domain.IRepository
import com.buzbuz.smartautoclicker.core.dumb.domain.IDumbRepository
import com.buzbuz.smartautoclicker.core.dumb.domain.model.DumbScenario
import com.buzbuz.smartautoclicker.core.ui.utils.getDynamicColorsContext
import com.buzbuz.smartautoclicker.localservice.LocalServiceProvider

import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.qualifiers.ApplicationContext

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import android.os.Build
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoriteScenarioPickerController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val smartRepository: IRepository,
    private val dumbRepository: IDumbRepository,
    private val overlayServiceContext: OverlayServiceContext,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    private var pickerVisible: Boolean = false

    fun show(
        onUnavailable: () -> Unit = {},
        onShown: () -> Unit = {},
        onDismiss: () -> Unit = {},
    ) {
        val overlayContext = overlayServiceContext.contextOrNull()
        if (overlayContext == null || !LocalServiceProvider.isServiceStarted()) {
            onUnavailable()
            return
        }

        if (pickerVisible) return

        scope.launch {
            val scenarios = loadFavoriteScenarios()
            pickerVisible = true
            showOverlayDialog(
                overlayContext = overlayContext,
                scenarios = scenarios,
                onShown = onShown,
                onDismiss = {
                    pickerVisible = false
                    onDismiss()
                },
            )
        }
    }

    private suspend fun loadFavoriteScenarios(): List<FavoriteScenarioItem> =
        combine(smartRepository.scenarios, dumbRepository.dumbScenarios) { smart, dumb ->
            buildList {
                addAll(smart.filter { it.isFavorite && it.eventCount > 0 }.map { FavoriteScenarioItem.Smart(it) })
                addAll(dumb.filter { it.isFavorite && it.dumbActions.isNotEmpty() }.map { FavoriteScenarioItem.Dumb(it) })
            }.sortedBy { it.name.lowercase() }
        }.first()

    private fun showOverlayDialog(
        overlayContext: Context,
        scenarios: List<FavoriteScenarioItem>,
        onShown: () -> Unit,
        onDismiss: () -> Unit,
    ) {
        val themedContext = overlayContext.getDynamicColorsContext(R.style.AppTheme)
        val dismissListener = { onDismiss() }

        if (scenarios.isEmpty()) {
            MaterialAlertDialogBuilder(themedContext)
                .setTitle(R.string.dialog_title_favorite_scenarios)
                .setMessage(R.string.message_no_favorite_scenarios)
                .setPositiveButton(android.R.string.ok) { _, _ -> dismissListener() }
                .setOnDismissListener { dismissListener() }
                .create()
                .also { onShown() }
                .showAsOverlay()
            return
        }

        MaterialAlertDialogBuilder(themedContext)
            .setTitle(R.string.dialog_title_favorite_scenarios)
            .setItems(scenarios.map { it.getDisplayName(overlayContext) }.toTypedArray()) { _, which ->
                onScenarioSelected(overlayContext, scenarios[which])
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> dismissListener() }
            .setOnDismissListener { dismissListener() }
            .create()
            .also { onShown() }
            .showAsOverlay()
    }

    private fun onScenarioSelected(context: Context, item: FavoriteScenarioItem) {
        when (item) {
            is FavoriteScenarioItem.Dumb -> {
                if (!startDumbScenario(item.scenario)) {
                    Toast.makeText(context, R.string.toast_denied_foreground_permission, Toast.LENGTH_SHORT).show()
                }
            }
            is FavoriteScenarioItem.Smart -> {
                context.startActivity(
                    FavoriteScenarioLauncherActivity.getSmartProjectionIntent(
                        context,
                        item.scenario.id.databaseId,
                    ),
                )
            }
        }
    }

    private fun startDumbScenario(scenario: DumbScenario): Boolean {
        if (!hasForegroundServicePermission()) return false
        LocalServiceProvider.localServiceInstance?.startDumbScenario(scenario) ?: return false
        return true
    }

    private fun hasForegroundServicePermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.P ||
            PermissionChecker.checkSelfPermission(context, Manifest.permission.FOREGROUND_SERVICE) ==
            PermissionChecker.PERMISSION_GRANTED

    private fun FavoriteScenarioItem.getDisplayName(context: Context): String =
        when (this) {
            is FavoriteScenarioItem.Dumb -> context.getString(R.string.item_favorite_scenario_dumb, name)
            is FavoriteScenarioItem.Smart -> context.getString(R.string.item_favorite_scenario_smart, name)
        }
}
