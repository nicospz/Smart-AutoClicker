package com.buzbuz.smartautoclicker.feature.dumb.config.ui.actions.precision

import androidx.lifecycle.ViewModel
import com.buzbuz.smartautoclicker.core.common.actions.precision.PrecisionTextExecutor
import com.buzbuz.smartautoclicker.core.common.actions.precision.PrecisionTextMode
import com.buzbuz.smartautoclicker.core.dumb.domain.model.DumbAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class DumbPrecisionTextViewModel @Inject constructor(
    private val precisionTextExecutor: PrecisionTextExecutor,
) : ViewModel() {

    private val _action = MutableStateFlow<DumbAction.DumbPrecisionText?>(null)
    val action: StateFlow<DumbAction.DumbPrecisionText?> = _action

    fun setEditedAction(action: DumbAction.DumbPrecisionText) { _action.value = action }
    fun setName(name: String) { _action.value = _action.value?.copy(name = name) }
    fun setText(text: String) { _action.value = _action.value?.copy(text = text) }
    fun setMode(mode: PrecisionTextMode) { _action.value = _action.value?.copy(mode = mode) }
    fun setRepeatCount(repeatCount: Int) { _action.value = _action.value?.copy(repeatCount = repeatCount.coerceAtLeast(1)) }
    fun setRepeatDelay(repeatDelayMs: Long) { _action.value = _action.value?.copy(repeatDelayMs = repeatDelayMs.coerceAtLeast(0)) }

    suspend fun tryType(): Result<Unit> = runCatching {
        val action = _action.value ?: error("Missing precision text")
        if (action.text.isEmpty()) error("Enter text first")
        precisionTextExecutor.typeText(action.text, action.mode)
    }
}

