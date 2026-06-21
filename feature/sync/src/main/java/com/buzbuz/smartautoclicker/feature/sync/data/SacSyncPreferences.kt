/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.feature.sync.data

import android.content.Context
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.buzbuz.smartautoclicker.core.base.PreferencesDataStore
import com.buzbuz.smartautoclicker.core.base.di.Dispatcher
import com.buzbuz.smartautoclicker.core.base.di.HiltCoroutineDispatchers.IO
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class SacSyncPreferences @Inject constructor(
    @ApplicationContext context: Context,
    @Dispatcher(IO) ioDispatcher: CoroutineDispatcher,
) {
    private val dataStore = PreferencesDataStore(context, ioDispatcher, PREFERENCES_FILE)

    val statusFlow: Flow<SacSyncStatus> = dataStore.data.map { prefs ->
        SacSyncStatus(
            lastSuccessAtMs = prefs[KEY_LAST_SUCCESS_AT] ?: 0L,
            lastError = prefs[KEY_LAST_ERROR],
        )
    }

    suspend fun recordSuccess() {
        val now = System.currentTimeMillis()
        dataStore.edit { prefs ->
            prefs[KEY_LAST_SUCCESS_AT] = now
            prefs.remove(KEY_LAST_ERROR)
        }
    }

    suspend fun recordFailure(message: String?) {
        dataStore.edit { prefs ->
            prefs[KEY_LAST_ERROR] = message.orEmpty()
        }
    }

    companion object {
        private const val PREFERENCES_FILE = "sac_sync"
        private val KEY_LAST_SUCCESS_AT = longPreferencesKey("lastSuccessAtMs")
        private val KEY_LAST_ERROR = stringPreferencesKey("lastError")
    }
}

data class SacSyncStatus(
    val lastSuccessAtMs: Long = 0L,
    val lastError: String? = null,
)
