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
class Migration30to31Tests {

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
            .getDatabasePath("migration-test-30-31").path
    }

    @Test
    fun migrate_addsParentGroupIdColumn() {
        clickHelper.createDatabase(dbPath, OLD_DB_VERSION).use { dbV30 ->
            dbV30.execSQL(
                """
                    INSERT INTO scenario_table (id, name, detection_quality, randomize, keep_screen_on)
                    VALUES ($SCENARIO_ID, "Scenario", 1200, 0, 0)
                """.trimIndent()
            )
            dbV30.execSQL(
                """
                    INSERT INTO event_group_table (
                        id, scenario_id, name, event_type, operator, priority
                    ) VALUES ($PARENT_GROUP_ID, $SCENARIO_ID, "Parent", "IMAGE", 1, 0)
                """.trimIndent()
            )
            dbV30.execSQL(
                """
                    INSERT INTO event_group_table (
                        id, scenario_id, name, event_type, operator, priority
                    ) VALUES ($CHILD_GROUP_ID, $SCENARIO_ID, "Child", "IMAGE", 1, 1)
                """.trimIndent()
            )
        }

        clickHelper.runMigrationsAndValidate(dbPath, NEW_DB_VERSION, true, Migration30to31).use { dbV31 ->
            dbV31.query("SELECT * FROM event_group_table WHERE id = $PARENT_GROUP_ID").use { cursor ->
                cursor.moveToFirst()
                cursor.assertColumnEquals(null as Long?, "parent_group_id")
            }
            dbV31.query("SELECT * FROM event_group_table WHERE id = $CHILD_GROUP_ID").use { cursor ->
                cursor.moveToFirst()
                cursor.assertColumnEquals(null as Long?, "parent_group_id")
            }
        }
    }

    private companion object {
        private const val OLD_DB_VERSION = 30
        private const val NEW_DB_VERSION = 31
        private const val SCENARIO_ID = 1L
        private const val PARENT_GROUP_ID = 10L
        private const val CHILD_GROUP_ID = 11L
    }
}
