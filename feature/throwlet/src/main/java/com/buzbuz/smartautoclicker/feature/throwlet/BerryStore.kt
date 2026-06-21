package com.buzbuz.smartautoclicker.feature.throwlet

import android.content.Context
import androidx.core.content.edit

class BerryStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(pokemonName: String?): BerryAction {
        if (pokemonName.isNullOrBlank()) return BerryAction.NONE
        val saved = prefs.getString(prefKey(pokemonName), BerryAction.NONE.name) ?: BerryAction.NONE.name
        return BerryAction.entries.firstOrNull { it.name == saved } ?: BerryAction.NONE
    }

    fun save(pokemonName: String?, berry: BerryAction) {
        if (pokemonName.isNullOrBlank()) return
        prefs.edit { putString(prefKey(pokemonName), berry.name) }
    }

    private fun prefKey(pokemonName: String): String =
        "$PREF_PREFIX${pokemonName.lowercase()}"

    companion object {
        private const val PREFS = "throwlet_catch_berry"
        private const val PREF_PREFIX = "selected_berry."
    }
}
