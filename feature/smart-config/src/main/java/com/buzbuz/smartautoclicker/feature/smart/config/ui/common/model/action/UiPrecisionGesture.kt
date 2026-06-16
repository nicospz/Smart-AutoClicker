/*
 * Copyright (C) 2026
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.buzbuz.smartautoclicker.feature.smart.config.ui.common.model.action

import android.content.Context
import androidx.annotation.DrawableRes
import com.buzbuz.smartautoclicker.core.domain.model.action.PrecisionGesture
import com.buzbuz.smartautoclicker.core.ui.utils.formatDuration
import com.buzbuz.smartautoclicker.feature.smart.config.R

@DrawableRes
internal fun getPrecisionGestureIconRes(): Int =
    R.drawable.ic_swipe

internal fun PrecisionGesture.getDescription(context: Context, inError: Boolean): String = when {
    inError -> context.getString(R.string.item_error_action_invalid_generic)
    else -> context.getString(
        R.string.item_precision_gesture_details,
        eventCount ?: 0,
        formatDuration(durationMs ?: 0L),
    )
}
