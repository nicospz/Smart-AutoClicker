/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.feature.sync.domain

import android.content.Context
import android.graphics.Point
import android.util.Log
import com.buzbuz.smartautoclicker.feature.sync.BuildConfig
import com.buzbuz.smartautoclicker.feature.sync.data.ScenarioSyncRepository
import com.buzbuz.smartautoclicker.feature.sync.data.SettingsSyncRepository
import com.buzbuz.smartautoclicker.feature.sync.data.SacSyncPreferences
import com.buzbuz.smartautoclicker.feature.sync.data.ThrowletSyncBridge
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class SacSyncEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scenarioSyncRepository: ScenarioSyncRepository,
    private val settingsSyncRepository: SettingsSyncRepository,
    private val throwletSyncBridge: ThrowletSyncBridge,
    private val syncPreferences: SacSyncPreferences,
) {
    val isConfigured: Boolean
        get() = BuildConfig.SUPABASE_URL.isNotBlank() &&
            BuildConfig.SUPABASE_ANON_KEY.isNotBlank() &&
            BuildConfig.SUPABASE_SYNC_PROFILE_ID.isNotBlank() &&
            BuildConfig.SUPABASE_SYNC_SECRET.isNotBlank()

    suspend fun syncAll(): SacSyncResult = withContext(Dispatchers.IO) {
        if (!isConfigured) {
            return@withContext SacSyncResult(skipped = true, statusMessage = "sync not configured")
        }
        runCatching {
            val screenSize = context.resources.displayMetrics.let { Point(it.widthPixels, it.heightPixels) }
            val scenarios = scenarioSyncRepository.syncScenarios(screenSize)
            val settings = settingsSyncRepository.syncSettings()
            val throwlet = throwletSyncBridge.syncAll()
            val result = SacSyncResult(
                scenariosPushed = scenarios.remotePushed,
                scenariosPulled = scenarios.remotePulled,
                settingsPushed = settings.remotePushed,
                settingsPulled = settings.remotePulled,
                throwletGesturesPushed = throwlet.gestures.remotePushed,
                throwletGesturesPulled = throwlet.gestures.remotePulled,
                throwletBuddyPushed = throwlet.buddyCrops.remotePushed,
                throwletBuddyPulled = throwlet.buddyCrops.remotePulled,
                catchNeedlesPushed = throwlet.catchNeedlesPushed,
                catchNeedlesPulled = throwlet.catchNeedlesPulled,
            )
            syncPreferences.recordSuccess()
            Log.i(TAG, "sync complete: $result")
            result
        }.getOrElse { error ->
            val message = humanizeSyncError(error)
            syncPreferences.recordFailure(message)
            Log.w(TAG, "sync failed: $message", error)
            SacSyncResult(errorMessage = message)
        }
    }

    suspend fun syncThrowlet() = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext
        runCatching { throwletSyncBridge.syncAll() }
            .onSuccess { syncPreferences.recordSuccess() }
            .onFailure { syncPreferences.recordFailure(humanizeSyncError(it)) }
    }

    suspend fun pushSettings() = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext
        runCatching { settingsSyncRepository.pushSettingsNow() }
            .onFailure { syncPreferences.recordFailure(humanizeSyncError(it)) }
    }

    companion object {
        private const val TAG = "SacSyncEngine"

        private fun humanizeSyncError(error: Throwable): String {
            val raw = error.message ?: error.javaClass.simpleName
            return if (raw.contains("invalid profile", ignoreCase = true)) {
                "Profile not registered in Supabase. Run migrations 001_sac_sync.sql and " +
                    "002_throwlet_gesture_buddy.sql, then register: " +
                    "INSERT INTO sac_sync_profiles (profile_id, sync_secret) " +
                    "VALUES ('<your-profile-id>', '<your-sync-secret>') " +
                    "ON CONFLICT (profile_id) DO UPDATE SET sync_secret = EXCLUDED.sync_secret; " +
                    "(Use SUPABASE_GESTURE_PROFILE_ID and SUPABASE_GESTURE_SYNC_SECRET from local.properties.)"
            } else {
                raw
            }
        }
    }
}

data class SacSyncResult(
    val scenariosPushed: Int = 0,
    val scenariosPulled: Int = 0,
    val settingsPushed: Int = 0,
    val settingsPulled: Int = 0,
    val throwletGesturesPushed: Int = 0,
    val throwletGesturesPulled: Int = 0,
    val throwletBuddyPushed: Int = 0,
    val throwletBuddyPulled: Int = 0,
    val catchNeedlesPushed: Int = 0,
    val catchNeedlesPulled: Int = 0,
    val skipped: Boolean = false,
    val errorMessage: String? = null,
    val statusMessage: String? = null,
) {
    val didWork: Boolean
        get() = scenariosPushed + scenariosPulled + settingsPushed + settingsPulled +
            throwletGesturesPushed + throwletGesturesPulled + throwletBuddyPushed + throwletBuddyPulled +
            catchNeedlesPushed + catchNeedlesPulled > 0

    fun displayStatus(): String = when {
        skipped -> statusMessage ?: "sync not configured"
        errorMessage != null -> "sync failed: $errorMessage"
        didWork -> "synced"
        else -> "already synced"
    }
}
