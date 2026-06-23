/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.feature.dumb.config.ui.actions.throwlet

import androidx.annotation.StringRes
import com.buzbuz.smartautoclicker.core.common.actions.ThrowletCatchLane
import com.buzbuz.smartautoclicker.core.common.actions.ThrowletCatchOperation
import com.buzbuz.smartautoclicker.core.ui.bindings.dropdown.DropdownItem
import com.buzbuz.smartautoclicker.feature.dumb.config.R

sealed class ManualThrowletOperationItem(@StringRes title: Int) : DropdownItem(title) {
    data object Show : ManualThrowletOperationItem(R.string.field_dropdown_manual_throwlet_operation_show)
    data object Hide : ManualThrowletOperationItem(R.string.field_dropdown_manual_throwlet_operation_hide)
    data object Toggle : ManualThrowletOperationItem(R.string.field_dropdown_manual_throwlet_operation_toggle)
}

internal val manualThrowletOperationItems: List<ManualThrowletOperationItem>
    get() = listOf(
        ManualThrowletOperationItem.Show,
        ManualThrowletOperationItem.Hide,
        ManualThrowletOperationItem.Toggle,
    )

internal fun ThrowletCatchOperation.toManualThrowletOperationItem(): ManualThrowletOperationItem =
    when (this) {
        ThrowletCatchOperation.SHOW -> ManualThrowletOperationItem.Show
        ThrowletCatchOperation.HIDE -> ManualThrowletOperationItem.Hide
        ThrowletCatchOperation.TOGGLE -> ManualThrowletOperationItem.Toggle
    }

internal fun ManualThrowletOperationItem.toThrowletCatchOperation(): ThrowletCatchOperation =
    when (this) {
        ManualThrowletOperationItem.Show -> ThrowletCatchOperation.SHOW
        ManualThrowletOperationItem.Hide -> ThrowletCatchOperation.HIDE
        ManualThrowletOperationItem.Toggle -> ThrowletCatchOperation.TOGGLE
    }

sealed class ManualThrowletLaneItem(@StringRes title: Int) : DropdownItem(title) {
    data object Full : ManualThrowletLaneItem(R.string.field_dropdown_manual_throwlet_lane_full)
    data object Top : ManualThrowletLaneItem(R.string.field_dropdown_manual_throwlet_lane_top)
    data object Bottom : ManualThrowletLaneItem(R.string.field_dropdown_manual_throwlet_lane_bottom)
}

internal val manualThrowletLaneItems: List<ManualThrowletLaneItem>
    get() = listOf(
        ManualThrowletLaneItem.Full,
        ManualThrowletLaneItem.Top,
        ManualThrowletLaneItem.Bottom,
    )

internal fun ThrowletCatchLane.toManualThrowletLaneItem(): ManualThrowletLaneItem =
    when (this) {
        ThrowletCatchLane.FULL -> ManualThrowletLaneItem.Full
        ThrowletCatchLane.TOP -> ManualThrowletLaneItem.Top
        ThrowletCatchLane.BOTTOM -> ManualThrowletLaneItem.Bottom
    }

internal fun ManualThrowletLaneItem.toThrowletCatchLane(): ThrowletCatchLane =
    when (this) {
        ManualThrowletLaneItem.Full -> ThrowletCatchLane.FULL
        ManualThrowletLaneItem.Top -> ThrowletCatchLane.TOP
        ManualThrowletLaneItem.Bottom -> ThrowletCatchLane.BOTTOM
    }
