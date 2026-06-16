/*
 * Copyright (C) 2026
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.buzbuz.smartautoclicker.core.common.actions.precision

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrecisionGestureExecutor @Inject constructor(
    private val setup: PrecisionGestureHelperSetup,
    private val client: PrecisionGestureHelperClient,
) {

    suspend fun recordOnce(): PrecisionGesturePayload {
        setup.ensureStarted().throwIfNotRunning()
        client.recordOnce().requireSuccess()
        return client.exportLast()
    }

    suspend fun play(payloadHex: String): PrecisionGesturePlayResult {
        setup.ensureStarted().throwIfNotRunning()
        client.importGesture(payloadHex)
        return client.playLast()
    }
}

fun PrecisionGestureSetupResult.throwIfNotRunning() {
    when (this) {
        is PrecisionGestureSetupResult.Running -> Unit
        PrecisionGestureSetupResult.UnsupportedAbi -> throw IllegalStateException("Precision gestures require arm64-v8a on this build.")
        PrecisionGestureSetupResult.ShizukuUnavailable -> throw IllegalStateException("Shizuku is not running. Start Shizuku, then try again.")
        PrecisionGestureSetupResult.PermissionDenied -> throw IllegalStateException("Shizuku permission is required for precision gestures.")
        is PrecisionGestureSetupResult.NotStarted -> throw IllegalStateException("Precision gesture helper is not running.", error)
        is PrecisionGestureSetupResult.StartFailed -> throw IllegalStateException("Precision gesture helper failed to start: ${error.message ?: error.javaClass.simpleName}", error)
    }
}
