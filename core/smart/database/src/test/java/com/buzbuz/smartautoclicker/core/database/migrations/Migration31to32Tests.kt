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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.Q])
class Migration31to32Tests {

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
            .getDatabasePath("migration-test-31-32").path
    }

    @Test
    fun migrate_addsSyncColumnsAndBackfillsSyncId() {
        clickHelper.createDatabase(dbPath, OLD_DB_VERSION).use { dbV31 ->
            dbV31.execSQL(
                """
                    INSERT INTO scenario_table (id, name, detection_quality, randomize, keep_screen_on)
                    VALUES ($SCENARIO_ID, "Scenario", 1200, 0, 0)
                """.trimIndent(),
            )
        }

        clickHelper.runMigrationsAndValidate(dbPath, NEW_DB_VERSION, true, Migration31to32).use { dbV32 ->
            dbV32.query("SELECT sync_id, updated_at_ms FROM scenario_table WHERE id = $SCENARIO_ID").use { cursor ->
                assertTrue(cursor.moveToFirst())
                val syncId = cursor.getString(0)
                val updatedAtMs = cursor.getLong(1)
                assertNotEquals("", syncId)
                assertTrue(updatedAtMs > 0L)
            }
        }
    }

    private companion object {
        private const val OLD_DB_VERSION = 31
        private const val NEW_DB_VERSION = 32
        private const val SCENARIO_ID = 12L
    }
}
