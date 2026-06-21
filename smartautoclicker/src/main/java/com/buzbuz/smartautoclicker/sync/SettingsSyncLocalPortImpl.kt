/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.sync

import com.buzbuz.smartautoclicker.core.settings.SettingsRepository
import com.buzbuz.smartautoclicker.core.settings.data.SettingsSyncSnapshot
import com.buzbuz.smartautoclicker.feature.qstile.data.QsTileSyncSnapshot
import com.buzbuz.smartautoclicker.feature.qstile.domain.QSTileRepository
import com.buzbuz.smartautoclicker.feature.sync.data.SacAppSettings
import com.buzbuz.smartautoclicker.feature.sync.data.SacProfileSettingsPayload
import com.buzbuz.smartautoclicker.feature.sync.data.SacQsTileSettings
import com.buzbuz.smartautoclicker.feature.sync.data.SacScenarioSortSettings
import com.buzbuz.smartautoclicker.feature.sync.data.SacScenarioSortType
import com.buzbuz.smartautoclicker.feature.sync.data.SettingsSyncLocalPort
import com.buzbuz.smartautoclicker.scenarios.list.sort.ScenarioSortConfigRepository
import com.buzbuz.smartautoclicker.scenarios.list.sort.ScenarioSortSyncSnapshot
import com.buzbuz.smartautoclicker.scenarios.list.sort.ScenarioSortType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsSyncLocalPortImpl @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val scenarioSortConfigRepository: ScenarioSortConfigRepository,
    private val qsTileRepository: QSTileRepository,
) : SettingsSyncLocalPort {

    override suspend fun readLocalPayload(): SacProfileSettingsPayload {
        val appSettings = settingsRepository.readCloudSyncSnapshot()
        val sort = scenarioSortConfigRepository.readSyncSnapshot()
        val qsTile = qsTileRepository.readCloudSyncSnapshot()
        return SacProfileSettingsPayload(
            settings = SacAppSettings(
                isFilterScenarioUiEnabled = appSettings.isFilterScenarioUiEnabled,
                isLegacyActionUiEnabled = appSettings.isLegacyActionUiEnabled,
                isLegacyNotificationUiEnabled = appSettings.isLegacyNotificationUiEnabled,
                forceEntireScreen = appSettings.forceEntireScreen,
                inputBlockWorkaround = appSettings.inputBlockWorkaround,
            ),
            scenarioSort = SacScenarioSortSettings(
                sortType = sort.sortType.toSacType(),
                inverted = sort.inverted,
                showSmartScenario = sort.showSmartScenario,
                showDumbScenario = sort.showDumbScenario,
                showFavoritesOnly = sort.showFavoritesOnly,
            ),
            qsTile = SacQsTileSettings(
                scenarioSyncId = qsTile.scenarioSyncId,
                isSmartScenario = qsTile.isSmartScenario,
            ),
        )
    }

    override suspend fun readLocalUpdatedAtMs(): Long {
        val appSettings = settingsRepository.readCloudSyncSnapshot()
        val sort = scenarioSortConfigRepository.readSyncSnapshot()
        val qsTile = qsTileRepository.readCloudSyncSnapshot()
        return maxOf(appSettings.updatedAtMs, sort.updatedAtMs, qsTile.updatedAtMs)
    }

    override suspend fun applyRemotePayload(payload: SacProfileSettingsPayload) {
        settingsRepository.applyCloudSyncSnapshot(
            SettingsSyncSnapshot(
                isFilterScenarioUiEnabled = payload.settings.isFilterScenarioUiEnabled,
                isLegacyActionUiEnabled = payload.settings.isLegacyActionUiEnabled,
                isLegacyNotificationUiEnabled = payload.settings.isLegacyNotificationUiEnabled,
                forceEntireScreen = payload.settings.forceEntireScreen,
                inputBlockWorkaround = payload.settings.inputBlockWorkaround,
                updatedAtMs = 0L,
            ),
        )
        scenarioSortConfigRepository.applySyncSnapshot(
            ScenarioSortSyncSnapshot(
                sortType = payload.scenarioSort.sortType.toLocalType(),
                inverted = payload.scenarioSort.inverted,
                showSmartScenario = payload.scenarioSort.showSmartScenario,
                showDumbScenario = payload.scenarioSort.showDumbScenario,
                showFavoritesOnly = payload.scenarioSort.showFavoritesOnly,
                updatedAtMs = 0L,
            ),
        )
        val syncId = payload.qsTile.scenarioSyncId
        val isSmart = payload.qsTile.isSmartScenario
        qsTileRepository.applyCloudSyncSnapshot(
            QsTileSyncSnapshot(
                scenarioSyncId = syncId,
                isSmartScenario = isSmart,
                scenarioDbId = null,
                updatedAtMs = 0L,
            ),
        )
        if (syncId != null && isSmart != null) {
            qsTileRepository.remapScenarioDbIdFromSyncId(syncId, isSmart)
        }
    }

    override suspend fun remapQsTileAfterScenarioSync() {
        val qsTile = qsTileRepository.readCloudSyncSnapshot()
        val syncId = qsTile.scenarioSyncId ?: return
        val isSmart = qsTile.isSmartScenario ?: return
        qsTileRepository.remapScenarioDbIdFromSyncId(syncId, isSmart)
    }

    private fun ScenarioSortType.toSacType(): SacScenarioSortType = when (this) {
        ScenarioSortType.NAME -> SacScenarioSortType.NAME
        ScenarioSortType.RECENT -> SacScenarioSortType.RECENT
        ScenarioSortType.MOST_USED -> SacScenarioSortType.MOST_USED
    }

    private fun SacScenarioSortType.toLocalType(): ScenarioSortType = when (this) {
        SacScenarioSortType.NAME -> ScenarioSortType.NAME
        SacScenarioSortType.RECENT -> ScenarioSortType.RECENT
        SacScenarioSortType.MOST_USED -> ScenarioSortType.MOST_USED
    }
}
