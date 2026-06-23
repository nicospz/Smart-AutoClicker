/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.feature.sync.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement

@Singleton
class SettingsSyncRepository @Inject constructor(
    private val localPort: SettingsSyncLocalPort,
    private val api: SacSupabaseClient,
) {
    private val payloadJson = Json { ignoreUnknownKeys = true }

    suspend fun syncSettings(): SettingsSyncCounts = withContext(Dispatchers.IO) {
        if (!api.isConfigured) return@withContext SettingsSyncCounts(skipped = true)
        val payload = localPort.readLocalPayload()
        val localUpdatedAtMs = localPort.readLocalUpdatedAtMs()
        val remote = api.getProfileSettings()
        var pulled = false
        var pushed = false
        if (remote != null && remote.updatedAtMs > localUpdatedAtMs) {
            localPort.applyRemotePayload(
                payloadJson.decodeFromJsonElement(SacProfileSettingsPayload.serializer(), remote.settingsJson),
            )
            pulled = true
        } else if (remote == null || localUpdatedAtMs > remote.updatedAtMs) {
            if (localUpdatedAtMs > 0L || remote == null) {
                api.upsertProfileSettings(
                    payloadJson.encodeToJsonElement(payload),
                    localUpdatedAtMs.coerceAtLeast(System.currentTimeMillis()),
                )
                pushed = true
            }
        }
        localPort.remapQsTileAfterScenarioSync()
        SettingsSyncCounts(remotePulled = if (pulled) 1 else 0, remotePushed = if (pushed) 1 else 0)
    }

    suspend fun pushSettingsNow(): Boolean = withContext(Dispatchers.IO) {
        if (!api.isConfigured) return@withContext false
        val payload = localPort.readLocalPayload()
        val updatedAtMs = localPort.readLocalUpdatedAtMs().coerceAtLeast(System.currentTimeMillis())
        api.upsertProfileSettings(payloadJson.encodeToJsonElement(payload), updatedAtMs)
        true
    }
}

data class SettingsSyncCounts(
    val remotePushed: Int = 0,
    val remotePulled: Int = 0,
    val skipped: Boolean = false,
)

@kotlinx.serialization.Serializable
data class SacProfileSettingsPayload(
    val settings: SacAppSettings,
    @SerialName("scenarioSort") val scenarioSort: SacScenarioSortSettings,
    @SerialName("qsTile") val qsTile: SacQsTileSettings,
    @SerialName("overlayButtons") val overlayButtons: List<SacSavedOverlayButton>? = null,
    @SerialName("overlayButtonSets") val overlayButtonSets: List<SacSavedOverlayButtonSet>? = null,
    @SerialName("activeOverlayButtonSetSyncId") val activeOverlayButtonSetSyncId: String? = null,
    @SerialName("activeOverlayButtonSetUpdatedAtMs") val activeOverlayButtonSetUpdatedAtMs: Long = 0L,
    @SerialName("overlayButtonSetButtons") val overlayButtonSetButtons: List<SacSavedOverlayButton>? = null,
)

@kotlinx.serialization.Serializable
data class SacAppSettings(
    @SerialName("isFilterScenarioUiEnabled") val isFilterScenarioUiEnabled: Boolean = true,
    @SerialName("isLegacyActionUiEnabled") val isLegacyActionUiEnabled: Boolean = false,
    @SerialName("isLegacyNotificationUiEnabled") val isLegacyNotificationUiEnabled: Boolean = false,
    @SerialName("forceEntireScreen") val forceEntireScreen: Boolean = false,
    @SerialName("inputBlockWorkaround") val inputBlockWorkaround: Boolean = false,
)

@kotlinx.serialization.Serializable
data class SacScenarioSortSettings(
    @SerialName("sortType") val sortType: SacScenarioSortType = SacScenarioSortType.NAME,
    val inverted: Boolean = false,
    val showSmartScenario: Boolean = true,
    val showDumbScenario: Boolean = true,
    val showFavoritesOnly: Boolean = false,
)

@kotlinx.serialization.Serializable
enum class SacScenarioSortType {
    @SerialName("NAME") NAME,
    @SerialName("RECENT") RECENT,
    @SerialName("MOST_USED") MOST_USED,
}

@kotlinx.serialization.Serializable
data class SacQsTileSettings(
    @SerialName("scenarioSyncId") val scenarioSyncId: String? = null,
    @SerialName("isSmartScenario") val isSmartScenario: Boolean? = null,
)

@kotlinx.serialization.Serializable
data class SacSavedOverlayButton(
    val id: Long,
    val syncId: String,
    val setSyncId: String? = null,
    val labelOverride: String? = null,
    val iconGlyph: String? = null,
    val scenarioDbId: Long? = null,
    val scenarioSyncId: String,
    val scenarioNameSnapshot: String,
    val enabled: Boolean = true,
    val isVisible: Boolean = true,
    val priority: Int = 0,
    val portraitXPercent: Float = 0.5f,
    val portraitYPercent: Float = 0.5f,
    val landscapeXPercent: Float? = null,
    val landscapeYPercent: Float? = null,
    val createdAtMs: Long = 0L,
    val updatedAtMs: Long = 0L,
    val deletedAtMs: Long? = null,
)

@kotlinx.serialization.Serializable
data class SacSavedOverlayButtonSet(
    val id: Long,
    val syncId: String,
    val name: String,
    val priority: Int = 0,
    val portraitXPercent: Float = 0.5f,
    val portraitYPercent: Float = 0.5f,
    val landscapeXPercent: Float? = null,
    val landscapeYPercent: Float? = null,
    val createdAtMs: Long = 0L,
    val updatedAtMs: Long = 0L,
    val deletedAtMs: Long? = null,
)
