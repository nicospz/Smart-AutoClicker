/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.feature.throwlet

import android.content.Context
import android.util.Log
import com.buzbuz.smartautoclicker.feature.throwlet.data.GestureStore
import com.buzbuz.smartautoclicker.feature.throwlet.data.ThrowletDatabase
import com.buzbuz.smartautoclicker.feature.throwlet.sync.SupabaseSyncRepository
import com.buzbuz.smartautoclicker.feature.throwlet.sync.SupabaseSyncResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class ThrowletRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: ThrowletDatabase,
    private val gestureStore: GestureStore,
    private val syncRepository: SupabaseSyncRepository,
) {
    suspend fun syncAll(): Pair<SupabaseSyncResult, SupabaseSyncResult> {
        val (gestures, buddyCrops) = syncRepository.syncAll()
        logSync("gesture sync", gestures)
        logSync("buddy crop sync", buddyCrops)
        return gestures to buddyCrops
    }

    suspend fun saveCatchGesture(
        lane: HelperLane,
        payloadHex: String,
        eventCount: Int,
        durationMs: Long,
        pokemonKey: String = DEFAULT_POKEMON_KEY,
        pokemonName: String = DEFAULT_POKEMON_NAME,
    ): SupabaseSyncResult = withContext(Dispatchers.IO) {
        val display = context.displaySize()
        val storageMode = GestureModes.storageMode(HelperMode.CATCH, lane)
        val saved = gestureStore.save(
            pokemonKey = pokemonKey,
            pokemonName = pokemonName,
            gestureMode = storageMode,
            payloadHex = payloadHex,
            eventCount = eventCount,
            durationMs = durationMs,
            helperMode = HelperMode.CATCH,
            sourceLane = lane,
            display = display,
        )
        val push = syncRepository.pushGesture(saved)
        logSync("gesture push", push)
        push
    }

    suspend fun findCatchGesturePayload(
        lane: HelperLane,
        pokemonKey: String = DEFAULT_POKEMON_KEY,
    ): String? = withContext(Dispatchers.IO) {
        val storageMode = GestureModes.storageMode(HelperMode.CATCH, lane)
        gestureStore.find(pokemonKey, storageMode)?.payloadHex
    }

    suspend fun pushBuddyCrop(entity: BuddyCropEntity): SupabaseSyncResult {
        val push = syncRepository.pushBuddyCrop(entity)
        logSync("buddy crop push", push)
        return push
    }

    suspend fun saveBuddyCrop(entity: BuddyCropEntity): BuddyCropEntity? = withContext(Dispatchers.IO) {
        database.buddyCropDao().upsert(entity)
        database.buddyCropDao().getByPokemonKey(entity.pokemonKey)
    }

    suspend fun findBuddyCrop(pokemonKey: String): BuddyCropEntity? = withContext(Dispatchers.IO) {
        database.buddyCropDao().getByPokemonKey(pokemonKey)
    }

    private fun logSync(label: String, result: SupabaseSyncResult) {
        Log.i(TAG, "$label: ${result.statusText()}")
    }

    private fun Context.displaySize(): SizeI {
        val metrics = resources.displayMetrics
        return SizeI(metrics.widthPixels, metrics.heightPixels)
    }

    companion object {
        private const val TAG = "ThrowletRepository"
        const val DEFAULT_POKEMON_KEY = "unknown"
        const val DEFAULT_POKEMON_NAME = "Unknown"
    }
}
