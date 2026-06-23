/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.sync

import com.buzbuz.smartautoclicker.buttons.SavedOverlayButton
import com.buzbuz.smartautoclicker.buttons.SavedOverlayButtonRepository
import com.buzbuz.smartautoclicker.buttons.SavedOverlayButtonSet
import com.buzbuz.smartautoclicker.core.dumb.domain.IDumbRepository
import com.buzbuz.smartautoclicker.core.settings.SettingsRepository
import com.buzbuz.smartautoclicker.core.settings.data.SettingsSyncSnapshot
import com.buzbuz.smartautoclicker.feature.qstile.data.QsTileSyncSnapshot
import com.buzbuz.smartautoclicker.feature.qstile.domain.QSTileRepository
import com.buzbuz.smartautoclicker.feature.sync.data.SacAppSettings
import com.buzbuz.smartautoclicker.feature.sync.data.SacProfileSettingsPayload
import com.buzbuz.smartautoclicker.feature.sync.data.SacQsTileSettings
import com.buzbuz.smartautoclicker.feature.sync.data.SacSavedOverlayButton
import com.buzbuz.smartautoclicker.feature.sync.data.SacSavedOverlayButtonSet
import com.buzbuz.smartautoclicker.feature.sync.data.SacScenarioSortSettings
import com.buzbuz.smartautoclicker.feature.sync.data.SacScenarioSortType
import com.buzbuz.smartautoclicker.feature.sync.data.SettingsSyncLocalPort
import com.buzbuz.smartautoclicker.scenarios.list.sort.ScenarioSortConfigRepository
import com.buzbuz.smartautoclicker.scenarios.list.sort.ScenarioSortSyncSnapshot
import com.buzbuz.smartautoclicker.scenarios.list.sort.ScenarioSortType
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsSyncLocalPortImpl @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val scenarioSortConfigRepository: ScenarioSortConfigRepository,
    private val qsTileRepository: QSTileRepository,
    private val savedOverlayButtonRepository: SavedOverlayButtonRepository,
    private val dumbRepository: IDumbRepository,
) : SettingsSyncLocalPort {

    override suspend fun readLocalPayload(): SacProfileSettingsPayload {
        val appSettings = settingsRepository.readCloudSyncSnapshot()
        val sort = scenarioSortConfigRepository.readSyncSnapshot()
        val qsTile = qsTileRepository.readCloudSyncSnapshot()
        val sets = savedOverlayButtonRepository.sets.value
        val buttons = savedOverlayButtonRepository.buttons.value
        val activeSetSyncId = savedOverlayButtonRepository.activeSetSyncId.value
        val activeSet = sets.firstOrNull { it.syncId == activeSetSyncId && it.deletedAtMs == null }
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
            overlayButtons = buttons
                .filter { activeSet != null && it.setSyncId == activeSet.syncId }
                .map { it.toSacButton(activeSet) },
            overlayButtonSets = sets.map { it.toSacSet() },
            activeOverlayButtonSetSyncId = activeSetSyncId,
            activeOverlayButtonSetUpdatedAtMs = savedOverlayButtonRepository.activeSetUpdatedAtMs.value,
            overlayButtonSetButtons = buttons.map { it.toSacButton() },
        )
    }

    override suspend fun readLocalUpdatedAtMs(): Long {
        val appSettings = settingsRepository.readCloudSyncSnapshot()
        val sort = scenarioSortConfigRepository.readSyncSnapshot()
        val qsTile = qsTileRepository.readCloudSyncSnapshot()
        val buttonsUpdatedAt = savedOverlayButtonRepository.buttons.value.maxOfOrNull { it.updatedAtMs } ?: 0L
        val setsUpdatedAt = savedOverlayButtonRepository.sets.value.maxOfOrNull { it.updatedAtMs } ?: 0L
        val activeSetUpdatedAt = savedOverlayButtonRepository.activeSetUpdatedAtMs.value
        return maxOf(appSettings.updatedAtMs, sort.updatedAtMs, qsTile.updatedAtMs, buttonsUpdatedAt, setsUpdatedAt, activeSetUpdatedAt)
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

        if (payload.overlayButtonSets != null || payload.overlayButtonSetButtons != null) {
            val sets = payload.overlayButtonSets.orEmpty().map { it.toLocalSet() }
            val buttons = (payload.overlayButtonSetButtons ?: payload.overlayButtons.orEmpty()).map { button ->
                button.toLocalButton(
                    scenarioDbId = dumbRepository.getDumbScenarioDatabaseIdBySyncId(button.scenarioSyncId)
                        ?: button.scenarioDbId
                        ?: -1L,
                    fallbackSetSyncId = sets.firstOrNull { it.deletedAtMs == null }?.syncId,
                )
            }
            savedOverlayButtonRepository.replaceAllFromSync(
                sets = sets,
                activeSetSyncId = payload.activeOverlayButtonSetSyncId,
                activeSetUpdatedAtMs = payload.activeOverlayButtonSetUpdatedAtMs,
                buttons = buttons,
            )
        } else {
            payload.overlayButtons?.let { remoteButtons ->
                val localButtons = remoteButtons.map { button ->
                    button.toLocalButton(
                        scenarioDbId = dumbRepository.getDumbScenarioDatabaseIdBySyncId(button.scenarioSyncId)
                            ?: button.scenarioDbId
                            ?: -1L,
                        fallbackSetSyncId = null,
                    )
                }
                val defaultSet = localButtons.toLegacyDefaultSet()
                savedOverlayButtonRepository.replaceAllFromSync(
                    sets = listOf(defaultSet),
                    activeSetSyncId = defaultSet.syncId,
                    activeSetUpdatedAtMs = localButtons.maxOfOrNull { it.updatedAtMs } ?: defaultSet.updatedAtMs,
                    buttons = localButtons.map { it.copy(setSyncId = defaultSet.syncId) },
                )
            }
        }
    }

    override suspend fun remapQsTileAfterScenarioSync() {
        val qsTile = qsTileRepository.readCloudSyncSnapshot()
        val syncId = qsTile.scenarioSyncId
        val isSmart = qsTile.isSmartScenario
        if (syncId != null && isSmart != null) {
            qsTileRepository.remapScenarioDbIdFromSyncId(syncId, isSmart)
        }
        savedOverlayButtonRepository.replaceAllFromSync(
            sets = savedOverlayButtonRepository.sets.value,
            activeSetSyncId = savedOverlayButtonRepository.activeSetSyncId.value,
            activeSetUpdatedAtMs = savedOverlayButtonRepository.activeSetUpdatedAtMs.value,
            buttons = savedOverlayButtonRepository.buttons.value.map { button ->
                val remappedId = dumbRepository.getDumbScenarioDatabaseIdBySyncId(button.scenarioSyncId)
                if (remappedId != null) button.copy(scenarioDbId = remappedId) else button
            },
        )
    }

    private fun SavedOverlayButton.toSacButton(legacyPositionSet: SavedOverlayButtonSet? = null): SacSavedOverlayButton =
        SacSavedOverlayButton(
            id = id,
            syncId = syncId,
            setSyncId = setSyncId,
            labelOverride = labelOverride,
            iconGlyph = iconGlyph,
            scenarioDbId = scenarioDbId,
            scenarioSyncId = scenarioSyncId,
            scenarioNameSnapshot = scenarioNameSnapshot,
            enabled = enabled,
            isVisible = isVisible,
            priority = priority,
            portraitXPercent = legacyPositionSet?.portraitXPercent ?: portraitXPercent,
            portraitYPercent = legacyPositionSet?.portraitYPercent ?: portraitYPercent,
            landscapeXPercent = legacyPositionSet?.landscapeXPercent ?: landscapeXPercent,
            landscapeYPercent = legacyPositionSet?.landscapeYPercent ?: landscapeYPercent,
            createdAtMs = createdAtMs,
            updatedAtMs = updatedAtMs,
            deletedAtMs = deletedAtMs,
        )

    private fun SavedOverlayButtonSet.toSacSet(): SacSavedOverlayButtonSet =
        SacSavedOverlayButtonSet(
            id = id,
            syncId = syncId,
            name = name,
            priority = priority,
            portraitXPercent = portraitXPercent,
            portraitYPercent = portraitYPercent,
            landscapeXPercent = landscapeXPercent,
            landscapeYPercent = landscapeYPercent,
            createdAtMs = createdAtMs,
            updatedAtMs = updatedAtMs,
            deletedAtMs = deletedAtMs,
        )

    private fun SacSavedOverlayButton.toLocalButton(
        scenarioDbId: Long,
        fallbackSetSyncId: String?,
    ): SavedOverlayButton =
        SavedOverlayButton(
            id = id,
            syncId = syncId,
            setSyncId = setSyncId ?: fallbackSetSyncId.orEmpty(),
            labelOverride = labelOverride,
            iconGlyph = iconGlyph,
            scenarioDbId = scenarioDbId,
            scenarioSyncId = scenarioSyncId,
            scenarioNameSnapshot = scenarioNameSnapshot,
            enabled = enabled && scenarioDbId > 0,
            isVisible = isVisible,
            priority = priority,
            portraitXPercent = portraitXPercent,
            portraitYPercent = portraitYPercent,
            landscapeXPercent = landscapeXPercent,
            landscapeYPercent = landscapeYPercent,
            createdAtMs = createdAtMs,
            updatedAtMs = updatedAtMs,
            deletedAtMs = deletedAtMs,
        )

    private fun SacSavedOverlayButtonSet.toLocalSet(): SavedOverlayButtonSet =
        SavedOverlayButtonSet(
            id = id,
            syncId = syncId,
            name = name,
            priority = priority,
            portraitXPercent = portraitXPercent,
            portraitYPercent = portraitYPercent,
            landscapeXPercent = landscapeXPercent,
            landscapeYPercent = landscapeYPercent,
            createdAtMs = createdAtMs,
            updatedAtMs = updatedAtMs,
            deletedAtMs = deletedAtMs,
        )

    private fun List<SavedOverlayButton>.toLegacyDefaultSet(): SavedOverlayButtonSet {
        val now = System.currentTimeMillis()
        val anchor = firstOrNull { it.deletedAtMs == null && it.isVisible }
            ?: firstOrNull { it.deletedAtMs == null }
            ?: firstOrNull()
        return SavedOverlayButtonSet(
            id = now,
            syncId = UUID.randomUUID().toString(),
            name = "Default",
            portraitXPercent = anchor?.portraitXPercent ?: SavedOverlayButtonSet.DEFAULT_POSITION,
            portraitYPercent = anchor?.portraitYPercent ?: SavedOverlayButtonSet.DEFAULT_POSITION,
            landscapeXPercent = anchor?.landscapeXPercent,
            landscapeYPercent = anchor?.landscapeYPercent,
            createdAtMs = minOfOrNull { it.createdAtMs } ?: now,
            updatedAtMs = maxOfOrNull { it.updatedAtMs } ?: now,
        )
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
