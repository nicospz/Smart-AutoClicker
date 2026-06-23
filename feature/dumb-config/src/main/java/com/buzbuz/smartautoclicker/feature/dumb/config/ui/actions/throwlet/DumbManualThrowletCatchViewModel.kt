/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.feature.dumb.config.ui.actions.throwlet

import android.content.Context
import androidx.lifecycle.ViewModel
import com.buzbuz.smartautoclicker.core.dumb.domain.model.DumbAction
import com.buzbuz.smartautoclicker.feature.throwlet.PokemonCatalog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

class DumbManualThrowletCatchViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _action = MutableStateFlow<DumbAction.DumbManualThrowletCatch?>(null)
    val action: StateFlow<DumbAction.DumbManualThrowletCatch?> = _action.asStateFlow()

    val pokemonNames: List<String> by lazy { PokemonCatalog.get(context).allNames() }

    fun setEditedAction(action: DumbAction.DumbManualThrowletCatch) {
        _action.value = action
    }

    fun setName(name: String) {
        _action.value = _action.value?.copy(name = name)
    }

    fun setOperation(item: ManualThrowletOperationItem) {
        _action.value = _action.value?.copy(operation = item.toThrowletCatchOperation())
    }

    fun setLane(item: ManualThrowletLaneItem) {
        _action.value = _action.value?.copy(lane = item.toThrowletCatchLane())
    }

    fun setPokemonNameOverride(name: String) {
        val normalized = PokemonCatalog.get(context)
            .resolveExactName(name)
            ?.name
            ?: name.trim().takeIf { it.isNotBlank() }
        _action.value = _action.value?.copy(pokemonNameOverride = normalized)
    }

    fun clearPokemonNameOverride() {
        _action.value = _action.value?.copy(pokemonNameOverride = null)
    }

    fun hasValidPokemonOverride(): Boolean {
        val name = _action.value?.pokemonNameOverride?.takeIf { it.isNotBlank() } ?: return true
        return PokemonCatalog.get(context).resolveExactName(name) != null
    }
}
