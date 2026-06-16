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
class Migration24to25Tests {

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
            .getDatabasePath("migration-test-24-25").path
    }

    @Test
    fun clickDatabase_migrate_anchoredRepeatToStandard() {
        clickHelper.createDatabase(dbPath, OLD_DB_VERSION).use { dbV24 ->
            dbV24.insertV24Scenario()
            dbV24.insertV24AnchoredEvent()
        }

        clickHelper.runMigrationsAndValidate(dbPath, NEW_DB_VERSION, true, Migration24to25).use { dbV25 ->
            dbV25.query("SELECT * FROM event_table").use { eventCursor ->
                eventCursor.assertCountEquals(1)
                eventCursor.moveToFirst()
                eventCursor.assertColumnEquals("STANDARD", "image_detection_mode")
                eventCursor.assertColumnEquals(null as Long?, "anchor_condition_id")
            }
        }
    }

    private fun SupportSQLiteDatabase.insertV24Scenario() {
        execSQL(
            """
                INSERT INTO scenario_table (id, name, detection_quality, randomize, keep_screen_on)
                VALUES ($SCENARIO_ID, "Scenario", 1200, 0, 0)
            """.trimIndent()
        )
    }

    private fun SupportSQLiteDatabase.insertV24AnchoredEvent() {
        execSQL(
            """
                INSERT INTO event_table (
                    id, scenario_id, name, operator, priority, enabled_on_start, type, keep_detecting,
                    image_detection_mode, anchor_condition_id, cooldown_ms
                )
                VALUES ($EVENT_ID, $SCENARIO_ID, "Event", 1, 0, 1, "IMAGE_EVENT", 0, "ANCHORED_REPEAT", 42, 0)
            """.trimIndent()
        )
    }

    private companion object {
        private const val OLD_DB_VERSION = 24
        private const val NEW_DB_VERSION = 25
        private const val SCENARIO_ID = 1L
        private const val EVENT_ID = 2L
    }
}
