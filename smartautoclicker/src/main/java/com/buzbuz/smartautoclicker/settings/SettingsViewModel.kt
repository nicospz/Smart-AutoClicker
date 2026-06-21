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
package com.buzbuz.smartautoclicker.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buzbuz.smartautoclicker.core.base.workarounds.isImpactedByInputBlock
import com.buzbuz.smartautoclicker.core.common.actions.precision.PrecisionGestureHelperSetup
import com.buzbuz.smartautoclicker.core.common.actions.precision.PrecisionGestureSetupResult
import com.buzbuz.smartautoclicker.core.common.quality.domain.QualityRepository
import com.buzbuz.smartautoclicker.core.settings.SettingsRepository
import com.buzbuz.smartautoclicker.feature.revenue.IRevenueRepository
import com.buzbuz.smartautoclicker.feature.revenue.UserBillingState
import com.buzbuz.smartautoclicker.feature.sync.data.SacSyncPreferences
import com.buzbuz.smartautoclicker.feature.sync.data.SacSyncStatus
import com.buzbuz.smartautoclicker.feature.sync.domain.SacSyncCoordinator
import com.buzbuz.smartautoclicker.feature.sync.domain.SacSyncEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject


@HiltViewModel
class SettingsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val qualityRepository: QualityRepository,
    private val revenueRepository: IRevenueRepository,
    private val settingsRepository: SettingsRepository,
    private val precisionGestureHelperSetup: PrecisionGestureHelperSetup,
    private val sacSyncEngine: SacSyncEngine,
    private val sacSyncCoordinator: SacSyncCoordinator,
    private val sacSyncPreferences: SacSyncPreferences,
) : ViewModel() {

    val sacSyncStatus: Flow<SacSyncStatus> = sacSyncPreferences.statusFlow
    val isSacSyncConfigured: Boolean get() = sacSyncEngine.isConfigured

    private val _precisionGestureHelperStatus = MutableStateFlow<PrecisionGestureSetupResult?>(null)
    val precisionGestureHelperStatus: StateFlow<PrecisionGestureSetupResult?> = _precisionGestureHelperStatus.asStateFlow()

    val isScenarioFiltersUiEnabled: Flow<Boolean> =
        settingsRepository.isFilterScenarioUiEnabledFlow

    val isLegacyActionUiEnabled: Flow<Boolean> =
        settingsRepository.isLegacyActionUiEnabledFlow

    val isLegacyNotificationUiEnabled: Flow<Boolean> =
        settingsRepository.isLegacyNotificationUiEnabledFlow

    val isEntireScreenCaptureForced: Flow<Boolean> =
        settingsRepository.isEntireScreenCaptureForcedFlow

    val isInputWorkaroundEnabled: Flow<Boolean> =
        settingsRepository.isInputBlockWorkaroundEnabledFlow

    val shouldShowEntireScreenCapture: Flow<Boolean> =
        flowOf(Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM)

    val shouldShowPrivacySettings: Flow<Boolean> =
        revenueRepository.isPrivacySettingRequired

    val shouldShowPurchase: Flow<Boolean> =
        revenueRepository.userBillingState.map { billingState ->
            billingState != UserBillingState.PURCHASED
        }

    val shouldShowInputBlockWorkaround: Flow<Boolean> =
        flowOf(isImpactedByInputBlock())

    fun refreshPrecisionGestureHelperStatus() {
        viewModelScope.launch {
            _precisionGestureHelperStatus.value = precisionGestureHelperSetup.getStatus()
        }
    }

    fun startPrecisionGestureHelper(activity: Activity) {
        viewModelScope.launch {
            val result = precisionGestureHelperSetup.ensureStarted()
            _precisionGestureHelperStatus.value = result
            if (result is PrecisionGestureSetupResult.ShizukuUnavailable) {
                openShizuku(activity)
            }
        }
    }

    fun stopPrecisionGestureHelper() {
        viewModelScope.launch {
            _precisionGestureHelperStatus.value = precisionGestureHelperSetup.stopHelper()
        }
    }

    fun toggleScenarioFiltersUi() {
        settingsRepository.toggleFilterScenarioUi()
        sacSyncCoordinator.scheduleSettingsPush()
    }

    fun toggleLegacyActionUi() {
        settingsRepository.toggleLegacyActionUi()
        sacSyncCoordinator.scheduleSettingsPush()
    }

    fun toggleLegacyNotificationUi() {
        settingsRepository.toggleLegacyNotificationUi()
        sacSyncCoordinator.scheduleSettingsPush()
    }

    fun toggleForceEntireScreenCapture() {
        settingsRepository.toggleForceEntireScreenCapture()
        sacSyncCoordinator.scheduleSettingsPush()
    }

    fun toggleInputBlockWorkaround() {
        settingsRepository.toggleInputBlockWorkaround()
        sacSyncCoordinator.scheduleSettingsPush()
    }

    fun syncNow() {
        sacSyncCoordinator.requestFullSync()
    }

    fun showPrivacySettings(activity: Activity) {
        revenueRepository.startPrivacySettingUiFlow(activity)
    }

    fun showPurchaseActivity(context: Context) {
        revenueRepository.startPurchaseUiFlow(context)
    }

    fun showTroubleshootingDialog(activity: FragmentActivity) {
        qualityRepository.startTroubleshootingUiFlow(activity)
    }

    private fun openShizuku(activity: Activity) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE_NAME)
            ?: Intent(Intent.ACTION_VIEW).setPackage(SHIZUKU_PACKAGE_NAME)
        runCatching { activity.startActivity(launchIntent) }
    }

    private companion object {
        private const val SHIZUKU_PACKAGE_NAME = "moe.shizuku.privileged.api"
    }
}
