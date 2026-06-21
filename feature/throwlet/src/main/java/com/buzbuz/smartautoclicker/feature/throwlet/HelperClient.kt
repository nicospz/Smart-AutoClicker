package com.buzbuz.smartautoclicker.feature.throwlet

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket

object HelperProtocol {
    const val HOST = "127.0.0.1"
    const val PORT = 49323
}

data class HelperReply(val command: String, val lines: List<String>, val error: Throwable? = null) {
    val ok: Boolean get() = error == null && lines.isNotEmpty() && lines.none { it.startsWith("ERROR") }
    val displayText: String get() = error?.let { "ERROR ${it.javaClass.simpleName}: ${it.message}" } ?: lines.joinToString("\n").ifBlank { "No reply" }
}

data class ExportedGesture(val payloadHex: String, val eventCount: Int, val durationMs: Long, val helperMode: String)

fun HelperReply.toExportedGesture(): ExportedGesture? {
    val line = lines.firstOrNull { it.startsWith("GESTURE ") } ?: return null
    fun value(key: String): String? = Regex("""(?:^|\s)$key=([^\s]+)""").find(line)?.groupValues?.getOrNull(1)
    return ExportedGesture(
        payloadHex = value("payload") ?: return null,
        eventCount = value("count")?.toIntOrNull() ?: 0,
        durationMs = value("durationMs")?.toLongOrNull() ?: 0L,
        helperMode = value("mode") ?: "raw-evdev",
    )
}

class HelperClient(private val host: String = HelperProtocol.HOST, private val port: Int = HelperProtocol.PORT) {
    suspend fun playConcurrent(
        holdHex: String,
        throwHex: String,
        throwOffsetMs: Int = FastCatchPreset.THROW_OFFSET_MS,
        holdAfterThrowMs: Int = FastCatchPreset.HOLD_AFTER_THROW_MS,
        timeoutMs: Int = 45_000,
    ): HelperReply = withContext(Dispatchers.IO) {
        val importThrow = send("IMPORT_GESTURE $throwHex", timeoutMs = timeoutMs)
        if (!importThrow.ok) return@withContext importThrow
        val importHold = send("IMPORT_HOLD_GESTURE $holdHex", timeoutMs = timeoutMs)
        if (!importHold.ok) return@withContext importHold
        send("PLAY_CONCURRENT_LAST $throwOffsetMs $holdAfterThrowMs", timeoutMs = timeoutMs)
    }

    suspend fun send(command: String, timeoutMs: Int = 30_000): HelperReply = withContext(Dispatchers.IO) {
        val commandLabel = commandLabel(command)
        ThrowletLog.i("helper <= $commandLabel timeoutMs=$timeoutMs")
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 2_000)
                socket.soTimeout = timeoutMs
                PrintWriter(socket.getOutputStream(), true).use { writer ->
                    BufferedReader(InputStreamReader(socket.getInputStream())).use { reader ->
                        writer.println(command)
                        val lines = mutableListOf<String>()
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line == "END") break
                            lines += line
                        }
                        ThrowletLog.i("helper => $commandLabel reply=${lines.joinToString(" | ").take(500)}")
                        HelperReply(commandLabel, lines)
                    }
                }
            }
        } catch (t: Throwable) {
            ThrowletLog.e("helper command failed command=$commandLabel", t)
            HelperReply(commandLabel, emptyList(), t)
        }
    }

    private fun commandLabel(command: String): String = when {
        command.startsWith("IMPORT_GESTURE ") -> "IMPORT_GESTURE payloadChars=${command.length - "IMPORT_GESTURE ".length}"
        command.startsWith("IMPORT_HOLD_GESTURE ") -> "IMPORT_HOLD_GESTURE payloadChars=${command.length - "IMPORT_HOLD_GESTURE ".length}"
        command.startsWith("PLAY_CONCURRENT ") -> "PLAY_CONCURRENT payloadChars=${command.length - "PLAY_CONCURRENT ".length}"
        else -> command
    }
}
