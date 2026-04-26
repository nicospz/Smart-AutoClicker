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
package com.buzbuz.smartautoclicker.feature.qstile.ui

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import com.buzbuz.smartautoclicker.core.base.data.AppComponentsProvider
import com.buzbuz.smartautoclicker.core.base.di.Dispatcher
import com.buzbuz.smartautoclicker.core.base.di.HiltCoroutineDispatchers.IO
import com.buzbuz.smartautoclicker.core.domain.IRepository
import com.buzbuz.smartautoclicker.core.dumb.domain.DumbRepository
import com.buzbuz.smartautoclicker.core.common.permissions.PermissionsController
import com.buzbuz.smartautoclicker.core.common.permissions.model.PermissionAccessibilityService
import com.buzbuz.smartautoclicker.core.common.permissions.model.PermissionOverlay
import com.buzbuz.smartautoclicker.core.common.permissions.model.PermissionPostNotification
import com.buzbuz.smartautoclicker.core.settings.SettingsRepository
import com.buzbuz.smartautoclicker.feature.qstile.domain.QSTileRecentScenario
import com.buzbuz.smartautoclicker.feature.qstile.domain.QSTileRepository

import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject


@HiltViewModel
class QSTileLauncherViewModel @Inject constructor(
    @param:Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
    private val qsTileRepository: QSTileRepository,
    private val permissionController: PermissionsController,
    private val smartRepository: IRepository,
    private val dumbRepository: DumbRepository,
    private val settingsRepository: SettingsRepository,
    private val appComponentsProvider: AppComponentsProvider,
) : ViewModel() {

    internal val recentScenarios: StateFlow<List<QSTileRecentScenario>?> = combine(
        dumbRepository.dumbScenarios,
        smartRepository.scenarios,
    ) { dumbScenarios, smartScenarios ->
        buildList {
            addAll(dumbScenarios.map { scenario ->
                QSTileRecentScenario(
                    scenarioId = scenario.id.databaseId,
                    isSmart = false,
                    name = scenario.name,
                    lastStartTimestampMs = scenario.stats?.lastStartTimestampMs ?: 0,
                )
            })
            addAll(smartScenarios.map { scenario ->
                QSTileRecentScenario(
                    scenarioId = scenario.id.databaseId,
                    isSmart = true,
                    name = scenario.name,
                    lastStartTimestampMs = scenario.stats?.lastStartTimestampMs ?: 0,
                )
            })
        }
            .asSequence()
            .filter { it.lastStartTimestampMs > 0 }
            .sortedByDescending { it.lastStartTimestampMs }
            .take(RECENT_SCENARIO_COUNT)
            .toList()
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        null,
    )

    fun startPermissionFlowIfNeeded(activity: AppCompatActivity, onAllGranted: () -> Unit, onMandatoryDenied: () -> Unit) {
        permissionController.startPermissionsUiFlow(
            activity = activity,
            permissions = listOf(
                PermissionOverlay(),
                PermissionAccessibilityService(
                    componentName = appComponentsProvider.klickrServiceComponentName,
                    isServiceRunning = { qsTileRepository.isAccessibilityServiceStarted() },
                ),
                PermissionPostNotification(optional = true),
            ),
            onAllGranted = onAllGranted,
            onMandatoryDenied = onMandatoryDenied,
        )
    }

    fun startSmartScenario(resultCode: Int, data: Intent, scenarioId: Long) {
        viewModelScope.launch(ioDispatcher) {
            val scenario = smartRepository.getScenario(scenarioId) ?: return@launch
            qsTileRepository.startSmartScenario(resultCode, data, scenario)
        }
    }

    fun startDumbScenario(scenarioId: Long) {
        viewModelScope.launch(ioDispatcher) {
            val scenario = dumbRepository.getDumbScenario(scenarioId) ?: return@launch
            qsTileRepository.startDumbScenario(scenario)
        }
    }

    fun isEntireScreenCaptureForced(): Boolean =
        settingsRepository.isEntireScreenCaptureForced()
}

private const val RECENT_SCENARIO_COUNT = 5
