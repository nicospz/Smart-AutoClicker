/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.feature.throwlet.sync

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.buzbuz.smartautoclicker.feature.throwlet.BuddyCropEntity
import com.buzbuz.smartautoclicker.feature.throwlet.BuildConfig
import com.buzbuz.smartautoclicker.feature.throwlet.GestureEntity
import com.buzbuz.smartautoclicker.feature.throwlet.GestureMode
import com.buzbuz.smartautoclicker.feature.throwlet.HelperLane
import com.buzbuz.smartautoclicker.feature.throwlet.data.ThrowletDatabase
import com.buzbuz.smartautoclicker.feature.throwlet.syncKey
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class SupabaseSyncResult(
    val remotePushed: Int = 0,
    val remotePulled: Int = 0,
    val skipped: Boolean = false,
    val errorMessage: String? = null,
) {
    val didWork: Boolean get() = remotePushed > 0 || remotePulled > 0

    fun statusText(defaultSuccess: String = "synced"): String = when {
        skipped -> "sync not configured"
        errorMessage != null -> "sync failed: $errorMessage"
        didWork -> defaultSuccess
        else -> "already synced"
    }
}

class SupabaseSyncRepository(
    private val context: Context,
    private val db: ThrowletDatabase,
    private val remote: SupabaseThrowletApi = SupabaseThrowletApi(),
) {
    suspend fun syncGestures(): SupabaseSyncResult = withContext(Dispatchers.IO) {
        if (!remote.isConfigured) return@withContext SupabaseSyncResult(skipped = true)
        runCatching {
            val local = db.gestureDao().all()
            val remoteGestures = remote.listGestures()
            val localByKey = local.associateBy { it.syncKey }
            val remoteByKey = remoteGestures.associateBy { it.syncKey }
            var pulled = 0
            var pushed = 0

            for (remoteGesture in remoteGestures) {
                val localGesture = localByKey[remoteGesture.syncKey]
                if (localGesture == null || remoteGesture.updatedAtMs > localGesture.updatedAtMs) {
                    db.gestureDao().upsert(remoteGesture)
                    pulled += 1
                }
            }

            for (localGesture in local) {
                val remoteGesture = remoteByKey[localGesture.syncKey]
                if (remoteGesture == null || localGesture.updatedAtMs > remoteGesture.updatedAtMs) {
                    if (remote.upsertGesture(localGesture)) pushed += 1
                }
            }
            SupabaseSyncResult(remotePushed = pushed, remotePulled = pulled)
        }.getOrElse { SupabaseSyncResult(errorMessage = it.message ?: it.javaClass.simpleName) }
    }

    suspend fun pushGesture(gesture: GestureEntity): SupabaseSyncResult = withContext(Dispatchers.IO) {
        if (!remote.isConfigured) return@withContext SupabaseSyncResult(skipped = true)
        runCatching {
            SupabaseSyncResult(remotePushed = if (remote.upsertGesture(gesture)) 1 else 0)
        }.getOrElse { SupabaseSyncResult(errorMessage = it.message ?: it.javaClass.simpleName) }
    }

    suspend fun pushBuddyCrop(entity: BuddyCropEntity): SupabaseSyncResult = withContext(Dispatchers.IO) {
        if (!remote.isConfigured) return@withContext SupabaseSyncResult(skipped = true)
        runCatching {
            val bitmap = BitmapFactory.decodeFile(entity.imagePath)
                ?: return@withContext SupabaseSyncResult(errorMessage = "buddy crop image missing")
            try {
                SupabaseSyncResult(
                    remotePushed = if (remote.upsertBuddyCrop(RemoteBuddyCrop.fromEntity(entity, bitmap))) 1 else 0,
                )
            } finally {
                bitmap.recycle()
            }
        }.getOrElse { SupabaseSyncResult(errorMessage = it.message ?: it.javaClass.simpleName) }
    }

    suspend fun syncBuddyCrops(): SupabaseSyncResult = withContext(Dispatchers.IO) {
        if (!remote.isConfigured) return@withContext SupabaseSyncResult(skipped = true)
        runCatching {
            val local = db.buddyCropDao().all()
            val remoteCrops = remote.listBuddyCrops()
            val localByKey = local.associateBy { it.pokemonKey }
            val remoteByKey = remoteCrops.associateBy { it.pokemonKey }
            var pulled = 0
            var pushed = 0

            for (remoteCrop in remoteCrops) {
                val localCrop = localByKey[remoteCrop.pokemonKey]
                if (localCrop == null || remoteCrop.updatedAtMs > localCrop.updatedAtMs) {
                    val bitmap = remoteCrop.decodeBitmap() ?: continue
                    try {
                        val existing = db.buddyCropDao().getByPokemonKey(remoteCrop.pokemonKey)
                        val path = saveBuddyCropImage(remoteCrop.pokemonKey, bitmap)
                        db.buddyCropDao().upsert(
                            remoteCrop.toEntity(
                                existing?.id ?: 0,
                                path,
                                existing?.createdAtMs ?: remoteCrop.updatedAtMs,
                            ),
                        )
                        existing?.imagePath?.takeIf { it != path }?.let { runCatching { File(it).delete() } }
                        pulled += 1
                    } finally {
                        bitmap.recycle()
                    }
                }
            }

            for (localCrop in local) {
                val remoteCrop = remoteByKey[localCrop.pokemonKey]
                if (remoteCrop == null || localCrop.updatedAtMs > remoteCrop.updatedAtMs) {
                    val bitmap = BitmapFactory.decodeFile(localCrop.imagePath) ?: continue
                    try {
                        if (remote.upsertBuddyCrop(RemoteBuddyCrop.fromEntity(localCrop, bitmap))) pushed += 1
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
            SupabaseSyncResult(remotePushed = pushed, remotePulled = pulled)
        }.getOrElse { SupabaseSyncResult(errorMessage = it.message ?: it.javaClass.simpleName) }
    }

    suspend fun syncAll(): Pair<SupabaseSyncResult, SupabaseSyncResult> =
        syncGestures() to syncBuddyCrops()

    private fun saveBuddyCropImage(pokemonKey: String, bitmap: Bitmap): String {
        val dir = File(context.filesDir, "needles/buddy").also { it.mkdirs() }
        val safeKey = pokemonKey.lowercase().replace(Regex("[^a-z0-9_-]"), "_")
        val file = File(dir, "$safeKey.png")
        file.outputStream().use { output -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, output) }
        return file.absolutePath
    }
}

class SupabaseThrowletApi(
    private val client: OkHttpClient = OkHttpClient(),
    supabaseUrl: String = BuildConfig.SUPABASE_URL,
    private val anonKey: String = BuildConfig.SUPABASE_ANON_KEY,
    private val profileId: String = BuildConfig.SUPABASE_GESTURE_PROFILE_ID,
    private val syncSecret: String = BuildConfig.SUPABASE_GESTURE_SYNC_SECRET,
) {
    private val baseUrl = supabaseUrl.trim().trimEnd('/')
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = true }

    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && anonKey.isNotBlank() && profileId.isNotBlank() && syncSecret.isNotBlank()

    suspend fun listGestures(): List<GestureEntity> = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext emptyList()
        val body = executeRpc("throwlet_list_gestures", json.encodeToString(ProfileRequest(profileId, syncSecret)))
        json.decodeFromString(ListSerializer(RemoteGesture.serializer()), body).map { it.toEntity() }
    }

    suspend fun upsertGesture(gesture: GestureEntity): Boolean = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext false
        executeRpc("throwlet_upsert_gesture", json.encodeToString(gesture.toUpsertRequest(profileId, syncSecret)))
        true
    }

    suspend fun listBuddyCrops(): List<RemoteBuddyCrop> = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext emptyList()
        val body = executeRpc("throwlet_list_buddy_crops", json.encodeToString(ProfileRequest(profileId, syncSecret)))
        json.decodeFromString(ListSerializer(RemoteBuddyCrop.serializer()), body)
    }

    suspend fun upsertBuddyCrop(crop: RemoteBuddyCrop): Boolean = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext false
        executeRpc("throwlet_upsert_buddy_crop", json.encodeToString(crop.toUpsertRequest(profileId, syncSecret)))
        true
    }

    private fun executeRpc(functionName: String, payloadJson: String): String {
        val request = Request.Builder()
            .url("$baseUrl/rest/v1/rpc/$functionName")
            .post(payloadJson.toRequestBody(JSON_MEDIA_TYPE))
            .header("apikey", anonKey)
            .header("Authorization", "Bearer $anonKey")
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw SupabaseThrowletException(response.code, response.message, body.take(500))
            }
            return body
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}

class SupabaseThrowletException(
    statusCode: Int,
    statusMessage: String,
    responseBody: String,
) : IOException("Supabase Throwlet sync HTTP $statusCode $statusMessage${if (responseBody.isNotBlank()) ": $responseBody" else ""}")

@Serializable
private data class ProfileRequest(
    @SerialName("p_profile_id") val profileId: String,
    @SerialName("p_sync_secret") val syncSecret: String,
)

@Serializable
private data class RemoteGesture(
    @SerialName("pokemon_key") val pokemonKey: String,
    @SerialName("pokemon_name") val pokemonName: String,
    @SerialName("gesture_mode") val gestureMode: String,
    @SerialName("payload_hex") val payloadHex: String,
    @SerialName("event_count") val eventCount: Int,
    @SerialName("duration_ms") val durationMs: Long,
    @SerialName("helper_mode") val helperMode: String,
    @SerialName("source_lane") val sourceLane: String,
    @SerialName("source_display_width") val sourceDisplayWidth: Int,
    @SerialName("source_display_height") val sourceDisplayHeight: Int,
    @SerialName("throw_score") val throwScore: String? = null,
    @SerialName("updated_at_ms") val updatedAtMs: Long,
) {
    fun toEntity(): GestureEntity =
        GestureEntity(
            pokemonKey = pokemonKey,
            pokemonName = pokemonName,
            gestureMode = GestureMode.valueOf(gestureMode),
            payloadHex = payloadHex,
            eventCount = eventCount,
            durationMs = durationMs,
            helperMode = helperMode,
            sourceLane = HelperLane.valueOf(sourceLane),
            sourceDisplayWidth = sourceDisplayWidth,
            sourceDisplayHeight = sourceDisplayHeight,
            throwScore = throwScore,
            laneOffsetTouch = null,
            updatedAtMs = updatedAtMs,
        )
}

@Serializable
private data class UpsertGestureRequest(
    @SerialName("p_profile_id") val profileId: String,
    @SerialName("p_sync_secret") val syncSecret: String,
    @SerialName("p_pokemon_key") val pokemonKey: String,
    @SerialName("p_pokemon_name") val pokemonName: String,
    @SerialName("p_gesture_mode") val gestureMode: String,
    @SerialName("p_payload_hex") val payloadHex: String,
    @SerialName("p_event_count") val eventCount: Int,
    @SerialName("p_duration_ms") val durationMs: Long,
    @SerialName("p_helper_mode") val helperMode: String,
    @SerialName("p_source_lane") val sourceLane: String,
    @SerialName("p_source_display_width") val sourceDisplayWidth: Int,
    @SerialName("p_source_display_height") val sourceDisplayHeight: Int,
    @SerialName("p_throw_score") val throwScore: String?,
    @SerialName("p_updated_at_ms") val updatedAtMs: Long,
)

private fun GestureEntity.toUpsertRequest(profileId: String, syncSecret: String): UpsertGestureRequest =
    UpsertGestureRequest(
        profileId = profileId,
        syncSecret = syncSecret,
        pokemonKey = pokemonKey,
        pokemonName = pokemonName,
        gestureMode = gestureMode.name,
        payloadHex = payloadHex,
        eventCount = eventCount,
        durationMs = durationMs,
        helperMode = helperMode,
        sourceLane = sourceLane.name,
        sourceDisplayWidth = sourceDisplayWidth,
        sourceDisplayHeight = sourceDisplayHeight,
        throwScore = throwScore,
        updatedAtMs = updatedAtMs,
    )

@Serializable
data class RemoteBuddyCrop(
    @SerialName("pokemon_key") val pokemonKey: String,
    @SerialName("pokemon_name") val pokemonName: String,
    @SerialName("image_png_base64") val imagePngBase64: String,
    @SerialName("source_lane") val sourceLane: String,
    @SerialName("source_width") val sourceWidth: Int,
    @SerialName("source_height") val sourceHeight: Int,
    @SerialName("crop_left") val cropLeft: Int,
    @SerialName("crop_top") val cropTop: Int,
    @SerialName("crop_right") val cropRight: Int,
    @SerialName("crop_bottom") val cropBottom: Int,
    @SerialName("threshold_percent") val thresholdPercent: Int,
    @SerialName("enabled") val enabled: Boolean,
    @SerialName("created_at_ms") val createdAtMs: Long,
    @SerialName("updated_at_ms") val updatedAtMs: Long,
) {
    fun decodeBitmap(): Bitmap? =
        runCatching {
            val bytes = Base64.decode(imagePngBase64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()

    fun toEntity(id: Long, imagePath: String, localCreatedAtMs: Long): BuddyCropEntity =
        BuddyCropEntity(
            id = id,
            pokemonKey = pokemonKey,
            pokemonName = pokemonName,
            imagePath = imagePath,
            sourceLane = HelperLane.valueOf(sourceLane),
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            cropLeft = cropLeft,
            cropTop = cropTop,
            cropRight = cropRight,
            cropBottom = cropBottom,
            thresholdPercent = thresholdPercent,
            enabled = enabled,
            createdAtMs = localCreatedAtMs,
            updatedAtMs = updatedAtMs,
        )

    internal fun toUpsertRequest(profileId: String, syncSecret: String): UpsertBuddyCropRequest =
        UpsertBuddyCropRequest(
            profileId = profileId,
            syncSecret = syncSecret,
            pokemonKey = pokemonKey,
            pokemonName = pokemonName,
            imagePngBase64 = imagePngBase64,
            sourceLane = sourceLane,
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            cropLeft = cropLeft,
            cropTop = cropTop,
            cropRight = cropRight,
            cropBottom = cropBottom,
            thresholdPercent = thresholdPercent,
            enabled = enabled,
            createdAtMs = createdAtMs,
            updatedAtMs = updatedAtMs,
        )

    companion object {
        fun fromEntity(entity: BuddyCropEntity, bitmap: Bitmap): RemoteBuddyCrop {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            return RemoteBuddyCrop(
                pokemonKey = entity.pokemonKey,
                pokemonName = entity.pokemonName,
                imagePngBase64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP),
                sourceLane = entity.sourceLane.name,
                sourceWidth = entity.sourceWidth,
                sourceHeight = entity.sourceHeight,
                cropLeft = entity.cropLeft,
                cropTop = entity.cropTop,
                cropRight = entity.cropRight,
                cropBottom = entity.cropBottom,
                thresholdPercent = entity.thresholdPercent,
                enabled = entity.enabled,
                createdAtMs = entity.createdAtMs,
                updatedAtMs = entity.updatedAtMs,
            )
        }
    }
}

@Serializable
internal data class UpsertBuddyCropRequest(
    @SerialName("p_profile_id") val profileId: String,
    @SerialName("p_sync_secret") val syncSecret: String,
    @SerialName("p_pokemon_key") val pokemonKey: String,
    @SerialName("p_pokemon_name") val pokemonName: String,
    @SerialName("p_image_png_base64") val imagePngBase64: String,
    @SerialName("p_source_lane") val sourceLane: String,
    @SerialName("p_source_width") val sourceWidth: Int,
    @SerialName("p_source_height") val sourceHeight: Int,
    @SerialName("p_crop_left") val cropLeft: Int,
    @SerialName("p_crop_top") val cropTop: Int,
    @SerialName("p_crop_right") val cropRight: Int,
    @SerialName("p_crop_bottom") val cropBottom: Int,
    @SerialName("p_threshold_percent") val thresholdPercent: Int,
    @SerialName("p_enabled") val enabled: Boolean,
    @SerialName("p_created_at_ms") val createdAtMs: Long,
    @SerialName("p_updated_at_ms") val updatedAtMs: Long,
)
