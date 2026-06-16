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

/** Tests the auto migration from database version 21 to 22. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.Q])
class Migration21to22Tests {

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
            .getDatabasePath("migration-test-21-22").path
    }

    @Test
    fun clickDatabase_migrate_imageEventAnchoredDefaults() {
        runImageEventAnchoredDefaultsTest(clickHelper)
    }

    private fun runImageEventAnchoredDefaultsTest(helper: MigrationTestHelper) {
        helper.createDatabase(dbPath, OLD_DB_VERSION).use { dbV21 ->
            dbV21.insertV21Scenario()
            dbV21.insertV21Event()
        }

        helper.runMigrationsAndValidate(dbPath, NEW_DB_VERSION, true, Migration21to22).use { dbV22 ->
            dbV22.query("SELECT * FROM event_table").use { eventCursor ->
                eventCursor.assertCountEquals(1)
                eventCursor.moveToFirst()
                eventCursor.assertColumnEquals("STANDARD", "image_detection_mode")
                eventCursor.assertColumnEquals(null as Long?, "anchor_condition_id")
            }
        }
    }

    private fun SupportSQLiteDatabase.insertV21Scenario() {
        execSQL(
            """
                INSERT INTO scenario_table (id, name, detection_quality, randomize, keep_screen_on)
                VALUES ($SCENARIO_ID, "Scenario", 1200, 0, 0)
            """.trimIndent()
        )
    }

    private fun SupportSQLiteDatabase.insertV21Event() {
        execSQL(
            """
                INSERT INTO event_table (id, scenario_id, name, operator, priority, enabled_on_start, type, keep_detecting)
                VALUES ($EVENT_ID, $SCENARIO_ID, "Event", 1, 0, 1, "IMAGE_EVENT", 0)
            """.trimIndent()
        )
    }

    private companion object {
        private const val OLD_DB_VERSION = 21
        private const val NEW_DB_VERSION = 22
        private const val SCENARIO_ID = 1L
        private const val EVENT_ID = 2L
    }
}
