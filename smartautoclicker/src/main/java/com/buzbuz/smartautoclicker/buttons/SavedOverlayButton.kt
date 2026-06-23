package com.buzbuz.smartautoclicker.buttons

import org.json.JSONObject

data class SavedOverlayButton(
    val id: Long,
    val syncId: String,
    val setSyncId: String,
    val labelOverride: String?,
    val iconGlyph: String? = null,
    val scenarioDbId: Long,
    val scenarioSyncId: String,
    val scenarioNameSnapshot: String,
    val enabled: Boolean = true,
    val isVisible: Boolean = true,
    val priority: Int = 0,
    val portraitXPercent: Float = DEFAULT_POSITION,
    val portraitYPercent: Float = DEFAULT_POSITION,
    val landscapeXPercent: Float? = null,
    val landscapeYPercent: Float? = null,
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = createdAtMs,
    val deletedAtMs: Long? = null,
) {

    val displayName: String
        get() = labelOverride?.takeIf { it.isNotBlank() } ?: scenarioNameSnapshot

    val overlayText: String
        get() = iconGlyph?.takeIf { it.isNotBlank() } ?: displayName

    fun toJson(): JSONObject = JSONObject().apply {
        put(KEY_ID, id)
        put(KEY_SYNC_ID, syncId)
        put(KEY_SET_SYNC_ID, setSyncId)
        put(KEY_LABEL_OVERRIDE, labelOverride)
        put(KEY_ICON_GLYPH, iconGlyph)
        put(KEY_SCENARIO_DB_ID, scenarioDbId)
        put(KEY_SCENARIO_SYNC_ID, scenarioSyncId)
        put(KEY_SCENARIO_NAME, scenarioNameSnapshot)
        put(KEY_ENABLED, enabled)
        put(KEY_VISIBLE, isVisible)
        put(KEY_PRIORITY, priority)
        put(KEY_PORTRAIT_X, portraitXPercent)
        put(KEY_PORTRAIT_Y, portraitYPercent)
        landscapeXPercent?.let { put(KEY_LANDSCAPE_X, it) }
        landscapeYPercent?.let { put(KEY_LANDSCAPE_Y, it) }
        put(KEY_CREATED_AT, createdAtMs)
        put(KEY_UPDATED_AT, updatedAtMs)
        deletedAtMs?.let { put(KEY_DELETED_AT, it) }
    }

    companion object {
        const val DEFAULT_POSITION = 0.5f

        fun fromJson(json: JSONObject): SavedOverlayButton? =
            runCatching {
                SavedOverlayButton(
                    id = json.getLong(KEY_ID),
                    syncId = json.getString(KEY_SYNC_ID),
                    setSyncId = json.optString(KEY_SET_SYNC_ID).takeIf { it.isNotBlank() && it != "null" }.orEmpty(),
                    labelOverride = json.optString(KEY_LABEL_OVERRIDE).takeIf { it.isNotBlank() && it != "null" },
                    iconGlyph = json.optString(KEY_ICON_GLYPH).takeIf { it.isNotBlank() && it != "null" },
                    scenarioDbId = json.getLong(KEY_SCENARIO_DB_ID),
                    scenarioSyncId = json.optString(KEY_SCENARIO_SYNC_ID),
                    scenarioNameSnapshot = json.getString(KEY_SCENARIO_NAME),
                    enabled = json.optBoolean(KEY_ENABLED, true),
                    isVisible = json.optBoolean(KEY_VISIBLE, true),
                    priority = json.optInt(KEY_PRIORITY, 0),
                    portraitXPercent = json.optDouble(KEY_PORTRAIT_X, DEFAULT_POSITION.toDouble()).toFloat(),
                    portraitYPercent = json.optDouble(KEY_PORTRAIT_Y, DEFAULT_POSITION.toDouble()).toFloat(),
                    landscapeXPercent = json.optNullableFloat(KEY_LANDSCAPE_X),
                    landscapeYPercent = json.optNullableFloat(KEY_LANDSCAPE_Y),
                    createdAtMs = json.optLong(KEY_CREATED_AT, System.currentTimeMillis()),
                    updatedAtMs = json.optLong(KEY_UPDATED_AT, System.currentTimeMillis()),
                    deletedAtMs = json.optNullableLong(KEY_DELETED_AT),
                )
            }.getOrNull()

        private fun JSONObject.optNullableFloat(key: String): Float? =
            if (has(key) && !isNull(key)) optDouble(key).toFloat() else null

        private fun JSONObject.optNullableLong(key: String): Long? =
            if (has(key) && !isNull(key)) optLong(key) else null
    }
}

private const val KEY_ID = "id"
private const val KEY_SYNC_ID = "syncId"
private const val KEY_SET_SYNC_ID = "setSyncId"
private const val KEY_LABEL_OVERRIDE = "labelOverride"
private const val KEY_ICON_GLYPH = "iconGlyph"
private const val KEY_SCENARIO_DB_ID = "scenarioDbId"
private const val KEY_SCENARIO_SYNC_ID = "scenarioSyncId"
private const val KEY_SCENARIO_NAME = "scenarioNameSnapshot"
private const val KEY_ENABLED = "enabled"
private const val KEY_VISIBLE = "isVisible"
private const val KEY_PRIORITY = "priority"
private const val KEY_PORTRAIT_X = "portraitXPercent"
private const val KEY_PORTRAIT_Y = "portraitYPercent"
private const val KEY_LANDSCAPE_X = "landscapeXPercent"
private const val KEY_LANDSCAPE_Y = "landscapeYPercent"
private const val KEY_CREATED_AT = "createdAtMs"
private const val KEY_UPDATED_AT = "updatedAtMs"
private const val KEY_DELETED_AT = "deletedAtMs"
