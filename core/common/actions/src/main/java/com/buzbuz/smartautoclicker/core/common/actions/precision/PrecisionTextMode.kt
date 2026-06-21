package com.buzbuz.smartautoclicker.core.common.actions.precision

enum class PrecisionTextMode {
    KEY_EVENTS,
    SHELL_INPUT,
    CLIPBOARD_PASTE,
    CLIPBOARD_PASTE_REPLACE,
}

fun PrecisionTextMode.isClipboardPaste(): Boolean =
    this == PrecisionTextMode.CLIPBOARD_PASTE || this == PrecisionTextMode.CLIPBOARD_PASTE_REPLACE
