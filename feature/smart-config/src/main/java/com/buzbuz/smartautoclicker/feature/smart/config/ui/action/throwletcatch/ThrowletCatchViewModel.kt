/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.feature.smart.config.ui.action.throwletcatch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buzbuz.smartautoclicker.core.domain.model.action.ThrowletCatch
import com.buzbuz.smartautoclicker.feature.smart.config.domain.EditionRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import javax.inject.Inject

class ThrowletCatchViewModel @Inject constructor(
    private val editionRepository: EditionRepository,
) : ViewModel() {

    private val configuredThrowletCatch = editionRepository.editionState.editedActionState
        .mapNotNull { action -> action.value }
        .filterIsInstance<ThrowletCatch>()

    private val editedActionHasChanged: StateFlow<Boolean> =
        editionRepository.editionState.editedActionState
            .map { it.hasChanged }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    @OptIn(FlowPreview::class)
    val isEditingAction: Flow<Boolean> = editionRepository.isEditingAction
        .distinctUntilChanged()
        .debounce(1000)

    val name: Flow<String?> = configuredThrowletCatch
        .map { it.name }
        .take(1)

    val nameError: Flow<Boolean> = configuredThrowletCatch.map { it.name?.isEmpty() ?: true }

    val operationItem: Flow<ThrowletCatchOperationItem> = configuredThrowletCatch
        .map { it.operation.toOperationItem() }

    val modeItem: Flow<ThrowletCatchModeItem> = configuredThrowletCatch
        .map { it.mode.toModeItem() }

    val laneItem: Flow<ThrowletCatchLaneItem> = configuredThrowletCatch
        .map { it.lane.toLaneItem() }

    val pokemonNameOverride: Flow<String?> = configuredThrowletCatch
        .map { it.pokemonNameOverride.orEmpty() }
        .take(1)

    val isValidAction: Flow<Boolean> = editionRepository.editionState.editedActionState
        .map { it.canBeSaved }

    fun hasUnsavedModifications(): Boolean =
        editedActionHasChanged.value

    fun setName(name: String) {
        editionRepository.editionState.getEditedAction<ThrowletCatch>()?.let { throwletCatch ->
            editionRepository.updateEditedAction(throwletCatch.copy(name = "" + name))
        }
    }

    fun setOperation(operationItem: ThrowletCatchOperationItem) {
        editionRepository.editionState.getEditedAction<ThrowletCatch>()?.let { throwletCatch ->
            editionRepository.updateEditedAction(
                throwletCatch.copy(operation = operationItem.toThrowletCatchOperation())
            )
        }
    }

    fun setMode(modeItem: ThrowletCatchModeItem) {
        editionRepository.editionState.getEditedAction<ThrowletCatch>()?.let { throwletCatch ->
            editionRepository.updateEditedAction(
                throwletCatch.copy(mode = modeItem.toThrowletCatchMode())
            )
        }
    }

    fun setLane(laneItem: ThrowletCatchLaneItem) {
        editionRepository.editionState.getEditedAction<ThrowletCatch>()?.let { throwletCatch ->
            editionRepository.updateEditedAction(
                throwletCatch.copy(lane = laneItem.toThrowletCatchLane())
            )
        }
    }

    fun setPokemonNameOverride(name: String) {
        editionRepository.editionState.getEditedAction<ThrowletCatch>()?.let { throwletCatch ->
            editionRepository.updateEditedAction(
                throwletCatch.copy(pokemonNameOverride = name.trim().takeIf { it.isNotBlank() })
            )
        }
    }
}
