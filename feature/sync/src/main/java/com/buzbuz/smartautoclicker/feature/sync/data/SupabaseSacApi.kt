/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.feature.sync.data

import com.buzbuz.smartautoclicker.feature.sync.BuildConfig
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

open class SupabaseSacApi(
    private val client: OkHttpClient = OkHttpClient(),
    supabaseUrl: String = BuildConfig.SUPABASE_URL,
    private val anonKey: String = BuildConfig.SUPABASE_ANON_KEY,
    protected val profileId: String = BuildConfig.SUPABASE_SYNC_PROFILE_ID,
    protected val syncSecret: String = BuildConfig.SUPABASE_SYNC_SECRET,
) {
    protected val baseUrl = supabaseUrl.trim().trimEnd('/')
    protected val json = Json { ignoreUnknownKeys = true; explicitNulls = true }

    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && anonKey.isNotBlank() && profileId.isNotBlank() && syncSecret.isNotBlank()

    protected fun executeRpc(functionName: String, payloadJson: String): String {
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
                throw SupabaseSacException(response.code, response.message, body.take(500))
            }
            return body
        }
    }

    companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}

class SupabaseSacException(
    statusCode: Int,
    statusMessage: String,
    responseBody: String,
) : java.io.IOException(
    "Supabase SAC sync HTTP $statusCode $statusMessage${if (responseBody.isNotBlank()) ": $responseBody" else ""}",
)

@Serializable
data class ProfileRequest(
    @SerialName("p_profile_id") val profileId: String,
    @SerialName("p_sync_secret") val syncSecret: String,
)

@Serializable
data class RemoteSacScenario(
    @SerialName("sync_id") val syncId: String,
    @SerialName("scenario_type") val scenarioType: String,
    @SerialName("payload_json") val payloadJson: JsonElement,
    @SerialName("schema_version") val schemaVersion: Int,
    @SerialName("screen_width") val screenWidth: Int,
    @SerialName("screen_height") val screenHeight: Int,
    @SerialName("updated_at_ms") val updatedAtMs: Long,
    @SerialName("deleted_at_ms") val deletedAtMs: Long? = null,
)

@Serializable
data class UpsertSacScenarioRequest(
    @SerialName("p_profile_id") val profileId: String,
    @SerialName("p_sync_secret") val syncSecret: String,
    @SerialName("p_sync_id") val syncId: String,
    @SerialName("p_scenario_type") val scenarioType: String,
    @SerialName("p_payload_json") val payloadJson: JsonElement,
    @SerialName("p_schema_version") val schemaVersion: Int,
    @SerialName("p_screen_width") val screenWidth: Int,
    @SerialName("p_screen_height") val screenHeight: Int,
    @SerialName("p_updated_at_ms") val updatedAtMs: Long,
    @SerialName("p_deleted_at_ms") val deletedAtMs: Long? = null,
)

@Serializable
data class RemoteConditionAsset(
    @SerialName("content_hash") val contentHash: String,
    @SerialName("image_png_base64") val imagePngBase64: String,
    @SerialName("updated_at_ms") val updatedAtMs: Long,
)

@Serializable
data class UpsertConditionAssetRequest(
    @SerialName("p_profile_id") val profileId: String,
    @SerialName("p_sync_secret") val syncSecret: String,
    @SerialName("p_content_hash") val contentHash: String,
    @SerialName("p_image_png_base64") val imagePngBase64: String,
    @SerialName("p_updated_at_ms") val updatedAtMs: Long,
)

@Serializable
data class RemoteProfileSettings(
    @SerialName("profile_id") val profileId: String,
    @SerialName("settings_json") val settingsJson: JsonElement,
    @SerialName("updated_at_ms") val updatedAtMs: Long,
)

@Serializable
data class UpsertProfileSettingsRequest(
    @SerialName("p_profile_id") val profileId: String,
    @SerialName("p_sync_secret") val syncSecret: String,
    @SerialName("p_settings_json") val settingsJson: JsonElement,
    @SerialName("p_updated_at_ms") val updatedAtMs: Long,
)

@Serializable
data class RemoteCatchNeedle(
    @SerialName("needle_id") val needleId: String,
    @SerialName("feature") val feature: String,
    @SerialName("lane") val lane: String,
    @SerialName("variant_order") val variantOrder: Int,
    @SerialName("image_png_base64") val imagePngBase64: String,
    @SerialName("source_width") val sourceWidth: Int,
    @SerialName("source_height") val sourceHeight: Int,
    @SerialName("crop_left") val cropLeft: Int,
    @SerialName("crop_top") val cropTop: Int,
    @SerialName("crop_right") val cropRight: Int,
    @SerialName("crop_bottom") val cropBottom: Int,
    @SerialName("search_left") val searchLeft: Int,
    @SerialName("search_top") val searchTop: Int,
    @SerialName("search_right") val searchRight: Int,
    @SerialName("search_bottom") val searchBottom: Int,
    @SerialName("threshold") val threshold: Int,
    @SerialName("created_at_ms") val createdAtMs: Long,
    @SerialName("updated_at_ms") val updatedAtMs: Long,
    @SerialName("deleted_at_ms") val deletedAtMs: Long? = null,
)
