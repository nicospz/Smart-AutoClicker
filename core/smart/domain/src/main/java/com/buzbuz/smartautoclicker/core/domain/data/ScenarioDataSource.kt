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
package com.buzbuz.smartautoclicker.core.domain.data

import android.util.Log
import androidx.room.withTransaction

import com.buzbuz.smartautoclicker.core.base.DatabaseListUpdater
import com.buzbuz.smartautoclicker.core.base.identifier.DATABASE_ID_INSERTION
import com.buzbuz.smartautoclicker.core.base.identifier.Identifier
import com.buzbuz.smartautoclicker.core.base.interfaces.areComplete
import com.buzbuz.smartautoclicker.core.database.ClickDatabase
import com.buzbuz.smartautoclicker.core.database.entity.ActionEntity
import com.buzbuz.smartautoclicker.core.database.entity.CompleteActionEntity
import com.buzbuz.smartautoclicker.core.database.entity.CompleteEventEntity
import com.buzbuz.smartautoclicker.core.database.entity.CompleteScenario
import com.buzbuz.smartautoclicker.core.database.entity.ConditionEntity
import com.buzbuz.smartautoclicker.core.database.entity.EventEntity
import com.buzbuz.smartautoclicker.core.database.entity.EventToggleEntity
import com.buzbuz.smartautoclicker.core.database.entity.EventListDataEntity
import com.buzbuz.smartautoclicker.core.database.entity.IntentExtraEntity
import com.buzbuz.smartautoclicker.core.database.entity.ScenarioStatsEntity
import com.buzbuz.smartautoclicker.core.database.entity.ScenarioWithEvents
import com.buzbuz.smartautoclicker.core.domain.model.action.Action
import com.buzbuz.smartautoclicker.core.domain.model.action.Intent
import com.buzbuz.smartautoclicker.core.domain.model.action.ToggleEvent
import com.buzbuz.smartautoclicker.core.domain.model.action.toggleevent.EventToggle
import com.buzbuz.smartautoclicker.core.domain.model.action.intent.IntentExtra
import com.buzbuz.smartautoclicker.core.domain.model.action.intent.toEntity
import com.buzbuz.smartautoclicker.core.domain.model.action.mapper.toEntity
import com.buzbuz.smartautoclicker.core.domain.model.action.toggleevent.toEntity
import com.buzbuz.smartautoclicker.core.domain.model.condition.Condition
import com.buzbuz.smartautoclicker.core.domain.model.condition.ImageCondition
import com.buzbuz.smartautoclicker.core.domain.model.condition.toEntity
import com.buzbuz.smartautoclicker.core.domain.model.event.toEntity
import com.buzbuz.smartautoclicker.core.domain.model.scenario.Scenario
import com.buzbuz.smartautoclicker.core.domain.model.scenario.toEntity
import com.buzbuz.smartautoclicker.core.domain.model.condition.TriggerCondition
import com.buzbuz.smartautoclicker.core.domain.model.event.Event
import com.buzbuz.smartautoclicker.core.domain.model.event.ImageEvent

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow

import java.lang.Exception
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
internal class ScenarioDataSource @Inject constructor(
    private val normalDatabase: ClickDatabase,
) {

    /** State of scenario during an update, to keep track of ids mapping. */
    private val scenarioUpdateState = ScenarioUpdateState()

    val scenarios: Flow<List<ScenarioWithEvents>> =
        normalDatabase.scenarioDao().getScenariosWithEvents()

    val allTriggerEvents: Flow<List<CompleteEventEntity>> =
        normalDatabase.eventDao().getAllTriggerEventsFlow()

    val allImageEvents: Flow<List<CompleteEventEntity>> =
        normalDatabase.eventDao().getAllImageEventsFlow()


    suspend fun getScenario(scenarioId: Long): ScenarioWithEvents? =
        normalDatabase.scenarioDao().getScenario(scenarioId)

    suspend fun getCompleteScenario(scenarioId: Long): CompleteScenario? =
        normalDatabase.scenarioDao().getCompleteScenario(scenarioId)

    fun getScenarioFlow(scenarioId: Long): Flow<ScenarioWithEvents?> =
        normalDatabase.scenarioDao().getScenarioFlow(scenarioId)

    suspend fun getImageEvents(scenarioId: Long): List<CompleteEventEntity> =
        normalDatabase.eventDao().getCompleteImageEvents(scenarioId)

    suspend fun getImageEventListData(scenarioId: Long): List<EventListDataEntity> =
        normalDatabase.eventDao().getImageEventListData(scenarioId)

    fun getImageEventsFlow(scenarioId: Long): Flow<List<CompleteEventEntity>> =
        normalDatabase.eventDao().getCompleteImageEventsFlow(scenarioId)

    suspend fun getTriggerEvents(scenarioId: Long): List<CompleteEventEntity> =
        normalDatabase.eventDao().getCompleteTriggerEvents(scenarioId)

    suspend fun getTriggerEventCount(scenarioId: Long): Int =
        normalDatabase.eventDao().getTriggerEventCount(scenarioId)

    fun getTriggerEventsFlow(scenarioId: Long): Flow<List<CompleteEventEntity>> =
        normalDatabase.eventDao().getCompleteTriggerEventsFlow(scenarioId)

    fun getAllConditions(): Flow<List<ConditionEntity>> =
        normalDatabase.conditionDao().getAllConditions()

    fun getAllActions(): Flow<List<CompleteActionEntity>> =
        normalDatabase.actionDao().getAllActions()

    suspend fun getImageConditionPathUsageCount(path: String): Int =
        normalDatabase.conditionDao().getValidPathCount(path)

    suspend fun addScenario(scenario: Scenario): Long {
        Log.d(TAG, "Add scenario to the database: ${scenario.id}")
        return normalDatabase.scenarioDao().add(scenario.toEntity())
    }

    suspend fun deleteScenario(scenarioId: Identifier, onImageConditionsRemoved: suspend (List<String>) -> Unit) {
        Log.d(TAG, "Delete scenario from the database: $scenarioId")

        val removedConditionsPath = mutableListOf<String>()
        normalDatabase.eventDao().getEventsIds(scenarioId.databaseId).forEach { eventId ->
            normalDatabase.conditionDao().getConditionsPaths(eventId).forEach { path ->
                if (!removedConditionsPath.contains(path)) removedConditionsPath.add(path)
            }
        }

        normalDatabase.scenarioDao().delete(scenarioId.databaseId)
        onImageConditionsRemoved(removedConditionsPath)
    }

    suspend fun addCompleteScenario(
        scenario: Scenario,
        events: List<Event>,
        onImageConditionsRemoved: suspend (List<String>) -> Unit,
    ): Long? {
        Log.d(TAG, "Add scenario copy to the database: ${scenario.id}")

        // Check the events correctness
        if (!events.areComplete())
            throw IllegalArgumentException("Can't update scenario content, one of the event is not complete")

        return try {
            normalDatabase.withTransaction {
                // First insert the scenario to get its database id, and put it in all events
                Log.d(TAG, "Insert scenario entity for copy: name=${scenario.name}, events=${events.size}")
                val scenarioId = Identifier(
                    databaseId = normalDatabase.scenarioDao().add(scenario.toEntity())
                )
                Log.d(TAG, "Inserted scenario copy with database id ${scenarioId.databaseId}")

                updateEvents(
                    scenarioDbId = scenarioId.databaseId,
                    events = events,
                    onImageConditionsRemoved = onImageConditionsRemoved,
                )

                scenarioId.databaseId
            }
        } catch (ex: Exception) {
            Log.e(TAG, "Error while inserting scenario copy", ex)
            null
        }
    }

    suspend fun updateScenario(
        scenario: Scenario,
        events: List<Event>,
        onImageConditionsRemoved: suspend (List<String>) -> Unit,
    ): Boolean {
        Log.d(TAG, "Update scenario in the database: ${scenario.id}")

        return try {
            normalDatabase.withTransaction {
                // Update scenario entity values
                normalDatabase.scenarioDao().update(scenario.toEntity())
                // Update scenario content
                updateEvents(
                    scenarioDbId = scenario.id.databaseId,
                    events = events,
                    onImageConditionsRemoved = onImageConditionsRemoved,
                )
            }

            true
        } catch (ex: Exception) {
            Log.e(TAG, "Error while updating scenario\n* Scenario=$scenario\n* Events=$events\n", ex)
            false
        }
    }

    suspend fun updateScenarioFavorite(scenarioDbId: Long, isFavorite: Boolean) {
        normalDatabase.scenarioDao().updateFavorite(scenarioDbId, isFavorite)
    }

    suspend fun markAsUsed(scenarioDbId: Long) {
        normalDatabase.scenarioDao().let { scenarioDao ->
            val previousStats = scenarioDao.getScenarioStats(scenarioDbId)
            if (previousStats != null) {
                scenarioDao.updateScenarioStats(
                    previousStats.copy(
                        lastStartTimestampMs = System.currentTimeMillis(),
                        startCount = previousStats.startCount + 1,
                    )
                )
            } else {
                scenarioDao.addScenarioStats(
                    ScenarioStatsEntity(
                        id = DATABASE_ID_INSERTION,
                        scenarioId = scenarioDbId,
                        lastStartTimestampMs = System.currentTimeMillis(),
                        startCount = 1,
                    )
                )
            }
        }
    }

    suspend fun getLegacyImageConditions(): List<ConditionEntity> =
        normalDatabase.conditionDao().getLegacyImageConditions()

    fun getLegacyImageConditionsFlow(): Flow<List<ConditionEntity>> =
        normalDatabase.conditionDao().getLegacyImageConditionsFlow()

    suspend fun updateLegacyImageCondition(condition: ConditionEntity, newPath: String) {
        val updatedCondition = condition.copy(path = newPath)
        normalDatabase.conditionDao().updateCondition(updatedCondition)
    }

    private suspend fun updateEvents(
        scenarioDbId: Long,
        events: List<Event>,
        onImageConditionsRemoved: suspend (List<String>) -> Unit,
    ) {
        scenarioUpdateState.initUpdateState()
        val updater = DatabaseListUpdater<Event, EventEntity>()

        Log.d(TAG, "Updating events in the database for scenario $scenarioDbId")
        updater.refreshUpdateValues(
            currentEntities = normalDatabase.eventDao().getEvents(scenarioDbId),
            newItems = events,
            mappingClosure = { event ->
                event.toEntity().apply {
                    scenarioId = scenarioDbId
                }
            }
        )
        Log.d(TAG, "Events updater: $updater")

        normalDatabase.eventDao().let { eventDao ->
            Log.d(TAG, "Insert/update/delete events for scenario $scenarioDbId")
            updater.executeUpdate(
                addList = eventDao::addEvents,
                updateList = eventDao::updateEvent,
                removeList = eventDao::deleteEvents,
                onSuccess = { addedMapping, added, updated, removed ->
                    Log.d(
                        TAG,
                        "Events write completed: added=${added.size}, updated=${updated.size}, " +
                            "removed=${removed.size}, idMap=$addedMapping",
                    )
                    addedMapping.forEach { (domainId, dbId) ->
                        scenarioUpdateState.addEventIdMapping(domainId, dbId)
                    }

                    updateEventsChildren(
                        scenarioDbId = scenarioDbId,
                        events = buildList {
                            addAll(added)
                            addAll(updated)
                        },
                        onImageConditionsRemoved = onImageConditionsRemoved,
                    )

                    if (removed.isNotEmpty()) onImageConditionsRemoved(events.getRemovedConditionsPath(removed))
                }
            )
        }
    }

    private suspend fun updateEventsChildren(
        scenarioDbId: Long,
        events: List<Event>,
        onImageConditionsRemoved: suspend (List<String>) -> Unit,
    ) {
        // Actions can reference a condition, do them all first
        events.forEach { event ->
            updateConditions(
                eventDbId = scenarioUpdateState.getEventDbId(event.id),
                newConditions = event.conditions,
                onImageConditionsRemoved = onImageConditionsRemoved,
            )
        }

        // Refresh condition references now that newly created conditions have database ids.
        events.forEach { event ->
            updateEventConditionReferences(
                scenarioDbId = scenarioDbId,
                eventDbId = scenarioUpdateState.getEventDbId(event.id),
                event = event,
            )
        }

        // Second iteration for actions
        events.forEach { event ->
            updateActions(
                eventDbId = scenarioUpdateState.getEventDbId(event.id),
                newActions = event.actions,
            )
        }
    }

    private suspend fun updateEventConditionReferences(scenarioDbId: Long, eventDbId: Long, event: Event) {
        normalDatabase.eventDao().updateEvent(listOf(
            event.toEntity().copy(
                id = eventDbId,
                scenarioId = scenarioDbId,
            )
        ))
    }

    private suspend fun updateConditions(
        eventDbId: Long,
        newConditions: List<Condition>,
        onImageConditionsRemoved: suspend (List<String>) -> Unit,
    ) {
        val updater = DatabaseListUpdater<Condition, ConditionEntity>()

        Log.d(TAG, "Updating conditions in the database for event $eventDbId")
        updater.refreshUpdateValues(
            currentEntities = normalDatabase.conditionDao().getConditions(eventDbId),
            newItems = newConditions,
            mappingClosure = { condition ->
                when (condition) {
                    is ImageCondition ->
                        condition.copy(eventId = Identifier(databaseId = eventDbId)).toEntity()
                    is TriggerCondition ->
                        condition.copy(evtId = Identifier(databaseId = eventDbId)).toEntity()
                }
            }
        )
        Log.d(TAG, "Conditions updater: $updater")

        normalDatabase.conditionDao().let { conditionDao ->
            Log.d(TAG, "Insert/update/delete conditions for event $eventDbId")
            updater.executeUpdate(
                addList = conditionDao::addConditions,
                updateList = conditionDao::updateConditions,
                removeList = conditionDao::deleteConditions,
                onSuccess = { addedMapping, _, _, removed ->
                    Log.d(
                        TAG,
                        "Conditions write completed: added=${addedMapping.size}, removed=${removed.size}, idMap=$addedMapping",
                    )
                    addedMapping.forEach { (domainId, dbId) ->
                        scenarioUpdateState.addConditionIdMapping(domainId, dbId)
                    }

                    if (removed.isNotEmpty()) onImageConditionsRemoved(removed.mapNotNull { it.path })
                }
            )
        }
    }

    private suspend fun updateActions(eventDbId: Long, newActions: List<Action>) {
        val currentCompleteActions = normalDatabase.actionDao().getCompleteActions(eventDbId)
        val currentActionsEntities = currentCompleteActions.map { it.action }
        val updater = DatabaseListUpdater<Action, ActionEntity>()

        Log.d(TAG, "Updating actions in the database for event $eventDbId")
        updater.refreshUpdateValues(
            currentEntities = currentActionsEntities,
            newItems = newActions,
            mappingClosure = { actionInEvent ->
                actionInEvent.toEntity().apply {
                    eventId = eventDbId
                    clickOnConditionId = scenarioUpdateState.getClickOnConditionDatabaseId(actionInEvent)
                }
            }
        )
        Log.d(TAG, "Actions updater: $updater")

        normalDatabase.actionDao().let { actionDao ->
            Log.d(TAG, "Insert/update/delete actions for event $eventDbId")
            updater.executeUpdate(
                addList = actionDao::addActions,
                updateList = actionDao::updateActions,
                removeList = actionDao::deleteActions,
                onSuccess = { addedMapping, added, updated, _ ->
                    Log.d(
                        TAG,
                        "Actions write completed: added=${added.size}, updated=${updated.size}, idMap=$addedMapping",
                    )
                    addedMapping.forEach { (domainId, dbId) ->
                        scenarioUpdateState.addActionIdMapping(domainId, dbId)
                    }

                    updateActionsChildren(buildList {
                        addAll(added)
                        addAll(updated)
                    })
                }
            )
        }
    }

    private suspend fun updateActionsChildren(actions: List<Action>) {
        actions.forEach { action ->
            when (action) {
                is Intent -> {
                    action.extras?.let { extras ->
                        updateIntentExtras(
                            actionDbId = scenarioUpdateState.getActionDbId(action.id),
                            newExtras = extras,
                        )
                    }
                }

                is ToggleEvent -> {
                    updateEventToggles(
                        actionDbId = scenarioUpdateState.getActionDbId(action.id),
                        newToggles = action.eventToggles,
                    )
                }

                else -> Unit
            }
        }
    }

    private suspend fun updateIntentExtras(actionDbId: Long, newExtras: List<IntentExtra<out Any>>) {
        val updater = DatabaseListUpdater<IntentExtra<out Any>, IntentExtraEntity>()

        updater.refreshUpdateValues(
            currentEntities = normalDatabase.actionDao().getIntentExtras(actionDbId),
            newItems = newExtras,
            mappingClosure = { item ->
                item.toEntity().apply {
                    actionId = actionDbId
                }
            }
        )
        Log.d(TAG, "IntentExtra updater $updater")

        normalDatabase.actionDao().let { actionDao ->
            Log.d(TAG, "Insert/update/delete intent extras for action $actionDbId")
            updater.executeUpdate(
                addList = actionDao::addIntentExtras,
                updateList = actionDao::updateIntentExtras,
                removeList = actionDao::deleteIntentExtras,
            )
        }
    }

    private suspend fun updateEventToggles(actionDbId: Long, newToggles: List<EventToggle>) {
        val updater = DatabaseListUpdater<EventToggle, EventToggleEntity>()

        updater.refreshUpdateValues(
            currentEntities = normalDatabase.actionDao().getEventsToggles(actionDbId),
            newItems = newToggles,
            mappingClosure = { item ->
                item.toEntity().apply {
                    actionId = actionDbId
                    toggleEventId = item.targetEventId?.let { scenarioUpdateState.getEventDbId(it) }
                }
            }
        )
        Log.d(TAG, "EventToggle updater $updater")

        normalDatabase.actionDao().let { actionDao ->
            Log.d(TAG, "Insert/update/delete event toggles for action $actionDbId")
            updater.executeUpdate(
                addList = actionDao::addEventToggles,
                updateList = actionDao::updateEventToggles,
                removeList = actionDao::deleteEventToggles,
            )
        }
    }

    private fun List<Event>.getRemovedConditionsPath(removedEntities: List<EventEntity>): List<String> =
        buildList {
            removedEntities.forEach { removedEntity ->
                // Find the deleted domain event, get its image conditions list and map to their path
                val removedEvent = this@getRemovedConditionsPath
                    .find { event -> event is ImageEvent && event.id.databaseId == removedEntity.id }
                    ?.conditions?.filterIsInstance<ImageCondition>()
                    ?.map { condition -> condition.path }
                    ?: return@forEach

                addAll(removedEvent)
            }
        }
}

/** Tag for logs. */
private const val TAG = "ScenarioDataSource"
