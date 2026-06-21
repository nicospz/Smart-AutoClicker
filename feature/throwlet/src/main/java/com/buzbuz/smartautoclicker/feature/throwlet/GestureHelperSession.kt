package com.buzbuz.smartautoclicker.feature.throwlet

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes IMPORT/PLAY/EXPORT against the single shared gesture-helper process. */
object GestureHelperSession {
    private val mutex = Mutex()

    suspend fun <T> runExclusive(block: suspend () -> T): T = mutex.withLock { block() }
}
