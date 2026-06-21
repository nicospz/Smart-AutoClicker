package com.buzbuz.smartautoclicker.core.common.actions.precision

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.KeyEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrecisionTextExecutor @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val setup: PrecisionGestureHelperSetup,
) {

    suspend fun typeText(text: String, mode: PrecisionTextMode): PrecisionTextResult {
        setup.ensureShizukuReady().throwIfNotRunning()

        when (mode) {
            PrecisionTextMode.KEY_EVENTS -> typeWithKeyEvents(text)
            PrecisionTextMode.SHELL_INPUT -> setup.runShizukuShell("/system/bin/input text ${text.toInputTextArgument()}", 30_000)
            PrecisionTextMode.CLIPBOARD_PASTE -> pasteWithClipboard(text, replaceExistingText = false)
            PrecisionTextMode.CLIPBOARD_PASTE_REPLACE -> pasteWithClipboard(text, replaceExistingText = true)
        }

        return PrecisionTextResult(mode, text.length)
    }

    private fun pasteWithClipboard(text: String, replaceExistingText: Boolean) {
        if (replaceExistingText) {
            setup.runShizukuShell(
                "/system/bin/input keycombination ${KeyEvent.KEYCODE_CTRL_LEFT} ${KeyEvent.KEYCODE_A}",
                5_000,
            )
        }

        if (text.isNotEmpty()) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("text", text))
        }

        setup.runShizukuShell("/system/bin/input keyevent ${KeyEvent.KEYCODE_PASTE}", 5_000)
    }

    private fun typeWithKeyEvents(text: String) {
        text.forEach { char ->
            val keyCode = char.toAndroidKeyCode()
            if (keyCode != null) {
                setup.runShizukuShell("/system/bin/input keyevent $keyCode", 5_000)
            } else {
                setup.runShizukuShell("/system/bin/input text ${char.toString().toInputTextArgument()}", 5_000)
            }
        }
    }

    private fun Char.toAndroidKeyCode(): Int? = when (this) {
        in 'a'..'z' -> 29 + (this - 'a')
        in 'A'..'Z' -> 29 + (this - 'A')
        in '0'..'9' -> 7 + (this - '0')
        ' ' -> 62
        '\n', '\r' -> 66
        '\b' -> 67
        else -> null
    }

    private fun String.toInputTextArgument(): String =
        replace("%", "%25")
            .replace(" ", "%s")
            .replace("'", "'\\''")
            .let { "'$it'" }
}

data class PrecisionTextResult(
    val mode: PrecisionTextMode,
    val chars: Int,
)
