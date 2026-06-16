/*
 * Copyright (C) 2026 Nicolas Espinoza
 *
 * Localhost TCP server exposing MediaProjection frames to Throwlet while recording.
 */
package com.buzbuz.smartautoclicker.core.display.recorder

import android.graphics.Bitmap
import android.util.Log
import com.buzbuz.smartautoclicker.core.base.di.Dispatcher
import com.buzbuz.smartautoclicker.core.base.di.HiltCoroutineDispatchers.IO
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScreenFrameBroker @Inject constructor(
    private val displayRecorder: DisplayRecorder,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) {
    private val recording = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private var acceptJob: Job? = null
    @Volatile private var serverSocket: ServerSocket? = null

    fun start() {
        if (!recording.compareAndSet(false, true)) return
        Log.i(TAG, "start broker ${FrameBrokerProtocol.HOST}:${FrameBrokerProtocol.PORT}")
        acceptJob = scope.launch {
            runCatching { runAcceptLoop() }
                .onFailure { error -> Log.e(TAG, "broker accept loop failed", error) }
                .also { shutdownServerSocket() }
        }
    }

    fun stop() {
        if (!recording.compareAndSet(true, false)) return
        Log.i(TAG, "stop broker")
        acceptJob?.cancel()
        acceptJob = null
        shutdownServerSocket()
    }

    private fun runAcceptLoop() {
        ServerSocket().use { server ->
            server.reuseAddress = true
            server.bind(InetSocketAddress(FrameBrokerProtocol.HOST, FrameBrokerProtocol.PORT))
            serverSocket = server
            while (recording.get()) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                scope.launch { handleClient(client) }
            }
        }
    }

    private fun shutdownServerSocket() {
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    private fun handleClient(socket: Socket) {
        socket.soTimeout = CLIENT_TIMEOUT_MS
        runCatching {
            socket.use { client ->
                val input = client.getInputStream()
                val output = client.getOutputStream()
                val command = input.readAsciiLine()?.trim().orEmpty()
                if (command.isEmpty()) return@use
                when {
                    command == "STATUS" || command == statusCommand() -> handleStatus(output)
                    command == frameCommand() -> handleFrame(output)
                    command.startsWith("FRAME") -> writeError(output, "ERROR BAD_TOKEN")
                    else -> writeError(output, "ERROR UNKNOWN_COMMAND")
                }
            }
        }.onFailure { error ->
            Log.w(TAG, "client handling failed: ${error.message}")
        }
    }

    private fun handleStatus(output: OutputStream) {
        val line = if (recording.get()) "OK RECORDING" else "ERROR NOT_RECORDING"
        output.writeLine(line)
        output.writeLine("END")
    }

    private fun handleFrame(output: OutputStream) {
        if (!recording.get()) {
            writeError(output, "ERROR NOT_RECORDING")
            return
        }
        val bitmap = runBlocking { displayRecorder.acquireLatestBitmap() }
        if (bitmap == null) {
            writeError(output, "ERROR NO_FRAME")
            return
        }
        val jpeg = ByteArrayOutputStream()
        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, jpeg)) {
            writeError(output, "ERROR ENCODE_FAILED")
            return
        }
        val bytes = jpeg.toByteArray()
        output.writeLine(
            "OK width=${bitmap.width} height=${bitmap.height} format=jpeg len=${bytes.size}",
        )
        output.write(bytes)
        output.writeLine("END")
        Log.d(TAG, "frame served ${bitmap.width}x${bitmap.height} bytes=${bytes.size}")
    }

    private fun writeError(output: OutputStream, message: String) {
        output.writeLine(message)
        output.writeLine("END")
    }

    private fun statusCommand(): String = "STATUS token=${FrameBrokerProtocol.TOKEN}"

    private fun frameCommand(): String = "FRAME token=${FrameBrokerProtocol.TOKEN}"

    private fun OutputStream.writeLine(line: String) {
        write((line + "\n").toByteArray(Charsets.US_ASCII))
        flush()
    }

    private fun InputStream.readAsciiLine(): String? {
        val buffer = StringBuilder()
        while (true) {
            val byte = read()
            if (byte == -1) return if (buffer.isEmpty()) null else buffer.toString()
            if (byte == '\n'.code) break
            if (byte != '\r'.code) buffer.append(byte.toChar())
        }
        return buffer.toString()
    }

    companion object {
        private const val TAG = "ScreenFrameBroker"
        private const val CLIENT_TIMEOUT_MS = 5_000
        private const val JPEG_QUALITY = 80
    }
}
