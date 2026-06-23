package com.buzbuz.smartautoclicker.buttons

import android.content.Context
import android.content.Intent

/** Public intent actions for external launchers (Tasker, MacroDroid, etc.). */
object ButtonLayoutSacIntents {

    /** Activity action used by Quick Settings and external apps to choose and start a saved button layout. */
    const val ACTION_START_BUTTON_LAYOUT = "com.buzbuz.smartautoclicker.action.START_BUTTON_LAYOUT"

    fun showButtonLayoutsActivity(context: Context): Intent =
        Intent(context, ButtonLayoutLauncherActivity::class.java)
            .setAction(ACTION_START_BUTTON_LAYOUT)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
}
