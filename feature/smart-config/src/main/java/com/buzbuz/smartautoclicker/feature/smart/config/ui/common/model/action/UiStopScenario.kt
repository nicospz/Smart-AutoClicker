/*
 * Copyright (C) 2026 Kevin Buzeau
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
package com.buzbuz.smartautoclicker.feature.smart.config.ui.common.model.action

import android.content.Context
import androidx.annotation.DrawableRes

import com.buzbuz.smartautoclicker.core.domain.model.action.StopScenario
import com.buzbuz.smartautoclicker.feature.smart.config.R

@DrawableRes
internal fun getStopScenarioIconRes(): Int = R.drawable.ic_stop

internal fun StopScenario.getDescription(context: Context, inError: Boolean): String =
    if (inError) context.getString(R.string.item_error_action_invalid_generic)
    else context.getString(R.string.item_stop_scenario_details)
