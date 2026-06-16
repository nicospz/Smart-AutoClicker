package com.buzbuz.smartautoclicker.feature.dumb.config.ui.actions.precision

import androidx.lifecycle.ViewModel
import com.buzbuz.smartautoclicker.core.common.actions.precision.PrecisionGestureExecutor
import com.buzbuz.smartautoclicker.core.dumb.domain.model.DumbAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class DumbPrecisionGestureViewModel @Inject constructor(
    private val precisionGestureExecutor: PrecisionGestureExecutor,
) : ViewModel() {

    private val _gesture = MutableStateFlow<DumbAction.DumbPrecisionGesture?>(null)
    val gesture: StateFlow<DumbAction.DumbPrecisionGesture?> = _gesture

    fun setEditedGesture(gesture: DumbAction.DumbPrecisionGesture) {
        _gesture.value = gesture
    }

    fun setName(name: String) {
        _gesture.value = _gesture.value?.copy(name = name)
    }

    fun setRepeatCount(repeatCount: Int) {
        _gesture.value = _gesture.value?.copy(repeatCount = repeatCount.coerceAtLeast(1))
    }

    fun setRepeatDelay(repeatDelayMs: Long) {
        _gesture.value = _gesture.value?.copy(repeatDelayMs = repeatDelayMs.coerceAtLeast(0))
    }

    fun clear() {
        _gesture.value = _gesture.value?.copy(
            payloadHex = null,
            eventCount = null,
            durationMs = null,
            helperMode = null,
        )
    }

    suspend fun record(): Result<Unit> = runCatching {
        val payload = precisionGestureExecutor.recordOnce()
        _gesture.value = _gesture.value?.copy(
            payloadHex = payload.payloadHex,
            eventCount = payload.eventCount,
            durationMs = payload.durationMs,
            helperMode = payload.helperMode,
        )
    }

    suspend fun play(): Result<Unit> = runCatching {
        val payload = _gesture.value?.payloadHex ?: error("Record a precision gesture first")
        precisionGestureExecutor.play(payload)
    }
}
