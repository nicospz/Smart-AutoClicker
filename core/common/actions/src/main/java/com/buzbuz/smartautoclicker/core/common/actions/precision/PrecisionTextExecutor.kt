package com.buzbuz.smartautoclicker.core.common.actions.precision

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrecisionTextExecutor @Inject constructor(
    private val setup: PrecisionGestureHelperSetup,
) {

    suspend fun typeText(text: String, mode: PrecisionTextMode): PrecisionTextResult {
        setup.ensureShizukuReady().throwIfNotRunning()

        when (mode) {
            PrecisionTextMode.KEY_EVENTS -> typeWithKeyEvents(text)
            PrecisionTextMode.SHELL_INPUT -> setup.runShizukuShell("/system/bin/input text ${text.toInputTextArgument()}", 30_000)
        }

        return PrecisionTextResult(mode, text.length)
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

