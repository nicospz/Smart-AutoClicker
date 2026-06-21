/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.core.database.entity

import androidx.room.ColumnInfo

data class ScenarioSyncMeta(
    @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "sync_id") val syncId: String,
    @ColumnInfo(name = "updated_at_ms") val updatedAtMs: Long,
    @ColumnInfo(name = "deleted_at_ms") val deletedAtMs: Long?,
)
