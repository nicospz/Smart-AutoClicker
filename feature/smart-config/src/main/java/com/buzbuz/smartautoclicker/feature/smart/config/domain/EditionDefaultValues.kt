/*
 * Copyright (C) 2024 Kevin Buzeau
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
package com.buzbuz.smartautoclicker.feature.smart.config.domain

import android.content.Context

import com.buzbuz.smartautoclicker.core.domain.model.AND
import com.buzbuz.smartautoclicker.core.domain.model.ConditionOperator
import com.buzbuz.smartautoclicker.core.domain.model.EXACT
import com.buzbuz.smartautoclicker.core.domain.model.action.Click
import com.buzbuz.smartautoclicker.core.domain.model.action.ToggleEvent
import com.buzbuz.smartautoclicker.core.domain.model.condition.TriggerCondition
import com.buzbuz.smartautoclicker.feature.smart.config.R
import com.buzbuz.smartautoclicker.feature.smart.config.utils.getClickPressDurationConfig
import com.buzbuz.smartautoclicker.feature.smart.config.utils.getConditionThresholdConfig
import com.buzbuz.smartautoclicker.feature.smart.config.utils.getClickWaitAfterConfig
import com.buzbuz.smartautoclicker.feature.smart.config.utils.getEventConfigPreferences
import com.buzbuz.smartautoclicker.feature.smart.config.utils.getIntentIsAdvancedConfig
import com.buzbuz.smartautoclicker.feature.smart.config.utils.getPauseDurationConfig
import com.buzbuz.smartautoclicker.feature.smart.config.utils.getSwipeDurationConfig

internal class EditionDefaultValues {

    fun eventName(context: Context): String =
        context.getString(R.string.default_event_name)
    fun eventGroupName(context: Context): String =
        context.getString(R.string.default_event_group_name)
    @ConditionOperator fun eventConditionOperator(): Int =
        AND

    fun conditionName(context: Context): String =
        context.getString(R.string.default_condition_name)
    fun conditionThreshold(context: Context): Int =
        context.getEventConfigPreferences().getConditionThresholdConfig(context)
    fun conditionDetectionType(): Int =
        EXACT
    fun conditionShouldBeDetected(): Boolean =
        true

    fun clickName(context: Context): String =
        context.getString(R.string.default_click_name)
    fun clickPressDuration(context: Context): Long =
        context.getEventConfigPreferences().getClickPressDurationConfig(context)
    fun clickWaitAfterDuration(context: Context): Long =
        context.getEventConfigPreferences().getClickWaitAfterConfig()
    fun clickPositionType(): Click.PositionType =
        Click.PositionType.USER_SELECTED

    fun swipeName(context: Context): String =
        context.getString(R.string.default_swipe_name)
    fun swipeDuration(context: Context): Long =
        context.getEventConfigPreferences().getSwipeDurationConfig(context)

    fun precisionGestureName(context: Context): String =
        context.getString(R.string.default_precision_gesture_name)

    fun pauseName(context: Context): String =
        context.getString(R.string.default_pause_name)
    fun pauseDuration(context: Context): Long =
        context.getEventConfigPreferences().getPauseDurationConfig(context)

    fun intentName(context: Context): String =
        context.getString(R.string.default_intent_name)
    fun intentIsAdvanced(context: Context): Boolean =
        context.getEventConfigPreferences().getIntentIsAdvancedConfig(context)

    fun toggleEventName(context: Context): String =
        context.getString(R.string.default_toggle_event_name)
    fun eventToggleType(): ToggleEvent.ToggleType =
        ToggleEvent.ToggleType.ENABLE

    fun changeCounterName(context: Context): String =
        context.getString(R.string.default_change_counter_name)

    fun notificationName(context: Context): String =
        context.getString(R.string.default_notification_name)

    fun systemActionName(context: Context): String =
        context.getString(R.string.default_system_action_name)

    fun setTextName(context: Context): String =
        context.getString(R.string.default_set_text_name)

    fun precisionTextName(context: Context): String =
        context.getString(R.string.default_precision_text_name)

    fun stopScenarioName(context: Context): String =
        context.getString(R.string.default_stop_scenario_name)

    fun throwletCatchName(context: Context): String =
        context.getString(R.string.default_throwlet_catch_name)

    fun counterComparisonOperation(): TriggerCondition.OnCounterCountReached.ComparisonOperation =
        TriggerCondition.OnCounterCountReached.ComparisonOperation.EQUALS
}
