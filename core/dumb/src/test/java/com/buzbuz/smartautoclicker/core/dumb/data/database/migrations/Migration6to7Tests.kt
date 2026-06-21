/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.core.dumb.data.database.migrations

import android.content.Context
import android.os.Build
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.buzbuz.smartautoclicker.core.dumb.data.database.DumbDatabase
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.Q])
class Migration6to7Tests {

    @get:Rule
    val dumbHelper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        DumbDatabase::class.java,
    )

    private lateinit var dbPath: String

    @Before
    fun setUp() {
        dbPath = ApplicationProvider
            .getApplicationContext<Context>()
            .getDatabasePath("migration-test-6-7").path
    }

    @Test
    fun migrate_addsSyncColumnsAndBackfillsSyncId() {
        dumbHelper.createDatabase(dbPath, OLD_DB_VERSION).use { dbV6 ->
            dbV6.execSQL(
                """
                    INSERT INTO dumb_scenario_table (
                        id, name, repeat_count, is_repeat_infinite,
                        max_duration_minutes, is_duration_infinite, randomize
                    ) VALUES ($SCENARIO_ID, "Dumb", 1, 0, 10, 0, 0)
                """.trimIndent(),
            )
        }

        dumbHelper.runMigrationsAndValidate(dbPath, NEW_DB_VERSION, true, Migration6to7).use { dbV7 ->
            dbV7.query("SELECT sync_id, updated_at_ms FROM dumb_scenario_table WHERE id = $SCENARIO_ID").use { cursor ->
                assertTrue(cursor.moveToFirst())
                val syncId = cursor.getString(0)
                val updatedAtMs = cursor.getLong(1)
                assertNotEquals("", syncId)
                assertTrue(updatedAtMs > 0L)
            }
        }
    }

    private companion object {
        private const val OLD_DB_VERSION = 6
        private const val NEW_DB_VERSION = 7
        private const val SCENARIO_ID = 8L
    }
}
