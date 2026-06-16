/*
 * Copyright (C) 2026 Kevin Buzeau
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
package com.buzbuz.smartautoclicker.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration18to19 : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.addColumnIfMissing("scenario_table", "is_favorite", "INTEGER NOT NULL DEFAULT 0")
        db.addColumnIfMissing("scenario_table", "auto_start", "INTEGER NOT NULL DEFAULT 0")
        db.addColumnIfMissing("scenario_table", "auto_start_delay_ms", "INTEGER NOT NULL DEFAULT 0")
        db.addColumnIfMissing("action_table", "click_wait_after_ms", "INTEGER NOT NULL DEFAULT 0")
    }
}

object Migration19to20 : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.addColumnIfMissing("action_table", "precision_gesture_payload_hex", "TEXT")
        db.addColumnIfMissing("action_table", "precision_gesture_event_count", "INTEGER")
        db.addColumnIfMissing("action_table", "precision_gesture_duration_ms", "INTEGER")
        db.addColumnIfMissing("action_table", "precision_gesture_helper_mode", "TEXT")
    }
}

object Migration20to21 : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.addColumnIfMissing("action_table", "precision_text_value", "TEXT")
        db.addColumnIfMissing("action_table", "precision_text_mode", "TEXT")
    }
}

object Migration21to22 : Migration(21, 22) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.addColumnIfMissing("event_table", "image_detection_mode", "TEXT NOT NULL DEFAULT 'STANDARD'")
        db.addColumnIfMissing("event_table", "anchor_condition_id", "INTEGER")
        db.sanitizeAnchoredRepeatEventAnchors()
    }
}

object Migration22to23 : Migration(22, 23) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.addColumnIfMissing("event_table", "cooldown_ms", "INTEGER NOT NULL DEFAULT 0")
    }
}

internal fun SupportSQLiteDatabase.sanitizeAnchoredRepeatEventAnchors() {
    execSQL(
        """
        UPDATE event_table
        SET anchor_condition_id = NULL
        WHERE image_detection_mode != 'ANCHORED_REPEAT'
          AND anchor_condition_id = 0
        """.trimIndent()
    )

    execSQL(
        """
        UPDATE event_table
        SET anchor_condition_id = (
            SELECT condition_table.id
            FROM condition_table
            WHERE condition_table.eventId = event_table.id
            ORDER BY condition_table.priority ASC, condition_table.id ASC
            LIMIT 1
        )
        WHERE image_detection_mode = 'ANCHORED_REPEAT'
          AND EXISTS (
              SELECT 1
              FROM condition_table
              WHERE condition_table.eventId = event_table.id
          )
          AND (
              anchor_condition_id IS NULL
              OR anchor_condition_id = 0
              OR NOT EXISTS (
                  SELECT 1
                  FROM condition_table
                  WHERE condition_table.id = event_table.anchor_condition_id
                    AND condition_table.eventId = event_table.id
              )
          )
        """.trimIndent()
    )
}

private fun SupportSQLiteDatabase.addColumnIfMissing(tableName: String, columnName: String, columnDefinition: String) {
    query("PRAGMA table_info($tableName)").use { cursor ->
        val nameColumnIndex = cursor.getColumnIndex("name")
        while (cursor.moveToNext()) {
            if (cursor.getString(nameColumnIndex) == columnName) return
        }
    }

    execSQL("ALTER TABLE $tableName ADD COLUMN $columnName $columnDefinition")
}
