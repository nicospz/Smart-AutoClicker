/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.feature.throwlet.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.buzbuz.smartautoclicker.feature.throwlet.BuddyCropEntity
import com.buzbuz.smartautoclicker.feature.throwlet.GestureEntity
import com.buzbuz.smartautoclicker.feature.throwlet.GestureMode
import com.buzbuz.smartautoclicker.feature.throwlet.HelperLane
import com.buzbuz.smartautoclicker.feature.throwlet.HelperMode
import com.buzbuz.smartautoclicker.feature.throwlet.SizeI
import com.buzbuz.smartautoclicker.feature.throwlet.SplitCalibrationEntity
import com.buzbuz.smartautoclicker.feature.throwlet.ThrowScore

@Dao
interface GestureDao {
    @Query("SELECT * FROM gestures ORDER BY updatedAtMs DESC")
    suspend fun all(): List<GestureEntity>

    @Query("SELECT * FROM gestures WHERE pokemonKey = :pokemonKey AND gestureMode = :gestureMode LIMIT 1")
    suspend fun get(pokemonKey: String, gestureMode: GestureMode): GestureEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: GestureEntity)

    @Query("UPDATE gestures SET throwScore = :throwScore, updatedAtMs = :updatedAtMs WHERE pokemonKey = :pokemonKey AND gestureMode = :gestureMode")
    suspend fun setThrowScore(
        pokemonKey: String,
        gestureMode: GestureMode,
        throwScore: String,
        updatedAtMs: Long = System.currentTimeMillis(),
    ): Int
}

@Dao
interface BuddyCropDao {
    @Query("SELECT * FROM buddy_crops ORDER BY updatedAtMs DESC")
    suspend fun all(): List<BuddyCropEntity>

    @Query("SELECT * FROM buddy_crops WHERE enabled = 1 ORDER BY updatedAtMs DESC")
    suspend fun enabled(): List<BuddyCropEntity>

    @Query("SELECT * FROM buddy_crops WHERE pokemonKey = :pokemonKey LIMIT 1")
    suspend fun getByPokemonKey(pokemonKey: String): BuddyCropEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BuddyCropEntity): Long
}

@Dao
interface SplitCalibrationDao {
    @Query("SELECT * FROM split_calibrations WHERE profileKey = :profileKey LIMIT 1")
    suspend fun get(profileKey: String = "default"): SplitCalibrationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SplitCalibrationEntity)
}

@Database(
    entities = [GestureEntity::class, BuddyCropEntity::class, SplitCalibrationEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class ThrowletDatabase : RoomDatabase() {
    abstract fun gestureDao(): GestureDao
    abstract fun buddyCropDao(): BuddyCropDao
    abstract fun splitCalibrationDao(): SplitCalibrationDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS split_calibrations (
                        profileKey TEXT NOT NULL,
                        topToBottomTouchDy INTEGER NOT NULL,
                        topToBottomScreenshotDy INTEGER,
                        updatedAtMs INTEGER NOT NULL,
                        PRIMARY KEY(profileKey)
                    )
                    """.trimIndent(),
                )
            }
        }

        @Volatile private var instance: ThrowletDatabase? = null

        fun get(context: Context): ThrowletDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                ThrowletDatabase::class.java,
                "throwlet.db",
            )
                .addMigrations(MIGRATION_1_2)
                .build()
                .also { instance = it }
        }
    }
}

class GestureStore(private val db: ThrowletDatabase) {
    suspend fun find(pokemonKey: String, mode: GestureMode): GestureEntity? =
        db.gestureDao().get(pokemonKey, mode)

    suspend fun save(
        pokemonKey: String,
        pokemonName: String,
        gestureMode: GestureMode,
        payloadHex: String,
        eventCount: Int,
        durationMs: Long,
        helperMode: HelperMode,
        sourceLane: HelperLane,
        display: SizeI,
        laneOffsetTouch: Int? = null,
    ): GestureEntity {
        val existing = db.gestureDao().get(pokemonKey, gestureMode)
        val entity = GestureEntity(
            pokemonKey = pokemonKey,
            pokemonName = pokemonName,
            gestureMode = gestureMode,
            payloadHex = payloadHex,
            eventCount = eventCount,
            durationMs = durationMs,
            helperMode = helperMode.name,
            sourceLane = sourceLane,
            sourceDisplayWidth = display.width,
            sourceDisplayHeight = display.height,
            throwScore = existing?.throwScore ?: if (helperMode == HelperMode.CATCH) ThrowScore.GREAT.name else null,
            laneOffsetTouch = laneOffsetTouch,
            updatedAtMs = System.currentTimeMillis(),
        )
        db.gestureDao().upsert(entity)
        return entity
    }

    suspend fun setThrowScore(pokemonKey: String, gestureMode: GestureMode, throwScore: ThrowScore): Boolean =
        db.gestureDao().setThrowScore(
            pokemonKey = pokemonKey,
            gestureMode = gestureMode,
            throwScore = throwScore.name,
        ) > 0
}
