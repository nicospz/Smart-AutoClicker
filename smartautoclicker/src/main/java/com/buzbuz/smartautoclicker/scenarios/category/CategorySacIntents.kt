/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.scenarios.category

import android.content.Context
import android.content.Intent

/** Public intent actions for external launchers (Tasker, MacroDroid, etc.). */
object CategorySacIntents {

    /** Show the category-scenarios picker as an accessibility overlay. */
    const val ACTION_SHOW_CATEGORY_SCENARIOS = "com.buzbuz.smartautoclicker.action.SHOW_CATEGORY_SCENARIOS"

    /** Activity action used by shortcuts and external apps. */
    const val ACTION_START_CATEGORY_SCENARIO = "com.buzbuz.smartautoclicker.action.START_CATEGORY_SCENARIO"

    /** Intent extra containing the target category name. Use an empty string for uncategorized scenarios. */
    const val EXTRA_CATEGORY = "com.buzbuz.smartautoclicker.extra.CATEGORY"

    /** Broadcast intent — preferred when the accessibility service is running. */
    fun showCategoryScenariosBroadcast(context: Context, category: String): Intent =
        Intent(ACTION_SHOW_CATEGORY_SCENARIOS)
            .setPackage(context.packageName)
            .putExtra(EXTRA_CATEGORY, category)

    /** Activity intent — fallback when the accessibility service is not running. */
    fun showCategoryScenariosActivity(context: Context, category: String): Intent =
        Intent(context, CategoryScenarioLauncherActivity::class.java)
            .setAction(ACTION_START_CATEGORY_SCENARIO)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            .putExtra(EXTRA_CATEGORY, category)
}
