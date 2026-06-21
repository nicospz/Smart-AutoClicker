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
class Migration26to27Tests {

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
            .getDatabasePath("migration-test-26-27").path
    }

    @Test
    fun scenarioTable_migrate_addsCategoryColumn() {
        clickHelper.createDatabase(dbPath, OLD_DB_VERSION).use { dbV26 ->
            dbV26.execSQL(
                """
                    INSERT INTO scenario_table (id, name, detection_quality, randomize, keep_screen_on)
                    VALUES ($SCENARIO_ID, "Scenario", 1200, 0, 0)
                """.trimIndent()
            )
        }

        clickHelper.runMigrationsAndValidate(dbPath, NEW_DB_VERSION, true, Migration26to27).use { dbV27 ->
            dbV27.query("SELECT * FROM scenario_table").use { cursor ->
                cursor.moveToFirst()
                cursor.assertColumnEquals(null as String?, "category")
            }
        }
    }

    private companion object {
        private const val OLD_DB_VERSION = 26
        private const val NEW_DB_VERSION = 27
        private const val SCENARIO_ID = 1L
    }
}
