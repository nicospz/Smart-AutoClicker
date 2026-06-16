package com.buzbuz.smartautoclicker.feature.smart.config.ui.common.model.action

import android.content.Context
import com.buzbuz.smartautoclicker.core.domain.model.action.PrecisionText
import com.buzbuz.smartautoclicker.feature.smart.config.R

internal fun PrecisionText.getDescription(context: Context, inError: Boolean): String {
    if (inError) return context.getString(R.string.item_error_action_invalid_generic)

    return if (text.isEmpty()) context.getString(R.string.item_set_text_details_text_empty)
    else context.getString(R.string.item_precision_text_details_text, text.length, mode.name)
}

