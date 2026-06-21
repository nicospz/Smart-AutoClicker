/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.feature.sync.data

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Point
import android.util.Base64
import com.buzbuz.smartautoclicker.core.database.CLICK_DATABASE_VERSION
import com.buzbuz.smartautoclicker.core.database.ClickDatabase
import com.buzbuz.smartautoclicker.core.database.entity.CompleteScenario
import com.buzbuz.smartautoclicker.core.domain.IRepository
import com.buzbuz.smartautoclicker.core.dumb.data.database.DUMB_DATABASE_VERSION
import com.buzbuz.smartautoclicker.core.dumb.data.database.DumbDatabase
import com.buzbuz.smartautoclicker.core.dumb.domain.IDumbRepository
import com.buzbuz.smartautoclicker.feature.backup.data.smart.collectConditionImagePaths
import com.buzbuz.smartautoclicker.feature.backup.domain.BackupSerialization
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

@Singleton
class ScenarioSyncRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val smartRepository: IRepository,
    private val dumbRepository: IDumbRepository,
    private val clickDatabase: ClickDatabase,
    private val dumbDatabase: DumbDatabase,
    private val conditionAssetSync: ConditionAssetSync,
    private val api: SacSupabaseClient,
) {
    suspend fun syncScenarios(screenSize: Point): ScenarioSyncCounts = withContext(Dispatchers.IO) {
        if (!api.isConfigured) return@withContext ScenarioSyncCounts(skipped = true)
        var pulled = 0
        var pushed = 0
        val remoteScenarios = api.listScenarios()
        val localSmartMeta = clickDatabase.scenarioDao().getAllSyncMeta().associateBy { it.syncId }
        val localDumbMeta = dumbDatabase.dumbScenarioDao().getAllSyncMeta().associateBy { it.syncId }
        val localMeta = buildMap {
            localSmartMeta.forEach { (syncId, meta) -> put(syncId, LocalScenarioMeta(meta.id, meta.updatedAtMs, meta.deletedAtMs, true)) }
            localDumbMeta.forEach { (syncId, meta) -> put(syncId, LocalScenarioMeta(meta.id, meta.updatedAtMs, meta.deletedAtMs, false)) }
        }

        for (remote in remoteScenarios) {
            val local = localMeta[remote.syncId]
            if (remote.deletedAtMs != null) {
                if (local != null && (local.deletedAtMs == null || remote.deletedAtMs > (local.deletedAtMs ?: 0L))) {
                    if (remote.scenarioType == SCENARIO_TYPE_SMART) {
                        smartRepository.deleteScenarioBySyncId(remote.syncId)
                    } else {
                        dumbRepository.deleteDumbScenarioBySyncId(remote.syncId)
                    }
                    pulled += 1
                }
                continue
            }
            if (local == null || remote.updatedAtMs > local.updatedAtMs) {
                applyRemoteScenario(remote, screenSize)
                pulled += 1
            }
        }

        for ((syncId, local) in localMeta) {
            if (local.deletedAtMs != null) continue
            val remote = remoteScenarios.find { it.syncId == syncId }
            if (remote == null || local.updatedAtMs > remote.updatedAtMs) {
                if (pushLocalScenario(local, screenSize)) pushed += 1
            }
        }

        ScenarioSyncCounts(remotePushed = pushed, remotePulled = pulled)
    }

    suspend fun pushScenario(syncId: String, isSmart: Boolean, screenSize: Point): Boolean = withContext(Dispatchers.IO) {
        if (!api.isConfigured) return@withContext false
        if (isSmart) {
            val scenarioId = smartRepository.getScenarioDatabaseIdBySyncId(syncId) ?: return@withContext false
            val complete = smartRepository.getCompleteScenario(scenarioId) ?: return@withContext false
            val meta = clickDatabase.scenarioDao().getScenarioEntityBySyncId(syncId) ?: return@withContext false
            pushSmartScenario(complete, meta.syncId, meta.updatedAtMs, screenSize)
        } else {
            val withActions = dumbRepository.getDumbScenarioWithActionsBySyncId(syncId) ?: return@withContext false
            val meta = dumbDatabase.dumbScenarioDao().getDumbScenarioEntityBySyncId(syncId) ?: return@withContext false
            pushDumbScenario(withActions, meta.syncId, meta.updatedAtMs, screenSize)
        }
        true
    }

    suspend fun pushScenarioDeleted(syncId: String, isSmart: Boolean, deletedAtMs: Long, screenSize: Point): Boolean =
        withContext(Dispatchers.IO) {
            if (!api.isConfigured) return@withContext false
            api.upsertScenario(
                syncId = syncId,
                scenarioType = if (isSmart) SCENARIO_TYPE_SMART else SCENARIO_TYPE_DUMB,
                payloadJson = JsonPrimitive("{}"),
                schemaVersion = if (isSmart) CLICK_DATABASE_VERSION else DUMB_DATABASE_VERSION,
                screenWidth = screenSize.x,
                screenHeight = screenSize.y,
                updatedAtMs = deletedAtMs,
                deletedAtMs = deletedAtMs,
            )
            true
        }

    private suspend fun applyRemoteScenario(remote: RemoteSacScenario, screenSize: Point) {
        if (remote.deletedAtMs != null) return
        val payloadText = remote.payloadJson.toString()
        when (remote.scenarioType) {
            SCENARIO_TYPE_SMART -> {
                val backup = BackupSerialization.decodeSmartScenario(payloadText) ?: return
                val paths = collectConditionImagePaths(backup.scenario)
                conditionAssetSync.ensureLocalAssets(paths)
                smartRepository.upsertScenarioBySyncId(backup.scenario, remote.syncId, remote.updatedAtMs)
            }
            SCENARIO_TYPE_DUMB -> {
                val backup = BackupSerialization.decodeDumbScenario(payloadText) ?: return
                dumbRepository.upsertDumbScenarioBySyncId(backup.dumbScenario, remote.syncId, remote.updatedAtMs)
            }
        }
    }

    private suspend fun pushLocalScenario(local: LocalScenarioMeta, screenSize: Point): Boolean =
        if (local.isSmart) {
            val complete = smartRepository.getCompleteScenario(local.id) ?: return false
            val entity = clickDatabase.scenarioDao().getScenarioEntityBySyncId(
                complete.scenario.syncId,
            ) ?: return false
            pushSmartScenario(complete, entity.syncId, entity.updatedAtMs, screenSize)
        } else {
            val withActions = dumbDatabase.dumbScenarioDao().getDumbScenariosWithAction(local.id) ?: return false
            val entity = withActions.scenario
            pushDumbScenario(withActions, entity.syncId, entity.updatedAtMs, screenSize)
        }

    private suspend fun pushSmartScenario(
        complete: CompleteScenario,
        syncId: String,
        updatedAtMs: Long,
        screenSize: Point,
    ): Boolean {
        val paths = collectConditionImagePaths(complete)
        conditionAssetSync.pushAssets(paths)
        val payload = BackupSerialization.encodeSmartScenario(complete, screenSize)
        api.upsertScenario(
            syncId = syncId,
            scenarioType = SCENARIO_TYPE_SMART,
            payloadJson = Json.parseToJsonElement(payload),
            schemaVersion = CLICK_DATABASE_VERSION,
            screenWidth = screenSize.x,
            screenHeight = screenSize.y,
            updatedAtMs = updatedAtMs,
            deletedAtMs = null,
        )
        return true
    }

    private suspend fun pushDumbScenario(
        withActions: com.buzbuz.smartautoclicker.core.dumb.data.database.DumbScenarioWithActions,
        syncId: String,
        updatedAtMs: Long,
        screenSize: Point,
    ): Boolean {
        val payload = BackupSerialization.encodeDumbScenario(withActions, screenSize)
        api.upsertScenario(
            syncId = syncId,
            scenarioType = SCENARIO_TYPE_DUMB,
            payloadJson = Json.parseToJsonElement(payload),
            schemaVersion = DUMB_DATABASE_VERSION,
            screenWidth = screenSize.x,
            screenHeight = screenSize.y,
            updatedAtMs = updatedAtMs,
            deletedAtMs = null,
        )
        return true
    }

    private data class LocalScenarioMeta(
        val id: Long,
        val updatedAtMs: Long,
        val deletedAtMs: Long?,
        val isSmart: Boolean,
    )

    companion object {
        const val SCENARIO_TYPE_SMART = "smart"
        const val SCENARIO_TYPE_DUMB = "dumb"
    }
}

data class ScenarioSyncCounts(
    val remotePushed: Int = 0,
    val remotePulled: Int = 0,
    val skipped: Boolean = false,
)

class SacSupabaseClient @Inject constructor() : SupabaseSacApi() {
    fun listScenarios(): List<RemoteSacScenario> {
        if (!isConfigured) return emptyList()
        val body = executeRpc("sac_list_scenarios", json.encodeToString(ProfileRequest(profileId, syncSecret)))
        return json.decodeFromString(ListSerializer(RemoteSacScenario.serializer()), body)
    }

    fun upsertScenario(
        syncId: String,
        scenarioType: String,
        payloadJson: JsonElement,
        schemaVersion: Int,
        screenWidth: Int,
        screenHeight: Int,
        updatedAtMs: Long,
        deletedAtMs: Long?,
    ) {
        if (!isConfigured) return
        executeRpc(
            "sac_upsert_scenario",
            json.encodeToString(
                UpsertSacScenarioRequest(
                    profileId = profileId,
                    syncSecret = syncSecret,
                    syncId = syncId,
                    scenarioType = scenarioType,
                    payloadJson = payloadJson,
                    schemaVersion = schemaVersion,
                    screenWidth = screenWidth,
                    screenHeight = screenHeight,
                    updatedAtMs = updatedAtMs,
                    deletedAtMs = deletedAtMs,
                ),
            ),
        )
    }

    fun listConditionAssets(): List<RemoteConditionAsset> {
        if (!isConfigured) return emptyList()
        val body = executeRpc("sac_list_condition_assets", json.encodeToString(ProfileRequest(profileId, syncSecret)))
        return json.decodeFromString(ListSerializer(RemoteConditionAsset.serializer()), body)
    }

    fun upsertConditionAsset(contentHash: String, imagePngBase64: String, updatedAtMs: Long) {
        if (!isConfigured) return
        executeRpc(
            "sac_upsert_condition_asset",
            json.encodeToString(
                UpsertConditionAssetRequest(
                    profileId = profileId,
                    syncSecret = syncSecret,
                    contentHash = contentHash,
                    imagePngBase64 = imagePngBase64,
                    updatedAtMs = updatedAtMs,
                ),
            ),
        )
    }

    fun getProfileSettings(): RemoteProfileSettings? {
        if (!isConfigured) return null
        val body = executeRpc("sac_get_profile_settings", json.encodeToString(ProfileRequest(profileId, syncSecret)))
        if (body.isBlank() || body == "null") return null
        return json.decodeFromString(RemoteProfileSettings.serializer(), body)
    }

    fun upsertProfileSettings(settingsJson: JsonElement, updatedAtMs: Long) {
        if (!isConfigured) return
        executeRpc(
            "sac_upsert_profile_settings",
            json.encodeToString(
                UpsertProfileSettingsRequest(
                    profileId = profileId,
                    syncSecret = syncSecret,
                    settingsJson = settingsJson,
                    updatedAtMs = updatedAtMs,
                ),
            ),
        )
    }

    fun listCatchNeedles(): List<RemoteCatchNeedle> {
        if (!isConfigured) return emptyList()
        val body = executeRpc("sac_list_catch_needles", json.encodeToString(ProfileRequest(profileId, syncSecret)))
        return json.decodeFromString(ListSerializer(RemoteCatchNeedle.serializer()), body)
    }

    fun upsertCatchNeedle(needle: RemoteCatchNeedle) {
        if (!isConfigured) return
        executeRpc("sac_upsert_catch_needle", json.encodeToString(needle.toUpsertRequest(profileId, syncSecret)))
    }
}

@kotlinx.serialization.Serializable
private data class UpsertCatchNeedleRequest(
    @kotlinx.serialization.SerialName("p_profile_id") val profileId: String,
    @kotlinx.serialization.SerialName("p_sync_secret") val syncSecret: String,
    @kotlinx.serialization.SerialName("p_needle_id") val needleId: String,
    @kotlinx.serialization.SerialName("p_feature") val feature: String,
    @kotlinx.serialization.SerialName("p_lane") val lane: String,
    @kotlinx.serialization.SerialName("p_variant_order") val variantOrder: Int,
    @kotlinx.serialization.SerialName("p_image_png_base64") val imagePngBase64: String,
    @kotlinx.serialization.SerialName("p_source_width") val sourceWidth: Int,
    @kotlinx.serialization.SerialName("p_source_height") val sourceHeight: Int,
    @kotlinx.serialization.SerialName("p_crop_left") val cropLeft: Int,
    @kotlinx.serialization.SerialName("p_crop_top") val cropTop: Int,
    @kotlinx.serialization.SerialName("p_crop_right") val cropRight: Int,
    @kotlinx.serialization.SerialName("p_crop_bottom") val cropBottom: Int,
    @kotlinx.serialization.SerialName("p_search_left") val searchLeft: Int,
    @kotlinx.serialization.SerialName("p_search_top") val searchTop: Int,
    @kotlinx.serialization.SerialName("p_search_right") val searchRight: Int,
    @kotlinx.serialization.SerialName("p_search_bottom") val searchBottom: Int,
    @kotlinx.serialization.SerialName("p_threshold") val threshold: Int,
    @kotlinx.serialization.SerialName("p_created_at_ms") val createdAtMs: Long,
    @kotlinx.serialization.SerialName("p_updated_at_ms") val updatedAtMs: Long,
    @kotlinx.serialization.SerialName("p_deleted_at_ms") val deletedAtMs: Long? = null,
)

private fun RemoteCatchNeedle.toUpsertRequest(profileId: String, syncSecret: String) =
    UpsertCatchNeedleRequest(
        profileId = profileId,
        syncSecret = syncSecret,
        needleId = needleId,
        feature = feature,
        lane = lane,
        variantOrder = variantOrder,
        imagePngBase64 = imagePngBase64,
        sourceWidth = sourceWidth,
        sourceHeight = sourceHeight,
        cropLeft = cropLeft,
        cropTop = cropTop,
        cropRight = cropRight,
        cropBottom = cropBottom,
        searchLeft = searchLeft,
        searchTop = searchTop,
        searchRight = searchRight,
        searchBottom = searchBottom,
        threshold = threshold,
        createdAtMs = createdAtMs,
        updatedAtMs = updatedAtMs,
        deletedAtMs = deletedAtMs,
    )
