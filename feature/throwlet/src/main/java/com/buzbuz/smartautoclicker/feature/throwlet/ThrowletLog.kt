package com.buzbuz.smartautoclicker.feature.throwlet

import android.util.Log

object ThrowletLog {
    const val TAG = "Throwlet"
    fun d(message: String) = safe { Log.d(TAG, message) }
    fun i(message: String) = safe { Log.i(TAG, message) }
    fun w(message: String, t: Throwable? = null) = safe { Log.w(TAG, message, t) }
    fun e(message: String, t: Throwable? = null) = safe { Log.e(TAG, message, t) }

    private inline fun safe(block: () -> Unit) {
        try {
            block()
        } catch (_: Throwable) {
            // android.util.Log is not mocked in local JVM unit tests.
        }
    }
}
