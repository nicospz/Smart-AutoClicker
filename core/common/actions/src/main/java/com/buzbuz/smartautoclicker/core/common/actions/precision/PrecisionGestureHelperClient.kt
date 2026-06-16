/*
 * Copyright (C) 2026
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.buzbuz.smartautoclicker.core.common.actions.precision

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrecisionGestureHelperClient @Inject constructor() {

    suspend fun status(): PrecisionGestureHelperReply = send(COMMAND_STATUS, timeoutMs = 5_000)
    suspend fun recordOnce(): PrecisionGestureHelperReply = send(COMMAND_RECORD_ONCE, timeoutMs = 30_000)
    suspend fun exportLast(): PrecisionGesturePayload = send(COMMAND_EXPORT_LAST, timeoutMs = 10_000).toPayload()
    suspend fun importGesture(payloadHex: String): PrecisionGesturePayload =
        send("$COMMAND_IMPORT_GESTURE $payloadHex", timeoutMs = 10_000).toImportedPayload(payloadHex)
    suspend fun playLast(): PrecisionGesturePlayResult = send(COMMAND_PLAY_LAST, timeoutMs = 30_000).toPlayResult()
    suspend fun stop(): PrecisionGestureHelperReply = send(COMMAND_STOP, timeoutMs = 5_000)

    suspend fun send(command: String, timeoutMs: Int): PrecisionGestureHelperReply = withContext(Dispatchers.IO) {
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(HELPER_HOST, HELPER_PORT), 2_000)
                socket.soTimeout = timeoutMs

                val writer = PrintWriter(socket.getOutputStream(), true)
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

                Log.i(TAG, "precision helper <= ${command.toLogSummary()}")
                writer.println(command)

                val lines = buildList {
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line == "END") break
                        add(line)
                    }
                }

                lines.forEach { Log.i(TAG, "precision helper => ${it.toLogSummary()}") }
                PrecisionGestureHelperReply(command, lines)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "precision helper command failed: ${command.toLogSummary()}", t)
            PrecisionGestureHelperReply(command, emptyList(), t)
        }
    }

    companion object {
        const val HELPER_HOST = "127.0.0.1"
        const val HELPER_PORT = 49323
    }
}

private fun String.toLogSummary(): String {
    if (length <= MAX_LOGGED_HELPER_LINE_LENGTH && !contains("payload=")) return this

    val command = substringBefore(' ')
    val count = value("count")
    val durationMs = value("durationMs")
    val payloadLength = value("payload")?.length

    return buildString {
        append(command)
        append(" len=").append(length)
        payloadLength?.let { append(" payloadLen=").append(it) }
        count?.let { append(" count=").append(it) }
        durationMs?.let { append(" durationMs=").append(it) }
    }
}

data class PrecisionGestureHelperReply(
    val command: String,
    val lines: List<String>,
    val error: Throwable? = null,
) {
    fun requireSuccess(): PrecisionGestureHelperReply {
        error?.let { throw it }
        lines.firstOrNull { it.startsWith("ERROR ") }?.let { throw IllegalStateException(it) }
        if (lines.isEmpty()) throw IllegalStateException("No reply from precision gesture helper")
        return this
    }
}

data class PrecisionGesturePlayResult(
    val mode: String,
    val eventCount: Int,
    val durationMs: Long,
)

fun PrecisionGestureHelperReply.toPayload(): PrecisionGesturePayload {
    val line = requireSuccess().lines.firstOrNull { it.startsWith("GESTURE ") }
        ?: throw IllegalStateException("No exported gesture in helper reply")

    return PrecisionGesturePayload(
        payloadHex = line.value("payload") ?: throw IllegalStateException("Missing gesture payload"),
        eventCount = line.value("count")?.toIntOrNull() ?: 0,
        durationMs = line.value("durationMs")?.toLongOrNull() ?: 0L,
        helperMode = PRECISION_GESTURE_HELPER_MODE,
    ).also {
        require(it.isValid()) { "Invalid precision gesture payload" }
    }
}

fun PrecisionGestureHelperReply.toImportedPayload(payloadHex: String): PrecisionGesturePayload {
    val line = requireSuccess().lines.firstOrNull { it.startsWith("IMPORTED ") }
        ?: throw IllegalStateException("Gesture import failed")

    return PrecisionGesturePayload(
        payloadHex = payloadHex,
        eventCount = line.value("count")?.toIntOrNull() ?: 0,
        durationMs = line.value("durationMs")?.toLongOrNull() ?: 0L,
        helperMode = PRECISION_GESTURE_HELPER_MODE,
    )
}

fun PrecisionGestureHelperReply.toPlayResult(): PrecisionGesturePlayResult {
    val line = requireSuccess().lines.firstOrNull { it.startsWith("PLAYED ") }
        ?: throw IllegalStateException("Gesture playback failed")

    return PrecisionGesturePlayResult(
        mode = line.value("mode") ?: PRECISION_GESTURE_HELPER_MODE,
        eventCount = line.value("count")?.toIntOrNull() ?: 0,
        durationMs = line.value("durationMs")?.toLongOrNull() ?: 0L,
    )
}

private fun String.value(key: String): String? =
    Regex("""(?:^|\s)$key=([^\s]+)""").find(this)?.groupValues?.getOrNull(1)

private const val COMMAND_STATUS = "STATUS"
private const val COMMAND_RECORD_ONCE = "RECORD_ONCE"
private const val COMMAND_EXPORT_LAST = "EXPORT_LAST"
private const val COMMAND_IMPORT_GESTURE = "IMPORT_GESTURE"
private const val COMMAND_PLAY_LAST = "PLAY_LAST"
private const val COMMAND_STOP = "STOP"
private const val TAG = "PrecisionGestureHelper"
private const val MAX_LOGGED_HELPER_LINE_LENGTH = 512
