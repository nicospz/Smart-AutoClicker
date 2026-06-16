/*
 * Copyright (C) 2023 Kevin Buzeau
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
package com.buzbuz.smartautoclicker.core.dumb.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.buzbuz.smartautoclicker.core.base.interfaces.EntityWithId
import kotlinx.serialization.Serializable

/**
 * Entity defining an action from an action.
 */
@Entity(
    tableName = "dumb_action_table",
    indices = [Index("dumb_scenario_id")],
    foreignKeys = [
        ForeignKey(
            entity = DumbScenarioEntity::class,
            parentColumns = ["id"],
            childColumns = ["dumb_scenario_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ]
)
@Serializable
data class DumbActionEntity(
    @PrimaryKey(autoGenerate = true) override var id: Long,
    @ColumnInfo(name = "dumb_scenario_id") var dumbScenarioId: Long,
    @ColumnInfo(name = "priority") var priority: Int = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "type") val type: DumbActionType,

    // Only for repeatable actions
    @ColumnInfo(name = "repeat_count") val repeatCount: Int? = null,
    @ColumnInfo(name = "is_repeat_infinite") val isRepeatInfinite: Boolean? = null,
    @ColumnInfo(name = "repeat_delay") val repeatDelay: Long? = null,

    // ActionType.CLICK
    @ColumnInfo(name = "press_duration") val pressDuration: Long? = null,
    @ColumnInfo(name = "x") val x: Int? = null,
    @ColumnInfo(name = "y") val y: Int? = null,

    // ActionType.SWIPE
    @ColumnInfo(name = "swipe_duration") val swipeDuration: Long? = null,
    @ColumnInfo(name = "fromX") val fromX: Int? = null,
    @ColumnInfo(name = "fromY") val fromY: Int? = null,
    @ColumnInfo(name = "toX") val toX: Int? = null,
    @ColumnInfo(name = "toY") val toY: Int? = null,

    // ActionType.PAUSE
    @ColumnInfo(name = "pause_duration") val pauseDuration: Long? = null,

    // ActionType.PRECISION_GESTURE
    @ColumnInfo(name = "precision_gesture_payload_hex") val precisionGesturePayloadHex: String? = null,
    @ColumnInfo(name = "precision_gesture_event_count") val precisionGestureEventCount: Int? = null,
    @ColumnInfo(name = "precision_gesture_duration_ms") val precisionGestureDurationMs: Long? = null,
    @ColumnInfo(name = "precision_gesture_helper_mode") val precisionGestureHelperMode: String? = null,

    // ActionType.PRECISION_TEXT
    @ColumnInfo(name = "precision_text_value") val precisionTextValue: String? = null,
    @ColumnInfo(name = "precision_text_mode") val precisionTextMode: String? = null,
) : EntityWithId

/**
 * Type of [DumbActionEntity].
 * For each type there is a set of values that will be available in the database, all others will always be null. Refers
 * to the [DumbActionEntity] documentation for values/type association.
 *
 * /!\ DO NOT RENAME: ActionType enum name is used in the database.
 */
enum class DumbActionType {
    /** A single tap on the screen. */
    CLICK,
    /** A swipe on the screen. */
    SWIPE,
    /** A pause, waiting before the next action. */
    PAUSE,
    /** Raw precision gesture recorded and replayed through the Shizuku helper. */
    PRECISION_GESTURE,
    /** Text injected through Shizuku shell key events or input text. */
    PRECISION_TEXT,
}

/** Type converter to read/write the [DumbActionType] into the database. */
internal class DumbActionTypeStringConverter {
    @TypeConverter
    fun fromString(value: String): DumbActionType = DumbActionType.valueOf(value)
    @TypeConverter
    fun toString(action: DumbActionType): String = action.toString()
}
