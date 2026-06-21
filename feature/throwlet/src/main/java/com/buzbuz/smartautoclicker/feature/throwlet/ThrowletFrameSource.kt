/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.feature.throwlet

import android.graphics.Bitmap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Cached screen-frame access for needle automation (replaces Throwlet's SAC TCP client). */
class ThrowletFrameSource(
    private val screenshotSource: ThrowletScreenshotSource,
    private val isRecording: () -> Boolean,
) {
    private val screenshotLock = Any()
    @Volatile private var cachedScreenshot: Bitmap? = null
    @Volatile private var cachedAtMs: Long = 0
    @Volatile private var inFlightLatch: CountDownLatch? = null

    fun isRecording(): Boolean = isRecording.invoke()

    fun screenshotBlocking(timeoutMs: Long = 2_500, maxCacheAgeMs: Long = CACHE_TTL_MS): Bitmap? {
        var latchToAwait: CountDownLatch? = null
        synchronized(screenshotLock) {
            val now = System.currentTimeMillis()
            cachedScreenshot?.let { cached ->
                if (!cached.isRecycled && maxCacheAgeMs > 0 && now - cachedAtMs <= maxCacheAgeMs) {
                    return cached.copy(Bitmap.Config.ARGB_8888, false)
                }
            }
            latchToAwait = inFlightLatch
            if (latchToAwait == null) {
                inFlightLatch = CountDownLatch(1)
            }
        }

        if (latchToAwait != null) {
            val completed = latchToAwait.await(timeoutMs, TimeUnit.MILLISECONDS)
            if (!completed) ThrowletLog.w("frame in-flight wait timeout after ${timeoutMs}ms")
            synchronized(screenshotLock) {
                val cached = cachedScreenshot
                val now = System.currentTimeMillis()
                if (cached != null && !cached.isRecycled && (maxCacheAgeMs <= 0 || now - cachedAtMs <= maxCacheAgeMs)) {
                    return cached.copy(Bitmap.Config.ARGB_8888, false)
                }
            }
            return null
        }

        val bitmap = screenshotSource.captureBlocking()
        synchronized(screenshotLock) {
            inFlightLatch?.countDown()
            inFlightLatch = null
            if (bitmap != null) {
                cachedScreenshot?.recycle()
                cachedScreenshot = bitmap
                cachedAtMs = System.currentTimeMillis()
                return bitmap.copy(Bitmap.Config.ARGB_8888, false)
            }
        }
        return null
    }

    companion object {
        private const val CACHE_TTL_MS = 750L
    }
}
