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

import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext

import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class FavoriteScenarioLauncherViewModel @Inject constructor(
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

    val favoriteScenarios: StateFlow<List<FavoriteScenarioItem>?> =
        combine(smartRepository.scenarios, dumbRepository.dumbScenarios) { smart, dumb ->
            buildList {
                addAll(smart.filter { it.isFavorite && it.eventCount > 0 }.map { FavoriteScenarioItem.Smart(it) })
                addAll(dumb.filter { it.isFavorite && it.dumbActions.isNotEmpty() }.map { FavoriteScenarioItem.Dumb(it) })
            }.sortedBy { it.name.lowercase() }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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

    fun startPermissionFlowIfNeeded(activity: AppCompatActivity, onAllGranted: () -> Unit, onMandatoryDenied: () -> Unit) {
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

    fun loadSmartScenario(resultCode: Int, data: Intent, scenario: Scenario): Boolean {
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

sealed class FavoriteScenarioItem(val name: String) {
    data class Smart(val scenario: Scenario) : FavoriteScenarioItem(scenario.name)
    data class Dumb(val scenario: DumbScenario) : FavoriteScenarioItem(scenario.name)
}
