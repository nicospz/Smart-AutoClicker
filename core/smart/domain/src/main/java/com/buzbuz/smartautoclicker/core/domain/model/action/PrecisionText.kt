package com.buzbuz.smartautoclicker.core.domain.model.action

import com.buzbuz.smartautoclicker.core.base.identifier.Identifier
import com.buzbuz.smartautoclicker.core.common.actions.precision.PrecisionTextMode
import com.buzbuz.smartautoclicker.core.common.actions.precision.isClipboardPaste

data class PrecisionText(
    override val id: Identifier,
    override val eventId: Identifier,
    override val name: String?,
    override var priority: Int = 0,
    val text: String = "",
    val mode: PrecisionTextMode = PrecisionTextMode.KEY_EVENTS,
) : Action() {

    override fun isComplete(): Boolean =
        !name.isNullOrBlank() && (text.isNotEmpty() || mode.isClipboardPaste())

    override fun hashCodeNoIds(): Int = listOf(name, priority, text, mode).hashCode()

    override fun deepCopy(): Action = copy(name = name?.let { "" + it }, text = "" + text)
}
