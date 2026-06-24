package com.buzbuz.smartautoclicker.feature.throwlet

import android.content.Context
import androidx.core.content.edit

class FastCatchStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(lane: HelperLane): Boolean = prefs.getBoolean(prefKey(lane), false)

    fun save(lane: HelperLane, enabled: Boolean) {
        prefs.edit { putBoolean(prefKey(lane), enabled) }
    }

    fun toggle(lane: HelperLane): Boolean {
        val next = !load(lane)
        save(lane, next)
        return next
    }

    private fun prefKey(lane: HelperLane): String = "$PREF_PREFIX${lane.name.lowercase()}"

    companion object {
        private const val PREFS = "throwlet_fast_catch"
        private const val PREF_PREFIX = "enabled."
    }
}

class HoldToThrowStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(lane: HelperLane): Boolean = prefs.getBoolean(prefKey(lane), false)

    fun save(lane: HelperLane, enabled: Boolean) {
        prefs.edit { putBoolean(prefKey(lane), enabled) }
    }

    fun toggle(lane: HelperLane): Boolean {
        val next = !load(lane)
        save(lane, next)
        return next
    }

    private fun prefKey(lane: HelperLane): String = "$PREF_PREFIX${lane.name.lowercase()}"

    companion object {
        private const val PREFS = "throwlet_hold_to_throw"
        private const val PREF_PREFIX = "enabled."
    }
}

data class ThrowGestureTuning(
    val speed: Double = ThrowSpeedStore.DEFAULT_SPEED,
    val power: Double = ThrowSpeedStore.DEFAULT_POWER,
    val topOffset: Int = 0,
    val bottomOffset: Int = 0,
    val leftOffset: Int = 0,
    val rightOffset: Int = 0,
) {
    val dx: Int get() = rightOffset - leftOffset
    val dy: Int get() = bottomOffset - topOffset
    val hasTranslation: Boolean get() = dx != 0 || dy != 0
}

class DeviceThrowTuningStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): ThrowGestureTuning =
        ThrowGestureTuning(
            speed = loadPositiveDouble(KEY_SPEED, DEFAULT_SPEED),
            power = loadPositiveDouble(KEY_POWER, DEFAULT_POWER),
            topOffset = prefs.getInt(KEY_OFFSET_TOP, 0),
            bottomOffset = prefs.getInt(KEY_OFFSET_BOTTOM, 0),
            leftOffset = prefs.getInt(KEY_OFFSET_LEFT, 0),
            rightOffset = prefs.getInt(KEY_OFFSET_RIGHT, 0),
        )

    fun save(tuning: ThrowGestureTuning): ThrowGestureTuning {
        val sanitized = tuning.copy(
            speed = tuning.speed.takeIf { it.isValidPositiveNumber() } ?: DEFAULT_SPEED,
            power = tuning.power.takeIf { it.isValidPositiveNumber() } ?: DEFAULT_POWER,
        )
        prefs.edit {
            putString(KEY_SPEED, sanitized.speed.toString())
            putString(KEY_POWER, sanitized.power.toString())
            putInt(KEY_OFFSET_TOP, sanitized.topOffset)
            putInt(KEY_OFFSET_BOTTOM, sanitized.bottomOffset)
            putInt(KEY_OFFSET_LEFT, sanitized.leftOffset)
            putInt(KEY_OFFSET_RIGHT, sanitized.rightOffset)
        }
        return sanitized
    }

    fun reset(): ThrowGestureTuning = save(ThrowGestureTuning(speed = DEFAULT_SPEED, power = DEFAULT_POWER))

    private fun loadPositiveDouble(key: String, default: Double): Double =
        prefs.getString(key, null)?.toDoubleOrNull()?.takeIf { it.isValidPositiveNumber() }
            ?: default

    companion object {
        const val DEFAULT_SPEED = 1.0
        const val DEFAULT_POWER = 1.0
        private const val PREFS = "throwlet_device_tuning"
        private const val KEY_SPEED = "speed"
        private const val KEY_POWER = "power"
        private const val KEY_OFFSET_TOP = "offset.top"
        private const val KEY_OFFSET_BOTTOM = "offset.bottom"
        private const val KEY_OFFSET_LEFT = "offset.left"
        private const val KEY_OFFSET_RIGHT = "offset.right"
    }
}

class ThrowSpeedStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun loadEnabled(lane: HelperLane): Boolean = prefs.getBoolean(enabledKey(lane), false)

    fun loadSpeed(lane: HelperLane): Double =
        prefs.getString(speedKey(lane), null)?.toDoubleOrNull()?.takeIf { it.isValidPositiveNumber() }
            ?: DEFAULT_SPEED

    fun loadTuning(lane: HelperLane): ThrowGestureTuning =
        ThrowGestureTuning(
            speed = loadSpeed(lane),
            power = loadPower(lane),
            topOffset = loadOffset(lane, OFFSET_TOP),
            bottomOffset = loadOffset(lane, OFFSET_BOTTOM),
            leftOffset = loadOffset(lane, OFFSET_LEFT),
            rightOffset = loadOffset(lane, OFFSET_RIGHT),
        )

    fun saveEnabled(lane: HelperLane, enabled: Boolean) {
        prefs.edit { putBoolean(enabledKey(lane), enabled) }
    }

    fun saveSpeed(lane: HelperLane, speed: Double): Double {
        val sanitized = speed.takeIf { it.isValidPositiveNumber() } ?: DEFAULT_SPEED
        prefs.edit { putString(speedKey(lane), sanitized.toString()) }
        return sanitized
    }

    fun saveTuning(lane: HelperLane, tuning: ThrowGestureTuning): ThrowGestureTuning {
        val sanitized = tuning.copy(
            speed = tuning.speed.takeIf { it.isValidPositiveNumber() } ?: DEFAULT_SPEED,
            power = tuning.power.takeIf { it.isValidPositiveNumber() } ?: DEFAULT_POWER,
        )
        prefs.edit {
            putString(speedKey(lane), sanitized.speed.toString())
            putString(powerKey(lane), sanitized.power.toString())
            putInt(offsetKey(lane, OFFSET_TOP), sanitized.topOffset)
            putInt(offsetKey(lane, OFFSET_BOTTOM), sanitized.bottomOffset)
            putInt(offsetKey(lane, OFFSET_LEFT), sanitized.leftOffset)
            putInt(offsetKey(lane, OFFSET_RIGHT), sanitized.rightOffset)
        }
        return sanitized
    }

    fun resetTuning(lane: HelperLane): ThrowGestureTuning {
        val defaults = ThrowGestureTuning()
        prefs.edit {
            putBoolean(enabledKey(lane), false)
            putString(speedKey(lane), defaults.speed.toString())
            putString(powerKey(lane), defaults.power.toString())
            putInt(offsetKey(lane, OFFSET_TOP), defaults.topOffset)
            putInt(offsetKey(lane, OFFSET_BOTTOM), defaults.bottomOffset)
            putInt(offsetKey(lane, OFFSET_LEFT), defaults.leftOffset)
            putInt(offsetKey(lane, OFFSET_RIGHT), defaults.rightOffset)
        }
        return defaults
    }

    fun toggle(lane: HelperLane): Boolean {
        val next = !loadEnabled(lane)
        saveEnabled(lane, next)
        return next
    }

    private fun enabledKey(lane: HelperLane): String = "$ENABLED_PREFIX${lane.name.lowercase()}"

    private fun speedKey(lane: HelperLane): String = "$SPEED_PREFIX${lane.name.lowercase()}"

    private fun powerKey(lane: HelperLane): String = "$POWER_PREFIX${lane.name.lowercase()}"

    private fun offsetKey(lane: HelperLane, direction: String): String =
        "$OFFSET_PREFIX${lane.name.lowercase()}.$direction"

    private fun loadPower(lane: HelperLane): Double =
        prefs.getString(powerKey(lane), null)?.toDoubleOrNull()?.takeIf { it.isValidPositiveNumber() }
            ?: DEFAULT_POWER

    private fun loadOffset(lane: HelperLane, direction: String): Int =
        prefs.getInt(offsetKey(lane, direction), 0)

    companion object {
        const val DEFAULT_SPEED = 1.2
        const val DEFAULT_POWER = 1.0
        private const val PREFS = "throwlet_throw_speed"
        private const val ENABLED_PREFIX = "enabled."
        private const val SPEED_PREFIX = "speed."
        private const val POWER_PREFIX = "power."
        private const val OFFSET_PREFIX = "offset."
        private const val OFFSET_TOP = "top"
        private const val OFFSET_BOTTOM = "bottom"
        private const val OFFSET_LEFT = "left"
        private const val OFFSET_RIGHT = "right"
    }
}

private fun Double.isValidPositiveNumber(): Boolean = isFinite() && this > 0.0
