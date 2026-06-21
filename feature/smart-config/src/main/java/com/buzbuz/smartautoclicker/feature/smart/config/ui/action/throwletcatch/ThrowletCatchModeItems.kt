/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.feature.smart.config.ui.action.throwletcatch

import com.buzbuz.smartautoclicker.core.domain.model.action.ThrowletCatch
import com.buzbuz.smartautoclicker.core.ui.bindings.dropdown.DropdownItem
import com.buzbuz.smartautoclicker.feature.smart.config.R

sealed class ThrowletCatchModeItem(
    @androidx.annotation.StringRes title: Int,
) : DropdownItem(title) {

    data object Catch : ThrowletCatchModeItem(
        title = R.string.field_dropdown_throwlet_catch_mode_catch,
    )

    data object Buddy : ThrowletCatchModeItem(
        title = R.string.field_dropdown_throwlet_catch_mode_buddy,
    )
}

internal val throwletCatchModeItems: List<ThrowletCatchModeItem>
    get() = listOf(
        ThrowletCatchModeItem.Catch,
        ThrowletCatchModeItem.Buddy,
    )

internal fun ThrowletCatch.Mode.toModeItem(): ThrowletCatchModeItem =
    when (this) {
        ThrowletCatch.Mode.CATCH -> ThrowletCatchModeItem.Catch
        ThrowletCatch.Mode.BUDDY -> ThrowletCatchModeItem.Buddy
    }

internal fun ThrowletCatchModeItem.toThrowletCatchMode(): ThrowletCatch.Mode =
    when (this) {
        ThrowletCatchModeItem.Catch -> ThrowletCatch.Mode.CATCH
        ThrowletCatchModeItem.Buddy -> ThrowletCatch.Mode.BUDDY
    }
