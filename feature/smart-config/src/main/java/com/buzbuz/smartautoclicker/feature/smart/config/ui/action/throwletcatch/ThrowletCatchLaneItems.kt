/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.feature.smart.config.ui.action.throwletcatch

import com.buzbuz.smartautoclicker.core.domain.model.action.ThrowletCatch
import com.buzbuz.smartautoclicker.core.ui.bindings.dropdown.DropdownItem
import com.buzbuz.smartautoclicker.feature.smart.config.R

sealed class ThrowletCatchLaneItem(
    @androidx.annotation.StringRes title: Int,
) : DropdownItem(title) {

    data object Full : ThrowletCatchLaneItem(
        title = R.string.field_dropdown_throwlet_catch_lane_full,
    )

    data object Top : ThrowletCatchLaneItem(
        title = R.string.field_dropdown_throwlet_catch_lane_top,
    )

    data object Bottom : ThrowletCatchLaneItem(
        title = R.string.field_dropdown_throwlet_catch_lane_bottom,
    )
}

internal val throwletCatchLaneItems: List<ThrowletCatchLaneItem>
    get() = listOf(
        ThrowletCatchLaneItem.Full,
        ThrowletCatchLaneItem.Top,
        ThrowletCatchLaneItem.Bottom,
    )

internal fun ThrowletCatch.Lane.toLaneItem(): ThrowletCatchLaneItem =
    when (this) {
        ThrowletCatch.Lane.FULL -> ThrowletCatchLaneItem.Full
        ThrowletCatch.Lane.TOP -> ThrowletCatchLaneItem.Top
        ThrowletCatch.Lane.BOTTOM -> ThrowletCatchLaneItem.Bottom
    }

internal fun ThrowletCatchLaneItem.toThrowletCatchLane(): ThrowletCatch.Lane =
    when (this) {
        ThrowletCatchLaneItem.Full -> ThrowletCatch.Lane.FULL
        ThrowletCatchLaneItem.Top -> ThrowletCatch.Lane.TOP
        ThrowletCatchLaneItem.Bottom -> ThrowletCatch.Lane.BOTTOM
    }
