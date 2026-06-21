/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.scenarios.category

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class CategoryScenarioBroadcastReceiver : BroadcastReceiver() {

    @Inject lateinit var pickerController: CategoryScenarioPickerController

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != CategorySacIntents.ACTION_SHOW_CATEGORY_SCENARIOS) return

        val category = intent.getStringExtra(CategorySacIntents.EXTRA_CATEGORY) ?: return

        pickerController.show(
            category = category,
            onUnavailable = {
                context.startActivity(CategorySacIntents.showCategoryScenariosActivity(context, category))
            },
        )
    }
}
