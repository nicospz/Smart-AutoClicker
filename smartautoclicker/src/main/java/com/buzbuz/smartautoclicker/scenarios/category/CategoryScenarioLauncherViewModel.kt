/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.scenarios.category

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build

import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.PermissionChecker
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import com.buzbuz.smartautoclicker.core.base.data.AppComponentsProvider
import com.buzbuz.smartautoclicker.core.base.matchesScenarioCategory
import com.buzbuz.smartautoclicker.core.common.permissions.PermissionsController
import com.buzbuz.smartautoclicker.core.common.permissions.model.PermissionAccessibilityService
import com.buzbuz.smartautoclicker.core.common.permissions.model.PermissionOverlay
import com.buzbuz.smartautoclicker.core.common.permissions.model.PermissionPostNotification
import com.buzbuz.smartautoclicker.core.common.quality.domain.QualityRepository
import com.buzbuz.smartautoclicker.core.domain.IRepository
import com.buzbuz.smartautoclicker.core.domain.model.scenario.Scenario
import com.buzbuz.smartautoclicker.core.dumb.domain.IDumbRepository
import com.buzbuz.smartautoclicker.core.dumb.domain.model.DumbScenario
import com.buzbuz.smartautoclicker.core.settings.SettingsRepository
import com.buzbuz.smartautoclicker.localservice.ILocalService
import com.buzbuz.smartautoclicker.localservice.LocalServiceProvider
import com.buzbuz.smartautoclicker.scenarios.favorite.FavoriteScenarioItem

import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext

import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class CategoryScenarioLauncherViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val smartRepository: IRepository,
    private val dumbRepository: IDumbRepository,
    private val permissionController: PermissionsController,
    private val qualityRepository: QualityRepository,
    private val settingsRepository: SettingsRepository,
    private val appComponentsProvider: AppComponentsProvider,
) : ViewModel() {

    private val serviceConnection: (ILocalService?) -> Unit = { localService ->
        clickerService = localService
    }

    private var clickerService: ILocalService? = null

    init {
        LocalServiceProvider.getLocalService(serviceConnection)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(NotificationManager::class.java)
        }
    }

    override fun onCleared() {
        LocalServiceProvider.getLocalService(null)
        super.onCleared()
    }

    fun getCategoryScenarios(category: String): StateFlow<List<FavoriteScenarioItem>?> =
        combine(smartRepository.scenarios, dumbRepository.dumbScenarios) { smart, dumb ->
            buildList {
                addAll(
                    smart.filter { it.eventCount > 0 && it.category.matchesScenarioCategory(category) }
                        .map { FavoriteScenarioItem.Smart(it) },
                )
                addAll(
                    dumb.filter { it.dumbActions.isNotEmpty() && it.category.matchesScenarioCategory(category) }
                        .map { FavoriteScenarioItem.Dumb(it) },
                )
            }.sortedBy { it.name.lowercase() }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun startPermissionFlowIfNeeded(
        activity: AppCompatActivity,
        onAllGranted: () -> Unit,
        onMandatoryDenied: () -> Unit,
    ) {
        permissionController.startPermissionsUiFlow(
            activity = activity,
            permissions = listOf(
                PermissionOverlay(),
                PermissionAccessibilityService(
                    componentName = appComponentsProvider.klickrServiceComponentName,
                    isServiceRunning = { LocalServiceProvider.isServiceStarted() },
                ),
                PermissionPostNotification(optional = true),
            ),
            onAllGranted = onAllGranted,
            onMandatoryDenied = onMandatoryDenied,
        )
    }

    fun startTroubleshootingFlowIfNeeded(activity: FragmentActivity, onCompleted: () -> Unit) {
        qualityRepository.startTroubleshootingUiFlowIfNeeded(activity, onCompleted)
    }

    fun isEntireScreenCaptureForced(): Boolean =
        settingsRepository.isEntireScreenCaptureForced()

    suspend fun getSmartScenario(scenarioId: Long): Scenario? =
        smartRepository.scenarios.first().find { it.id.databaseId == scenarioId }

    suspend fun getSmartScenarioByName(scenarioName: String): Scenario? =
        smartRepository.scenarios.first().find { it.name.equals(scenarioName, ignoreCase = true) }

    fun loadSmartScenario(resultCode: Int, data: Intent?, scenario: Scenario): Boolean {
        if (!hasForegroundServicePermission()) return false

        clickerService?.startSmartScenario(resultCode, data, scenario)
        return true
    }

    fun loadDumbScenario(scenario: DumbScenario): Boolean {
        if (!hasForegroundServicePermission()) return false

        clickerService?.startDumbScenario(scenario)
        return true
    }

    private fun hasForegroundServicePermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.P ||
            PermissionChecker.checkSelfPermission(context, Manifest.permission.FOREGROUND_SERVICE) ==
            PermissionChecker.PERMISSION_GRANTED
}
