/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.feature.smart.config.ui.action.throwletcatch

import androidx.annotation.StringRes
import com.buzbuz.smartautoclicker.core.domain.model.action.ThrowletCatch
import com.buzbuz.smartautoclicker.core.ui.bindings.dropdown.DropdownItem
import com.buzbuz.smartautoclicker.feature.smart.config.R

sealed class ThrowletCatchOperationItem(
    @StringRes title: Int,
) : DropdownItem(title) {

    data object Toggle : ThrowletCatchOperationItem(
        title = R.string.field_dropdown_throwlet_catch_operation_toggle,
    )

    data object Hide : ThrowletCatchOperationItem(
        title = R.string.field_dropdown_throwlet_catch_operation_hide,
    )

    data object Show : ThrowletCatchOperationItem(
        title = R.string.field_dropdown_throwlet_catch_operation_show,
    )
}

internal val throwletCatchOperationItems: List<ThrowletCatchOperationItem>
    get() = listOf(
        ThrowletCatchOperationItem.Show,
        ThrowletCatchOperationItem.Hide,
        ThrowletCatchOperationItem.Toggle,
    )

internal fun ThrowletCatch.Operation.toOperationItem(): ThrowletCatchOperationItem =
    when (this) {
        ThrowletCatch.Operation.TOGGLE -> ThrowletCatchOperationItem.Toggle
        ThrowletCatch.Operation.HIDE -> ThrowletCatchOperationItem.Hide
        ThrowletCatch.Operation.SHOW -> ThrowletCatchOperationItem.Show
    }

internal fun ThrowletCatchOperationItem.toThrowletCatchOperation(): ThrowletCatch.Operation =
    when (this) {
        ThrowletCatchOperationItem.Toggle -> ThrowletCatch.Operation.TOGGLE
        ThrowletCatchOperationItem.Hide -> ThrowletCatch.Operation.HIDE
        ThrowletCatchOperationItem.Show -> ThrowletCatch.Operation.SHOW
    }
