/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.feature.sync.data

/**
 * Reads/writes local preference blobs through the app's existing DataStore singletons.
 * Implemented in the app module to avoid opening duplicate DataStore files.
 */
interface SettingsSyncLocalPort {
    suspend fun readLocalPayload(): SacProfileSettingsPayload
    suspend fun readLocalUpdatedAtMs(): Long
    suspend fun applyRemotePayload(payload: SacProfileSettingsPayload)
    suspend fun remapQsTileAfterScenarioSync()
}
