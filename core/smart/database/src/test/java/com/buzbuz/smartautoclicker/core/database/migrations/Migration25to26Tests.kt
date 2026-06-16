/*
 * Copyright (C) 2026 Kevin Buzeau
 */
package com.buzbuz.smartautoclicker.core.database.migrations

import android.content.Context
import android.os.Build

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry

import com.buzbuz.smartautoclicker.core.database.ClickDatabase
import com.buzbuz.smartautoclicker.core.database.utils.assertColumnEquals
import com.buzbuz.smartautoclicker.core.database.utils.assertCountEquals

import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.Q])
class Migration25to26Tests {

    @get:Rule
    val clickHelper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ClickDatabase::class.java,
    )

    private lateinit var dbPath: String

    @Before
    fun setUp() {
        dbPath = ApplicationProvider
            .getApplicationContext<Context>()
            .getDatabasePath("migration-test-25-26").path
    }

    @Test
    fun eventToggleTable_migrate_addsPrefixColumnAndNullableEventId() {
        clickHelper.createDatabase(dbPath, OLD_DB_VERSION).use { dbV25 ->
            dbV25.insertV25Scenario()
            dbV25.insertV25ToggleEventAction()
            dbV25.insertV25EventToggle()
        }

        clickHelper.runMigrationsAndValidate(dbPath, NEW_DB_VERSION, true, Migration25to26).use { dbV26 ->
            dbV26.query("SELECT * FROM event_toggle_table").use { cursor ->
                cursor.assertCountEquals(1)
                cursor.moveToFirst()
                cursor.assertColumnEquals(EVENT_TOGGLE_ID, "id")
                cursor.assertColumnEquals(ACTION_ID, "action_id")
                cursor.assertColumnEquals("DISABLE", "toggle_type")
                cursor.assertColumnEquals(TARGET_EVENT_ID, "toggle_event_id")
                cursor.assertColumnEquals(null as String?, "event_name_prefix")
            }
        }
    }

    private fun SupportSQLiteDatabase.insertV25Scenario() {
        execSQL(
            """
                INSERT INTO scenario_table (id, name, detection_quality, randomize, keep_screen_on)
                VALUES ($SCENARIO_ID, "Scenario", 1200, 0, 0)
            """.trimIndent()
        )
        execSQL(
            """
                INSERT INTO event_table (
                    id, scenario_id, name, operator, priority, enabled_on_start, type, keep_detecting,
                    image_detection_mode, cooldown_ms
                )
                VALUES ($TARGET_EVENT_ID, $SCENARIO_ID, "Target", 1, 0, 1, "IMAGE_EVENT", 0, "STANDARD", 0)
            """.trimIndent()
        )
    }

    private fun SupportSQLiteDatabase.insertV25ToggleEventAction() {
        execSQL(
            """
                INSERT INTO action_table (
                    id, eventId, priority, name, type, toggle_all
                )
                VALUES ($ACTION_ID, $TARGET_EVENT_ID, 0, "Toggle", "TOGGLE_EVENT", 0)
            """.trimIndent()
        )
    }

    private fun SupportSQLiteDatabase.insertV25EventToggle() {
        execSQL(
            """
                INSERT INTO event_toggle_table (id, action_id, toggle_type, toggle_event_id)
                VALUES ($EVENT_TOGGLE_ID, $ACTION_ID, "DISABLE", $TARGET_EVENT_ID)
            """.trimIndent()
        )
    }

    private companion object {
        private const val OLD_DB_VERSION = 25
        private const val NEW_DB_VERSION = 26
        private const val SCENARIO_ID = 1L
        private const val TARGET_EVENT_ID = 2L
        private const val ACTION_ID = 3L
        private const val EVENT_TOGGLE_ID = 4L
    }
}
