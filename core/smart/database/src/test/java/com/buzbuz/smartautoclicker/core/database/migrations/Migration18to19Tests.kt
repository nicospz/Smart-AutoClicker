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

/** Tests the auto migration from database version 18 to 19. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.Q])
class Migration18to19Tests {

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
            .getDatabasePath("migration-test").path
    }

    @Test
    fun clickDatabase_migrate_scenarioDefaults() {
        runSmartMigrationDefaultsTest(clickHelper)
    }

    private fun runSmartMigrationDefaultsTest(helper: MigrationTestHelper) {
        helper.createDatabase(dbPath, OLD_DB_VERSION).use { dbV18 ->
            dbV18.insertV18Scenario()
            dbV18.insertV18Event()
            dbV18.insertV18ClickAction()
        }

        helper.runMigrationsAndValidate(dbPath, NEW_DB_VERSION, true, Migration18to19).use { dbV19 ->
            dbV19.query("SELECT * FROM scenario_table").use { scenarioCursor ->
                scenarioCursor.assertCountEquals(1)
                scenarioCursor.moveToFirst()
                scenarioCursor.assertColumnEquals(false, "is_favorite")
                scenarioCursor.assertColumnEquals(false, "auto_start")
                scenarioCursor.assertColumnEquals(0L, "auto_start_delay_ms")
            }

            dbV19.query("SELECT * FROM action_table").use { actionCursor ->
                actionCursor.assertCountEquals(1)
                actionCursor.moveToFirst()
                actionCursor.assertColumnEquals(0L, "click_wait_after_ms")
            }
        }
    }

    private fun SupportSQLiteDatabase.insertV18Scenario() {
        execSQL(
            """
                INSERT INTO scenario_table (id, name, detection_quality, randomize, keep_screen_on)
                VALUES ($SCENARIO_ID, "Scenario", 1200, 0, 0)
            """.trimIndent()
        )
    }

    private fun SupportSQLiteDatabase.insertV18Event() {
        execSQL(
            """
                INSERT INTO event_table (id, scenario_id, name, operator, priority, enabled_on_start, type, keep_detecting)
                VALUES ($EVENT_ID, $SCENARIO_ID, "Event", 1, 0, 1, "IMAGE_EVENT", 0)
            """.trimIndent()
        )
    }

    private fun SupportSQLiteDatabase.insertV18ClickAction() {
        execSQL(
            """
                INSERT INTO action_table (id, eventId, priority, name, type, clickPositionType, x, y, pressDuration)
                VALUES ($ACTION_ID, $EVENT_ID, 0, "Click", "CLICK", "USER_SELECTED", 10, 20, 50)
            """.trimIndent()
        )
    }

    private companion object {
        private const val OLD_DB_VERSION = 18
        private const val NEW_DB_VERSION = 19
        private const val SCENARIO_ID = 1L
        private const val EVENT_ID = 2L
        private const val ACTION_ID = 3L
    }
}
