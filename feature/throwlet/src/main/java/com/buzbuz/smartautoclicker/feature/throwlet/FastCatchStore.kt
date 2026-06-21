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
