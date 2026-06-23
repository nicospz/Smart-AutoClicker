package com.buzbuz.smartautoclicker.buttons

import android.content.Context
import android.content.SharedPreferences

import com.buzbuz.smartautoclicker.core.dumb.domain.model.DumbScenario

import dagger.hilt.android.qualifiers.ApplicationContext

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

import org.json.JSONArray

@Singleton
class SavedOverlayButtonRepository @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val initialState = readState()

    private val _sets = MutableStateFlow(initialState.sets)
    val sets: StateFlow<List<SavedOverlayButtonSet>> = _sets.asStateFlow()

    private val _buttons = MutableStateFlow(initialState.buttons)
    val buttons: StateFlow<List<SavedOverlayButton>> = _buttons.asStateFlow()

    private val _activeSetSyncId = MutableStateFlow(initialState.activeSetSyncId)
    val activeSetSyncId: StateFlow<String?> = _activeSetSyncId.asStateFlow()

    private val _activeSetUpdatedAtMs = MutableStateFlow(initialState.activeSetUpdatedAtMs)
    val activeSetUpdatedAtMs: StateFlow<Long> = _activeSetUpdatedAtMs.asStateFlow()

    fun createSet(name: String): SavedOverlayButtonSet {
        val now = System.currentTimeMillis()
        val currentSets = _sets.value
        val set = SavedOverlayButtonSet(
            id = nextId(currentSets.map { it.id }),
            syncId = UUID.randomUUID().toString(),
            name = normalizedSetName(name, currentSets),
            priority = currentSets.maxOfOrNull { it.priority }?.plus(1) ?: 0,
            createdAtMs = now,
            updatedAtMs = now,
        )

        replaceState(currentSets + set, _buttons.value, set.syncId, now)
        return set
    }

    fun renameSet(setSyncId: String, name: String) {
        val now = System.currentTimeMillis()
        val normalized = normalizedSetName(name, _sets.value)
        replaceState(
            sets = _sets.value.map { set ->
                if (set.syncId == setSyncId) set.copy(name = normalized, updatedAtMs = now) else set
            },
            buttons = _buttons.value,
            activeSetSyncId = _activeSetSyncId.value,
            activeSetUpdatedAtMs = _activeSetUpdatedAtMs.value,
        )
    }

    fun duplicateSet(sourceSetSyncId: String, newName: String): SavedOverlayButtonSet {
        val now = System.currentTimeMillis()
        val source = _sets.value.firstOrNull { it.syncId == sourceSetSyncId && it.deletedAtMs == null }
            ?: return createSet(newName)
        val currentSets = _sets.value
        val newSet = source.copy(
            id = nextId(currentSets.map { it.id }),
            syncId = UUID.randomUUID().toString(),
            name = normalizedSetName(newName, currentSets),
            priority = currentSets.maxOfOrNull { it.priority }?.plus(1) ?: 0,
            createdAtMs = now,
            updatedAtMs = now,
            deletedAtMs = null,
        )
        var usedButtonIds = _buttons.value.map { it.id }.toSet()
        val copiedButtons = _buttons.value
            .filter { it.deletedAtMs == null && it.setSyncId == sourceSetSyncId }
            .map { button ->
                val newId = nextId(usedButtonIds)
                usedButtonIds = usedButtonIds + newId
                button.copy(
                    id = newId,
                    syncId = UUID.randomUUID().toString(),
                    setSyncId = newSet.syncId,
                    createdAtMs = now,
                    updatedAtMs = now,
                    deletedAtMs = null,
                )
            }

        replaceState(currentSets + newSet, _buttons.value + copiedButtons, newSet.syncId, now)
        return newSet
    }

    fun deleteSet(setSyncId: String) {
        val now = System.currentTimeMillis()
        val deletingActive = _activeSetSyncId.value == setSyncId
        replaceState(
            sets = _sets.value.map { set ->
                if (set.syncId == setSyncId) set.copy(deletedAtMs = now, updatedAtMs = now) else set
            },
            buttons = _buttons.value.map { button ->
                if (button.setSyncId == setSyncId) button.copy(
                    isVisible = false,
                    deletedAtMs = now,
                    updatedAtMs = now,
                ) else button
            },
            activeSetSyncId = if (deletingActive) null else _activeSetSyncId.value,
            activeSetUpdatedAtMs = if (deletingActive) now else _activeSetUpdatedAtMs.value,
        )
    }

    fun setActiveSet(setSyncId: String?) {
        val normalized = setSyncId?.takeIf { candidate ->
            _sets.value.any { it.syncId == candidate && it.deletedAtMs == null }
        }
        if (_activeSetSyncId.value == normalized) return
        replaceState(_sets.value, _buttons.value, normalized, System.currentTimeMillis())
    }

    fun updateSetPosition(setSyncId: String, xPercent: Float, yPercent: Float, isLandscape: Boolean) {
        val now = System.currentTimeMillis()
        replaceState(
            sets = _sets.value.map { set ->
                if (set.syncId != setSyncId) set
                else if (isLandscape) {
                    set.copy(
                        landscapeXPercent = xPercent.coerceIn(0f, 1f),
                        landscapeYPercent = yPercent.coerceIn(0f, 1f),
                        updatedAtMs = now,
                    )
                } else {
                    set.copy(
                        portraitXPercent = xPercent.coerceIn(0f, 1f),
                        portraitYPercent = yPercent.coerceIn(0f, 1f),
                        updatedAtMs = now,
                    )
                }
            },
            buttons = _buttons.value,
            activeSetSyncId = _activeSetSyncId.value,
            activeSetUpdatedAtMs = _activeSetUpdatedAtMs.value,
        )
    }

    fun addButtonForScenario(setSyncId: String, scenario: DumbScenario): SavedOverlayButton {
        val now = System.currentTimeMillis()
        val current = _buttons.value
        val button = SavedOverlayButton(
            id = nextId(current.map { it.id }),
            syncId = UUID.randomUUID().toString(),
            setSyncId = setSyncId,
            labelOverride = null,
            iconGlyph = null,
            scenarioDbId = scenario.id.databaseId,
            scenarioSyncId = scenario.syncId,
            scenarioNameSnapshot = scenario.name,
            enabled = true,
            isVisible = true,
            priority = current.filter { it.setSyncId == setSyncId }.maxOfOrNull { it.priority }?.plus(1) ?: 0,
            createdAtMs = now,
            updatedAtMs = now,
        )

        replaceState(
            sets = _sets.value,
            buttons = current + button,
            activeSetSyncId = _activeSetSyncId.value ?: setSyncId,
            activeSetUpdatedAtMs = if (_activeSetSyncId.value == null) now else _activeSetUpdatedAtMs.value,
        )
        return button
    }

    fun updateButton(button: SavedOverlayButton) {
        val now = System.currentTimeMillis()
        replaceState(
            sets = _sets.value,
            buttons = _buttons.value.map { existing ->
                if (existing.id == button.id) button.copy(updatedAtMs = now) else existing
            },
            activeSetSyncId = _activeSetSyncId.value,
            activeSetUpdatedAtMs = _activeSetUpdatedAtMs.value,
        )
    }

    fun setVisible(buttonId: Long, visible: Boolean) {
        updateById(buttonId) { it.copy(isVisible = visible) }
    }

    fun setEnabled(buttonId: Long, enabled: Boolean) {
        updateById(buttonId) { it.copy(enabled = enabled) }
    }

    fun updateLabel(buttonId: Long, label: String?) {
        updateById(buttonId) { it.copy(labelOverride = label?.trim()?.takeIf(String::isNotEmpty)) }
    }

    fun updateAppearance(buttonId: Long, label: String?, iconGlyph: String?) {
        updateById(buttonId) {
            it.copy(
                labelOverride = label?.trim()?.takeIf(String::isNotEmpty),
                iconGlyph = iconGlyph?.trim()?.takeIf(String::isNotEmpty),
            )
        }
    }

    fun deleteButton(buttonId: Long) {
        val now = System.currentTimeMillis()
        replaceState(
            sets = _sets.value,
            buttons = _buttons.value.map { existing ->
                if (existing.id == buttonId) existing.copy(
                    isVisible = false,
                    deletedAtMs = now,
                    updatedAtMs = now,
                ) else existing
            },
            activeSetSyncId = _activeSetSyncId.value,
            activeSetUpdatedAtMs = _activeSetUpdatedAtMs.value,
        )
    }

    fun replaceAllFromSync(
        sets: List<SavedOverlayButtonSet>,
        activeSetSyncId: String?,
        activeSetUpdatedAtMs: Long,
        buttons: List<SavedOverlayButton>,
    ) {
        val normalized = normalizeState(
            StoredState(
                sets = sets,
                buttons = buttons,
                activeSetSyncId = activeSetSyncId,
                activeSetUpdatedAtMs = activeSetUpdatedAtMs,
            )
        )
        replaceState(
            sets = normalized.sets,
            buttons = normalized.buttons,
            activeSetSyncId = normalized.activeSetSyncId,
            activeSetUpdatedAtMs = normalized.activeSetUpdatedAtMs,
        )
    }

    private fun updateById(buttonId: Long, update: (SavedOverlayButton) -> SavedOverlayButton) {
        val now = System.currentTimeMillis()
        replaceState(
            sets = _sets.value,
            buttons = _buttons.value.map { existing ->
                if (existing.id == buttonId) update(existing).copy(updatedAtMs = now) else existing
            },
            activeSetSyncId = _activeSetSyncId.value,
            activeSetUpdatedAtMs = _activeSetUpdatedAtMs.value,
        )
    }

    private fun replaceState(
        sets: List<SavedOverlayButtonSet>,
        buttons: List<SavedOverlayButton>,
        activeSetSyncId: String?,
        activeSetUpdatedAtMs: Long,
    ) {
        val normalized = normalizeState(StoredState(sets, buttons, activeSetSyncId, activeSetUpdatedAtMs))
        _sets.value = sortSets(normalized.sets)
        _buttons.value = sortButtons(normalized.buttons)
        _activeSetSyncId.value = normalized.activeSetSyncId
        _activeSetUpdatedAtMs.value = normalized.activeSetUpdatedAtMs
        writeState(normalized)
    }

    private fun readState(): StoredState {
        val stored = StoredState(
            sets = readSets(),
            buttons = readButtons(),
            activeSetSyncId = preferences.getString(KEY_ACTIVE_SET_SYNC_ID, null),
            activeSetUpdatedAtMs = preferences.getLong(KEY_ACTIVE_SET_UPDATED_AT, 0L),
        )
        val normalized = normalizeState(stored)
        if (stored != normalized) writeState(normalized)
        return normalized
    }

    private fun normalizeState(state: StoredState): StoredState {
        var sets = state.sets
        var buttons = state.buttons
        var changed = false

        val hasButtons = buttons.isNotEmpty()
        val hasMissingSetButtons = buttons.any { it.setSyncId.isBlank() || sets.none { set -> set.syncId == it.setSyncId } }
        if (hasButtons && (sets.isEmpty() || hasMissingSetButtons)) {
            val defaultSet = createDefaultSetFromButtons(buttons, sets)
            sets = sets + defaultSet
            buttons = buttons.map { button ->
                if (button.setSyncId.isBlank() || sets.none { set -> set.syncId == button.setSyncId }) {
                    button.copy(setSyncId = defaultSet.syncId)
                } else button
            }
            changed = true
        }

        val activeSetSyncId = state.activeSetSyncId?.takeIf { candidate ->
            sets.any { it.syncId == candidate && it.deletedAtMs == null }
        }
        if (activeSetSyncId != state.activeSetSyncId) changed = true

        val activeUpdatedAtMs = state.activeSetUpdatedAtMs.takeIf { it > 0L }
            ?: sets.maxOfOrNull { it.updatedAtMs }
            ?: buttons.maxOfOrNull { it.updatedAtMs }
            ?: 0L

        val normalized = StoredState(
            sets = sortSets(sets),
            buttons = sortButtons(buttons),
            activeSetSyncId = activeSetSyncId,
            activeSetUpdatedAtMs = activeUpdatedAtMs,
        )
        return if (changed || normalized != state) normalized else state
    }

    private fun createDefaultSetFromButtons(
        buttons: List<SavedOverlayButton>,
        existingSets: List<SavedOverlayButtonSet>,
    ): SavedOverlayButtonSet {
        val now = System.currentTimeMillis()
        val anchor = buttons.firstOrNull { it.deletedAtMs == null && it.isVisible }
            ?: buttons.firstOrNull { it.deletedAtMs == null }
            ?: buttons.firstOrNull()
        return SavedOverlayButtonSet(
            id = nextId(existingSets.map { it.id }),
            syncId = UUID.randomUUID().toString(),
            name = normalizedSetName(DEFAULT_SET_NAME, existingSets),
            priority = existingSets.maxOfOrNull { it.priority }?.plus(1) ?: 0,
            portraitXPercent = anchor?.portraitXPercent ?: SavedOverlayButtonSet.DEFAULT_POSITION,
            portraitYPercent = anchor?.portraitYPercent ?: SavedOverlayButtonSet.DEFAULT_POSITION,
            landscapeXPercent = anchor?.landscapeXPercent,
            landscapeYPercent = anchor?.landscapeYPercent,
            createdAtMs = buttons.minOfOrNull { it.createdAtMs } ?: now,
            updatedAtMs = buttons.maxOfOrNull { it.updatedAtMs } ?: now,
        )
    }

    private fun readButtons(): List<SavedOverlayButton> {
        val raw = preferences.getString(KEY_BUTTONS, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    array.optJSONObject(index)?.let(SavedOverlayButton::fromJson)?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun readSets(): List<SavedOverlayButtonSet> {
        val raw = preferences.getString(KEY_SETS, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    array.optJSONObject(index)?.let(SavedOverlayButtonSet::fromJson)?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun writeState(state: StoredState) {
        val buttonsArray = JSONArray()
        sortButtons(state.buttons).forEach { buttonsArray.put(it.toJson()) }
        val setsArray = JSONArray()
        sortSets(state.sets).forEach { setsArray.put(it.toJson()) }
        preferences.edit()
            .putString(KEY_BUTTONS, buttonsArray.toString())
            .putString(KEY_SETS, setsArray.toString())
            .putString(KEY_ACTIVE_SET_SYNC_ID, state.activeSetSyncId)
            .putLong(KEY_ACTIVE_SET_UPDATED_AT, state.activeSetUpdatedAtMs)
            .apply()
    }

    private fun normalizedSetName(name: String, existingSets: List<SavedOverlayButtonSet>): String =
        name.trim().takeIf { it.isNotEmpty() }
            ?: nextLayoutName(existingSets)

    private fun nextLayoutName(existingSets: List<SavedOverlayButtonSet>): String {
        val existingNames = existingSets.filter { it.deletedAtMs == null }.map { it.name }.toSet()
        var index = 1
        while ("Layout $index" in existingNames) index++
        return "Layout $index"
    }

    private fun sortSets(sets: List<SavedOverlayButtonSet>): List<SavedOverlayButtonSet> =
        sets.sortedWith(compareBy<SavedOverlayButtonSet> { it.deletedAtMs != null }.thenBy { it.priority }.thenBy { it.name })

    private fun sortButtons(buttons: List<SavedOverlayButton>): List<SavedOverlayButton> =
        buttons.sortedWith(
            compareBy<SavedOverlayButton> { it.deletedAtMs != null }
                .thenBy { it.setSyncId }
                .thenBy { it.priority }
                .thenBy { it.displayName }
        )

    private fun nextId(existingIds: Iterable<Long>): Long {
        val used = existingIds.toSet()
        var candidate = System.currentTimeMillis()
        while (candidate in used) candidate++
        return candidate
    }

    private data class StoredState(
        val sets: List<SavedOverlayButtonSet>,
        val buttons: List<SavedOverlayButton>,
        val activeSetSyncId: String?,
        val activeSetUpdatedAtMs: Long,
    )
}

private const val PREFS_NAME = "saved_overlay_buttons"
private const val KEY_BUTTONS = "buttons"
private const val KEY_SETS = "sets"
private const val KEY_ACTIVE_SET_SYNC_ID = "activeSetSyncId"
private const val KEY_ACTIVE_SET_UPDATED_AT = "activeSetUpdatedAtMs"
private const val DEFAULT_SET_NAME = "Default"
