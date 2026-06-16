/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.scenarios.favorite

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class FavoriteScenarioBroadcastReceiver : BroadcastReceiver() {

    @Inject lateinit var pickerController: FavoriteScenarioPickerController

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != SacIntents.ACTION_SHOW_FAVORITE_SCENARIOS) return

        pickerController.show(
            onUnavailable = {
                context.startActivity(SacIntents.showFavoriteScenariosActivity(context))
            },
        )
    }
}
