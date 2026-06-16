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
class Migration23to24Tests {

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
            .getDatabasePath("migration-test-23-24").path
    }

    @Test
    fun clickDatabase_migrate_offsetRepeatDefaults() {
        clickHelper.createDatabase(dbPath, OLD_DB_VERSION).use { dbV23 ->
            dbV23.insertV23Scenario()
            dbV23.insertV23Event()
        }

        clickHelper.runMigrationsAndValidate(dbPath, NEW_DB_VERSION, true, Migration23to24).use { dbV24 ->
            dbV24.query("SELECT * FROM event_table").use { eventCursor ->
                eventCursor.assertCountEquals(1)
                eventCursor.moveToFirst()
                eventCursor.assertColumnEquals(0, "offset_repeat_count")
                eventCursor.assertColumnEquals(0, "offset_repeat_x")
                eventCursor.assertColumnEquals(0, "offset_repeat_y")
                eventCursor.assertColumnEquals("FIRST_MATCH", "offset_repeat_match_mode")
            }
        }
    }

    private fun SupportSQLiteDatabase.insertV23Scenario() {
        execSQL(
            """
                INSERT INTO scenario_table (id, name, detection_quality, randomize, keep_screen_on)
                VALUES ($SCENARIO_ID, "Scenario", 1200, 0, 0)
            """.trimIndent()
        )
    }

    private fun SupportSQLiteDatabase.insertV23Event() {
        execSQL(
            """
                INSERT INTO event_table (
                    id, scenario_id, name, operator, priority, enabled_on_start, type, keep_detecting,
                    image_detection_mode, cooldown_ms
                )
                VALUES ($EVENT_ID, $SCENARIO_ID, "Event", 1, 0, 1, "IMAGE_EVENT", 0, "STANDARD", 0)
            """.trimIndent()
        )
    }

    private companion object {
        private const val OLD_DB_VERSION = 23
        private const val NEW_DB_VERSION = 24
        private const val SCENARIO_ID = 1L
        private const val EVENT_ID = 2L
    }
}
