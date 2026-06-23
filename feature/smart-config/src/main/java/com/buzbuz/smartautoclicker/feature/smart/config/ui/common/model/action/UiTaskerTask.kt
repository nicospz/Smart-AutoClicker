/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.feature.smart.config.ui.common.model.action

import android.content.Context
import com.buzbuz.smartautoclicker.core.domain.model.action.TaskerTask
import com.buzbuz.smartautoclicker.feature.smart.config.R

internal fun getTaskerTaskIconRes(): Int = R.drawable.ic_intent

internal fun TaskerTask.getDescription(context: Context, inError: Boolean): String {
    val taskName = taskName?.takeIf { it.isNotBlank() }
        ?: return context.getString(R.string.item_tasker_task_details_missing)

    val waitSuffix = if (waitForCompletion) {
        context.getString(R.string.item_tasker_task_details_wait_suffix)
    } else {
        ""
    }
    return context.getString(R.string.item_tasker_task_details_text, taskName, waitSuffix)
}
