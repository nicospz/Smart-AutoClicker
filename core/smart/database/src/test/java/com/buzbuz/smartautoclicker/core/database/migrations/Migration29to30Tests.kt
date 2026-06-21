/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.core.database.migrations

import android.content.Context
import android.os.Build
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.buzbuz.smartautoclicker.core.database.ClickDatabase
import com.buzbuz.smartautoclicker.core.database.utils.assertColumnEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.Q])
class Migration29to30Tests {

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
            .getDatabasePath("migration-test-29-30").path
    }

    @Test
    fun migrate_createsEventGroupTableAndEventGroupIdColumn() {
        clickHelper.createDatabase(dbPath, OLD_DB_VERSION).use { dbV29 ->
            dbV29.execSQL(
                """
                    INSERT INTO scenario_table (id, name, detection_quality, randomize, keep_screen_on)
                    VALUES ($SCENARIO_ID, "Scenario", 1200, 0, 0)
                """.trimIndent()
            )
            dbV29.execSQL(
                """
                    INSERT INTO event_table (
                        id, scenario_id, name, operator, priority, enabled_on_start, type, keep_detecting
                    ) VALUES ($EVENT_ID, $SCENARIO_ID, "Event", 1, 0, 1, "IMAGE_EVENT", 0)
                """.trimIndent()
            )
            dbV29.execSQL(
                """
                    INSERT INTO condition_table (
                        id, eventId, name, type, priority, path,
                        area_left, area_top, area_right, area_bottom,
                        threshold, detection_type, shouldBeDetected
                    ) VALUES (
                        $CONDITION_ID, $EVENT_ID, "Condition", "ImageCondition", 0, "/path",
                        0, 0, 10, 10, 10, 0, 1
                    )
                """.trimIndent()
            )
        }

        clickHelper.runMigrationsAndValidate(dbPath, NEW_DB_VERSION, true, Migration29to30).use { dbV30 ->
            dbV30.query("SELECT name FROM sqlite_master WHERE type='table' AND name='event_group_table'").use { cursor ->
                assert(cursor.moveToFirst())
            }

            dbV30.query("SELECT * FROM event_table").use { cursor ->
                cursor.moveToFirst()
                cursor.assertColumnEquals(null as Long?, "group_id")
            }

            dbV30.query("SELECT * FROM condition_table").use { cursor ->
                cursor.moveToFirst()
                cursor.assertColumnEquals(EVENT_ID, "eventId")
                cursor.assertColumnEquals(null as Long?, "event_group_id")
            }
        }
    }

    private companion object {
        private const val OLD_DB_VERSION = 29
        private const val NEW_DB_VERSION = 30
        private const val SCENARIO_ID = 1L
        private const val EVENT_ID = 1L
        private const val CONDITION_ID = 1L
    }
}
