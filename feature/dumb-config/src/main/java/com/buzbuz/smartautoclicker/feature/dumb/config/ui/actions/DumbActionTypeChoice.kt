/*
 * Copyright (C) 2023 Kevin Buzeau
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.buzbuz.smartautoclicker.feature.dumb.config.ui.actions

import com.buzbuz.smartautoclicker.core.common.overlays.dialog.implementation.DialogChoice
import com.buzbuz.smartautoclicker.feature.dumb.config.R


/** Choices for the dumb action type selection dialog. */
sealed class DumbActionTypeChoice(
    title: Int,
    description: Int,
    iconId: Int?,
): DialogChoice(
    title = title,
    description = description,
    iconId = iconId,
) {
    /** Copy Action choice. */
    data object Copy : DumbActionTypeChoice(
        R.string.item_title_dumb_action_copy,
        R.string.item_desc_dumb_action_copy,
        R.drawable.ic_copy,
    )

    /** Click Action choice. */
    data object Click : DumbActionTypeChoice(
        R.string.item_title_dumb_click,
        R.string.item_desc_dumb_click,
        R.drawable.ic_click,
    )
    /** Swipe Action choice. */
    data object Swipe : DumbActionTypeChoice(
        R.string.item_title_dumb_swipe,
        R.string.item_desc_dumb_swipe,
        R.drawable.ic_swipe,
    )
    /** Precision gesture Action choice. */
    data object PrecisionGesture : DumbActionTypeChoice(
        R.string.item_title_dumb_precision_gesture,
        R.string.item_desc_dumb_precision_gesture,
        R.drawable.ic_swipe,
    )
    /** Precision text Action choice. */
    data object PrecisionText : DumbActionTypeChoice(
        R.string.item_title_dumb_precision_text,
        R.string.item_desc_dumb_precision_text,
        R.drawable.ic_wait,
    )
    /** Pause Action choice. */
    data object Pause : DumbActionTypeChoice(
        R.string.item_title_dumb_pause,
        R.string.item_desc_dumb_pause,
        R.drawable.ic_wait,
    )

    /** Tasker task Action choice. */
    data object TaskerTask : DumbActionTypeChoice(
        R.string.item_title_dumb_tasker_task,
        R.string.item_desc_dumb_tasker_task,
        R.drawable.ic_intent,
    )

    /** Manual Throwlet Catch action choice. */
    data object ManualThrowletCatch : DumbActionTypeChoice(
        R.string.item_title_dumb_manual_throwlet_catch,
        R.string.item_desc_dumb_manual_throwlet_catch,
        R.drawable.ic_intent,
    )
}

fun allDumbActionChoices() = listOf(
    DumbActionTypeChoice.Copy,
    DumbActionTypeChoice.Click,
    DumbActionTypeChoice.Swipe,
    DumbActionTypeChoice.PrecisionGesture,
    DumbActionTypeChoice.PrecisionText,
    DumbActionTypeChoice.Pause,
    DumbActionTypeChoice.TaskerTask,
    DumbActionTypeChoice.ManualThrowletCatch,
)
