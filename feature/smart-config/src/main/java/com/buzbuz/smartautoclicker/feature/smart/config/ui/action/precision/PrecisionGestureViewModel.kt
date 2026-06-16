package com.buzbuz.smartautoclicker.feature.smart.config.ui.action.precision

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buzbuz.smartautoclicker.core.common.actions.precision.PrecisionGestureExecutor
import com.buzbuz.smartautoclicker.core.domain.model.action.PrecisionGesture
import com.buzbuz.smartautoclicker.feature.smart.config.domain.EditionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

class PrecisionGestureViewModel @Inject constructor(
    private val editionRepository: EditionRepository,
    private val precisionGestureExecutor: PrecisionGestureExecutor,
) : ViewModel() {

    private val configuredGesture: Flow<PrecisionGesture> = editionRepository.editionState.editedActionState
        .mapNotNull { it.value }
        .filterIsInstance<PrecisionGesture>()

    val gesture: StateFlow<PrecisionGesture?> = configuredGesture
        .stateIn(viewModelScope, SharingStarted.Eagerly, editionRepository.editionState.getEditedAction())

    val hasUnsavedModifications: StateFlow<Boolean> = editionRepository.editionState.editedActionState
        .map { it.hasChanged }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setName(name: String) {
        editionRepository.editionState.getEditedAction<PrecisionGesture>()?.let { action ->
            editionRepository.updateEditedAction(action.copy(name = name))
        }
    }

    suspend fun record(): Result<PrecisionGesture> = runCatching {
        val payload = precisionGestureExecutor.recordOnce()
        val updated = editionRepository.editionState.getEditedAction<PrecisionGesture>()!!.copy(
            payloadHex = payload.payloadHex,
            eventCount = payload.eventCount,
            durationMs = payload.durationMs,
            helperMode = payload.helperMode,
        )
        editionRepository.updateEditedAction(updated)
        updated
    }

    suspend fun play(): Result<Unit> = runCatching {
        val payload = editionRepository.editionState.getEditedAction<PrecisionGesture>()?.payloadHex
            ?: error("Record a precision gesture first")
        precisionGestureExecutor.play(payload)
    }

    fun clear() {
        editionRepository.editionState.getEditedAction<PrecisionGesture>()?.let { action ->
            editionRepository.updateEditedAction(
                action.copy(
                    payloadHex = null,
                    eventCount = null,
                    durationMs = null,
                    helperMode = null,
                )
            )
        }
    }
}
