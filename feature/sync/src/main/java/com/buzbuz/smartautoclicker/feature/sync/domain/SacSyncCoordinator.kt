/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.feature.sync.domain

import com.buzbuz.smartautoclicker.core.base.di.Dispatcher
import com.buzbuz.smartautoclicker.core.base.di.HiltCoroutineDispatchers.IO
import com.buzbuz.smartautoclicker.feature.sync.data.ScenarioSyncRepository
import android.graphics.Point
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

@Singleton
class SacSyncCoordinator @Inject constructor(
    @Dispatcher(IO) ioDispatcher: CoroutineDispatcher,
    private val syncEngine: SacSyncEngine,
    private val scenarioSyncRepository: ScenarioSyncRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val debouncedScenarioPushJobs: ConcurrentHashMap<String, Job> = ConcurrentHashMap()

    fun requestFullSync() {
        scope.launch { syncEngine.syncAll() }
    }

    fun requestThrowletSync() {
        scope.launch { syncEngine.syncThrowlet() }
    }

    fun scheduleScenarioPush(syncId: String, isSmart: Boolean, screenWidth: Int, screenHeight: Int) {
        if (syncId.isBlank()) return

        val pushKey = "$isSmart:$syncId"
        debouncedScenarioPushJobs[pushKey]?.cancel()
        val pushJob = scope.launch {
            delay(DEBOUNCE_MS)
            scenarioSyncRepository.pushScenario(
                syncId = syncId,
                isSmart = isSmart,
                screenSize = Point(screenWidth, screenHeight),
            )
        }
        debouncedScenarioPushJobs[pushKey] = pushJob
        pushJob.invokeOnCompletion {
            debouncedScenarioPushJobs.remove(pushKey, pushJob)
        }
    }

    fun scheduleScenarioDeletePush(
        syncId: String,
        isSmart: Boolean,
        screenWidth: Int,
        screenHeight: Int,
    ) {
        if (syncId.isBlank()) return
        scope.launch {
            scenarioSyncRepository.pushScenarioDeleted(
                syncId = syncId,
                isSmart = isSmart,
                deletedAtMs = System.currentTimeMillis(),
                screenSize = Point(screenWidth, screenHeight),
            )
        }
    }

    fun scheduleSettingsPush() {
        scope.launch { syncEngine.pushSettings() }
    }

    companion object {
        private const val DEBOUNCE_MS = 5_000L
    }
}
