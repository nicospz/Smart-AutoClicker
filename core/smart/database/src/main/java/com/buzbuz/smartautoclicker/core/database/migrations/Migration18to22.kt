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

object Migration23to24 : Migration(23, 24) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.addColumnIfMissing("event_table", "offset_repeat_count", "INTEGER NOT NULL DEFAULT 0")
        db.addColumnIfMissing("event_table", "offset_repeat_x", "INTEGER NOT NULL DEFAULT 0")
        db.addColumnIfMissing("event_table", "offset_repeat_y", "INTEGER NOT NULL DEFAULT 0")
        db.addColumnIfMissing("event_table", "offset_repeat_match_mode", "TEXT NOT NULL DEFAULT 'FIRST_MATCH'")
    }
}

object Migration24to25 : Migration(24, 25) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            UPDATE event_table
            SET image_detection_mode = 'STANDARD',
                anchor_condition_id = NULL
            WHERE image_detection_mode = 'ANCHORED_REPEAT'
            """.trimIndent()
        )
    }
}

object Migration25to26 : Migration(25, 26) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS event_toggle_table_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                action_id INTEGER NOT NULL,
                toggle_type TEXT NOT NULL,
                toggle_event_id INTEGER,
                event_name_prefix TEXT,
                FOREIGN KEY(action_id) REFERENCES action_table(id) ON DELETE CASCADE,
                FOREIGN KEY(toggle_event_id) REFERENCES event_table(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO event_toggle_table_new (id, action_id, toggle_type, toggle_event_id, event_name_prefix)
            SELECT id, action_id, toggle_type, toggle_event_id, NULL FROM event_toggle_table
            """.trimIndent()
        )
        db.execSQL("DROP TABLE event_toggle_table")
        db.execSQL("ALTER TABLE event_toggle_table_new RENAME TO event_toggle_table")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_event_toggle_table_action_id ON event_toggle_table (action_id)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_event_toggle_table_toggle_event_id ON event_toggle_table (toggle_event_id)"
        )
    }
}

object Migration26to27 : Migration(26, 27) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.addColumnIfMissing("scenario_table", "category", "TEXT")
    }
}

object Migration27to28 : Migration(27, 28) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.addColumnIfMissing("action_table", "throwlet_catch_operation", "TEXT")
        db.execSQL(
            """
            UPDATE action_table
            SET type = 'THROWLET_CATCH',
                throwlet_catch_operation = 'TOGGLE',
                system_action_type = NULL
            WHERE type = 'SYSTEM'
              AND system_action_type = 'TOGGLE_THROWLET_OVERLAY'
            """.trimIndent()
        )
    }
}

object Migration28to29 : Migration(28, 29) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.addColumnIfMissing("action_table", "throwlet_catch_mode", "TEXT")
        db.addColumnIfMissing("action_table", "throwlet_catch_lane", "TEXT")
        db.execSQL(
            """
            UPDATE action_table
            SET throwlet_catch_mode = 'CATCH',
                throwlet_catch_lane = 'FULL'
            WHERE type = 'THROWLET_CATCH'
              AND (throwlet_catch_mode IS NULL OR throwlet_catch_lane IS NULL)
            """.trimIndent()
        )
    }
}

object Migration29to30 : Migration(29, 30) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS event_group_table (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                scenario_id INTEGER NOT NULL,
                name TEXT NOT NULL,
                event_type TEXT NOT NULL,
                operator INTEGER NOT NULL,
                priority INTEGER NOT NULL,
                FOREIGN KEY(scenario_id) REFERENCES scenario_table(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_event_group_table_scenario_id ON event_group_table (scenario_id)"
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS event_table_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                scenario_id INTEGER NOT NULL,
                name TEXT NOT NULL,
                operator INTEGER NOT NULL,
                priority INTEGER NOT NULL,
                enabled_on_start INTEGER NOT NULL DEFAULT 1,
                type TEXT NOT NULL,
                keep_detecting INTEGER,
                image_detection_mode TEXT NOT NULL DEFAULT 'STANDARD',
                anchor_condition_id INTEGER,
                group_id INTEGER,
                cooldown_ms INTEGER NOT NULL DEFAULT 0,
                offset_repeat_count INTEGER NOT NULL DEFAULT 0,
                offset_repeat_x INTEGER NOT NULL DEFAULT 0,
                offset_repeat_y INTEGER NOT NULL DEFAULT 0,
                offset_repeat_match_mode TEXT NOT NULL DEFAULT 'FIRST_MATCH',
                FOREIGN KEY(scenario_id) REFERENCES scenario_table(id) ON DELETE CASCADE,
                FOREIGN KEY(group_id) REFERENCES event_group_table(id) ON DELETE SET NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO event_table_new (
                id, scenario_id, name, operator, priority, enabled_on_start, type, keep_detecting,
                image_detection_mode, anchor_condition_id, group_id, cooldown_ms, offset_repeat_count,
                offset_repeat_x, offset_repeat_y, offset_repeat_match_mode
            )
            SELECT
                id, scenario_id, name, operator, priority, enabled_on_start, type, keep_detecting,
                image_detection_mode, anchor_condition_id, NULL, cooldown_ms, offset_repeat_count,
                offset_repeat_x, offset_repeat_y, offset_repeat_match_mode
            FROM event_table
            """.trimIndent()
        )
        db.execSQL("DROP TABLE event_table")
        db.execSQL("ALTER TABLE event_table_new RENAME TO event_table")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_event_table_scenario_id ON event_table (scenario_id)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_event_table_group_id ON event_table (group_id)"
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS condition_table_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                eventId INTEGER,
                event_group_id INTEGER,
                name TEXT NOT NULL,
                type TEXT NOT NULL,
                priority INTEGER NOT NULL DEFAULT 0,
                path TEXT,
                area_left INTEGER,
                area_top INTEGER,
                area_right INTEGER,
                area_bottom INTEGER,
                threshold INTEGER,
                detection_type INTEGER,
                shouldBeDetected INTEGER,
                detection_area_left INTEGER,
                detection_area_top INTEGER,
                detection_area_right INTEGER,
                detection_area_bottom INTEGER,
                broadcast_action TEXT,
                counter_name TEXT,
                counter_comparison_operation TEXT,
                counter_operation_value_type TEXT,
                counter_value INTEGER,
                counter_value_counter_name TEXT,
                timer_value_ms INTEGER,
                timer_restart_when_reached INTEGER,
                FOREIGN KEY(eventId) REFERENCES event_table(id) ON DELETE CASCADE,
                FOREIGN KEY(event_group_id) REFERENCES event_group_table(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO condition_table_new (
                id, eventId, event_group_id, name, type, priority, path,
                area_left, area_top, area_right, area_bottom, threshold, detection_type,
                shouldBeDetected, detection_area_left, detection_area_top, detection_area_right,
                detection_area_bottom, broadcast_action, counter_name, counter_comparison_operation,
                counter_operation_value_type, counter_value, counter_value_counter_name,
                timer_value_ms, timer_restart_when_reached
            )
            SELECT
                id, eventId, NULL, name, type, priority, path,
                area_left, area_top, area_right, area_bottom, threshold, detection_type,
                shouldBeDetected, detection_area_left, detection_area_top, detection_area_right,
                detection_area_bottom, broadcast_action, counter_name, counter_comparison_operation,
                counter_operation_value_type, counter_value, counter_value_counter_name,
                timer_value_ms, timer_restart_when_reached
            FROM condition_table
            """.trimIndent()
        )
        db.execSQL("DROP TABLE condition_table")
        db.execSQL("ALTER TABLE condition_table_new RENAME TO condition_table")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_condition_table_eventId ON condition_table (eventId)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_condition_table_event_group_id ON condition_table (event_group_id)"
        )
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

object Migration30to31 : Migration(30, 31) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.addColumnIfMissing(
            tableName = "event_group_table",
            columnName = "parent_group_id",
            columnDefinition = "INTEGER REFERENCES event_group_table(id) ON DELETE SET NULL",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_event_group_table_parent_group_id ON event_group_table (parent_group_id)"
        )
    }
}

object Migration31to32 : Migration(31, 32) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.addColumnIfMissing("scenario_table", "sync_id", "TEXT NOT NULL DEFAULT ''")
        db.addColumnIfMissing("scenario_table", "updated_at_ms", "INTEGER NOT NULL DEFAULT 0")
        db.addColumnIfMissing("scenario_table", "deleted_at_ms", "INTEGER")
        db.backfillScenarioSyncIds()
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_scenario_table_sync_id ON scenario_table (sync_id)")
    }
}

object Migration32to33 : Migration(32, 33) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.addColumnIfMissing("action_table", "throwlet_catch_pokemon_name_override", "TEXT")
    }
}

object Migration33to34 : Migration(33, 34) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.addColumnIfMissing("event_table", "ignored", "INTEGER NOT NULL DEFAULT 0")
    }
}

object Migration34to35 : Migration(34, 35) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.addColumnIfMissing("scenario_table", "screen_capture_mode", "TEXT NOT NULL DEFAULT 'MEDIA_PROJECTION'")
    }
}

private fun SupportSQLiteDatabase.backfillScenarioSyncIds() {
    val now = System.currentTimeMillis()
    query("SELECT id FROM scenario_table WHERE sync_id = '' OR sync_id IS NULL").use { cursor ->
        while (cursor.moveToNext()) {
            val id = cursor.getLong(0)
            val syncId = java.util.UUID.randomUUID().toString()
            execSQL(
                "UPDATE scenario_table SET sync_id = '$syncId', updated_at_ms = $now WHERE id = $id",
            )
        }
    }
}
