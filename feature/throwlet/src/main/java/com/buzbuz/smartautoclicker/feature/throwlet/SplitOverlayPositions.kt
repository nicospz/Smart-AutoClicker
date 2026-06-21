package com.buzbuz.smartautoclicker.feature.throwlet

import android.content.Context
import androidx.core.content.edit

/** Lane-local overlay anchors so top and bottom play buttons stay on the same in-game target. */
class SplitOverlayPositions(
    private val context: Context,
    private val mode: HelperMode,
    private val layout: SplitScreenLayout,
) {
    private val prefs = context.getSharedPreferences("throwlet_split_overlay_${mode.name}", Context.MODE_PRIVATE)

    fun restoredActionPosition(lane: HelperLane, defaultFullX: Int): Pair<Int, Int> {
        if (lane == HelperLane.FULL) {
            return defaultFullX to prefs.getInt(fullYKey(HelperLane.FULL), layout.defaultLocalActionY(mode) + layout.dividerPx / 2)
        }
        val localY = prefs.getInt(localYKey(), -1)
        if (localY >= 0) {
            val x = prefs.getInt(actionXKey(), defaultFullX)
            return x to layout.fullYForLane(localY, lane)
        }
        val legacy = context.getSharedPreferences("throwlet_overlay_${mode.name}_${lane.name}", Context.MODE_PRIVATE)
        val legacyY = legacy.getInt("action_y", layout.fullYForLane(layout.defaultLocalActionY(mode), lane))
        val legacyX = legacy.getInt("action_x", defaultFullX)
        val migratedLocalY = layout.localYForLane(legacyY, lane)
        prefs.edit {
            putInt(actionXKey(), legacyX)
            putInt(localYKey(), migratedLocalY)
        }
        return legacyX to layout.fullYForLane(migratedLocalY, lane)
    }

    fun saveActionPosition(lane: HelperLane, fullX: Int, fullY: Int) {
        prefs.edit {
            putInt(actionXKey(), fullX)
            putInt(localYKey(), layout.localYForLane(fullY, lane))
            if (lane == HelperLane.FULL) {
                putInt(fullYKey(HelperLane.FULL), fullY)
            }
        }
    }

    private fun actionXKey(): String = "action_x"
    private fun localYKey(): String = "action_local_y"
    private fun fullYKey(lane: HelperLane): String = "action_full_y_${lane.name}"
}
