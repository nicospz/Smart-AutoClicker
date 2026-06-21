/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.core.dumb.data.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.UUID

object Migration6to7 : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        addColumnIfMissing(db, "dumb_scenario_table", "sync_id", "TEXT NOT NULL DEFAULT ''")
        addColumnIfMissing(db, "dumb_scenario_table", "updated_at_ms", "INTEGER NOT NULL DEFAULT 0")
        addColumnIfMissing(db, "dumb_scenario_table", "deleted_at_ms", "INTEGER")
        backfillSyncIds(db)
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_dumb_scenario_table_sync_id ON dumb_scenario_table (sync_id)",
        )
    }

    private fun backfillSyncIds(db: SupportSQLiteDatabase) {
        val now = System.currentTimeMillis()
        db.query("SELECT id FROM dumb_scenario_table WHERE sync_id = '' OR sync_id IS NULL").use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val syncId = UUID.randomUUID().toString()
                db.execSQL(
                    "UPDATE dumb_scenario_table SET sync_id = '$syncId', updated_at_ms = $now WHERE id = $id",
                )
            }
        }
    }

    private fun addColumnIfMissing(
        db: SupportSQLiteDatabase,
        tableName: String,
        columnName: String,
        columnDefinition: String,
    ) {
        db.query("PRAGMA table_info($tableName)").use { cursor ->
            val nameColumnIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                if (cursor.getString(nameColumnIndex) == columnName) return
            }
        }
        db.execSQL("ALTER TABLE $tableName ADD COLUMN $columnName $columnDefinition")
    }
}
