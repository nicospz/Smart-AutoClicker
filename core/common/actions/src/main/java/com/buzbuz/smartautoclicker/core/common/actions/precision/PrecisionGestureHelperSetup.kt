/*
 * Copyright (C) 2026
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.buzbuz.smartautoclicker.core.common.actions.precision

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess
import java.util.concurrent.TimeUnit
import kotlin.math.min
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrecisionGestureHelperSetup @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val helperClient: PrecisionGestureHelperClient,
) {

    suspend fun getStatus(): PrecisionGestureSetupResult = withContext(Dispatchers.IO) {
        queryRunningHelperStatus()?.let { status ->
            return@withContext PrecisionGestureSetupResult.Running(status)
        }

        when (val shizukuResult = ensureShizukuReady(requireSupportedAbi = true, requestPermission = false)) {
            is PrecisionGestureSetupResult.Running -> PrecisionGestureSetupResult.NotStarted()
            else -> shizukuResult
        }
    }

    suspend fun ensureStarted(): PrecisionGestureSetupResult = withContext(Dispatchers.IO) {
        queryRunningHelperStatus()?.let { status ->
            return@withContext PrecisionGestureSetupResult.Running(status)
        }

        when (val shizukuResult = ensureShizukuReady(requireSupportedAbi = true, requestPermission = true)) {
            is PrecisionGestureSetupResult.Running -> Unit
            else -> return@withContext shizukuResult
        }

        runCatching {
            val helperBytes = context.assets.open(HELPER_ASSET_PATH).use { it.readBytes() }
            writeHelper(helperBytes, timeoutMs = 30_000)
            runShizukuShell("chmod 755 $HELPER_REMOTE_PATH", timeoutMs = 10_000)
            runShizukuShell("for pid in \$(pidof $HELPER_PROCESS_NAME 2>/dev/null); do kill \$pid; done 2>/dev/null || true", timeoutMs = 10_000)
            runShizukuShell("rm -f $HELPER_LOG_PATH", timeoutMs = 10_000)
            val deviceArg = detectTouchDevicePath()?.let { path -> "--device '$path' " }.orEmpty()
            runShizukuShell(
                "nohup $HELPER_REMOTE_PATH ${deviceArg}--port ${PrecisionGestureHelperClient.HELPER_PORT} > $HELPER_LOG_PATH 2>&1 &",
                timeoutMs = 10_000,
            )
            waitForHelper()
        }.fold(
            onSuccess = { PrecisionGestureSetupResult.Running(it) },
            onFailure = { PrecisionGestureSetupResult.StartFailed(it) },
        )
    }

    suspend fun stopHelper(): PrecisionGestureSetupResult = withContext(Dispatchers.IO) {
        if (queryRunningHelperStatus() == null) {
            return@withContext PrecisionGestureSetupResult.NotStarted()
        }

        runCatching { helperClient.stop() }

        when (ensureShizukuReady(requireSupportedAbi = false, requestPermission = false)) {
            is PrecisionGestureSetupResult.Running -> {
                runCatching {
                    runShizukuShell(
                        "for pid in \$(pidof $HELPER_PROCESS_NAME 2>/dev/null); do kill \$pid; done 2>/dev/null || true",
                        timeoutMs = 10_000,
                    )
                }
            }
            else -> Unit
        }

        repeat(10) {
            if (queryRunningHelperStatus() == null) {
                return@withContext PrecisionGestureSetupResult.NotStarted()
            }
            Thread.sleep(250)
        }

        val status = queryRunningHelperStatus()
        PrecisionGestureSetupResult.StopFailed(
            IllegalStateException(status?.let { "helper still running: $it" } ?: "helper still running"),
        )
    }

    suspend fun ensureShizukuReady(
        requireSupportedAbi: Boolean = false,
        requestPermission: Boolean = true,
    ): PrecisionGestureSetupResult = withContext(Dispatchers.IO) {
        if (requireSupportedAbi && !Build.SUPPORTED_ABIS.contains(SUPPORTED_ABI)) {
            return@withContext PrecisionGestureSetupResult.UnsupportedAbi
        }
        if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
            return@withContext PrecisionGestureSetupResult.ShizukuUnavailable
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            if (!requestPermission) {
                return@withContext PrecisionGestureSetupResult.PermissionDenied
            }
            runCatching { Shizuku.requestPermission(REQUEST_CODE) }
            if (!waitForShizukuPermission()) {
                return@withContext PrecisionGestureSetupResult.PermissionDenied
            }
        }

        PrecisionGestureSetupResult.Running("shizuku")
    }

    private suspend fun queryRunningHelperStatus(): String? {
        val reply = runCatching { helperClient.status() }.getOrNull()
            ?: return null

        if (reply.error != null || reply.lines.isEmpty()) return null
        return reply.lines.first().takeUnless { it.startsWith("ERROR ") }
    }

    private suspend fun waitForHelper(): String {
        var lastError: Throwable? = null
        repeat(10) {
            val reply = runCatching { helperClient.status() }
            if (reply.isSuccess && reply.getOrThrow().error == null && reply.getOrThrow().lines.isNotEmpty()) {
                return reply.getOrThrow().lines.first()
            }
            lastError = reply.exceptionOrNull() ?: reply.getOrNull()?.error
            Thread.sleep(250)
        }
        val log = runCatching { runShizukuShell("tail -n 20 $HELPER_LOG_PATH 2>/dev/null || true", timeoutMs = 5_000) }
            .getOrDefault("")
            .trim()
        throw IllegalStateException(
            buildString {
                append("helper did not reply")
                lastError?.let { append("; client error: ").append(it.javaClass.simpleName).append(": ").append(it.message) }
                if (log.isNotBlank()) append("; helper log: ").append(log.take(500))
            },
            lastError,
        )
    }

    private fun detectTouchDevicePath(): String? {
        val command = """
            getevent -lp 2>/dev/null | awk '
              /add device/ { path=${'$'}4 }
              /name:/ {
                if (${'$'}0 ~ /sec_touchscreen|touchscreen|Touchscreen|touch_screen/) { print path; exit }
              }
            '
        """.trimIndent()

        return runCatching { runShizukuShell(command, timeoutMs = 10_000).lineSequence().firstOrNull()?.trim() }
            .getOrNull()
            ?.takeIf { it.startsWith("/dev/input/event") }
    }

    private fun waitForShizukuPermission(): Boolean {
        repeat(20) { attempt ->
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) return true
            Thread.sleep(min(250L + attempt * 50L, 750L))
        }
        return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }

    private fun writeHelper(bytes: ByteArray, timeoutMs: Long) {
        val result = runProcess(
            command = arrayOf("sh", "-c", "cat > $HELPER_REMOTE_PATH.tmp && mv $HELPER_REMOTE_PATH.tmp $HELPER_REMOTE_PATH"),
            stdin = bytes,
            timeoutMs = timeoutMs,
        )
        if (result.exitCode != 0) {
            error("Shizuku helper copy failed (${result.exitCode}): ${result.stderr.ifBlank { result.stdout.trim() }}")
        }
    }

    fun runShizukuShell(command: String, timeoutMs: Long): String {
        val result = runProcess(arrayOf("sh", "-c", command), null, timeoutMs)
        if (result.exitCode != 0) {
            error("Shizuku shell failed (${result.exitCode}): ${result.stderr.ifBlank { result.stdout.trim() }}")
        }
        return result.stdout
    }

    private fun runProcess(command: Array<String>, stdin: ByteArray?, timeoutMs: Long): ShellResult {
        val process = newShizukuProcess(command, null, null)
        val stdout = StringBuilder()
        val stderr = StringBuilder()
        val stdoutThread = Thread {
            runCatching { process.inputStream.bufferedReader().forEachLine { stdout.append(it).append('\n') } }
        }
        val stderrThread = Thread {
            runCatching { process.errorStream.bufferedReader().forEachLine { stderr.append(it).append('\n') } }
        }
        stdoutThread.start()
        stderrThread.start()
        stdin?.let { bytes ->
            process.outputStream.use { output ->
                output.write(bytes)
                output.flush()
            }
        }
        val exitCode = waitForProcess(process, timeoutMs)
        stdoutThread.join(2_000)
        stderrThread.join(2_000)
        return ShellResult(exitCode, stdout.toString(), stderr.toString().trim())
    }

    private fun newShizukuProcess(command: Array<String>, env: Array<String>?, directory: String?): Process {
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java,
        )
        method.isAccessible = true
        return method.invoke(null, command, env, directory) as Process
    }

    private fun waitForProcess(process: Process, timeoutMs: Long): Int {
        if (process is ShizukuRemoteProcess) {
            if (!process.waitForTimeout(timeoutMs, TimeUnit.MILLISECONDS)) {
                process.destroy()
                error("Shizuku shell command timed out")
            }
            return process.waitFor()
        }
        if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly()
            error("Shizuku shell command timed out")
        }
        return process.exitValue()
    }

    private data class ShellResult(val exitCode: Int, val stdout: String, val stderr: String)

    companion object {
        private const val REQUEST_CODE = 49323
        private const val SUPPORTED_ABI = "arm64-v8a"
        private const val HELPER_ASSET_PATH = "helper/arm64-v8a/gesture-helper"
        private const val HELPER_REMOTE_PATH = "/data/local/tmp/sac-gesture-helper"
        private const val HELPER_LOG_PATH = "/data/local/tmp/sac-gesture-helper.log"
        private const val HELPER_PROCESS_NAME = "sac-gesture-helper"
    }
}

sealed class PrecisionGestureSetupResult {
    data class Running(val status: String) : PrecisionGestureSetupResult()
    data object UnsupportedAbi : PrecisionGestureSetupResult()
    data object ShizukuUnavailable : PrecisionGestureSetupResult()
    data object PermissionDenied : PrecisionGestureSetupResult()
    data class NotStarted(val error: Throwable? = null) : PrecisionGestureSetupResult()
    data class StartFailed(val error: Throwable) : PrecisionGestureSetupResult()
    data class StopFailed(val error: Throwable) : PrecisionGestureSetupResult()
}
