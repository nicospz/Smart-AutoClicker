/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.scenarios.favorite

import android.content.Context
import android.content.Intent

/** Public intent actions for external launchers (Tasker, MacroDroid, etc.). */
object SacIntents {

    /** Show the favorite-scenarios picker as an accessibility overlay. */
    const val ACTION_SHOW_FAVORITE_SCENARIOS = "com.buzbuz.smartautoclicker.action.SHOW_FAVORITE_SCENARIOS"

    /** Legacy activity action used by the home-screen shortcut. */
    const val ACTION_START_FAVORITE_SCENARIO = "com.buzbuz.smartautoclicker.action.START_FAVORITE_SCENARIO"

    /** Broadcast intent — preferred for gestures; does not launch an activity. */
    fun showFavoriteScenariosBroadcast(context: Context): Intent =
        Intent(ACTION_SHOW_FAVORITE_SCENARIOS)
            .setPackage(context.packageName)

    /** Activity intent — fallback when the accessibility service is not running. */
    fun showFavoriteScenariosActivity(context: Context): Intent =
        Intent(context, FavoriteScenarioLauncherActivity::class.java)
            .setAction(ACTION_START_FAVORITE_SCENARIO)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
}
