/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.scenarios.named

import android.content.Context
import android.content.Intent
import com.buzbuz.smartautoclicker.scenarios.category.CategoryScenarioLauncherActivity

/** Public intent actions for starting a scenario by display name. */
object NamedScenarioSacIntents {

    const val ACTION_START_SCENARIO_BY_NAME = "com.buzbuz.smartautoclicker.action.START_SCENARIO_BY_NAME"

    const val EXTRA_SCENARIO_NAME = "com.buzbuz.smartautoclicker.extra.SCENARIO_NAME"

    fun startScenarioByNameActivity(context: Context, scenarioName: String): Intent =
        Intent(context, CategoryScenarioLauncherActivity::class.java)
            .setAction(ACTION_START_SCENARIO_BY_NAME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            .putExtra(EXTRA_SCENARIO_NAME, scenarioName)
}
