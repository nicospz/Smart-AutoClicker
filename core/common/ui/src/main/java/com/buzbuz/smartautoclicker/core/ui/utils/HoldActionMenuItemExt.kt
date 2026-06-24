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
package com.buzbuz.smartautoclicker.core.ui.utils

import android.graphics.Typeface
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.buzbuz.smartautoclicker.core.ui.R

fun View.bindHoldActionMenuItem(
    @DrawableRes iconRes: Int,
    @StringRes labelRes: Int,
    @StringRes contentDescriptionRes: Int,
) {
    val label = context.getString(labelRes)
    findViewById<ImageView>(R.id.hold_action_item_icon).apply {
        setImageResource(iconRes)
        imageTintList = ContextCompat.getColorStateList(context, R.color.overlayMenuButtons)
    }
    findViewById<TextView>(R.id.hold_action_item_label).apply {
        text = label
        setTypeface(typeface, Typeface.NORMAL)
    }
    contentDescription = context.getString(contentDescriptionRes)
}

fun View.holdActionMenuItemIcon(): ImageView =
    findViewById(R.id.hold_action_item_icon)

fun View.holdActionMenuItemLabel(): TextView =
    findViewById(R.id.hold_action_item_label)
