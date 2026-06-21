/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.feature.smart.config.ui.common.model.action

import android.content.Context
import androidx.annotation.DrawableRes
import com.buzbuz.smartautoclicker.core.domain.model.action.ThrowletCatch
import com.buzbuz.smartautoclicker.core.ui.R

@DrawableRes
internal fun getThrowletCatchIconRes(): Int = R.drawable.ic_intent

internal fun ThrowletCatch.getDescription(context: Context, inError: Boolean): String {
    if (inError) return context.getString(com.buzbuz.smartautoclicker.feature.smart.config.R.string.item_error_action_invalid_generic)

    val base = context.getString(
        com.buzbuz.smartautoclicker.feature.smart.config.R.string.item_throwlet_catch_details_text,
        operation.toDisplayString(context),
        mode.toDisplayString(context),
        lane.toDisplayString(context),
    )
    val override = pokemonNameOverride?.takeIf { it.isNotBlank() } ?: return base
    return context.getString(
        com.buzbuz.smartautoclicker.feature.smart.config.R.string.item_throwlet_catch_details_with_override_text,
        base,
        override,
    )
}

internal fun ThrowletCatch.Operation.toDisplayString(context: Context): String =
    when (this) {
        ThrowletCatch.Operation.TOGGLE ->
            context.getString(com.buzbuz.smartautoclicker.feature.smart.config.R.string.field_dropdown_throwlet_catch_operation_toggle)
        ThrowletCatch.Operation.HIDE ->
            context.getString(com.buzbuz.smartautoclicker.feature.smart.config.R.string.field_dropdown_throwlet_catch_operation_hide)
        ThrowletCatch.Operation.SHOW ->
            context.getString(com.buzbuz.smartautoclicker.feature.smart.config.R.string.field_dropdown_throwlet_catch_operation_show)
    }

internal fun ThrowletCatch.Mode.toDisplayString(context: Context): String =
    when (this) {
        ThrowletCatch.Mode.CATCH ->
            context.getString(com.buzbuz.smartautoclicker.feature.smart.config.R.string.field_dropdown_throwlet_catch_mode_catch)
        ThrowletCatch.Mode.BUDDY ->
            context.getString(com.buzbuz.smartautoclicker.feature.smart.config.R.string.field_dropdown_throwlet_catch_mode_buddy)
    }

internal fun ThrowletCatch.Lane.toDisplayString(context: Context): String =
    when (this) {
        ThrowletCatch.Lane.FULL ->
            context.getString(com.buzbuz.smartautoclicker.feature.smart.config.R.string.field_dropdown_throwlet_catch_lane_full)
        ThrowletCatch.Lane.TOP ->
            context.getString(com.buzbuz.smartautoclicker.feature.smart.config.R.string.field_dropdown_throwlet_catch_lane_top)
        ThrowletCatch.Lane.BOTTOM ->
            context.getString(com.buzbuz.smartautoclicker.feature.smart.config.R.string.field_dropdown_throwlet_catch_lane_bottom)
    }
