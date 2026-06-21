package com.buzbuz.smartautoclicker.feature.smart.config.ui.action.precision

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buzbuz.smartautoclicker.core.common.actions.precision.PrecisionTextExecutor
import com.buzbuz.smartautoclicker.core.common.actions.precision.PrecisionTextMode
import com.buzbuz.smartautoclicker.core.common.actions.precision.isClipboardPaste
import com.buzbuz.smartautoclicker.core.domain.model.action.PrecisionText
import com.buzbuz.smartautoclicker.feature.smart.config.domain.EditionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

class PrecisionTextViewModel @Inject constructor(
    private val editionRepository: EditionRepository,
    private val precisionTextExecutor: PrecisionTextExecutor,
) : ViewModel() {

    private val configuredAction: Flow<PrecisionText> = editionRepository.editionState.editedActionState
        .mapNotNull { it.value }
        .filterIsInstance<PrecisionText>()

    val action: StateFlow<PrecisionText?> = configuredAction
        .stateIn(viewModelScope, SharingStarted.Eagerly, editionRepository.editionState.getEditedAction())

    val hasUnsavedModifications: StateFlow<Boolean> = editionRepository.editionState.editedActionState
        .map { it.hasChanged }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setName(name: String) = update { copy(name = name) }
    fun setText(text: String) = update { copy(text = text) }
    fun setMode(mode: PrecisionTextMode) = update { copy(mode = mode) }
    fun setReplaceExistingText(replaceExistingText: Boolean) = update {
        copy(mode = if (replaceExistingText) PrecisionTextMode.CLIPBOARD_PASTE_REPLACE else PrecisionTextMode.CLIPBOARD_PASTE)
    }

    suspend fun tryType(): Result<Unit> = runCatching {
        val action = editionRepository.editionState.getEditedAction<PrecisionText>() ?: error("Missing precision text")
        if (action.text.isEmpty() && !action.mode.isClipboardPaste()) error("Enter text first")
        precisionTextExecutor.typeText(action.text, action.mode)
    }

    private fun update(block: PrecisionText.() -> PrecisionText) {
        editionRepository.editionState.getEditedAction<PrecisionText>()?.let { action ->
            editionRepository.updateEditedAction(action.block())
        }
    }
}
