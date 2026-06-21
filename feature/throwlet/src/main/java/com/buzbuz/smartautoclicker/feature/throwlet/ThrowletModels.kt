/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.feature.throwlet

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class HelperMode { CATCH, BUDDY }

enum class HelperLane { FULL, SPLIT_TOP, SPLIT_BOTTOM }

enum class GestureMode {
    CATCH_FULL,
    CATCH_SPLIT_BOTTOM_NORMALIZED,
    BUDDY_FULL,
    BUDDY_SPLIT_BOTTOM_NORMALIZED,
}

enum class ThrowScore(val badge: String, val label: String) {
    UNKNOWN("?", "Unknown"),
    NICE("N", "Nice"),
    GREAT("G", "Great"),
    EXCELLENT("E", "Excellent");

    fun next(): ThrowScore = when (this) {
        UNKNOWN -> NICE
        NICE -> GREAT
        GREAT -> EXCELLENT
        EXCELLENT -> UNKNOWN
    }

    companion object {
        fun fromStored(value: String?): ThrowScore =
            entries.firstOrNull { it.name == value } ?: GREAT
    }
}

data class HelperSessionKey(val lane: HelperLane)

data class CatchDetectionState(
    val pokemonKey: String?,
    val pokemonName: String?,
    val confidencePercent: Int?,
    val hasGesture: Boolean,
    val message: String,
    val throwScore: ThrowScore = ThrowScore.UNKNOWN,
)

data class SplitCalibration(
    val profileKey: String,
    val topToBottomTouchDy: Int,
    val topToBottomScreenshotDy: Int?,
    val updatedAtMs: Long,
)

data class RectI(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

data class SizeI(val width: Int, val height: Int)

data class PointI(val x: Int, val y: Int)

data class GesturePayload(val points: List<PointI>, val durationMs: Long) {
    val eventCount: Int get() = points.size
    fun translated(dy: Int): GesturePayload = copy(points = points.map { it.copy(y = it.y + dy) })
    fun dominantLane(divider: Int): HelperLane? {
        if (points.isEmpty()) return null
        val split = divider.coerceAtLeast(1)
        val medianY = points.map { it.y }.sorted()[points.size / 2]
        return if (medianY < split) HelperLane.SPLIT_TOP else HelperLane.SPLIT_BOTTOM
    }
}

@Entity(tableName = "split_calibrations")
data class SplitCalibrationEntity(
    @PrimaryKey val profileKey: String,
    val topToBottomTouchDy: Int,
    val topToBottomScreenshotDy: Int?,
    val updatedAtMs: Long,
)

object GestureModes {
    fun storageMode(helperMode: HelperMode, lane: HelperLane): GestureMode = when (helperMode) {
        HelperMode.CATCH -> when (lane) {
            HelperLane.FULL -> GestureMode.CATCH_FULL
            HelperLane.SPLIT_TOP, HelperLane.SPLIT_BOTTOM -> GestureMode.CATCH_SPLIT_BOTTOM_NORMALIZED
        }
        HelperMode.BUDDY -> when (lane) {
            HelperLane.FULL -> GestureMode.BUDDY_FULL
            HelperLane.SPLIT_TOP, HelperLane.SPLIT_BOTTOM -> GestureMode.BUDDY_SPLIT_BOTTOM_NORMALIZED
        }
    }
}

@Entity(tableName = "gestures", primaryKeys = ["pokemonKey", "gestureMode"])
data class GestureEntity(
    val pokemonKey: String,
    val pokemonName: String,
    val gestureMode: GestureMode,
    val payloadHex: String,
    val eventCount: Int,
    val durationMs: Long,
    val helperMode: String,
    val sourceLane: HelperLane,
    val sourceDisplayWidth: Int,
    val sourceDisplayHeight: Int,
    val throwScore: String? = null,
    val laneOffsetTouch: Int? = null,
    val updatedAtMs: Long,
)

@Entity(
    tableName = "buddy_crops",
    indices = [Index(value = ["pokemonKey"], unique = true)],
)
data class BuddyCropEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pokemonKey: String,
    val pokemonName: String,
    val imagePath: String,
    val sourceLane: HelperLane,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val cropLeft: Int,
    val cropTop: Int,
    val cropRight: Int,
    val cropBottom: Int,
    val thresholdPercent: Int = 85,
    val enabled: Boolean = true,
    val createdAtMs: Long,
    val updatedAtMs: Long,
)

internal val GestureEntity.syncKey: String
    get() = "$pokemonKey\u0000${gestureMode.name}"
