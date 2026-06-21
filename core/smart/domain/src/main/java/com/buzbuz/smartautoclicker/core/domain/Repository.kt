/*
 * Copyright (C) 2024 Kevin Buzeau
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.buzbuz.smartautoclicker.core.domain

import android.util.Log
import com.buzbuz.smartautoclicker.core.base.FILE_EXTENSION_PNG

import com.buzbuz.smartautoclicker.core.base.di.Dispatcher
import com.buzbuz.smartautoclicker.core.base.di.HiltCoroutineDispatchers.IO
import com.buzbuz.smartautoclicker.core.base.extensions.mapList
import com.buzbuz.smartautoclicker.core.base.identifier.Identifier
import com.buzbuz.smartautoclicker.core.bitmaps.BitmapRepository
import com.buzbuz.smartautoclicker.core.database.entity.CompleteScenario
import com.buzbuz.smartautoclicker.core.database.entity.EventListDataEntity
import com.buzbuz.smartautoclicker.core.domain.data.ScenarioDataSource
import com.buzbuz.smartautoclicker.core.domain.model.action.Action
import com.buzbuz.smartautoclicker.core.domain.model.action.mapper.toDomain
import com.buzbuz.smartautoclicker.core.domain.model.condition.Condition
import com.buzbuz.smartautoclicker.core.domain.model.condition.ImageCondition
import com.buzbuz.smartautoclicker.core.domain.model.condition.toDomain
import com.buzbuz.smartautoclicker.core.domain.model.event.Event
import com.buzbuz.smartautoclicker.core.domain.model.event.EventGroup
import com.buzbuz.smartautoclicker.core.domain.model.event.ImageEvent
import com.buzbuz.smartautoclicker.core.domain.model.event.ImageEventListData
import com.buzbuz.smartautoclicker.core.domain.model.event.TriggerEvent
import com.buzbuz.smartautoclicker.core.domain.model.event.toDomainImageEvent
import com.buzbuz.smartautoclicker.core.domain.model.event.toDomainTriggerEvent
import com.buzbuz.smartautoclicker.core.domain.model.scenario.Scenario
import com.buzbuz.smartautoclicker.core.domain.model.scenario.toDomain
import com.buzbuz.smartautoclicker.core.domain.model.event.toDomain as eventGroupToDomain

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.max

/**
 * Repository for the database and bitmap manager.
 * Provide the access to the scenario, events, actions and conditions from the database and the conditions bitmap from
 * the application data folder.
 */
internal class Repository @Inject internal constructor(
    @Dispatcher(IO) ioDispatcher: CoroutineDispatcher,
    private val dataSource: ScenarioDataSource,
    private val bitmapRepository: BitmapRepository,
): IRepository {

    private val coroutineScopeIo: CoroutineScope =
        CoroutineScope(SupervisorJob() + ioDispatcher)

    override val scenarios: Flow<List<Scenario>> =
        dataSource.scenarios.mapList { it.toDomain() }

    override val allImageEvents: Flow<List<ImageEvent>> =
        dataSource.allImageEvents.mapList { it.toDomainImageEvent() }

    override val allTriggerEvents: Flow<List<TriggerEvent>> =
        dataSource.allTriggerEvents.mapList { it.toDomainTriggerEvent() }

    override val allConditions: Flow<List<Condition>> =
        dataSource.getAllConditions().mapList { it.toDomain() }

    override val allActions: Flow<List<Action>> =
        dataSource.getAllActions().mapList { it.toDomain() }

    override val legacyConditionsCount: Flow<Int> =
        dataSource.getLegacyImageConditionsFlow()
            .map { it.size }
            .distinctUntilChanged()


    override suspend fun getScenario(scenarioId: Long): Scenario? =
        dataSource.getScenario(scenarioId)?.toDomain()

    override fun getScenarioFlow(scenarioId: Long): Flow<Scenario?> =
        dataSource.getScenarioFlow(scenarioId).map { it?.toDomain() }

    override fun getEventsFlow(scenarioId: Long): Flow<List<Event>> =
        getImageEventsFlow(scenarioId).combine(getTriggerEventsFlow(scenarioId)) { imgEvts, trigEvts ->
            buildList {
                addAll(imgEvts)
                addAll(trigEvts)
            }
        }

    override suspend fun getImageEvents(scenarioId: Long): List<ImageEvent> =
        dataSource.getImageEvents(scenarioId).map { it.toDomainImageEvent() }

    override suspend fun getImageEventListData(scenarioId: Long): List<ImageEventListData> =
        dataSource.getImageEventListData(scenarioId).map { it.toDomain() }

    override fun getImageEventsFlow(scenarioId: Long): Flow<List<ImageEvent>> =
        dataSource.getImageEventsFlow(scenarioId).mapList { it.toDomainImageEvent() }

    override suspend fun getTriggerEvents(scenarioId: Long): List<TriggerEvent> =
        dataSource.getTriggerEvents(scenarioId).map { it.toDomainTriggerEvent() }

    override suspend fun getImageEventGroups(scenarioId: Long): List<EventGroup> =
        dataSource.getImageEventGroups(scenarioId).map { it.eventGroupToDomain() }

    override suspend fun getTriggerEventGroups(scenarioId: Long): List<EventGroup> =
        dataSource.getTriggerEventGroups(scenarioId).map { it.eventGroupToDomain() }

    override suspend fun getTriggerEventCount(scenarioId: Long): Int =
        dataSource.getTriggerEventCount(scenarioId)

    override fun getTriggerEventsFlow(scenarioId: Long): Flow<List<TriggerEvent>> =
        dataSource.getTriggerEventsFlow(scenarioId).mapList { it.toDomainTriggerEvent() }

    override suspend fun addScenario(scenario: Scenario): Long =
        dataSource.addScenario(scenario)

    override suspend fun deleteScenario(scenarioId: Identifier): Unit =
        dataSource.deleteScenario(scenarioId, ::clearRemovedConditionsBitmaps)

    override suspend fun markAsUsed(scenarioId: Identifier) {
        dataSource.markAsUsed(scenarioId.databaseId)
    }

    override suspend fun addScenarioCopy(completeScenario: CompleteScenario): Long? {
        val (scenario, events, eventGroups) = completeScenario.toDomain(cleanIds = true)
        return dataSource.addCompleteScenario(scenario, events, eventGroups, ::clearRemovedConditionsBitmaps)
    }

    override fun addScenarioCopy(scenarioId: Long, copyName: String, onCopyCompleted: (Boolean) -> Unit) {
        coroutineScopeIo.launch {
            val (scenario, events, eventGroups) = dataSource.getCompleteScenario(scenarioId)?.toDomain(cleanIds = true) ?: run {
                onCopyCompleted(false)
                return@launch
            }

            dataSource.addCompleteScenario(
                scenario.copy(name = copyName),
                events,
                eventGroups,
                ::clearRemovedConditionsBitmaps,
            )
            onCopyCompleted(true)
        }
    }

    override suspend fun updateScenario(
        scenario: Scenario,
        events: List<Event>,
        eventGroups: List<EventGroup>,
    ): Boolean =
        dataSource.updateScenario(scenario, events, eventGroups, ::clearRemovedConditionsBitmaps)

    override suspend fun updateScenarioFavorite(scenarioId: Identifier, isFavorite: Boolean) {
        dataSource.updateScenarioFavorite(scenarioId.databaseId, isFavorite)
    }

    override suspend fun getCompleteScenario(scenarioId: Long): CompleteScenario? =
        dataSource.getCompleteScenario(scenarioId)

    override suspend fun getCompleteScenarioBySyncId(syncId: String): CompleteScenario? =
        dataSource.getCompleteScenarioBySyncId(syncId)

    override suspend fun getScenarioDatabaseIdBySyncId(syncId: String): Long? =
        dataSource.getScenarioDatabaseIdBySyncId(syncId)

    override suspend fun upsertScenarioBySyncId(
        completeScenario: CompleteScenario,
        syncId: String,
        updatedAtMs: Long,
    ): Long? = dataSource.upsertScenarioBySyncId(
        completeScenario = completeScenario,
        syncId = syncId,
        updatedAtMs = updatedAtMs,
        onImageConditionsRemoved = ::clearRemovedConditionsBitmaps,
    )

    override suspend fun deleteScenarioBySyncId(syncId: String): Boolean =
        dataSource.deleteScenarioBySyncId(syncId, ::clearRemovedConditionsBitmaps)

    override suspend fun migrateLegacyImageConditions(): Boolean {
        val legacyConditions = dataSource.getLegacyImageConditions()
        Log.i(TAG, "Migrating ${legacyConditions.size} image conditions...")

        var success = true
        val removedPaths = mutableMapOf<String, String>()

        legacyConditions.forEach { conditionEntity ->
            val oldPath = conditionEntity.path ?: return@forEach
            if (oldPath.endsWith(FILE_EXTENSION_PNG)) return@forEach

            val newPath =
                if (removedPaths.containsKey(oldPath)) removedPaths[oldPath]
                else bitmapRepository.migrateImageConditionBitmap(
                    path = oldPath,
                    width = max(0, (conditionEntity.areaRight ?: 0) - (conditionEntity.areaLeft ?: 0)),
                    height = max(0, (conditionEntity.areaBottom ?: 0) - (conditionEntity.areaTop ?: 0)),
                )

            if (newPath == null) {
                success = false
                Log.w(TAG, "Can't migrate legacy condition ${conditionEntity.id}:${conditionEntity.name}")
                return@forEach
            }

            removedPaths[oldPath] = newPath
            dataSource.updateLegacyImageCondition(
                condition = conditionEntity,
                newPath = newPath,
            )
        }

        return success
    }

    /**
     * Remove bitmaps from the application data folder.
     * @param removedPath the list of path for the bitmaps to be removed.
     */
    private suspend fun clearRemovedConditionsBitmaps(removedPath: List<String>) {
        Log.d(TAG, "Clearing removed conditions bitmaps: $removedPath")
        val deletedPaths = removedPath.filter { path ->
            path.isNotEmpty() && dataSource.getImageConditionPathUsageCount(path) == 0
        }

        Log.d(TAG, "Removed conditions count: ${removedPath.size}; Unused bitmaps after removal: ${deletedPaths.size}")
        if (deletedPaths.isNotEmpty()) bitmapRepository.deleteImageConditionBitmaps(deletedPaths)
    }
}

private fun EventListDataEntity.toDomain(): ImageEventListData = ImageEventListData(
    id = Identifier(id = event.id),
    name = event.name,
    actionsCount = actionsCount,
    conditionsCount = conditionsCount,
    firstCondition = firstCondition?.toDomain() as? ImageCondition,
)

/** Tag for logs. */
private const val TAG = "RepositoryImpl"
